package com.aefyr.sai.ui.fragments;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.aefyr.sai.R;
import com.aefyr.sai.installer2.impl.FlexSaiPackageInstaller;
import com.aefyr.sai.utils.VsiDeveloperOptions;

import java.util.List;

public class VsiInstallQueueFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        rebuild();
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);

        FlexSaiPackageInstaller installer = FlexSaiPackageInstaller.getInstance(requireContext());

        Preference status = new Preference(requireContext());
        status.setSelectable(false);
        status.setTitle(R.string.vsi_queue_status);
        status.setSummary(getString(
                R.string.vsi_queue_status_summary,
                installer.activeQueueCount(),
                installer.pendingQueueCount()
        ));
        screen.addPreference(status);

        Preference pause = new Preference(requireContext());
        pause.setTitle(installer.isQueuePaused()
                ? R.string.vsi_queue_resume
                : R.string.vsi_queue_pause);
        pause.setSummary(installer.isQueuePaused()
                ? R.string.vsi_queue_resume_summary
                : R.string.vsi_queue_pause_summary);
        pause.setOnPreferenceClickListener(p -> {
            installer.setQueuePaused(!installer.isQueuePaused());
            rebuild();
            return true;
        });
        screen.addPreference(pause);

        Preference refresh = new Preference(requireContext());
        refresh.setTitle(R.string.vsi_queue_refresh);
        refresh.setSummary(R.string.vsi_queue_refresh_summary);
        refresh.setOnPreferenceClickListener(p -> {
            rebuild();
            return true;
        });
        screen.addPreference(refresh);

        Preference config = new Preference(requireContext());
        config.setSelectable(false);
        config.setTitle(R.string.vsi_queue_configuration);
        config.setSummary(getString(
                R.string.vsi_queue_configuration_summary,
                VsiDeveloperOptions.getInstance(requireContext()).queueParallelism()
        ));
        screen.addPreference(config);

        addActiveCategory(
                screen,
                installer.activeQueueSessionIds()
        );

        addWaitingCategory(
                screen,
                installer,
                installer.pendingQueueSessionIds()
        );
    }

    private void addActiveCategory(
            PreferenceScreen screen,
            List<String> ids
    ) {
        PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setTitle(R.string.vsi_queue_active);
        screen.addPreference(category);

        if (ids.isEmpty()) {
            Preference empty = new Preference(requireContext());
            empty.setSelectable(false);
            empty.setTitle(R.string.vsi_queue_none_active);
            category.addPreference(empty);
            return;
        }

        for (String id : ids) {
            Preference session = new Preference(requireContext());
            session.setSelectable(false);
            session.setTitle(shortSession(id));
            session.setSummary(getString(R.string.vsi_queue_active_session_summary, id));
            category.addPreference(session);
        }
    }

    private void addWaitingCategory(
            PreferenceScreen screen,
            FlexSaiPackageInstaller installer,
            List<String> ids
    ) {
        PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setTitle(R.string.vsi_queue_waiting);
        screen.addPreference(category);

        if (ids.isEmpty()) {
            Preference empty = new Preference(requireContext());
            empty.setSelectable(false);
            empty.setTitle(R.string.vsi_queue_none_waiting);
            category.addPreference(empty);
            return;
        }

        for (String id : ids) {
            Preference session = new Preference(requireContext());
            session.setTitle(shortSession(id));
            session.setSummary(getString(R.string.vsi_queue_waiting_session_summary, id));
            session.setOnPreferenceClickListener(p -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.vsi_queue_remove_title)
                        .setMessage(R.string.vsi_queue_remove_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.vsi_queue_remove, (dialog, which) -> {
                            boolean removed = installer.removePendingSession(id);
                            Toast.makeText(
                                    requireContext(),
                                    removed
                                            ? R.string.vsi_queue_removed
                                            : R.string.vsi_queue_remove_failed,
                                    Toast.LENGTH_SHORT
                            ).show();
                            rebuild();
                        })
                        .show();
                return true;
            });
            category.addPreference(session);
        }
    }

    private String shortSession(String id) {
        int at = id.indexOf('@');
        if (at < 0)
            return id;

        String backend = id.substring(at + 1);
        int dot = backend.lastIndexOf('.');
        if (dot >= 0)
            backend = backend.substring(dot + 1);

        return "#" + id.substring(0, at) + " · " + backend;
    }
}
