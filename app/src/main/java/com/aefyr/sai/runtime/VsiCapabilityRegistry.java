package com.aefyr.sai.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VsiCapabilityRegistry {

    private static final String[] BASE = {
            "APK & split APK installation",
            "APKS / APKM / XAPK / ZIP containers",
            "VSI Quick Install preflight",
            "Update / downgrade / reinstall detection",
            "Certificate SHA-256 inspection",
            "Permission difference analysis",
            "Minimum / target SDK compatibility",
            "ABI compatibility detection",
            "Split Intelligence",
            "Strict package validation",
            "Package repair",
            "Package converter",
            "Install Queue",
            "Session History",
            "Install Profiles",
            "Source identity tracking",
            "Backup / restore integration",
            "Package size inspection",
            "Installed-app launch actions",
            "Safe source cleanup after success"
    };

    private static final String[] SHIZUKU = {
            "Privileged PackageInstaller sessions",
            "Silent-capable Shizuku install path",
            "Android user/profile targeting",
            "Privileged downgrade flag",
            "testOnly APK installation",
            "Install-location override",
            "Installer identity attribution",
            "PackageManager shell diagnostics",
            "dumpsys package analyzer enrichment",
            "Privileged uninstall / replacement"
    };

    private static final String[] XPOSED = {
            "System-installer in-memory redirect",
            "Xposed / Vector runtime probe",
            "Stock-installer fail-open fallback",
            "Verbose hook diagnostics",
            "3-minute App Downgrade override",
            "3-minute Signature Verification override",
            "Active-install-session security scoping",
            "Framework PackageManager hook diagnostics",
            "OEM Package Installer compatibility hook",
            "Automatic security-window expiry"
    };

    private static final String[] ROOT = {
            "Root PackageInstaller backend",
            "Root-assisted silent install",
            "Explicit profile 0 install",
            "Cross-user package installation",
            "Advanced pm install-create flags",
            "Direct package uninstall",
            "Confirmed signature replacement workflow",
            "Preferred-installer routing reset",
            "Root System Installer bridge",
            "VSI System Service",
            "Persistent privileged logging",
            "SELinux context inspection",
            "App data-directory audit",
            "Installed APK path inspection",
            "OAT / ODEX state inspection",
            "Granted-permission snapshot",
            "AppOps snapshot",
            "UID / GID inspection",
            "System / priv-app classification",
            "Root shell diagnostics",
            "Installer-source attribution",
            "Stock-component state restore",
            "Patch Lite",
            "Patch VSI profile-0 routing",
            "Kernel module detection",
            "Temporary-root detection",
            "KernelSU / Magisk detection",
            "Systemless module builder",
            "Installer-component backup",
            "Root-assisted package repair"
    };

    private static final String[] ROOT_XPOSED = {
            "Root + Xposed combined system-installer mode",
            "Signature override with root guard",
            "Downgrade hook + root flag combination",
            "system_server security policy hooks",
            "OEM installer hook fallback chain",
            "Root/Xposed service health monitor",
            "Temp-root automatic stock reversion",
            "Patcher Pro gate",
            "Maximum-tier APK Analyzer",
            "VSI installer-session ownership enforcement"
    };

    private VsiCapabilityRegistry() {}

    public static List<String> forMode(VsiAppMode mode) {
        ArrayList<String> result = new ArrayList<>();
        add(result, BASE);

        switch (mode) {
            case SHIZUKU:
                add(result, SHIZUKU);
                break;
            case XPOSED:
                add(result, XPOSED);
                break;
            case ROOT:
                add(result, ROOT);
                break;
            case ROOT_XPOSED:
                add(result, ROOT);
                add(result, ROOT_XPOSED);
                break;
            case NORMAL:
            default:
                break;
        }

        return Collections.unmodifiableList(result);
    }

    private static void add(List<String> target, String[] values) {
        Collections.addAll(target, values);
    }
}
