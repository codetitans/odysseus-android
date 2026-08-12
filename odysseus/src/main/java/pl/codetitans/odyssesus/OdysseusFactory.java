package pl.codetitans.odyssesus;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class OdysseusFactory {

    private static volatile OdysseusClient client;

    /**
     * Gets the shared instance of the Odysseus Logger Client.
     */
    public static OdysseusClient getInstance() {
        return client;
    }

    /**
     * Setup new instance the Odysseus Client.
     * <p>
     * Unsubmitted entries only live in memory with this overload - prefer the
     * {@link Context}-accepting overloads, which persist unsubmitted entries to disk and
     * automatically retry them (including after a crash) on the next {@code start()}.
     */
    public static synchronized OdysseusClient start(@NonNull String appId, @NonNull String appKey) {
        return client = new OdysseusClient(appId, appKey);
    }

    /**
     * Setup new instance the Odysseus Client. See the in-memory-only caveat on {@link #start(String, String)}.
     */
    public static synchronized OdysseusClient start(@NonNull String appId, @NonNull String appKey, short platform) {
        return client = new OdysseusClient(appId, appKey, platform);
    }

    /**
     * Setup new instance the Odysseus Client. See the in-memory-only caveat on {@link #start(String, String)}.
     */
    public static synchronized OdysseusClient start(@NonNull String appId, @NonNull String appKey, int delaySeconds, @NonNull LogSeverity minSeverity, short platform, @Nullable String host) {
        return client = new OdysseusClient(appId, appKey, delaySeconds, minSeverity, platform, host);
    }

    /**
     * Setup new instance of the Odysseus Client, persisting unsubmitted log entries and events to
     * disk (under {@code context}'s no-backup storage) and installing an automatic crash handler.
     * See {@link OdysseusClient#OdysseusClient(Context, String, String)}.
     */
    public static synchronized OdysseusClient start(@NonNull Context context, @NonNull String appId, @NonNull String appKey) {
        return client = new OdysseusClient(context, appId, appKey);
    }

    /**
     * Setup new instance of the Odysseus Client. See {@link #start(Context, String, String)}.
     */
    public static synchronized OdysseusClient start(@NonNull Context context, @NonNull String appId, @NonNull String appKey, short platform) {
        return client = new OdysseusClient(context, appId, appKey, platform);
    }

    /**
     * Setup new instance of the Odysseus Client. See {@link #start(Context, String, String)}.
     */
    public static synchronized OdysseusClient start(@NonNull Context context, @NonNull String appId, @NonNull String appKey, int delaySeconds, @NonNull LogSeverity minSeverity, short platform, @Nullable String host) {
        return client = new OdysseusClient(context, appId, appKey, delaySeconds, minSeverity, platform, host);
    }

    /**
     * Releases the instance of Odysseus Client, restoring any uncaught-exception handler it chained in front of.
     */
    public static synchronized void stop() {
        final OdysseusClient c = client;
        if (c != null) {
            c.shutdown();
        }
        client = null;
    }

    /**
     * Creates new logger for a given tag and associates it with the currently initialized client.
     */
    public static IOdysseusLog create(@Nullable String tag) {
        final OdysseusClient c = client;
        if (c == null) {
            throw new OdysseusException("Client is not initialized");
        }

        return new OdysseusLogger(c, tag);
    }
}
