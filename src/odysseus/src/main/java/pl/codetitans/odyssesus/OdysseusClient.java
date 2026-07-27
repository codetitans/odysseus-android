package pl.codetitans.odyssesus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client object to provide Odysseus Logging Platform capabilities for the Android projects.
 */
public final class OdysseusClient implements IOdysseusClient, IOdysseusSession {
    public static final int DEFAULT_DELAY_SECONDS = 5;
    public static final short DEFAULT_PLATFORM = 7;

    private UUID sessionId;
    private final OdysseusCollection<OdysseusLogEntry> logs;
    private final OdysseusCollection<OdysseusEventEntry> events;
    private String user;
    private short platform;
    private LogSeverity minSeverity;

    /**
     * Initializes the instance to connect as given application.
     */
    public OdysseusClient(@NonNull String appId, @NonNull String appKey) {
        this(appId, appKey, DEFAULT_PLATFORM, DEFAULT_DELAY_SECONDS, LogSeverity.DEBUG);
    }

    /**
     * Initializes the instance to connect as given application, with a custom upload batching delay.
     */
    public OdysseusClient(@NonNull String appId, @NonNull String appKey, short platform, int delaySeconds, @NonNull LogSeverity minSeverity) {
        this.logs = new OdysseusCollection<>("/api/logs/" + encode(appId) + "/" + encode(appKey), delaySeconds);
        this.events = new OdysseusCollection<>("/api/events/" + encode(appId) + "/" + encode(appKey), delaySeconds);
        this.sessionId = UUID.randomUUID();
        this.platform = platform;
        this.minSeverity = minSeverity != null ? minSeverity : LogSeverity.DEBUG;
    }

    /**
     * Sets or unsets the user associated with all following log entries or events.
     */
    @Override
    public void setUser(@Nullable String value) {
        this.user = value;
    }

    /**
     * Gets the information about current user associated with log entries or events.
     */
    @Override
    @Nullable
    public String getUser() {
        return user;
    }

    /**
     * Sets the minimal log severity, below which log entries get discarded instead of uploaded.
     */
    @Override
    public void setMinSeverity(@NonNull LogSeverity value) {
        this.minSeverity = value;
    }

    /**
     * Gets the minimal log severity, below which log entries get discarded instead of uploaded.
     */
    @Override
    @NonNull
    public LogSeverity getMinSeverity() {
        return minSeverity;
    }

    /**
     * Checks, if given severity will match and let the log be stored internally.
     */
    public boolean isMatching(@NonNull LogSeverity severity) {
        return severity.getValue() >= minSeverity.getValue();
    }

    /**
     * Sets or unsets the platform identifier associated with all following log entries or events.
     */
    @Override
    public void setPlatform(short value) {
        this.platform = value;
    }

    /**
     * Gets the platform identifier associated with log entries or events.
     */
    @Override
    public short getPlatform() {
        return platform;
    }

    /**
     * Gets the identifier of the current session, generated automatically after application relaunch.
     */
    @Override
    @NonNull
    public UUID getSessionId() {
        return sessionId;
    }

    /**
     * Assigns new globally unique value for the session.
     */
    @Override
    public void setSessionId() {
        this.sessionId = UUID.randomUUID();
    }

    /**
     * Assigns new value for session.
     */
    @Override
    public void setSessionId(@NonNull UUID value) {
        this.sessionId = value != null ? value : UUID.randomUUID();
    }

    /**
     * Stores the log entry internally and later on submits it to the cloud, when possible.
     * Returns null, if the entry's severity is below the currently configured minimal severity.
     */
    @Override
    @Nullable
    public OdysseusLogEntry add(@NonNull OdysseusLogEntry entry) {
        if (entry.getSeverity() < minSeverity.getValue()) {
            return null;
        }

        this.logs.add(entry);
        return entry;
    }

    /**
     * Stores new log entry internally and later uploads it to the cloud.
     */
    @Override
    @Nullable
    public OdysseusLogEntry log(@NonNull String message, @NonNull LogSeverity severity, @Nullable String tag,
                         @Nullable String file, @Nullable String method, @Nullable Long line,
                         @Nullable Long thread, @Nullable Date timestamp,
                         @Nullable Map<String, Object> context) {
        if (severity.getValue() < minSeverity.getValue()) {
            return null;
        }

        final OdysseusLogEntry entry = new OdysseusLogEntry(message, getSessionId(), severity, tag, getPlatform(),
                file, method, line, thread, getUser(), timestamp, context);
        this.logs.add(entry);
        return entry;
    }

    /**
     * Stores the log entries internally and later on submits it to the cloud, when possible.
     * Returns only the entries that were accepted (severity not below the currently configured minimal severity).
     */
    @NonNull
    public List<OdysseusLogEntry> addAllLogs(@NonNull List<OdysseusLogEntry> entries) {
        final List<OdysseusLogEntry> accepted = new ArrayList<>();

        for (OdysseusLogEntry entry : entries) {
            if (entry.getSeverity() >= minSeverity.getValue()) {
                accepted.add(entry);
            }
        }

        this.logs.addAll(accepted);
        return accepted;
    }

    /**
     * Stores the event entry internally and later on submits it to the cloud, when possible.
     */
    @NonNull
    public OdysseusEventEntry add(@NonNull OdysseusEventEntry event) {
        this.events.add(event);
        return event;
    }

    /**
     * Stores the event entry internally and later on submits it to the cloud, when possible.
     */
    @Override
    @NonNull
    public OdysseusEventEntry event(@NonNull String name, @Nullable UUID id, int type, @Nullable UUID streamId, int position,
                             @Nullable Date timestamp,
                             @Nullable Map<String, Object> data,
                             @Nullable Map<String, Object> meta) {

        final OdysseusEventEntry event = new OdysseusEventEntry(id != null ? id : UUID.randomUUID(),
                name, getPlatform(), getSessionId(), type, streamId, position, getUser(), timestamp, data, meta);
        this.events.add(event);
        return event;
    }

    /**
     * Stores the event entries internally and later on submits it to the cloud, when possible.
     */
    @NonNull
    public List<OdysseusEventEntry> addAllEvents(@NonNull List<OdysseusEventEntry> events) {
        this.events.addAll(events);
        return events;
    }

    /**
     * Wraps an exception (and its cause chain) into a dictionary for easier setting as a parameter in context/meta.
     */
    @Override
    @NonNull
    public Map<String, Object> wrap(@NonNull Throwable error) {
        final Hashtable<String, Object> d = new Hashtable<>();

        d.put("type", error.getClass().getSimpleName());
        if (error.getMessage() != null) {
            d.put("message", error.getMessage());
        }

        final String stack = formatStackTrace(error);
        if (!stack.isEmpty()) {
            d.put("stack", stack);
        }

        final Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            d.put("inner", wrap(cause));
        }

        return d;
    }

    /**
     * Creates new associated and simplified logger for a given tag.
     */
    public IOdysseusLog create(@Nullable String tag) {
        return new OdysseusLogger(this, tag);
    }

    @NonNull
    private static String formatStackTrace(@NonNull Throwable error) {
        final StackTraceElement[] elements = error.getStackTrace();
        final StringBuilder sb = new StringBuilder();

        for (StackTraceElement element : elements) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("   at ").append(element);
        }

        return sb.toString();
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
