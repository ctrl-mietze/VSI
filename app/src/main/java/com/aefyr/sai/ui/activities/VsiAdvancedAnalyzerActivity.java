package com.aefyr.sai.ui.activities;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.aefyr.sai.R;
import com.aefyr.sai.utils.VsiAdvancedApkAnalyzer;
import com.aefyr.sai.utils.VsiAppMode;
import com.aefyr.sai.utils.VsiDeveloperOptions;
import com.aefyr.sai.utils.VsiModeManager;
import com.google.android.material.button.MaterialButton;

public class VsiAdvancedAnalyzerActivity extends ThemedActivity {

    private static final int PICK_APK = 9401;
    private TextView mOutput;

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
        pick.setTextAllCaps(false);
        pick.setOnClickListener(v -> pickApk());
        root.addView(pick);

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

        if (!VsiDeveloperOptions.getInstance(this).advancedApkAnalysis()) {
            mOutput.setText(R.string.vsi_analyzer_disabled);
            pick.setEnabled(false);
            return;
        }

        VsiAppMode mode = VsiModeManager.getMode(this);
        if (mode == VsiAppMode.NORMAL) {
            mOutput.setText(R.string.vsi_analyzer_requires_privileged_mode);
            pick.setEnabled(false);
        }
    }

    private void pickApk() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/vnd.android.package-archive");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_APK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != PICK_APK || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null)
            return;

        Uri uri = data.getData();
        mOutput.setText(R.string.vsi_tools_working);

        new Thread(() -> {
            try {
                String report = VsiAdvancedApkAnalyzer.analyze(
                        this,
                        uri,
                        VsiModeManager.getMode(this)
                );
                runOnUiThread(() -> mOutput.setText(report));
            } catch (Exception e) {
                runOnUiThread(() -> mOutput.setText(
                        getString(R.string.vsi_tools_error, e.getLocalizedMessage())
                ));
            }
        }, "VSI Advanced Analyzer").start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
