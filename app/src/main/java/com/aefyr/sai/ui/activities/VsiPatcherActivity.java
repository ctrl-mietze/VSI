package com.aefyr.sai.ui.activities;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.aefyr.sai.R;
import com.aefyr.sai.utils.VsiPatcherManager;
import com.google.android.material.button.MaterialButton;

public class VsiPatcherActivity extends ThemedActivity {

    private TextView mOutput;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(24));

        TextView title = new TextView(this);
        title.setText(R.string.vsi_patcher_title);
        title.setTextSize(26);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView summary = new TextView(this);
        summary.setText(R.string.vsi_patcher_summary);
        summary.setPadding(0, dp(8), 0, dp(18));
        root.addView(summary);

        addButton(root, R.string.vsi_patch_lite, R.string.vsi_patch_lite_summary,
                () -> confirm(
                        R.string.vsi_patch_lite,
                        R.string.vsi_patch_lite_confirm,
                        () -> runAsync(() -> VsiPatcherManager.installPatchLite(this))
                ));

        addButton(root, R.string.vsi_patch_lite_remove, R.string.vsi_patch_lite_remove_summary,
                () -> runAsync(VsiPatcherManager::removePatchLite));

        addButton(root, R.string.vsi_patch_vsi, R.string.vsi_patch_vsi_summary,
                () -> confirm(
                        R.string.vsi_patch_vsi,
                        R.string.vsi_patch_vsi_confirm,
                        () -> runAsync(() -> VsiPatcherManager.applyPatchVsiUser0(this))
                ));

        addButton(root, R.string.vsi_patch_vsi_restore, R.string.vsi_patch_vsi_restore_summary,
                () -> runAsync(() -> VsiPatcherManager.restorePatchVsiUser0(this)));

        addButton(root, R.string.vsi_patcher_pro, R.string.vsi_patcher_pro_summary,
                () -> confirm(
                        R.string.vsi_patcher_pro,
                        R.string.vsi_patcher_pro_confirm,
                        () -> runAsync(() -> VsiPatcherManager.installPatcherPro(this))
                ));

        addButton(root, R.string.vsi_patcher_pro_remove, R.string.vsi_patcher_pro_remove_summary,
                () -> runAsync(VsiPatcherManager::removePatcherPro));

        mOutput = new TextView(this);
        mOutput.setTextIsSelectable(true);
        mOutput.setPadding(0, dp(18), 0, dp(20));
        root.addView(mOutput);

        setContentView(root);
    }

    private void addButton(
            LinearLayout root,
            int title,
            int summary,
            Runnable action
    ) {
        MaterialButton button = new MaterialButton(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        root.addView(button);

        TextView help = new TextView(this);
        help.setText(summary);
        help.setPadding(dp(4), 0, dp(4), dp(12));
        root.addView(help);
    }

    private void confirm(int title, int message, Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ok, (d, w) -> action.run())
                .show();
    }

    private void runAsync(PatcherCall call) {
        mOutput.setText(R.string.vsi_tools_working);

        new Thread(() -> {
            VsiPatcherManager.Result result = call.run();
            runOnUiThread(() -> mOutput.setText(
                    (result.success ? "✅ " : "❌ ") + result.details
            ));
        }, "VSI Patcher").start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface PatcherCall {
        VsiPatcherManager.Result run();
    }
}
