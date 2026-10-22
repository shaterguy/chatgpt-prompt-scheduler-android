package com.shaterguy.chatgptpromptscheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only view policy; original ConfigStore schedule order is never changed. */
final class ScheduleListPolicy {
    static final String ALL = "all";
    static final String UNASSIGNED = "unassigned";
    static final String UNKNOWN = "unknown";
    static final String PROJECT_PREFIX = "project:";
    static final String NEWEST = "newest";
    static final String DUE = "due";

    static final class Filter {
        final String key;
        final String label;
        Filter(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    private static final class IndexedSchedule {
        final Schedule schedule;
        final int originalIndex;
        final long due;
        IndexedSchedule(Schedule schedule, int originalIndex, long now) {
            this.schedule = schedule;
            this.originalIndex = originalIndex;
            this.due = Recurrence.effectiveNextRunAt(schedule, now);
        }
    }

    private ScheduleListPolicy() {}

    static String membership(Schedule schedule) {
        if (!"project".equals(schedule.targetType) && !"existing".equals(schedule.targetType)) return UNASSIGNED;
        String projectId = TargetParser.projectId(schedule.targetUrl);
        if (projectId != null && !projectId.isEmpty()) return PROJECT_PREFIX + projectId;
        return "project".equals(schedule.targetType) ? UNKNOWN : UNASSIGNED;
    }

    static List<Filter> filters(List<Schedule> schedules, Map<String, String> catalogNames) {
        Map<String, String> names = new LinkedHashMap<>();
        names.putAll(catalogNames);
        boolean unknown = false;
        for (Schedule schedule : schedules) {
            String membership = membership(schedule);
            if (membership.startsWith(PROJECT_PREFIX)) {
                names.putIfAbsent(membership.substring(PROJECT_PREFIX.length()), "");
            } else if (UNKNOWN.equals(membership)) {
                unknown = true;
            }
        }

        Map<String, Integer> labelCounts = new HashMap<>();
        for (Map.Entry<String, String> entry : names.entrySet()) {
            String name = entry.getValue();
            if (name != null && !name.isBlank()) labelCounts.merge(name, 1, Integer::sum);
        }
        List<Filter> result = new ArrayList<>();
        result.add(new Filter(ALL, "전체"));
        result.add(new Filter(UNASSIGNED, "프로젝트 없음"));
        List<Filter> projects = new ArrayList<>();
        for (Map.Entry<String, String> entry : names.entrySet()) {
            String id = entry.getKey();
            String name = entry.getValue();
            String label = name == null || name.isBlank() ? "저장된 프로젝트 · " + id : name;
            if (labelCounts.getOrDefault(name, 0) > 1) label += " · " + id;
            projects.add(new Filter(PROJECT_PREFIX + id, label));
        }
        projects.sort(Comparator.comparing((Filter filter) -> filter.label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(filter -> filter.key));
        result.addAll(projects);
        if (unknown) result.add(new Filter(UNKNOWN, "프로젝트 미확인"));
        return result;
    }

    static boolean hasFilter(List<Filter> filters, String key) {
        for (Filter filter : filters) if (filter.key.equals(key)) return true;
        return false;
    }

    static List<Schedule> visible(List<Schedule> original, String filter, String sort, long now) {
        List<IndexedSchedule> entries = new ArrayList<>();
        for (int i = 0; i < original.size(); i++) {
            Schedule schedule = original.get(i);
            if (ALL.equals(filter) || membership(schedule).equals(filter)) {
                entries.add(new IndexedSchedule(schedule, i, now));
            }
        }
        entries.sort((left, right) -> {
            if (DUE.equals(sort)) {
                if (left.due == 0L && right.due != 0L) return 1;
                if (left.due != 0L && right.due == 0L) return -1;
                if (left.due != 0L) {
                    int byDue = Long.compare(left.due, right.due);
                    if (byDue != 0) return byDue;
                }
            }
            return Integer.compare(right.originalIndex, left.originalIndex);
        });
        List<Schedule> result = new ArrayList<>(entries.size());
        for (IndexedSchedule entry : entries) result.add(entry.schedule);
        return result;
    }
}
