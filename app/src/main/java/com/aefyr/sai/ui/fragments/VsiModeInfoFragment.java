package com.aefyr.sai.ui.fragments;

import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.aefyr.sai.R;
import com.aefyr.sai.utils.VsiAppMode;
import com.aefyr.sai.utils.VsiCapabilityCatalog;
import com.aefyr.sai.utils.VsiModeManager;

import java.util.List;

public class VsiModeInfoFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);

        VsiAppMode mode = VsiModeManager.getMode(requireContext());

        Preference status = new Preference(requireContext());
        status.setSelectable(false);
        status.setTitle(getString(R.string.vsi_mode_info_current, displayName(mode)));
        status.setSummary(getString(
                R.string.vsi_mode_info_count,
                mode.capabilityCount(),
                serviceName(mode)
        ));
        screen.addPreference(status);

        PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setTitle(R.string.vsi_mode_info_functions);
        screen.addPreference(category);

        List<String> features = VsiCapabilityCatalog.forMode(mode);
        for (int i = 0; i < features.size(); i++) {
            Preference p = new Preference(requireContext());
            p.setSelectable(false);
            p.setTitle((i + 1) + ". " + features.get(i));
            category.addPreference(p);
        }
    }

    private String displayName(VsiAppMode mode) {
        String[] labels = getResources().getStringArray(R.array.vsi_app_modes);
        return mode.ordinal() < labels.length ? labels[mode.ordinal()] : mode.name();
    }

    private String serviceName(VsiAppMode mode) {
        switch (mode.serviceKind()) {
            case VSERVICE:
                return getString(R.string.vsi_service_v_title);
            case SYSTEM:
                return getString(R.string.vsi_service_system_title);
            case LITE:
            default:
                return getString(R.string.vsi_service_lite_title);
        }
    }
}
