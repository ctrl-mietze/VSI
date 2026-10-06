package com.aefyr.sai.ui.fragments;

import android.os.Bundle;

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

        addSessionCategory(
                screen,
                R.string.vsi_queue_active,
                installer.activeQueueSessionIds(),
                R.string.vsi_queue_none_active
        );

        addSessionCategory(
                screen,
                R.string.vsi_queue_waiting,
                installer.pendingQueueSessionIds(),
                R.string.vsi_queue_none_waiting
        );
    }

    private void addSessionCategory(
            PreferenceScreen screen,
            int title,
            List<String> ids,
            int emptyText
    ) {
        PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setTitle(title);
        screen.addPreference(category);

        if (ids.isEmpty()) {
            Preference empty = new Preference(requireContext());
            empty.setSelectable(false);
            empty.setTitle(emptyText);
            category.addPreference(empty);
            return;
        }

        for (String id : ids) {
            Preference session = new Preference(requireContext());
            session.setSelectable(false);
            session.setTitle(shortSession(id));
            session.setSummary(id);
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
