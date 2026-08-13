package pl.codetitans.odyssesus;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client object to provide Odysseus Logging Platform capabilities for the Android projects.
 */
public final class OdysseusClient implements IOdysseusClient, IOdysseusSession {
    private static final String TAG = "OdysseusClient";
    public static final int DEFAULT_DELAY_SECONDS = 5;
    public static final short DEFAULT_PLATFORM = 7;
    public static final int DEFAULT_MAX_ENTRIES = 2_000;
    private static final String PENDING_STORE_DIR = "odysseus-pending";

    private UUID sessionId;
    private final OdysseusCollection logs;
    private final OdysseusCollection events;
    @Nullable
    private final Context appContext;
    private String user;
    private short platform;
    private LogSeverity minSeverity;
    @Nullable
    private Thread.UncaughtExceptionHandler installedCrashHandler;
    @Nullable
    private Thread.UncaughtExceptionHandler previousCrashHandler;

    /**
     * Initializes the instance to connect as given application.
     * <p>
     * Unsubmitted entries only live in memory with this overload: if the process dies before they
     * are uploaded, they are lost. Prefer the {@link Context}-accepting constructors, which
     * persist unsubmitted entries to disk and automatically retry them on the next launch.
     */
    public OdysseusClient(@NonNull String appId, @NonNull String appKey) {
        this(null, appId, appKey, DEFAULT_DELAY_SECONDS, LogSeverity.DEBUG, DEFAULT_PLATFORM, null, DEFAULT_MAX_ENTRIES);
    }

    /**
     * Initializes the instance to connect as given application. See the in-memory-only caveat on
     * {@link #OdysseusClient(String, String)}.
     */
    public OdysseusClient(@NonNull String appId, @NonNull String appKey, short platform) {
        this(null, appId, appKey, DEFAULT_DELAY_SECONDS, LogSeverity.DEBUG, platform, null, DEFAULT_MAX_ENTRIES);
    }

    /**
     * Initializes the instance to connect as given application, with a custom upload batching
     * delay. See the in-memory-only caveat on {@link #OdysseusClient(String, String)}.
     */
    public OdysseusClient(@NonNull String appId, @NonNull String appKey, int delaySeconds, @NonNull LogSeverity minSeverity, short platform, @Nullable String host) {
        this(null, appId, appKey, delaySeconds, minSeverity, platform, host, DEFAULT_MAX_ENTRIES);
    }

    /**
     * Initializes the instance to connect as given application. Unsubmitted log entries and
     * events are persisted to disk (under {@code context}'s no-backup storage) and are
     * automatically retried the next time a client is constructed with the same {@code appId}/
     * {@code appKey} - including across app restarts after a crash or lost network connection.
     * Also installs an uncaught-exception handler that logs the crash (with stack trace) as a
     * {@link LogSeverity#CRITICAL} entry before chaining to whatever handler was previously
     * installed, so no crash goes unrecorded.
     */
    public OdysseusClient(@NonNull Context context, @NonNull String appId, @NonNull String appKey) {
        this(context, appId, appKey, DEFAULT_DELAY_SECONDS, LogSeverity.DEBUG, DEFAULT_PLATFORM, null, DEFAULT_MAX_ENTRIES);
    }

    /**
     * Initializes the instance to connect as given application. See {@link #OdysseusClient(Context, String, String)}.
     */
    public OdysseusClient(@NonNull Context context, @NonNull String appId, @NonNull String appKey, short platform) {
        this(context, appId, appKey, DEFAULT_DELAY_SECONDS, LogSeverity.DEBUG, platform, null, DEFAULT_MAX_ENTRIES);
    }

    /**
     * Full-control constructor, using the default of {@link #DEFAULT_MAX_ENTRIES} pending entries.
     * See {@link #OdysseusClient(Context, String, String, int, LogSeverity, short, String, int)}.
     */
    public OdysseusClient(@Nullable Context context, @NonNull String appId, @NonNull String appKey, int delaySeconds, @NonNull LogSeverity minSeverity, short platform, @Nullable String host) {
        this(context, appId, appKey, delaySeconds, minSeverity, platform, host, DEFAULT_MAX_ENTRIES);
    }

