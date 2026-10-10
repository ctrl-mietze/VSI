package com.aefyr.sai.runtime;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public final class VsiSecurityWindowManager {

    public static final long WINDOW_MS = 3 * 60 * 1000L;

    private static final String KEY_DOWNGRADE_UNTIL = "vsi_security_downgrade_until";
    private static final String KEY_SIGNATURE_UNTIL = "vsi_security_signature_until";

    private VsiSecurityWindowManager() {}

    public static long armDowngrade(Context context) {
        long until = System.currentTimeMillis() + WINDOW_MS;
        prefs(context).edit().putLong(KEY_DOWNGRADE_UNTIL, until).apply();
        return until;
    }

    public static long armSignatureOverride(Context context) {
        long until = System.currentTimeMillis() + WINDOW_MS;
        prefs(context).edit().putLong(KEY_SIGNATURE_UNTIL, until).apply();
        return until;
    }

    public static void disarmAll(Context context) {
        prefs(context).edit()
                .remove(KEY_DOWNGRADE_UNTIL)
                .remove(KEY_SIGNATURE_UNTIL)
                .apply();
    }

    public static long downgradeUntil(Context context) {
        return prefs(context).getLong(KEY_DOWNGRADE_UNTIL, 0L);
    }

    public static long signatureUntil(Context context) {
        return prefs(context).getLong(KEY_SIGNATURE_UNTIL, 0L);
    }

    public static boolean isDowngradeActive(Context context) {
        return System.currentTimeMillis() < downgradeUntil(context);
    }

    public static boolean isSignatureOverrideActive(Context context) {
        return System.currentTimeMillis() < signatureUntil(context);
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(
                context.getApplicationContext()
        );
    }
}
