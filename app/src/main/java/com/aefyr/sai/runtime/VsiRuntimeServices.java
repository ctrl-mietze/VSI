package com.aefyr.sai.runtime;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.aefyr.sai.R;
import com.aefyr.sai.ui.activities.MainActivity;

public final class VsiRuntimeServices {

    private static final String CHANNEL_ID = "vsi_runtime_service";
    private static final int NOTIFICATION_ID = 62020;

    private VsiRuntimeServices() {}

    public static void sync(Context context, VsiAppMode mode) {
        stopAll(context);

        Class<? extends Base> serviceClass;
        switch (mode) {
            case SHIZUKU:
            case XPOSED:
                serviceClass = VService.class;
                break;
            case ROOT:
            case ROOT_XPOSED:
                serviceClass = SystemService.class;
                break;
            case NORMAL:
            default:
                serviceClass = LiteService.class;
                break;
        }

        Intent intent = new Intent(context, serviceClass)
                .putExtra("mode", mode.id());

        try {
            ContextCompat.startForegroundService(context, intent);
        } catch (Exception ignored) {
            context.startService(intent);
        }
    }

    public static void stopAll(Context context) {
        context.stopService(new Intent(context, LiteService.class));
        context.stopService(new Intent(context, VService.class));
        context.stopService(new Intent(context, SystemService.class));
    }

    public abstract static class Base extends Service {

        protected abstract String serviceName();

        @Override
        public void onCreate() {
            super.onCreate();
            createChannel();
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            VsiAppMode mode = VsiAppMode.fromId(
                    intent == null ? null : intent.getStringExtra("mode")
            );

            Intent open = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent pending = PendingIntent.getActivity(
                    this,
                    62020,
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT
                            | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            ? PendingIntent.FLAG_IMMUTABLE : 0)
            );

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_install_full)
                    .setContentTitle(serviceName())
                    .setContentText("VSI " + mode.id() + " · " + mode.capabilityCount() + " functions")
                    .setContentIntent(pending)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();

            startForeground(NOTIFICATION_ID, notification);
            return START_STICKY;
        }

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        private void createChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
                return;

            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null)
                return;

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "VSI Runtime Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps the selected VSI installer runtime ready.");
            manager.createNotificationChannel(channel);
        }
    }

    public static class LiteService extends Base {
        @Override
        protected String serviceName() {
            return "VSI Lite Service";
        }
    }

    public static class VService extends Base {
        @Override
        protected String serviceName() {
            return "VSI VService";
        }
    }

    public static class SystemService extends Base {
        @Override
        protected String serviceName() {
            return "VSI System Service";
        }
    }
}
