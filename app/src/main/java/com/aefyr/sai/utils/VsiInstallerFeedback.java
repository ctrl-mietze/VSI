package com.aefyr.sai.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.aefyr.sai.R;
import com.aefyr.sai.installer2.base.model.SaiPiSessionState;
import com.aefyr.sai.installer2.base.model.SaiPiSessionStatus;
import com.aefyr.sai.ui.activities.MainActivity;

public final class VsiInstallerFeedback {

    public static final String MODE_NONE = "none";
    public static final String MODE_DIALOG = "dialog";
    public static final String MODE_NOTIFICATION = "notification";
    public static final String MODE_POPUP = "popup";

    private static final String CHANNEL_ID = "vsi_install_results";

    private VsiInstallerFeedback() {}

    public static void showInstallResultNotification(Context context, SaiPiSessionState state) {
        Context localized = VsiLocaleHelper.wrap(context);

        boolean success = state.status() == SaiPiSessionStatus.INSTALLATION_SUCCEED;
        String app = state.appTempName();
        if (app == null || app.trim().isEmpty())
            app = state.packageName();
        if (app == null || app.trim().isEmpty())
            app = localized.getString(R.string.installer_unknown_app);

        String title;
        String message;

        if (success) {
            title = localized.getString(R.string.vsi_feedback_success_title);
            message = localized.getString(R.string.vsi_feedback_success_message, app);
        } else {
            title = localized.getString(R.string.vsi_feedback_failure_title);
            String reason = state.shortError();
            if (reason == null || reason.trim().isEmpty())
                reason = localized.getString(R.string.vsi_feedback_unknown_error);
            message = localized.getString(R.string.vsi_feedback_failure_message, app, reason);
        }

        showNotification(localized, success, title, message);
    }

    public static void showNotification(
            Context context,
            boolean success,
            String title,
            String message
    ) {
        Context localized = VsiLocaleHelper.wrap(context);
        createChannel(localized);

        Intent open = new Intent(localized, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("vsi_open_install_sessions", true);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                localized,
                (int) (System.currentTimeMillis() & 0x7fffffff),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(localized, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_install_full)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(success
                        ? NotificationCompat.PRIORITY_DEFAULT
                        : NotificationCompat.PRIORITY_HIGH);

        NotificationManagerCompat.from(localized).notify(
                "vsi_install_result",
                (int) (System.currentTimeMillis() & 0x7fffffff),
                builder.build()
        );
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null)
            return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.vsi_feedback_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(context.getString(R.string.vsi_feedback_notification_channel_summary));
        manager.createNotificationChannel(channel);
    }
}
