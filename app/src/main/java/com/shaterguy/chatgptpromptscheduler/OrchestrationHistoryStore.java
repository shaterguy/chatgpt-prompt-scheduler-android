package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Durable, bounded summaries of autorun jobs. Full debug telemetry stays in OrchestrationRunLog. */
public final class OrchestrationHistoryStore {
    static final int MAX_JOBS = 100;
    private static final String PREFS = "orchestration_history";
    private static final String KEY_PRIMARY = "jobs";
    private static final String KEY_BACKUP = "jobsBackup";
    private final SharedPreferences preferences;

    public OrchestrationHistoryStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void sync(OrchestrationStore store) {
        if (store == null || store.runJobId().isEmpty()) return;
        long now = System.currentTimeMillis();
        JSONArray current = read();
        JSONObject previous = find(current, store.runJobId());
        JSONObject snapshot = snapshot(store, previous, now);
        JSONArray next = new JSONArray();
        next.put(snapshot);
        for (int index = 0; index < current.length() && next.length() < MAX_JOBS; index++) {
            JSONObject item = current.optJSONObject(index);
            if (item == null || store.runJobId().equals(item.optString("jobId"))) continue;
            next.put(item);
        }
        persist(next);
    }

    public synchronized JSONArray read() {
        JSONArray primary = parse(preferences.getString(KEY_PRIMARY, "[]"));
        if (primary != null) return primary;
        JSONArray backup = parse(preferences.getString(KEY_BACKUP, "[]"));
        return backup == null ? new JSONArray() : backup;
    }

    public synchronized JSONObject get(String jobId) {
        JSONObject item = find(read(), jobId);
        if (item == null) return null;
        try {
            return new JSONObject(item.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JSONObject snapshot(OrchestrationStore store, JSONObject previous, long now) {
        JSONObject item = new JSONObject();
        try {
            long createdAt = store.runCreatedAt();
            if (createdAt <= 0L && previous != null) createdAt = previous.optLong("createdAt", 0L);
            if (createdAt <= 0L) createdAt = now;
            item.put("jobId", store.runJobId());
            item.put("createdAt", createdAt);
            item.put("updatedAt", now);
            item.put("requirement", bounded(store.runRequirement(), 4_000));
            item.put("projectUrl", store.runProjectUrl());
            item.put("workModel", store.runWorkModel());
            item.put("reasoningEffort", store.runReasoningEffort());
            item.put("chatUrl", store.runChatUrl());
            item.put("workUrl", store.runWorkUrl());
            item.put("bootstrapState", store.bootstrapState());
            item.put("statusSummary", store.statusSummary());
            item.put("status", bounded(store.status(), 1_000));
            item.put("step", store.currentStep());
            item.put("round", store.currentRound());
            item.put("lastSignal", store.lastAcceptedSignal());
            item.put("lastSignalAt", store.lastSignalAt());
            item.put("lastErrorCode", store.lastErrorCode());
            item.put("error", bounded(store.error(), 2_000));
            item.put("errorAt", store.errorAt());
            item.put("active", store.active());
            item.put("paused", store.paused());
            item.put("terminal", store.terminal());
            item.put("waitingForUser", store.waitingForUser());
        } catch (Exception ignored) {
            // JSONObject writes only primitive/string values and should not fail.
        }
        return item;
    }

    private static JSONObject find(JSONArray array, String jobId) {
        if (jobId == null || jobId.isEmpty()) return null;
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.optJSONObject(index);
            if (item != null && jobId.equals(item.optString("jobId"))) return item;
        }
        return null;
    }

    private static JSONArray parse(String value) {
        try {
            return new JSONArray(value == null ? "[]" : value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void persist(JSONArray jobs) {
        String previous = preferences.getString(KEY_PRIMARY, "[]");
        preferences.edit().putString(KEY_BACKUP, previous).putString(KEY_PRIMARY, jobs.toString()).commit();
    }

    private static String bounded(String value, int max) {
        String safe = value == null ? "" : value;
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
