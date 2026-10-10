package com.aefyr.sai.xposed;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.aefyr.sai.utils.PreferencesHelper;
import com.aefyr.sai.utils.PreferencesKeys;
import com.aefyr.sai.utils.VsiAppMode;
import com.aefyr.sai.utils.VsiDeveloperOptions;
import com.aefyr.sai.utils.VsiModeManager;

public class VsiHookConfigProvider extends ContentProvider {

    private static final String[] COLUMNS = new String[]{
            "enabled",
            "appMode",
            "verbose",
            "stockFallback",
            "allowDowngrade",
            "signatureOverride",
            "securityTimestamp"
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                        @Nullable String selection, @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        PreferencesHelper prefs = PreferencesHelper.getInstance(getContext());
        VsiDeveloperOptions dev = VsiDeveloperOptions.getInstance(getContext());
        VsiAppMode mode = VsiModeManager.getMode(getContext());

        long securityTimestamp = prefs.getPrefs().getLong(
                PreferencesKeys.SECURITY_POLICY_TIMESTAMP,
                0L
        );

        MatrixCursor cursor = new MatrixCursor(COLUMNS, 1);
        cursor.addRow(new Object[]{
                mode.hasXposed() ? 1 : 0,
                mode.value(),
                dev.xposedVerbose() ? 1 : 0,
                dev.xposedStockFallback() ? 1 : 0,
                prefs.shouldAllowDowngrade() ? 1 : 0,
                dev.signatureVerificationOverride() ? 1 : 0,
                securityTimestamp
        });
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "vnd.android.cursor.item/vnd.vsi.hook-status";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                      @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }
}
