package com.shaterguy.chatgptpromptscheduler;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Set;

public final class Recurrence {
    /** Saved edits invalidate older automatic occurrences, including already queued work. */
    static boolean isStaleAutomaticOccurrence(Schedule schedule, boolean manual, long scheduledAt) {
        return !manual && schedule != null && scheduledAt > 0L && scheduledAt != schedule.nextRunAt;
    }

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private Recurrence() {}

    public static long nextRunAt(Schedule schedule, long afterEpochMillis) {
        return nextRunAt(schedule, afterEpochMillis, Collections.emptySet());
    }

    static long nextRunAt(Schedule schedule, long afterEpochMillis, Set<LocalDate> publishedHolidays) {
        return nextRunAt(schedule, afterEpochMillis, publishedHolidays, false);
    }

    static long nextRunAfterEdit(Schedule schedule, long now, Set<LocalDate> publishedHolidays) {
        return nextRunAt(schedule, now, publishedHolidays, true);
    }

    private static long nextRunAt(Schedule schedule, long afterEpochMillis,
                                  Set<LocalDate> publishedHolidays, boolean edited) {
        if (!schedule.enabled) return 0L;
        if ("interval".equals(schedule.recurrence)) {
            long duration = Schedule.normalizedIntervalMinutes(schedule.intervalMinutes) * 60_000L;
            long anchor = schedule.lastRunAt > 0L ? schedule.lastRunAt : afterEpochMillis;
            long periods = afterEpochMillis >= anchor ? (afterEpochMillis - anchor) / duration + 1L : 1L;
            try { return Math.addExact(anchor, Math.multiplyExact(periods, duration)); }
            catch (ArithmeticException overflow) { return 0L; }
        }
        if (schedule.times.isEmpty()) return 0L;
        ZonedDateTime after = Instant.ofEpochMilli(afterEpochMillis).atZone(KST);
        if ("daily".equals(schedule.recurrence)) {
            return nextDaily(schedule, afterEpochMillis, after.toLocalDate(), publishedHolidays, edited);
        }
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

    private static long nextDaily(Schedule schedule, long after, LocalDate today,
                                  Set<LocalDate> holidays, boolean edited) {
        if (schedule.lastRunAt <= 0L) {
            for (int offset = 0; offset <= 366; offset++) {
                LocalDate date = today.plusDays(offset);
                if (schedule.excludesHolidays() && KoreanHolidays.isClosed(date, holidays)) continue;
                long next = timeOn(schedule, date, after);
                if (next > 0L) return next;
            }
            return 0L;
        }
        LocalDate completed = Instant.ofEpochMilli(schedule.lastRunAt).atZone(KST).toLocalDate();
        LocalDate occurrence = Instant.ofEpochMilli(schedule.lastOccurrenceAt > 0L
                ? schedule.lastOccurrenceAt : schedule.lastRunAt).atZone(KST).toLocalDate();
        // Normal completion may leave more times on this execution day. Explicit saves start a new interval.
        if (!edited && completed.equals(today) && occurrence.equals(completed)
                && !(schedule.excludesHolidays() && KoreanHolidays.isClosed(today, holidays))) {
            long remaining = timeOn(schedule, today, after);
            if (remaining > 0L) return remaining;
        }
        long step = Math.max(1, schedule.dailyIntervalDays);
        LocalDate due = completed.plusDays(step);
        long elapsedDays = ChronoUnit.DAYS.between(due, today);
        if (elapsedDays > 0L) due = due.plusDays((elapsedDays / step) * step);
        for (int attempts = 0; attempts <= 366; attempts++, due = due.plusDays(step)) {
            LocalDate date = nextOpenDate(schedule, due, holidays);
            if (date == null) return 0L;
            long next = timeOn(schedule, date, after);
            if (next > 0L) return next;
        }
        return 0L;
    }

    private static long timeOn(Schedule schedule, LocalDate date, long after) {
        long earliest = Long.MAX_VALUE;
        for (String value : schedule.times) {
            long candidate = date.atTime(LocalTime.parse(value)).atZone(KST).toInstant().toEpochMilli();
            if (candidate > after) earliest = Math.min(earliest, candidate);
        }
        return earliest == Long.MAX_VALUE ? 0L : earliest;
    }

    private static LocalDate nextOpenDate(Schedule schedule, LocalDate date, Set<LocalDate> holidays) {
        for (int offset = 0; offset <= 366; offset++, date = date.plusDays(1)) {
            if (!schedule.excludesHolidays() || !KoreanHolidays.isClosed(date, holidays)) return date;
        }
        return null;
    }

    /** Reconcile an already selected daily occurrence without reopening its completed day. */
    static long rollDailyOccurrence(Schedule schedule, long occurrenceAt, Set<LocalDate> holidays) {
        ZonedDateTime occurrence = Instant.ofEpochMilli(occurrenceAt).atZone(KST);
        LocalDate date = nextOpenDate(schedule, occurrence.toLocalDate(), holidays);
        return date == null ? 0L : date.atTime(occurrence.toLocalTime()).atZone(KST).toInstant().toEpochMilli();
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
            return schedule.intervalValue() + ("hours".equals(schedule.normalizedIntervalUnit()) ? "시간" : "분") + " 간격 · 다음 "
                    + Instant.ofEpochMilli(next).atZone(KST).toLocalDateTime().toString().replace('T', ' ');
        }
        String time = Instant.ofEpochMilli(next).atZone(KST).toLocalDateTime().toString().replace('T', ' ');
        if ("daily".equals(schedule.recurrence)) time = "매 " + Math.max(1, schedule.dailyIntervalDays) + "일 · " + time;
        return schedule.excludesHolidays() ? time + " · 공휴일 제외" : time;
    }

    public static DayOfWeek dayOfWeek(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(KST).getDayOfWeek();
    }
}
