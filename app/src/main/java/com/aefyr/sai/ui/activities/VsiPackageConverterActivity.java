package com.aefyr.sai.ui.activities;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.aefyr.sai.R;
import com.aefyr.sai.utils.VsiPackageContainerTools;
import com.google.android.material.button.MaterialButton;

public class VsiPackageConverterActivity extends ThemedActivity {

    private static final int PICK = 9101;
    private TextView mStatus;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = buildRoot(R.string.vsi_converter_title, R.string.vsi_converter_summary);
        MaterialButton pick = new MaterialButton(this);
        pick.setText(R.string.vsi_converter_pick);
        pick.setAllCaps(false);
        pick.setOnClickListener(v -> pick());
        root.addView(pick);

        mStatus = new TextView(this);
        mStatus.setPadding(0, dp(18), 0, 0);
        root.addView(mStatus);

        setContentView(root);
    }

    private void pick() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK || resultCode != Activity.RESULT_OK || data == null || data.getData() == null)
            return;

        Uri uri = data.getData();
        mStatus.setText(R.string.vsi_tools_working);

        new Thread(() -> {
            try {
                VsiPackageContainerTools.Result result = VsiPackageContainerTools.convert(this, uri);
                runOnUiThread(() -> mStatus.setText(
                        getString(R.string.vsi_converter_done, result.displayName, result.apkEntries)
                ));
            } catch (Exception e) {
                runOnUiThread(() -> mStatus.setText(
                        getString(R.string.vsi_tools_error, e.getLocalizedMessage())
                ));
            }
        }, "VSI Converter").start();
    }

    private LinearLayout buildRoot(int titleRes, int summaryRes) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextSize(28);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView summary = new TextView(this);
        summary.setText(summaryRes);
        summary.setTextSize(15);
        summary.setPadding(0, dp(10), 0, dp(22));
        root.addView(summary);

        return root;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
