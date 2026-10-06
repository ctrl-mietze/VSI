package com.aefyr.sai.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.preference.PreferenceManager;

import com.aefyr.sai.installer2.base.model.SaiPiSessionParams;
import com.aefyr.sai.installer2.base.model.SaiPiSessionState;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VsiSessionHistoryStore {

    private static final String KEY = "vsi_session_history_json";
    private static final int MAX_ENTRIES = 200;
    private static VsiSessionHistoryStore sInstance;

    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final Gson mGson = new Gson();
    private final Type mListType = new TypeToken<ArrayList<Entry>>() {}.getType();

    public static VsiSessionHistoryStore getInstance(Context context) {
        synchronized (VsiSessionHistoryStore.class) {
            if (sInstance == null)
                sInstance = new VsiSessionHistoryStore(context.getApplicationContext());
            return sInstance;
        }
    }

    private VsiSessionHistoryStore(Context context) {
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public synchronized void record(SaiPiSessionState state, SaiPiSessionParams params) {
        if (!VsiDeveloperOptions.getInstance(mContext).sessionHistory())
            return;

        ArrayList<Entry> entries = new ArrayList<>(getEntries());
        Entry entry = new Entry();
        entry.timestamp = System.currentTimeMillis();
        entry.sessionId = state.sessionId();
        entry.status = state.status().name();
        entry.packageName = state.packageName();
        entry.appName = state.appTempName();
        entry.installer = installerFromSessionId(state.sessionId());
        entry.shortError = state.shortError();

        if (params != null && VsiDeveloperOptions.getInstance(mContext).showSourceIdentity()) {
            entry.sources = new ArrayList<>();
            for (Uri uri : params.sourceUris())
                entry.sources.add(uri.toString());
        }

        entries.add(0, entry);
        if (entries.size() > MAX_ENTRIES)
            entries.subList(MAX_ENTRIES, entries.size()).clear();

        mPrefs.edit().putString(KEY, mGson.toJson(entries)).apply();
    }

    public synchronized List<Entry> getEntries() {
        String json = mPrefs.getString(KEY, "[]");
        try {
            ArrayList<Entry> entries = mGson.fromJson(json, mListType);
            if (entries == null)
                return Collections.emptyList();
            return Collections.unmodifiableList(entries);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public synchronized void recordLegacy(
            long sessionId,
            boolean success,
            String packageNameOrError,
            int installerMode,
            List<Uri> sourceUris
    ) {
        if (!VsiDeveloperOptions.getInstance(mContext).sessionHistory())
            return;

        ArrayList<Entry> entries = new ArrayList<>(getEntries());
        Entry entry = new Entry();
        entry.timestamp = System.currentTimeMillis();
        entry.sessionId = "legacy:" + sessionId;
        entry.status = success ? "INSTALLATION_SUCCEED" : "INSTALLATION_FAILED";
        entry.installer = installerFromMode(installerMode);

        if (success)
            entry.packageName = packageNameOrError;
        else
            entry.shortError = packageNameOrError;

        if (VsiDeveloperOptions.getInstance(mContext).showSourceIdentity()
                && sourceUris != null
                && !sourceUris.isEmpty()) {
            entry.sources = new ArrayList<>();
            for (Uri uri : sourceUris)
                entry.sources.add(uri.toString());
        }

        entries.add(0, entry);
        if (entries.size() > MAX_ENTRIES)
            entries.subList(MAX_ENTRIES, entries.size()).clear();

        mPrefs.edit().putString(KEY, mGson.toJson(entries)).apply();
    }

    public synchronized void clear() {
        mPrefs.edit().remove(KEY).apply();
    }

    private String installerFromMode(int mode) {
        switch (mode) {
            case PreferencesValues.INSTALLER_ROOTED:
                return "Root";
            case PreferencesValues.INSTALLER_SHIZUKU:
                return "Shizuku";
            case PreferencesValues.INSTALLER_XPOSED:
                return "Xposed / Vector";
            default:
                return "Rootless";
        }
    }

    private String installerFromSessionId(String sessionId) {
        if (sessionId == null)
            return "Unknown";
        if (sessionId.contains("XposedSaiPackageInstaller"))
            return "Xposed / Vector";
        if (sessionId.contains("ShizukuSaiPackageInstaller"))
            return "Shizuku";
        if (sessionId.contains("RootedSaiPackageInstaller"))
            return "Root";
        if (sessionId.contains("RootlessSaiPackageInstaller"))
            return "Rootless";
        return "Unknown";
    }

    public static class Entry {
        public long timestamp;
        public String sessionId;
        public String status;
        public String packageName;
        public String appName;
        public String installer;
        public String shortError;
        public List<String> sources;
    }
}
