package com.aefyr.sai.installer2.impl;

import android.content.Context;

import com.aefyr.sai.installer2.base.SaiPackageInstaller;
import com.aefyr.sai.installer2.base.SaiPiSessionObserver;
import com.aefyr.sai.installer2.base.model.SaiPiSessionParams;
import com.aefyr.sai.installer2.base.model.SaiPiSessionState;
import com.aefyr.sai.installer2.base.model.SaiPiSessionStatus;
import com.aefyr.sai.installer2.impl.rootless.RootlessSaiPackageInstaller;
import com.aefyr.sai.installer2.impl.rootless.XposedSaiPackageInstaller;
import com.aefyr.sai.installer2.impl.shell.RootedSaiPackageInstaller;
import com.aefyr.sai.installer2.impl.shell.ShizukuSaiPackageInstaller;
import com.aefyr.sai.utils.PreferencesValues;
import com.aefyr.sai.utils.VsiDeveloperOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class FlexSaiPackageInstaller implements SaiPackageInstaller, SaiPiSessionObserver {

    private static FlexSaiPackageInstaller sInstance;

    private final Context mContext;
    private SaiPackageInstaller mDefaultInstaller;

    private final HashMap<Integer, SaiPackageInstaller> mInstallers = new HashMap<>();
    private final ConcurrentHashMap<String, SaiPackageInstaller> mSessionIdToInstaller = new ConcurrentHashMap<>();

    private final Set<SaiPiSessionObserver> mObservers =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final ConcurrentLinkedQueue<QueuedDispatch> mPendingQueue = new ConcurrentLinkedQueue<>();
    private final Set<String> mActiveQueuedSessions =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicInteger mActiveQueueCount = new AtomicInteger(0);

    public static FlexSaiPackageInstaller getInstance(Context c) {
        synchronized (FlexSaiPackageInstaller.class) {
            return sInstance != null ? sInstance : new FlexSaiPackageInstaller(c);
        }
    }

    private FlexSaiPackageInstaller(Context c) {
        mContext = c.getApplicationContext();

        addInstaller(PreferencesValues.INSTALLER_ROOTLESS, RootlessSaiPackageInstaller.getInstance(mContext));
        addInstaller(PreferencesValues.INSTALLER_ROOTED, RootedSaiPackageInstaller.getInstance(mContext));
        addInstaller(PreferencesValues.INSTALLER_SHIZUKU, ShizukuSaiPackageInstaller.getInstance(mContext));
        addInstaller(PreferencesValues.INSTALLER_XPOSED, XposedSaiPackageInstaller.getInstance(mContext));

        sInstance = this;
    }

    public void addInstaller(int id, SaiPackageInstaller installer) {
        if (mInstallers.containsKey(id))
            throw new IllegalStateException("Installer with this id already added");

        if (mDefaultInstaller == null)
            mDefaultInstaller = installer;

        mInstallers.put(id, installer);
        installer.registerSessionObserver(this);
    }

    public String createSessionOnInstaller(int installerId, SaiPiSessionParams params) {
        return createSessionOnInstaller(Objects.requireNonNull(mInstallers.get(installerId)), params);
    }

    private String createSessionOnInstaller(SaiPackageInstaller installer, SaiPiSessionParams params) {
        String sessionId = installer.createSession(params);
        mSessionIdToInstaller.put(sessionId, installer);
        return sessionId;
    }

    @Override
    public String createSession(SaiPiSessionParams params) {
        return createSessionOnInstaller(mDefaultInstaller, params);
    }

    @Override
    public void enqueueSession(String sessionId) {
        SaiPackageInstaller installer = mSessionIdToInstaller.remove(sessionId);
        if (installer == null)
            throw new IllegalArgumentException("Unknown sessionId");

        if (!VsiDeveloperOptions.getInstance(mContext).installQueue()) {
            installer.enqueueSession(sessionId);
            return;
        }

        mPendingQueue.add(new QueuedDispatch(sessionId, installer));
        drainQueue();
    }

    private synchronized void drainQueue() {
        int maxParallel = VsiDeveloperOptions.getInstance(mContext).queueParallelism();

        while (mActiveQueueCount.get() < maxParallel) {
            QueuedDispatch next = mPendingQueue.poll();
            if (next == null)
                return;

            mActiveQueueCount.incrementAndGet();
            mActiveQueuedSessions.add(next.sessionId);
            next.installer.enqueueSession(next.sessionId);
        }
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
        ArrayList<SaiPiSessionState> sessions = new ArrayList<>();

        for (SaiPackageInstaller installer : mInstallers.values())
            sessions.addAll(installer.getSessions());

        Collections.sort(sessions);
        return sessions;
    }

    @Override
    public void onSessionStateChanged(SaiPiSessionState state) {
        if (isTerminal(state.status()) && mActiveQueuedSessions.remove(state.sessionId())) {
            mActiveQueueCount.updateAndGet(v -> Math.max(0, v - 1));
            drainQueue();
        }

        for (SaiPiSessionObserver observer : mObservers)
            observer.onSessionStateChanged(state);
    }

    public int pendingQueueCount() {
        return mPendingQueue.size();
    }

    public int activeQueueCount() {
        return mActiveQueueCount.get();
    }

    public List<String> activeQueueSessionIds() {
        ArrayList<String> ids = new ArrayList<>(mActiveQueuedSessions);
        Collections.sort(ids);
        return Collections.unmodifiableList(ids);
    }

    public List<String> pendingQueueSessionIds() {
        ArrayList<String> ids = new ArrayList<>();
        for (QueuedDispatch dispatch : mPendingQueue)
            ids.add(dispatch.sessionId);
        return Collections.unmodifiableList(ids);
    }

    private boolean isTerminal(SaiPiSessionStatus status) {
        return status == SaiPiSessionStatus.INSTALLATION_SUCCEED
                || status == SaiPiSessionStatus.INSTALLATION_FAILED;
    }

    private static class QueuedDispatch {
        final String sessionId;
        final SaiPackageInstaller installer;

        QueuedDispatch(String sessionId, SaiPackageInstaller installer) {
            this.sessionId = sessionId;
            this.installer = installer;
        }
    }
}
