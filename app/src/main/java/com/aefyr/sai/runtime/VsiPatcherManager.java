package com.aefyr.sai.runtime;

import android.content.Context;

import androidx.preference.PreferenceManager;

import com.aefyr.sai.BuildConfig;
import com.aefyr.sai.shell.Shell;
import com.aefyr.sai.shell.SuShell;
import com.aefyr.sai.utils.VsiDeveloperKeys;

public final class VsiPatcherManager {

    private static final String PROXY_COMPONENT =
            BuildConfig.APPLICATION_ID + "/com.aefyr.sai.ui.activities.ApkActionViewProxyActivity";

    private static final String PROFILE0_STATE =
            "/data/local/tmp/vsi-profile0-stock-component";

    private VsiPatcherManager() {}

    public static Result installPatchLite(Context context) {
        SuShell shell = SuShell.getInstance();
        if (!shell.requestRoot())
            return Result.fail("Root access unavailable.");

        String systemService = BuildConfig.APPLICATION_ID
                + "/com.aefyr.sai.runtime.VsiRuntimeServices\\$SystemService";

        String script =
                "MOD=/data/adb/modules/vsi_patch_lite\n"
                + "rm -rf \"$MOD\"\n"
                + "mkdir -p \"$MOD\"\n"
                + "cat > \"$MOD/module.prop\" <<'EOF'\n"
                + "id=vsi_patch_lite\n"
                + "name=VSI Patch Lite\n"
                + "version=0.2.0\n"
                + "versionCode=2\n"
                + "author=Veyra\n"
                + "description=Boot/session routing helper for VSI. No system partition files are replaced.\n"
                + "EOF\n"
                + "cat > \"$MOD/service.sh\" <<'EOF'\n"
                + "#!/system/bin/sh\n"
                + "until [ \"$(getprop sys.boot_completed)\" = \"1\" ]; do sleep 2; done\n"
                + "STOCK=$(cmd package resolve-activity --brief --user 0 -a android.intent.action.VIEW "
                + "-d file:///data/local/tmp/vsi-placeholder.apk "
                + "-t application/vnd.android.package-archive 2>/dev/null | tail -n 1)\n"
                + "PKG=$" + "{STOCK%%/*}\n"
                + "case \"$PKG\" in ''|" + BuildConfig.APPLICATION_ID + ") ;; "
                + "*) cmd package clear-package-preferred-activities \"$PKG\" >/dev/null 2>&1 ;; esac\n"
                + "pm enable --user 0 " + PROXY_COMPONENT + " >/dev/null 2>&1\n"
                + "cmd package clear-package-preferred-activities " + BuildConfig.APPLICATION_ID + " >/dev/null 2>&1\n"
                + "am start-foreground-service -n " + systemService + " --es mode root_xposed >/dev/null 2>&1\n"
                + "EOF\n"
                + "chmod 0755 \"$MOD/service.sh\"\n"
                + "chmod 0644 \"$MOD/module.prop\"\n"
                + "\"$MOD/service.sh\" >/dev/null 2>&1 &";

        Shell.Result result = execScript(shell, script);
        return new Result(result.isSuccessful(), result.toString(), false);
    }

    public static Result removePatchLite() {
        return execRoot("rm -rf /data/adb/modules/vsi_patch_lite");
    }

    public static Result patchProfile0(Context context) {
        SuShell shell = SuShell.getInstance();
        if (!shell.requestRoot())
            return Result.fail("Root access unavailable.");

        String script =
                "STOCK=$(cmd package resolve-activity --brief --user 0 "
                + "-a android.intent.action.VIEW "
                + "-d file:///data/local/tmp/vsi-placeholder.apk "
                + "-t application/vnd.android.package-archive 2>/dev/null | tail -n 1)\n"
                + "case \"$STOCK\" in\n"
                + "  " + BuildConfig.APPLICATION_ID + "/*|'') exit 21 ;;\n"
                + "  */*) ;;\n"
                + "  *) exit 22 ;;\n"
                + "esac\n"
                + "printf '%s' \"$STOCK\" > " + PROFILE0_STATE + "\n"
                + "pm enable --user 0 " + PROXY_COMPONENT + "\n"
                + "pm disable-user --user 0 \"$STOCK\"\n"
                + "cmd package clear-package-preferred-activities " + BuildConfig.APPLICATION_ID + " >/dev/null 2>&1\n"
                + "exit 0";

        Shell.Result result = execScript(shell, script);
        return new Result(
                result.isSuccessful(),
                result.isSuccessful()
                        ? result.toString()
                        : result.toString() + "\nProfile-0 patch was not applied.",
                true
        );
    }

