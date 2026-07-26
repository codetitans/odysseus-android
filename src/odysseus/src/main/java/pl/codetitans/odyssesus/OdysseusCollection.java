package pl.codetitans.odyssesus;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Buffers entries in memory and periodically uploads them in batches to the Odysseus Logging Platform.
 */
final class OdysseusCollection<T extends OdysseusJsonEntry> {
    private static final String DEFAULT_HOST = "https://odysseus.codetitans.dev";
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 15_000;

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "OdysseusUploader");
        thread.setDaemon(true);
        return thread;
    });

    private final Object lock = new Object();
    private final List<T> entries = new ArrayList<>();
    private final URL baseUrl;
    private final String endPoint;
    private final int delaySeconds;
    private boolean scheduled;

    OdysseusCollection(@NonNull String endPoint, int delaySeconds) {
        this(DEFAULT_HOST, endPoint, delaySeconds);
    }

    OdysseusCollection(@NonNull String host, @NonNull String endPoint, int delaySeconds) {
        try {
            this.baseUrl = new URL(host);
        } catch (MalformedURLException e) {
            throw new OdysseusException("Invalid Odysseus host: " + host, e);
        }

        this.endPoint = endPoint;
        this.delaySeconds = Math.max(delaySeconds, 1);
    }

    void add(@NonNull T item) {
        synchronized (lock) {
            entries.add(item);
            scheduleFlush();
        }
    }

    void addAll(@NonNull List<T> items) {
        synchronized (lock) {
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
        final List<T> toUpload;
        synchronized (lock) {
            toUpload = new ArrayList<>(entries);
            entries.clear();
            scheduled = false;
        }

        if (toUpload.isEmpty()) {
            return;
        }

        if (!upload(toUpload)) {
            // put the entries back at the front of the queue and retry on the next flush
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

    private boolean upload(@NonNull List<T> items) {
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
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @NonNull
    private static <T extends OdysseusJsonEntry> String toJsonArray(@NonNull List<T> items) {
        final StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            items.get(i).writeJson(sb);
        }
        sb.append(']');
        return sb.toString();
    }
}
