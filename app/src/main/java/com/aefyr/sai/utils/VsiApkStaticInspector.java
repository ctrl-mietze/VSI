package com.aefyr.sai.utils;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VsiApkStaticInspector {

    private VsiApkStaticInspector() {}

    public static String inspect(Context context, Uri uri) throws Exception {
        Materialized materialized = materialize(context, uri);
        try {
            PackageManager pm = context.getPackageManager();

            int flags = PackageManager.GET_ACTIVITIES
                    | PackageManager.GET_SERVICES
                    | PackageManager.GET_RECEIVERS
                    | PackageManager.GET_PROVIDERS
                    | PackageManager.GET_PERMISSIONS
                    | PackageManager.GET_CONFIGURATIONS
                    | PackageManager.GET_META_DATA;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                flags |= PackageManager.GET_SIGNING_CERTIFICATES;
            else
                flags |= PackageManager.GET_SIGNATURES;

            PackageInfo info = pm.getPackageArchiveInfo(
                    materialized.file.getAbsolutePath(),
                    flags
            );

            if (info == null || info.applicationInfo == null)
                throw new IllegalArgumentException("Android could not parse APK manifest.");

            ApplicationInfo app = info.applicationInfo;
            app.sourceDir = materialized.file.getAbsolutePath();
            app.publicSourceDir = materialized.file.getAbsolutePath();

            StringBuilder out = new StringBuilder();

            out.append("Manifest / package audit\n");
            out.append("------------------------\n");
            out.append("Package: ").append(info.packageName).append("\n");
            out.append("Install location: ").append(info.installLocation).append("\n");
            out.append("Split count: ")
                    .append(info.splitNames == null ? 0 : info.splitNames.length)
                    .append("\n");

            if (info.splitNames != null)
                out.append("Split names: ").append(Arrays.toString(info.splitNames)).append("\n");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                out.append("minSdk: ").append(app.minSdkVersion).append("\n");

            out.append("targetSdk: ").append(app.targetSdkVersion).append("\n");
            out.append("Flags:\n");
            appendFlag(out, "debuggable", app.flags, ApplicationInfo.FLAG_DEBUGGABLE);
            appendFlag(out, "testOnly", app.flags, ApplicationInfo.FLAG_TEST_ONLY);
            appendFlag(out, "allowBackup", app.flags, ApplicationInfo.FLAG_ALLOW_BACKUP);
            appendFlag(out, "largeHeap", app.flags, ApplicationInfo.FLAG_LARGE_HEAP);
            appendFlag(out, "hasCode", app.flags, ApplicationInfo.FLAG_HAS_CODE);
            appendFlag(out, "supportsRTL", app.flags, ApplicationInfo.FLAG_SUPPORTS_RTL);
            appendFlag(out, "externalStorage", app.flags, ApplicationInfo.FLAG_EXTERNAL_STORAGE);
            appendFlag(out, "multiArch", app.flags, ApplicationInfo.FLAG_MULTIARCH);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                appendFlag(
                        out,
                        "usesCleartextTraffic",
                        app.flags,
                        ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC
                );
            }

            out.append("\nComponents\n----------\n");
            appendComponents(out, "Activities", info.activities);
            appendComponents(out, "Receivers", info.receivers);
            appendComponents(out, "Services", info.services);
            appendComponents(out, "Providers", info.providers);

            out.append("\nPermissions\n-----------\n");
            List<String> requested = info.requestedPermissions == null
                    ? new ArrayList<>()
                    : Arrays.asList(info.requestedPermissions);

            out.append("Requested: ").append(requested.size()).append("\n");
            out.append("Declared by APK: ")
                    .append(info.permissions == null ? 0 : info.permissions.length)
                    .append("\n");

            List<String> highRisk = highRiskPermissions(requested);
            out.append("High-impact requested: ").append(highRisk.size()).append("\n");
            for (String permission : highRisk)
                out.append("  ! ").append(permission).append("\n");

            if (info.permissions != null) {
                for (PermissionInfo permission : info.permissions) {
                    if (permission == null)
                        continue;
                    out.append("  Declares: ").append(permission.name).append("\n");
                }
            }

            out.append("\nDevice feature requests\n-----------------------\n");
            FeatureInfo[] features = info.reqFeatures;
            out.append("Features: ").append(features == null ? 0 : features.length).append("\n");
            if (features != null) {
                for (FeatureInfo feature : features) {
                    if (feature == null)
                        continue;

                    if (feature.name != null) {
                        out.append("  ")
                                .append((feature.flags & FeatureInfo.FLAG_REQUIRED) != 0
                                        ? "required "
                                        : "optional ")
                                .append(feature.name)
                                .append("\n");
                    } else if (feature.reqGlEsVersion != 0) {
                        out.append("  OpenGL ES 0x")
                                .append(Integer.toHexString(feature.reqGlEsVersion))
                                .append("\n");
                    }
                }
            }

            out.append("\nSigning\n-------\n");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
                out.append("Multiple signers: ")
                        .append(info.signingInfo.hasMultipleSigners())
                        .append("\n");
                out.append("Signing history entries: ")
                        .append(info.signingInfo.getSigningCertificateHistory() == null
                                ? 0
                                : info.signingInfo.getSigningCertificateHistory().length)
                        .append("\n");
            }

            return out.toString();
        } finally {
            if (materialized.temporary)
                materialized.file.delete();
        }
    }

    private static void appendFlag(
            StringBuilder out,
            String name,
            int flags,
            int flag
    ) {
        out.append("  ")
                .append(name)
                .append(": ")
                .append((flags & flag) != 0)
                .append("\n");
    }

    private static void appendComponents(
            StringBuilder out,
            String label,
            ComponentInfo[] components
    ) {
        int total = components == null ? 0 : components.length;
        int exported = 0;
        int enabled = 0;

        if (components != null) {
            for (ComponentInfo component : components) {
                if (component == null)
                    continue;
                if (component.exported)
                    exported++;
                if (component.enabled)
                    enabled++;
            }
        }

        out.append(label)
                .append(": ")
                .append(total)
                .append(" · exported ")
                .append(exported)
                .append(" · enabled ")
                .append(enabled)
                .append("\n");

        if (components != null) {
            for (ComponentInfo component : components) {
                if (component == null || !component.exported)
                    continue;

                out.append("  exported: ")
                        .append(component.name)
                        .append("\n");
            }
        }
    }

    private static List<String> highRiskPermissions(List<String> requested) {
        Set<String> highRisk = new HashSet<>(Arrays.asList(
                "android.permission.CAMERA",
                "android.permission.RECORD_AUDIO",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_BACKGROUND_LOCATION",
                "android.permission.READ_CONTACTS",
                "android.permission.WRITE_CONTACTS",
                "android.permission.READ_SMS",
                "android.permission.SEND_SMS",
                "android.permission.RECEIVE_SMS",
                "android.permission.CALL_PHONE",
                "android.permission.READ_CALL_LOG",
                "android.permission.WRITE_CALL_LOG",
                "android.permission.READ_PHONE_STATE",
                "android.permission.MANAGE_EXTERNAL_STORAGE",
                "android.permission.QUERY_ALL_PACKAGES",
                "android.permission.REQUEST_INSTALL_PACKAGES",
                "android.permission.SYSTEM_ALERT_WINDOW",
                "android.permission.BIND_ACCESSIBILITY_SERVICE",
                "android.permission.PACKAGE_USAGE_STATS",
                "android.permission.WRITE_SETTINGS"
        ));

        ArrayList<String> result = new ArrayList<>();
        for (String permission : requested) {
            if (highRisk.contains(permission))
                result.add(permission);
        }
        return result;
    }

    private static Materialized materialize(Context context, Uri uri) throws Exception {
        if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null)
            return new Materialized(new File(uri.getPath()), false);

        File temp = File.createTempFile("vsi-static-inspector-", ".apk", context.getCacheDir());

        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(temp)) {
            if (in == null)
                throw new IllegalStateException("Unable to open package source.");
            IOUtils.copyStream(in, out);
        }

        return new Materialized(temp, true);
    }

    private static final class Materialized {
        final File file;
        final boolean temporary;

        Materialized(File file, boolean temporary) {
            this.file = file;
            this.temporary = temporary;
        }
    }
}
