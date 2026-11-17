package com.shaterguy.chatgptpromptscheduler;

import org.json.JSONObject;
import org.junit.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public final class IntervalDailyRecurrenceTest {
    @Test public void intervalUnitsRoundTripWithoutLosingLegacyMinutes() throws Exception {
        Schedule legacy = Schedule.fromJson(new JSONObject().put("intervalMinutes", 90));
        assertEquals("minutes", legacy.normalizedIntervalUnit());
        assertEquals(90, legacy.intervalValue());
        assertEquals(1, legacy.dailyIntervalDays);
        legacy.intervalUnit = "hours";
        assertEquals("minutes", Schedule.fromJson(legacy.toJson()).intervalUnit);
        legacy.intervalMinutes = Schedule.parseIntervalMinutes("2", "hours");
        Schedule restored = Schedule.fromJson(legacy.toJson());
        assertEquals(120, restored.intervalMinutes);
        assertEquals("hours", restored.intervalUnit);
        assertEquals(2, restored.intervalValue());
        assertEquals(10080, Schedule.parseIntervalMinutes("168", "hours"));
    }

    @Test public void invalidIntervalsAreRejectedBeforeConversionOrSaving() {
        for (String value : List.of("", "0", "-1", "14", "10081", "999999999999999999999")) {
            assertThrows(IllegalArgumentException.class, () -> Schedule.parseIntervalMinutes(value, "minutes"));
        }
        for (String value : List.of("", "0", "-1", "0.5", "169", "9223372036854775807")) {
            assertThrows(IllegalArgumentException.class, () -> Schedule.parseIntervalMinutes(value, "hours"));
        }
        for (String value : List.of("", "0", "-1", "1.5", "2147483648")) {
            assertThrows(IllegalArgumentException.class, () -> Schedule.parseDailyIntervalDays(value));
        }
        assertEquals(Integer.MAX_VALUE, Schedule.parseDailyIntervalDays("2147483647"));
    }

    @Test public void intervalUsesActualCompletionForSuccessFailureAndSave() {
        Schedule s = new Schedule();
        s.recurrence = "interval";
        s.intervalMinutes = 120;
        s.intervalUnit = "hours";
        s.lastRunAt = at("2026-11-17T10:10");
        assertEquals(at("2026-11-17T12:10"), edit(s, "2026-11-17T11:00"));
        assertEquals(at("2026-11-17T12:10"), Recurrence.nextRunAt(s, s.lastRunAt + 1000L));
        ConfigStore.mergeRunCompletion(s, at("2026-11-17T11:20"), "NETWORK_ERROR_LOG_SAVE_FAILED", false);
        assertEquals(at("2026-11-17T13:20"), edit(s, "2026-11-17T12:00"));
    }

    @Test public void overdueIntervalSelectsFirstFutureAnchoredOccurrence() {
        Schedule s = new Schedule();
        s.recurrence = "interval";
        s.intervalMinutes = 120;
        s.lastRunAt = at("2026-11-17T10:10");
        assertEquals(at("2026-11-17T16:10"), edit(s, "2026-11-17T14:10"));
        assertEquals(at("2026-11-17T16:10"), edit(s, "2026-11-17T15:00"));
        s.lastRunAt = 0;
        assertEquals(at("2026-11-17T17:00"), edit(s, "2026-11-17T15:00"));
    }

    @Test public void newDailyStartsAtFirstFutureTimeEvenWithThreeDayInterval() {
        Schedule s = daily(3, false);
        assertEquals(at("2026-11-17T09:00"), edit(s, "2026-11-17T08:00"));
        assertEquals(at("2026-11-18T09:00"), edit(s, "2026-11-17T09:00"));
        assertEquals(at("2026-11-18T09:00"), edit(s, "2026-11-17T22:00"));
        s.excludeHolidays = true;
        assertEquals(at("2026-11-16T09:00"), edit(s, "2026-11-13T22:00"));
    }

    @Test public void calendarDaysRollForwardToNextAllowedDate() {
        Schedule s = daily(3, true);
        complete(s, "2026-11-13T09:01");
        assertEquals(at("2026-11-16T09:00"), edit(s, "2026-11-13T10:00"));
        complete(s, "2026-11-11T09:01");
        assertEquals(at("2026-11-16T09:00"), edit(s, "2026-11-11T10:00"));
        complete(s, "2026-11-10T09:01");
        Set<LocalDate> holidays = KoreanHolidays.dates("20261113 20261116");
        assertEquals(at("2026-11-17T09:00"), Recurrence.nextRunAfterEdit(s, at("2026-11-10T10:00"), holidays));
        complete(s, "2026-11-17T09:01");
        assertEquals(at("2026-11-20T09:00"), Recurrence.nextRunAt(s, s.lastRunAt + 1000L, holidays));
    }

    @Test public void intervalIsCalendarBasedWhenHolidayExclusionIsOff() {
        Schedule s = daily(3, false);
        complete(s, "2026-11-11T09:01");
        assertEquals(at("2026-11-14T09:00"), edit(s, "2026-11-11T10:00"));
    }

    @Test public void normalRunKeepsRemainingTimesButEditStartsNextDayInterval() {
        Schedule s = daily(3, false);
        s.times.add("23:00");
        complete(s, "2026-11-17T09:01");
        assertEquals(at("2026-11-17T23:00"), Recurrence.nextRunAt(s, s.lastRunAt + 1000L));
        assertEquals(at("2026-11-20T09:00"), edit(s, "2026-11-17T10:00"));
        s.nextRunAt = edit(s, "2026-11-17T10:00");
        assertEquals(at("2026-11-20T09:00"), Recurrence.effectiveNextRunAt(s, at("2026-11-17T12:00")));
    }

    @Test public void overnightCompletionDoesNotStartAnotherSameDayBatch() {
        Schedule s = daily(3, false);
        s.times.add("23:00");
        ConfigStore.mergeRunCompletion(s, at("2026-11-17T00:01"), "VERIFIED", false, at("2026-11-16T23:00"));
        assertEquals(at("2026-11-20T09:00"), Recurrence.nextRunAt(s, s.lastRunAt + 1000L));
    }

    @Test public void overdueDailyKeepsCalendarGridAndDoesNotReplayPastRuns() {
        Schedule s = daily(3, false);
        complete(s, "2026-11-11T09:01");
        assertEquals(at("2026-11-20T09:00"), edit(s, "2026-11-18T12:00"));
        s.excludeHolidays = true;
        assertEquals(at("2026-11-16T09:00"), edit(s, "2026-11-15T12:00"));
        assertEquals(at("2026-11-17T09:00"), edit(s, "2026-11-16T12:00"));
    }

    @Test public void holidayRefreshPreservesEditedFutureOccurrenceAndClockTime() {
        Schedule s = daily(3, true);
        complete(s, "2026-11-17T09:01");
        s.nextRunAt = edit(s, "2026-11-17T10:00");
        assertEquals(s.nextRunAt, Recurrence.rollDailyOccurrence(s, s.nextRunAt, Set.of()));
        assertEquals(at("2026-11-23T09:00"), Recurrence.rollDailyOccurrence(s, s.nextRunAt, KoreanHolidays.dates("20261120")));
        assertEquals(at("2026-11-17T09:01"), s.lastRunAt);
    }

    @Test public void dailyRoundTripRetainsAnchorAndSupportsLongIntervals() throws Exception {
        Schedule s = daily(3, true);
        complete(s, "2026-12-30T09:01");
        assertEquals(at("2027-01-04T09:00"), edit(s, "2026-12-30T10:00"));
        Schedule restored = Schedule.fromJson(s.toJson());
        assertEquals(3, restored.dailyIntervalDays);
        assertEquals(s.lastRunAt, restored.lastRunAt);
        assertEquals(s.lastOccurrenceAt, restored.lastOccurrenceAt);
        assertEquals(edit(s, "2026-12-30T10:00"), edit(restored, "2026-12-30T10:00"));
        s.excludeHolidays = false;
        s.dailyIntervalDays = Integer.MAX_VALUE;
        assertTrue(edit(s, "2026-12-30T10:00") > s.lastRunAt);
    }

    @Test public void staleEditorKeepsNewCompletionAndUnchangedEnabledToggle() {
        Schedule incoming = daily(3, false);
        incoming.enabled = true;
        Schedule latest = daily(1, false);
        latest.enabled = false;
        complete(latest, "2026-11-17T10:10");
        latest.lastStatus = "FAILED";
        ConfigStore.mergeEditedRuntime(incoming, latest, true);
        assertEquals(latest.lastRunAt, incoming.lastRunAt);
        assertEquals("FAILED", incoming.lastStatus);
        assertEquals(3, incoming.dailyIntervalDays);
        assertFalse(incoming.enabled);
        incoming.enabled = true;
        ConfigStore.mergeEditedRuntime(incoming, latest, false);
        assertTrue(incoming.enabled);
        assertEquals(at("2026-11-20T09:00"), edit(incoming, "2026-11-17T11:00"));
    }

    @Test public void labelsAndVisibilityExposeBothNewControls() {
        assertEquals("간격", UiDisplayPolicy.label("interval"));
        assertEquals("시간", UiDisplayPolicy.label("hours"));
        assertEquals("분", UiDisplayPolicy.label("minutes"));
        assertTrue(ScheduleEditorActivity.showsDailyInterval("daily"));
        assertFalse(ScheduleEditorActivity.showsDailyInterval("weekly"));
        assertFalse(ScheduleEditorActivity.showsDailyInterval("interval"));
    }

    @Test public void editedAutomaticOccurrenceRejectsOldQueueRegardlessOfHolidaySetting() {
        for (String recurrence : List.of("interval", "daily", "weekly", "once")) {
            Schedule schedule = new Schedule();
            schedule.recurrence = recurrence;
            schedule.nextRunAt = at("2026-11-20T09:00");
            for (boolean excludeHolidays : List.of(false, true)) {
                schedule.excludeHolidays = excludeHolidays;
                assertTrue(Recurrence.isStaleAutomaticOccurrence(schedule, false, at("2026-11-17T09:00")));
                assertFalse(Recurrence.isStaleAutomaticOccurrence(schedule, false, schedule.nextRunAt));
                assertFalse(Recurrence.isStaleAutomaticOccurrence(schedule, true, at("2026-11-17T09:00")));
                assertFalse(Recurrence.isStaleAutomaticOccurrence(schedule, false, 0L));
            }
            long completedOccurrence = schedule.nextRunAt;
            schedule.nextRunAt = 0L;
            assertTrue(Recurrence.isStaleAutomaticOccurrence(schedule, false, completedOccurrence));
        }
    }

    private static Schedule daily(int days, boolean holidays) {
        Schedule s = new Schedule();
        s.dailyIntervalDays = days;
        s.excludeHolidays = holidays;
        return s;
    }
    private static void complete(Schedule s, String time) {
        s.lastRunAt = at(time);
        s.lastOccurrenceAt = s.lastRunAt;
        s.lastStatus = "VERIFIED";
    }
    private static long edit(Schedule s, String time) { return Recurrence.nextRunAfterEdit(s, at(time), Set.of()); }
    private static long at(String time) { return LocalDateTime.parse(time).atZone(Recurrence.KST).toInstant().toEpochMilli(); }
}
