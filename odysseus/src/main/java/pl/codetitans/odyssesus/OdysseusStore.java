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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Owns the in-memory queue of pre-serialized JSON entries waiting to be uploaded, and - when
 * constructed with a write-ahead file - their durable backing on disk.
 * <p>
 * Entries are persisted to that file in batches, not one write per {@link #add}/{@link #addAll}
 * call (that would mean one disk write per log line), and are only removed from it once their
 * batch has been confirmed uploaded via {@link #confirmSent}. That way, entries survive the
 * process dying (crash, kill, no network) before a batch could be sent - the next
 * {@code OdysseusStore} constructed against the same file (i.e. on the next app launch) picks them
 * back up automatically.
 * <p>
 * Persistence is coalesced two ways: a short debounce ({@link #PERSIST_DEBOUNCE_MILLIS}) batches
 * bursts of adds into a single write, and {@link #takeBatch} always persists whatever's still
 * unwritten right before handing a batch off for upload - so the on-disk copy is never more than
 * one short debounce (or one upload cycle, whichever is sooner) behind memory. A size-based
 * fallback ({@link #PERSIST_MAX_BUFFERED}) also forces an early write if a burst of adds outruns
 * the debounce, bounding both memory use and the worst-case loss window.
 * <p>
 * This class only manages storage - it knows nothing about the network. Callers hand a batch off
 * via {@link #takeBatch} and report the outcome back via {@link #confirmSent} or {@link #requeue}.
 */
final class OdysseusStore {
    private static final String TAG = "OdysseusStore";
    private static final long PERSIST_DEBOUNCE_MILLIS = 2_000;
    private static final int PERSIST_MAX_BUFFERED = 200;

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "OdysseusStore");
        thread.setDaemon(true);
        return thread;
    });

    private final Object lock = new Object();
    private final List<String> entries = new ArrayList<>();
    @Nullable
    private final File walFile;
    private boolean persistScheduled;
    // entries[0, persistedCount) are already durably written to walFile; the rest is only in memory.
    private int persistedCount;

    OdysseusStore(@Nullable File walFile) {
        this.walFile = walFile;

        // Recover anything left over from a previous process (crash, kill, no network, ...) - this
        // is the only place recovery needs to happen, since from here on the file and the
        // in-memory queue are always kept in lock-step by add()/addAll()/takeBatch()/requeue().
        final List<String> recovered = readAllLines(walFile);
        if (!recovered.isEmpty()) {
            Log.i(TAG, "Recovered " + recovered.size() + " unsent entries from a previous session");
            synchronized (lock) {
                entries.addAll(recovered);
                persistedCount = recovered.size(); // already on disk - it's where we just read them from
            }
        }
    }

    void add(@NonNull String json) {
        synchronized (lock) {
            entries.add(json);
            onEntriesAdded();
        }
    }

    void addAll(@NonNull List<String> items) {
        if (items.isEmpty()) {
            return;
        }

        synchronized (lock) {
            entries.addAll(items);
            onEntriesAdded();
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
     * Snapshots everything currently buffered (persisting any not-yet-written entries first) and
     * clears the in-memory queue, handing ownership of that batch to the caller until it reports
     * back via {@link #confirmSent} or {@link #requeue}. Entries added while the batch is still
     * outstanding accumulate separately and are unaffected.
     */
    @NonNull
    List<String> takeBatch() {
        synchronized (lock) {
            persistPending();

            final List<String> batch = new ArrayList<>(entries);
            entries.clear();
            persistedCount = 0;
            return batch;
        }
    }

    /**
     * Reports that a batch obtained from {@link #takeBatch} was successfully uploaded, so it can
     * be dropped from the durable store.
     */
    void confirmSent(@NonNull List<String> batch) {
        synchronized (lock) {
            // Only the first batch.size() lines belong to this batch - anything appended to the
            // file (by add()/addAll(), via persistPending()) while the upload was in flight must
            // be kept.
            removeFirstLines(walFile, batch.size());
        }
    }

    /**
     * Reports that a batch obtained from {@link #takeBatch} failed to upload, so it's put back at
     * the front of the queue for a later retry.
     */
    void requeue(@NonNull List<String> batch) {
        synchronized (lock) {
            // The WAL file already holds it (takeBatch()'s persistPending() guaranteed that) plus
            // anything added meanwhile, so the file itself needs no change - just extend the
            // persisted prefix to cover the entries we're putting back in front of it.
            entries.addAll(0, batch);
            persistedCount += batch.size();
        }
    }

    // must be called while already holding `lock`
    private void onEntriesAdded() {
        if (entries.size() - persistedCount >= PERSIST_MAX_BUFFERED) {
            // a burst outran the debounce below - write now rather than let unpersisted entries
            // (and the loss window they represent) grow unbounded
            persistPending();
        } else {
            schedulePersist();
        }
    }

    // must be called while already holding `lock`
    private void schedulePersist() {
        if (!persistScheduled) {
            persistScheduled = true;
            EXECUTOR.schedule(this::persistPendingTick, PERSIST_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    private void persistPendingTick() {
        synchronized (lock) {
            persistScheduled = false;
            persistPending();
        }
    }

    // must be called while already holding `lock`
    private void persistPending() {
        if (persistedCount < entries.size()) {
            appendLines(walFile, entries.subList(persistedCount, entries.size()));
            persistedCount = entries.size();
        }
    }

    // --- Write-ahead file persistence, so unsent entries survive the process dying -----------

    private static void appendLines(@Nullable File file, @NonNull List<String> lines) {
        if (file == null) {
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
