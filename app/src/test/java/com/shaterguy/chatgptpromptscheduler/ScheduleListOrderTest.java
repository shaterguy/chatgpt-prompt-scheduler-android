package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class ScheduleListOrderTest {
    private Schedule item(String name, String type, String url, long due, boolean enabled) {
        Schedule schedule = new Schedule();
        schedule.name = name;
        schedule.targetType = type;
        schedule.targetUrl = url;
        schedule.nextRunAt = due;
        schedule.enabled = enabled;
        return schedule;
    }

    @Test public void newestOrderReversesInsertionWithoutMutatingStore() {
        Schedule a = item("older", "general", "https://chatgpt.com", 1, true);
        Schedule b = item("newer", "general", "https://chatgpt.com", 2, true);
        List<Schedule> source = Arrays.asList(a, b);
        assertEquals(Arrays.asList(b, a), ScheduleListOrder.visible(source, "", 0, 10));
        assertEquals(Arrays.asList(a, b), source);
    }

    @Test public void filterUsesProjectIdAndKeepsNonprojectSchedulesAccessible() {
        String one = "https://chatgpt.com/g/g-p-first/project";
        String two = "https://chatgpt.com/g/g-p-second/project";
        Schedule a = item("A", "project", one, 1, true);
        Schedule b = item("B", "project", two, 2, true);
        Schedule c = item("C", "general", "https://chatgpt.com", 3, true);
        Schedule d = item("D", "existing", "https://chatgpt.com/c/abc", 4, true);
        List<Schedule> source = Arrays.asList(a, b, c, d);
        assertEquals(Arrays.asList(a), ScheduleListOrder.visible(source, "g-p-first", 1, 10));
        assertEquals(Arrays.asList(b), ScheduleListOrder.visible(source, "g-p-second", 1, 10));
        assertEquals(Arrays.asList(c, d), ScheduleListOrder.visible(source, "#nonproject", 1, 10));
        assertEquals(4, ScheduleListOrder.visible(source, "", 1, 10).size());
    }

    @Test public void dueSortPlacesDisabledLastAndKeepsTiesStable() {
        Schedule later = item("later", "general", "", 200, true);
        Schedule disabled = item("disabled", "general", "", 5, false);
        Schedule earlier = item("earlier", "general", "", 100, true);
        Schedule tie = item("tie", "general", "", 100, true);
        assertEquals(Arrays.asList(earlier, tie, later, disabled), ScheduleListOrder.visible(
                Arrays.asList(later, disabled, earlier, tie), "", 1, 10));
    }
}
