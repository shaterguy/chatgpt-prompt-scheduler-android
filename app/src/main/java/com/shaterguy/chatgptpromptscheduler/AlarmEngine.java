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
        for (Schedule schedule : schedules) {
            cancel(context, schedule.id);
            if (!schedule.enabled) {
                if (schedule.nextRunAt != 0L) {
                    schedule.nextRunAt = 0L;
                    store.saveSchedule(schedule);
                }
                continue;
            }
            if (schedule.nextRunAt > now) {
                scheduleAt(context, schedule, schedule.nextRunAt);
            } else {
                scheduleNext(context, schedule, now);
            }
        }
    }

    public static long scheduleNext(Context context, Schedule schedule, long after) {
        long when = Recurrence.nextRunAt(schedule, after);
        if (when == 0L) {
            schedule.nextRunAt = 0L;
            new ConfigStore(context).saveSchedule(schedule);
            return 0L;
        }
        return scheduleAt(context, schedule, when);
    }

    public static long scheduleAt(Context context, Schedule schedule, long when) {
        if (when <= 0L) return 0L;
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        PendingIntent pending = pending(context, schedule.id);
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
        context.getSystemService(AlarmManager.class).cancel(pending(context, scheduleId));
    }

    private static PendingIntent pending(Context context, String scheduleId) {
        Intent intent = new Intent(context, AlarmReceiver.class).setAction("RUN_SCHEDULE").putExtra("scheduleId", scheduleId);
        return PendingIntent.getBroadcast(context, scheduleId.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
