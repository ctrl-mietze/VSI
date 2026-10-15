package com.aefyr.sai.ui.activities;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.aefyr.sai.R;
import com.aefyr.sai.utils.VsiInstallerDoctor;
import com.aefyr.sai.utils.VsiReportExporter;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VsiInstallerDoctorActivity extends ThemedActivity {

    private LinearLayout mResults;
    private MaterialButton mExportButton;
    private VsiInstallerDoctor.Report mLastReport;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText(R.string.vsi_doctor_title);
        title.setTextSize(28);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView summary = new TextView(this);
        summary.setText(R.string.vsi_doctor_summary);
        summary.setPadding(0, dp(8), 0, dp(14));
        root.addView(summary);

        MaterialButton run = new MaterialButton(this);
        run.setText(R.string.vsi_doctor_run);
        run.setAllCaps(false);
        run.setOnClickListener(v -> runDoctor());
        root.addView(run);

        mExportButton = new MaterialButton(this);
        mExportButton.setText(R.string.vsi_doctor_export);
        mExportButton.setAllCaps(false);
        mExportButton.setEnabled(false);
        mExportButton.setOnClickListener(v -> exportReport());
        root.addView(mExportButton);

        mResults = new LinearLayout(this);
        mResults.setOrientation(LinearLayout.VERTICAL);
        mResults.setPadding(0, dp(14), 0, dp(30));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(mResults);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setContentView(root);

        runDoctor();
    }

    private void runDoctor() {
        mResults.removeAllViews();

        TextView working = new TextView(this);
        working.setText(R.string.vsi_tools_working);
        mResults.addView(working);

        new Thread(() -> {
            VsiInstallerDoctor.Report report = VsiInstallerDoctor.run(this);
            runOnUiThread(() -> showReport(report));
        }, "VSI Installer Doctor").start();
    }

    private void showReport(VsiInstallerDoctor.Report report) {
        mLastReport = report;
        mResults.removeAllViews();

        TextView overview = new TextView(this);
        overview.setText(getString(
                R.string.vsi_doctor_result_summary,
                report.problemCount()
        ));
        overview.setTextSize(17);
        overview.setTypeface(
                overview.getTypeface(),
                android.graphics.Typeface.BOLD
        );
        overview.setPadding(0, 0, 0, dp(12));
        mResults.addView(overview);

        for (VsiInstallerDoctor.Item item : report.items()) {
            TextView card = new TextView(this);
            card.setText(
                    (item.ok ? "✅ " : "⚠️ ")
                            + item.name
                            + "\n"
                            + item.value
            );
            card.setTextSize(15);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackgroundResource(R.drawable.vsi_bg_warning);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.bottomMargin = dp(8);
            card.setLayoutParams(lp);
            mResults.addView(card);
        }

        if (mExportButton != null)
            mExportButton.setEnabled(true);
    }

    private void exportReport() {
        if (mLastReport == null)
            return;

        String stamp = new SimpleDateFormat(
                "yyyyMMdd-HHmmss",
                Locale.US
        ).format(new Date());

        try {
            VsiReportExporter.Result result = VsiReportExporter.saveText(
                    this,
                    "VSI-Installer-Doctor-" + stamp + ".txt",
                    mLastReport.toText()
            );
            Toast.makeText(
                    this,
                    getString(R.string.vsi_doctor_exported, result.location),
                    Toast.LENGTH_LONG
            ).show();
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    getString(R.string.vsi_tools_error, e.getLocalizedMessage()),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
