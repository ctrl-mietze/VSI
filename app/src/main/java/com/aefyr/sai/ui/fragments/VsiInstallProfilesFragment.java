package com.aefyr.sai.ui.fragments;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.aefyr.sai.R;
import com.aefyr.sai.shell.ShizukuShell;
import com.aefyr.sai.shell.SuShell;
import com.aefyr.sai.utils.VsiInstallProfileManager;
import com.aefyr.sai.xposed.VsiXposedRuntimeProbe;

public class VsiInstallProfilesFragment extends PreferenceFragmentCompat {

    private VsiInstallProfileManager mProfiles;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        mProfiles = new VsiInstallProfileManager(requireContext());

        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);

        PreferenceCategory builtIn = new PreferenceCategory(requireContext());
        builtIn.setTitle(R.string.vsi_profiles_builtin);
        screen.addPreference(builtIn);

        addProfile(builtIn, R.string.vsi_profile_standard, R.string.vsi_profile_standard_summary,
                () -> mProfiles.applyStandard());

        addProfile(builtIn, R.string.vsi_profile_root_power, R.string.vsi_profile_root_power_summary,
                () -> {
                    if (!SuShell.getInstance().requestRoot())
                        throw new IllegalStateException(getString(R.string.settings_main_use_root_error));
                    mProfiles.applyRootPower();
                });

        addProfile(builtIn, R.string.vsi_profile_shizuku_power, R.string.vsi_profile_shizuku_power_summary,
                () -> {
                    if (!ShizukuShell.getInstance().isAvailable())
                        throw new IllegalStateException(getString(R.string.settings_main_installer_error_no_shizuku));
                    mProfiles.applyShizukuPower();
                });

        addProfile(builtIn, R.string.vsi_profile_xposed, R.string.vsi_profile_xposed_summary,
                () -> {
                    if (!VsiXposedRuntimeProbe.isActive())
                        throw new IllegalStateException(getString(R.string.settings_main_installer_error_no_xposed));
                    mProfiles.applyXposed();
                });

        addProfile(builtIn, R.string.vsi_profile_compatibility, R.string.vsi_profile_compatibility_summary,
                () -> mProfiles.applyCompatibility());

        PreferenceCategory custom = new PreferenceCategory(requireContext());
        custom.setTitle(R.string.vsi_profiles_custom);
        screen.addPreference(custom);

        Preference save = new Preference(requireContext());
        save.setTitle(R.string.vsi_profile_save_current);
        save.setSummary(R.string.vsi_profile_save_current_summary);
        save.setOnPreferenceClickListener(p -> {
            mProfiles.saveCurrent();
            toast(R.string.vsi_profile_saved);
            return true;
        });
        custom.addPreference(save);

        Preference apply = new Preference(requireContext());
        apply.setTitle(R.string.vsi_profile_apply_custom);
        apply.setSummary(R.string.vsi_profile_apply_custom_summary);
        apply.setOnPreferenceClickListener(p -> {
            toast(mProfiles.applyCustom() ? R.string.vsi_profile_applied : R.string.vsi_profile_no_custom);
            return true;
        });
        custom.addPreference(apply);
    }

    private void addProfile(PreferenceCategory category, int title, int summary, Runnable action) {
        Preference pref = new Preference(requireContext());
        pref.setTitle(title);
        pref.setSummary(summary);
        pref.setOnPreferenceClickListener(p -> {
            try {
                action.run();
                toast(R.string.vsi_profile_applied);
            } catch (Exception e) {
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            }
            return true;
        });
        category.addPreference(pref);
    }

    private void toast(int stringRes) {
        Toast.makeText(requireContext(), stringRes, Toast.LENGTH_SHORT).show();
    }
}
