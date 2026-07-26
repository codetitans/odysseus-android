package pl.codetitans.odyssesus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface IOdysseusLog {

    void setUser(@Nullable String value);
    void setSessionId();
    void setSessionId(@NonNull UUID value);

    @Nullable
    OdysseusLogEntry t(@NonNull String message);

    @Nullable
    OdysseusLogEntry d(@NonNull String message);

    @Nullable
    OdysseusLogEntry i(@NonNull String message);

    @Nullable
    OdysseusLogEntry s(@NonNull String message);

    @Nullable
    OdysseusLogEntry w(@NonNull String message);

    @Nullable
    OdysseusLogEntry w(@Nullable Throwable ex, @NonNull String message);

    @Nullable
    OdysseusLogEntry e(@NonNull String message);

    @Nullable
    OdysseusLogEntry e(@Nullable Throwable ex, @NonNull String message);

    @Nullable
    OdysseusLogEntry c(@NonNull String message);

    @Nullable
    OdysseusLogEntry c(@Nullable Throwable ex, @NonNull String message);

    @Nullable
    OdysseusLogEntry log(@NonNull OdysseusLogEntry entry);

    @Nullable
    OdysseusLogEntry log(@NonNull String message, @NonNull LogSeverity severity,
                         @Nullable String file, @Nullable String method, @Nullable Long line,
                         @Nullable Long thread, @Nullable Instant timestamp,
                         @Nullable Map<String, Object> context);

    @NonNull
    OdysseusEventEntry event(@NonNull OdysseusEventEntry event);

    @NonNull
    OdysseusEventEntry event(@NonNull String name, @Nullable UUID id, int type, @Nullable UUID streamId, int position,
                             @Nullable Instant timestamp,
                             @Nullable Map<String, Object> data,
                             @Nullable Map<String, Object> meta);
}
