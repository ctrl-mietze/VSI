package com.aefyr.sai.ui.fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import com.aefyr.sai.BuildConfig;
import com.aefyr.sai.R;
import com.aefyr.sai.analytics.AnalyticsProvider;
import com.aefyr.sai.analytics.DefaultAnalyticsProvider;
import com.aefyr.sai.shell.SuShell;
import com.aefyr.sai.ui.activities.AboutActivity;
import com.aefyr.sai.ui.activities.ApkActionViewProxyActivity;
import com.aefyr.sai.ui.activities.BackupSettingsActivity;
import com.aefyr.sai.ui.activities.PreferencesActivity;
import com.aefyr.sai.ui.dialogs.DarkLightThemeSelectionDialogFragment;
import com.aefyr.sai.ui.dialogs.FilePickerDialogFragment;
import com.aefyr.sai.ui.dialogs.SimpleAlertDialogFragment;
import com.aefyr.sai.ui.dialogs.SingleChoiceListDialogFragment;
import com.aefyr.sai.ui.dialogs.ThemeSelectionDialogFragment;
import com.aefyr.sai.ui.dialogs.base.BaseBottomSheetDialogFragment;
import com.aefyr.sai.utils.AlertsUtils;
import com.aefyr.sai.utils.PermissionsUtils;
import com.aefyr.sai.utils.PreferencesHelper;
import com.aefyr.sai.utils.PreferencesKeys;
import com.aefyr.sai.utils.Theme;
import com.aefyr.sai.utils.VsiAppMode;
import com.aefyr.sai.utils.VsiBatteryOptimizationHelper;
import com.aefyr.sai.utils.VsiModeManager;
import com.aefyr.sai.utils.Utils;
import com.aefyr.sai.xposed.VsiXposedRuntimeProbe;
import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;

import java.io.File;
import java.util.List;
import java.util.Objects;

import rikka.shizuku.Shizuku;

public class PreferencesFragment extends PreferenceFragmentCompat implements FilePickerDialogFragment.OnFilesSelectedListener, SingleChoiceListDialogFragment.OnItemSelectedListener, BaseBottomSheetDialogFragment.OnDismissListener, SharedPreferences.OnSharedPreferenceChangeListener, DarkLightThemeSelectionDialogFragment.OnDarkLightThemesChosenListener, Shizuku.OnRequestPermissionResultListener {

    private PreferencesHelper mHelper;
    private AnalyticsProvider mAnalyticsProvider;
    private PackageManager mPm;

    private Preference mHomeDirPref;
    private Preference mFilePickerSortPref;
    private Preference mInstallerPref;
    private Preference mBatteryOptimizationPref;
    private VsiAppMode mPendingModeAfterShizuku;
    private Preference mThemePref;
    private SwitchPreference mAutoThemeSwitch;
    private Preference mAutoThemePicker;

    private FilePickerDialogFragment mPendingFilePicker;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        mHelper = PreferencesHelper.getInstance(requireContext());
        // Migrate the former boolean installer-dialog setting into the new
        // four-way feedback selector before preferences are inflated.
        mHelper.getInstallerFeedbackMode();

        mAnalyticsProvider = DefaultAnalyticsProvider.getInstance(requireContext());
        mPm = requireContext().getPackageManager();

        //Inject some prefs
        //Inject current auto theme status since it isn't managed by PreferencesKeys.AUTO_THEME key
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putBoolean(PreferencesKeys.AUTO_THEME, Theme.getInstance(requireContext()).getThemeMode() == Theme.Mode.AUTO_LIGHT_DARK).apply();

        //Inject apk proxy activity state since there's no guarantee preference value matches actual state
        int apkProxyActivityState = mPm.getComponentEnabledSetting(ApkActionViewProxyActivity.getComponentName(requireContext()));
        boolean isApkProxyActivityEnabled;
        switch (apkProxyActivityState) {
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                isApkProxyActivityEnabled = true;
                break;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
                isApkProxyActivityEnabled = false;
                break;
            default:
                throw new IllegalStateException(String.format("ApkProxyActivity state is %d", apkProxyActivityState));
        }
        prefsEditor.putBoolean(PreferencesKeys.ENABLE_APK_ACTION_VIEW, isApkProxyActivityEnabled);

