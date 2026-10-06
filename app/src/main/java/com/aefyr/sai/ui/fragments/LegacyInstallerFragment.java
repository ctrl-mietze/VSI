package com.aefyr.sai.ui.fragments;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.aefyr.sai.R;
import com.aefyr.sai.ui.activities.MainActivity;
import com.aefyr.sai.ui.activities.PreferencesActivity;
import com.aefyr.sai.ui.dialogs.AppInstalledDialogFragment;
import com.aefyr.sai.ui.dialogs.ErrorLogDialogFragment;
import com.aefyr.sai.ui.dialogs.FilePickerDialogFragment;
import com.aefyr.sai.ui.dialogs.InstallationConfirmationDialogFragment;
import com.aefyr.sai.ui.dialogs.ThemeSelectionDialogFragment;
import com.aefyr.sai.utils.AlertsUtils;
import com.aefyr.sai.utils.PermissionsUtils;
import com.aefyr.sai.utils.PreferencesHelper;
import com.aefyr.sai.utils.Utils;
import com.aefyr.sai.utils.VsiInstallerFeedback;
import com.aefyr.sai.utils.saf.SafUtils;
import com.aefyr.sai.viewmodels.LegacyInstallerViewModel;
import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LegacyInstallerFragment extends InstallerFragment implements FilePickerDialogFragment.OnFilesSelectedListener, InstallationConfirmationDialogFragment.ConfirmationListener {

    private static final int REQUEST_CODE_GET_FILES = 337;

    private LegacyInstallerViewModel mViewModel;
    private Button mButton;
    private ImageButton mButtonSettings;

    private PreferencesHelper mHelper;

    private Uri mPendingActionViewUri;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mHelper = PreferencesHelper.getInstance(getContext());

        mButton = findViewById(R.id.button_install);
        mButtonSettings = findViewById(R.id.ib_settings);

        mViewModel = new ViewModelProvider(this).get(LegacyInstallerViewModel.class);
        mViewModel.getState().observe(getViewLifecycleOwner(), (state) -> {
            switch (state) {
                case IDLE:
                    mButton.setText(R.string.installer_install_apks);
                    mButton.setEnabled(true);
                    setNavigationEnabled(true);
                    break;
                case INSTALLING:
                    mButton.setText(R.string.installer_installation_in_progress);
                    mButton.setEnabled(false);
                    setNavigationEnabled(false);
                    break;
            }
        });
        mViewModel.getEvents().observe(getViewLifecycleOwner(), (event) -> {
            if (event.isConsumed())
                return;

            String[] eventData = event.consume();
            String feedbackMode = mHelper.getInstallerFeedbackMode();

            if (VsiInstallerFeedback.MODE_NONE.equals(feedbackMode)
                    || VsiInstallerFeedback.MODE_NOTIFICATION.equals(feedbackMode)) {
                return;
            }

            switch (eventData[0]) {
                case LegacyInstallerViewModel.EVENT_PACKAGE_INSTALLED:
                    if (VsiInstallerFeedback.MODE_POPUP.equals(feedbackMode))
                        showInstallSuccessPopup(eventData[1]);
                    else
                        showPackageInstalledAlert(eventData[1]);
                    break;

                case LegacyInstallerViewModel.EVENT_INSTALLATION_FAILED:
                    if (VsiInstallerFeedback.MODE_POPUP.equals(feedbackMode))
                        showInstallFailurePopup(eventData[1]);
                    else
                        ErrorLogDialogFragment.newInstance(
                                getString(R.string.installer_installation_failed),
                                eventData[1]
                        ).show(getChildFragmentManager(), "installation_error_dialog");
                    break;
            }
        });

        findViewById(R.id.ib_toggle_theme).setOnClickListener((v -> ThemeSelectionDialogFragment.newInstance(requireContext()).show(getChildFragmentManager(), "theme_selection_dialog")));
        mButtonSettings.setOnClickListener((v) -> PreferencesActivity.open(requireContext(), PreferencesFragment.class, getString(R.string.settings_title)));

        mButton.setOnClickListener((v) -> checkPermissionsAndPickFiles());
        mButton.setOnLongClickListener((v) -> pickFilesWithSaf());
        findViewById(R.id.button_help).setOnClickListener((v) -> AlertsUtils.showAlert(this, R.string.help, R.string.installer_help));

        if (mPendingActionViewUri != null) {
            handleActionView(mPendingActionViewUri);
            mPendingActionViewUri = null;
        }
    }

    @Override
    public void handleActionView(Uri uri) {
        if (!isAdded()) {
            mPendingActionViewUri = uri;
            return;
        }

        DialogFragment existingDialog = (DialogFragment) getChildFragmentManager().findFragmentByTag("installation_confirmation_dialog");
        if (existingDialog != null)
            existingDialog.dismiss();
        InstallationConfirmationDialogFragment.newInstance(uri).show(getChildFragmentManager(), "installation_confirmation_dialog");
    }

    private void checkPermissionsAndPickFiles() {
        if (!PermissionsUtils.checkAndRequestStoragePermissions(this))
            return;

        DialogProperties properties = new DialogProperties();
        properties.selection_mode = DialogConfigs.MULTI_MODE;
        properties.selection_type = DialogConfigs.FILE_SELECT;
        properties.root = Environment.getExternalStorageDirectory();
        properties.offset = new File(mHelper.getHomeDirectory());
        properties.extensions = null; // VSI custom containers may use any final extension
        properties.sortBy = mHelper.getFilePickerSortBy();
        properties.sortOrder = mHelper.getFilePickerSortOrder();

        FilePickerDialogFragment.newInstance(null, getString(R.string.installer_pick_apks), properties).show(getChildFragmentManager(), "dialog_files_picker");
    }

    private boolean pickFilesWithSaf() {
        Intent getContentIntent = new Intent(Intent.ACTION_GET_CONTENT);
        getContentIntent.setType("*/*");
        getContentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(getContentIntent, getString(R.string.installer_pick_apks)), REQUEST_CODE_GET_FILES);

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionsUtils.REQUEST_CODE_STORAGE_PERMISSIONS) {
            if (grantResults.length == 0 || grantResults[0] == PackageManager.PERMISSION_DENIED)
                AlertsUtils.showAlert(this, R.string.error, R.string.permissions_required_storage);
            else
                checkPermissionsAndPickFiles();
        }

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_GET_FILES) {
            if (resultCode != Activity.RESULT_OK || data == null)
                return;

            if (data.getData() != null) {
                installContentUris(Collections.singletonList(data.getData()));
                return;
            }

            if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                List<Uri> sourceUris = new ArrayList<>(clipData.getItemCount());

                for (int i = 0; i < clipData.getItemCount(); i++)
                    sourceUris.add(clipData.getItemAt(i).getUri());

                installContentUris(sourceUris);
            }
        }
    }

    private void showPackageInstalledAlert(String packageName) {
        AppInstalledDialogFragment.newInstance(packageName).show(getChildFragmentManager(), "dialog_app_installed");
    }

    private void showInstallSuccessPopup(@Nullable String packageName) {
        Snackbar snackbar = Snackbar.make(
                requireView(),
                R.string.vsi_feedback_popup_success,
                Snackbar.LENGTH_LONG
        );
        snackbar.setBackgroundTint(Color.rgb(20, 20, 20));
        snackbar.show();
    }

    private void showInstallFailurePopup(@Nullable String error) {
        String reason = error;
        if (reason == null || reason.trim().isEmpty())
            reason = getString(R.string.vsi_feedback_unknown_error);

        String compact = reason.replace('\n', ' ').trim();
        if (compact.length() > 180)
            compact = compact.substring(0, 177) + "...";

        Snackbar snackbar = Snackbar.make(
                requireView(),
                getString(R.string.vsi_feedback_popup_failure, compact),
                Snackbar.LENGTH_LONG
        );
        snackbar.setBackgroundTint(Color.rgb(20, 20, 20));
        final String fullReason = reason;
        snackbar.setAction(R.string.vsi_feedback_more, v ->
                ErrorLogDialogFragment.newInstance(
                        getString(R.string.installer_installation_failed),
                        fullReason
                ).show(getChildFragmentManager(), "installation_error_dialog")
        );
        snackbar.show();
    }

    private void setNavigationEnabled(boolean enabled) {
        ((MainActivity) requireActivity()).setNavigationEnabled(enabled);

        mButtonSettings.setEnabled(enabled);
        mButtonSettings.animate()
                .alpha(enabled ? 1f : 0.4f)
                .setDuration(300)
                .start();
    }

    @Override
    public void onFilesSelected(String tag, List<File> files) {
        if (files.isEmpty())
            return;

        if (files.size() == 1) {
            String extension = Utils.getExtension(files.get(0).getName());
            if (extension == null || !"apk".equalsIgnoreCase(extension)) {
                mViewModel.installPackagesFromZip(files.get(0));
                return;
            }
        }

        for (File file : files) {
            String extension = Utils.getExtension(file.getName());
            if (extension == null || !"apk".equalsIgnoreCase(extension)) {
                AlertsUtils.showAlert(this, R.string.error, R.string.installer_error_mixed_extensions);
                return;
            }
        }

        mViewModel.installPackages(files);
    }

    private void installContentUris(List<Uri> sourceUris) {
        if (sourceUris.size() == 1) {
            Uri uri = sourceUris.get(0);
            String fileName = SafUtils.getFileNameFromContentUri(requireContext(), uri);
            String extension = fileName == null ? null : Utils.getExtension(fileName);
            if (extension == null || !"apk".equalsIgnoreCase(extension)) {
                mViewModel.installPackagesFromContentProviderZip(uri);
                return;
            }
        }

        List<Uri> apkUris = new ArrayList<>();
        for (Uri uri : sourceUris) {
            String fileName = SafUtils.getFileNameFromContentUri(requireContext(), uri);
            String extension = fileName == null ? null : Utils.getExtension(fileName);
            if (extension == null || !"apk".equalsIgnoreCase(extension)) {
                AlertsUtils.showAlert(this, R.string.error, R.string.installer_error_mixed_extensions);
                return;
            }
            apkUris.add(uri);
        }

        mViewModel.installPackagesFromContentProviderUris(apkUris);
    }

    @Override
    public void onConfirmed(Uri apksFileUri) {
        installContentUris(Collections.singletonList(apksFileUri));
    }

    @Override
    protected int layoutId() {
        return R.layout.fragment_installer;
    }
}
