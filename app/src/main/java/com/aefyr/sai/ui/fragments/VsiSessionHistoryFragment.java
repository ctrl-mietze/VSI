package com.aefyr.sai.ui.fragments;

import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.aefyr.sai.R;
import com.aefyr.sai.utils.VsiSessionHistoryStore;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class VsiSessionHistoryFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        rebuild();
    }

    private void rebuild() {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);

        Preference clear = new Preference(requireContext());
        clear.setTitle(R.string.vsi_history_clear);
        clear.setSummary(R.string.vsi_history_clear_summary);
        clear.setOnPreferenceClickListener(p -> {
            VsiSessionHistoryStore.getInstance(requireContext()).clear();
            rebuild();
            return true;
        });
        screen.addPreference(clear);

        PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setTitle(R.string.vsi_history_title);
        screen.addPreference(category);

        List<VsiSessionHistoryStore.Entry> entries =
                VsiSessionHistoryStore.getInstance(requireContext()).getEntries();

        if (entries.isEmpty()) {
            Preference empty = new Preference(requireContext());
            empty.setSelectable(false);
            empty.setTitle(R.string.vsi_history_empty);
            category.addPreference(empty);
            return;
        }

        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

        for (VsiSessionHistoryStore.Entry entry : entries) {
            Preference pref = new Preference(requireContext());

            String name = entry.appName != null ? entry.appName
                    : entry.packageName != null ? entry.packageName
                    : getString(R.string.installer_unknown_app);

            pref.setTitle(name + " · " + prettyStatus(entry.status));

            StringBuilder summary = new StringBuilder();
            summary.append(entry.installer == null ? "?" : entry.installer)
                    .append(" · ")
                    .append(dateFormat.format(new Date(entry.timestamp)));

            if (entry.packageName != null)
                summary.append("\n").append(entry.packageName);

            if (entry.shortError != null)
                summary.append("\n").append(entry.shortError);

            if (entry.sources != null && !entry.sources.isEmpty())
                summary.append("\n").append(getString(R.string.vsi_history_source))
                        .append(": ").append(entry.sources.get(0));

            pref.setSummary(summary.toString());
            pref.setSelectable(false);
            category.addPreference(pref);
        }
    }

    private String prettyStatus(String status) {
        if ("INSTALLATION_SUCCEED".equals(status))
            return getString(R.string.installer_state_installed);
        if ("INSTALLATION_FAILED".equals(status))
            return getString(R.string.installer_state_failed);
        return status == null ? "?" : status;
    }
}
