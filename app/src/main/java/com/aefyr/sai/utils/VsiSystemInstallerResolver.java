package com.aefyr.sai.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import com.aefyr.sai.BuildConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class VsiSystemInstallerResolver {

    private VsiSystemInstallerResolver() {}

    public static List<ComponentName> findInstallerActivities(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(
                        Uri.parse("file:///sdcard/Download/vsi-probe.apk"),
                        "application/vnd.android.package-archive"
                );

        List<ResolveInfo> infos = context.getPackageManager().queryIntentActivities(intent, 0);
        Set<ComponentName> out = new LinkedHashSet<>();

        for (ResolveInfo info : infos) {
            if (info.activityInfo == null)
                continue;

            String pkg = info.activityInfo.packageName;
            String name = info.activityInfo.name;

            if (pkg == null || name == null || BuildConfig.APPLICATION_ID.equals(pkg))
                continue;

            out.add(new ComponentName(pkg, name));
        }

        return Collections.unmodifiableList(new ArrayList<>(out));
    }

    public static List<String> commonInstallerPackages() {
        ArrayList<String> packages = new ArrayList<>();
        packages.add("com.google.android.packageinstaller");
        packages.add("com.android.packageinstaller");
        packages.add("com.samsung.android.packageinstaller");
        packages.add("com.miui.packageinstaller");
        packages.add("com.oplus.packageinstaller");
        packages.add("com.coloros.packageinstaller");
        packages.add("com.oneplus.packageinstaller");
        return Collections.unmodifiableList(packages);
    }

    public static boolean looksLikeInstallerPackage(String packageName) {
        if (packageName == null)
            return false;

        String lower = packageName.toLowerCase();
        if (lower.contains("packageinstaller"))
            return true;

        return commonInstallerPackages().contains(packageName);
    }
}
