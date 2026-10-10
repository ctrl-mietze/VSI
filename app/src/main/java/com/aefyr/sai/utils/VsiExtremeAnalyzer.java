package com.aefyr.sai.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import com.aefyr.sai.runtime.VsiAppMode;
import com.aefyr.sai.runtime.VsiModeManager;
import com.aefyr.sai.runtime.VsiPatcherManager;
import com.aefyr.sai.runtime.VsiSecurityWindowManager;
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
        if (mode == VsiAppMode.NORMAL) {
            throw new IllegalStateException(
                    "Extreme APK Analysis requires Shizuku, Xposed, Root or RootXposed mode."
            );
        }

        VsiPackagePreflight.Result preflight = VsiPackagePreflight.analyze(context, uri);
        StringBuilder out = new StringBuilder();

        out.append("VSI Extreme APK Analysis\n");
        out.append("========================\n");
        out.append("Mode: ").append(mode.id())
                .append(" · tier functions: ").append(mode.capabilityCount()).append("\n");
        out.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        out.append("Device: ").append(Build.MANUFACTURER)
                .append(" ").append(Build.MODEL).append("\n");
        out.append("Package source: ").append(uri).append("\n\n");

        out.append("Preflight\n---------\n");
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
            out.append("Installed cert SHA-256: ")
                    .append(preflight.firstInstalledCertificate()).append("\n");
        }

        out.append("Incoming cert SHA-256: ")
                .append(preflight.firstIncomingCertificate()).append("\n");
        out.append("Requested permissions: ")
                .append(preflight.requestedPermissions.size()).append("\n");
        out.append("New permissions: ")
                .append(preflight.addedPermissions.size()).append("\n");

        for (String permission : preflight.addedPermissions)
            out.append("  + ").append(permission).append("\n");

        out.append("\n").append(VsiApkStaticInspector.inspect(context, uri)).append("\n");

        appendZipAnalysis(context, uri, out);

        if (mode == VsiAppMode.SHIZUKU) {
            appendShell(
                    "SHIZUKU",
                    ShizukuShell.getInstance(),
                    preflight.packageName,
                    out,
                    false
            );
        } else if (mode == VsiAppMode.ROOT || mode == VsiAppMode.ROOT_XPOSED) {
            appendShell(
                    "ROOT",
                    SuShell.getInstance(),
                    preflight.packageName,
                    out,
                    true
            );
        }

        if (mode.usesXposed()) {
            out.append("\nXposed / Vector\n---------------\n");
            out.append("Runtime probe: ").append(VsiXposedRuntimeProbe.isActive()).append("\n");
            out.append("Downgrade override active: ")
                    .append(VsiSecurityWindowManager.isDowngradeActive(context))
                    .append("\n");
            out.append("Downgrade override until: ")
                    .append(VsiSecurityWindowManager.downgradeUntil(context))
                    .append("\n");
            out.append("Signature override active: ")
                    .append(VsiSecurityWindowManager.isSignatureOverrideActive(context))
                    .append("\n");
            out.append("Signature override until: ")
                    .append(VsiSecurityWindowManager.signatureUntil(context))
                    .append("\n");
        }

        if (mode == VsiAppMode.ROOT_XPOSED) {
            VsiPatcherManager.Result status = VsiPatcherManager.status();
            out.append("\nPatch state\n-----------\n")
                    .append(status.details)
                    .append("\n");
        }

        return out.toString();
    }

    private static void appendZipAnalysis(Context context, Uri uri, StringBuilder out) {
        int dex = 0;
        int libs = 0;
        int entries = 0;
        int assets = 0;
        int resources = 0;
        int signatureEntries = 0;
        int stored = 0;
        int deflated = 0;
        int duplicates = 0;
        int suspiciousPaths = 0;
        boolean manifest = false;

        long uncompressed = 0;
        long compressed = 0;

        Set<String> abis = new HashSet<>();
        Set<String> names = new HashSet<>();

        try (InputStream raw = context.getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {

            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory())
                    continue;

                entries++;

                String name = entry.getName();
                if (name == null)
                    name = "?";

                if (!names.add(name))
                    duplicates++;

                if (name.startsWith("/") || name.contains("../"))
                    suspiciousPaths++;

                if ("AndroidManifest.xml".equals(name))
                    manifest = true;

                if (name.matches("classes(\\d*)?\\.dex"))
                    dex++;

                if (name.startsWith("assets/"))
                    assets++;

                if (name.startsWith("res/"))
                    resources++;

                String upper = name.toUpperCase();
                if (upper.startsWith("META-INF/")
                        && (upper.endsWith(".RSA")
                        || upper.endsWith(".DSA")
                        || upper.endsWith(".EC")
                        || upper.endsWith("MANIFEST.MF"))) {
                    signatureEntries++;
                }

                if (name.startsWith("lib/") && name.endsWith(".so")) {
                    libs++;
                    String[] parts = name.split("/");
                    if (parts.length > 2)
                        abis.add(parts[1]);
                }

                if (entry.getMethod() == ZipEntry.STORED)
                    stored++;
                else if (entry.getMethod() == ZipEntry.DEFLATED)
                    deflated++;

                if (entry.getSize() > 0)
                    uncompressed += entry.getSize();
                if (entry.getCompressedSize() > 0)
                    compressed += entry.getCompressedSize();
            }
        } catch (Exception e) {
            out.append("\nZIP / container structure\n-------------------------\n");
            out.append("ZIP scan unavailable: ").append(e.getMessage()).append("\n");
            return;
        }

        out.append("\nZIP / container structure\n-------------------------\n");
        out.append("AndroidManifest.xml: ").append(manifest).append("\n");
        out.append("Archive entries: ").append(entries).append("\n");
        out.append("Duplicate names: ").append(duplicates).append("\n");
        out.append("Suspicious traversal paths: ").append(suspiciousPaths).append("\n");
        out.append("DEX files: ").append(dex).append("\n");
        out.append("Assets entries: ").append(assets).append("\n");
        out.append("Resource entries: ").append(resources).append("\n");
        out.append("META-INF signature entries: ").append(signatureEntries).append("\n");
        out.append("Native libraries: ").append(libs).append("\n");
        out.append("ABIs: ").append(abis).append("\n");
        out.append("Stored entries: ").append(stored)
                .append(" · deflated: ").append(deflated).append("\n");
        out.append("Known uncompressed bytes: ").append(uncompressed).append("\n");
        out.append("Known compressed bytes: ").append(compressed).append("\n\n");
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

        Shell.Result appOps = shell.exec(new Shell.Command("cmd", "appops", "get", packageName));
        out.append("AppOps:\n")
                .append(clip(appOps.out + "\n" + appOps.err, 8000))
                .append("\n");

        String shellScript =
                "echo 'Installer packages:'; "
                        + "pm list packages -i | grep -F " + shell.makeLiteral(packageName)
                        + " || true; "
                        + "echo 'UID:'; "
                        + "pm list packages -U | grep -F " + shell.makeLiteral(packageName)
                        + " || true; "
                        + "echo 'PackageInstaller candidates:'; "
                        + "pm list packages | grep -Ei 'packageinstaller|installer' | head -80";

        Shell.Result shellAudit = shell.exec(new Shell.Command(
                "sh",
                "-c",
                shell.makeLiteral(shellScript)
        ));
        out.append("PackageManager routing:\n")
                .append(clip(shellAudit.out + "\n" + shellAudit.err, 8000))
                .append("\n");

        if (root) {
            String script =
                    "P=$(pm path " + shell.makeLiteral(packageName)
                            + " 2>/dev/null | head -n1 | cut -d: -f2); "
                            + "echo APK=$P; "
                            + "echo 'SELinux mode:'; getenforce 2>/dev/null || true; "
                            + "[ -n \"$P\" ] && { "
                            + "  ls -lZ \"$P\"; "
                            + "  BASE=$(dirname \"$P\"); "
                            + "  echo 'Compiled artifacts:'; "
                            + "  find \"$BASE\" -maxdepth 4 -type f "
                            + "\\( -name '*.odex' -o -name '*.vdex' -o -name '*.art' \\) "
                            + "-print 2>/dev/null | head -80; "
                            + "}; "
                            + "echo 'Data directory:'; "
                            + "ls -ldZ /data/user/0/" + packageName + " 2>/dev/null || true; "
                            + "echo 'Data top level:'; "
                            + "find /data/user/0/" + packageName
                            + " -maxdepth 1 -mindepth 1 -printf '%M %u:%g %p\\n' "
                            + "2>/dev/null | head -80; "
                            + "echo 'Root identity:'; id";

            Shell.Result rootAudit = shell.exec(new Shell.Command(
                    "sh",
                    "-c",
                    shell.makeLiteral(script)
            ));

            out.append("Root filesystem / SELinux / OAT audit:\n")
                    .append(clip(rootAudit.out + "\n" + rootAudit.err, 12000))
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
