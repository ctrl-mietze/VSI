package com.aefyr.sai.runtime;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.preference.PreferenceManager;

import com.aefyr.sai.shell.SuShell;
import com.aefyr.sai.utils.PreferencesKeys;
import com.aefyr.sai.utils.PreferencesValues;
import com.aefyr.sai.xposed.VsiXposedRuntimeProbe;

import rikka.shizuku.Shizuku;

public final class VsiModeManager {

    public static final String KEY_APP_MODE = "vsi_app_mode";

    private VsiModeManager() {}

    public static VsiAppMode getCurrentMode(Context context) {
        String stored = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(KEY_APP_MODE, null);

        if (stored != null)
            return VsiAppMode.fromId(stored);

        int installer = PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(PreferencesKeys.INSTALLER, PreferencesValues.INSTALLER_ROOTLESS);

        VsiAppMode migrated;
        switch (installer) {
            case PreferencesValues.INSTALLER_SHIZUKU:
                migrated = VsiAppMode.SHIZUKU;
                break;
            case PreferencesValues.INSTALLER_XPOSED:
                migrated = VsiAppMode.XPOSED;
                break;
            case PreferencesValues.INSTALLER_ROOTED:
                migrated = VsiAppMode.ROOT;
                break;
            case PreferencesValues.INSTALLER_ROOTLESS:
            default:
                migrated = VsiAppMode.NORMAL;
                break;
        }

        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(KEY_APP_MODE, migrated.id())
                .apply();

        return migrated;
    }

    public static Availability checkAvailability(Context context, VsiAppMode mode) {
        switch (mode) {
            case SHIZUKU:
                if (!Shizuku.pingBinder())
                    return Availability.unavailable("Shizuku service is not running.");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && !Shizuku.isPreV11()
                        && Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return Availability.unavailable("Shizuku permission is not granted to VSI.");
                }
                return Availability.available();

            case XPOSED:
                return VsiXposedRuntimeProbe.isActive()
                        ? Availability.available()
                        : Availability.unavailable("Xposed / Vector is not active for VSI.");

            case ROOT:
                return SuShell.getInstance().requestRoot()
                        ? Availability.available()
                        : Availability.unavailable("Root access is not available.");

            case ROOT_XPOSED:
                if (!SuShell.getInstance().requestRoot())
                    return Availability.unavailable("Root access is not available.");
                if (!VsiXposedRuntimeProbe.isActive())
                    return Availability.unavailable("Xposed / Vector is not active for VSI.");
                return Availability.available();

            case NORMAL:
            default:
                return Availability.available();
        }
    }

    public static Availability applyMode(Context context, VsiAppMode mode) {
        Availability availability = checkAvailability(context, mode);
        if (!availability.available)
            return availability;

        int backend;
        switch (mode) {
            case SHIZUKU:
                backend = PreferencesValues.INSTALLER_SHIZUKU;
                break;
            case XPOSED:
                backend = PreferencesValues.INSTALLER_XPOSED;
                break;
            case ROOT:
            case ROOT_XPOSED:
                backend = PreferencesValues.INSTALLER_ROOTED;
                break;
            case NORMAL:
            default:
                backend = PreferencesValues.INSTALLER_ROOTLESS;
                break;
        }

        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(KEY_APP_MODE, mode.id())
                .putInt(PreferencesKeys.INSTALLER, backend)
                .apply();

        VsiRuntimeServices.sync(context.getApplicationContext(), mode);
        return Availability.available();
    }

    public static boolean isBatteryOptimizationDisabled(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null
                && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    public static void requestBatteryOptimizationExemption(Context context) {
        Intent direct = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:" + context.getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(direct);
            return;
        } catch (Exception ignored) {
        }

        Intent fallback = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(fallback);
        } catch (Exception ignored) {
        }
    }

    public static final class Availability {
        public final boolean available;
        public final String reason;

        private Availability(boolean available, String reason) {
            this.available = available;
            this.reason = reason;
        }

        public static Availability available() {
            return new Availability(true, null);
        }

        public static Availability unavailable(String reason) {
            return new Availability(false, reason);
        }
    }
}
