package com.aefyr.sai.installer2.impl.rootless;

import android.content.Context;

/**
 * Xposed/Vector mode uses the same Android PackageInstaller.Session transport
 * as rootless installs, but it is intentionally a distinct VSI backend so
 * sessions and UI can identify the fourth installer mode correctly.
 *
 * The system-wide interception itself lives in VsiSystemInstallerHook.
 */
public class XposedSaiPackageInstaller extends RootlessSaiPackageInstaller {

    private static final String TAG = "XposedSaiPi";
    private static XposedSaiPackageInstaller sInstance;

    public static XposedSaiPackageInstaller getInstance(Context c) {
        synchronized (XposedSaiPackageInstaller.class) {
            if (sInstance == null)
                sInstance = new XposedSaiPackageInstaller(c);
            return sInstance;
        }
    }

    private XposedSaiPackageInstaller(Context c) {
        super(c);
    }

    @Override
    protected String tag() {
        return TAG;
    }
}