    /**
     * Full-control constructor. See {@link #OdysseusClient(Context, String, String)}.
     * <p>
     * Pass {@code context = null} to fall back to the in-memory-only behavior described on
     * {@link #OdysseusClient(String, String)} (used internally by the no-{@code Context}
     * overloads of this constructor).
     * <p>
     * {@code maxEntries} caps how many not-yet-uploaded log entries and events (each counted
     * separately) are ever held in memory/on disk at once - a very long stretch without a
     * connection drops the oldest ones to make room for new ones, rather than growing without
     * bound.
     */
    public OdysseusClient(@Nullable Context context, @NonNull String appId, @NonNull String appKey, int delaySeconds, @NonNull LogSeverity minSeverity, short platform, @Nullable String host, int maxEntries) {
        final File storageDir = context != null ? resolveStorageDir(context) : null;
        this.logs = new OdysseusCollection(host, "/api/logs/" + encode(appId) + "/" + encode(appKey), delaySeconds,
                storageDir != null ? new File(storageDir, "pending-logs.jsonl") : null, maxEntries);
        this.events = new OdysseusCollection(host, "/api/events/" + encode(appId) + "/" + encode(appKey), delaySeconds,
                storageDir != null ? new File(storageDir, "pending-events.jsonl") : null, maxEntries);
        // The application context, never whatever short-lived Activity/Service context was passed
        // in, so this can't leak - it's only used later for captureAppInfo()/captureDeviceInfo().
        final Context applicationContext = context != null ? context.getApplicationContext() : null;
        this.appContext = applicationContext != null ? applicationContext : context;
        this.sessionId = UUID.randomUUID();
        this.platform = platform;
        this.minSeverity = minSeverity;

        if (context != null) {
            installCrashHandler();
        }
    }

    /**
     * Sets or unsets the user associated with all following log entries or events.
     */
    @Override
    public void setUser(@Nullable String value) {
        this.user = value;
    }

    /**
     * Gets the information about current user associated with log entries or events.
     */
    @Override
    @Nullable
    public String getUser() {
        return user;
    }

    /**
     * Sets the minimal log severity, below which log entries get discarded instead of uploaded.
     */
    @Override
    public void setMinSeverity(@NonNull LogSeverity value) {
        this.minSeverity = value;
    }

    /**
     * Gets the minimal log severity, below which log entries get discarded instead of uploaded.
     */
    @Override
    @NonNull
    public LogSeverity getMinSeverity() {
        return minSeverity;
    }

    /**
     * Checks, if given severity will match and let the log be stored internally.
     */
    public boolean isMatching(@NonNull LogSeverity severity) {
        return severity.getValue() >= minSeverity.getValue();
    }

    /**
     * Sets or unsets the platform identifier associated with all following log entries or events.
     */
    @Override
    public void setPlatform(short value) {
        this.platform = value;
    }

    /**
     * Gets the platform identifier associated with log entries or events.
     */
    @Override
    public short getPlatform() {
        return platform;
    }

    /**
     * Gets the identifier of the current session, generated automatically after application relaunch.
     */
    @Override
    @NonNull
    public UUID getSessionId() {
        return sessionId;
    }

    /**
     * Assigns new globally unique value for the session.
     */
    @Override
    public void setSessionId() {
        this.sessionId = UUID.randomUUID();
    }

    /**
     * Assigns new value for session.
     */
    @Override
    public void setSessionId(@NonNull UUID value) {
        this.sessionId = value != null ? value : UUID.randomUUID();
    }

    /**
     * Stores the log entry internally and later on submits it to the cloud, when possible.
     * Returns null, if the entry's severity is below the currently configured minimal severity.
     */
    @Override
    @Nullable
    public OdysseusLogEntry add(@NonNull OdysseusLogEntry entry) {
        if (entry.getSeverity() < minSeverity.getValue()) {
            return null;
        }

        this.logs.add(toJson(entry));
        return entry;
    }

