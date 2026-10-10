package com.aefyr.sai.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public final class VsiDeveloperOptions {

    private static VsiDeveloperOptions sInstance;
    private final SharedPreferences mPrefs;

    public static VsiDeveloperOptions getInstance(Context context) {
        synchronized (VsiDeveloperOptions.class) {
            if (sInstance == null)
                sInstance = new VsiDeveloperOptions(context.getApplicationContext());
            return sInstance;
        }
    }

    private VsiDeveloperOptions(Context context) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    private boolean b(String key, boolean def) {
        return mPrefs.getBoolean(key, def);
    }

    public boolean expertCommandPreview() { return b(VsiDeveloperKeys.EXPERT_COMMAND_PREVIEW, false); }
    public boolean verboseInstallLogs() { return b(VsiDeveloperKeys.VERBOSE_INSTALL_LOGS, false); }
    public boolean rootSystemInstallerBridge() { return b(VsiDeveloperKeys.ROOT_SYSTEM_INSTALLER_BRIDGE, false); }
    public boolean forceDowngrade() { return b(VsiDeveloperKeys.FORCE_DOWNGRADE, false); }
    public boolean forceTestOnly() { return b(VsiDeveloperKeys.FORCE_TEST_ONLY, false); }
    public boolean signatureGuard() { return b(VsiDeveloperKeys.SIGNATURE_GUARD, true); }
    public boolean allowSignatureReplacement() { return b(VsiDeveloperKeys.ALLOW_SIGNATURE_REPLACEMENT, false); }
    public boolean quickInstallSheet() { return b(VsiDeveloperKeys.QUICK_INSTALL_SHEET, true); }
    public boolean updateCompare() { return b(VsiDeveloperKeys.UPDATE_COMPARE, true); }
    public boolean sessionHistory() { return b(VsiDeveloperKeys.SESSION_HISTORY, true); }
    public boolean installQueue() { return b(VsiDeveloperKeys.INSTALL_QUEUE, true); }
    public boolean splitIntelligence() { return b(VsiDeveloperKeys.SPLIT_INTELLIGENCE, true); }
    public boolean autoSelectRecommended() { return b(VsiDeveloperKeys.AUTO_SELECT_RECOMMENDED, true); }
    public boolean allowIncompatibleSplits() { return b(VsiDeveloperKeys.ALLOW_INCOMPATIBLE_SPLITS, false); }
    public boolean strictValidation() { return b(VsiDeveloperKeys.STRICT_VALIDATION, true); }
    public boolean autoRepairContainers() { return b(VsiDeveloperKeys.AUTO_REPAIR_CONTAINERS, false); }
    public boolean probeUnknownContainers() { return b(VsiDeveloperKeys.PROBE_UNKNOWN_CONTAINERS, true); }
    public boolean neverDeleteSource() { return b(VsiDeveloperKeys.NEVER_DELETE_SOURCE, false); }
    public boolean keepFailedSessions() { return b(VsiDeveloperKeys.KEEP_FAILED_SESSIONS, true); }
    public boolean keepSuccessSessions() { return b(VsiDeveloperKeys.KEEP_SUCCESS_SESSIONS, true); }
    public boolean xposedVerbose() { return b(VsiDeveloperKeys.XPOSED_VERBOSE, false); }
    public boolean xposedStockFallback() { return b(VsiDeveloperKeys.XPOSED_STOCK_FALLBACK, true); }
    public boolean showSourceIdentity() { return b(VsiDeveloperKeys.SHOW_SOURCE_IDENTITY, true); }
    public boolean advancedApkAnalysis() { return b(VsiDeveloperKeys.ADVANCED_APK_ANALYSIS, false); }
    public boolean signatureVerificationOverride() {
        if (!b(VsiDeveloperKeys.SIGNATURE_VERIFICATION_OVERRIDE, false))
            return false;

        long timestamp = mPrefs.getLong(PreferencesKeys.SECURITY_POLICY_TIMESTAMP, 0L);
        long elapsed = System.currentTimeMillis() - timestamp;

        if (timestamp <= 0L || elapsed < 0L || elapsed >= 3 * 60 * 1000L) {
            mPrefs.edit()
                    .putBoolean(VsiDeveloperKeys.SIGNATURE_VERIFICATION_OVERRIDE, false)
                    .putLong(PreferencesKeys.SECURITY_POLICY_TIMESTAMP, 0L)
                    .apply();
            return false;
        }

        return true;
    }
    public boolean permanentKernelRootFunctions() { return b(VsiDeveloperKeys.PERMANENT_KERNEL_ROOT_FUNCTIONS, false); }

    public int queueParallelism() {
        String raw = mPrefs.getString(VsiDeveloperKeys.QUEUE_PARALLELISM, "1");
        try {
            return Math.max(1, Math.min(4, Integer.parseInt(raw)));
        } catch (Exception ignored) {
            return 1;
        }
    }

    public String customInstallCreateCommand() {
        String raw = mPrefs.getString(VsiDeveloperKeys.CUSTOM_INSTALL_CREATE, "");
        if (raw == null || raw.trim().isEmpty())
            return null;
        return raw.trim();
    }
}
