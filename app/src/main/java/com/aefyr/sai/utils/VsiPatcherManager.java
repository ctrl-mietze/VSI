package com.aefyr.sai.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.aefyr.sai.BuildConfig;
import com.aefyr.sai.shell.Shell;
import com.aefyr.sai.shell.SuShell;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class VsiPatcherManager {

    private static final String PREF_PATCH_VSI_COMPONENTS = "vsi_patch_vsi_components";
    private static final String PATCH_LITE_DIR = "/data/adb/modules/vsi-patch-lite";
    private static final String PATCHER_PRO_DIR = "/data/adb/modules/vsi-patcher-pro";

    private VsiPatcherManager() {}

    public static Result installPatchLite(Context context) {
        SuShell shell = SuShell.getInstance();
        if (!shell.requestRoot())
            return Result.fail("Root access unavailable.");

        String moduleProp =
                "id=vsi-patch-lite\n" +
                "name=VSI Patch Lite\n" +
                "version=1.0\n" +
                "versionCode=1\n" +
                "author=Veyra\n" +
                "description=Temporary/systemless VSI installer routing for KernelSU/Magisk sessions.\n";

        String service = buildRoutingScript();

        String script =
                "set -e\n" +
                "mkdir -p " + PATCH_LITE_DIR + "\n" +
                "printf %s " + shell.makeLiteral(moduleProp) + " > " + PATCH_LITE_DIR + "/module.prop\n" +
                "printf %s " + shell.makeLiteral(service) + " > " + PATCH_LITE_DIR + "/service.sh\n" +
                "chmod 0755 " + PATCH_LITE_DIR + "/service.sh\n" +
                "chmod 0644 " + PATCH_LITE_DIR + "/module.prop\n" +
                PATCH_LITE_DIR + "/service.sh\n";

        Shell.Result result = sh(shell, script);
        return new Result(result.isSuccessful(), result.toString());
    }

    public static Result removePatchLite() {
        SuShell shell = SuShell.getInstance();
        if (!shell.requestRoot())
            return Result.fail("Root access unavailable.");

        Shell.Result result = sh(shell, "rm -rf " + PATCH_LITE_DIR);
        return new Result(result.isSuccessful(), result.toString());
    }

    public static Result applyPatchVsiUser0(Context context) {
        SuShell shell = SuShell.getInstance();
        if (!shell.requestRoot())
            return Result.fail("Root access unavailable.");

        List<ComponentName> components =
                VsiSystemInstallerResolver.findInstallerActivities(context);

        if (components.isEmpty())
            return Result.fail("No stock package-installer entry Activity was detected.");

        ArrayList<String> disabled = new ArrayList<>();
        StringBuilder log = new StringBuilder();

        Shell.Result enableVsi = shell.exec(new Shell.Command(
                "pm", "enable", "--user", "0",
                BuildConfig.APPLICATION_ID + "/com.aefyr.sai.ui.activities.ApkActionViewProxyActivity"
        ));
        log.append(enableVsi).append("\n\n");

        for (ComponentName component : components) {
            String flat = component.flattenToString();
            Shell.Result disable = shell.exec(new Shell.Command(
                    "pm", "disable-user", "--user", "0", flat
            ));
            log.append(disable).append("\n\n");
            if (disable.isSuccessful())
                disabled.add(flat);
        }

        if (disabled.isEmpty())
            return new Result(false, log.toString());

        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_PATCH_VSI_COMPONENTS, join(disabled))
                .apply();

        return new Result(true, log.toString());
    }

    public static Result restorePatchVsiUser0(Context context) {
        SuShell shell = SuShell.getInstance();
        if (!shell.requestRoot())
            return Result.fail("Root access unavailable.");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String raw = prefs.getString(PREF_PATCH_VSI_COMPONENTS, "");

        if (raw == null || raw.trim().isEmpty())
            return Result.fail("No stored Patch VSI component list was found.");

        StringBuilder log = new StringBuilder();
        boolean ok = true;

        for (String component : raw.split("\\n")) {
            if (component.trim().isEmpty())
                continue;

            Shell.Result enable = shell.exec(new Shell.Command(
                    "pm", "enable", "--user", "0", component.trim()
            ));
            log.append(enable).append("\n\n");
            ok &= enable.isSuccessful();
        }

        if (ok)
            prefs.edit().remove(PREF_PATCH_VSI_COMPONENTS).apply();

        return new Result(ok, log.toString());
    }

    public static Result installPatcherPro(Context context) {
        VsiDeveloperOptions dev = VsiDeveloperOptions.getInstance(context);
        if (!dev.permanentKernelRootFunctions())
            return Result.fail("Permanent kernel root functions are disabled.");

        SuShell shell = SuShell.getInstance();
        if (!shell.requestRoot())
            return Result.fail("Root access unavailable.");

        String sourceApk = context.getApplicationInfo().sourceDir;
        String moduleProp =
                "id=vsi-patcher-pro\n" +
                "name=VSI Patcher Pro\n" +
                "version=1.0\n" +
                "versionCode=1\n" +
                "author=Veyra\n" +
                "description=Systemless privileged VSI installer overlay. Remove module to restore stock state.\n";

        String permissions =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<permissions>\n" +
                "  <privapp-permissions package=\"" + BuildConfig.APPLICATION_ID + "\">\n" +
                "    <permission name=\"android.permission.INSTALL_PACKAGES\" />\n" +
                "    <permission name=\"android.permission.DELETE_PACKAGES\" />\n" +
                "    <permission name=\"android.permission.INTERACT_ACROSS_USERS_FULL\" />\n" +
                "  </privapp-permissions>\n" +
                "</permissions>\n";

        String service = buildRoutingScript();

        String script =
                "set -e\n" +
                "rm -rf " + PATCHER_PRO_DIR + "\n" +
                "mkdir -p " + PATCHER_PRO_DIR + "/system/priv-app/VSI\n" +
                "mkdir -p " + PATCHER_PRO_DIR + "/system/etc/permissions\n" +
                "cp " + shell.makeLiteral(sourceApk) + " " +
                        PATCHER_PRO_DIR + "/system/priv-app/VSI/VSI.apk\n" +
                "printf %s " + shell.makeLiteral(moduleProp) + " > " +
                        PATCHER_PRO_DIR + "/module.prop\n" +
                "printf %s " + shell.makeLiteral(permissions) + " > " +
                        PATCHER_PRO_DIR + "/system/etc/permissions/privapp-permissions-vsi.xml\n" +
                "printf %s " + shell.makeLiteral(service) + " > " +
                        PATCHER_PRO_DIR + "/service.sh\n" +
                "chmod 0755 " + PATCHER_PRO_DIR + "/service.sh\n" +
                "find " + PATCHER_PRO_DIR + "/system -type d -exec chmod 0755 {} \\;\n" +
                "find " + PATCHER_PRO_DIR + "/system -type f -exec chmod 0644 {} \\;\n";

        Shell.Result result = sh(shell, script);
        return new Result(result.isSuccessful(), result.toString());
    }

    public static Result removePatcherPro() {
        SuShell shell = SuShell.getInstance();
        if (!shell.requestRoot())
            return Result.fail("Root access unavailable.");

        Shell.Result result = sh(
                shell,
                "if [ -d " + PATCHER_PRO_DIR + " ]; then touch " +
                        PATCHER_PRO_DIR + "/remove; fi"
        );
        return new Result(result.isSuccessful(), result.toString());
    }

    private static String buildRoutingScript() {
        StringBuilder script = new StringBuilder();
        script.append("#!/system/bin/sh\n");
        script.append("until [ \"$(getprop sys.boot_completed)\" = \"1\" ]; do sleep 2; done\n");
        script.append("pm enable --user 0 ")
                .append(BuildConfig.APPLICATION_ID)
                .append("/com.aefyr.sai.ui.activities.ApkActionViewProxyActivity >/dev/null 2>&1\n");

        for (String pkg : VsiSystemInstallerResolver.commonInstallerPackages()) {
            script.append("cmd package clear-package-preferred-activities ")
                    .append(pkg)
                    .append(" >/dev/null 2>&1\n");
        }

        script.append("cmd package clear-package-preferred-activities ")
                .append(BuildConfig.APPLICATION_ID)
                .append(" >/dev/null 2>&1\n");

        return script.toString();
    }

    private static Shell.Result sh(SuShell shell, String script) {
        return shell.exec(new Shell.Command(
                "sh",
                "-c",
                shell.makeLiteral(script)
        ));
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0)
                out.append('\n');
            out.append(value);
        }
        return out.toString();
    }

    public static final class Result {
        public final boolean success;
        public final String details;

        Result(boolean success, String details) {
            this.success = success;
            this.details = details;
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }
}
