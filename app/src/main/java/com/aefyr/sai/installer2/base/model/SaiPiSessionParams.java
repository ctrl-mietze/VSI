package com.aefyr.sai.installer2.base.model;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.aefyr.sai.model.apksource.ApkSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SaiPiSessionParams {

    private final ApkSource mApkSource;
    private boolean mAllowDowngrade;
    private boolean mAllowTestApks;
    private int mTargetUserId = -1;
    private boolean mDeleteSourceAfterSuccess;
    private List<Uri> mSourceUris = Collections.emptyList();

    public SaiPiSessionParams(@NonNull ApkSource apkSource) {
        mApkSource = apkSource;
    }

    @NonNull
    public ApkSource apkSource() {
        return mApkSource;
    }

    public SaiPiSessionParams setAllowDowngrade(boolean allowDowngrade) {
        mAllowDowngrade = allowDowngrade;
        return this;
    }

    public boolean allowDowngrade() {
        return mAllowDowngrade;
    }

    public SaiPiSessionParams setAllowTestApks(boolean allowTestApks) {
        mAllowTestApks = allowTestApks;
        return this;
    }

    public boolean allowTestApks() {
        return mAllowTestApks;
    }

    public SaiPiSessionParams setTargetUserId(int targetUserId) {
        mTargetUserId = targetUserId;
        return this;
    }

    public int targetUserId() {
        return mTargetUserId;
    }

    public SaiPiSessionParams setDeleteSourceAfterSuccess(boolean deleteSourceAfterSuccess) {
        mDeleteSourceAfterSuccess = deleteSourceAfterSuccess;
        return this;
    }

    public boolean deleteSourceAfterSuccess() {
        return mDeleteSourceAfterSuccess;
    }

    public SaiPiSessionParams setSourceUris(@NonNull List<Uri> sourceUris) {
        mSourceUris = Collections.unmodifiableList(new ArrayList<>(sourceUris));
        return this;
    }

    @NonNull
    public List<Uri> sourceUris() {
        return mSourceUris;
    }
}
