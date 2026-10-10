package com.aefyr.sai.utils;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;

import com.aefyr.sai.shell.Shell;
import com.aefyr.sai.shell.ShizukuShell;
import com.aefyr.sai.shell.SuShell;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class VsiAdvancedApkAnalyzer {

    private VsiAdvancedApkAnalyzer() {}

    public static String analyze(Context context, Uri source, VsiAppMode mode) throws Exception {
        if (mode == VsiAppMode.NORMAL)
            throw new IllegalStateException("Advanced APK Analysis requires Shizuku, Xposed or Root mode.");

        File temp = null;
        File apk;

        if ("file".equalsIgnoreCase(source.getScheme()) && source.getPath() != null) {
            apk = new File(source.getPath());
        } else {
            temp = File.createTempFile("vsi-analysis-", ".apk", context.getCacheDir());
            try (InputStream in = context.getContentResolver().openInputStream(source);
                 FileOutputStream out = new FileOutputStream(temp)) {
                if (in == null)
                    throw new IllegalStateException("Unable to open APK.");
                IOUtils.copyStream(in, out);
            }
            apk = temp;
        }

        try {
            return analyzeFile(context, apk, mode);
        } finally {
            if (temp != null)
                temp.delete();
        }
    }

    private static String analyzeFile(Context context, File apk, VsiAppMode mode) throws Exception {
        PackageManager pm = context.getPackageManager();
        int flags = PackageManager.GET_PERMISSIONS
                | PackageManager.GET_ACTIVITIES
                | PackageManager.GET_SERVICES
                | PackageManager.GET_RECEIVERS
                | PackageManager.GET_PROVIDERS;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            flags |= PackageManager.GET_SIGNING_CERTIFICATES;
        else
            flags |= PackageManager.GET_SIGNATURES;

        PackageInfo info = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (info == null || info.applicationInfo == null)
            throw new IllegalArgumentException("Android could not parse this APK.");

        info.applicationInfo.sourceDir = apk.getAbsolutePath();
        info.applicationInfo.publicSourceDir = apk.getAbsolutePath();

        StringBuilder out = new StringBuilder();
        out.append("VSI Advanced APK Analysis\n");
        out.append("=========================\n\n");

        out.append("Mode: ").append(mode.name()).append("\n");
        out.append("File: ").append(apk.getName()).append("\n");
        out.append("Size: ").append(apk.length()).append(" bytes\n");
        out.append("SHA-256: ").append(sha256(apk)).append("\n\n");

        out.append("Package: ").append(info.packageName).append("\n");
        out.append("Version: ").append(info.versionName).append(" (")
                .append(longVersionCode(info)).append(")\n");
        out.append("minSdk: ").append(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? info.applicationInfo.minSdkVersion : 0).append("\n");
        out.append("targetSdk: ").append(info.applicationInfo.targetSdkVersion).append("\n");
        out.append("debuggable: ")
                .append((info.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                .append("\n");
        out.append("testOnly: ")
                .append((info.applicationInfo.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0)
                .append("\n\n");

        List<String> certs = certificates(info);
        out.append("Certificates (SHA-256):\n");
        if (certs.isEmpty())
            out.append("  none\n");
        else
            for (String cert : certs)
                out.append("  ").append(cert).append("\n");

        out.append("\nRequested permissions (")
                .append(info.requestedPermissions == null ? 0 : info.requestedPermissions.length)
                .append("):\n");
        if (info.requestedPermissions != null)
            for (String permission : info.requestedPermissions)
                out.append("  ").append(permission).append("\n");

        appendComponents(out, "Activities", info.activities);
        appendComponents(out, "Services", info.services);
        appendComponents(out, "Receivers", info.receivers);
        appendProviders(out, info.providers);

        ZipScan zip = scanZip(apk);
        out.append("\nArchive scan:\n");
        out.append("  Entries: ").append(zip.entryCount).append("\n");
        out.append("  DEX files: ").append(zip.dexCount).append("\n");
        out.append("  Native libraries: ").append(zip.nativeLibCount).append("\n");
        out.append("  ABIs: ").append(zip.abis).append("\n");
        out.append("  Embedded APKs: ").append(zip.embeddedApkCount).append("\n");

        PackageInfo installed = null;
        try {
            installed = pm.getPackageInfo(info.packageName, flags);
        } catch (PackageManager.NameNotFoundException ignored) {}

        if (installed != null) {
            out.append("\nInstalled package comparison:\n");
            out.append("  Installed version: ").append(installed.versionName)
                    .append(" (").append(longVersionCode(installed)).append(")\n");
            out.append("  Incoming version: ").append(info.versionName)
                    .append(" (").append(longVersionCode(info)).append(")\n");
            out.append("  Signature match: ")
                    .append(certificates(installed).equals(certs))
                    .append("\n");
        }

        Shell shell = shellFor(mode);
        if (shell != null && shell.isAvailable() && info.packageName != null) {
            Shell.Result path = shell.exec(new Shell.Command("pm", "path", info.packageName));
            Shell.Result dump = shell.exec(new Shell.Command("dumpsys", "package", info.packageName));

            out.append("\nPrivileged package diagnostics:\n");
            out.append("pm path:\n").append(path.toString()).append("\n\n");

            String dumpText = dump.toString();
            if (dumpText.length() > 12000)
                dumpText = dumpText.substring(0, 12000) + "\n… truncated by VSI";
            out.append("dumpsys package:\n").append(dumpText).append("\n");
        }

        return out.toString();
    }

    private static Shell shellFor(VsiAppMode mode) {
        if (mode == VsiAppMode.SHIZUKU)
            return ShizukuShell.getInstance();
        if (mode == VsiAppMode.ROOT || mode == VsiAppMode.ROOT_XPOSED)
            return SuShell.getInstance();
        return null;
    }

    private static void appendComponents(StringBuilder out, String title, ComponentInfo[] items) {
        out.append("\n").append(title).append(" (")
                .append(items == null ? 0 : items.length).append("):\n");
        if (items == null)
            return;

        for (ComponentInfo item : items) {
            out.append("  ").append(item.name)
                    .append(" | exported=").append(item.exported)
                    .append(" | enabled=").append(item.enabled)
                    .append("\n");
        }
    }

    private static void appendProviders(StringBuilder out, ProviderInfo[] items) {
        out.append("\nProviders (")
                .append(items == null ? 0 : items.length).append("):\n");
        if (items == null)
            return;

        for (ProviderInfo item : items) {
            out.append("  ").append(item.name)
                    .append(" | authority=").append(item.authority)
                    .append(" | exported=").append(item.exported)
                    .append("\n");
        }
    }

    private static long longVersionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode()
                : info.versionCode;
    }

    private static List<String> certificates(PackageInfo info) throws Exception {
        Signature[] signatures;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
        } else {
            signatures = info.signatures;
        }

        ArrayList<String> out = new ArrayList<>();
        if (signatures == null)
            return out;

        for (Signature signature : signatures)
            out.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));

        return out;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];

        try (InputStream in = new BufferedInputStream(new java.io.FileInputStream(file))) {
            int read;
            while ((read = in.read(buffer)) != -1)
                digest.update(buffer, 0, read);
        }

        return hex(digest.digest());
    }

    private static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data)
            sb.append(String.format("%02X", b));
        return sb.toString();
    }

    private static ZipScan scanZip(File file) throws Exception {
        ZipScan scan = new ZipScan();

        try (ZipInputStream in = new ZipInputStream(
                new BufferedInputStream(new java.io.FileInputStream(file)))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    in.closeEntry();
                    continue;
                }

                scan.entryCount++;
                String name = entry.getName();

                if (name.matches("classes(\\d*)?\\.dex"))
                    scan.dexCount++;

                if (name.startsWith("lib/") && name.endsWith(".so")) {
                    scan.nativeLibCount++;
                    String[] parts = name.split("/");
                    if (parts.length >= 3)
                        scan.abis.add(parts[1]);
                }

                if (name.toLowerCase().endsWith(".apk"))
                    scan.embeddedApkCount++;

                in.closeEntry();
            }
        }

        return scan;
    }

    private static class ZipScan {
        int entryCount;
        int dexCount;
        int nativeLibCount;
        int embeddedApkCount;
        final Set<String> abis = new LinkedHashSet<>();
    }
}
