package pl.codetitans.odyssesus;

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
     */
    public static synchronized OdysseusClient start(@NonNull String appId, @NonNull String appKey) {
        return client = new OdysseusClient(appId, appKey);
    }

    /**
     * Setup new instance the Odysseus Client.
     */
    public static synchronized OdysseusClient start(@NonNull String appId, @NonNull String appKey, short platform) {
        return client = new OdysseusClient(appId, appKey, platform);
    }

    /**
     * Setup new instance the Odysseus Client.
     */
    public static synchronized OdysseusClient start(@NonNull String appId, @NonNull String appKey, int delaySeconds, @NonNull LogSeverity minSeverity, short platform, @Nullable String host) {
        return client = new OdysseusClient(appId, appKey, delaySeconds, minSeverity, platform, host);
    }

    /**
     * Releases the instance of Odysseus Client.
     */
    public static synchronized void stop() {
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
