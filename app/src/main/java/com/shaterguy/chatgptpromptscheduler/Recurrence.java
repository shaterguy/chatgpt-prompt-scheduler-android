package com.shaterguy.chatgptpromptscheduler;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Recurrence {
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private Recurrence() {}

    public static long nextRunAt(Schedule schedule, long afterEpochMillis) {
        if (!schedule.enabled) return 0L;
        if ("interval".equals(schedule.recurrence)) {
            return afterEpochMillis + Schedule.normalizedIntervalMinutes(schedule.intervalMinutes) * 60_000L;
        }
        if (schedule.times.isEmpty()) return 0L;
        ZonedDateTime after = Instant.ofEpochMilli(afterEpochMillis).atZone(KST);
        List<ZonedDateTime> candidates = new ArrayList<>();
        int days = "once".equals(schedule.recurrence) ? 1 : 8;
        for (int offset = 0; offset < days; offset++) {
            LocalDate date = after.toLocalDate().plusDays(offset);
            int iso = date.getDayOfWeek().getValue();
            if ("weekly".equals(schedule.recurrence) && !schedule.weekdays.contains(iso)) continue;
            for (String value : schedule.times) {
                LocalTime time = LocalTime.parse(value);
                ZonedDateTime candidate = LocalDateTime.of(date, time).atZone(KST);
                if (candidate.toInstant().toEpochMilli() > afterEpochMillis) candidates.add(candidate);
            }
        }
        return candidates.stream().min(Comparator.naturalOrder()).map(v -> v.toInstant().toEpochMilli()).orElse(0L);
    }

    public static String describeNext(Schedule schedule, long now) {
        if (!schedule.enabled) return "다음 실행 없음";
        long next = schedule.nextRunAt > 0L ? schedule.nextRunAt : nextRunAt(schedule, now);
        if (next == 0L) return "다음 실행 없음";
        if ("interval".equals(schedule.recurrence)) {
            return Schedule.normalizedIntervalMinutes(schedule.intervalMinutes) + "분 간격 · 다음 "
                    + Instant.ofEpochMilli(next).atZone(KST).toLocalDateTime().toString().replace('T', ' ');
        }
        return Instant.ofEpochMilli(next).atZone(KST).toLocalDateTime().toString().replace('T', ' ');
    }

    public static DayOfWeek dayOfWeek(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(KST).getDayOfWeek();
    }
}
