package com.shaterguy.chatgptpromptscheduler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.content.pm.PackageManager;

public final class NotificationHelper {
    public static final String CHANNEL_ACTIVE = "scheduler_active";
    public static final String CHANNEL_RESULT = "scheduler_result";
    public static final String CHANNEL_ORCHESTRATION = "orchestration_active";
    public static final String CHANNEL_ORCHESTRATION_ALERT = "orchestration_alert";

    private NotificationHelper() {}

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ACTIVE, "예약 실행 중", NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_RESULT, "예약 실행 결과", NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ORCHESTRATION, "오토런 중계", NotificationManager.IMPORTANCE_LOW));
        NotificationChannel alert = new NotificationChannel(CHANNEL_ORCHESTRATION_ALERT,
                "오토런 오류 및 사용자 조치", NotificationManager.IMPORTANCE_DEFAULT);
        alert.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(alert);
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

    public static Notification orchestrationActive(Context context, String text) {
        ensureChannels(context);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 7020,
                new Intent(context, OrchestrationActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(context, CHANNEL_ORCHESTRATION)
                .setSmallIcon(R.drawable.ic_stat_schedule)
                .setContentTitle("ChatGPT 오토런 중계")
                .setContentText(text)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicOrchestrationNotification(context))
                .setContentIntent(contentIntent)
                .build();
    }

    public static void orchestrationResult(Context context, boolean success, String title, String message) {
        ensureChannels(context);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 7021,
                new Intent(context, OrchestrationActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(context, CHANNEL_RESULT)
                .setSmallIcon(R.drawable.ic_stat_schedule)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicOrchestrationNotification(context))
                .setContentIntent(contentIntent)
                .build();
        context.getSystemService(NotificationManager.class).notify((int) (System.currentTimeMillis() & 0x7fffffff), notification);
    }

    public static void orchestrationTerminal(Context context, OrchestrationSignal.Type type, String jobId) {
        String title = terminalTitle(type);
        String message = terminalMessage(type, jobId);
        orchestrationResult(context, type == OrchestrationSignal.Type.DONE, title, message);
    }

    public static String terminalTitle(OrchestrationSignal.Type type) {
        switch (type) {
            case DONE -> { return "오토런 작업 완료"; }
            case PAUSE -> { return "오토런 작업 일시정지"; }
            case ABORTED -> { return "오토런 작업 중단"; }
            default -> throw new IllegalArgumentException("terminal 신호가 아닙니다.");
        }
    }

    public static String terminalMessage(OrchestrationSignal.Type type, String jobId) {
        String safeJob = safeId(jobId);
        switch (type) {
            case DONE -> { return "Job " + safeJob + " 작업이 완료되었습니다."; }
            case PAUSE -> { return "Job " + safeJob + " 작업이 일시정지되었습니다."; }
            case ABORTED -> { return "Job " + safeJob + " 작업이 중단되었습니다."; }
            default -> throw new IllegalArgumentException("terminal 신호가 아닙니다.");
        }
    }

    public static void orchestrationError(Context context, String side, String jobId,
                                          String step, String round, String detail) {
        String sideLabel = OrchestrationStore.sideLabel(side);
        String sequence = safeId(step) + (safeId(round).isEmpty() ? "" : "/" + safeId(round));
        String content = "Job " + safeId(jobId) + " · " + safeDetail(detail)
                + (sequence.isEmpty() ? "" : " · " + sequence) + " 중계를 일시정지했습니다.";
        notifyOrchestrationAlert(context, "오토런 오류 · " + sideLabel, content,
                safeId(jobId).hashCode() ^ 0x2b19);
    }

    public static void orchestrationUserAction(Context context, String side, String jobId,
                                               String step, String round, String actionId) {
        String sequence = safeId(step) + (safeId(round).isEmpty() ? "" : "/" + safeId(round));
        String content = "Job " + safeId(jobId) + " · 사용자 조치가 필요합니다"
                + (sequence.isEmpty() ? "." : " · " + sequence + ".")
                + " 앱에서 상태를 확인해 주세요.";
        notifyOrchestrationAlert(context, "오토런 사용자 조치 · " + OrchestrationStore.sideLabel(side),
                content, safeId(jobId).hashCode() ^ safeId(actionId).hashCode());
    }

    private static void notifyOrchestrationAlert(Context context, String title, String content, int requestCode) {
        ensureChannels(context);
        Intent open = new Intent(context, OrchestrationActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, requestCode, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(context, CHANNEL_ORCHESTRATION_ALERT)
                .setSmallIcon(R.drawable.ic_stat_schedule)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new Notification.BigTextStyle().bigText(content))
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicOrchestrationNotification(context))
                .setContentIntent(contentIntent)
                .build();
        context.getSystemService(NotificationManager.class).notify(requestCode, notification);
    }

    private static Notification publicOrchestrationNotification(Context context) {
        return new Notification.Builder(context, CHANNEL_ORCHESTRATION_ALERT)
                .setSmallIcon(R.drawable.ic_stat_schedule)
                .setContentTitle("ChatGPT 오토런 중계")
                .setContentText("앱에서 중계 상태를 확인해 주세요.")
                .build();
    }

    private static String safeId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{0,64}")) return "";
        return value;
    }

    private static String safeDetail(String value) {
        if (value == null) return "오토런 응답 오류";
        String safe = value.replace('\n', ' ').replace('\r', ' ').trim();
        return safe.length() > 180 ? safe.substring(0, 180) : safe;
    }

    public static boolean orchestrationAlertsEnabled(Context context) {
        ensureChannels(context);
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ORCHESTRATION_ALERT);
            return channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        return true;
    }
}