    /**
     * Stores new log entry internally and later uploads it to the cloud.
     */
    @Override
    @Nullable
    public OdysseusLogEntry log(@NonNull String message, @NonNull LogSeverity severity, @Nullable String tag,
                         @Nullable String file, @Nullable String method, @Nullable Long line,
                         @Nullable Long thread, @Nullable String threadName, @Nullable Date timestamp,
                         @Nullable Map<String, Object> context) {
        if (severity.getValue() < minSeverity.getValue()) {
            return null;
        }

        final OdysseusLogEntry entry = new OdysseusLogEntry(message, getSessionId(), severity, tag, getPlatform(),
                file, method, line, thread, threadName, getUser(), timestamp, context);
        this.logs.add(toJson(entry));
        return entry;
    }

    /**
     * Stores the log entries internally and later on submits it to the cloud, when possible.
     * Returns only the entries that were accepted (severity not below the currently configured minimal severity).
     */
    @NonNull
    public List<OdysseusLogEntry> addAllLogs(@NonNull List<OdysseusLogEntry> entries) {
        final List<OdysseusLogEntry> accepted = new ArrayList<>();
        final List<String> json = new ArrayList<>();

        for (OdysseusLogEntry entry : entries) {
            if (entry.getSeverity() >= minSeverity.getValue()) {
                accepted.add(entry);
                json.add(toJson(entry));
            }
        }

        this.logs.addAll(json);
        return accepted;
    }

    /**
     * Stores the event entry internally and later on submits it to the cloud, when possible.
     */
    @NonNull
    public OdysseusEventEntry add(@NonNull OdysseusEventEntry event) {
        this.events.add(toJson(event));
        return event;
    }

    /**
     * Stores the event entry internally and later on submits it to the cloud, when possible.
     */
    @Override
    @NonNull
    public OdysseusEventEntry event(@NonNull String name, @Nullable UUID id, int type, @Nullable UUID streamId, int position,
                             @Nullable Date timestamp,
                             @Nullable Map<String, Object> data,
                             @Nullable Map<String, Object> meta) {

        final OdysseusEventEntry event = new OdysseusEventEntry(id != null ? id : UUID.randomUUID(),
                name, getPlatform(), getSessionId(), type, streamId, position, getUser(), timestamp, data, meta);
        this.events.add(toJson(event));
        return event;
    }

    /**
     * Stores the event entries internally and later on submits it to the cloud, when possible.
     */
    @NonNull
    public List<OdysseusEventEntry> addAllEvents(@NonNull List<OdysseusEventEntry> events) {
        final List<String> json = new ArrayList<>();
        for (OdysseusEventEntry event : events) {
            json.add(toJson(event));
        }

        this.events.addAll(json);
        return events;
    }

    /**
     * Wraps an exception (and its cause chain) into a dictionary for easier setting as a parameter in context/meta.
     */
    @Override
    @NonNull
    public Map<String, Object> wrap(@NonNull Throwable error) {
        final Hashtable<String, Object> d = new Hashtable<>();

        d.put("type", error.getClass().getSimpleName());
        if (error.getMessage() != null) {
            d.put("message", error.getMessage());
        }

        final String stack = formatStackTrace(error);
        if (!stack.isEmpty()) {
            d.put("stack", stack);
        }

        final Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            d.put("inner", wrap(cause));
        }

