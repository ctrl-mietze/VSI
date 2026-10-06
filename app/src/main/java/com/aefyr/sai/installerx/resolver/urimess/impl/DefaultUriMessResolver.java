package com.aefyr.sai.installerx.resolver.urimess.impl;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.aefyr.sai.R;
import com.aefyr.sai.installerx.resolver.meta.ApkSourceFile;
import com.aefyr.sai.installerx.resolver.meta.ApkSourceMetaResolutionResult;
import com.aefyr.sai.installerx.resolver.meta.SplitApkSourceMetaResolver;
import com.aefyr.sai.installerx.resolver.meta.impl.ZipFileApkSourceFile;
import com.aefyr.sai.installerx.resolver.urimess.SourceType;
import com.aefyr.sai.installerx.resolver.urimess.UriHost;
import com.aefyr.sai.installerx.resolver.urimess.UriMessResolutionError;
import com.aefyr.sai.installerx.resolver.urimess.UriMessResolutionResult;
import com.aefyr.sai.installerx.resolver.urimess.UriMessResolver;
import com.aefyr.sai.utils.Utils;
import com.aefyr.sai.utils.VsiDeveloperOptions;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DefaultUriMessResolver implements UriMessResolver {
    private static final String TAG = "DefaultMessResolver";

    private final Context mContext;
    private final SplitApkSourceMetaResolver mMetaResolver;

    public DefaultUriMessResolver(Context context, SplitApkSourceMetaResolver metaResolver) {
        mContext = context;
        mMetaResolver = metaResolver;
    }

    @Override
    public List<UriMessResolutionResult> resolve(Collection<Uri> uris, UriHost uriHost) {
        List<UriMessResolutionResult> results = new ArrayList<>();
        List<Uri> apkFileUris = new ArrayList<>();

        for (Uri uri : uris) {
            String fileName = uriHost.getFileNameFromUri(uri);
            String extension = Utils.getExtension(fileName);

            if (extension != null && "apk".equalsIgnoreCase(extension)) {
                apkFileUris.add(uri);
                continue;
            }

            boolean knownArchive = extension != null && isKnownArchiveExtension(extension);

            if (!knownArchive && !VsiDeveloperOptions.getInstance(mContext).probeUnknownContainers()) {
                String extensionForError = extension == null ? "?" : extension;
                results.add(UriMessResolutionResult.failure(
                        SourceType.UNKNOWN,
                        Collections.singletonList(uri),
                        new UriMessResolutionError(
                                mContext.getString(R.string.installerx_default_mess_resolver_error_unknown_extension, extensionForError),
                                false
                        )
                ));
                continue;
            }

            UriMessResolutionResult archiveResult = resolveArchive(uri, uriHost, fileName, knownArchive);

            if (archiveResult != null) {
                results.add(archiveResult);
                continue;
            }

            String extensionForError = extension == null ? "?" : extension;
            results.add(UriMessResolutionResult.failure(
                    SourceType.UNKNOWN,
                    Collections.singletonList(uri),
                    new UriMessResolutionError(
                            mContext.getString(R.string.installerx_default_mess_resolver_error_unknown_extension, extensionForError),
                            false
                    )
            ));
        }

        if (!apkFileUris.isEmpty()) {
            try {
                ApkSourceMetaResolutionResult resolutionResult =
                        mMetaResolver.resolveFor(new MultipleApkFilesApkSourceFile(apkFileUris, uriHost));

                if (resolutionResult.isSuccessful()) {
                    results.add(UriMessResolutionResult.success(
                            SourceType.APK_FILES,
                            apkFileUris,
                            resolutionResult.meta()
                    ));
                } else {
                    results.add(UriMessResolutionResult.failure(
                            SourceType.APK_FILES,
                            apkFileUris,
                            new UriMessResolutionError(
                                    resolutionResult.error().message(),
                                    resolutionResult.error().doesTryingToInstallNonethelessMakeSense()
                            )
                    ));
                }
            } catch (Exception e) {
                Log.w(TAG, "Exception while resolving split meta", e);
                results.add(UriMessResolutionResult.failure(
                        SourceType.APK_FILES,
                        apkFileUris,
                        new UriMessResolutionError(e.getMessage(), true)
                ));
            }
        }

        return results;
    }

    /**
     * Known archive extensions are treated as archives directly.
     * Unknown extensions are probed as ZIP-compatible containers. This is the
     * extension-independent hook used by VSI's future custom package formats.
     */
    private UriMessResolutionResult resolveArchive(Uri uri, UriHost uriHost, String fileName, boolean knownArchive) {
        try (UriHost.UriAsFile uriAsFile = uriHost.openUriAsFile(uri)) {
            try {
                return resolutionResultToUriResult(
                        uri,
                        mMetaResolver.resolveFor(
                                new ZipFileApkSourceFile(uriAsFile.file(), fileName)
                        )
                );
            } catch (Exception firstError) {
                if (!VsiDeveloperOptions.getInstance(mContext).autoRepairContainers())
                    throw firstError;

                Log.w(TAG, "VSI auto-repair fallback for " + uri, firstError);

                File repaired = rebuildApkOnlyArchive(uriAsFile.file());
                ApkSourceMetaResolutionResult repairedResolution =
                        mMetaResolver.resolveFor(
                                new ZipFileApkSourceFile(repaired, fileName)
                        );

                if (!repairedResolution.isSuccessful()) {
                    if (!repaired.delete())
                        Log.d(TAG, "Unable to delete failed VSI repaired cache archive " + repaired);

                    return resolutionResultToUriResult(uri, repairedResolution);
                }

                // Keep the repaired file in app cache for the installation session.
                // Android may remove it later as normal cache data. Installing the
                // original source here would defeat the purpose of auto-repair.
                return resolutionResultToUriResult(Uri.fromFile(repaired), repairedResolution);
            }
        } catch (Exception e) {
            if (!knownArchive) {
                Log.d(TAG, "Unknown extension is not a ZIP-compatible VSI container: " + uri);
                return null;
            }

            Log.w(TAG, "Exception while resolving archive split meta", e);
            return UriMessResolutionResult.failure(
                    SourceType.ZIP,
                    Collections.singletonList(uri),
                    new UriMessResolutionError(e.getLocalizedMessage(), true)
            );
        }
    }

    private UriMessResolutionResult resolutionResultToUriResult(
            Uri originalUri,
            ApkSourceMetaResolutionResult resolutionResult
    ) {
        if (resolutionResult.isSuccessful()) {
            return UriMessResolutionResult.success(
                    SourceType.ZIP,
                    Collections.singletonList(originalUri),
                    resolutionResult.meta()
            );
        }

        return UriMessResolutionResult.failure(
                SourceType.ZIP,
                Collections.singletonList(originalUri),
                new UriMessResolutionError(
                        resolutionResult.error().message(),
                        resolutionResult.error().doesTryingToInstallNonethelessMakeSense()
                )
        );
    }

    private File rebuildApkOnlyArchive(File source) throws Exception {
        File output = File.createTempFile("vsi-repaired-", ".zip", mContext.getCacheDir());
        Set<String> names = new HashSet<>();
        int written = 0;

        try (ZipInputStream in = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(source)));
             ZipOutputStream out = new ZipOutputStream(
                     new BufferedOutputStream(new FileOutputStream(output)))) {

            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];

            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    in.closeEntry();
                    continue;
                }

                String name = entry.getName();
                if (name == null
                        || !name.toLowerCase().endsWith(".apk")
                        || !names.add(name)
                        || entry.getSize() == 0) {
                    in.closeEntry();
                    continue;
                }

                out.putNextEntry(new ZipEntry(name));

                int read;
                while ((read = in.read(buffer)) != -1)
                    out.write(buffer, 0, read);

                out.closeEntry();
                in.closeEntry();
                written++;
            }
        }

        if (written == 0) {
            output.delete();
            throw new IllegalArgumentException("Auto-repair found no usable APK entries");
        }

        return output;
    }

    private boolean isKnownArchiveExtension(String extension) {
        switch (extension.toLowerCase()) {
            case "zip":
            case "apks":
            case "xapk":
            case "apkm":
                return true;
            default:
                return false;
        }
    }

    private static class MultipleApkFilesApkSourceFile implements ApkSourceFile {

        private final List<Uri> mUris;
        private final UriHost mUriHost;

        private MultipleApkFilesApkSourceFile(List<Uri> uris, UriHost uriHost) {
            mUris = uris;
            mUriHost = uriHost;
        }

        @Override
        public List<Entry> listEntries() {
            List<Entry> entries = new ArrayList<>();
            for (Uri uri : mUris) {
                String name = mUriHost.getFileNameFromUri(uri);
                entries.add(new InternalEntry(uri, name, name, mUriHost.getFileSizeFromUri(uri)));
            }
            return entries;
        }

        @Override
        public InputStream openEntryInputStream(Entry entry) throws Exception {
            return mUriHost.openUriInputStream(((InternalEntry) entry).mUri);
        }

        @Override
        public String getName() {
            return "multiple.apk";
        }

        private static class InternalEntry extends Entry {
            private final Uri mUri;

            private InternalEntry(Uri uri, String name, String localPath, long size) {
                super(name, localPath, size);
                mUri = uri;
            }
        }
    }
}
