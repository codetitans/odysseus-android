package pl.codetitans.odyssesus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Instant;
import java.util.Dictionary;
import java.util.UUID;

/**
 * Representation of the event happening inside the application.
 */
public final class OdysseusEventEntry {
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
    private final Instant timestamp;
    @Nullable
    private final Dictionary<String, Object> data;
    @Nullable
    private final Dictionary<String, Object> meta;

    public OdysseusEventEntry(@NonNull UUID id, @NonNull String name, short platform, @NonNull UUID sessionId,
                              int type, @Nullable UUID streamId, int position, @Nullable String user,
                              @NonNull Instant timestamp,
                              @Nullable Dictionary<String, Object> data,
                              @Nullable Dictionary<String, Object> meta) {
        this.id = id;
        this.name = name;
        this.platform = platform;
        this.sessionId = sessionId;
        this.type = type;
        this.streamId = streamId;
        this.position = position;
        this.user = user;
        this.timestamp = timestamp;
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
    public Instant getTimestamp() {
        return timestamp;
    }

    @Nullable
    public Dictionary<String, Object> getData() {
        return data;
    }

    @Nullable
    public Dictionary<String, Object> getMeta() {
        return meta;
    }

    @NonNull
    @Override
    public String toString() {
        return getId() + ": " + getName() + " [" + getType() + "]";
    }
}
