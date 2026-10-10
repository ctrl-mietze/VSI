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
import com.aefyr.sai.runtime.VsiAppMode;
import com.aefyr.sai.runtime.VsiModeManager;
import com.aefyr.sai.utils.VsiDeveloperOptions;
import com.aefyr.sai.utils.VsiExtremeAnalyzer;
import com.google.android.material.button.MaterialButton;

public class VsiApkAnalyzerActivity extends ThemedActivity {

    private static final int PICK = 9220;

    private TextView mReport;
    private MaterialButton mCopy;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(30), dp(24), dp(30));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText(R.string.vsi_analyzer_title);
        title.setTextSize(28);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView summary = new TextView(this);
        summary.setText(R.string.vsi_analyzer_summary);
        summary.setPadding(0, dp(10), 0, dp(20));
        root.addView(summary);

        MaterialButton choose = new MaterialButton(this);
        choose.setText(R.string.vsi_analyzer_choose);
        choose.setAllCaps(false);
        choose.setOnClickListener(v -> choose());
        root.addView(choose);

        mCopy = new MaterialButton(this);
        mCopy.setText(R.string.vsi_analyzer_copy);
        mCopy.setAllCaps(false);
        mCopy.setEnabled(false);
        mCopy.setOnClickListener(v -> copy());
        root.addView(mCopy);

        mReport = new TextView(this);
        mReport.setTextIsSelectable(true);
        mReport.setPadding(0, dp(20), 0, 0);
        root.addView(mReport);

        setContentView(scroll);

        if (!VsiDeveloperOptions.getInstance(this).extremeApkAnalysis()) {
            mReport.setText(R.string.vsi_analyzer_disabled);
            choose.setEnabled(false);
        } else if (VsiModeManager.getCurrentMode(this) == VsiAppMode.NORMAL) {
            mReport.setText(R.string.vsi_analyzer_requires_mode);
            choose.setEnabled(false);
        }
    }

    private void choose() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType("*/*")
                .addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK
                || resultCode != Activity.RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        mReport.setText(R.string.vsi_tools_working);
        mCopy.setEnabled(false);

        new Thread(() -> {
            try {
                String report = VsiExtremeAnalyzer.analyze(this, uri);
                runOnUiThread(() -> {
                    mReport.setText(report);
                    mCopy.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> mReport.setText(
                        getString(R.string.vsi_tools_error, e.getMessage())
                ));
            }
        }, "VSI Extreme Analyzer").start();
    }

    private void copy() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null)
            return;

        clipboard.setPrimaryClip(ClipData.newPlainText(
                "VSI APK Analysis",
                mReport.getText()
        ));
        Toast.makeText(this, R.string.vsi_analyzer_copied, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
