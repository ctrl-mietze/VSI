package com.aefyr.sai.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

public final class VsiInstallProfileManager {

    private static final String CUSTOM_PROFILE = "vsi_custom_install_profile";
    private final SharedPreferences mPrefs;
    private final Gson mGson = new Gson();

    public VsiInstallProfileManager(Context context) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void applyStandard() {
        apply(new Profile(PreferencesValues.INSTALLER_ROOTLESS, false, false, -1, false, false, false));
    }

    public void applyRootPower() {
        apply(new Profile(PreferencesValues.INSTALLER_ROOTED, true, true, -1, false, false, false));
    }

    public void applyShizukuPower() {
        apply(new Profile(PreferencesValues.INSTALLER_SHIZUKU, true, true, -1, false, false, false));
    }

    public void applyXposed() {
        apply(new Profile(PreferencesValues.INSTALLER_XPOSED, false, false, -1, false, false, false));
    }

    public void applyCompatibility() {
        apply(new Profile(PreferencesValues.INSTALLER_ROOTLESS, false, false, -1, true, true, false));
    }

    public void saveCurrent() {
        Profile p = new Profile(
                mPrefs.getInt(PreferencesKeys.INSTALLER, PreferencesValues.INSTALLER_ROOTLESS),
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
            apply(p);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void apply(Profile p) {
        mPrefs.edit()
                .putInt(PreferencesKeys.INSTALLER, p.installer)
                .putBoolean(PreferencesKeys.ALLOW_DOWNGRADE, p.allowDowngrade)
                .putBoolean(PreferencesKeys.ALLOW_TEST_APKS, p.allowTest)
                .putString(PreferencesKeys.TARGET_USER_ID, String.valueOf(p.userId))
                .putBoolean(PreferencesKeys.EXTRACT_ARCHIVES, p.extractArchives)
                .putBoolean(PreferencesKeys.USE_ZIPFILE, p.useZipFile)
                .putBoolean(PreferencesKeys.SIGN_APKS, p.signApks)
                .apply();
    }

    private int parseUserId(String raw) {
        try { return Integer.parseInt(raw); }
        catch (Exception ignored) { return -1; }
    }

    private static class Profile {
        int installer;
        boolean allowDowngrade;
        boolean allowTest;
        int userId;
        boolean extractArchives;
        boolean useZipFile;
        boolean signApks;

        Profile(int installer, boolean allowDowngrade, boolean allowTest, int userId,
                boolean extractArchives, boolean useZipFile, boolean signApks) {
            this.installer = installer;
            this.allowDowngrade = allowDowngrade;
            this.allowTest = allowTest;
            this.userId = userId;
            this.extractArchives = extractArchives;
            this.useZipFile = useZipFile;
            this.signApks = signApks;
        }
    }
}
