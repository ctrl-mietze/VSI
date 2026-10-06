package com.aefyr.sai.ui.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import com.aefyr.sai.R;
import com.aefyr.sai.shell.SuShell;
import com.aefyr.sai.utils.VsiDeveloperKeys;
import com.aefyr.sai.utils.VsiRootSystemInstallerBridge;

import java.util.Objects;

public class VsiDeveloperOptionsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_developer, rootKey);

        EditTextPreference queueSlots = Objects.requireNonNull(
                findPreference(VsiDeveloperKeys.QUEUE_PARALLELISM)
        );
        queueSlots.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            editText.setSelectAllOnFocus(true);
        });
        queueSlots.setOnPreferenceChangeListener((preference, newValue) -> {
            try {
                int value = Integer.parseInt(String.valueOf(newValue));
                if (value < 1 || value > 4) {
                    Toast.makeText(requireContext(), R.string.vsi_dev_queue_parallelism_invalid, Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            } catch (Exception e) {
                Toast.makeText(requireContext(), R.string.vsi_dev_queue_parallelism_invalid, Toast.LENGTH_LONG).show();
                return false;
            }
        });

        EditTextPreference customCommand = Objects.requireNonNull(
                findPreference(VsiDeveloperKeys.CUSTOM_INSTALL_CREATE)
        );
        customCommand.setOnPreferenceChangeListener((preference, newValue) -> {
            String value = String.valueOf(newValue == null ? "" : newValue).trim();
            if (value.isEmpty())
                return true;

            if (!value.contains("install-create")) {
                Toast.makeText(requireContext(), R.string.vsi_dev_custom_install_create_invalid, Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
        });

        SwitchPreference rootBridge = Objects.requireNonNull(
                findPreference(VsiDeveloperKeys.ROOT_SYSTEM_INSTALLER_BRIDGE)
        );
        rootBridge.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enable = Boolean.TRUE.equals(newValue);

            if (!SuShell.getInstance().requestRoot()) {
                Toast.makeText(requireContext(), R.string.settings_main_use_root_error, Toast.LENGTH_LONG).show();
                return false;
            }

            VsiRootSystemInstallerBridge.Result result =
                    VsiRootSystemInstallerBridge.setEnabled(requireContext(), enable);

            Toast.makeText(
                    requireContext(),
                    result.success
                            ? (enable ? R.string.vsi_dev_root_bridge_enabled : R.string.vsi_dev_root_bridge_disabled)
                            : R.string.vsi_dev_root_bridge_failed,
                    Toast.LENGTH_LONG
            ).show();

            return result.success;
        });

        Preference xposedVerbose = findPreference(VsiDeveloperKeys.XPOSED_VERBOSE);
        if (xposedVerbose != null) {
            xposedVerbose.setSummary(
                    getString(R.string.vsi_dev_xposed_verbose_summary)
                            + "\n"
                            + getString(R.string.vsi_dev_xposed_scope_hint)
            );
        }
    }
}
