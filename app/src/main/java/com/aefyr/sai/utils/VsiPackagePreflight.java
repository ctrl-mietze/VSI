package com.aefyr.sai.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VsiPackagePreflight {

    private VsiPackagePreflight() {}

    public static Result analyze(Context context, Uri uri) throws Exception {
        File temp = null;
        File apkFile;

        if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
            apkFile = new File(uri.getPath());
        } else {
            temp = File.createTempFile("vsi-preflight-", ".apk", context.getCacheDir());
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(temp)) {
                if (in == null)
                    throw new IllegalStateException("Unable to open APK");
                IOUtils.copyStream(in, out);
            }
            apkFile = temp;
        }

        try {
            return analyzeFile(context, apkFile);
        } finally {
            if (temp != null)
                temp.delete();
        }
    }

    private static Result analyzeFile(Context context, File apkFile) throws Exception {
        PackageManager pm = context.getPackageManager();
        int flags = PackageManager.GET_PERMISSIONS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            flags |= PackageManager.GET_SIGNING_CERTIFICATES;
        else
            flags |= PackageManager.GET_SIGNATURES;

        PackageInfo incoming = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), flags);
        if (incoming == null || incoming.applicationInfo == null)
            throw new IllegalArgumentException("Android could not parse this APK.");

        incoming.applicationInfo.sourceDir = apkFile.getAbsolutePath();
        incoming.applicationInfo.publicSourceDir = apkFile.getAbsolutePath();

        Result result = new Result();
        result.packageName = incoming.packageName;
        result.versionName = incoming.versionName;
        result.versionCode = longVersionCode(incoming);
        result.targetSdk = incoming.applicationInfo.targetSdkVersion;
        result.minSdk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? incoming.applicationInfo.minSdkVersion
                : 0;

        try {
            CharSequence label = incoming.applicationInfo.loadLabel(pm);
            result.appName = label == null ? incoming.packageName : label.toString();
        } catch (Exception e) {
            result.appName = incoming.packageName;
        }

        result.incomingCertificates = certificates(incoming);
        result.requestedPermissions = incoming.requestedPermissions == null
                ? Collections.emptyList()
                : Arrays.asList(incoming.requestedPermissions);

        try {
            PackageInfo installed = pm.getPackageInfo(incoming.packageName, flags);
            result.installed = true;
            result.installedVersionName = installed.versionName;
            result.installedVersionCode = longVersionCode(installed);
            result.installedCertificates = certificates(installed);
            result.signatureMatch = sameCertificates(
                    result.incomingCertificates,
                    result.installedCertificates
            );

            result.operation = compareVersions(
                    result.installedVersionCode,
                    result.versionCode
            );

            Set<String> installedPermissions = installed.requestedPermissions == null
                    ? Collections.emptySet()
                    : new HashSet<>(Arrays.asList(installed.requestedPermissions));

            for (String permission : result.requestedPermissions) {
                if (!installedPermissions.contains(permission))
                    result.addedPermissions.add(permission);
            }

            result.installedSystemApp = installed.applicationInfo != null
                    && (installed.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            result.installed = false;
            result.signatureMatch = true;
            result.operation = Operation.NEW_INSTALL;
        }

        return result;
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

        if (signatures == null)
            return Collections.emptyList();

        ArrayList<String> out = new ArrayList<>();
        for (Signature signature : signatures)
            out.add(sha256(signature.toByteArray()));

        Collections.sort(out);
        return out;
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest)
            sb.append(String.format("%02X", b));
        return sb.toString();
    }

    private static boolean sameCertificates(List<String> a, List<String> b) {
        if (a.isEmpty() || b.isEmpty())
            return false;

        for (String certificate : a) {
            if (b.contains(certificate))
                return true;
        }

        return false;
    }

    private static Operation compareVersions(long installed, long incoming) {
        if (incoming > installed)
            return Operation.UPDATE;
        if (incoming < installed)
            return Operation.DOWNGRADE;
        return Operation.REINSTALL;
    }

    public enum Operation {
        NEW_INSTALL,
        UPDATE,
        DOWNGRADE,
        REINSTALL
    }

    public static class Result {
        public String packageName;
        public String appName;
        public long versionCode;
        public String versionName;
        public int minSdk;
        public int targetSdk;

        public boolean installed;
        public boolean installedSystemApp;
        public long installedVersionCode;
        public String installedVersionName;

        public boolean signatureMatch;
        public Operation operation;

        public List<String> incomingCertificates = Collections.emptyList();
        public List<String> installedCertificates = Collections.emptyList();
        public List<String> requestedPermissions = Collections.emptyList();
        public final List<String> addedPermissions = new ArrayList<>();

        @Nullable
        public String firstIncomingCertificate() {
            return incomingCertificates.isEmpty() ? null : incomingCertificates.get(0);
        }

        @Nullable
        public String firstInstalledCertificate() {
            return installedCertificates.isEmpty() ? null : installedCertificates.get(0);
        }
    }
}
