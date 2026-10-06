package com.aefyr.sai.ui.fragments;

import android.content.Intent;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.aefyr.sai.R;
import com.aefyr.sai.ui.activities.PreferencesActivity;
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

        add(packages, R.string.vsi_tools_converter, R.string.vsi_tools_converter_summary, () ->
                startActivity(new Intent(requireContext(), VsiPackageConverterActivity.class)));

        add(packages, R.string.vsi_tools_repair, R.string.vsi_tools_repair_summary, () ->
                startActivity(new Intent(requireContext(), VsiPackageRepairActivity.class)));

        PreferenceCategory runtime = new PreferenceCategory(requireContext());
        runtime.setTitle(R.string.vsi_tools_runtime);
        screen.addPreference(runtime);

        add(runtime, R.string.vsi_tools_xposed_diag, R.string.vsi_tools_xposed_diag_summary, () ->
                PreferencesActivity.open(requireContext(), VsiXposedDiagnosticsFragment.class,
                        getString(R.string.vsi_tools_xposed_diag)));

        add(runtime, R.string.vsi_developer_options, R.string.vsi_developer_options_summary, () ->
                PreferencesActivity.open(requireContext(), VsiDeveloperOptionsFragment.class,
                        getString(R.string.vsi_developer_options)));
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
