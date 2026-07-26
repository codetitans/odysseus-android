package pl.codetitans.odyssesus;

import androidx.annotation.NonNull;

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
    public static synchronized OdysseusClient start(@NonNull String appId, @NonNull String appKey, short platform, int delaySeconds, @NonNull LogSeverity minSeverity) {
        return client = new OdysseusClient(appId, appKey, platform, delaySeconds, minSeverity);
    }

    /**
     * Releases the instance of Odysseus Client.
     */
    public static synchronized void stop() {
        client = null;
    }
}
