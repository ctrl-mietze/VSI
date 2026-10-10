package com.aefyr.sai.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.aefyr.sai.R;
import com.aefyr.sai.runtime.VsiAppMode;
import com.aefyr.sai.runtime.VsiModeManager;
import com.aefyr.sai.runtime.VsiSecurityWindowManager;
import com.aefyr.sai.ui.activities.PreferencesActivity;
import com.aefyr.sai.ui.activities.VsiApkAnalyzerActivity;
import com.aefyr.sai.ui.activities.VsiPackageConverterActivity;
import com.aefyr.sai.ui.activities.VsiPackageRepairActivity;

public class VsiToolsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);

        PreferenceCategory install = new PreferenceCategory(requireContext());
        install.setTitle(R.string.vsi_tools_install);
        screen.addPreference(install);

        add(install, R.string.vsi_tools_profiles, R.string.vsi_tools_profiles_summary, () ->
                PreferencesActivity.open(requireContext(), VsiInstallProfilesFragment.class,
                        getString(R.string.vsi_tools_profiles)));

        add(install, R.string.vsi_tools_history, R.string.vsi_tools_history_summary, () ->
                PreferencesActivity.open(requireContext(), VsiSessionHistoryFragment.class,
                        getString(R.string.vsi_tools_history)));

        add(install, R.string.vsi_tools_queue, R.string.vsi_tools_queue_summary, () ->
                PreferencesActivity.open(requireContext(), VsiInstallQueueFragment.class,
                        getString(R.string.vsi_tools_queue)));

        PreferenceCategory packages = new PreferenceCategory(requireContext());
        packages.setTitle(R.string.vsi_tools_packages);
        screen.addPreference(packages);

        add(packages, R.string.vsi_tools_analyzer, R.string.vsi_tools_analyzer_summary, () ->
                startActivity(new Intent(requireContext(), VsiApkAnalyzerActivity.class)));

        add(packages, R.string.vsi_tools_converter, R.string.vsi_tools_converter_summary, () ->
                startActivity(new Intent(requireContext(), VsiPackageConverterActivity.class)));

        add(packages, R.string.vsi_tools_repair, R.string.vsi_tools_repair_summary, () ->
                startActivity(new Intent(requireContext(), VsiPackageRepairActivity.class)));

        PreferenceCategory security = new PreferenceCategory(requireContext());
        security.setTitle(R.string.vsi_tools_security);
        screen.addPreference(security);

        add(security, R.string.vsi_security_downgrade_window,
                R.string.vsi_security_downgrade_window_summary,
                () -> armSecurity(false));

        add(security, R.string.vsi_security_signature_window,
                R.string.vsi_security_signature_window_summary,
                () -> armSecurity(true));

        add(security, R.string.vsi_security_disarm,
                R.string.vsi_security_disarm_summary,
                () -> {
                    VsiSecurityWindowManager.disarmAll(requireContext());
                    Toast.makeText(requireContext(), R.string.vsi_security_disarmed, Toast.LENGTH_SHORT).show();
                });

        PreferenceCategory runtime = new PreferenceCategory(requireContext());
        runtime.setTitle(R.string.vsi_tools_runtime);
        screen.addPreference(runtime);

        add(runtime, R.string.vsi_tools_patcher, R.string.vsi_tools_patcher_summary, () ->
                PreferencesActivity.open(requireContext(), VsiPatcherFragment.class,
                        getString(R.string.vsi_tools_patcher)));

        add(runtime, R.string.vsi_tools_xposed_diag, R.string.vsi_tools_xposed_diag_summary, () ->
                PreferencesActivity.open(requireContext(), VsiXposedDiagnosticsFragment.class,
                        getString(R.string.vsi_tools_xposed_diag)));

        add(runtime, R.string.vsi_developer_options, R.string.vsi_developer_options_summary, () ->
                PreferencesActivity.open(requireContext(), VsiDeveloperOptionsFragment.class,
                        getString(R.string.vsi_developer_options)));
    }

    private void armSecurity(boolean signature) {
        VsiAppMode mode = VsiModeManager.getCurrentMode(requireContext());
        if (!mode.usesXposed()) {
            Toast.makeText(
                    requireContext(),
                    R.string.vsi_security_requires_xposed,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (signature)
            VsiSecurityWindowManager.armSignatureOverride(requireContext());
        else
            VsiSecurityWindowManager.armDowngrade(requireContext());

        Toast.makeText(
                requireContext(),
                signature
                        ? R.string.vsi_security_signature_armed
                        : R.string.vsi_security_downgrade_armed,
                Toast.LENGTH_LONG
        ).show();
    }

    private void add(PreferenceCategory category, int title, int summary, Runnable action) {
        Preference p = new Preference(requireContext());
        p.setTitle(title);
        p.setSummary(summary);
        p.setOnPreferenceClickListener(pref -> {
            action.run();
            return true;
        });
        category.addPreference(p);
    }
}
