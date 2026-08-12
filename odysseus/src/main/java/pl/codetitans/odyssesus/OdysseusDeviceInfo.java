package pl.codetitans.odyssesus;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.BatteryManager;
import android.os.Build;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Captures a best-effort snapshot of app/device information, meant to be attached as event
 * data/meta - typically once, to an "app start" event - so it's on hand later when reading logs.
 * Every individual piece is gathered defensively: a failure reading one thing (an odd OEM, a
 * missing system service, ...) never prevents the rest from being collected, and never throws
 * back at the caller. Nothing here requires any permission beyond what any app already has.
 */
final class OdysseusDeviceInfo {
    private static final String TAG = "OdysseusDeviceInfo";

    private OdysseusDeviceInfo() {
    }

    /**
     * Captures information about this application's own package/build: version, install origin,
     * debug/release build, and similar.
     */
    @NonNull
    public static Map<String, Object> captureAppInfo(@NonNull Context context) {
        final Map<String, Object> info = new Hashtable<>();
        final String packageName = context.getPackageName();
        info.put("package_name", packageName);

        try {
            final PackageManager pm = context.getPackageManager();
            final PackageInfo packageInfo = getPackageInfo(pm, packageName);

            if (packageInfo.versionName != null) {
                info.put("version_name", packageInfo.versionName);
            }
            info.put("version_code", getVersionCode(packageInfo));
            info.put("first_install_time", new Date(packageInfo.firstInstallTime));
            info.put("last_update_time", new Date(packageInfo.lastUpdateTime));

            final ApplicationInfo appInfo = packageInfo.applicationInfo;
            if (appInfo != null) {
                info.put("target_sdk", appInfo.targetSdkVersion);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    info.put("min_sdk", appInfo.minSdkVersion);
                }
                info.put("debuggable", (appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);

                final CharSequence label = pm.getApplicationLabel(appInfo);
                if (label != null) {
                    info.put("app_name", label.toString());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to capture package info: " + e.getMessage());
        }

        try {
            final String installer = getInstallerPackageName(context.getPackageManager(), packageName);
            if (installer != null) {
                info.put("installer_package_name", installer);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to capture installer info: " + e.getMessage());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && context instanceof Application) {
            try {
                info.put("process_name", ((Application) context).getProcessName());
            } catch (Exception e) {
                Log.w(TAG, "Failed to capture process name: " + e.getMessage());
            }
        }

        return info;
    }

    /**
     * Captures information about the device and its current state: hardware/OS identification,
     * screen, memory, storage and battery.
     */
    @NonNull
    public static Map<String, Object> captureDeviceInfo(@NonNull Context context) {
        final Map<String, Object> info = new Hashtable<>();

        info.put("manufacturer", Build.MANUFACTURER);
        info.put("brand", Build.BRAND);
        info.put("model", Build.MODEL);
        info.put("device", Build.DEVICE);
        info.put("product", Build.PRODUCT);
        info.put("hardware", Build.HARDWARE);
        info.put("board", Build.BOARD);
        info.put("fingerprint", Build.FINGERPRINT);
        info.put("os_version", Build.VERSION.RELEASE);
        info.put("sdk_int", Build.VERSION.SDK_INT);
        if (Build.VERSION.CODENAME != null) {
            info.put("codename", Build.VERSION.CODENAME);
        }
        info.put("supported_abis", Arrays.asList(Build.SUPPORTED_ABIS));
        info.put("locale", Locale.getDefault().toString());
        info.put("timezone", TimeZone.getDefault().getID());
        info.put("available_processors", Runtime.getRuntime().availableProcessors());
        info.put("max_heap_bytes", Runtime.getRuntime().maxMemory());

        try {
            final Resources resources = context.getResources();
            final DisplayMetrics metrics = resources.getDisplayMetrics();
            info.put("screen_width_px", metrics.widthPixels);
            info.put("screen_height_px", metrics.heightPixels);
            info.put("screen_density", metrics.density);
            info.put("screen_density_dpi", metrics.densityDpi);

            final Configuration configuration = resources.getConfiguration();
            info.put("orientation", configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ? "landscape" : "portrait");
        } catch (Exception e) {
            Log.w(TAG, "Failed to capture screen info: " + e.getMessage());
        }

        try {
            final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                final ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
                activityManager.getMemoryInfo(memoryInfo);
                info.put("total_ram_bytes", memoryInfo.totalMem);
                info.put("available_ram_bytes", memoryInfo.availMem);
                info.put("low_memory", memoryInfo.lowMemory);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to capture memory info: " + e.getMessage());
        }

        try {
            final StatFs statFs = new StatFs(context.getFilesDir().getPath());
            info.put("total_storage_bytes", statFs.getTotalBytes());
            info.put("free_storage_bytes", statFs.getAvailableBytes());
        } catch (Exception e) {
            Log.w(TAG, "Failed to capture storage info: " + e.getMessage());
        }

        final Map<String, Object> battery = captureBatteryInfo(context);
        if (!battery.isEmpty()) {
            info.put("battery", battery);
        }

        return info;
    }

    @NonNull
    private static Map<String, Object> captureBatteryInfo(@NonNull Context context) {
        final Map<String, Object> battery = new Hashtable<>();

        try {
            // A null receiver just reads the current sticky broadcast state - no permission,
            // no actual receiver left registered.
            final Intent status = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (status != null) {
                final int level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                final int scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    battery.put("level_percent", Math.round(level * 100f / scale));
                }

                final int plugged = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                if (plugged >= 0) {
                    battery.put("charging", plugged != 0);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to capture battery info: " + e.getMessage());
        }

        return battery;
    }

    @NonNull
    private static PackageInfo getPackageInfo(@NonNull PackageManager pm, @NonNull String packageName) throws PackageManager.NameNotFoundException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
        }
        return getPackageInfoLegacy(pm, packageName);
    }

    // PackageManager#getPackageInfo(String, int) is deprecated in favor of the PackageInfoFlags
    // overload above, but that overload only exists from API 33 - minSdk here is 23.
    @SuppressWarnings("deprecation")
    @NonNull
    private static PackageInfo getPackageInfoLegacy(@NonNull PackageManager pm, @NonNull String packageName) throws PackageManager.NameNotFoundException {
        return pm.getPackageInfo(packageName, 0);
    }

    private static long getVersionCode(@NonNull PackageInfo packageInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return packageInfo.getLongVersionCode();
        }
        return getVersionCodeLegacy(packageInfo);
    }

    // PackageInfo#versionCode is deprecated in favor of getLongVersionCode(), but that only exists
    // from API 28 - minSdk here is 23.
    @SuppressWarnings("deprecation")
    private static long getVersionCodeLegacy(@NonNull PackageInfo packageInfo) {
        return packageInfo.versionCode;
    }

    // PackageManager#getInstallerPackageName(String) is deprecated in favor of
    // getInstallSourceInfo(String), but that only exists from API 30 - minSdk here is 23. It's
    // still fully functional (just deprecated) on newer API levels too, so one implementation
    // covers all of them.
    @SuppressWarnings("deprecation")
    private static String getInstallerPackageName(@NonNull PackageManager pm, @NonNull String packageName) {
        return pm.getInstallerPackageName(packageName);
    }
}
