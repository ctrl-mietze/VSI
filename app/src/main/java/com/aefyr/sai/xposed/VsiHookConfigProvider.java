package com.aefyr.sai.xposed;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.aefyr.sai.utils.PreferencesHelper;
import com.aefyr.sai.utils.PreferencesValues;

/**
 * Tiny read-only bridge used by the hook running inside the stock package
 * installer process. It exposes no private user data; only the currently
 * selected VSI installer mode.
 */
public class VsiHookConfigProvider extends ContentProvider {

    private static final String[] COLUMNS = new String[]{"enabled", "installer"};

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                        @Nullable String selection, @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        int installer = PreferencesHelper.getInstance(getContext()).getInstaller();

        MatrixCursor cursor = new MatrixCursor(COLUMNS, 1);
        cursor.addRow(new Object[]{
                installer == PreferencesValues.INSTALLER_XPOSED ? 1 : 0,
                installer
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
