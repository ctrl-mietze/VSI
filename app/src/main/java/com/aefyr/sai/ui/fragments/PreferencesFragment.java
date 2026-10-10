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
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import com.aefyr.sai.BuildConfig;
import com.aefyr.sai.R;
import com.aefyr.sai.analytics.AnalyticsProvider;
import com.aefyr.sai.analytics.DefaultAnalyticsProvider;
import com.aefyr.sai.shell.SuShell;
import com.aefyr.sai.runtime.VsiAppMode;
import com.aefyr.sai.runtime.VsiModeManager;
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
import com.aefyr.sai.utils.PreferencesValues;
import com.aefyr.sai.utils.Theme;
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

        ListPreference appModePref = Objects.requireNonNull(findPreference("vsi_app_mode"));
        appModePref.setValue(VsiModeManager.getCurrentMode(requireContext()).id());
        appModePref.setOnPreferenceChangeListener((preference, newValue) -> {
            VsiAppMode requested = VsiAppMode.fromId(String.valueOf(newValue));

            if (!VsiModeManager.isBatteryOptimizationDisabled(requireContext())) {
                Toast.makeText(
                        requireContext(),
                        R.string.vsi_mode_battery_required,
                        Toast.LENGTH_LONG
                ).show();
                VsiModeManager.requestBatteryOptimizationExemption(requireContext());
                return false;
            }

            if (requested == VsiAppMode.SHIZUKU
                    && Shizuku.pingBinder()
                    && !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(PermissionsUtils.REQUEST_CODE_SHIZUKU);
                Toast.makeText(
                        requireContext(),
                        R.string.vsi_mode_shizuku_permission_wait,
                        Toast.LENGTH_LONG
                ).show();
                return false;
            }

            VsiModeManager.Availability availability =
                    VsiModeManager.applyMode(requireContext(), requested);

            if (!availability.available) {
                Toast.makeText(
                        requireContext(),
                        availability.reason,
                        Toast.LENGTH_LONG
                ).show();
                return false;
            }

            updateBatteryOptimizationSummary();
            Toast.makeText(requireContext(), R.string.vsi_mode_applied, Toast.LENGTH_SHORT).show();
            return true;
        });

        Preference functionInfo = Objects.requireNonNull(findPreference("vsi_mode_function_info"));
        functionInfo.setOnPreferenceClickListener(preference -> {
            PreferencesActivity.open(
                    requireContext(),
                    VsiModeInfoFragment.class,
                    getString(R.string.vsi_mode_function_info)
            );
            return true;
        });

        Preference batteryOptimization =
                Objects.requireNonNull(findPreference("vsi_battery_optimization"));
        batteryOptimization.setOnPreferenceClickListener(preference -> {
            VsiModeManager.requestBatteryOptimizationExemption(requireContext());
            return true;
        });
        updateBatteryOptimizationSummary();

        Preference systemHookInfoPref = findPreference("vsi_system_hook_info");
        Objects.requireNonNull(systemHookInfoPref).setOnPreferenceClickListener(preference -> {
            SimpleAlertDialogFragment.newInstance(requireContext(),
                    R.string.vsi_system_hook_title,
                    R.string.vsi_system_hook_details)
                    .show(getChildFragmentManager(), "vsi_system_hook_info");
            return true;
        });

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
            if (grantResults.length == 0 || grantResults[0] == PackageManager.PERMISSION_DENIED)
                AlertsUtils.showAlert(this, R.string.error, R.string.permissions_required_shizuku);
            else {
                VsiModeManager.applyMode(requireContext(), VsiAppMode.SHIZUKU);
                ListPreference appMode = findPreference("vsi_app_mode");
                if (appMode != null)
                    appMode.setValue(VsiAppMode.SHIZUKU.id());
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


        }

        return true;
    }

    private void updateBatteryOptimizationSummary() {
        Preference battery = findPreference("vsi_battery_optimization");
        if (battery == null)
            return;

        battery.setSummary(
                VsiModeManager.isBatteryOptimizationDisabled(requireContext())
                        ? R.string.vsi_mode_battery_ok
                        : R.string.vsi_mode_battery_required
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        updateBatteryOptimizationSummary();

        if (VsiModeManager.isBatteryOptimizationDisabled(requireContext())) {
            VsiModeManager.applyMode(
                    requireContext(),
                    VsiModeManager.getCurrentMode(requireContext())
            );
        }
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
        if (key.equals(PreferencesKeys.USE_OLD_INSTALLER)) {
            prefs.edit().putBoolean(
                    PreferencesKeys.USE_OLD_INSTALLER,
                    prefs.getBoolean(PreferencesKeys.USE_OLD_INSTALLER, false)
            ).commit();
            Utils.hardRestartApp(requireContext());
            return;
        }

        if (key.equals(PreferencesKeys.VSI_LANGUAGE)) {
            Utils.hardRestartApp(requireContext());
        }
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
                    VsiModeManager.applyMode(requireContext(), VsiAppMode.SHIZUKU);
                    ListPreference appMode = findPreference("vsi_app_mode");
                    if (appMode != null)
                        appMode.setValue(VsiAppMode.SHIZUKU.id());
                }
                break;
        }
    }
}
