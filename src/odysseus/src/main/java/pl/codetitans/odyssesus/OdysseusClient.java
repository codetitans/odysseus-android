package pl.codetitans.odyssesus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Client object to provide Odysseus Logging Platform capabilities for the Android projects.
 */
public final class OdysseusClient {
    private static State state;

    /**
     * Initializes the instance to connect as given application.
     */
    public static void start(@NonNull String appId, @NonNull String appKey) {
        OdysseusClient.state = new State(appId, appKey);
    }

    /**
     * Deinitializes the running instance.
     */
    public static void stop() {
        OdysseusClient.state = null;
    }

    /**
     * Checks, whether the client has been initialized to work in context of a given application.
     */
    public static boolean isStarted() {
        return OdysseusClient.state != null;
    }

    /**
     * Sets or unsets the user associated with all following log entries or events.
     */
    public static void setUser(@Nullable String user) {
        final State s = OdysseusClient.state;
        if (s == null) {
            throw new OdysseusException("Client not initialized yet");
        }

        s.setUser(user);
    }

    /**
     * Gets the information about current user associated with log entries or events.
     */
    @Nullable
    public static String getUser() {
        final State s = OdysseusClient.state;
        return s != null ? s.getUser() : null;
    }

    /**
     * Stores the log entry internally and later on submits it to the cloud, when possible.
     */
    @Nullable
    public OdysseusLogEntry add(OdysseusLogEntry entry) {
        // TODO: store and later submit

        return entry;
    }

    /**
     * Stores the log entries internally and later on submits it to the cloud, when possible.
     */
    @Nullable
    public List<OdysseusLogEntry> addAllLogs(List<OdysseusLogEntry> entries) {
        // TODO: store and later submit

        return entries;
    }

    /**
     * Stores the event entry internally and later on submits it to the cloud, when possible.
     */
    @Nullable
    public OdysseusEventEntry add(OdysseusEventEntry event) {
        // TODO: store and later submit

        return event;
    }

    /**
     * Stores the event entries internally and later on submits it to the cloud, when possible.
     */
    @Nullable
    public List<OdysseusEventEntry> addAllEvents(List<OdysseusEventEntry> events) {
        // TODO: store and later submit

        return events;
    }

    private final static class State {
        private final String appId;
        private final String appKey;
        private String user;

        public State(@NonNull String appId, @NonNull String appKey) {
            this.appId = appId;
            this.appKey = appKey;
        }

        @NonNull
        public String getAppId() {
            return appId;
        }

        @NonNull
        public String getAppKey() {
            return appKey;
        }

        public void setUser(@Nullable String user) {
            this.user = user;
        }

        @Nullable
        public String getUser() {
            return user;
        }
    }
}