        prefsEditor.apply();


        if (Utils.apiIsAtLeast(Build.VERSION_CODES.M)) {
            Shizuku.addRequestPermissionResultListener(this);
        }

        super.onCreate(savedInstanceState);
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);

        mHomeDirPref = findPreference("home_directory");
        updateHomeDirPrefSummary();
        mHomeDirPref.setOnPreferenceClickListener((p) -> {
            selectHomeDir();
            return true;
        });

        mFilePickerSortPref = findPreference("file_picker_sort");
        updateFilePickerSortSummary();
        mFilePickerSortPref.setOnPreferenceClickListener((p) -> {
            SingleChoiceListDialogFragment.newInstance(getText(R.string.settings_main_file_picker_sort), R.array.file_picker_sort_variants, mHelper.getFilePickerRawSort()).show(getChildFragmentManager(), "sort");
            return true;
        });

        findPreference("about").setOnPreferenceClickListener((p) -> {
            startActivity(new Intent(getContext(), AboutActivity.class));
            return true;
        });

        Preference vsiTools = findPreference("vsi_tools");
        if (vsiTools != null) {
            vsiTools.setOnPreferenceClickListener(p -> {
                PreferencesActivity.open(
                        requireContext(),
                        VsiToolsFragment.class,
                        getString(R.string.vsi_tools_title)
                );
                return true;
            });
        }

        Preference developerOptions = findPreference("vsi_developer_options");
        if (developerOptions != null) {
            developerOptions.setOnPreferenceClickListener(p -> {
                PreferencesActivity.open(
                        requireContext(),
                        VsiDeveloperOptionsFragment.class,
                        getString(R.string.vsi_developer_options)
                );
                return true;
            });
        }

        mInstallerPref = findPreference(PreferencesKeys.VSI_APP_MODE);
        updateInstallerSummary();
        mInstallerPref.setOnPreferenceClickListener((p -> {
            SingleChoiceListDialogFragment.newInstance(
                    getText(R.string.settings_main_app_mode),
                    R.array.vsi_app_modes,
                    VsiModeManager.getMode(requireContext()).ordinal()
            ).show(getChildFragmentManager(), "app_mode");
            return true;
        }));

        Preference modeInfo = findPreference("vsi_mode_info");
        if (modeInfo != null) {
            modeInfo.setOnPreferenceClickListener(p -> {
                PreferencesActivity.open(
                        requireContext(),
                        VsiModeInfoFragment.class,
                        getString(R.string.vsi_mode_info_title)
                );
                return true;
            });
        }

        mBatteryOptimizationPref = findPreference("vsi_battery_optimization");
        updateBatteryOptimizationSummary();
        if (mBatteryOptimizationPref != null) {
            mBatteryOptimizationPref.setOnPreferenceClickListener(p -> {
                Intent intent = VsiBatteryOptimizationHelper.buildRequestIntent(requireContext());
                if (intent != null)
                    startActivity(intent);
                return true;
            });
        }

        EditTextPreference targetUserPref = Objects.requireNonNull(findPreference(PreferencesKeys.TARGET_USER_ID));
        targetUserPref.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
            editText.setSelectAllOnFocus(true);
        });
        targetUserPref.setSummaryProvider(preference -> {
            String value = ((EditTextPreference) preference).getText();
            if (value == null || value.trim().isEmpty() || "-1".equals(value.trim()))
                return getString(R.string.settings_main_target_user_current_summary);
            return getString(R.string.settings_main_target_user_specific_summary, value.trim());
        });
        targetUserPref.setOnPreferenceChangeListener((preference, newValue) -> {
            try {
                int userId = Integer.parseInt(String.valueOf(newValue).trim());
                if (userId < -1)
                    throw new NumberFormatException();
                return true;
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), R.string.settings_main_target_user_invalid, Toast.LENGTH_LONG).show();
                return false;
            }
        });

        findPreference(PreferencesKeys.BACKUP_SETTINGS).setOnPreferenceClickListener(p -> {
            startActivity(new Intent(requireContext(), BackupSettingsActivity.class));
            return true;
        });

        mThemePref = findPreference(PreferencesKeys.THEME);
        updateThemeSummary();
        mThemePref.setOnPreferenceClickListener(p -> {
            ThemeSelectionDialogFragment.newInstance(requireContext()).show(getChildFragmentManager(), "theme");
            return true;
        });
        if (Theme.getInstance(requireContext()).getThemeMode() != Theme.Mode.CONCRETE) {
            mThemePref.setVisible(false);
        }

        mAutoThemeSwitch = Objects.requireNonNull(findPreference(PreferencesKeys.AUTO_THEME));
        mAutoThemePicker = findPreference(PreferencesKeys.AUTO_THEME_PICKER);
        updateAutoThemePickerSummary();

        mAutoThemeSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean value = (boolean) newValue;
            if (value) {
                if (!Utils.apiIsAtLeast(Build.VERSION_CODES.Q))
                    SimpleAlertDialogFragment.newInstance(requireContext(), R.string.settings_main_auto_theme, R.string.settings_main_auto_theme_pre_q_warning).show(getChildFragmentManager(), null);

                Theme.getInstance(requireContext()).setMode(Theme.Mode.AUTO_LIGHT_DARK);
            } else {
                Theme.getInstance(requireContext()).setMode(Theme.Mode.CONCRETE);
            }

            //Hack to not mess with hiding/showing preferences manually
            requireActivity().recreate();
            return true;
        });

        mAutoThemePicker.setOnPreferenceClickListener(pref -> {
            DarkLightThemeSelectionDialogFragment.newInstance().show(getChildFragmentManager(), null);
            return true;
        });

        if (Theme.getInstance(requireContext()).getThemeMode() != Theme.Mode.AUTO_LIGHT_DARK) {
            mAutoThemePicker.setVisible(false);
        }

        SwitchPreference analyticsPref = findPreference(PreferencesKeys.ENABLE_ANALYTICS);
        analyticsPref.setOnPreferenceChangeListener((preference, newValue) -> {
            mAnalyticsProvider.setDataCollectionEnabled((boolean) newValue);
            return true;
        });
        if (!mAnalyticsProvider.supportsDataCollection())
            analyticsPref.setVisible(false);

        SwitchPreference enableApkActionViewPref = findPreference(PreferencesKeys.ENABLE_APK_ACTION_VIEW);
        enableApkActionViewPref.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = (boolean) newValue;
            mPm.setComponentEnabledSetting(ApkActionViewProxyActivity.getComponentName(requireContext()), enabled ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            return true;
        });


        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setDividerHeight(0);
    }

    private void openFilePicker(FilePickerDialogFragment filePicker) {
        if (!PermissionsUtils.checkAndRequestStoragePermissions(this)) {
            mPendingFilePicker = filePicker;
            return;
        }
        filePicker.show(Objects.requireNonNull(getChildFragmentManager()), null);
    }

    private void selectHomeDir() {
        DialogProperties properties = new DialogProperties();
        properties.selection_mode = DialogConfigs.SINGLE_MODE;
        properties.selection_type = DialogConfigs.DIR_SELECT;
        properties.root = Environment.getExternalStorageDirectory();

        openFilePicker(FilePickerDialogFragment.newInstance("home", getString(R.string.settings_main_pick_dir), properties));
    }

    private void updateHomeDirPrefSummary() {
        mHomeDirPref.setSummary(getString(R.string.settings_main_home_directory_summary, mHelper.getHomeDirectory()));
    }

    private void updateFilePickerSortSummary() {
        mFilePickerSortPref.setSummary(getString(R.string.settings_main_file_picker_sort_summary, getResources().getStringArray(R.array.file_picker_sort_variants)[mHelper.getFilePickerRawSort()]));
    }

    private void updateInstallerSummary() {
        if (mInstallerPref == null)
            return;

        VsiAppMode mode = VsiModeManager.getMode(requireContext());
        String[] modes = getResources().getStringArray(R.array.vsi_app_modes);
        String label = mode.ordinal() < modes.length ? modes[mode.ordinal()] : mode.name();

        mInstallerPref.setSummary(getString(
                R.string.settings_main_app_mode_summary,
                label,
                mode.capabilityCount()
        ));
    }

    private void updateBatteryOptimizationSummary() {
        if (mBatteryOptimizationPref == null)
            return;

        mBatteryOptimizationPref.setSummary(
                VsiBatteryOptimizationHelper.isExempt(requireContext())
                        ? R.string.vsi_battery_optimization_disabled
                        : R.string.vsi_battery_optimization_required
        );
    }

    private void updateThemeSummary() {
        mThemePref.setSummary(Theme.getInstance(requireContext()).getConcreteTheme().getName(requireContext()));
    }

    private void updateAutoThemePickerSummary() {
        Theme theme = Theme.getInstance(requireContext());
        mAutoThemePicker.setSummary(getString(R.string.settings_main_auto_theme_picker_summary, theme.getLightTheme().getName(requireContext()), theme.getDarkTheme().getName(requireContext())));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionsUtils.REQUEST_CODE_STORAGE_PERMISSIONS) {
            if (grantResults.length == 0 || grantResults[0] == PackageManager.PERMISSION_DENIED)
                AlertsUtils.showAlert(this, R.string.error, R.string.permissions_required_storage);
            else {
                if (mPendingFilePicker != null) {
                    openFilePicker(mPendingFilePicker);
                    mPendingFilePicker = null;
                }
            }
        }

        if (requestCode == PermissionsUtils.REQUEST_CODE_SHIZUKU) {
            if (grantResults.length == 0 || grantResults[0] == PackageManager.PERMISSION_DENIED) {
                AlertsUtils.showAlert(this, R.string.error, R.string.permissions_required_shizuku);
            } else {
                VsiAppMode mode = mPendingModeAfterShizuku != null
                        ? mPendingModeAfterShizuku
                        : VsiAppMode.SHIZUKU;
                mPendingModeAfterShizuku = null;
                VsiModeManager.applyMode(requireContext(), mode);
                updateInstallerSummary();
            }
        }
    }

    @Override
    public void onFilesSelected(String tag, List<File> files) {
        switch (tag) {
            case "home":
                mHelper.setHomeDirectory(files.get(0).getAbsolutePath());
                updateHomeDirPrefSummary();
                break;
        }
    }

    @Override
    public boolean onItemSelected(String dialogTag, int selectedItemIndex) {
        switch (dialogTag) {
            case "sort":
                mHelper.setFilePickerRawSort(selectedItemIndex);
                switch (selectedItemIndex) {
                    case 0:
                        mHelper.setFilePickerSortBy(DialogConfigs.SORT_BY_NAME);
                        mHelper.setFilePickerSortOrder(DialogConfigs.SORT_ORDER_NORMAL);
                        break;
                    case 1:
                        mHelper.setFilePickerSortBy(DialogConfigs.SORT_BY_NAME);
                        mHelper.setFilePickerSortOrder(DialogConfigs.SORT_ORDER_REVERSE);
                        break;
                    case 2:
                        mHelper.setFilePickerSortBy(DialogConfigs.SORT_BY_LAST_MODIFIED);
                        mHelper.setFilePickerSortOrder(DialogConfigs.SORT_ORDER_NORMAL);
                        break;
                    case 3:
                        mHelper.setFilePickerSortBy(DialogConfigs.SORT_BY_LAST_MODIFIED);
                        mHelper.setFilePickerSortOrder(DialogConfigs.SORT_ORDER_REVERSE);
                        break;
                    case 4:
                        mHelper.setFilePickerSortBy(DialogConfigs.SORT_BY_SIZE);
                        mHelper.setFilePickerSortOrder(DialogConfigs.SORT_ORDER_REVERSE);
                        break;
                    case 5:
                        mHelper.setFilePickerSortBy(DialogConfigs.SORT_BY_SIZE);
                        mHelper.setFilePickerSortOrder(DialogConfigs.SORT_ORDER_NORMAL);
                        break;
                }
                updateFilePickerSortSummary();
                return true;

            case "app_mode": {
                if (selectedItemIndex < 0 || selectedItemIndex >= VsiAppMode.values().length)
                    return false;

                final VsiAppMode selectedMode = VsiAppMode.values()[selectedItemIndex];

                if (!VsiBatteryOptimizationHelper.isExempt(requireContext())) {
                    Intent batteryIntent = VsiBatteryOptimizationHelper.buildRequestIntent(requireContext());
                    if (batteryIntent != null)
                        startActivity(batteryIntent);

                    Toast.makeText(
                            requireContext(),
                            R.string.vsi_battery_optimization_select_mode_again,
                            Toast.LENGTH_LONG
                    ).show();
                    return false;
                }

                switch (selectedMode) {
                    case NORMAL:
                        break;

                    case SHIZUKU:
                        if (!Utils.apiIsAtLeast(Build.VERSION_CODES.M)) {
                            AlertsUtils.showAlert(
                                    this,
                                    R.string.error,
                                    R.string.settings_main_installer_error_shizuku_pre_m
                            );
                            return false;
                        }

                        if (!Shizuku.pingBinder()) {
                            AlertsUtils.showAlert(
                                    this,
                                    R.string.error,
                                    R.string.settings_main_installer_error_no_shizuku
                            );
                            return false;
                        }

                        if (!Shizuku.isPreV11()
                                && Shizuku.getVersion() >= 11
                                && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                            mPendingModeAfterShizuku = VsiAppMode.SHIZUKU;
                            Shizuku.requestPermission(PermissionsUtils.REQUEST_CODE_SHIZUKU);
                            return false;
                        }
                        break;

                    case XPOSED:
                        if (!VsiXposedRuntimeProbe.isActive()) {
                            AlertsUtils.showAlert(
                                    this,
                                    R.string.error,
                                    R.string.settings_main_installer_error_no_xposed
                            );
                            return false;
                        }
                        break;

                    case ROOT:
                        if (!SuShell.getInstance().requestRoot()) {
                            AlertsUtils.showAlert(
                                    this,
                                    R.string.error,
                                    R.string.settings_main_use_root_error
                            );
                            return false;
                        }
                        break;

                    case ROOT_XPOSED:
                        if (!SuShell.getInstance().requestRoot()) {
                            AlertsUtils.showAlert(
                                    this,
                                    R.string.error,
                                    R.string.settings_main_use_root_error
                            );
                            return false;
                        }
                        if (!VsiXposedRuntimeProbe.isActive()) {
                            AlertsUtils.showAlert(
                                    this,
                                    R.string.error,
                                    R.string.settings_main_installer_error_no_xposed
                            );
                            return false;
                        }
                        break;
                }

                VsiModeManager.applyMode(requireContext(), selectedMode);
                updateInstallerSummary();
                return true;
            }
        }

        return true;
    }

    @Override
    public void onDialogDismissed(@NonNull String dialogTag) {
        switch (dialogTag) {
            case "theme":
                updateThemeSummary();
                break;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);

        if (Utils.apiIsAtLeast(Build.VERSION_CODES.M)) {
            Shizuku.removeRequestPermissionResultListener(this);
        }
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals(PreferencesKeys.VSI_LANGUAGE)) {
            Utils.hardRestartApp(requireContext());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateInstallerSummary();
        updateBatteryOptimizationSummary();
    }

    @Override
    public void onThemesChosen(@Nullable String tag, Theme.ThemeDescriptor lightTheme, Theme.ThemeDescriptor darkTheme) {
        Theme theme = Theme.getInstance(requireContext());
        theme.setLightTheme(lightTheme);
        theme.setDarkTheme(darkTheme);
    }

    @Override
    public void onRequestPermissionResult(int requestCode, int grantResult) {
        switch (requestCode) {
            case PermissionsUtils.REQUEST_CODE_SHIZUKU:
                if (grantResult == PackageManager.PERMISSION_DENIED)
                    AlertsUtils.showAlert(this, R.string.error, R.string.permissions_required_shizuku);
                else {
                    VsiAppMode mode = mPendingModeAfterShizuku != null
                            ? mPendingModeAfterShizuku
                            : VsiAppMode.SHIZUKU;
                    mPendingModeAfterShizuku = null;
                    VsiModeManager.applyMode(requireContext(), mode);
                    updateInstallerSummary();
                }
                break;
        }
    }
}
