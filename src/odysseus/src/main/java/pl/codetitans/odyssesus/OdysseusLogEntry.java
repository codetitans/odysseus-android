package pl.codetitans.odyssesus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Representation of the log entry registered inside the application.
 */
public final class OdysseusLogEntry implements OdysseusJsonEntry {
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
    @NonNull
    private final Instant timestamp;
    @Nullable
    private final Map<String, Object> context;

    public OdysseusLogEntry(@NonNull String message, @NonNull UUID sessionId, short severity,
                            @Nullable String tag, short platform, @Nullable String file, @Nullable String method, @Nullable Long line, @Nullable Long thread,
                            @Nullable String user, @Nullable Instant timestamp, @Nullable Map<String, Object> context) {
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
        this.timestamp = timestamp != null ? timestamp : Instant.now();
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
    public Map<String, Object> getContext() {
        return context;
    }

    @NonNull
    @Override
    public String toString() {
        return getSeverity() + ": " + getMessage();
    }

    @Override
    public void writeJson(@NonNull StringBuilder sb) {
        sb.append('{');
        sb.append("\"message\":"); OdysseusJson.writeValue(sb, message); sb.append(',');
        sb.append("\"session_id\":"); OdysseusJson.writeValue(sb, sessionId); sb.append(',');
        sb.append("\"severity\":"); OdysseusJson.writeValue(sb, severity); sb.append(',');
        if (tag != null && !tag.isEmpty()) {
            sb.append("\"tag\":"); OdysseusJson.writeValue(sb, tag); sb.append(',');
        }
        sb.append("\"platform\":"); OdysseusJson.writeValue(sb, platform); sb.append(',');
        if (file != null && !file.isEmpty()) {
            sb.append("\"file\":"); OdysseusJson.writeValue(sb, file); sb.append(',');
        }
        if (method != null && !method.isEmpty()) {
            sb.append("\"method\":"); OdysseusJson.writeValue(sb, method); sb.append(',');
        }
        if (line != null) {
            sb.append("\"line\":"); OdysseusJson.writeValue(sb, line); sb.append(',');
        }
        if (thread != null) {
            sb.append("\"thread\":"); OdysseusJson.writeValue(sb, thread); sb.append(',');
        }
        if (user != null && !user.isEmpty()) {
            sb.append("\"user\":"); OdysseusJson.writeValue(sb, user); sb.append(',');
        }
        sb.append("\"timestamp\":"); OdysseusJson.writeValue(sb, timestamp); sb.append(',');
        if (context != null) {
            sb.append("\"context\":"); OdysseusJson.writeValue(sb, context);
        }
        sb.append('}');
    }
}
