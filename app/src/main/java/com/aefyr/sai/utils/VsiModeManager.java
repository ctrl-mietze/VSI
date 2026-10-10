package com.aefyr.sai.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.aefyr.sai.service.VsiLiteService;
import com.aefyr.sai.service.VsiSystemService;
import com.aefyr.sai.service.VsiVService;
import com.aefyr.sai.shell.SuShell;
import com.aefyr.sai.xposed.VsiXposedRuntimeProbe;

import rikka.shizuku.Shizuku;

public final class VsiModeManager {

    private VsiModeManager() {}

    public static VsiAppMode getMode(Context context) {
        String raw = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PreferencesKeys.VSI_APP_MODE, VsiAppMode.NORMAL.value());
        return VsiAppMode.fromValue(raw);
    }

    public static void applyMode(Context context, VsiAppMode mode) {
        android.content.SharedPreferences.Editor editor =
                PreferenceManager.getDefaultSharedPreferences(context)
                        .edit()
                        .putString(PreferencesKeys.VSI_APP_MODE, mode.value())
                        .putInt(PreferencesKeys.INSTALLER, mode.installerBackend());

        if (mode != VsiAppMode.ROOT_XPOSED) {
            editor.putBoolean(
                    VsiDeveloperKeys.SIGNATURE_VERIFICATION_OVERRIDE,
                    false
            );
            editor.putLong(PreferencesKeys.SECURITY_POLICY_TIMESTAMP, 0L);
        }

        editor.apply();

        startCorrectService(context, mode);
    }

    public static boolean isAvailable(Context context, VsiAppMode mode) {
        switch (mode) {
            case NORMAL:
                return true;
            case SHIZUKU:
                if (!Shizuku.pingBinder())
                    return false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && !Shizuku.isPreV11()
                        && Shizuku.getVersion() >= 11) {
                    return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
                }
                return true;
            case XPOSED:
                return VsiXposedRuntimeProbe.isActive();
            case ROOT:
                return SuShell.getInstance().isAvailable();
            case ROOT_XPOSED:
                return SuShell.getInstance().isAvailable() && VsiXposedRuntimeProbe.isActive();
            default:
                return false;
        }
    }

    public static boolean requestRootIfNeeded(VsiAppMode mode) {
        if (!mode.hasRoot())
            return true;
        return SuShell.getInstance().requestRoot();
    }

    public static void startCorrectService(Context context) {
        startCorrectService(context, getMode(context));
    }

    public static void startCorrectService(Context context, VsiAppMode mode) {
        Context app = context.getApplicationContext();

        if (!VsiBatteryOptimizationHelper.isExempt(app)) {
            app.stopService(new Intent(app, VsiLiteService.class));
            app.stopService(new Intent(app, VsiVService.class));
            app.stopService(new Intent(app, VsiSystemService.class));
            return;
        }

        Class<?> serviceClass;
        switch (mode.serviceKind()) {
            case VSERVICE:
                serviceClass = VsiVService.class;
                app.stopService(new Intent(app, VsiLiteService.class));
                app.stopService(new Intent(app, VsiSystemService.class));
                break;
            case SYSTEM:
                serviceClass = VsiSystemService.class;
                app.stopService(new Intent(app, VsiLiteService.class));
                app.stopService(new Intent(app, VsiVService.class));
                break;
            case LITE:
            default:
                serviceClass = VsiLiteService.class;
                app.stopService(new Intent(app, VsiVService.class));
                app.stopService(new Intent(app, VsiSystemService.class));
                break;
        }

        Intent service = new Intent(app, serviceClass);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ContextCompat.startForegroundService(app, service);
        else
            app.startService(service);
    }
}
