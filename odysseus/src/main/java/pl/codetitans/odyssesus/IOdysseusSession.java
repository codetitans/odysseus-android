package pl.codetitans.odyssesus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

public interface IOdysseusSession {

    @Nullable
    String getUser();
    void setUser(@Nullable String value);

    @NonNull
    UUID getSessionId();
    void setSessionId();
    void setSessionId(@NonNull UUID value);

    @NonNull
    LogSeverity getMinSeverity();
    void setMinSeverity(@NonNull LogSeverity value);

    short getPlatform();
    void setPlatform(short value);
}
