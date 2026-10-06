package com.aefyr.sai.ui.activities;

import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.aefyr.sai.R;
import com.aefyr.sai.installer.ApkSourceBuilder;
import com.aefyr.sai.installer2.base.model.SaiPiSessionParams;
import com.aefyr.sai.installer2.impl.FlexSaiPackageInstaller;
import com.aefyr.sai.model.apksource.ApkSource;
import com.aefyr.sai.shell.Shell;
import com.aefyr.sai.shell.ShizukuShell;
import com.aefyr.sai.shell.SuShell;
import com.aefyr.sai.utils.PreferencesHelper;
import com.aefyr.sai.utils.PreferencesValues;
import com.aefyr.sai.utils.VsiDeveloperOptions;
import com.aefyr.sai.utils.VsiInstallPolicy;
import com.aefyr.sai.utils.VsiPackagePreflight;
import com.aefyr.sai.xposed.VsiXposedRuntimeProbe;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.Collections;

public class ApkActionViewProxyActivity extends ThemedActivity {

    private Uri mUri;
    private BottomSheetDialog mSheet;
    private LinearLayout mContent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Intent.ACTION_VIEW.equals(getIntent().getAction()) || getIntent().getData() == null) {
            finish();
            return;
        }

        mUri = getIntent().getData();

        if (!VsiDeveloperOptions.getInstance(this).quickInstallSheet()) {
            forwardToVsi();
            return;
        }

        showLoadingSheet();

        new Thread(() -> {
            try {
                VsiPackagePreflight.Result result = VsiPackagePreflight.analyze(this, mUri);
                runOnUiThread(() -> showResult(result));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e));
            }
        }, "VSI Preflight").start();
    }

    private void showLoadingSheet() {
        mSheet = new BottomSheetDialog(this);
        mContent = new LinearLayout(this);
        mContent.setOrientation(LinearLayout.VERTICAL);
        mContent.setPadding(dp(24), dp(18), dp(24), dp(24));

        TextView title = text(getString(R.string.vsi_quick_title), 24, true);
        TextView body = text(getString(R.string.vsi_quick_analyzing), 15, false);
        body.setPadding(0, dp(12), 0, 0);

        mContent.addView(title);
        mContent.addView(body);

        mSheet.setContentView(mContent);
        mSheet.setOnDismissListener(dialog -> finish());
        mSheet.setCanceledOnTouchOutside(true);
        mSheet.show();
    }

    private void showResult(VsiPackagePreflight.Result result) {
        mContent.removeAllViews();

        mContent.addView(text(
                result.appName != null ? result.appName : result.packageName,
                24,
                true
        ));

        StringBuilder meta = new StringBuilder();
        meta.append(result.packageName).append("\n");

        if (VsiDeveloperOptions.getInstance(this).updateCompare()) {
            meta.append(operationLabel(result.operation)).append("\n");
            if (result.installed) {
                meta.append(nullToQuestion(result.installedVersionName))
                        .append(" (").append(result.installedVersionCode).append(")")
                        .append("  →  ")
                        .append(nullToQuestion(result.versionName))
                        .append(" (").append(result.versionCode).append(")")
                        .append("\n");
            } else {
                meta.append(nullToQuestion(result.versionName))
                        .append(" (").append(result.versionCode).append(")")
                        .append("\n");
            }
        }

        meta.append("SDK ").append(result.minSdk)
                .append(" → target ").append(result.targetSdk);

        if (result.installed) {
            meta.append("\n")
                    .append(getString(R.string.vsi_quick_signature))
                    .append(": ")
                    .append(result.signatureMatch ? "✅ " : "❌ ")
                    .append(result.signatureMatch
                            ? getString(R.string.vsi_quick_signature_match)
                            : getString(R.string.vsi_quick_signature_mismatch));
        }

        if (!result.addedPermissions.isEmpty()) {
            meta.append("\n")
                    .append(getString(R.string.vsi_quick_new_permissions))
                    .append(": ")
                    .append(result.addedPermissions.size());
        }

        if (VsiDeveloperOptions.getInstance(this).showSourceIdentity())
            meta.append("\n").append(getString(R.string.vsi_history_source)).append(": ").append(mUri);

        TextView body = text(meta.toString(), 15, false);
        body.setPadding(0, dp(12), 0, dp(18));
        mContent.addView(body);

        if (result.installed && !result.signatureMatch) {
            TextView warning = text(getString(R.string.vsi_quick_signature_warning), 14, true);
            warning.setPadding(0, 0, 0, dp(14));
            mContent.addView(warning);
        }

        MaterialButton install = button(getString(R.string.vsi_quick_install));
        install.setOnClickListener(v -> {
            if (result.installed
                    && !result.signatureMatch
                    && VsiDeveloperOptions.getInstance(this).signatureGuard()) {
                showSignatureDecision(result);
            } else {
                startInstallationWithPreviewIfNeeded();
            }
        });
        mContent.addView(install);

        if (result.installed
                && !result.signatureMatch
                && !result.installedSystemApp
                && VsiDeveloperOptions.getInstance(this).allowSignatureReplacement()
                && privilegedReplacementShell() != null) {
            MaterialButton replace = button(getString(R.string.vsi_quick_privileged_replace));
            replace.setOnClickListener(v -> confirmPrivilegedReplace(result));
            mContent.addView(replace);
        }

        MaterialButton cancel = button(getString(android.R.string.cancel));
        cancel.setOnClickListener(v -> {
            mSheet.dismiss();
            finish();
        });
        mContent.addView(cancel);
    }

    private void showSignatureDecision(VsiPackagePreflight.Result result) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.vsi_quick_signature_mismatch)
                .setMessage(R.string.vsi_quick_signature_warning)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(
                        R.string.vsi_quick_try_normal,
                        (d, which) -> startInstallationWithPreviewIfNeeded()
                );

        if (!result.installedSystemApp
                && VsiDeveloperOptions.getInstance(this).allowSignatureReplacement()
                && privilegedReplacementShell() != null) {
            builder.setPositiveButton(
                    R.string.vsi_quick_privileged_replace,
                    (d, which) -> confirmPrivilegedReplace(result)
            );
        }

        builder.show();
    }

    private void confirmPrivilegedReplace(VsiPackagePreflight.Result result) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.vsi_quick_replace_confirm_title)
                .setMessage(R.string.vsi_quick_replace_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.vsi_quick_replace_confirm, (d, which) ->
                        performPrivilegedReplace(result))
                .show();
    }

    private void performPrivilegedReplace(VsiPackagePreflight.Result result) {
        Shell shell = privilegedReplacementShell();
        if (shell == null) {
            Toast.makeText(this, R.string.vsi_quick_no_privileged_backend, Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, R.string.vsi_quick_removing_old, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            Shell.Result uninstall = shell.exec(new Shell.Command("pm", "uninstall", result.packageName));
            runOnUiThread(() -> {
                if (!uninstall.isSuccessful()) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.error)
                            .setMessage(uninstall.toString())
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }

                Toast.makeText(this, R.string.vsi_quick_old_removed, Toast.LENGTH_SHORT).show();
                startInstallationWithPreviewIfNeeded();
            });
        }, "VSI Signature Replace").start();
    }

    @Nullable
    private Shell privilegedReplacementShell() {
        int mode = PreferencesHelper.getInstance(this).getInstaller();

        if (mode == PreferencesValues.INSTALLER_ROOTED) {
            return SuShell.getInstance().isAvailable() ? SuShell.getInstance() : null;
        }

        if (mode == PreferencesValues.INSTALLER_SHIZUKU) {
            return ShizukuShell.getInstance().isAvailable() ? ShizukuShell.getInstance() : null;
        }

        if (mode == PreferencesValues.INSTALLER_XPOSED && VsiXposedRuntimeProbe.isActive()) {
            if (SuShell.getInstance().isAvailable())
                return SuShell.getInstance();
            if (ShizukuShell.getInstance().isAvailable())
                return ShizukuShell.getInstance();
        }

        return null;
    }

    private void showError(Exception error) {
        mContent.removeAllViews();
        mContent.addView(text(getString(R.string.vsi_quick_error_title), 22, true));
        TextView body = text(error.getLocalizedMessage() == null ? error.toString() : error.getLocalizedMessage(), 14, false);
        body.setPadding(0, dp(10), 0, dp(16));
        mContent.addView(body);

        MaterialButton full = button(getString(R.string.vsi_quick_open_installer));
        full.setOnClickListener(v -> forwardToVsi());
        mContent.addView(full);
    }

    private void startInstallationWithPreviewIfNeeded() {
        int installer = PreferencesHelper.getInstance(this).getInstaller();
        boolean privilegedShell = installer == PreferencesValues.INSTALLER_ROOTED
                || installer == PreferencesValues.INSTALLER_SHIZUKU;

        if (privilegedShell
                && VsiDeveloperOptions.getInstance(this).expertCommandPreview()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.vsi_dev_expert_command_preview)
                    .setMessage(VsiInstallPolicy.buildCommandPreview(this))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(
                            R.string.vsi_quick_install,
                            (dialog, which) -> enqueueDirectInstall()
                    )
                    .show();
            return;
        }

        enqueueDirectInstall();
    }

    private void enqueueDirectInstall() {
        try {
            PreferencesHelper prefs = PreferencesHelper.getInstance(this);

            ApkSource apkSource = new ApkSourceBuilder(this)
                    .fromApkContentUris(Collections.singletonList(mUri))
                    .setSigningEnabled(prefs.shouldSignApks())
                    .build();

            SaiPiSessionParams params = VsiInstallPolicy.apply(
                    this,
                    new SaiPiSessionParams(apkSource)
                            .setSourceUris(Collections.singletonList(mUri))
            );

            FlexSaiPackageInstaller installer = FlexSaiPackageInstaller.getInstance(this);
            String sessionId = installer.createSessionOnInstaller(
                    prefs.getInstaller(),
                    params
            );
            installer.enqueueSession(sessionId);

            Toast.makeText(this, R.string.vsi_quick_queued, Toast.LENGTH_SHORT).show();

            if (mSheet != null)
                mSheet.setOnDismissListener(null);

            finish();
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.error)
                    .setMessage(e.getLocalizedMessage() == null ? e.toString() : e.getLocalizedMessage())
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(
                            R.string.vsi_quick_open_installer,
                            (dialog, which) -> forwardToVsi()
                    )
                    .show();
        }
    }

    private void forwardToVsi() {
        Intent source = getIntent();
        Intent main = new Intent(this, MainActivity.class);
        main.setAction(Intent.ACTION_VIEW);

        if (source.getType() != null)
            main.setDataAndType(mUri, source.getType());
        else
            main.setData(mUri);

        int grantFlags = source.getFlags() &
                (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);

        main.addFlags(grantFlags | Intent.FLAG_GRANT_READ_URI_PERMISSION);

        ClipData clipData = source.getClipData();
        if (clipData != null)
            main.setClipData(clipData);
        else
            main.setClipData(ClipData.newRawUri("VSI install source", mUri));

        startActivity(main);

        if (mSheet != null)
            mSheet.setOnDismissListener(null);
        finish();
    }

    private String operationLabel(VsiPackagePreflight.Operation operation) {
        switch (operation) {
            case UPDATE:
                return getString(R.string.vsi_quick_update);
            case DOWNGRADE:
                return getString(R.string.vsi_quick_downgrade);
            case REINSTALL:
                return getString(R.string.vsi_quick_reinstall);
            default:
                return getString(R.string.vsi_quick_new_install);
        }
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        if (bold)
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        return tv;
    }

    private MaterialButton button(String value) {
        MaterialButton button = new MaterialButton(this);
        button.setText(value);
        button.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = dp(8);
        button.setLayoutParams(lp);
        return button;
    }

    private String nullToQuestion(String s) {
        return s == null ? "?" : s;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public static ComponentName getComponentName(Context context) {
        return new ComponentName(context, ApkActionViewProxyActivity.class);
    }
}
