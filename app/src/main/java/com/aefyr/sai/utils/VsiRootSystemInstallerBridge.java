package com.aefyr.sai.utils;

import android.content.Context;

import com.aefyr.sai.BuildConfig;
import com.aefyr.sai.shell.Shell;
import com.aefyr.sai.shell.SuShell;

public final class VsiRootSystemInstallerBridge {

    private static final String VSI_PROXY =
            BuildConfig.APPLICATION_ID + "/com.aefyr.sai.ui.activities.ApkActionViewProxyActivity";

    private VsiRootSystemInstallerBridge() {}

    public static Result setEnabled(Context context, boolean enabled) {
        SuShell shell = SuShell.getInstance();
        if (!shell.requestRoot())
            return new Result(false, "Root access unavailable");

        StringBuilder log = new StringBuilder();

        if (enabled) {
            Shell.Result enableProxy = shell.exec(new Shell.Command(
                    "pm", "enable", "--user", "current", VSI_PROXY
            ));
            append(log, enableProxy);

            String script =
                    "STOCK=$(cmd package resolve-activity --brief --user current "
                            + "-a android.intent.action.VIEW "
                            + "-d file:///data/local/tmp/vsi-placeholder.apk "
                            + "-t application/vnd.android.package-archive 2>/dev/null | tail -n 1); "
                            + "PKG=$" + "{STOCK%%/*}; "
                            + "case \"$PKG\" in "
                            + "''|" + BuildConfig.APPLICATION_ID + ") ;; "
                            + "*) cmd package clear-package-preferred-activities \"$PKG\" ;; "
                            + "esac; "
                            + "cmd package clear-package-preferred-activities "
                            + BuildConfig.APPLICATION_ID + " >/dev/null 2>&1; "
                            + "exit 0";

            Shell.Result clearResolved = shell.exec(new Shell.Command(
                    "sh",
                    "-c",
                    shell.makeLiteral(script)
            ));
            append(log, clearResolved);

            return new Result(
                    enableProxy.isSuccessful() && clearResolved.isSuccessful(),
                    log.toString()
            );
        }

        Shell.Result clearVsi = shell.exec(new Shell.Command(
                "cmd",
                "package",
                "clear-package-preferred-activities",
                BuildConfig.APPLICATION_ID
        ));
        append(log, clearVsi);

        boolean keepProxy = PreferencesHelper.getInstance(context).getPrefs()
                .getBoolean(PreferencesKeys.ENABLE_APK_ACTION_VIEW, true);

        Shell.Result proxyState = shell.exec(new Shell.Command(
                "pm",
                keepProxy ? "enable" : "disable",
                "--user",
                "current",
                VSI_PROXY
        ));
        append(log, proxyState);

        return new Result(
                clearVsi.isSuccessful() && proxyState.isSuccessful(),
                log.toString()
        );
    }

    private static void append(StringBuilder sb, Shell.Result result) {
        if (sb.length() > 0)
            sb.append("\n\n");
        sb.append(result.toString());
    }

    public static final class Result {
        public final boolean success;
        public final String details;

        public Result(boolean success, String details) {
            this.success = success;
            this.details = details;
        }
    }
}
