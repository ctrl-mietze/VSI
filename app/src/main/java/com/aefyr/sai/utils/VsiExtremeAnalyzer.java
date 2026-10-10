package com.aefyr.sai.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import com.aefyr.sai.runtime.VsiAppMode;
import com.aefyr.sai.runtime.VsiModeManager;
import com.aefyr.sai.runtime.VsiPatcherManager;
import com.aefyr.sai.shell.Shell;
import com.aefyr.sai.shell.ShizukuShell;
import com.aefyr.sai.shell.SuShell;
import com.aefyr.sai.xposed.VsiXposedRuntimeProbe;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class VsiExtremeAnalyzer {

    private VsiExtremeAnalyzer() {}

    public static String analyze(Context context, Uri uri) throws Exception {
        VsiAppMode mode = VsiModeManager.getCurrentMode(context);
        if (mode == VsiAppMode.NORMAL)
            throw new IllegalStateException("Extreme APK Analysis requires Shizuku, Xposed, Root or RootXposed mode.");

        VsiPackagePreflight.Result preflight = VsiPackagePreflight.analyze(context, uri);
        StringBuilder out = new StringBuilder();

        out.append("VSI Extreme APK Analysis\n");
        out.append("========================\n");
        out.append("Mode: ").append(mode.id()).append("\n");
        out.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        out.append("Package: ").append(preflight.packageName).append("\n");
        out.append("App: ").append(preflight.appName).append("\n");
        out.append("Version: ").append(preflight.versionName)
                .append(" (").append(preflight.versionCode).append(")\n");
        out.append("minSdk: ").append(preflight.minSdk)
                .append(" · targetSdk: ").append(preflight.targetSdk).append("\n");
        out.append("Operation: ").append(preflight.operation).append("\n");
        out.append("Installed: ").append(preflight.installed).append("\n");
        if (preflight.installed) {
            out.append("Installed version: ")
                    .append(preflight.installedVersionName)
                    .append(" (").append(preflight.installedVersionCode).append(")\n");
            out.append("Signature match: ").append(preflight.signatureMatch).append("\n");
        }

        out.append("Incoming cert SHA-256: ")
                .append(preflight.firstIncomingCertificate()).append("\n");
        out.append("Requested permissions: ")
                .append(preflight.requestedPermissions.size()).append("\n");
        out.append("New permissions: ")
                .append(preflight.addedPermissions.size()).append("\n\n");

        appendZipAnalysis(context, uri, out);

        if (mode == VsiAppMode.SHIZUKU) {
            appendShell("SHIZUKU", ShizukuShell.getInstance(), preflight.packageName, out, false);
        } else if (mode == VsiAppMode.ROOT || mode == VsiAppMode.ROOT_XPOSED) {
            appendShell("ROOT", SuShell.getInstance(), preflight.packageName, out, true);
        }

        if (mode.usesXposed()) {
            out.append("\nXposed / Vector\n---------------\n");
            out.append("Runtime probe: ").append(VsiXposedRuntimeProbe.isActive()).append("\n");
            out.append("Downgrade override active: ")
                    .append(com.aefyr.sai.runtime.VsiSecurityWindowManager.isDowngradeActive(context))
                    .append("\n");
            out.append("Signature override active: ")
                    .append(com.aefyr.sai.runtime.VsiSecurityWindowManager.isSignatureOverrideActive(context))
                    .append("\n");
        }

        if (mode == VsiAppMode.ROOT_XPOSED) {
            VsiPatcherManager.Result status = VsiPatcherManager.status();
            out.append("\nPatch state\n-----------\n").append(status.details).append("\n");
        }

        return out.toString();
    }

    private static void appendZipAnalysis(Context context, Uri uri, StringBuilder out) {
        int dex = 0;
        int libs = 0;
        int entries = 0;
        long uncompressed = 0;
        Set<String> abis = new HashSet<>();

        try (InputStream raw = context.getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {

            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory())
                    continue;

                entries++;
                if (entry.getSize() > 0)
                    uncompressed += entry.getSize();

                String name = entry.getName();
                if (name != null && name.matches("classes(\\d*)?\\.dex"))
                    dex++;

                if (name != null && name.startsWith("lib/") && name.endsWith(".so")) {
                    libs++;
                    String[] parts = name.split("/");
                    if (parts.length > 2)
                        abis.add(parts[1]);
                }
            }
        } catch (Exception e) {
            out.append("ZIP scan: unavailable (").append(e.getMessage()).append(")\n");
            return;
        }

        out.append("Archive entries: ").append(entries).append("\n");
        out.append("DEX files: ").append(dex).append("\n");
        out.append("Native libraries: ").append(libs).append("\n");
        out.append("ABIs: ").append(abis).append("\n");
        out.append("Known uncompressed bytes: ").append(uncompressed).append("\n\n");
    }

    private static void appendShell(
            String label,
            Shell shell,
            String packageName,
            StringBuilder out,
            boolean root
    ) {
        out.append(label).append(" enrichment\n----------------\n");

        if (!shell.isAvailable()) {
            out.append(label).append(" shell unavailable.\n");
            return;
        }

        Shell.Result path = shell.exec(new Shell.Command("pm", "path", packageName));
        out.append("pm path:\n").append(clip(path.out, 4000)).append("\n");

        Shell.Result dump = shell.exec(new Shell.Command("dumpsys", "package", packageName));
        out.append("dumpsys package:\n").append(clip(dump.out, 12000)).append("\n");

        if (root) {
            String script =
                    "P=$(pm path " + packageName + " 2>/dev/null | head -n1 | cut -d: -f2); "
                            + "echo APK=$P; "
                            + "[ -n \"$P\" ] && ls -lZ \"$P\"; "
                            + "ls -ldZ /data/user/0/" + packageName + " 2>/dev/null || true; "
                            + "id";
            String literal = shell.makeLiteral(script);
            Shell.Result rootAudit = shell.exec(new Shell.Command("sh", "-c", literal));
            out.append("Root filesystem / SELinux:\n")
                    .append(clip(rootAudit.out + "\n" + rootAudit.err, 8000))
                    .append("\n");
        }
    }

    private static String clip(String value, int max) {
        if (value == null)
            return "";
        if (value.length() <= max)
            return value;
        return value.substring(0, max) + "\n… truncated by VSI …";
    }
}
