package com.shaterguy.chatgptpromptscheduler;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Set;

public final class Recurrence {
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private Recurrence() {}

    public static long nextRunAt(Schedule schedule, long afterEpochMillis) {
        return nextRunAt(schedule, afterEpochMillis, Collections.emptySet());
    }

    static long nextRunAt(Schedule schedule, long afterEpochMillis, Set<LocalDate> publishedHolidays) {
        if (!schedule.enabled) return 0L;
        if ("interval".equals(schedule.recurrence)) {
            return afterEpochMillis + Schedule.normalizedIntervalMinutes(schedule.intervalMinutes) * 60_000L;
        }
        if (schedule.times.isEmpty()) return 0L;
        ZonedDateTime after = Instant.ofEpochMilli(afterEpochMillis).atZone(KST);
        boolean excludesHolidays = schedule.excludesHolidays();
        int days = "once".equals(schedule.recurrence) ? 1 : excludesHolidays ? 366 : 8;
        for (int offset = 0; offset < days; offset++) {
            LocalDate date = after.toLocalDate().plusDays(offset);
            int iso = date.getDayOfWeek().getValue();
            if ("weekly".equals(schedule.recurrence) && !schedule.weekdays.contains(iso)) continue;
            if (excludesHolidays && KoreanHolidays.isClosed(date, publishedHolidays)) continue;
            long earliest = Long.MAX_VALUE;
            for (String value : schedule.times) {
                LocalTime time = LocalTime.parse(value);
                long candidate = LocalDateTime.of(date, time).atZone(KST).toInstant().toEpochMilli();
                if (candidate > afterEpochMillis) earliest = Math.min(earliest, candidate);
            }
            if (earliest != Long.MAX_VALUE) return earliest;
        }
        return 0L;
    }

    static boolean isExcludedAt(Schedule schedule, long epochMillis, Set<LocalDate> publishedHolidays) {
        return schedule.excludesHolidays() && KoreanHolidays.isClosed(
                Instant.ofEpochMilli(epochMillis).atZone(KST).toLocalDate(), publishedHolidays);
    }

    static boolean excludesAutomaticRun(Schedule schedule, long scheduledAt, long now,
                                        Set<LocalDate> publishedHolidays) {
        return (scheduledAt > 0L && isExcludedAt(schedule, scheduledAt, publishedHolidays))
                || isExcludedAt(schedule, now, publishedHolidays);
    }

    /** Shared next-run display target: persisted alarm times take precedence over recomputation. */
    public static long effectiveNextRunAt(Schedule schedule, long now) {
        if (!schedule.enabled) return 0L;
        return schedule.nextRunAt > 0L ? schedule.nextRunAt : nextRunAt(schedule, now);
    }

    public static String describeNext(Schedule schedule, long now) {
        long next = effectiveNextRunAt(schedule, now);
        if (next == 0L) return "다음 실행 없음";
        if ("interval".equals(schedule.recurrence)) {
            return Schedule.normalizedIntervalMinutes(schedule.intervalMinutes) + "분 간격 · 다음 "
                    + Instant.ofEpochMilli(next).atZone(KST).toLocalDateTime().toString().replace('T', ' ');
        }
        String time = Instant.ofEpochMilli(next).atZone(KST).toLocalDateTime().toString().replace('T', ' ');
        return schedule.excludesHolidays() ? time + " · 공휴일 제외" : time;
    }

    public static DayOfWeek dayOfWeek(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(KST).getDayOfWeek();
    }
}
