package com.aefyr.sai.ui.fragments;

import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.aefyr.sai.R;
import com.aefyr.sai.utils.PreferencesHelper;
import com.aefyr.sai.utils.PreferencesValues;
import com.aefyr.sai.xposed.VsiXposedRuntimeProbe;

public class VsiXposedDiagnosticsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);

        PreferenceCategory status = new PreferenceCategory(requireContext());
        status.setTitle(R.string.vsi_xposed_diag_title);
        screen.addPreference(status);

        add(status, R.string.vsi_xposed_diag_runtime,
                VsiXposedRuntimeProbe.isActive() ? "✅ Active" : "❌ Not active");

        add(status, R.string.vsi_xposed_diag_scope,
                "ctrl.mietze.vsi\ncom.google.android.packageinstaller");

        boolean packageInstallerPresent;
        try {
            requireContext().getPackageManager().getPackageInfo("com.google.android.packageinstaller", 0);
            packageInstallerPresent = true;
        } catch (PackageManager.NameNotFoundException e) {
            packageInstallerPresent = false;
        }

        add(status, R.string.vsi_xposed_diag_system_installer,
                packageInstallerPresent ? "✅ com.google.android.packageinstaller" : "❌ Missing");

        int installer = PreferencesHelper.getInstance(requireContext()).getInstaller();
        add(status, R.string.vsi_xposed_diag_selected_mode,
                installer == PreferencesValues.INSTALLER_XPOSED
                        ? "✅ Xposed / Vector"
                        : getString(R.string.vsi_xposed_diag_not_selected));

        Preference info = new Preference(requireContext());
        info.setSelectable(false);
        info.setTitle(R.string.vsi_xposed_diag_help);
        info.setSummary(R.string.vsi_xposed_diag_help_summary);
        screen.addPreference(info);
    }

    private void add(PreferenceCategory category, int title, String summary) {
        Preference p = new Preference(requireContext());
        p.setSelectable(false);
        p.setTitle(title);
        p.setSummary(summary);
        category.addPreference(p);
    }
}
