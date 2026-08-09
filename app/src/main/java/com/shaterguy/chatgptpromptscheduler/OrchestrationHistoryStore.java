package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Durable, bounded summaries of autorun jobs. Full debug telemetry stays in OrchestrationRunLog. */
public final class OrchestrationHistoryStore {
    private static final String PREFS = "orchestration_history";
    private static final String KEY_PRIMARY = "jobs";
    private static final String KEY_BACKUP = "jobsBackup";
    private static final String KEY_HIDDEN = "hiddenJobIds";
    private final Context context;
    private final SharedPreferences preferences;

    public OrchestrationHistoryStore(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized boolean sync(OrchestrationStore store) {
        if (store == null || store.runJobId().isEmpty()) return true;
        if (!store.saveWorkspace(context)) return false;
        if (hiddenJobIds().contains(store.runJobId())) return true;
        long now = System.currentTimeMillis();
        JSONArray current = read();
        JSONObject previous = find(current, store.runJobId());
        JSONObject snapshot = snapshot(store, previous, now);
        JSONArray next = new JSONArray();
        next.put(snapshot);
        for (int index = 0; index < current.length(); index++) {
            JSONObject item = current.optJSONObject(index);
            if (item == null || store.runJobId().equals(item.optString("jobId"))) continue;
            next.put(item);
        }
        return persist(next);
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

    public synchronized boolean isHidden(String jobId) {
        return jobId != null && !jobId.isEmpty() && hiddenJobIds().contains(jobId);
    }

    public boolean hasWorkspace(String jobId) { return new OrchestrationStore(context).hasWorkspace(context, jobId); }

    public boolean restoreWorkspace(String jobId, OrchestrationStore target) {
        return target != null && target.restoreWorkspace(context, jobId);
    }

    public String workspaceRequirement(String jobId, String fallback) {
        return OrchestrationStore.workspaceRequirement(context, jobId, fallback);
    }

    /** Marks an inactive archived Job as locally stopped without touching the current workspace. */
    public synchronized boolean stopWorkspace(String jobId) {
        JSONArray jobs = read();
        JSONObject item = find(jobs, jobId);
        if (item == null) return false;
        if (!OrchestrationStore.stopWorkspace(context, jobId)) return false;
        try {
            item.put("updatedAt", System.currentTimeMillis());
            item.put("statusSummary", "중지됨");
            item.put("status", "사용자가 중지함");
            item.put("bootstrapState", OrchestrationStore.BOOTSTRAP_STOPPED);
            item.put("active", false);
            item.put("paused", false);
            item.put("terminal", true);
            item.put("userStopped", true);
            item.put("waitingForUser", false);
            item.put("actionId", "");
            item.put("lastErrorCode", "");
            item.put("error", "");
            item.put("errorAt", 0L);
        } catch (Exception ignored) {
            return false;
        }
        return persist(jobs);
    }

    /** Hides a Job locally. It never deletes ChatGPT conversations or Drive artifacts. */
    public synchronized boolean hideJob(String jobId) {
        if (jobId == null || !jobId.matches("[A-Za-z0-9._-]{1,64}")) return false;
        JSONArray current = read();
        JSONArray next = new JSONArray();
        for (int index = 0; index < current.length(); index++) {
            JSONObject item = current.optJSONObject(index);
            if (item != null && !jobId.equals(item.optString("jobId"))) next.put(item);
        }
        Set<String> hidden = hiddenJobIds();
        hidden.add(jobId);
        String previous = preferences.getString(KEY_PRIMARY, "[]");
        return preferences.edit().putString(KEY_BACKUP, previous).putString(KEY_PRIMARY, next.toString())
                .putStringSet(KEY_HIDDEN, hidden).commit();
    }

    private Set<String> hiddenJobIds() {
        return new HashSet<>(preferences.getStringSet(KEY_HIDDEN, Collections.emptySet()));
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
            // The list keeps a compact preview. The full original remains in the per-Job workspace.
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
            item.put("lastSignalSource", store.lastSignalSource());
            item.put("lastSignalAt", store.lastSignalAt());
            item.put("lastDeliveryTarget", store.lastDeliveryTarget());
            item.put("lastDeliveredPrompt", bounded(store.lastDeliveredPrompt(), 1_000));
            item.put("lastDeliveryState", store.lastDeliveryState());
            item.put("lastDeliveryAt", store.lastDeliveryAt());
            item.put("deliveryTarget", store.deliveryTarget());
            item.put("deliveryState", store.deliveryState());
            item.put("expectedSignal", bounded(store.expectedSignal(), 1_000));
            item.put("schedulePreempted", store.schedulePreempted());
            item.put("actionId", store.actionId());
            item.put("lastErrorCode", store.lastErrorCode());
            item.put("error", bounded(store.error(), 2_000));
            item.put("errorAt", store.errorAt());
            item.put("active", store.active());
            item.put("paused", store.paused());
            item.put("terminal", store.terminal());
            item.put("userStopped", store.userStopped());
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

    private boolean persist(JSONArray jobs) {
        String previous = preferences.getString(KEY_PRIMARY, "[]");
        return preferences.edit().putString(KEY_BACKUP, previous)
                .putString(KEY_PRIMARY, jobs.toString()).commit();
    }

    private static String bounded(String value, int max) {
        String safe = value == null ? "" : value;
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
