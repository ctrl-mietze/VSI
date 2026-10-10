package com.aefyr.sai.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VsiCapabilityCatalog {

    private static final String[] FEATURES = new String[]{
            "APK install",
            "Split APK install",
            "APKS / APKM / XAPK containers",
            "Package preflight",
            "Update / downgrade comparison",
            "Certificate SHA-256",
            "Permission diff",
            "Session history",
            "Install queue",
            "Source identity",
            "Quick Install Sheet",
            "Package validation",
            "Container repair",
            "Package converter",
            "Split Intelligence",
            "ABI compatibility",
            "Locale split matching",
            "Density split matching",
            "Install Profiles",
            "Post-install verification",

            "Shizuku shell transport",
            "Privileged install flags",
            "Target Android user",
            "Test-only APK install",
            "Silent session creation",
            "PackageManager diagnostics",
            "Installed-package deep compare",
            "Installer source override",
            "Session command preview",
            "Advanced APK Analysis",

            "Root shell transport",
            "Temp-root support",
            "Root package routing",
            "User-0 installer routing",
            "Root session recovery",
            "Root permission audit",
            "Root install rollback marker",
            "Root source cleanup",
            "Root app-data aware warnings",
            "Root package conflict scan",
            "Root-only batch operations",
            "Root module detection",
            "KernelSU detection",
            "Magisk compatibility",
            "Systemless patch generation",
            "Patch Lite support",
            "Patch VSI user-0 routing",
            "Patcher Pro module staging",
            "Privileged app capability check",
            "Permanent-root feature gate",

            "Xposed / Vector runtime probe",
            "OEM installer Activity hook",
            "SystemServer install-session hook",
            "Time-limited downgrade policy hook",
            "Session-scoped signature policy hook",
            "RootXposed System Service",
            "Stock installer fail-open fallback",
            "Hook diagnostics",
            "Cross-OEM installer package scope",
            "Temporary system-installer takeover"
    };

    private VsiCapabilityCatalog() {}

    public static List<String> forMode(VsiAppMode mode) {
        int count = Math.min(mode.capabilityCount(), FEATURES.length);
        ArrayList<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            out.add(FEATURES[i]);
        return Collections.unmodifiableList(out);
    }
}
