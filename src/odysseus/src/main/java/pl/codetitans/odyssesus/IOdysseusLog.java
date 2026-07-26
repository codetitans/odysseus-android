package pl.codetitans.odyssesus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Instant;
import java.util.Dictionary;
import java.util.List;
import java.util.UUID;

public interface IOdysseusLog {

    @Nullable
    OdysseusLogEntry add(@NonNull OdysseusLogEntry entry);

    @NonNull
    OdysseusEventEntry add(@NonNull OdysseusEventEntry event);

    @Nullable
    OdysseusLogEntry log(@NonNull String message, @NonNull LogSeverity severity, @Nullable String tag,
                         @Nullable String file, @Nullable String method, @Nullable Long line,
                         @Nullable Long thread, @Nullable Instant timestamp,
                         @Nullable Dictionary<String, Object> context);

    @NonNull
    OdysseusEventEntry event(@NonNull String name, @Nullable UUID id, int type, @Nullable UUID streamId, int position,
                             @Nullable Instant timestamp,
                             @Nullable Dictionary<String, Object> data,
                             @Nullable Dictionary<String, Object> meta);

    @NonNull
    List<OdysseusLogEntry> addAllLogs(@NonNull List<OdysseusLogEntry> entries);

    @NonNull
    List<OdysseusEventEntry> addAllEvents(@NonNull List<OdysseusEventEntry> events);
}
