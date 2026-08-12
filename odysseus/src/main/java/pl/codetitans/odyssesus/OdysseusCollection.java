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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Buffers pre-serialized JSON entries in memory and periodically uploads them in batches to the
 * Odysseus Logging Platform.
 * <p>
 * When constructed with a write-ahead file, every entry is durably appended to that file before
 * {@link #add}/{@link #addAll} return, and is only removed from it once its batch has been
 * confirmed uploaded. That way, entries survive the process dying (crash, kill, no network) before
 * a batch could be sent - the next {@code OdysseusCollection} created against the same file (i.e.
 * on the next app launch) picks them back up and retries automatically.
 */
final class OdysseusCollection {
    private static final String TAG = "OdysseusCollection";
    private static final String DEFAULT_HOST = "https://odysseus.codetitans.dev";
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 15_000;

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "OdysseusUploader");
        thread.setDaemon(true);
        return thread;
    });

    private final Object lock = new Object();
    private final List<String> entries = new ArrayList<>();
    private final URL baseUrl;
    private final String endPoint;
    private final int delaySeconds;
    @Nullable
    private final File walFile;
    private boolean scheduled;

    OdysseusCollection(@NonNull String endPoint, int delaySeconds, @Nullable File walFile) {
        this(null, endPoint, delaySeconds, walFile);
    }

    OdysseusCollection(@Nullable String host, @NonNull String endPoint, int delaySeconds, @Nullable File walFile) {
        try {
            this.baseUrl = new URL(host == null || host.isEmpty() ? DEFAULT_HOST : host);
        } catch (MalformedURLException e) {
            throw new OdysseusException("Invalid Odysseus host: " + host, e);
        }

        this.endPoint = endPoint;
        this.delaySeconds = Math.max(delaySeconds, 1);
        this.walFile = walFile;

        // Recover anything left over from a previous process (crash, kill, no network, ...) - this
        // is the only place recovery needs to happen, since from here on the file and the
        // in-memory queue are always kept in lock-step by add()/addAll()/flush().
        final List<String> recovered = readAllLines(walFile);
        if (!recovered.isEmpty()) {
            Log.i(TAG, "Recovered " + recovered.size() + " unsent entries from a previous session");
            synchronized (lock) {
                entries.addAll(recovered);
                scheduleFlush();
            }
        }
    }

    void add(@NonNull String json) {
        synchronized (lock) {
            appendLines(walFile, Collections.singletonList(json));
            entries.add(json);
            scheduleFlush();
        }
    }

    void addAll(@NonNull List<String> items) {
        if (items.isEmpty()) {
            return;
        }

        synchronized (lock) {
            appendLines(walFile, items);
            entries.addAll(items);
            scheduleFlush();
        }
    }

    private void scheduleFlush() {
        if (!scheduled) {
            scheduled = true;
            EXECUTOR.schedule(this::flush, delaySeconds, TimeUnit.SECONDS);
        }
    }

    private void flush() {
        final List<String> toUpload;
        synchronized (lock) {
            toUpload = new ArrayList<>(entries);
            entries.clear();
            scheduled = false;
        }

        if (toUpload.isEmpty()) {
            return;
        }

        if (upload(toUpload)) {
            // Only the first toUpload.size() lines belong to this batch - anything appended to
            // the file by add()/addAll() while the upload was in flight must be kept.
            synchronized (lock) {
                removeFirstLines(walFile, toUpload.size());
            }
        } else {
            // Put the entries back at the front of the queue and retry on the next flush. The WAL
            // file already holds them (plus anything added meanwhile), so nothing to do there.
            synchronized (lock) {
                entries.addAll(0, toUpload);
            }
        }

        synchronized (lock) {
            if (!entries.isEmpty()) {
                scheduleFlush();
            }
        }
    }

    private boolean upload(@NonNull List<String> items) {
        Log.i(TAG, "Sending OdysseusLogs[" + items.size() + "]");

        HttpURLConnection connection = null;
        try {
            final byte[] body = toJsonArray(items).getBytes(StandardCharsets.UTF_8);

            connection = (HttpURLConnection) new URL(baseUrl, endPoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setFixedLengthStreamingMode(body.length);

            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }

            final int status = connection.getResponseCode();
            return status >= 200 && status < 300;
        } catch (IOException e) {
            Log.e(TAG, "Failed to submit: " + e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @NonNull
    private static String toJsonArray(@NonNull List<String> items) {
        final StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(items.get(i));
        }
        sb.append(']');
        return sb.toString();
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
