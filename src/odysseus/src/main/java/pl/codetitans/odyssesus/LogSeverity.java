package pl.codetitans.odyssesus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public enum LogSeverity {

    TRACE(0, "TRACE"),
    DEBUG(1, "DEBUG"),
    INFO(2, "INFO"),
    SUCCESS(3, "SUCCESS"),
    WARN(4, "WARN"),
    ERROR(5, "ERROR"),
    CRITICAL(6, "CRITICAL"),
    ;

    private final int value;
    private final String name;

    LogSeverity(int value, @NonNull String name) {
        this.value = value;
        this.name = name;
    }

    /**
     * Gets the value of the log-level.
     */
    public int getValue() {
        return value;
    }

    /**
     * Gets the name of the log-level.
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Gets the proper entry based on a numerical value.
     */
    @Nullable
    public static LogSeverity from(int value) {
        switch (value) {
            case 0: return TRACE;
            case 1: return DEBUG;
            case 2: return INFO;
            case 3: return SUCCESS;
            case 4: return WARN;
            case 5: return ERROR;
            case 6: return CRITICAL;

            default:
                return null;
        }
    }

    /**
     * Gets the proper entry based on a name.
     */
    @Nullable
    public static LogSeverity from(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        if (TRACE.name.equalsIgnoreCase(value)) {
            return TRACE;
        }
        if (DEBUG.name.equalsIgnoreCase(value)) {
            return DEBUG;
        }
        if (INFO.name.equalsIgnoreCase(value)) {
            return INFO;
        }
        if (SUCCESS.name.equalsIgnoreCase(value)) {
            return SUCCESS;
        }
        if (WARN.name.equalsIgnoreCase(value)) {
            return WARN;
        }
        if (ERROR.name.equalsIgnoreCase(value)) {
            return ERROR;
        }
        if (CRITICAL.name.equalsIgnoreCase(value)) {
            return CRITICAL;
        }

        return null;
    }
}
