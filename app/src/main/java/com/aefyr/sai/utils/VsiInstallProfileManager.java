package com.aefyr.sai.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.aefyr.sai.runtime.VsiAppMode;
import com.aefyr.sai.runtime.VsiModeManager;
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
        apply(new Profile(VsiAppMode.NORMAL.id(), false, false, -1, false, false, false));
    }

    public void applyRootPower() {
        apply(new Profile(VsiAppMode.ROOT.id(), true, true, -1, false, false, false));
    }

    public void applyShizukuPower() {
        apply(new Profile(VsiAppMode.SHIZUKU.id(), true, true, -1, false, false, false));
    }

    public void applyXposed() {
        apply(new Profile(VsiAppMode.XPOSED.id(), false, false, -1, false, false, false));
    }

    public void applyCompatibility() {
        apply(new Profile(VsiAppMode.NORMAL.id(), false, false, -1, true, true, false));
    }

    public void saveCurrent() {
        Profile p = new Profile(
                VsiModeManager.getCurrentMode(mContext).id(),
                mPrefs.getBoolean(PreferencesKeys.ALLOW_DOWNGRADE, false),
                mPrefs.getBoolean(PreferencesKeys.ALLOW_TEST_APKS, false),
                parseUserId(mPrefs.getString(PreferencesKeys.TARGET_USER_ID, "-1")),
                mPrefs.getBoolean(PreferencesKeys.EXTRACT_ARCHIVES, false),
                mPrefs.getBoolean(PreferencesKeys.USE_ZIPFILE, false),
                mPrefs.getBoolean(PreferencesKeys.SIGN_APKS, false)
        );
        mPrefs.edit().putString(CUSTOM_PROFILE, mGson.toJson(p)).apply();
    }

    public boolean applyCustom() {
        String json = mPrefs.getString(CUSTOM_PROFILE, null);
        if (json == null)
            return false;

        try {
            Profile p = mGson.fromJson(json, Profile.class);
            if (p == null)
                return false;

            if (p.mode == null)
                p.mode = modeFromLegacyInstaller(p.installer).id();

            apply(p);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void apply(Profile p) {
        VsiAppMode mode = VsiAppMode.fromId(p.mode);
        VsiModeManager.Availability availability =
                VsiModeManager.applyMode(mContext, mode);

        if (!availability.available)
            throw new IllegalStateException(availability.reason);

        mPrefs.edit()
                .putBoolean(PreferencesKeys.ALLOW_DOWNGRADE, p.allowDowngrade)
                .putBoolean(PreferencesKeys.ALLOW_TEST_APKS, p.allowTest)
                .putString(PreferencesKeys.TARGET_USER_ID, String.valueOf(p.userId))
                .putBoolean(PreferencesKeys.EXTRACT_ARCHIVES, p.extractArchives)
                .putBoolean(PreferencesKeys.USE_ZIPFILE, p.useZipFile)
                .putBoolean(PreferencesKeys.SIGN_APKS, p.signApks)
                .apply();
    }

    private VsiAppMode modeFromLegacyInstaller(int installer) {
        switch (installer) {
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

    private static class Profile {
        String mode;

        // Kept only so old saved custom profiles can still be migrated.
        int installer;

        boolean allowDowngrade;
        boolean allowTest;
        int userId;
        boolean extractArchives;
        boolean useZipFile;
        boolean signApks;

        Profile(
                String mode,
                boolean allowDowngrade,
                boolean allowTest,
                int userId,
                boolean extractArchives,
                boolean useZipFile,
                boolean signApks
        ) {
            this.mode = mode;
            this.allowDowngrade = allowDowngrade;
            this.allowTest = allowTest;
            this.userId = userId;
            this.extractArchives = extractArchives;
            this.useZipFile = useZipFile;
            this.signApks = signApks;
        }
    }
}
