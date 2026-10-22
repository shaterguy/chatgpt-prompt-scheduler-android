package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public final class ScheduleListPolicyTest {
    private static final long NOW = LocalDateTime.of(2026, 10, 22, 10, 0)
            .atZone(Recurrence.KST).toInstant().toEpochMilli();

    private static Schedule target(String type, String url) {
        Schedule schedule = new Schedule();
        schedule.targetType = type;
        schedule.targetUrl = url;
        return schedule;
    }

    @Test public void groupsProjectAndExistingConversationWithoutGuessingConversationId() {
        String id = "g-p-0123456789abcdef0123456789abcdef";
        Schedule general = target("general", "https://chatgpt.com/");
        Schedule project = target("project", "https://chatgpt.com/g/" + id + "-my-project/project");
        Schedule existingProject = target("existing", "https://chatgpt.com/g/" + id + "/c/conv");
        Schedule existingPlain = target("existing", "https://chatgpt.com/c/" + id);
        Schedule malformed = target("project", "not-an-url");
        assertEquals(ScheduleListPolicy.PROJECT_PREFIX + id, ScheduleListPolicy.membership(project));
        assertEquals(ScheduleListPolicy.PROJECT_PREFIX + id, ScheduleListPolicy.membership(existingProject));
        assertEquals(ScheduleListPolicy.UNASSIGNED, ScheduleListPolicy.membership(general));
        assertEquals(ScheduleListPolicy.UNASSIGNED, ScheduleListPolicy.membership(existingPlain));
        assertEquals(ScheduleListPolicy.UNKNOWN, ScheduleListPolicy.membership(malformed));
        List<Schedule> original = List.of(general, project, existingProject, existingPlain, malformed);
        assertEquals(List.of(existingProject, project),
                ScheduleListPolicy.visible(original, ScheduleListPolicy.PROJECT_PREFIX + id, ScheduleListPolicy.NEWEST, NOW));
        assertEquals(List.of(existingPlain, general),
                ScheduleListPolicy.visible(original, ScheduleListPolicy.UNASSIGNED, ScheduleListPolicy.NEWEST, NOW));
        assertEquals(5, original.size());
    }

    @Test public void catalogUnionPreservesOrphanProjectsAndDisambiguatesNames() {
        Schedule orphan = target("project", "https://chatgpt.com/g/g-p-orphan/project");
        Schedule malformed = target("project", "not-a-url");
        Map<String, String> catalog = new LinkedHashMap<>();
        catalog.put("g-p-first", "중복 이름");
        catalog.put("g-p-second", "중복 이름");
        catalog.put("g-p-empty", "비어 있는 프로젝트");
        List<ScheduleListPolicy.Filter> options = ScheduleListPolicy.filters(List.of(orphan, malformed), catalog);
        assertTrue(ScheduleListPolicy.hasFilter(options, ScheduleListPolicy.ALL));
        assertTrue(ScheduleListPolicy.hasFilter(options, ScheduleListPolicy.PROJECT_PREFIX + "g-p-orphan"));
        assertTrue(ScheduleListPolicy.hasFilter(options, ScheduleListPolicy.PROJECT_PREFIX + "g-p-empty"));
        assertTrue(ScheduleListPolicy.hasFilter(options, ScheduleListPolicy.UNKNOWN));
        String labelFirst = options.stream().filter(o -> o.key.equals("project:g-p-first")).findFirst().orElseThrow().label;
        String labelSecond = options.stream().filter(o -> o.key.equals("project:g-p-second")).findFirst().orElseThrow().label;
        assertNotEquals(labelFirst, labelSecond);
        assertEquals(List.of(), ScheduleListPolicy.visible(List.of(orphan), "project:g-p-empty", "newest", NOW));
    }

    @Test public void newestRegistrationOrderIsIndependentFromViewOrScheduleMutation() {
        Schedule first = target("general", "https://chatgpt.com/");
        Schedule second = target("general", "https://chatgpt.com/");
        Schedule third = target("general", "https://chatgpt.com/");
        ArrayList<Schedule> loaded = new ArrayList<>(List.of(first, second, third));
        assertEquals(List.of(third, second, first),
                ScheduleListPolicy.visible(loaded, "all", "newest", NOW));
        second.lastStatus = "SUCCESS";
        second.enabled = false;
        assertEquals(List.of(third, second, first),
                ScheduleListPolicy.visible(loaded, "all", "newest", NOW));
        assertEquals(List.of(first, second, third), loaded);
    }

    @Test public void dueOrderUsesPersistedTargetsAndPutsDisabledAndExpiredLast() {
        Schedule later = target("general", "https://chatgpt.com/");
        later.recurrence = "interval";
        later.nextRunAt = NOW + 120_000L;
        Schedule earlier = target("general", "https://chatgpt.com/");
        earlier.recurrence = "interval";
        earlier.nextRunAt = NOW - 60_000L; // overdue is still the persisted target
        Schedule disabled = target("general", "https://chatgpt.com/");
        disabled.enabled = false;
        disabled.nextRunAt = NOW - 120_000L;
        Schedule expired = target("general", "https://chatgpt.com/");
        expired.recurrence = "once";
        expired.times.clear();
        expired.times.add("01:00");
        List<Schedule> schedules = List.of(later, earlier, disabled, expired);
        assertEquals(List.of(earlier, later, expired, disabled),
                ScheduleListPolicy.visible(schedules, "all", "due", NOW));
        assertEquals(0L, Recurrence.effectiveNextRunAt(disabled, NOW));
        assertEquals("다음 실행 없음", Recurrence.describeNext(disabled, NOW));
        assertEquals(earlier.nextRunAt, Recurrence.effectiveNextRunAt(earlier, NOW + 300_000L));
        assertEquals(List.of(earlier, later, expired, disabled),
                ScheduleListPolicy.visible(schedules, "all", "due", NOW + 300_000L));
    }

    @Test public void equalDueAndNoNextBreakTiesByReverseRegistrationIndex() {
        Schedule older = target("general", "https://chatgpt.com/");
        older.nextRunAt = NOW + 50_000L;
        Schedule newer = target("general", "https://chatgpt.com/");
        newer.nextRunAt = NOW + 50_000L;
        Schedule offOld = target("general", "https://chatgpt.com/");
        offOld.enabled = false;
        Schedule offNew = target("general", "https://chatgpt.com/");
        offNew.enabled = false;
        assertEquals(List.of(newer, older, offNew, offOld),
                ScheduleListPolicy.visible(List.of(older, newer, offOld, offNew), "all", "due", NOW));
    }
}
