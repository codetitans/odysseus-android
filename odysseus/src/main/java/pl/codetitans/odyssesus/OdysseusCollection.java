package pl.codetitans.odyssesus;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically uploads batches of entries to the Odysseus Logging Platform over HTTP.
 * <p>
 * Entry storage/persistence is entirely delegated to {@link OdysseusStore} - this class only
 * decides when to attempt an upload and does the actual network call, handing a batch to the
 * store's {@link OdysseusStore#takeBatch()} and reporting the outcome back via
 * {@link OdysseusStore#confirmSent}/{@link OdysseusStore#requeue}.
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
    private final OdysseusStore store;
    private final URL baseUrl;
    private final String endPoint;
    private final int delaySeconds;
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
        this.store = new OdysseusStore(walFile);

        // pick up anything the store recovered from a previous session
        if (store.hasPending()) {
            scheduleFlush();
        }
    }

    void add(@NonNull String json) {
        store.add(json);
        scheduleFlush();
    }

    void addAll(@NonNull List<String> items) {
        if (items.isEmpty()) {
            return;
        }

        store.addAll(items);
        scheduleFlush();
    }

    /**
     * Forces everything currently buffered to durable storage right now, bypassing the normal
     * "only persist if the upload didn't go through" flow. Meant to be called from a crash
     * handler, where there's no time left for a normal upload cycle.
     */
    void persistPendingNow() {
        store.persistNow();
    }

    private void scheduleFlush() {
        synchronized (lock) {
            if (!scheduled) {
                scheduled = true;
                EXECUTOR.schedule(this::flush, delaySeconds, TimeUnit.SECONDS);
            }
        }
    }

    private void flush() {
        synchronized (lock) {
            scheduled = false;
        }

        final OdysseusStore.TakenBatch batch = store.takeBatch();
        if (batch.isEmpty()) {
            return;
        }

        if (upload(batch.items)) {
            // never touched disk on a healthy connection - store.confirmSent() is a no-op then
            store.confirmSent(batch);
        } else {
            // retried on a later flush - store persists whatever wasn't already durable
            store.requeue(batch);
        }

        if (store.hasPending()) {
            scheduleFlush();
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
}