        return d;
    }

    /**
     * Captures details about the currently running app (version, install origin, debug/release
     * build, ...) - see {@link OdysseusDeviceInfo#captureAppInfo}. Requires a {@code Context} to
     * have been supplied at construction time; otherwise returns just {@code extra} (or an empty
     * map), with a warning logged.
     */
    @Override
    @NonNull
    public Map<String, Object> captureAppInfo(@Nullable Map<String, Object> extra) {
        final Map<String, Object> info = appContext != null
                ? OdysseusDeviceInfo.captureAppInfo(appContext)
                : missingContext("captureAppInfo");

        if (extra != null) {
            info.putAll(extra);
        }
        return info;
    }

    /**
     * Captures details about the current device (hardware/OS, screen, memory, storage, battery,
     * ...) - see {@link OdysseusDeviceInfo#captureDeviceInfo}. Requires a {@code Context} to have
     * been supplied at construction time; otherwise returns just {@code extra} (or an empty map),
     * with a warning logged.
     */
    @Override
    @NonNull
    public Map<String, Object> captureDeviceInfo(@Nullable Map<String, Object> extra) {
        final Map<String, Object> info = appContext != null
                ? OdysseusDeviceInfo.captureDeviceInfo(appContext)
                : missingContext("captureDeviceInfo");

        if (extra != null) {
            info.putAll(extra);
        }
        return info;
    }

    @NonNull
    private static Map<String, Object> missingContext(@NonNull String method) {
        Log.w(TAG, method + "() called without a Context - construct the client with a Context to capture this");
        return new Hashtable<>();
    }

    /**
     * Creates new associated and simplified logger for a given tag.
     */
    public IOdysseusLog create(@Nullable String tag) {
        return new OdysseusLogger(this, tag);
    }

    /**
     * Restores whatever uncaught-exception handler was installed before this client's, if this
     * client is still the currently active one. Called by {@link OdysseusFactory#stop()}.
     */
    void shutdown() {
        if (installedCrashHandler != null && Thread.getDefaultUncaughtExceptionHandler() == installedCrashHandler) {
            Thread.setDefaultUncaughtExceptionHandler(previousCrashHandler);
        }
    }

    /**
     * Chains an uncaught-exception handler in front of whichever one is currently installed
     * (never replaces it outright), so a crash still gets recorded as a CRITICAL log entry - with
     * its stack trace, via {@link #wrap} - even though there's no time left for an HTTP upload;
     * the entry is durably queued on disk instead and retried on the next launch.
     */
    private void installCrashHandler() {
        previousCrashHandler = Thread.getDefaultUncaughtExceptionHandler();
        installedCrashHandler = (thread, error) -> {
            try {
                final Map<String, Object> context = new Hashtable<>();
                context.put("exception", wrap(error));

                final String message = error.getMessage() != null ? error.getMessage() : error.getClass().getName();
                add(new OdysseusLogEntry(message, getSessionId(), LogSeverity.CRITICAL, "CRASH", getPlatform(),
                        null, null, null, threadId(thread), thread.getName(), getUser(), new Date(), context));
            } catch (Throwable ignored) {
                // never let crash-reporting itself crash the crash handler
            } finally {
                // Everything still only in memory - the crash entry just added above, plus
                // whatever else was queued but hadn't failed an upload (and so was never
                // persisted) yet - must hit disk now: there's no time left for a normal upload
                // cycle once we return from here.
                try {
                    logs.persistPendingNow();
                    events.persistPendingNow();
                } catch (Throwable ignored) {
                    // as above - never let crash-reporting itself crash the crash handler
                }

                if (previousCrashHandler != null) {
                    previousCrashHandler.uncaughtException(thread, error);
                } else {
                    Process.killProcess(Process.myPid());
                    System.exit(10);
                }
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(installedCrashHandler);
    }

    // Thread.getId() is deprecated in favor of threadId(), but that replacement only exists from
    // API 35 - minSdk here is 23, so the deprecated call is the only option and is deliberate.
    @SuppressWarnings("deprecation")
    private static long threadId(@NonNull Thread thread) {
        return thread.getId();
    }

    @NonNull
    private static File resolveStorageDir(@NonNull Context context) {
        File base = context.getNoBackupFilesDir();
        if (base == null) {
            base = context.getFilesDir();
        }
        return new File(base, PENDING_STORE_DIR);
    }

    @NonNull
    private static String toJson(@NonNull OdysseusJsonEntry entry) {
        final StringBuilder sb = new StringBuilder();
        entry.writeJson(sb);
        return sb.toString();
    }

    @NonNull
    private static String formatStackTrace(@NonNull Throwable error) {
        final StackTraceElement[] elements = error.getStackTrace();
        final StringBuilder sb = new StringBuilder();

        for (StackTraceElement element : elements) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("   at ").append(element);
        }

        return sb.toString();
    }

    @NonNull
    private static String encode(@NonNull String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new OdysseusException("Failed to encode value: " + value, e);
        }
    }
}
