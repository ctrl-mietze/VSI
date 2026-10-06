package com.aefyr.sai.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class VsiPackageContainerTools {

    private VsiPackageContainerTools() {}

    public static Result convert(Context context, Uri source) throws Exception {
        return normalize(
                context,
                source,
                VsiDeveloperOptions.getInstance(context).autoRepairContainers()
        );
    }

    public static Result repair(Context context, Uri source) throws Exception {
        return normalize(context, source, true);
    }

    private static Result normalize(Context context, Uri source, boolean repair) throws Exception {
        String sourceName = fileName(context, source);
        if (sourceName == null)
            sourceName = "package";

        boolean sourceIsApk = sourceName.toLowerCase().endsWith(".apk");
        String base = stripExtension(sourceName);
        String outputName = base + (repair ? "-repaired.apks" : "-converted.apks");

        Uri output = createDownload(context, outputName);
        int written = 0;
        int skipped = 0;

        try (InputStream rawIn = context.getContentResolver().openInputStream(source);
             OutputStream rawOut = context.getContentResolver().openOutputStream(output);
             ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(rawOut))) {

            if (rawIn == null || rawOut == null)
                throw new IllegalStateException("Unable to open source or destination");

            if (sourceIsApk) {
                ZipEntry outEntry = new ZipEntry("base.apk");
                zipOut.putNextEntry(outEntry);
                IOUtils.copyStream(new BufferedInputStream(rawIn), zipOut);
                zipOut.closeEntry();
                written = 1;
            } else {
                Set<String> names = new HashSet<>();
                try (ZipInputStream zipIn = new ZipInputStream(new BufferedInputStream(rawIn))) {
                    ZipEntry entry;
                    while ((entry = zipIn.getNextEntry()) != null) {
                        if (entry.isDirectory()) {
                            zipIn.closeEntry();
                            continue;
                        }

                        String name = entry.getName();
                        if (name == null || !name.toLowerCase().endsWith(".apk")) {
                            skipped++;
                            zipIn.closeEntry();
                            continue;
                        }

                        if (!names.add(name)) {
                            skipped++;
                            zipIn.closeEntry();
                            continue;
                        }

                        if (repair && entry.getSize() == 0) {
                            skipped++;
                            zipIn.closeEntry();
                            continue;
                        }

                        ZipEntry outEntry = new ZipEntry(name);
                        zipOut.putNextEntry(outEntry);
                        IOUtils.copyStream(zipIn, zipOut);
                        zipOut.closeEntry();
                        zipIn.closeEntry();
                        written++;
                    }
                }
            }
        } catch (Exception e) {
            deleteOutput(context, output);
            throw e;
        }

        if (written == 0) {
            deleteOutput(context, output);
            throw new IllegalArgumentException("No APK entries found in container");
        }

        finishPending(context, output);
        return new Result(output, outputName, written, skipped);
    }

    private static Uri createDownload(Context context, String displayName) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File vsiDir = new File(downloads, "VSI");
            if (!vsiDir.exists() && !vsiDir.mkdirs())
                throw new IllegalStateException("Unable to create Downloads/VSI");

            File output = uniqueFile(vsiDir, displayName);
            if (!output.createNewFile())
                throw new IllegalStateException("Unable to create " + output);
            return Uri.fromFile(output);
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
        values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/VSI");
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri uri = context.getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
        );
        if (uri == null)
            throw new IllegalStateException("Unable to create output in Downloads/VSI");
        return uri;
    }

    private static File uniqueFile(File directory, String displayName) {
        File candidate = new File(directory, displayName);
        if (!candidate.exists())
            return candidate;

        String base = stripExtension(displayName);
        String extension = displayName.toLowerCase().endsWith(".apks") ? ".apks" : "";
        int index = 2;
        while (candidate.exists()) {
            candidate = new File(directory, base + "-" + index++ + extension);
        }
        return candidate;
    }

    private static void deleteOutput(Context context, Uri uri) {
        try {
            if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null)
                new File(uri.getPath()).delete();
            else
                context.getContentResolver().delete(uri, null, null);
        } catch (Exception ignored) {}
    }

    private static void finishPending(Context context, Uri uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            return;

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.IS_PENDING, 0);
        context.getContentResolver().update(uri, values, null, null);
    }

    private static String fileName(Context context, Uri uri) {
        if ("file".equalsIgnoreCase(uri.getScheme()))
            return uri.getLastPathSegment();

        try (Cursor c = context.getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (c != null && c.moveToFirst())
                return c.getString(0);
        } catch (Exception ignored) {}

        return uri.getLastPathSegment();
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static class Result {
        public final Uri uri;
        public final String displayName;
        public final int apkEntries;
        public final int skippedEntries;

        Result(Uri uri, String displayName, int apkEntries, int skippedEntries) {
            this.uri = uri;
            this.displayName = displayName;
            this.apkEntries = apkEntries;
            this.skippedEntries = skippedEntries;
        }
    }
}
