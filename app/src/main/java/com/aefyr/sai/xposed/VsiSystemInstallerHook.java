package com.aefyr.sai.xposed;

import android.app.Activity;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

import com.aefyr.sai.BuildConfig;
import com.aefyr.sai.utils.VsiAppMode;
import com.aefyr.sai.utils.VsiSystemInstallerResolver;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.callbacks.XCallback;

public class VsiSystemInstallerHook implements IXposedHookLoadPackage {

    public static final String EXTRA_FROM_SYSTEM_HOOK =
            "ctrl.mietze.vsi.extra.FROM_SYSTEM_INSTALLER_HOOK";
    public static final String EXTRA_BYPASS_SYSTEM_HOOK =
            "ctrl.mietze.vsi.extra.BYPASS_SYSTEM_INSTALLER_HOOK";

    private static final long SIGNATURE_TIMEOUT_MS = 3 * 60 * 1000L;
    private static final long ACTIVE_INSTALL_WINDOW_MS = 60 * 1000L;
    private static final long SESSION_STALE_MS = 5 * 60 * 1000L;
    private static final long CONFIG_CACHE_MS = 1000L;
    private static final long REDIRECT_DEBOUNCE_MS = 1500L;

    private static final ConcurrentHashMap<Integer, Long> ACTIVE_INSTALL_SESSIONS =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> HOOKED_INSTALLER_PROCESSES =
            new ConcurrentHashMap<>();

    private static volatile String sLastRedirectKey;
    private static volatile long sLastRedirectRealtime;

    private static volatile long sLastInstallSessionRealtime;
    private static volatile long sLastSystemConfigReadRealtime;
    private static volatile HookConfig sCachedSystemConfig = new HookConfig();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (BuildConfig.APPLICATION_ID.equals(lpparam.packageName)) {
            hookVsiRuntimeProbe(lpparam.classLoader);
            return;
        }

        if ("android".equals(lpparam.packageName)) {
            hookSystemServerPolicies(lpparam.classLoader);
            return;
        }

        if (!VsiSystemInstallerResolver.looksLikeInstallerPackage(lpparam.packageName))
            return;

