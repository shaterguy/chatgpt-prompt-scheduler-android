package com.shaterguy.chatgptpromptscheduler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class Schedule {
    public static final int MIN_INTERVAL_MINUTES = 15;
    public static final int MAX_INTERVAL_MINUTES = 10_080;

    public String id = UUID.randomUUID().toString();
    public String name = "새 예약";
    public String targetType = "general";
    public String targetUrl = "https://chatgpt.com/";
    public String experience = "chat";
    public String workModel = "inherit";
    public String reasoningEffort = "inherit";
    public String prompt = "";
    public String recurrence = "daily";
    public int intervalMinutes = 30;
    public final List<Integer> weekdays = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7));
    public final List<String> times = new ArrayList<>(List.of("09:00"));
    public boolean enabled = true;
    public int retryCount = 2;
    public long lastRunAt = 0L;
    public long nextRunAt = 0L;
    public String lastStatus = "NEVER";

    public static String normalizedExperience(String targetType, String experience) {
        if ("existing".equals(targetType)) return "inherit";
        return "work".equals(experience) ? "work" : "chat";
    }

    public static String normalizedReasoningEffort(String experience, String reasoningEffort) {
        if (!"work".equals(experience)) return "inherit";
        return switch (reasoningEffort) {
            case "light", "medium", "high", "xhigh", "max", "ultra" -> reasoningEffort;
            default -> "inherit";
        };
    }

    public static String displayReasoningEffort(String experience, String reasoningEffort) {
        String normalized = normalizedReasoningEffort(experience, reasoningEffort);
        return "ultra".equals(normalized) ? "울트라" : normalized;
    }

    public static String normalizedWorkModel(String experience, String workModel) {
        if (!"work".equals(experience)) return "inherit";
        return switch (workModel) {
            case "sol", "terra", "luna" -> workModel;
            default -> "inherit";
        };
    }

    public static int normalizedIntervalMinutes(int value) {
        return Math.max(MIN_INTERVAL_MINUTES, Math.min(MAX_INTERVAL_MINUTES, value));
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("targetType", targetType);
        object.put("targetUrl", targetUrl);
        object.put("experience", normalizedExperience(targetType, experience));
        object.put("workModel", normalizedWorkModel(
                normalizedExperience(targetType, experience), workModel));
        object.put("reasoningEffort", normalizedReasoningEffort(
                normalizedExperience(targetType, experience), reasoningEffort));
        object.put("prompt", prompt);
        object.put("recurrence", recurrence);
        object.put("intervalMinutes", normalizedIntervalMinutes(intervalMinutes));
        object.put("weekdays", new JSONArray(weekdays));
        object.put("times", new JSONArray(times));
        object.put("enabled", enabled);
        object.put("retryCount", retryCount);
        object.put("lastRunAt", lastRunAt);
        object.put("nextRunAt", nextRunAt);
        object.put("lastStatus", lastStatus);
        return object;
    }

    public static Schedule fromJson(JSONObject object) throws JSONException {
        Schedule schedule = new Schedule();
        schedule.id = object.optString("id", UUID.randomUUID().toString());
        schedule.name = object.optString("name", "예약");
        schedule.targetType = object.optString("targetType", "general");
        schedule.targetUrl = object.optString("targetUrl", "https://chatgpt.com/");
        schedule.experience = normalizedExperience(schedule.targetType, object.optString("experience", "chat"));
        schedule.workModel = normalizedWorkModel(
                schedule.experience, object.optString("workModel", "inherit"));
        schedule.reasoningEffort = normalizedReasoningEffort(
                schedule.experience, object.optString("reasoningEffort", "inherit"));
        schedule.prompt = object.optString("prompt", "");
        schedule.recurrence = object.optString("recurrence", "daily");
        schedule.intervalMinutes = normalizedIntervalMinutes(object.optInt("intervalMinutes", 30));
        schedule.weekdays.clear();
        JSONArray weekdayArray = object.optJSONArray("weekdays");
        if (weekdayArray != null) {
            for (int i = 0; i < weekdayArray.length(); i++) schedule.weekdays.add(weekdayArray.optInt(i));
        }
        if (schedule.weekdays.isEmpty()) schedule.weekdays.addAll(Arrays.asList(1, 2, 3, 4, 5, 6, 7));
        schedule.times.clear();
        JSONArray timeArray = object.optJSONArray("times");
        if (timeArray != null) {
            for (int i = 0; i < timeArray.length(); i++) {
                String value = timeArray.optString(i, "");
                if (value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) schedule.times.add(value);
            }
        }
        if (schedule.times.isEmpty()) schedule.times.add("09:00");
        schedule.enabled = object.optBoolean("enabled", true);
        schedule.retryCount = Math.max(0, Math.min(5, object.optInt("retryCount", 2)));
        schedule.lastRunAt = object.optLong("lastRunAt", 0L);
        schedule.nextRunAt = Math.max(0L, object.optLong("nextRunAt", 0L));
        schedule.lastStatus = object.optString("lastStatus", "NEVER");
        return schedule;
    }
}
