package com.aefyr.sai.xposed;

import android.app.Activity;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.aefyr.sai.BuildConfig;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.callbacks.XCallback;

/**
 * Reversible VSI entry hook for LSPosed and Vector.
 *
 * Recommended scope:
 *   com.google.android.packageinstaller
 *
 * The hook never patches the system APK. While the Xposed-compatible runtime
 * is active it redirects direct APK VIEW / INSTALL_PACKAGE entry intents to
 * VSI. PackageInstaller confirmation intents remain stock to avoid loops.
 */
public class VsiSystemInstallerHook implements IXposedHookLoadPackage {

    public static final String TARGET_PACKAGE = "com.google.android.packageinstaller";
    public static final String EXTRA_FROM_SYSTEM_HOOK =
            "ctrl.mietze.vsi.extra.FROM_SYSTEM_INSTALLER_HOOK";
    public static final String EXTRA_BYPASS_SYSTEM_HOOK =
            "ctrl.mietze.vsi.extra.BYPASS_SYSTEM_INSTALLER_HOOK";

    private static final String[] ENTRY_ACTIVITIES = new String[]{
            "com.android.packageinstaller.InstallStart",
            "com.android.packageinstaller.v2.ui.InstallLaunch"
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (BuildConfig.APPLICATION_ID.equals(lpparam.packageName)) {
            hookVsiRuntimeProbe(lpparam.classLoader);
            return;
        }

        if (!TARGET_PACKAGE.equals(lpparam.packageName))
            return;

        for (String activityClassName : ENTRY_ACTIVITIES)
            hookEntryActivity(activityClassName, lpparam.classLoader);
    }

    private void hookVsiRuntimeProbe(ClassLoader classLoader) {
        Class<?> probeClass = XposedHelpers.findClassIfExists(
                "com.aefyr.sai.xposed.VsiXposedRuntimeProbe",
                classLoader
        );
        if (probeClass == null) {
            XposedBridge.log("VSI System Hook: runtime probe class not found");
            return;
        }

        XposedHelpers.findAndHookMethod(
                probeClass,
                "isActive",
                XC_MethodReplacement.returnConstant(true)
        );
        XposedBridge.log("VSI System Hook: runtime probe active");
    }

    private void hookEntryActivity(String className, ClassLoader classLoader) {
        Class<?> activityClass = XposedHelpers.findClassIfExists(className, classLoader);
        if (activityClass == null) {
            XposedBridge.log("VSI System Hook: class not present: " + className);
            return;
        }

        XposedHelpers.findAndHookMethod(activityClass, "onCreate", Bundle.class,
                new XC_MethodHook(XCallback.PRIORITY_HIGHEST) {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!(param.thisObject instanceof Activity))
                            return;

                        Activity activity = (Activity) param.thisObject;
                        Intent original = activity.getIntent();

                        if (!shouldRedirect(activity, original))
                            return;

                        try {
                            Intent vsiIntent = buildVsiIntent(original);
                            activity.startActivity(vsiIntent);
                            activity.finish();
                            activity.overridePendingTransition(0, 0);

                            // Stop stock staging only after VSI launched successfully.
                            param.setResult(null);
                            XposedBridge.log("VSI System Hook: redirected " +
                                    original.getAction() + " -> " + BuildConfig.APPLICATION_ID);
                        } catch (Throwable t) {
                            // Fail open: stock Package Installer continues untouched.
                            XposedBridge.log("VSI System Hook: redirect failed, using stock installer");
                            XposedBridge.log(t);
                        }
                    }
                });
    }

    private boolean shouldRedirect(Activity activity, Intent intent) {
        if (intent == null)
            return false;

        if (!isXposedInstallerModeEnabled(activity))
            return false;

        if (intent.getBooleanExtra(EXTRA_BYPASS_SYSTEM_HOOK, false))
            return false;

        String action = intent.getAction();
        if (!Intent.ACTION_VIEW.equals(action) && !Intent.ACTION_INSTALL_PACKAGE.equals(action))
            return false;

        Uri data = intent.getData();
        if (data == null)
            return false;

        String scheme = data.getScheme();
        return "content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme);
    }

    private boolean isXposedInstallerModeEnabled(Activity activity) {
        Uri statusUri = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".hookconfig/status");

        try (Cursor cursor = activity.getContentResolver().query(
                statusUri,
                new String[]{"enabled", "installer"},
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst())
                return false;

            int enabledColumn = cursor.getColumnIndex("enabled");
            return enabledColumn >= 0 && cursor.getInt(enabledColumn) == 1;
        } catch (Throwable t) {
            // VSI missing/unavailable/config bridge blocked -> leave stock installer untouched.
            XposedBridge.log("VSI System Hook: unable to read VSI hook mode; using stock installer");
            XposedBridge.log(t);
            return false;
        }
    }

    private Intent buildVsiIntent(Intent original) {
        Uri data = original.getData();

        Intent out = new Intent(Intent.ACTION_VIEW);
        out.setComponent(new ComponentName(
                BuildConfig.APPLICATION_ID,
                "com.aefyr.sai.ui.activities.MainActivity"
        ));

        if (original.getType() != null)
            out.setDataAndType(data, original.getType());
        else
            out.setData(data);

        int grantFlags = original.getFlags() &
                (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);

        out.addFlags(grantFlags | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        out.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        out.putExtra(EXTRA_FROM_SYSTEM_HOOK, true);

        ClipData clipData = original.getClipData();
        if (clipData != null)
            out.setClipData(clipData);
        else if (data != null)
            out.setClipData(ClipData.newRawUri("VSI install source", data));

        return out;
    }
}
