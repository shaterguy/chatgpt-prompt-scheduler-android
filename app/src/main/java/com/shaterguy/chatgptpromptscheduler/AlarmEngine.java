package com.shaterguy.chatgptpromptscheduler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import java.util.List;

public final class AlarmEngine {
    private AlarmEngine() {}

    public static void rebuildAll(Context context) {
        ConfigStore store = new ConfigStore(context);
        List<Schedule> schedules = store.loadSchedules();
        long now = System.currentTimeMillis();
        var published = KoreanHolidaySync.cached(context);
        for (Schedule schedule : schedules) {
            cancel(context, schedule.id);
            if (!schedule.enabled) {
                if (schedule.nextRunAt != 0L) {
                    schedule.nextRunAt = 0L;
                    store.saveSchedule(schedule);
                }
                continue;
            }
            if (schedule.nextRunAt > now
                    && !Recurrence.isExcludedAt(schedule, schedule.nextRunAt, published)) {
                scheduleAt(context, schedule, schedule.nextRunAt);
            } else {
                scheduleNext(context, schedule, now);
            }
        }
    }

    public static long scheduleNext(Context context, Schedule schedule, long after) {
        long when = schedule.excludesHolidays()
                ? Recurrence.nextRunAt(schedule, after, KoreanHolidaySync.cached(context))
                : Recurrence.nextRunAt(schedule, after);
        if (when == 0L) {
            cancel(context, schedule.id);
            schedule.nextRunAt = 0L;
            new ConfigStore(context).saveSchedule(schedule);
            return 0L;
        }
        return scheduleAt(context, schedule, when);
    }

    static void reconcileHolidayAlarms(Context context) {
        long now = System.currentTimeMillis();
        var published = KoreanHolidaySync.cached(context);
        for (Schedule schedule : new ConfigStore(context).loadSchedules()) {
            if (!schedule.enabled || !schedule.excludesHolidays() || schedule.nextRunAt <= now) continue;
            long next = Recurrence.nextRunAt(schedule, now, published);
            if (next != schedule.nextRunAt) scheduleNext(context, schedule, now);
        }
    }

    public static long scheduleAt(Context context, Schedule schedule, long when) {
        if (when <= 0L) return 0L;
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        PendingIntent pending = pending(context, schedule.id, when, PendingIntent.FLAG_UPDATE_CURRENT);
        boolean interval = "interval".equals(schedule.recurrence);
        int alarmType = interval ? AlarmManager.ELAPSED_REALTIME_WAKEUP : AlarmManager.RTC_WAKEUP;
        long trigger = interval
                ? SystemClock.elapsedRealtime() + Math.max(1L, when - System.currentTimeMillis())
                : when;
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(alarmType, trigger, pending);
        } else {
            manager.setExactAndAllowWhileIdle(alarmType, trigger, pending);
        }
        schedule.nextRunAt = when;
        new ConfigStore(context).saveSchedule(schedule);
        return when;
    }

    public static void cancel(Context context, String scheduleId) {
        PendingIntent pending = pending(context, scheduleId, 0L, PendingIntent.FLAG_NO_CREATE);
        if (pending != null) context.getSystemService(AlarmManager.class).cancel(pending);
    }

    private static PendingIntent pending(Context context, String scheduleId, long scheduledAt, int flags) {
        Intent intent = new Intent(context, AlarmReceiver.class).setAction("RUN_SCHEDULE")
                .putExtra("scheduleId", scheduleId).putExtra("scheduledAt", scheduledAt);
        return PendingIntent.getBroadcast(context, scheduleId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE | flags);
    }
}
