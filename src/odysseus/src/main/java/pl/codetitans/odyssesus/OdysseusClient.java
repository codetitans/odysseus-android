package pl.codetitans.odyssesus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client object to provide Odysseus Logging Platform capabilities for the Android projects.
 */
public final class OdysseusClient {
    public static final int DEFAULT_DELAY_SECONDS = 5;
    public static final short DEFAULT_PLATFORM = 7;

    private static State state;

    /**
     * Initializes the instance to connect as given application.
     */
    public static void start(@NonNull String appId, @NonNull String appKey) {
        start(appId, appKey, DEFAULT_PLATFORM, DEFAULT_DELAY_SECONDS);
    }

    /**
     * Initializes the instance to connect as given application, with a custom upload batching delay.
     */
    public static void start(@NonNull String appId, @NonNull String appKey, short platform, int delaySeconds) {
        OdysseusClient.state = new State(appId, appKey, platform, delaySeconds);
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
        requireState().user = user;
    }

    /**
     * Gets the information about current user associated with log entries or events.
     */
    @Nullable
    public static String getUser() {
        final State s = OdysseusClient.state;
        return s != null ? s.user : null;
    }

    /**
     * Sets the minimal log severity, below which log entries get discarded instead of uploaded.
     */
    public static void setMinSeverity(@NonNull LogSeverity severity) {
        requireState().minSeverity = severity;
    }

    /**
     * Gets the minimal log severity, below which log entries get discarded instead of uploaded.
     */
    @NonNull
    public static LogSeverity getMinSeverity() {
        final State s = OdysseusClient.state;
        return s != null ? s.minSeverity : LogSeverity.DEBUG;
    }

    /**
     * Sets or unsets the platform identifier associated with all following log entries or events.
     */
    public static void setPlatform(short platform) {
        requireState().platform = platform;
    }

    /**
     * Gets the platform identifier associated with log entries or events.
     */
    public static short getPlatform() {
        final State s = OdysseusClient.state;
        return s != null ? s.platform : (short) 0;
    }

    /**
     * Gets the identifier of the current session, generated once per {@link #start}.
     */
    @NonNull
    public static UUID getSessionId() {
        return requireState().sessionId;
    }

    /**
     * Stores the log entry internally and later on submits it to the cloud, when possible.
     * Returns null, if the entry's severity is below the currently configured minimal severity.
     */
    @Nullable
    public static OdysseusLogEntry add(@NonNull OdysseusLogEntry entry) {
        final State s = requireState();
        if (entry.getSeverity() < s.minSeverity.getValue()) {
            return null;
        }

        s.logs.add(entry);
        return entry;
    }

    /**
     * Stores the log entries internally and later on submits it to the cloud, when possible.
     * Returns only the entries that were accepted (severity not below the currently configured minimal severity).
     */
    @NonNull
    public static List<OdysseusLogEntry> addAllLogs(@NonNull List<OdysseusLogEntry> entries) {
        final State s = requireState();
        final List<OdysseusLogEntry> accepted = new ArrayList<>();

        for (OdysseusLogEntry entry : entries) {
            if (entry.getSeverity() >= s.minSeverity.getValue()) {
                accepted.add(entry);
            }
        }

        s.logs.addAll(accepted);
        return accepted;
    }

    /**
     * Stores the event entry internally and later on submits it to the cloud, when possible.
     */
    @NonNull
    public static OdysseusEventEntry add(@NonNull OdysseusEventEntry event) {
        final State s = requireState();
        s.events.add(event);
        return event;
    }

    /**
     * Stores the event entries internally and later on submits it to the cloud, when possible.
     */
    @NonNull
    public static List<OdysseusEventEntry> addAllEvents(@NonNull List<OdysseusEventEntry> events) {
        final State s = requireState();
        s.events.addAll(events);
        return events;
    }

    @NonNull
    private static State requireState() {
        final State s = OdysseusClient.state;
        if (s == null) {
            throw new OdysseusException("Client not initialized yet");
        }

        return s;
    }

    private final static class State {
        private final UUID sessionId = UUID.randomUUID();
        private final OdysseusCollection<OdysseusLogEntry> logs;
        private final OdysseusCollection<OdysseusEventEntry> events;
        private String user;
        private LogSeverity minSeverity = LogSeverity.DEBUG;
        private short platform;

        State(@NonNull String appId, @NonNull String appKey, short platform, int delaySeconds) {
            this.logs = new OdysseusCollection<>("/api/logs/" + encode(appId) + "/" + encode(appKey), delaySeconds);
            this.events = new OdysseusCollection<>("/api/events/" + encode(appId) + "/" + encode(appKey), delaySeconds);
            this.platform = platform;
        }

        @NonNull
        private static String encode(@NonNull String value) {
            try {
                return URLEncoder.encode(value, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new OdysseusException("Failed to encode value: " + value, e);
            }
        }
    }
}
