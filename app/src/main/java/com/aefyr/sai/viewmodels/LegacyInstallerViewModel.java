package com.aefyr.sai.viewmodels;


import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.aefyr.sai.installer.ApkSourceBuilder;
import com.aefyr.sai.installer.PackageInstallerProvider;
import com.aefyr.sai.installer.SAIPackageInstaller;
import com.aefyr.sai.model.apksource.ApkSource;
import com.aefyr.sai.utils.Event;
import com.aefyr.sai.utils.PreferencesHelper;
import com.aefyr.sai.utils.VsiDeveloperOptions;
import com.aefyr.sai.utils.VsiSessionHistoryStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LegacyInstallerViewModel extends AndroidViewModel implements SAIPackageInstaller.InstallationStatusListener {
    private static final String TAG = "LegacyInstallerVM";

    public static final String EVENT_PACKAGE_INSTALLED = "package_installed";
    public static final String EVENT_INSTALLATION_FAILED = "installation_failed";

    private SAIPackageInstaller mInstaller;
    private Context mContext;
    private PreferencesHelper mPrefsHelper;
    private long mOngoingSessionId;
    private List<Uri> mCurrentSourceUris = Collections.emptyList();

    public enum InstallerState {
        IDLE, INSTALLING
    }

    private MutableLiveData<InstallerState> mState = new MutableLiveData<>();
    private MutableLiveData<Event<String[]>> mEvents = new MutableLiveData<>();

    public LegacyInstallerViewModel(@NonNull Application application) {
        super(application);
        mContext = application;
        mPrefsHelper = PreferencesHelper.getInstance(mContext);
        ensureInstallerActuality();
    }

    public LiveData<InstallerState> getState() {
        return mState;
    }

    public LiveData<Event<String[]>> getEvents() {
        return mEvents;
    }

    public void installPackages(List<File> apkFiles) {
        ensureInstallerActuality();

        ApkSource apkSource = new ApkSourceBuilder(mContext)
                .fromApkFiles(apkFiles)
                .setSigningEnabled(mPrefsHelper.shouldSignApks())
                .build();

        List<Uri> sourceUris = new ArrayList<>(apkFiles.size());
        for (File apkFile : apkFiles)
            sourceUris.add(Uri.fromFile(apkFile));
        mCurrentSourceUris = sourceUris;

        mOngoingSessionId = mInstaller.createInstallationSession(apkSource);
        mInstaller.startInstallationSession(mOngoingSessionId);
    }

    public void installPackagesFromZip(File zipWithApkFiles) {
        ensureInstallerActuality();

        ApkSource apkSource = new ApkSourceBuilder(mContext)
                .fromZipFile(zipWithApkFiles)
                .setZipExtractionEnabled(mPrefsHelper.shouldExtractArchives())
                .setReadZipViaZipFileEnabled(mPrefsHelper.shouldUseZipFileApi())
                .setSigningEnabled(mPrefsHelper.shouldSignApks())
                .build();

        mCurrentSourceUris = Collections.singletonList(Uri.fromFile(zipWithApkFiles));

        mOngoingSessionId = mInstaller.createInstallationSession(apkSource);
        mInstaller.startInstallationSession(mOngoingSessionId);
    }

    public void installPackagesFromContentProviderZip(Uri zipContentUri) {
        ensureInstallerActuality();

        ApkSource apkSource = new ApkSourceBuilder(mContext)
                .fromZipContentUri(zipContentUri)
                .setZipExtractionEnabled(mPrefsHelper.shouldExtractArchives())
                .setReadZipViaZipFileEnabled(mPrefsHelper.shouldUseZipFileApi())
                .setSigningEnabled(mPrefsHelper.shouldSignApks())
                .build();

        mCurrentSourceUris = Collections.singletonList(zipContentUri);

        mOngoingSessionId = mInstaller.createInstallationSession(apkSource);
        mInstaller.startInstallationSession(mOngoingSessionId);
    }

    public void installPackagesFromContentProviderUris(List<Uri> apkUris) {
        ensureInstallerActuality();

        ApkSource apkSource = new ApkSourceBuilder(mContext)
                .fromApkContentUris(apkUris)
                .setSigningEnabled(mPrefsHelper.shouldSignApks())
                .build();

        mCurrentSourceUris = new ArrayList<>(apkUris);

        mOngoingSessionId = mInstaller.createInstallationSession(apkSource);
        mInstaller.startInstallationSession(mOngoingSessionId);
    }

    private void ensureInstallerActuality() {
        SAIPackageInstaller actualInstaller = PackageInstallerProvider.getInstaller(mContext);
        if (actualInstaller != mInstaller) {
            if (mInstaller != null)
                mInstaller.removeStatusListener(this);

            mInstaller = actualInstaller;
            mInstaller.addStatusListener(this);
            mState.setValue(mInstaller.isInstallationInProgress() ? InstallerState.INSTALLING : InstallerState.IDLE);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mInstaller.removeStatusListener(this);
    }

    @Override
    public void onStatusChanged(long installationID, SAIPackageInstaller.InstallationStatus status, @Nullable String packageNameOrErrorDescription) {
        if (installationID != mOngoingSessionId)
            return;

        switch (status) {
            case QUEUED:
            case INSTALLING:
                mState.setValue(InstallerState.INSTALLING);
                break;
            case INSTALLATION_SUCCEED:
                mState.setValue(InstallerState.IDLE);

                VsiSessionHistoryStore.getInstance(mContext).recordLegacy(
                        installationID,
                        true,
                        packageNameOrErrorDescription,
                        mPrefsHelper.getInstaller(),
                        mCurrentSourceUris
                );

                if (mPrefsHelper.shouldDeleteSourceAfterInstall()
                        && !VsiDeveloperOptions.getInstance(mContext).neverDeleteSource()) {
                    deleteCurrentSources();
                } else {
                    clearCurrentSources();
                }

                mEvents.setValue(new Event<>(new String[]{EVENT_PACKAGE_INSTALLED, packageNameOrErrorDescription}));
                break;

            case INSTALLATION_FAILED:
                mState.setValue(InstallerState.IDLE);

                VsiSessionHistoryStore.getInstance(mContext).recordLegacy(
                        installationID,
                        false,
                        packageNameOrErrorDescription,
                        mPrefsHelper.getInstaller(),
                        mCurrentSourceUris
                );

                clearCurrentSources();
                mEvents.setValue(new Event<>(new String[]{EVENT_INSTALLATION_FAILED, packageNameOrErrorDescription}));
                break;
        }
    }

    private void deleteCurrentSources() {
        for (Uri uri : mCurrentSourceUris) {
            try {
                if ("file".equalsIgnoreCase(uri.getScheme())) {
                    String path = uri.getPath();
                    if (path != null && !new File(path).delete())
                        Log.w(TAG, "Unable to delete source file " + path);
                } else if ("content".equalsIgnoreCase(uri.getScheme())) {
                    int deleted = mContext.getContentResolver().delete(uri, null, null);
                    if (deleted <= 0)
                        Log.w(TAG, "Content provider did not delete source " + uri);
                }
            } catch (Exception e) {
                Log.w(TAG, "Unable to delete installation source " + uri, e);
            }
        }
        clearCurrentSources();
    }

    private void clearCurrentSources() {
        mCurrentSourceUris = Collections.emptyList();
    }
}
