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

        XposedHelpers.findAndHookMethod(
                activityClass,
                "onCreate",
                Bundle.class,
                new XC_MethodHook(XCallback.PRIORITY_HIGHEST) {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!(param.thisObject instanceof Activity))
                            return;

                        Activity activity = (Activity) param.thisObject;
                        Intent original = activity.getIntent();
                        HookConfig config = readConfig(activity);

                        if (!shouldRedirect(config, original))
                            return;

                        try {
                            Intent vsiIntent = buildVsiIntent(original);
                            activity.startActivity(vsiIntent);
                            activity.finish();
                            activity.overridePendingTransition(0, 0);
                            param.setResult(null);

                            if (config.verbose) {
                                XposedBridge.log(
                                        "VSI System Hook: redirected "
                                                + original.getAction()
                                                + " -> Quick Install Sheet"
                                );
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("VSI System Hook: redirect failed");
                            XposedBridge.log(t);

                            if (!config.stockFallback) {
                                activity.finish();
                                param.setResult(null);
                            } else if (config.verbose) {
                                XposedBridge.log("VSI System Hook: falling back to stock installer");
                            }
                        }
                    }
                }
        );
    }

    private boolean shouldRedirect(HookConfig config, Intent intent) {
        if (intent == null || !config.enabled)
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

    private HookConfig readConfig(Activity activity) {
        HookConfig config = new HookConfig();
        Uri statusUri = Uri.parse(
                "content://" + BuildConfig.APPLICATION_ID + ".hookconfig/status"
        );

        try (Cursor cursor = activity.getContentResolver().query(
                statusUri,
                new String[]{"enabled", "installer", "verbose", "stockFallback"},
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst())
                return config;

            config.enabled = readBool(cursor, "enabled", false);
            config.verbose = readBool(cursor, "verbose", false);
            config.stockFallback = readBool(cursor, "stockFallback", true);
            return config;
        } catch (Throwable t) {
            XposedBridge.log("VSI System Hook: unable to read VSI hook config");
            XposedBridge.log(t);
            return config;
        }
    }

    private boolean readBool(Cursor cursor, String column, boolean fallback) {
        int index = cursor.getColumnIndex(column);
        return index < 0 ? fallback : cursor.getInt(index) == 1;
    }

    private Intent buildVsiIntent(Intent original) {
        Uri data = original.getData();

        Intent out = new Intent(Intent.ACTION_VIEW);
        out.setComponent(new ComponentName(
                BuildConfig.APPLICATION_ID,
                "com.aefyr.sai.ui.activities.ApkActionViewProxyActivity"
        ));

        if (original.getType() != null)
            out.setDataAndType(data, original.getType());
        else
            out.setData(data);

        int grantFlags = original.getFlags() &
                (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);

        out.addFlags(grantFlags | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        out.putExtra(EXTRA_FROM_SYSTEM_HOOK, true);

        ClipData clipData = original.getClipData();
        if (clipData != null)
            out.setClipData(clipData);
        else if (data != null)
            out.setClipData(ClipData.newRawUri("VSI install source", data));

        return out;
    }

    private static class HookConfig {
        boolean enabled;
        boolean verbose;
        boolean stockFallback = true;
    }
}
