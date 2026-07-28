package pl.codetitans.odyssesus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Representation of the event happening inside the application.
 */
public final class OdysseusEventEntry implements OdysseusJsonEntry {
    @NonNull
    private final UUID id;
    @NonNull
    private final String name;
    private final short platform;
    @NonNull
    private final UUID sessionId;
    private final int type;
    @Nullable
    private final UUID streamId;
    private final int position;
    @Nullable
    private final String user;
    @NonNull
    private final Date timestamp;
    @Nullable
    private final Map<String, Object> data;
    @Nullable
    private final Map<String, Object> meta;

    public OdysseusEventEntry(@NonNull UUID id, @NonNull String name, short platform, @NonNull UUID sessionId,
                              int type, @Nullable UUID streamId, int position, @Nullable String user,
                              @Nullable Date timestamp,
                              @Nullable Map<String, Object> data,
                              @Nullable Map<String, Object> meta) {
        this.id = id;
        this.name = name;
        this.platform = platform;
        this.sessionId = sessionId;
        this.type = type;
        this.streamId = streamId;
        this.position = position;
        this.user = user;
        this.timestamp = timestamp != null ? timestamp : new Date();
        this.data = data;
        this.meta = meta;
    }

    @NonNull
    public UUID getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public short getPlatform() {
        return platform;
    }

    @NonNull
    public UUID getSessionId() {
        return sessionId;
    }

    public int getType() {
        return type;
    }

    @Nullable
    public UUID getStreamId() {
        return streamId;
    }

    public int getPosition() {
        return position;
    }

    @Nullable
    public String getUser() {
        return user;
    }

    @NonNull
    public Date getTimestamp() {
        return timestamp;
    }

    @Nullable
    public Map<String, Object> getData() {
        return data;
    }

    @Nullable
    public Map<String, Object> getMeta() {
        return meta;
    }

    @NonNull
    @Override
    public String toString() {
        return getId() + ": " + getName() + " [" + getType() + "]";
    }

    @Override
    public void writeJson(@NonNull StringBuilder sb) {
        sb.append('{');
        sb.append("\"id\":"); OdysseusJson.writeValue(sb, id); sb.append(',');
        sb.append("\"name\":"); OdysseusJson.writeValue(sb, name); sb.append(',');
        sb.append("\"platform\":"); OdysseusJson.writeValue(sb, platform); sb.append(',');
        sb.append("\"session_id\":"); OdysseusJson.writeValue(sb, sessionId); sb.append(',');
        sb.append("\"type\":"); OdysseusJson.writeValue(sb, type); sb.append(',');
        if (streamId != null) {
            sb.append("\"stream_id\":"); OdysseusJson.writeValue(sb, streamId); sb.append(',');
        }
        sb.append("\"position\":"); OdysseusJson.writeValue(sb, position); sb.append(',');
        if (user != null && !user.isEmpty()) {
            sb.append("\"user\":"); OdysseusJson.writeValue(sb, user); sb.append(',');
        }
        if (data != null) {
            sb.append("\"data\":"); OdysseusJson.writeValue(sb, data); sb.append(',');
        }
        if (meta != null) {
            sb.append("\"meta\":"); OdysseusJson.writeValue(sb, meta); sb.append(',');
        }
        sb.append("\"timestamp\":"); OdysseusJson.writeValue(sb, timestamp);
        sb.append('}');
    }
}
