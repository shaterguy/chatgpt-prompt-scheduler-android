package com.shaterguy.chatgptpromptscheduler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class NotificationHelper {
    public static final String CHANNEL_ACTIVE = "scheduler_active";
    public static final String CHANNEL_RESULT = "scheduler_result";
    private static final String RETIRED_CHANNEL_ORCHESTRATION = "orchestration_active";
    private static final String RETIRED_CHANNEL_ORCHESTRATION_ALERT = "orchestration_alert";

    private NotificationHelper() {}

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.deleteNotificationChannel(RETIRED_CHANNEL_ORCHESTRATION);
        manager.deleteNotificationChannel(RETIRED_CHANNEL_ORCHESTRATION_ALERT);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ACTIVE, "예약 실행 중", NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_RESULT, "예약 실행 결과", NotificationManager.IMPORTANCE_DEFAULT));
    }

    public static Notification active(Context context, String text) {
        ensureChannels(context);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(context, CHANNEL_ACTIVE)
                .setSmallIcon(R.drawable.ic_stat_schedule)
                .setContentTitle("ChatGPT 예약 실행")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .build();
    }

    public static void result(Context context, boolean success, String title, String message, String runId) {
        ensureChannels(context);
        int requestCode = runId == null ? 1 : runId.hashCode();
        Intent openLogs = new Intent(context, LogsActivity.class).putExtra("focusRunId", runId);
        PendingIntent contentIntent = PendingIntent.getActivity(context, requestCode, openLogs,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_RESULT)
                .setSmallIcon(R.drawable.ic_stat_schedule)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);
        if (!success && runId != null && !runId.isBlank()) {
            Intent exportLog = new Intent(context, LogsActivity.class).putExtra("exportRunId", runId);
            PendingIntent exportIntent = PendingIntent.getActivity(context, requestCode ^ 0x5a5a, exportLog,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(new Notification.Action.Builder(R.drawable.ic_stat_schedule, "풀로그 내려받기", exportIntent).build());
        }
        context.getSystemService(NotificationManager.class).notify((int) (System.currentTimeMillis() & 0x7fffffff), builder.build());
    }
}
