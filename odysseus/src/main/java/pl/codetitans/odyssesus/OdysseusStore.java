package pl.codetitans.odyssesus;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Owns the queue of pre-serialized JSON entries waiting to be uploaded, and - when constructed
 * with a write-ahead directory - their durable backing on disk, split across multiple small chunk
 * files instead of one ever-growing file.
 * <p>
 * At any time there's at most one "active" chunk, held only in memory, that new entries are
 * appended to. Once it reaches {@code entriesPerFile} entries it's written to disk in one go,
 * registered as a "closed" chunk (only its file identity is kept in memory from then on - never
 * its content), and a fresh empty active chunk takes over. This means:
 * <ul>
 *     <li>only ever up to {@code entriesPerFile} entries sit in memory at once, no matter how big
 *     the backlog on disk gets;</li>
 *     <li>closed chunks are written exactly once and never rewritten - a long outage with entries
 *     still streaming in keeps producing new, small, one-shot writes instead of repeatedly
 *     rewriting a single growing file;</li>
 *     <li>{@link #takeBatch} still hands the active chunk straight out of memory with no write
 *     beforehand (see {@link #confirmSent}) - on a healthy connection, nothing needs to touch disk
 *     at all, exactly as before chunking was introduced.</li>
 * </ul>
 * At most {@code maxFiles} closed chunks are ever kept - if closing a new one would exceed that,
 * the oldest chunk (and its up-to-{@code entriesPerFile} entries) is dropped first. Total capacity
 * is therefore {@code entriesPerFile * maxFiles} entries.
 * <p>
 * This class only manages storage - it knows nothing about the network. Callers hand a batch off
 * via {@link #takeBatch} (always the oldest available data first) and report the outcome back via
 * {@link #confirmSent} or {@link #requeue}.
 */
final class OdysseusStore {
    private static final String TAG = "OdysseusStore";
    private static final String CHUNK_FILE_SUFFIX = ".jsonl";

    private final Object lock = new Object();
    @Nullable
    private final File walDir;
    private final int entriesPerFile;
    private final int maxFiles;

    // The active chunk: entirely in memory until it's closed (rotated out because it reached
    // entriesPerFile) or an upload attempt needs it durable ahead of that.
    private final List<String> activeChunk = new ArrayList<>();
    private int activeChunkPersistedCount;
    private long activeChunkSequence;

    // Closed chunks: fully on disk, not kept in memory - only their file identity, oldest first.
    private final Deque<Long> closedChunks = new ArrayDeque<>();

    OdysseusStore(@Nullable File walDir, int entriesPerFile, int maxFiles) {
        this.walDir = walDir;
        this.entriesPerFile = Math.max(entriesPerFile, 1);
        this.maxFiles = Math.max(maxFiles, 1);

        // Recover chunk files left over from a previous process (crash, kill, no network, ...).
        // Every file found is treated as a closed chunk, whether or not it was ever filled to
        // entriesPerFile - simpler than trying to resume filling a partial one, and still fully
        // durable. A fresh, empty active chunk starts right after.
        final List<Long> recovered = listChunkSequences(walDir);
        long nextSequence = 0;
        if (!recovered.isEmpty()) {
            Log.i(TAG, "Recovered " + recovered.size() + " pending chunk file(s) from a previous session");
            synchronized (lock) {
                closedChunks.addAll(recovered);
                evictExcessChunks(); // in case maxFiles was lowered since the last run
            }
            nextSequence = recovered.get(recovered.size() - 1) + 1;
        }
        this.activeChunkSequence = nextSequence;
    }

    void add(@NonNull String json) {
        synchronized (lock) {
            activeChunk.add(json);
            rotateIfFull();
        }
    }

    void addAll(@NonNull List<String> items) {
        if (items.isEmpty()) {
            return;
        }

        synchronized (lock) {
            for (String item : items) {
                activeChunk.add(item);
                rotateIfFull();
            }
        }
    }

    /**
     * Whether there's anything - persisted or not - currently waiting to be uploaded.
     */
    boolean hasPending() {
        synchronized (lock) {
            return !activeChunk.isEmpty() || !closedChunks.isEmpty();
        }
    }

    /**
     * Hands out the oldest available batch - a closed chunk if one exists, otherwise whatever's
     * in the active chunk - without touching disk on its own. Ownership passes to the caller until
     * it reports back via {@link #confirmSent} or {@link #requeue}. Taking the active chunk always
     * starts a fresh one behind it (with a new identity), so entries added afterward never get
     * mixed up with this batch.
     */
    @NonNull
    TakenBatch takeBatch() {
        synchronized (lock) {
            final Long oldestClosed = closedChunks.peekFirst();
            if (oldestClosed != null) {
                return TakenBatch.fromClosedChunk(readAllLines(chunkFile(oldestClosed)), oldestClosed);
            }

            if (activeChunk.isEmpty()) {
                return TakenBatch.empty();
            }

            final List<String> items = new ArrayList<>(activeChunk);
            final int persisted = activeChunkPersistedCount;
            final long sequence = activeChunkSequence;

            activeChunk.clear();
            activeChunkPersistedCount = 0;
            activeChunkSequence = sequence + 1;

            return TakenBatch.fromActiveChunk(items, sequence, persisted);
        }
    }

    /**
     * Reports that a batch obtained from {@link #takeBatch} was successfully uploaded. If it never
     * touched disk (the common case on a healthy connection), this is a no-op; otherwise its file
     * is deleted.
     */
    void confirmSent(@NonNull TakenBatch batch) {
        synchronized (lock) {
            if (batch.fromClosedChunk) {
                closedChunks.remove(batch.sequence);
                deleteChunkFile(batch.sequence);
            } else if (batch.persistedCount > 0) {
                deleteChunkFile(batch.sequence);
            }
        }
    }

    /**
     * Reports that a batch obtained from {@link #takeBatch} failed to upload, so it needs a later
     * retry. A closed-chunk batch needs no change at all - its file, if still present, is already
     * exactly where it needs to be, still the oldest thing pending. An active-chunk batch's
     * sequence was already permanently retired when it was taken, so it can't merge back into
     * whatever the (now different) active chunk has become in the meantime; instead it's persisted
     * (whatever part of it wasn't already durable) and registered as its own closed chunk, at the
     * front since it's the oldest data around.
     */
    void requeue(@NonNull TakenBatch batch) {
        synchronized (lock) {
            if (batch.fromClosedChunk) {
                return;
            }

            if (walDir == null) {
                // Nothing durable to fall back to - put the actual content straight back into
                // memory instead of registering a chunk with no file behind it.
                activeChunk.addAll(0, batch.items);
                rotateIfFull();
                return;
            }

            if (batch.persistedCount < batch.items.size()) {
                appendLines(chunkFile(batch.sequence), batch.items.subList(batch.persistedCount, batch.items.size()));
            }

            closedChunks.addFirst(batch.sequence);
            evictExcessChunks();
        }
    }

    /**
     * Forces the active chunk to disk right now, regardless of upload state - without rotating it
     * out. Meant to be called from a crash handler or a background-transition hook, where there's
     * no time left to wait for a normal upload cycle.
     */
    void persistNow() {
        synchronized (lock) {
            persistActiveChunkLocked();
        }
    }

    // must be called while already holding `lock`
    private void rotateIfFull() {
        if (walDir == null) {
            // No disk to hold "closed" chunks on - just cap the total in-memory size directly,
            // dropping the oldest as needed, the same way a single-buffer store would.
            final int overflow = activeChunk.size() - entriesPerFile * maxFiles;
            if (overflow > 0) {
                activeChunk.subList(0, overflow).clear();
            }
            return;
        }

        if (activeChunk.size() >= entriesPerFile) {
            closeActiveChunk();
        }
    }

    // must be called while already holding `lock`
    private void closeActiveChunk() {
        persistActiveChunkLocked();
        closedChunks.addLast(activeChunkSequence);
        activeChunk.clear();
        activeChunkPersistedCount = 0;
        activeChunkSequence++;
        evictExcessChunks();
    }

    // must be called while already holding `lock`
    private void persistActiveChunkLocked() {
        if (activeChunkPersistedCount >= activeChunk.size()) {
            return;
        }
        appendLines(chunkFile(activeChunkSequence), activeChunk.subList(activeChunkPersistedCount, activeChunk.size()));
        activeChunkPersistedCount = activeChunk.size();
    }

    // must be called while already holding `lock`
    private void evictExcessChunks() {
        while (closedChunks.size() > maxFiles) {
            final Long oldest = closedChunks.pollFirst();
            if (oldest != null) {
                deleteChunkFile(oldest);
                Log.w(TAG, "Pending chunk limit (" + maxFiles + " files) exceeded - dropped oldest chunk "
                        + oldest + " (up to " + entriesPerFile + " entries)");
            }
        }
    }

    /**
     * A batch of entries taken via {@link #takeBatch}: either a full closed chunk read from disk,
     * or a snapshot of what the active chunk held (with how much of it, if any, was already
     * durable at take time).
     */
    static final class TakenBatch {
        @NonNull
        final List<String> items;
        final long sequence;
        final boolean fromClosedChunk;
        final int persistedCount;

        private TakenBatch(@NonNull List<String> items, long sequence, boolean fromClosedChunk, int persistedCount) {
            this.items = items;
            this.sequence = sequence;
            this.fromClosedChunk = fromClosedChunk;
            this.persistedCount = persistedCount;
        }

        @NonNull
        static TakenBatch empty() {
            return new TakenBatch(Collections.emptyList(), -1, false, 0);
        }

        @NonNull
        static TakenBatch fromClosedChunk(@NonNull List<String> items, long sequence) {
            return new TakenBatch(items, sequence, true, items.size());
        }

        @NonNull
        static TakenBatch fromActiveChunk(@NonNull List<String> items, long sequence, int persistedCount) {
            return new TakenBatch(items, sequence, false, persistedCount);
        }

        boolean isEmpty() {
            return items.isEmpty();
        }
    }

    // --- Chunk file naming/discovery -----------------------------------------------------------

    @Nullable
    private File chunkFile(long sequence) {
        if (walDir == null) {
            return null;
        }
        return new File(walDir, String.format(Locale.US, "%06d%s", sequence, CHUNK_FILE_SUFFIX));
    }

    private void deleteChunkFile(long sequence) {
        final File file = chunkFile(sequence);
        if (file != null && file.exists() && !file.delete()) {
            Log.w(TAG, "Unable to delete: " + file.getAbsolutePath());
        }
    }

    @NonNull
    private static List<Long> listChunkSequences(@Nullable File dir) {
        final List<Long> sequences = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) {
            return sequences;
        }

        final File[] files = dir.listFiles();
        if (files == null) {
            return sequences;
        }

        for (File file : files) {
            final String name = file.getName();
            if (!name.endsWith(CHUNK_FILE_SUFFIX)) {
                continue;
            }
            try {
                sequences.add(Long.parseLong(name.substring(0, name.length() - CHUNK_FILE_SUFFIX.length())));
            } catch (NumberFormatException ignored) {
                // not one of our chunk files - skip
            }
        }

        Collections.sort(sequences);
        return sequences;
    }

    // --- Plain single-file I/O helpers ---------------------------------------------------------

    private static void appendLines(@Nullable File file, @NonNull List<String> lines) {
        if (file == null || lines.isEmpty()) {
            return;
        }

        try {
            final File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                throw new IOException("Could not create " + parent);
            }

            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to persist pending entries: " + e.getMessage());
        }
    }

    @NonNull
    private static List<String> readAllLines(@Nullable File file) {
        final List<String> lines = new ArrayList<>();
        if (file == null || !file.exists()) {
            return lines;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read pending store: " + e.getMessage());
        }

        return lines;
    }
}