    public static Result restoreProfile0() {
        SuShell shell = SuShell.getInstance();
        if (!shell.requestRoot())
            return Result.fail("Root access unavailable.");

        String script =
                "if [ -f " + PROFILE0_STATE + " ]; then\n"
                + "  STOCK=$(cat " + PROFILE0_STATE + ")\n"
                + "  [ -n \"$STOCK\" ] && pm default-state --user 0 \"$STOCK\"\n"
                + "  rm -f " + PROFILE0_STATE + "\n"
                + "fi\n"
                + "pm enable --user 0 " + PROXY_COMPONENT + " >/dev/null 2>&1\n"
                + "exit 0";

        Shell.Result result = execScript(shell, script);
        return new Result(result.isSuccessful(), result.toString(), false);
    }

    public static Result installPatcherPro(Context context) {
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                VsiDeveloperKeys.ACTIVATE_PERMANENT_KERNEL_ROOT_FUNCTIONS,
                false
        )) {
            return Result.fail("Permanent kernel-root functions are locked in Developer Options.");
        }

        SuShell shell = SuShell.getInstance();
        if (!shell.requestRoot())
            return Result.fail("Root access unavailable.");

        Shell.Result persistentCheck = execScript(
                shell,
                "test -d /data/adb/modules && "
                        + "(test -d /data/adb/ksu || test -d /data/adb/magisk "
                        + "|| command -v magisk >/dev/null 2>&1 "
                        + "|| command -v ksud >/dev/null 2>&1)"
        );
        if (!persistentCheck.isSuccessful()) {
            return Result.fail(
                    "No persistent KernelSU/Magisk module environment detected. "
                            + "Patcher Pro is blocked for temporary-only root."
            );
        }

        String apk = shell.makeLiteral(context.getApplicationInfo().sourceDir);
        String systemService = BuildConfig.APPLICATION_ID
                + "/com.aefyr.sai.runtime.VsiRuntimeServices\\$SystemService";

        String script =
                "MOD=/data/adb/modules/vsi_patcher_pro\n"
                + "rm -rf \"$MOD\"\n"
                + "mkdir -p \"$MOD/system/priv-app/VSI\" \"$MOD/system/etc/permissions\"\n"
                + "cp " + apk + " \"$MOD/system/priv-app/VSI/VSI.apk\"\n"
                + "cat > \"$MOD/module.prop\" <<'EOF'\n"
                + "id=vsi_patcher_pro\n"
                + "name=VSI Patcher Pro\n"
                + "version=0.2.0\n"
                + "versionCode=2\n"
                + "author=Veyra\n"
                + "description=Persistent systemless priv-app mount for VSI. Requires permanent kernel root.\n"
                + "EOF\n"
                + "cat > \"$MOD/system/etc/permissions/privapp-permissions-vsi.xml\" <<'EOF'\n"
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<permissions>\n"
                + "  <privapp-permissions package=\"" + BuildConfig.APPLICATION_ID + "\">\n"
                + "    <permission name=\"android.permission.INSTALL_PACKAGES\" />\n"
                + "    <permission name=\"android.permission.DELETE_PACKAGES\" />\n"
                + "    <permission name=\"android.permission.INTERACT_ACROSS_USERS_FULL\" />\n"
                + "  </privapp-permissions>\n"
                + "</permissions>\n"
                + "EOF\n"
                + "cat > \"$MOD/service.sh\" <<'EOF'\n"
                + "#!/system/bin/sh\n"
                + "until [ \"$(getprop sys.boot_completed)\" = \"1\" ]; do sleep 2; done\n"
                + "STATE=/data/local/tmp/vsi-patcher-pro-stock-component\n"
                + "STOCK=$(cmd package resolve-activity --brief --user 0 "
                + "-a android.intent.action.VIEW "
                + "-d file:///data/local/tmp/vsi-placeholder.apk "
                + "-t application/vnd.android.package-archive 2>/dev/null | tail -n 1)\n"
                + "case \"$STOCK\" in\n"
                + "  " + BuildConfig.APPLICATION_ID + "/*|'') ;;\n"
                + "  */*) printf '%s' \"$STOCK\" > \"$STATE\"; pm disable-user --user 0 \"$STOCK\" >/dev/null 2>&1 ;;\n"
                + "esac\n"
                + "pm enable --user 0 " + PROXY_COMPONENT + " >/dev/null 2>&1\n"
                + "cmd package clear-package-preferred-activities " + BuildConfig.APPLICATION_ID + " >/dev/null 2>&1\n"
                + "am start-foreground-service -n " + systemService + " --es mode root_xposed >/dev/null 2>&1\n"
                + "EOF\n"
                + "chmod 0755 \"$MOD/service.sh\"\n"
                + "chmod 0644 \"$MOD/module.prop\" "
                + "\"$MOD/system/priv-app/VSI/VSI.apk\" "
                + "\"$MOD/system/etc/permissions/privapp-permissions-vsi.xml\"\n"
                + "exit 0";

        Shell.Result result = execScript(shell, script);
        return new Result(result.isSuccessful(), result.toString(), true);
    }

    public static Result removePatcherPro() {
        SuShell shell = SuShell.getInstance();
        if (!shell.requestRoot())
            return Result.fail("Root access unavailable.");

        String script =
                "STATE=/data/local/tmp/vsi-patcher-pro-stock-component\n"
                + "if [ -f \"$STATE\" ]; then\n"
                + "  STOCK=$(cat \"$STATE\")\n"
                + "  case \"$STOCK\" in */*) pm default-state --user 0 \"$STOCK\" >/dev/null 2>&1 ;; esac\n"
                + "  rm -f \"$STATE\"\n"
                + "fi\n"
                + "rm -rf /data/adb/modules/vsi_patcher_pro\n"
                + "cmd package clear-package-preferred-activities " + BuildConfig.APPLICATION_ID + " >/dev/null 2>&1\n"
                + "exit 0";

        Shell.Result result = execScript(shell, script);
        return new Result(result.isSuccessful(), result.toString(), true);
    }

    public static Result status() {
        return execRoot(
                "echo 'Patch Lite:'; "
                        + "[ -d /data/adb/modules/vsi_patch_lite ] && echo installed || echo off; "
                        + "echo 'Profile 0:'; "
                        + "[ -f " + PROFILE0_STATE + " ] && cat " + PROFILE0_STATE + " || echo stock; "
                        + "echo 'Patcher Pro:'; "
                        + "[ -d /data/adb/modules/vsi_patcher_pro ] && echo installed || echo off; "
                        + "echo 'Patcher Pro stock component:'; "
                        + "[ -f /data/local/tmp/vsi-patcher-pro-stock-component ] "
                        + "&& cat /data/local/tmp/vsi-patcher-pro-stock-component || echo none"
        );
    }

    private static Result execRoot(String script) {
        SuShell shell = SuShell.getInstance();
        if (!shell.requestRoot())
            return Result.fail("Root access unavailable.");
        Shell.Result result = execScript(shell, script);
        return new Result(result.isSuccessful(), result.toString(), false);
    }

    private static Shell.Result execScript(SuShell shell, String script) {
        return shell.exec(new Shell.Command(
                "sh",
                "-c",
                shell.makeLiteral(script)
        ));
    }

    public static final class Result {
        public final boolean success;
        public final String details;
        public final boolean requiresReboot;

        Result(boolean success, String details, boolean requiresReboot) {
            this.success = success;
            this.details = details;
            this.requiresReboot = requiresReboot;
        }

        static Result fail(String message) {
            return new Result(false, message, false);
        }
    }
}
