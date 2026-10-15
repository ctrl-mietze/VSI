package com.aefyr.sai.utils;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class VsiReportExporter {

    private VsiReportExporter() {}

    public static Result saveText(Context context, String displayName, String content) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            return saveWithMediaStore(context, displayName, content);

        File dir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "VSI"
        );
        if (!dir.exists() && !dir.mkdirs())
            throw new IllegalStateException("Unable to create Downloads/VSI");

        File file = new File(dir, displayName);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        return new Result(Uri.fromFile(file), file.getAbsolutePath());
    }

    private static Result saveWithMediaStore(
            Context context,
            String displayName,
            String content
    ) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
        values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
        values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/VSI");
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri uri = context.getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
        );
        if (uri == null)
            throw new IllegalStateException("Unable to create report in Downloads/VSI");

        try {
            try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if (out == null)
                    throw new IllegalStateException("Unable to open report destination");
                out.write(content.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            context.getContentResolver().update(uri, done, null, null);

            return new Result(uri, "Downloads/VSI/" + displayName);
        } catch (Exception e) {
            try {
                context.getContentResolver().delete(uri, null, null);
            } catch (Exception ignored) {}
            throw e;
        }
    }

    public static final class Result {
        public final Uri uri;
        public final String location;

        Result(Uri uri, String location) {
            this.uri = uri;
            this.location = location;
        }
    }
}
