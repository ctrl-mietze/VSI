package com.aefyr.sai.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;

import com.aefyr.sai.BuildConfig;
import com.aefyr.sai.shell.Shell;
import com.aefyr.sai.shell.ShizukuShell;
import com.aefyr.sai.shell.SuShell;
import com.aefyr.sai.xposed.VsiXposedRuntimeProbe;

import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

public final class VsiInstallerDoctor {

    private VsiInstallerDoctor() {}

    public static Report run(Context context) {
        Report report = new Report();

        report.add("Device", Build.MANUFACTURER + " " + Build.MODEL, true);
        report.add("Android", Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT, true);
        report.add("VSI package", BuildConfig.APPLICATION_ID, true);
        report.add("VSI mode", VsiModeManager.getMode(context).name(), true);

        boolean root = SuShell.getInstance().isAvailable();
        report.add("Root shell", root ? "Available" : "Unavailable", root);

        boolean shizukuBinder = Shizuku.pingBinder();
        boolean shizukuPermission = false;
        if (shizukuBinder) {
            try {
                shizukuPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                        || Shizuku.isPreV11()
                        || Shizuku.getVersion() < 11
                        || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            } catch (Throwable ignored) {}
        }
        report.add(
                "Shizuku",
                !shizukuBinder
                        ? "Binder unavailable"
                        : shizukuPermission
                        ? "Connected + permission granted"
                        : "Connected, permission missing",
                shizukuBinder && shizukuPermission
        );

        boolean xposed = VsiXposedRuntimeProbe.isActive();
        report.add(
                "Xposed / Vector runtime",
                xposed ? "VSI runtime probe active" : "Runtime probe inactive",
                xposed
        );

        List<ComponentName> installerActivities =
                VsiSystemInstallerResolver.findInstallerActivities(context);
        StringBuilder installers = new StringBuilder();
        for (ComponentName component : installerActivities) {
            if (installers.length() > 0)
                installers.append("\n");
            installers.append(component.flattenToShortString());
        }
        report.add(
                "Stock installer entries",
                installerActivities.isEmpty() ? "No matching installer Activity found" : installers.toString(),
                !installerActivities.isEmpty()
        );

        boolean canInstallUnknown = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                canInstallUnknown = context.getPackageManager().canRequestPackageInstalls();
            } catch (Throwable ignored) {
                canInstallUnknown = false;
            }
        }
        report.add(
                "Install unknown apps",
                canInstallUnknown ? "Allowed" : "Not allowed for VSI",
                canInstallUnknown
        );

        boolean notifications = NotificationManagerCompat.from(context).areNotificationsEnabled();
        report.add(
                "Notifications",
                notifications ? "Allowed" : "Blocked",
                notifications
        );

        boolean batteryExempt = VsiBatteryOptimizationHelper.isExempt(context);
        report.add(
                "Battery optimization",
                batteryExempt ? "VSI exempt" : "Optimization still active",
                batteryExempt
        );

        int rollbackMode = PreferencesHelper.getInstance(context).getRollbackMode();
        report.add(
                "Rollback policy",
                rollbackMode < 0 ? "Disabled" : "Enabled, data policy " + rollbackMode,
                true
        );

        Shell privileged = null;
        VsiAppMode mode = VsiModeManager.getMode(context);
        if (mode.hasRoot() && root)
            privileged = SuShell.getInstance();
        else if (mode == VsiAppMode.SHIZUKU && shizukuBinder && shizukuPermission)
            privileged = ShizukuShell.getInstance();
        else if (root)
            privileged = SuShell.getInstance();
        else if (shizukuBinder && shizukuPermission)
            privileged = ShizukuShell.getInstance();

        if (privileged != null && privileged.isAvailable()) {
            Shell.Result help = privileged.exec(new Shell.Command("cmd", "package", "help"));
            String helpText = String.valueOf(help.out) + "\n" + String.valueOf(help.err);
            boolean rollbackSupported = helpText.contains("--enable-rollback");
            report.add(
                    "Android rollback CLI",
                    rollbackSupported
                            ? "Supported by this PackageManager"
                            : "Not reported by cmd package help",
                    rollbackSupported
            );

            if (root) {
                Shell.Result modules = SuShell.getInstance().exec(
                        new Shell.Command("ls", "-1", "/data/adb/modules")
                );
                report.add(
                        "Root modules",
                        modules.isSuccessful() && modules.out != null && !modules.out.trim().isEmpty()
                                ? modules.out.trim()
                                : "No readable /data/adb/modules entries",
                        modules.isSuccessful()
                );
            }
        } else {
            report.add(
                    "Android rollback CLI",
                    "Privileged shell unavailable; capability not probed",
                    false
            );
        }

        String installerSource = null;
        try {
            installerSource = context.getPackageManager()
                    .getInstallerPackageName(BuildConfig.APPLICATION_ID);
        } catch (Throwable ignored) {}
        report.add(
                "VSI installer source",
                installerSource == null ? "Unknown / sideloaded" : installerSource,
                true
        );

        report.add(
                "App language",
                VsiLocaleHelper.resolveLocale(context).toLanguageTag(),
                true
        );

        return report;
    }

    public static final class Report {
        private final List<Item> items = new ArrayList<>();

        void add(String name, String value, boolean ok) {
            items.add(new Item(name, value, ok));
        }

        public List<Item> items() {
            return items;
        }

        public int problemCount() {
            int count = 0;
            for (Item item : items)
                if (!item.ok)
                    count++;
            return count;
        }

        public String toText() {
            StringBuilder out = new StringBuilder();
            out.append("VSI Installer Doctor\n");
            out.append("====================\n\n");

            for (Item item : items) {
                out.append(item.ok ? "[OK] " : "[!] ")
                        .append(item.name)
                        .append("\n")
                        .append(item.value)
                        .append("\n\n");
            }

            out.append("Problems / unavailable capabilities: ")
                    .append(problemCount())
                    .append("\n");
            return out.toString();
        }
    }

    public static final class Item {
        public final String name;
        public final String value;
        public final boolean ok;

        Item(String name, String value, boolean ok) {
            this.name = name;
            this.value = value;
            this.ok = ok;
        }
    }
}
