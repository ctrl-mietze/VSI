package com.aefyr.sai.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import androidx.preference.PreferenceManager;

import com.github.angads25.filepicker.model.DialogConfigs;

public class PreferencesHelper {
    private static PreferencesHelper sInstance;

    private SharedPreferences mPrefs;

    public static PreferencesHelper getInstance(Context c) {
        return sInstance != null ? sInstance : new PreferencesHelper(c);
    }

    private PreferencesHelper(Context c) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(c);
        sInstance = this;
    }

    public SharedPreferences getPrefs() {
        return mPrefs;
    }

    public String getHomeDirectory() {
        return mPrefs.getString(PreferencesKeys.HOME_DIRECTORY, Environment.getExternalStorageDirectory().getAbsolutePath());
    }

    public void setHomeDirectory(String homeDirectory) {
        mPrefs.edit().putString(PreferencesKeys.HOME_DIRECTORY, homeDirectory).apply();
    }

    public int getFilePickerRawSort() {
        return mPrefs.getInt(PreferencesKeys.FILE_PICKER_SORT_RAW, 0);
    }

    public void setFilePickerRawSort(int rawSort) {
        mPrefs.edit().putInt(PreferencesKeys.FILE_PICKER_SORT_RAW, rawSort).apply();
    }

    public int getFilePickerSortBy() {
        return mPrefs.getInt(PreferencesKeys.FILE_PICKER_SORT_BY, DialogConfigs.SORT_BY_NAME);
    }

    public void setFilePickerSortBy(int sortBy) {
        mPrefs.edit().putInt(PreferencesKeys.FILE_PICKER_SORT_BY, sortBy).apply();
    }

    public int getFilePickerSortOrder() {
        return mPrefs.getInt(PreferencesKeys.FILE_PICKER_SORT_ORDER, DialogConfigs.SORT_ORDER_NORMAL);
    }

    public void setFilePickerSortOrder(int sortOrder) {
        mPrefs.edit().putInt(PreferencesKeys.FILE_PICKER_SORT_ORDER, sortOrder).apply();
    }

    public boolean shouldSignApks() {
        return mPrefs.getBoolean(PreferencesKeys.SIGN_APKS, false);
    }

    public void setShouldSignApks(boolean signApks) {
        mPrefs.edit().putBoolean(PreferencesKeys.SIGN_APKS, signApks).apply();
    }

    public boolean shouldExtractArchives() {
        return mPrefs.getBoolean(PreferencesKeys.EXTRACT_ARCHIVES, false);
    }

    public boolean shouldUseZipFileApi() {
        return mPrefs.getBoolean(PreferencesKeys.USE_ZIPFILE, false);
    }

    public void setInstaller(int installer) {
        mPrefs.edit().putInt(PreferencesKeys.INSTALLER, installer).apply();
    }

    public int getInstaller() {
        return mPrefs.getInt(PreferencesKeys.INSTALLER, PreferencesValues.INSTALLER_ROOTLESS);
    }

    public boolean shouldAllowDowngrade() {
        return mPrefs.getBoolean(PreferencesKeys.ALLOW_DOWNGRADE, false);
    }

    public boolean shouldAllowTestApks() {
        return mPrefs.getBoolean(PreferencesKeys.ALLOW_TEST_APKS, false);
    }

    /**
     * -1 means current Android user.
     */
    public int getTargetUserId() {
        String rawUserId = mPrefs.getString(PreferencesKeys.TARGET_USER_ID, "-1");
        try {
            return Integer.parseInt(rawUserId);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public boolean shouldDeleteSourceAfterInstall() {
        return mPrefs.getBoolean(PreferencesKeys.DELETE_SOURCE_AFTER_INSTALL, false);
    }

    public void setBackupFileNameFormat(String format) {
        mPrefs.edit().putString(PreferencesKeys.BACKUP_FILE_NAME_FORMAT, format).apply();
    }

    public String getBackupFileNameFormat() {
        return mPrefs.getString(PreferencesKeys.BACKUP_FILE_NAME_FORMAT, PreferencesValues.BACKUP_FILE_NAME_FORMAT_DEFAULT);
    }

    public void setInstallLocation(int installLocation) {
        mPrefs.edit().putString(PreferencesKeys.INSTALL_LOCATION, String.valueOf(installLocation)).apply();
    }

    public int getInstallLocation() {
        String rawInstallLocation = mPrefs.getString(PreferencesKeys.INSTALL_LOCATION, "0");
        try {
            return Integer.parseInt(rawInstallLocation);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean useOldInstaller() {
        return mPrefs.getBoolean(PreferencesKeys.USE_OLD_INSTALLER, false);
    }

    public String getInstallerFeedbackMode() {
        if (mPrefs.contains(PreferencesKeys.INSTALLER_FEEDBACK_MODE)) {
            return mPrefs.getString(
                    PreferencesKeys.INSTALLER_FEEDBACK_MODE,
                    VsiInstallerFeedback.MODE_DIALOG
            );
        }

        // Automatic migration from the old boolean setting.
        boolean oldDialogs = mPrefs.getBoolean(PreferencesKeys.SHOW_INSTALLER_DIALOGS, true);
        String migrated = oldDialogs
                ? VsiInstallerFeedback.MODE_DIALOG
                : VsiInstallerFeedback.MODE_NONE;

        mPrefs.edit()
                .putString(PreferencesKeys.INSTALLER_FEEDBACK_MODE, migrated)
                .apply();

        return migrated;
    }

    @Deprecated
    public boolean showInstallerDialogs() {
        return VsiInstallerFeedback.MODE_DIALOG.equals(getInstallerFeedbackMode());
    }

    public String getLanguageMode() {
        return mPrefs.getString(PreferencesKeys.VSI_LANGUAGE, VsiLocaleHelper.MODE_SYSTEM);
    }

    public boolean shouldShowAppFeatures() {
        return mPrefs.getBoolean(PreferencesKeys.SHOW_APP_FEATURES, true);
    }

    public boolean shouldShowSafTip() {
        return !mPrefs.getBoolean(PreferencesKeys.SAF_TIP_SHOWN, false);
    }

    public void setSafTipShown() {
        mPrefs.edit().putBoolean(PreferencesKeys.SAF_TIP_SHOWN, true).apply();
    }

    public boolean isInstallerXEnabled() {
        return mPrefs.getBoolean(PreferencesKeys.USE_INSTALLERX, true);
    }

    public boolean isBruteParserEnabled() {
        return mPrefs.getBoolean(PreferencesKeys.USE_BRUTE_PARSER, true);
    }

    public boolean isAnalyticsEnabled() {
        return mPrefs.getBoolean(PreferencesKeys.ENABLE_ANALYTICS, true);
    }

    public void setAnalyticsEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PreferencesKeys.ENABLE_ANALYTICS, enabled).apply();
    }

    public boolean isInitialIndexingDone() {
        return mPrefs.getBoolean(PreferencesKeys.INITIAL_INDEXING_RUN, false);
    }

    public void setInitialIndexingDone(boolean done) {
        mPrefs.edit().putBoolean(PreferencesKeys.INITIAL_INDEXING_RUN, done).apply();
    }

    public boolean isSingleApkExportEnabled() {
        return mPrefs.getBoolean(PreferencesKeys.BACKUP_APK_EXPORT, false);
    }

    public void setSingleApkExportEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PreferencesKeys.BACKUP_APK_EXPORT, enabled).apply();
    }

}
