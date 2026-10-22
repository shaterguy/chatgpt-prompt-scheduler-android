package com.shaterguy.chatgptpromptscheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Display-only filter and sorting; persisted schedule order and data are unchanged. */
final class ScheduleListOrder {
    private ScheduleListOrder() {}

    static ProjectUrlPolicy.ProjectRef projectOf(Schedule schedule) {
        return "project".equals(schedule.targetType)
                ? ProjectUrlPolicy.parseProject(schedule.targetUrl) : null;
    }

    static List<Schedule> visible(List<Schedule> schedules, String projectId, int mode, long now) {
        List<Schedule> result = new ArrayList<>();
        for (Schedule schedule : schedules) {
            ProjectUrlPolicy.ProjectRef ref = projectOf(schedule);
            if ("#nonproject".equals(projectId) ? ref != null
                    : !projectId.isEmpty() && (ref == null || !projectId.equals(ref.projectId))) continue;
            result.add(schedule);
        }
        if (mode == 1) {
            result.sort(Comparator.comparingLong(schedule -> due(schedule, now)));
        } else {
            java.util.Collections.reverse(result);
        }
        return result;
    }

    private static long due(Schedule schedule, long now) {
        if (!schedule.enabled) return Long.MAX_VALUE;
        long value = schedule.nextRunAt > 0 ? schedule.nextRunAt : Recurrence.nextRunAt(schedule, now);
        return value > 0 ? value : Long.MAX_VALUE;
    }
}
