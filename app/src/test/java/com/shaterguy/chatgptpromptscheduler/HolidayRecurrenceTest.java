package com.shaterguy.chatgptpromptscheduler;

import org.json.JSONObject;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HolidayRecurrenceTest {
    @Test public void dailyPreservesConfiguredTimesAfterWeekendAndSubstituteHoliday() {
        Schedule schedule = new Schedule();
        schedule.excludeHolidays = true;
        schedule.times.clear();
        schedule.times.addAll(List.of("17:00", "09:00"));
        assertEquals(at("2027-07-20T09:00"), Recurrence.nextRunAt(schedule, at("2027-07-16T17:00")));
        assertEquals(at("2027-07-20T17:00"), Recurrence.nextRunAt(schedule, at("2027-07-20T09:00")));
    }

    @Test public void weeklySkipsHolidaysWithoutChangingTheSelectedDay() {
        Schedule schedule = new Schedule();
        schedule.recurrence = "weekly";
        schedule.excludeHolidays = true;
        schedule.weekdays.clear();
        schedule.weekdays.add(1);
        Set<LocalDate> published = KoreanHolidays.dates("20270726");
        assertEquals(at("2027-08-02T09:00"),
                Recurrence.nextRunAt(schedule, at("2027-07-16T17:00"), published));
    }

    @Test public void weeklyWeekendSelectionHasNoEligibleOccurrence() {
        Schedule schedule = new Schedule();
        schedule.recurrence = "weekly";
        schedule.excludeHolidays = true;
        schedule.weekdays.clear();
        schedule.weekdays.addAll(List.of(6, 7));
        assertEquals(0L, Recurrence.nextRunAt(schedule, at("2026-11-11T08:00")));
    }

    @Test public void coversElectionTemporaryHolidayAndYearBoundary() {
        Schedule schedule = new Schedule();
        schedule.excludeHolidays = true;
        assertEquals(at("2025-01-31T09:00"), Recurrence.nextRunAt(schedule, at("2025-01-24T09:00")));
        assertEquals(at("2026-06-04T09:00"), Recurrence.nextRunAt(schedule, at("2026-06-02T09:00")));
        assertEquals(at("2027-01-04T09:00"), Recurrence.nextRunAt(schedule, at("2026-12-31T09:00")));
    }

    @Test public void delayedAutomaticRunChecksBothKoreanCalendarDates() {
        Schedule schedule = new Schedule();
        schedule.excludeHolidays = true;
        schedule.nextRunAt = at("2026-12-24T23:59");
        assertTrue(Recurrence.excludesAutomaticRun(schedule, schedule.nextRunAt,
                at("2026-12-25T00:01"), Set.of()));
        schedule.nextRunAt = at("2026-12-25T23:59");
        assertTrue(Recurrence.excludesAutomaticRun(schedule, schedule.nextRunAt,
                at("2026-12-28T00:01"), Set.of()));
    }

    @Test public void preferenceRoundTripsAndLegacySchedulesKeepTheirDefault() throws Exception {
        Schedule schedule = Schedule.fromJson(new JSONObject());
        assertFalse(schedule.excludeHolidays);
        assertEquals(at("2026-12-25T09:00"), Recurrence.nextRunAt(schedule, at("2026-12-24T09:00")));
        schedule.excludeHolidays = true;
        assertTrue(Schedule.fromJson(schedule.toJson()).excludesHolidays());
        schedule.recurrence = "weekly";
        assertTrue(Schedule.fromJson(schedule.toJson()).excludesHolidays());
        schedule.recurrence = "once";
        assertFalse(Schedule.fromJson(schedule.toJson()).excludesHolidays());
        assertEquals(at("2026-12-25T09:00"), Recurrence.nextRunAt(schedule, at("2026-12-25T08:00")));
        schedule.recurrence = "interval";
        assertFalse(Schedule.fromJson(schedule.toJson()).excludesHolidays());
        assertEquals(at("2026-12-25T08:30"), Recurrence.nextRunAt(schedule, at("2026-12-25T08:00")));
    }

    @Test public void publicFeedIncludesAllDayRangesAndExcludesObservancesAndCancellations() {
        String feed = String.join("\r\n",
                "BEGIN:VCALENDAR",
                "BEGIN:VEVENT", "DTSTART;VALUE=DATE:20261112", "DTEND;VALUE=DATE:20261114",
                "DESCRIPTION:공", " 휴일", "END:VEVENT",
                "BEGIN:VEVENT", "DTSTART;VALUE=DATE:20261116", "DTEND;VALUE=DATE:20261117",
                "DESCRIPTION:기념일", "END:VEVENT",
                "BEGIN:VEVENT", "DTSTART;VALUE=DATE:20261117", "DTEND;VALUE=DATE:20261118",
                "DESCRIPTION:공휴일", "STATUS:CANCELLED", "END:VEVENT", "END:VCALENDAR");
        assertEquals(KoreanHolidays.dates("20261112 20261113"), KoreanHolidays.parsePublicHolidayCalendar(feed));
    }

    private static long at(String time) {
        return LocalDateTime.parse(time).atZone(Recurrence.KST).toInstant().toEpochMilli();
    }
}
