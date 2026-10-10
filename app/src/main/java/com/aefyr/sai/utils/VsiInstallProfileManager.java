package com.aefyr.sai.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

public final class VsiInstallProfileManager {

    private static final String CUSTOM_PROFILE = "vsi_custom_install_profile";

    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final Gson mGson = new Gson();

    public VsiInstallProfileManager(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public void applyStandard() {
        apply(new Profile(
                VsiAppMode.NORMAL,
                false,
                false,
                -1,
                false,
                false,
                false
        ));
    }

    public void applyRootPower() {
        apply(new Profile(
                VsiAppMode.ROOT,
                true,
                true,
                -1,
                false,
                false,
                false
        ));
    }

    public void applyRootXposedPower() {
        apply(new Profile(
                VsiAppMode.ROOT_XPOSED,
                true,
                true,
                -1,
                false,
                false,
                false
        ));
    }

    public void applyShizukuPower() {
        apply(new Profile(
                VsiAppMode.SHIZUKU,
                true,
                true,
                -1,
                false,
                false,
                false
        ));
    }

    public void applyXposed() {
        apply(new Profile(
                VsiAppMode.XPOSED,
                false,
                false,
                -1,
                false,
                false,
                false
        ));
    }

    public void applyCompatibility() {
        apply(new Profile(
                VsiAppMode.NORMAL,
                false,
                false,
                -1,
                true,
                true,
                false
        ));
    }

    public void saveCurrent() {
        Profile profile = new Profile(
                VsiModeManager.getMode(mContext),
                mPrefs.getBoolean(PreferencesKeys.ALLOW_DOWNGRADE, false),
                mPrefs.getBoolean(PreferencesKeys.ALLOW_TEST_APKS, false),
                parseUserId(mPrefs.getString(PreferencesKeys.TARGET_USER_ID, "-1")),
                mPrefs.getBoolean(PreferencesKeys.EXTRACT_ARCHIVES, false),
                mPrefs.getBoolean(PreferencesKeys.USE_ZIPFILE, false),
                mPrefs.getBoolean(PreferencesKeys.SIGN_APKS, false)
        );

        mPrefs.edit()
                .putString(CUSTOM_PROFILE, mGson.toJson(profile))
                .apply();
    }

    public boolean applyCustom() {
        String json = mPrefs.getString(CUSTOM_PROFILE, null);
        if (json == null)
            return false;

        try {
            Profile profile = mGson.fromJson(json, Profile.class);
            if (profile == null)
                return false;

            apply(profile);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void apply(Profile profile) {
        VsiAppMode mode = resolveMode(profile);

        mPrefs.edit()
                .putBoolean(PreferencesKeys.ALLOW_DOWNGRADE, profile.allowDowngrade)
                .putBoolean(PreferencesKeys.ALLOW_TEST_APKS, profile.allowTest)
                .putString(PreferencesKeys.TARGET_USER_ID, String.valueOf(profile.userId))
                .putBoolean(PreferencesKeys.EXTRACT_ARCHIVES, profile.extractArchives)
                .putBoolean(PreferencesKeys.USE_ZIPFILE, profile.useZipFile)
                .putBoolean(PreferencesKeys.SIGN_APKS, profile.signApks)
                .apply();

        // Keep App Mode, installer backend, service and Xposed state synchronized.
        VsiModeManager.applyMode(mContext, mode);
    }

    private VsiAppMode resolveMode(Profile profile) {
        if (profile.appMode != null && !profile.appMode.trim().isEmpty())
            return VsiAppMode.fromValue(profile.appMode);

        // Migration path for custom profiles saved by VSI 0.1.x.
        switch (profile.installer) {
            case PreferencesValues.INSTALLER_ROOTED:
                return VsiAppMode.ROOT;
            case PreferencesValues.INSTALLER_SHIZUKU:
                return VsiAppMode.SHIZUKU;
            case PreferencesValues.INSTALLER_XPOSED:
                return VsiAppMode.XPOSED;
            case PreferencesValues.INSTALLER_ROOTLESS:
            default:
                return VsiAppMode.NORMAL;
        }
    }

    private int parseUserId(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static final class Profile {
        String appMode;

        // Kept for backward compatibility with custom profiles from 0.1.x.
        int installer;

        boolean allowDowngrade;
        boolean allowTest;
        int userId;
        boolean extractArchives;
        boolean useZipFile;
        boolean signApks;

        Profile(
                VsiAppMode mode,
                boolean allowDowngrade,
                boolean allowTest,
                int userId,
                boolean extractArchives,
                boolean useZipFile,
                boolean signApks
        ) {
            appMode = mode.value();
            installer = mode.installerBackend();
            this.allowDowngrade = allowDowngrade;
            this.allowTest = allowTest;
            this.userId = userId;
            this.extractArchives = extractArchives;
            this.useZipFile = useZipFile;
            this.signApks = signApks;
        }
    }
}
