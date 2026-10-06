package com.aefyr.sai.utils;

import android.content.Context;

import com.aefyr.sai.BuildConfig;
import com.aefyr.sai.shell.Shell;
import com.aefyr.sai.shell.SuShell;

public final class VsiRootSystemInstallerBridge {

    private static final String STOCK_INSTALLER = "com.google.android.packageinstaller";
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

            Shell.Result clearStock = shell.exec(new Shell.Command(
                    "cmd", "package", "clear-package-preferred-activities", STOCK_INSTALLER
            ));
            append(log, clearStock);

            // We deliberately do NOT disable or replace the stock package. Clearing its
            // preferred mapping lets Android offer VSI as the APK handler while keeping
            // the system confirmation path intact and reversible.
            return new Result(enableProxy.isSuccessful() && clearStock.isSuccessful(), log.toString());
        }

        Shell.Result clearVsi = shell.exec(new Shell.Command(
                "cmd", "package", "clear-package-preferred-activities", BuildConfig.APPLICATION_ID
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

        return new Result(clearVsi.isSuccessful() && proxyState.isSuccessful(), log.toString());
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
