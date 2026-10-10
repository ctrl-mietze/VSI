package com.aefyr.sai.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

public final class VsiBatteryOptimizationHelper {

    private VsiBatteryOptimizationHelper() {}

    public static boolean isExempt(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return true;

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    public static Intent buildRequestIntent(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return null;

        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        return intent;
    }

    public static Intent buildSettingsIntent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return null;
        return new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
    }
}