        hookGenericInstallerActivity(
                lpparam.packageName,
                lpparam.processName,
                lpparam.classLoader
        );
    }

    private void hookVsiRuntimeProbe(ClassLoader classLoader) {
        Class<?> probeClass = XposedHelpers.findClassIfExists(
                "com.aefyr.sai.xposed.VsiXposedRuntimeProbe",
                classLoader
        );
        if (probeClass == null)
            return;

        XposedHelpers.findAndHookMethod(
                probeClass,
                "isActive",
                XC_MethodReplacement.returnConstant(true)
        );
        XposedBridge.log("VSI: Xposed/Vector runtime probe active");
    }

    private void hookGenericInstallerActivity(
            String packageName,
            String processName,
            ClassLoader classLoader
    ) {
        String hookKey = packageName + ":" + (processName == null ? "?" : processName);
        if (HOOKED_INSTALLER_PROCESSES.putIfAbsent(hookKey, true) != null)
            return;

        try {
            Class<?> activityClass = XposedHelpers.findClass(
                    "android.app.Activity",
                    classLoader
            );

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

                            if (wasRecentlyRedirected(original))
                                return;

                            try {
                                Intent vsiIntent = buildVsiIntent(original);
                                activity.startActivity(vsiIntent);
                                markRedirect(original);
                                activity.finish();
                                activity.overridePendingTransition(0, 0);
                                param.setResult(null);

                                if (config.verbose) {
                                    XposedBridge.log(
                                            "VSI: " + packageName
                                                    + " redirected APK install -> VSI"
                                    );
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("VSI: installer redirect failed in " + packageName);
                                XposedBridge.log(t);

                                if (!config.stockFallback) {
                                    activity.finish();
                                    param.setResult(null);
                                }
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            XposedBridge.log("VSI: generic installer hook failed for " + packageName);
            XposedBridge.log(t);
        }
    }

    private void hookSystemServerPolicies(ClassLoader classLoader) {
        hookPackageInstallerService(classLoader);
        hookDowngradeHelpers(classLoader);
        hookSignatureChecks(classLoader);
    }

    private void hookPackageInstallerService(ClassLoader classLoader) {
        Class<?> clazz = XposedHelpers.findClassIfExists(
                "com.android.server.pm.PackageInstallerService",
                classLoader
        );
        if (clazz == null)
            return;

        XposedBridge.hookAllMethods(clazz, "createSession", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                HookConfig config = getSystemConfig(classLoader);
                if (!isRootXposed(config))
                    return;

                if (config.allowDowngrade) {
                    Object params = param.args != null && param.args.length > 0
                            ? param.args[0]
                            : null;
                    if (params != null) {
                        try {
                            Field field = params.getClass().getDeclaredField("installFlags");
                            field.setAccessible(true);
                            int flags = field.getInt(params);
                            field.setInt(params, flags | 0x00000082);
                            if (config.verbose)
                                XposedBridge.log("VSI: injected downgrade install flags");
                        } catch (Throwable t) {
                            if (config.verbose)
                                XposedBridge.log("VSI: unable to inject downgrade flags: " + t);
                        }
                    }
                }

                if (isSignaturePolicyEnabled(config))
                    sLastInstallSessionRealtime = SystemClock.elapsedRealtime();
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                HookConfig config = getSystemConfig(classLoader);
                if (!isSignaturePolicyEnabled(config))
                    return;

                Object result = param.getResult();
                if (result instanceof Integer) {
                    int sessionId = (Integer) result;
                    if (sessionId > 0) {
                        ACTIVE_INSTALL_SESSIONS.put(
                                sessionId,
                                SystemClock.elapsedRealtime()
                        );
                    }
                }
            }
        });

        XC_MethodHook cleanup = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length == 0)
                    return;
                Object id = param.args[0];
                if (id instanceof Integer)
                    ACTIVE_INSTALL_SESSIONS.remove((Integer) id);
            }
        };

        XposedBridge.hookAllMethods(clazz, "abandonSession", cleanup);
        XposedBridge.hookAllMethods(clazz, "cleanupSession", cleanup);
    }

    private void hookDowngradeHelpers(ClassLoader classLoader) {
        String[] classes = new String[]{
                "com.android.server.pm.InstallPackageHelper",
                "com.android.server.pm.PackageManagerServiceUtils",
                "com.android.server.pm.PackageManagerService"
        };

        String[] methods = new String[]{
                "checkDowngrade",
                "isDowngradePermitted"
        };

        for (String className : classes) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, classLoader);
            if (clazz == null)
                continue;

            for (String methodName : methods) {
                XposedBridge.hookAllMethods(clazz, methodName, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        HookConfig config = getSystemConfig(classLoader);
                        if (!isRootXposed(config) || !config.allowDowngrade)
                            return;

                        Class<?> returnType = ((java.lang.reflect.Method) param.method)
                                .getReturnType();

                        if (returnType == boolean.class || returnType == Boolean.class)
                            param.setResult(true);
                        else
                            param.setResult(null);
                    }
                });
            }
        }
    }

    private void hookSignatureChecks(ClassLoader classLoader) {
        String[] classes = new String[]{
                "com.android.server.pm.ComputerEngine",
                "com.android.server.pm.Computer"
        };

        for (String className : classes) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, classLoader);
            if (clazz == null)
                continue;

            XposedBridge.hookAllMethods(clazz, "checkSignatures", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    HookConfig config = getSystemConfig(classLoader);
                    if (isSignaturePolicyActiveForInstall(config))
                        param.setResult(0);
                }
            });
        }

        Class<?> signingDetails = XposedHelpers.findClassIfExists(
                "android.content.pm.SigningDetails",
                classLoader
        );

        if (signingDetails == null)
            return;

        XC_MethodHook boolOverride = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                HookConfig config = getSystemConfig(classLoader);
                if (isSignaturePolicyActiveForInstall(config))
                    param.setResult(true);
            }
        };

        XposedBridge.hookAllMethods(signingDetails, "checkCapability", boolOverride);
        XposedBridge.hookAllMethods(signingDetails, "hasAncestorOrSelf", boolOverride);
    }

    private boolean shouldRedirect(HookConfig config, Intent intent) {
        if (intent == null || !config.enabled)
            return false;

        if (intent.getBooleanExtra(EXTRA_BYPASS_SYSTEM_HOOK, false))
            return false;

        String action = intent.getAction();
        if (!Intent.ACTION_VIEW.equals(action)
                && !Intent.ACTION_INSTALL_PACKAGE.equals(action))
            return false;

        Uri data = intent.getData();
        if (data == null)
            return false;

        String scheme = data.getScheme();
        if (!"content".equalsIgnoreCase(scheme)
                && !"file".equalsIgnoreCase(scheme))
            return false;

        if (Intent.ACTION_INSTALL_PACKAGE.equals(action))
            return true;

        String type = intent.getType();
        if ("application/vnd.android.package-archive".equalsIgnoreCase(type))
            return true;

        String name = data.getLastPathSegment();
        if (name == null)
            return false;

        String lower = name.toLowerCase();
        return lower.endsWith(".apk")
                || lower.endsWith(".apks")
                || lower.endsWith(".apkm")
                || lower.endsWith(".xapk")
                || lower.endsWith(".zip");
    }

    private boolean wasRecentlyRedirected(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        String key = String.valueOf(intent == null ? null : intent.getAction())
                + "|"
                + String.valueOf(data);

        long now = SystemClock.elapsedRealtime();
        return key.equals(sLastRedirectKey)
                && now - sLastRedirectRealtime >= 0L
                && now - sLastRedirectRealtime < REDIRECT_DEBOUNCE_MS;
    }

    private void markRedirect(Intent intent) {
        sLastRedirectKey = String.valueOf(intent.getAction())
                + "|"
                + String.valueOf(intent.getData());
        sLastRedirectRealtime = SystemClock.elapsedRealtime();
    }

    private HookConfig getSystemConfig(ClassLoader classLoader) {
        long now = SystemClock.elapsedRealtime();
        if (now - sLastSystemConfigReadRealtime < CONFIG_CACHE_MS)
            return sCachedSystemConfig;

        Context context = getSystemContext(classLoader);
        HookConfig config = context == null ? new HookConfig() : readConfig(context);

        sCachedSystemConfig = config;
        sLastSystemConfigReadRealtime = now;
        return config;
    }

    private Context getSystemContext(ClassLoader classLoader) {
        try {
            Class<?> activityThread = XposedHelpers.findClass(
                    "android.app.ActivityThread",
                    classLoader
            );
            Object thread = XposedHelpers.callStaticMethod(
                    activityThread,
                    "currentActivityThread"
            );
            if (thread == null)
                return null;

            Object context = XposedHelpers.callMethod(thread, "getSystemContext");
            return context instanceof Context ? (Context) context : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private HookConfig readConfig(Context context) {
        HookConfig config = new HookConfig();
        Uri statusUri = Uri.parse(
                "content://" + BuildConfig.APPLICATION_ID + ".hookconfig/status"
        );

        try (Cursor cursor = context.getContentResolver().query(
                statusUri,
                new String[]{
                        "enabled",
                        "appMode",
                        "verbose",
                        "stockFallback",
                        "allowDowngrade",
                        "signatureOverride",
                        "securityTimestamp"
                },
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst())
                return config;

            config.enabled = readBool(cursor, "enabled", false);
            config.appMode = readString(cursor, "appMode", VsiAppMode.NORMAL.value());
            config.verbose = readBool(cursor, "verbose", false);
            config.stockFallback = readBool(cursor, "stockFallback", true);
            config.allowDowngrade = readBool(cursor, "allowDowngrade", false);
            config.signatureOverride = readBool(cursor, "signatureOverride", false);
            config.securityTimestamp = readLong(cursor, "securityTimestamp", 0L);
            return config;
        } catch (Throwable t) {
            return config;
        }
    }

    private boolean isRootXposed(HookConfig config) {
        return VsiAppMode.ROOT_XPOSED.value().equals(config.appMode);
    }

    private boolean isSignaturePolicyEnabled(HookConfig config) {
        if (!isRootXposed(config) || !config.signatureOverride)
            return false;

        if (config.securityTimestamp <= 0L)
            return false;

        long elapsed = System.currentTimeMillis() - config.securityTimestamp;
        return elapsed >= 0L && elapsed < SIGNATURE_TIMEOUT_MS;
    }

    private boolean isSignaturePolicyActiveForInstall(HookConfig config) {
        if (!isSignaturePolicyEnabled(config)) {
            ACTIVE_INSTALL_SESSIONS.clear();
            return false;
        }

        long now = SystemClock.elapsedRealtime();
        Iterator<Map.Entry<Integer, Long>> iterator =
                ACTIVE_INSTALL_SESSIONS.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, Long> entry = iterator.next();
            if (now - entry.getValue() > SESSION_STALE_MS)
                ACTIVE_INSTALL_SESSIONS.remove(entry.getKey(), entry.getValue());
        }

        if (!ACTIVE_INSTALL_SESSIONS.isEmpty())
            return true;

        long elapsed = now - sLastInstallSessionRealtime;
        return elapsed >= 0L && elapsed < ACTIVE_INSTALL_WINDOW_MS;
    }

    private boolean readBool(Cursor cursor, String column, boolean fallback) {
        int index = cursor.getColumnIndex(column);
        return index < 0 ? fallback : cursor.getInt(index) == 1;
    }

    private long readLong(Cursor cursor, String column, long fallback) {
        int index = cursor.getColumnIndex(column);
        return index < 0 ? fallback : cursor.getLong(index);
    }

    private String readString(Cursor cursor, String column, String fallback) {
        int index = cursor.getColumnIndex(column);
        if (index < 0)
            return fallback;
        String value = cursor.getString(index);
        return value == null ? fallback : value;
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
        String appMode = VsiAppMode.NORMAL.value();
        boolean verbose;
        boolean stockFallback = true;
        boolean allowDowngrade;
        boolean signatureOverride;
        long securityTimestamp;
    }
}
