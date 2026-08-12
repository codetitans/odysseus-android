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
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the in-memory queue of pre-serialized JSON entries waiting to be uploaded, and - when
 * constructed with a write-ahead file - their durable backing on disk.
 * <p>
 * Disk is only touched when it's actually needed: {@link #takeBatch} hands a snapshot straight out
 * of memory, with no write beforehand. If the upload succeeds, {@link #confirmSent} is a no-op for
 * a batch that never touched disk - on a healthy connection, entries can go from {@link #add} to
 * "successfully delivered" without a single disk write. Only {@link #requeue} (the upload failed)
 * and {@link #persistNow} (a crash was detected - there's no time left for a normal upload cycle)
 * actually write anything, and only what isn't already durable.
 * <p>
 * Entries are only removed from the file once their batch has been confirmed uploaded via
 * {@link #confirmSent}. That way, entries survive the process dying (crash, kill, no network)
 * after being persisted - the next {@code OdysseusStore} constructed against the same file (i.e.
 * on the next app launch) picks them back up automatically.
 * <p>
 * This class only manages storage - it knows nothing about the network. Callers hand a batch off
 * via {@link #takeBatch} and report the outcome back via {@link #confirmSent} or {@link #requeue}.
 * <p>
 * At most {@link #MAX_ENTRIES} entries are ever held (in memory and on disk combined) - if adding
 * more would exceed that, the oldest entries are dropped to make room, so a very long stretch
 * without a connection can't grow the pending queue (or the file backing it) without bound.
 */
final class OdysseusStore {
    private static final String TAG = "OdysseusStore";
    private static final int MAX_ENTRIES = 2_000;

    private final Object lock = new Object();
    private final List<String> entries = new ArrayList<>();
    @Nullable
    private final File walFile;
    // entries[0, persistedCount) are already durably written to walFile; the rest is only in memory.
    private int persistedCount;

    OdysseusStore(@Nullable File walFile) {
        this.walFile = walFile;

        // Recover anything left over from a previous process (crash, kill, no network, ...) - this
        // is the only place recovery needs to happen, since from here on the file and the
        // in-memory queue are always kept in lock-step by takeBatch()/confirmSent()/requeue()/persistNow().
        final List<String> recovered = readAllLines(walFile);
        if (!recovered.isEmpty()) {
            Log.i(TAG, "Recovered " + recovered.size() + " unsent entries from a previous session");
            synchronized (lock) {
                entries.addAll(recovered);
                persistedCount = recovered.size(); // already on disk - it's where we just read them from
                enforceLimit();
            }
        }
    }

    void add(@NonNull String json) {
        synchronized (lock) {
            entries.add(json);
            enforceLimit();
        }
    }

    void addAll(@NonNull List<String> items) {
        if (items.isEmpty()) {
            return;
        }

        synchronized (lock) {
            entries.addAll(items);
            enforceLimit();
        }
    }

    /**
     * Whether there's anything - persisted or not - currently waiting to be uploaded.
     */
    boolean hasPending() {
        synchronized (lock) {
            return !entries.isEmpty();
        }
    }

    /**
     * Snapshots everything currently buffered - without touching disk - and clears the in-memory
     * queue, handing ownership of that batch to the caller until it reports back via
     * {@link #confirmSent} or {@link #requeue}. Entries added while the batch is still outstanding
     * accumulate separately and are unaffected.
     */
    @NonNull
    TakenBatch takeBatch() {
        synchronized (lock) {
            final List<String> batch = new ArrayList<>(entries);
            final int batchPersistedCount = persistedCount;
            entries.clear();
            persistedCount = 0;
            return new TakenBatch(batch, batchPersistedCount);
        }
    }

    /**
     * Reports that a batch obtained from {@link #takeBatch} was successfully uploaded. If it never
     * touched disk (the common case on a healthy connection), this is a no-op; otherwise it drops
     * the now-confirmed prefix from the durable store.
     */
    void confirmSent(@NonNull TakenBatch batch) {
        if (batch.persistedCount <= 0) {
            return;
        }

        synchronized (lock) {
            removeFirstLines(walFile, batch.persistedCount);
        }
    }

    /**
     * Reports that a batch obtained from {@link #takeBatch} failed to upload: persists whatever
     * part of it isn't already durable, then puts the whole batch back at the front of the queue
     * for a later retry.
     */
    void requeue(@NonNull TakenBatch batch) {
        synchronized (lock) {
            if (batch.persistedCount < batch.items.size()) {
                appendLines(walFile, batch.items.subList(batch.persistedCount, batch.items.size()));
            }

            entries.addAll(0, batch.items);
            persistedCount = batch.items.size();
            enforceLimit();
        }
    }

    // must be called while already holding `lock`
    private void enforceLimit() {
        final int overflow = entries.size() - MAX_ENTRIES;
        if (overflow <= 0) {
            return;
        }

        // Drop the oldest `overflow` entries to make room. If any of them were already durably
        // persisted, trim the same count from the front of the file too, so it stays in lock-step
        // with memory instead of accumulating entries we've decided to discard.
        final int droppedPersisted = Math.min(overflow, persistedCount);
        if (droppedPersisted > 0) {
            removeFirstLines(walFile, droppedPersisted);
        }

        entries.subList(0, overflow).clear();
        persistedCount = Math.max(0, persistedCount - overflow);

        Log.w(TAG, "Pending queue exceeded " + MAX_ENTRIES + " entries - dropped the oldest " + overflow);
    }

    /**
     * Forces everything currently buffered to disk right now, regardless of upload state. Meant to
     * be called from a crash handler, where there's no time left to wait for a normal upload cycle.
     */
    void persistNow() {
        synchronized (lock) {
            if (persistedCount < entries.size()) {
                appendLines(walFile, entries.subList(persistedCount, entries.size()));
                persistedCount = entries.size();
            }
        }
    }

    /**
     * A batch of entries taken via {@link #takeBatch}, along with how many of its leading entries
     * were already durable on disk at the time it was taken.
     */
    static final class TakenBatch {
        @NonNull
        final List<String> items;
        final int persistedCount;

        TakenBatch(@NonNull List<String> items, int persistedCount) {
            this.items = items;
            this.persistedCount = persistedCount;
        }

        boolean isEmpty() {
            return items.isEmpty();
        }
    }

    // --- Write-ahead file persistence, so unsent entries survive the process dying -----------

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

    private static void removeFirstLines(@Nullable File file, int count) {
        if (file == null || count <= 0 || !file.exists()) {
            return;
        }

        try {
            final List<String> remaining = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                int skipped = 0;
                while ((line = reader.readLine()) != null) {
                    if (skipped < count) {
                        skipped++;
                        continue;
                    }
                    remaining.add(line);
                }
            }

            if (remaining.isEmpty()) {
                if (!file.delete()) {
                    Log.w(TAG, "Unable to delete: " + file.getAbsolutePath());
                }
                return;
            }

            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8))) {
                for (String line : remaining) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to trim pending store: " + e.getMessage());
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
