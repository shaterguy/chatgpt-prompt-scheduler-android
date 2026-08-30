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
    private static final String PREFS = "scheduler_config";
    private static final String KEY = "config_json";
    private final SharedPreferences preferences;
    private final RequestProfileRegistry profileRegistry;

    public ConfigStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        profileRegistry = new RequestProfileRegistry(context);
    }

    public synchronized JSONObject loadRoot() {
        String stored = preferences.getString(KEY, null);
        try { if (stored != null) return migrate(new JSONObject(stored)); }
        catch (JSONException ignored) {}
        return defaultRoot();
    }

    public synchronized List<Schedule> loadSchedules() {
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

    public synchronized Schedule findSchedule(String id) {
        for (Schedule schedule : loadSchedules()) if (schedule.id.equals(id)) return schedule;
        return null;
    }

    public synchronized void saveSchedule(Schedule incoming) {
        List<Schedule> schedules = loadSchedules();
        boolean replaced = false;
        for (int i = 0; i < schedules.size(); i++) if (schedules.get(i).id.equals(incoming.id)) {
            schedules.set(i, incoming); replaced = true; break;
        }
        if (!replaced) schedules.add(incoming);
        saveSchedules(schedules);
    }

    public synchronized void deleteSchedule(String id) {
        List<Schedule> schedules = loadSchedules();
        schedules.removeIf(schedule -> schedule.id.equals(id));
        saveSchedules(schedules);
    }

    public synchronized void saveSchedules(List<Schedule> schedules) {
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

    public synchronized JSONObject exportPortable() {
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

    public synchronized int importPortable(JSONObject incoming) throws JSONException {
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

    public synchronized JSONObject settings() {
        JSONObject settings = loadRoot().optJSONObject("settings");
        return settings == null ? defaultSettings() : settings;
    }

    public synchronized void saveSettings(JSONObject settings) {
        JSONObject root = loadRoot();
        try { root.put("settings", settings); } catch (JSONException ignored) {}
        saveRoot(root);
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
