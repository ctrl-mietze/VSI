package com.aefyr.sai.ui.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.aefyr.sai.R;
import com.aefyr.sai.ui.activities.MainActivity;
import com.aefyr.sai.utils.VsiReportExporter;
import com.aefyr.sai.utils.VsiSessionHistoryStore;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VsiSessionHistoryFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        rebuild();
    }

    private void rebuild() {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);

        Preference export = new Preference(requireContext());
        export.setTitle(R.string.vsi_history_export);
        export.setSummary(R.string.vsi_history_export_summary);
        export.setOnPreferenceClickListener(p -> {
            exportHistory();
            return true;
        });
        screen.addPreference(export);

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

        DateFormat dateFormat = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT
        );

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

            boolean hasSource = entry.sources != null && !entry.sources.isEmpty();
            if (hasSource) {
                summary.append("\n")
                        .append(getString(R.string.vsi_history_source))
                        .append(": ")
                        .append(entry.sources.get(0))
                        .append("\n")
                        .append(getString(R.string.vsi_history_tap_retry));
            }

            pref.setSummary(summary.toString());
            pref.setSelectable(hasSource);

            if (hasSource) {
                pref.setOnPreferenceClickListener(p -> {
                    retrySource(entry.sources.get(0));
                    return true;
                });
            }

            category.addPreference(pref);
        }
    }

    private void retrySource(String rawUri) {
        try {
            Uri uri = Uri.parse(rawUri);

            if ("file".equalsIgnoreCase(uri.getScheme())) {
                String path = uri.getPath();
                if (path == null || !new File(path).exists()) {
                    toast(R.string.vsi_history_source_missing);
                    return;
                }
            } else if ("content".equalsIgnoreCase(uri.getScheme())) {
                try (java.io.InputStream in =
                             requireContext().getContentResolver().openInputStream(uri)) {
                    if (in == null) {
                        toast(R.string.vsi_history_source_missing);
                        return;
                    }
                }
            }

            Intent intent = new Intent(requireContext(), MainActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            toast(R.string.vsi_history_source_missing);
        }
    }

    private void exportHistory() {
        List<VsiSessionHistoryStore.Entry> entries =
                VsiSessionHistoryStore.getInstance(requireContext()).getEntries();

        StringBuilder report = new StringBuilder();
        report.append("VSI Session History\n");
        report.append("===================\n\n");

        DateFormat dateFormat = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.MEDIUM
        );

        for (VsiSessionHistoryStore.Entry entry : entries) {
            report.append(dateFormat.format(new Date(entry.timestamp))).append("\n");
            report.append("Status: ").append(entry.status).append("\n");
            report.append("Installer: ").append(entry.installer).append("\n");

            if (entry.appName != null)
                report.append("App: ").append(entry.appName).append("\n");
            if (entry.packageName != null)
                report.append("Package: ").append(entry.packageName).append("\n");
            if (entry.shortError != null)
                report.append("Error: ").append(entry.shortError).append("\n");

            if (entry.sources != null) {
                for (String source : entry.sources)
                    report.append("Source: ").append(source).append("\n");
            }

            report.append("Session: ").append(entry.sessionId).append("\n");
            report.append("\n");
        }

        String timestamp = new SimpleDateFormat(
                "yyyyMMdd-HHmmss",
                Locale.US
        ).format(new Date());

        try {
            VsiReportExporter.Result result = VsiReportExporter.saveText(
                    requireContext(),
                    "VSI-Session-History-" + timestamp + ".txt",
                    report.toString()
            );
            Toast.makeText(
                    requireContext(),
                    getString(R.string.vsi_history_exported, result.location),
                    Toast.LENGTH_LONG
            ).show();
        } catch (Exception e) {
            Toast.makeText(
                    requireContext(),
                    getString(R.string.vsi_tools_error, e.getLocalizedMessage()),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private String prettyStatus(String status) {
        if ("INSTALLATION_SUCCEED".equals(status))
            return getString(R.string.installer_state_installed);
        if ("INSTALLATION_FAILED".equals(status))
            return getString(R.string.installer_state_failed);
        return status == null ? "?" : status;
    }

    private void toast(int res) {
        Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show();
    }
}
