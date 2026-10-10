package com.aefyr.sai.xposed;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.aefyr.sai.runtime.VsiAppMode;
import com.aefyr.sai.runtime.VsiModeManager;
import com.aefyr.sai.runtime.VsiSecurityWindowManager;
import com.aefyr.sai.utils.PreferencesHelper;
import com.aefyr.sai.utils.VsiDeveloperOptions;

public class VsiHookConfigProvider extends ContentProvider {

    private static final String[] COLUMNS = new String[]{
            "enabled",
            "installer",
            "appMode",
            "verbose",
            "stockFallback",
            "downgradeUntil",
            "signatureUntil"
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
        VsiAppMode mode = VsiModeManager.getCurrentMode(getContext());
        VsiDeveloperOptions dev = VsiDeveloperOptions.getInstance(getContext());

        MatrixCursor cursor = new MatrixCursor(COLUMNS, 1);
        cursor.addRow(new Object[]{
                mode.usesXposed() ? 1 : 0,
                PreferencesHelper.getInstance(getContext()).getInstaller(),
                mode.id(),
                dev.xposedVerbose() ? 1 : 0,
                dev.xposedStockFallback() ? 1 : 0,
                mode.usesXposed()
                        ? VsiSecurityWindowManager.downgradeUntil(getContext())
                        : 0L,
                mode.usesXposed()
                        ? VsiSecurityWindowManager.signatureUntil(getContext())
                        : 0L
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
