package com.aefyr.sai.ui.activities;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.aefyr.sai.R;
import com.aefyr.sai.utils.VsiAdvancedApkAnalyzer;
import com.aefyr.sai.utils.VsiModeManager;
import com.aefyr.sai.utils.VsiReportExporter;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VsiAdvancedAnalyzerActivity extends ThemedActivity {

    private static final int PICK_APK = 9401;

    private TextView mOutput;
    private MaterialButton mCopy;
    private MaterialButton mExport;
    private String mLastReport;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText(R.string.vsi_analyzer_title);
        title.setTextSize(26);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView summary = new TextView(this);
        summary.setText(R.string.vsi_analyzer_summary);
        summary.setPadding(0, dp(8), 0, dp(16));
        root.addView(summary);

        MaterialButton pick = new MaterialButton(this);
        pick.setText(R.string.vsi_analyzer_pick);
        pick.setAllCaps(false);
        pick.setOnClickListener(v -> pickApk());
        root.addView(pick);

        mCopy = new MaterialButton(this);
        mCopy.setText(R.string.vsi_analyzer_copy);
        mCopy.setAllCaps(false);
        mCopy.setEnabled(false);
        mCopy.setOnClickListener(v -> copyReport());
        root.addView(mCopy);

        mExport = new MaterialButton(this);
        mExport.setText(R.string.vsi_analyzer_export);
        mExport.setAllCaps(false);
        mExport.setEnabled(false);
        mExport.setOnClickListener(v -> exportReport());
        root.addView(mExport);

        mOutput = new TextView(this);
        mOutput.setTextIsSelectable(true);
        mOutput.setPadding(0, dp(18), 0, dp(32));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(mOutput);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setContentView(root);
    }

    private void pickApk() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/vnd.android.package-archive");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_APK);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            @Nullable Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != PICK_APK
                || resultCode != Activity.RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        mOutput.setText(R.string.vsi_tools_working);
        mCopy.setEnabled(false);
        mExport.setEnabled(false);
        mLastReport = null;

        new Thread(() -> {
            try {
                String report = VsiAdvancedApkAnalyzer.analyze(
                        this,
                        uri,
                        VsiModeManager.getMode(this)
                );
                runOnUiThread(() -> {
                    mLastReport = report;
                    mOutput.setText(report);
                    mCopy.setEnabled(true);
                    mExport.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> mOutput.setText(
                        getString(
                                R.string.vsi_tools_error,
                                e.getLocalizedMessage()
                        )
                ));
            }
        }, "VSI Advanced Analyzer").start();
    }

    private void copyReport() {
        if (mLastReport == null)
            return;

        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText("VSI APK Analysis", mLastReport)
            );
            Toast.makeText(
                    this,
                    R.string.vsi_analyzer_copied,
                    Toast.LENGTH_SHORT
            ).show();
        }
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
                    "VSI-APK-Analysis-" + stamp + ".txt",
                    mLastReport
            );
            Toast.makeText(
                    this,
                    getString(R.string.vsi_analyzer_exported, result.location),
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
