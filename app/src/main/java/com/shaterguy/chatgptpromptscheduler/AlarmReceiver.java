package com.shaterguy.chatgptpromptscheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String scheduleId = intent.getStringExtra("scheduleId");
        if (scheduleId == null || scheduleId.isBlank()) return;

        long scheduledAt = intent.getLongExtra("scheduledAt", 0L);
        Schedule schedule = new ConfigStore(context).findSchedule(scheduleId);
        if (schedule != null && schedule.enabled && schedule.excludesHolidays()) {
            long now = System.currentTimeMillis();
            if (scheduledAt == 0L) scheduledAt = schedule.nextRunAt;
            if (scheduledAt > now || scheduledAt != schedule.nextRunAt) return;
            if (Recurrence.excludesAutomaticRun(schedule, scheduledAt, now, KoreanHolidaySync.cached(context))) {
                AlarmEngine.scheduleNext(context, schedule, now);
                return;
            }
        }

        QueueStore queueStore = new QueueStore(context);
        QueueStore.EnqueueResult result;
        try {
            result = queueStore.enqueue(scheduleId, false, scheduledAt);
        } catch (RuntimeException error) {
            return;
        }
        if (!result.added) return;

        Intent service = new Intent(context, ExecutionService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service); else context.startService(service);
        } catch (RuntimeException error) {
            queueStore.finish(result.runId);
        }
    }
}
