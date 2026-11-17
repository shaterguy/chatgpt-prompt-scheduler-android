package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ConfigStore {
    public static final int SCHEMA_VERSION = 1;
    static final Object LOCK = new Object();
    private static final String PREFS = "scheduler_config";
    private static final String KEY = "config_json";
    private final SharedPreferences preferences;
    private final RequestProfileRegistry profileRegistry;

    public ConfigStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        profileRegistry = new RequestProfileRegistry(context);
    }

    public JSONObject loadRoot() {
        synchronized (LOCK) {
            String stored = preferences.getString(KEY, null);
            try { if (stored != null) return migrate(new JSONObject(stored)); }
            catch (JSONException ignored) {}
            return defaultRoot();
        }
    }

    public List<Schedule> loadSchedules() {
        synchronized (LOCK) {
            List<Schedule> schedules = new ArrayList<>();
            JSONArray array = loadRoot().optJSONArray("schedules");
            if (array == null) return schedules;
            for (int i = 0; i < array.length(); i++) {
                try {
                    Schedule schedule = Schedule.fromJson(array.getJSONObject(i));
                    profileRegistry.attach(schedule);
                    schedules.add(schedule);
                } catch (JSONException ignored) {}
            }
            return schedules;
        }
    }

    public Schedule findSchedule(String id) {
        synchronized (LOCK) {
            for (Schedule schedule : loadSchedules()) if (schedule.id.equals(id)) return schedule;
            return null;
        }
    }

    public void saveSchedule(Schedule incoming) {
        synchronized (LOCK) {
            List<Schedule> schedules = loadSchedules();
            boolean replaced = false;
            for (int i = 0; i < schedules.size(); i++) if (schedules.get(i).id.equals(incoming.id)) {
                mergeLatestCompletion(incoming, schedules.get(i));
                schedules.set(i, incoming); replaced = true; break;
            }
            if (!replaced) schedules.add(incoming);
            saveSchedules(schedules);
        }
    }

    /** Persist editor fields and the next alarm using the latest terminal state. */
    public Schedule saveEditedSchedule(Schedule incoming, boolean originallyEnabled, long now,
                                       java.util.Set<java.time.LocalDate> holidays) {
        synchronized (LOCK) {
            Schedule latest = findSchedule(incoming.id);
            mergeEditedRuntime(incoming, latest, originallyEnabled);
            incoming.nextRunAt = Recurrence.nextRunAfterEdit(incoming, now, holidays);
            saveSchedule(incoming);
            return incoming;
        }
    }

    static void mergeEditedRuntime(Schedule incoming, Schedule latest, boolean originallyEnabled) {
        if (latest == null) return;
        mergeLatestCompletion(incoming, latest);
        if (incoming.enabled == originallyEnabled) incoming.enabled = latest.enabled;
    }

    private static void mergeLatestCompletion(Schedule incoming, Schedule latest) {
        if (latest.lastRunAt >= incoming.lastRunAt) {
            incoming.lastRunAt = latest.lastRunAt;
            incoming.lastOccurrenceAt = latest.lastOccurrenceAt;
            incoming.lastStatus = latest.lastStatus;
        }
    }

    /** Merge execution metadata into the most recently saved schedule. */
    public Schedule recordRunCompletion(String id, long finishedAt,
                                        String status, boolean automaticOnce) {
        synchronized (LOCK) {
            return recordRunCompletion(id, finishedAt, status, automaticOnce, finishedAt);
        }
    }

    public Schedule recordRunCompletion(String id, long finishedAt,
                                        String status, boolean automaticOnce, long occurrenceAt) {
        synchronized (LOCK) {
            Schedule latest = findSchedule(id);
            if (latest == null) return null;
            mergeRunCompletion(latest, finishedAt, status, automaticOnce, occurrenceAt);
            saveSchedule(latest);
            return latest;
        }
    }

    static void mergeRunCompletion(Schedule latest, long finishedAt,
                                   String status, boolean automaticOnce) {
        mergeRunCompletion(latest, finishedAt, status, automaticOnce, finishedAt);
    }

    static void mergeRunCompletion(Schedule latest, long finishedAt,
                                   String status, boolean automaticOnce, long occurrenceAt) {
        if (finishedAt < latest.lastRunAt) return;
        latest.lastRunAt = finishedAt;
        latest.lastOccurrenceAt = occurrenceAt > 0L ? occurrenceAt : finishedAt;
        latest.lastStatus = status;
        if (automaticOnce && "once".equals(latest.recurrence)) latest.enabled = false;
    }

    public void deleteSchedule(String id) {
        synchronized (LOCK) {
            List<Schedule> schedules = loadSchedules();
            schedules.removeIf(schedule -> schedule.id.equals(id));
            saveSchedules(schedules);
        }
    }

    public void saveSchedules(List<Schedule> schedules) {
        synchronized (LOCK) {
            JSONObject root = loadRoot();
            JSONArray array = new JSONArray();
            for (Schedule schedule : schedules) try { array.put(schedule.toJson()); } catch (JSONException ignored) {}
            try {
                root.put("schemaVersion", SCHEMA_VERSION);
                root.put("schedules", array);
                root.put("exportedAt", System.currentTimeMillis());
            } catch (JSONException ignored) {}
            saveRoot(root);
        }
    }

    public JSONObject exportPortable() {
        synchronized (LOCK) {
            JSONObject current = loadRoot(), portable = new JSONObject();
            try {
                portable.put("app", "ChatGPT Prompt Scheduler");
                portable.put("schemaVersion", SCHEMA_VERSION);
                portable.put("exportedAt", System.currentTimeMillis());
                portable.put("schedules", current.optJSONArray("schedules") == null ? new JSONArray() : current.optJSONArray("schedules"));
                portable.put("settings", current.optJSONObject("settings") == null ? defaultSettings() : current.optJSONObject("settings"));
            } catch (JSONException ignored) {}
            return portable;
        }
    }

    public int importPortable(JSONObject incoming) throws JSONException {
        synchronized (LOCK) {
            JSONObject migrated = migrate(incoming);
            JSONArray schedules = migrated.optJSONArray("schedules");
            if (schedules == null) throw new JSONException("schedules 배열이 없습니다.");
            for (int i = 0; i < schedules.length(); i++) Schedule.fromJson(schedules.getJSONObject(i));
            JSONObject root = defaultRoot();
            root.put("schedules", schedules);
            root.put("settings", migrated.optJSONObject("settings") == null ? defaultSettings() : migrated.optJSONObject("settings"));
            saveRoot(root);
            return schedules.length();
        }
    }

    public JSONObject settings() {
        synchronized (LOCK) {
            JSONObject settings = loadRoot().optJSONObject("settings");
            return settings == null ? defaultSettings() : settings;
        }
    }

    public void saveSettings(JSONObject settings) {
        synchronized (LOCK) {
            JSONObject root = loadRoot();
            try { root.put("settings", settings); } catch (JSONException ignored) {}
            saveRoot(root);
        }
    }

    private JSONObject migrate(JSONObject root) throws JSONException {
        int schema = root.optInt("schemaVersion", 1);
        if (schema > SCHEMA_VERSION) throw new JSONException("지원하지 않는 미래 스키마입니다: " + schema);
        if (!root.has("schedules")) root.put("schedules", new JSONArray());
        if (!root.has("settings")) root.put("settings", defaultSettings());
        root.put("schemaVersion", SCHEMA_VERSION);
        return root;
    }

    private void saveRoot(JSONObject root) {
        if (!preferences.edit().putString(KEY, root.toString()).commit()) throw new IllegalStateException("설정을 저장하지 못했습니다.");
    }

    public static JSONObject defaultRoot() {
        JSONObject root = new JSONObject();
        try {
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("schedules", new JSONArray());
            root.put("settings", defaultSettings());
        } catch (JSONException ignored) {}
        return root;
    }

    public static JSONObject defaultSettings() {
        JSONObject settings = new JSONObject();
        try {
            settings.put("notifySuccess", true);
            settings.put("notifyFailure", true);
            settings.put("missedGraceMinutes", 30);
            settings.put("maxRetries", 2);
            settings.put("executionTimeoutSeconds", 90);
        } catch (JSONException ignored) {}
        return settings;
    }
}
