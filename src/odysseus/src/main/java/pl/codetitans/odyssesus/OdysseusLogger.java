package pl.codetitans.odyssesus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Instant;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class OdysseusLogger implements IOdysseusLog {
    @NonNull
    private final OdysseusClient client;
    @Nullable
    private final String tag;

    public OdysseusLogger(@NonNull OdysseusClient client, @Nullable String tag) {
        this.client = client;
        this.tag = tag;
    }

    @Nullable
    public String getTag() {
        return tag;
    }

    @Override
    public void setUser(@Nullable String value) {
        client.setUser(value);
    }

    @Override
    public void setSessionId() {
        client.setSessionId();
    }

    @Override
    public void setSessionId(@NonNull UUID value) {
        client.setSessionId(value);
    }

    @Nullable
    @Override
    public OdysseusLogEntry t(@NonNull String message) {
        return client.log(message, LogSeverity.TRACE, getTag(), null, null, null, null, null, null);
    }

    @Nullable
    @Override
    public OdysseusLogEntry d(@NonNull String message) {
        return client.log(message, LogSeverity.DEBUG, getTag(), null, null, null, null, null, null);
    }

    @Nullable
    @Override
    public OdysseusLogEntry i(@NonNull String message) {
        return client.log(message, LogSeverity.INFO, getTag(), null, null, null, null, null, null);
    }

    @Nullable
    @Override
    public OdysseusLogEntry s(@NonNull String message) {
        return client.log(message, LogSeverity.SUCCESS, getTag(), null, null, null, null, null, null);
    }

    @Nullable
    @Override
    public OdysseusLogEntry w(@NonNull String message) {
        return client.log(message, LogSeverity.WARN, getTag(), null, null, null, null, null, null);
    }

    @Override
    @Nullable
    public OdysseusLogEntry w(@Nullable Throwable ex, @NonNull String message) {

    }

    @Nullable
    @Override
    public OdysseusLogEntry e(@NonNull String message) {
        return client.log(message, LogSeverity.ERROR, getTag(), null, null, null, null, null, null);
    }

    @Nullable
    @Override
    public OdysseusLogEntry e(@Nullable Throwable ex, @NonNull String message) {
        return null;
    }

    @Nullable
    @Override
    public OdysseusLogEntry c(@NonNull String message) {
        return client.log(message, LogSeverity.CRITICAL, getTag(), null, null, null, null, null, null);
    }

    @Override
    @Nullable
    public OdysseusLogEntry c(@Nullable Throwable ex, @NonNull String message) {

    }

    private Map<String, Object> createContextFor(@Nullable Throwable error) {
        if (error == null) {
            return null;
        }

        final Map<String, Object> context = new HashMap<>();
        context.put("exception", client.wrap(error));
        return context;
    }

    @Override
    @Nullable
    public OdysseusLogEntry log(@NonNull OdysseusLogEntry entry) {
        return client.add(entry);
    }

    @Override
    @Nullable
    public OdysseusLogEntry log(@NonNull String message, @NonNull LogSeverity severity, @Nullable String file, @Nullable String method, @Nullable Long line, @Nullable Long thread, @Nullable Instant timestamp, @Nullable Dictionary<String, Object> context) {
        return null;
    }

    @Override
    @NonNull
    public OdysseusEventEntry event(@NonNull OdysseusEventEntry event) {
        return client.add(event);
    }

    @NonNull
    @Override
    public OdysseusEventEntry event(@NonNull String name, @Nullable UUID id, int type, @Nullable UUID streamId, int position, @Nullable Instant timestamp, @Nullable Dictionary<String, Object> data, @Nullable Dictionary<String, Object> meta) {
        return null;
    }
}
