package pl.codetitans.odyssesus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Instant;
import java.util.Dictionary;
import java.util.UUID;

/**
 * Representation of the log entry registered inside the application.
 */
public final class OdysseusLogEntry {
    @NonNull
    private final String message;
    @NonNull
    private final UUID sessionId;
    private final short severity;
    @Nullable
    private final String tag;
    private final short platform;
    @Nullable
    private final String file;
    @Nullable
    private final String method;
    @Nullable
    private final Long line;
    @Nullable
    private final Long thread;
    @Nullable
    private final String user;
    private final Instant timestamp;
    @Nullable
    private final Dictionary<String, Object> context;

    public OdysseusLogEntry(@NonNull String message, @NonNull UUID sessionId, short severity,
                            @Nullable String tag, short platform, @Nullable String file, @Nullable String method, @Nullable Long line, @Nullable Long thread,
                            @Nullable String user, @NonNull Instant timestamp, @Nullable Dictionary<String, Object> context) {
        this.message = message;
        this.sessionId = sessionId;
        this.severity = severity;
        this.tag = tag;
        this.platform = platform;
        this.file = file;
        this.method = method;
        this.line = line;
        this.thread = thread;
        this.user = user;
        this.timestamp = timestamp;
        this.context = context;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    @NonNull
    public UUID getSessionId() {
        return sessionId;
    }

    public short getSeverity() {
        return severity;
    }

    @Nullable
    public String getTag() {
        return tag;
    }

    public short getPlatform() {
        return platform;
    }

    @Nullable
    public String getFile() {
        return file;
    }

    @Nullable
    public String getMethod() {
        return method;
    }

    @Nullable
    public Long getLine() {
        return line;
    }

    @Nullable
    public Long getThread() {
        return thread;
    }

    @Nullable
    public String getUser() {
        return user;
    }

    @NonNull
    public Instant getTimestamp() {
        return timestamp;
    }

    @Nullable
    public Dictionary<String, Object> getContext() {
        return context;
    }

    @NonNull
    @Override
    public String toString() {
        return getSeverity() + ": " + getMessage();
    }
}
