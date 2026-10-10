package com.aefyr.sai.ui.fragments;

import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.aefyr.sai.R;
import com.aefyr.sai.runtime.VsiAppMode;
import com.aefyr.sai.runtime.VsiCapabilityRegistry;
import com.aefyr.sai.runtime.VsiModeManager;
import com.aefyr.sai.xposed.VsiXposedRuntimeProbe;

import java.util.List;

public class VsiModeInfoFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);

        VsiAppMode mode = VsiModeManager.getCurrentMode(requireContext());
        List<String> capabilities = VsiCapabilityRegistry.forMode(mode);

        Preference header = new Preference(requireContext());
        header.setSelectable(false);
        header.setTitle(getString(R.string.vsi_mode_info_title, modeName(mode)));
        header.setSummary(getString(
                R.string.vsi_mode_info_summary,
                capabilities.size(),
                VsiModeManager.isBatteryOptimizationDisabled(requireContext())
                        ? getString(R.string.vsi_mode_battery_ok)
                        : getString(R.string.vsi_mode_battery_required),
                VsiXposedRuntimeProbe.isActive()
                        ? getString(R.string.vsi_mode_xposed_active)
                        : getString(R.string.vsi_mode_xposed_inactive)
        ));
        screen.addPreference(header);

        PreferenceCategory functions = new PreferenceCategory(requireContext());
        functions.setTitle(getString(R.string.vsi_mode_functions, capabilities.size()));
        screen.addPreference(functions);

        int index = 1;
        for (String capability : capabilities) {
            Preference pref = new Preference(requireContext());
            pref.setSelectable(false);
            pref.setTitle(index + ". " + capability);
            functions.addPreference(pref);
            index++;
        }
    }

    private String modeName(VsiAppMode mode) {
        switch (mode) {
            case SHIZUKU:
                return getString(R.string.vsi_mode_shizuku);
            case XPOSED:
                return getString(R.string.vsi_mode_xposed);
            case ROOT:
                return getString(R.string.vsi_mode_root);
            case ROOT_XPOSED:
                return getString(R.string.vsi_mode_root_xposed);
            case NORMAL:
            default:
                return getString(R.string.vsi_mode_normal);
        }
    }
}
