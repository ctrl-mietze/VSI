package com.aefyr.sai.xposed;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;

import com.aefyr.sai.BuildConfig;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class VsiPmsSecurityHook {

    private static final String TAG = "VSI PMS Security";
    private static final long ACTIVE_INSTALL_WINDOW_MS = 60_000L;
    private static final long SESSION_MAX_AGE_MS = 5 * 60_000L;
    private static final long CONFIG_CACHE_MS = 750L;

    private static final Map<Integer, Long> ACTIVE_SESSIONS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> CREATE_SESSION_OWNED_BY_VSI = new ThreadLocal<>();

    private static volatile boolean sHooked;
    private static volatile long sLastInstallRealtime;
    private static volatile long sLastConfigReadRealtime;
    private static volatile long sDowngradeUntil;
    private static volatile long sSignatureUntil;

    private VsiPmsSecurityHook() {}

    public static synchronized void hook(ClassLoader classLoader) {
        if (sHooked)
            return;
        sHooked = true;

        hookPackageInstallerService(classLoader);
        hookDowngradeChecks(classLoader);
        hookSignatureChecks(classLoader);
        hookSigningDetails(classLoader);

        XposedBridge.log(TAG + ": compatibility hooks initialized");
    }

    private static void hookPackageInstallerService(ClassLoader classLoader) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(
                    "com.android.server.pm.PackageInstallerService",
                    classLoader
            );
            if (clazz == null)
                return;

            XposedBridge.hookAllMethods(clazz, "createSession", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    refreshConfig();

                    boolean ownedByVsi = isVsiCreateSession(param.args);
                    CREATE_SESSION_OWNED_BY_VSI.set(ownedByVsi);

                    if (!ownedByVsi)
                        return;

                    sLastInstallRealtime = SystemClock.elapsedRealtime();

                    if (!downgradeActive())
                        return;

                    Object params = param.args.length > 0 ? param.args[0] : null;
                    if (params == null)
                        return;

                    try {
                        java.lang.reflect.Field field =
                                params.getClass().getDeclaredField("installFlags");
                        field.setAccessible(true);
                        field.setInt(params, field.getInt(params) | 0x00000082);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": installFlags injection skipped: " + t);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    refreshConfig();

                    boolean ownedByVsi =
                            Boolean.TRUE.equals(CREATE_SESSION_OWNED_BY_VSI.get());
                    CREATE_SESSION_OWNED_BY_VSI.remove();

                    if (!ownedByVsi)
                        return;

                    Object result = param.getResult();
                    if (!(result instanceof Integer)
                            || (!signatureActive() && !downgradeActive())) {
                        return;
                    }

                    int sessionId = (Integer) result;
                    if (sessionId > 0) {
                        ACTIVE_SESSIONS.put(sessionId, SystemClock.elapsedRealtime());
                        sLastInstallRealtime = SystemClock.elapsedRealtime();
                    }
                }
            });

            XC_MethodHook cleanup = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length == 0 || !(param.args[0] instanceof Integer))
                        return;
                    ACTIVE_SESSIONS.remove((Integer) param.args[0]);
                }
            };

            XposedBridge.hookAllMethods(clazz, "abandonSession", cleanup);
            XposedBridge.hookAllMethods(clazz, "cleanupSession", cleanup);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": PackageInstallerService hook failed: " + t);
        }
    }

    private static void hookDowngradeChecks(ClassLoader classLoader) {
        String[] classes = {
                "com.android.server.pm.InstallPackageHelper",
                "com.android.server.pm.PackageManagerServiceUtils",
                "com.android.server.pm.PackageManagerService"
        };

        String[] methods = {
                "checkDowngrade",
                "isDowngradePermitted"
        };

        for (String className : classes) {
            try {
                Class<?> clazz = XposedHelpers.findClassIfExists(className, classLoader);
                if (clazz == null)
                    continue;

                for (String methodName : methods) {
                    XposedBridge.hookAllMethods(clazz, methodName, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            refreshConfig();
                            sLastInstallRealtime = SystemClock.elapsedRealtime();

                            if (!downgradeActiveForInstall())
                                return;

                            if (param.method instanceof Method) {
                                Class<?> returnType = ((Method) param.method).getReturnType();
                                if (returnType == boolean.class || returnType == Boolean.class) {
                                    param.setResult(true);
                                } else if (returnType == void.class || returnType == Void.class) {
                                    param.setResult(null);
                                }
                            }
                        }
                    });
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": downgrade hook skipped for " + className + ": " + t);
            }
        }
    }

    private static void hookSignatureChecks(ClassLoader classLoader) {
        String[] classes = {
                "com.android.server.pm.ComputerEngine",
                "com.android.server.pm.Computer"
        };

        for (String className : classes) {
            try {
                Class<?> clazz = XposedHelpers.findClassIfExists(className, classLoader);
                if (clazz == null)
                    continue;

                XposedBridge.hookAllMethods(clazz, "checkSignatures", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        refreshConfig();
                        if (signatureActiveForInstall())
                            param.setResult(0);
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": signature hook skipped for " + className + ": " + t);
            }
        }
    }

    private static void hookSigningDetails(ClassLoader classLoader) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(
                    "android.content.pm.SigningDetails",
                    classLoader
            );
            if (clazz == null)
                return;

            XC_MethodHook allowDuringInstall = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    refreshConfig();
                    if (signatureActiveForInstall())
                        param.setResult(true);
                }
            };

            XposedBridge.hookAllMethods(clazz, "checkCapability", allowDuringInstall);
            XposedBridge.hookAllMethods(clazz, "hasAncestorOrSelf", allowDuringInstall);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": SigningDetails hook failed: " + t);
        }
    }

    private static boolean isVsiCreateSession(Object[] args) {
        if (args == null)
            return false;

        for (Object arg : args) {
            if (arg instanceof String
                    && BuildConfig.APPLICATION_ID.equals(arg)) {
                return true;
            }
        }

        return false;
    }

    private static boolean signatureActiveForInstall() {
        if (!signatureActive())
            return false;
        return hasRecentVsiInstall();
    }

    private static boolean downgradeActiveForInstall() {
        if (!downgradeActive())
            return false;
        return hasRecentVsiInstall();
    }

    private static boolean hasRecentVsiInstall() {
        long now = SystemClock.elapsedRealtime();

        for (Map.Entry<Integer, Long> entry : ACTIVE_SESSIONS.entrySet()) {
            if (now - entry.getValue() > SESSION_MAX_AGE_MS)
                ACTIVE_SESSIONS.remove(entry.getKey());
        }

        return !ACTIVE_SESSIONS.isEmpty()
                || (now - sLastInstallRealtime >= 0
                && now - sLastInstallRealtime < ACTIVE_INSTALL_WINDOW_MS);
    }

    private static boolean downgradeActive() {
        return System.currentTimeMillis() < sDowngradeUntil;
    }

    private static boolean signatureActive() {
        return System.currentTimeMillis() < sSignatureUntil;
    }

    private static void refreshConfig() {
        long now = SystemClock.elapsedRealtime();
        if (now - sLastConfigReadRealtime < CONFIG_CACHE_MS)
            return;
        sLastConfigReadRealtime = now;

        Context context = systemContext();
        if (context == null)
            return;

        Uri uri = Uri.parse(
                "content://" + BuildConfig.APPLICATION_ID + ".hookconfig/status"
        );

        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{"downgradeUntil", "signatureUntil"},
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst())
                return;

            int downgradeIndex = cursor.getColumnIndex("downgradeUntil");
            int signatureIndex = cursor.getColumnIndex("signatureUntil");

            if (downgradeIndex >= 0)
                sDowngradeUntil = cursor.getLong(downgradeIndex);
            if (signatureIndex >= 0)
                sSignatureUntil = cursor.getLong(signatureIndex);
        } catch (Throwable ignored) {
            // Provider may be unavailable during early boot. Keep the hooks fail-closed.
        }
    }

    private static Context systemContext() {
        try {
            Class<?> activityThread = XposedHelpers.findClass(
                    "android.app.ActivityThread",
                    null
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
}
