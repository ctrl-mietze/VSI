package com.aefyr.sai.ui.fragments;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.aefyr.sai.R;
import com.aefyr.sai.runtime.VsiPatcherManager;

public class VsiPatcherFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);

        Preference intro = new Preference(requireContext());
        intro.setSelectable(false);
        intro.setTitle(R.string.vsi_patcher_title);
        intro.setSummary(R.string.vsi_patcher_summary);
        screen.addPreference(intro);

        PreferenceCategory temp = new PreferenceCategory(requireContext());
        temp.setTitle(R.string.vsi_patcher_temp_category);
        screen.addPreference(temp);

        add(temp, R.string.vsi_patch_lite, R.string.vsi_patch_lite_summary,
                () -> runAction(() -> VsiPatcherManager.installPatchLite(requireContext())));

        add(temp, R.string.vsi_patch_lite_remove, R.string.vsi_patch_lite_remove_summary,
                () -> runAction(VsiPatcherManager::removePatchLite));

        PreferenceCategory dangerous = new PreferenceCategory(requireContext());
        dangerous.setTitle(R.string.vsi_patcher_danger_category);
        screen.addPreference(dangerous);

        add(dangerous, R.string.vsi_patch_profile0, R.string.vsi_patch_profile0_summary,
                () -> confirm(
                        R.string.vsi_patch_profile0,
                        R.string.vsi_patch_profile0_confirm,
                        () -> runAction(() -> VsiPatcherManager.patchProfile0(requireContext()))
                ));

        add(dangerous, R.string.vsi_patch_profile0_restore, R.string.vsi_patch_profile0_restore_summary,
                () -> runAction(VsiPatcherManager::restoreProfile0));

        add(dangerous, R.string.vsi_patcher_pro, R.string.vsi_patcher_pro_summary,
                () -> confirm(
                        R.string.vsi_patcher_pro,
                        R.string.vsi_patcher_pro_confirm,
                        () -> runAction(() -> VsiPatcherManager.installPatcherPro(requireContext()))
                ));

        add(dangerous, R.string.vsi_patcher_pro_remove, R.string.vsi_patcher_pro_remove_summary,
                () -> runAction(VsiPatcherManager::removePatcherPro));

        PreferenceCategory status = new PreferenceCategory(requireContext());
        status.setTitle(R.string.vsi_patcher_status);
        screen.addPreference(status);

        add(status, R.string.vsi_patcher_refresh, R.string.vsi_patcher_refresh_summary,
                () -> runAction(VsiPatcherManager::status));
    }

    private void add(PreferenceCategory category, int title, int summary, Runnable action) {
        Preference pref = new Preference(requireContext());
        pref.setTitle(title);
        pref.setSummary(summary);
        pref.setOnPreferenceClickListener(p -> {
            action.run();
            return true;
        });
        category.addPreference(pref);
    }

    private void confirm(int title, int message, Runnable action) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> action.run())
                .show();
    }

    private void runAction(Action action) {
        Toast.makeText(requireContext(), R.string.vsi_tools_working, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            VsiPatcherManager.Result result = action.run();
            requireActivity().runOnUiThread(() -> new AlertDialog.Builder(requireContext())
                    .setTitle(result.success ? R.string.vsi_patcher_ok : R.string.error)
                    .setMessage(result.details
                            + (result.requiresReboot
                            ? "\n\n" + getString(R.string.vsi_patcher_reboot_required)
                            : ""))
                    .setPositiveButton(R.string.ok, null)
                    .show());
        }, "VSI Patcher").start();
    }

    private interface Action {
        VsiPatcherManager.Result run();
    }
}
