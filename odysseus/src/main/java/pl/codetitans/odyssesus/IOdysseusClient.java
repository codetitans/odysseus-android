package pl.codetitans.odyssesus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.UUID;

public interface IOdysseusClient {

    @Nullable
    OdysseusLogEntry add(@NonNull OdysseusLogEntry entry);

    @NonNull
    OdysseusEventEntry add(@NonNull OdysseusEventEntry event);

    @Nullable
    OdysseusLogEntry log(@NonNull String message, @NonNull LogSeverity severity, @Nullable String tag,
                         @Nullable String file, @Nullable String method, @Nullable Long line,
                         @Nullable Long thread, @Nullable Date timestamp,
                         @Nullable Map<String, Object> context);

    @NonNull
    OdysseusEventEntry event(@NonNull String name, @Nullable UUID id, int type, @Nullable UUID streamId, int position,
                             @Nullable Date timestamp,
                             @Nullable Map<String, Object> data,
                             @Nullable Map<String, Object> meta);

    @NonNull
    List<OdysseusLogEntry> addAllLogs(@NonNull List<OdysseusLogEntry> entries);

    @NonNull
    List<OdysseusEventEntry> addAllEvents(@NonNull List<OdysseusEventEntry> events);

    /**
     * Wraps an exception (and its cause chain) into a map for easier setting as a parameter in context/meta.
     */
    @NonNull
    Map<String, Object> wrap(@NonNull Throwable error);
}
