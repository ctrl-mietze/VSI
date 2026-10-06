package com.aefyr.sai.installer2.impl;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;

import java.io.File;

import com.aefyr.sai.installer2.base.SaiPackageInstaller;
import com.aefyr.sai.installer2.base.SaiPiSessionObserver;
import com.aefyr.sai.installer2.base.model.SaiPiSessionParams;
import com.aefyr.sai.installer2.base.model.SaiPiSessionState;
import com.aefyr.sai.installer2.base.model.SaiPiSessionStatus;
import com.aefyr.sai.utils.Logs;
import com.aefyr.sai.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

@SuppressLint("UseSparseArrays")
public abstract class BaseSaiPackageInstaller implements SaiPackageInstaller {

    private Context mContext;
    private long mLastSessionId = 0;

    private ConcurrentHashMap<String, SaiPiSessionParams> mCreatedSessions = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, SaiPiSessionParams> mActiveSessions = new ConcurrentHashMap<>();

    private ConcurrentSkipListMap<String, SaiPiSessionState> mSessionStates = new ConcurrentSkipListMap<>();

    private Set<SaiPiSessionObserver> mObservers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    protected BaseSaiPackageInstaller(Context c) {
        mContext = c.getApplicationContext();
    }

    @Override
    public String createSession(SaiPiSessionParams params) {
        String sessionId = newSessionId();
        mCreatedSessions.put(sessionId, params);
        setSessionState(sessionId, new SaiPiSessionState.Builder(sessionId, SaiPiSessionStatus.CREATED).build());
        return sessionId;
    }

    @Override
    public void registerSessionObserver(SaiPiSessionObserver observer) {
        mObservers.add(observer);
    }

    @Override
    public void unregisterSessionObserver(SaiPiSessionObserver observer) {
        mObservers.remove(observer);
    }

    @Override
    public List<SaiPiSessionState> getSessions() {
        return Collections.unmodifiableList(new ArrayList<>(mSessionStates.values()));
    }

    protected void setSessionState(String sessionId, SaiPiSessionState state) {
        Logs.d(tag(), String.format("%s->setSessionState(%s, %s)", getClass().getSimpleName(), sessionId, state));
        mSessionStates.put(sessionId, state);

        if (state.status() == SaiPiSessionStatus.INSTALLATION_SUCCEED || state.status() == SaiPiSessionStatus.INSTALLATION_FAILED) {
            SaiPiSessionParams params = mActiveSessions.remove(sessionId);
            if (state.status() == SaiPiSessionStatus.INSTALLATION_SUCCEED && params != null && params.deleteSourceAfterSuccess()) {
                deleteSourceUris(params.sourceUris());
            }
        }

        Utils.onMainThread(() -> {
            for (SaiPiSessionObserver observer : mObservers)
                observer.onSessionStateChanged(state);
        });
    }

    private void deleteSourceUris(List<Uri> sourceUris) {
        for (Uri uri : sourceUris) {
            try {
                if ("file".equalsIgnoreCase(uri.getScheme())) {
                    String path = uri.getPath();
                    if (path != null && !new File(path).delete())
                        Logs.w(tag(), "Unable to delete source file " + path);
                } else if ("content".equalsIgnoreCase(uri.getScheme())) {
                    int deleted = getContext().getContentResolver().delete(uri, null, null);
                    if (deleted <= 0)
                        Logs.w(tag(), "Content provider did not delete source " + uri);
                }
            } catch (Exception e) {
                Logs.w(tag(), "Unable to delete installation source " + uri, e);
            }
        }
    }

    protected SaiPiSessionParams takeCreatedSession(String sessionId) {
        SaiPiSessionParams params = mCreatedSessions.remove(sessionId);
        if (params != null)
            mActiveSessions.put(sessionId, params);
        return params;
    }

    @SuppressLint("DefaultLocale")
    protected String newSessionId() {
        long sessionId = mLastSessionId++;
        return String.format("%d@%s", sessionId, getClass().getName());
    }

    protected Context getContext() {
        return mContext;
    }

    protected abstract String tag();
}
