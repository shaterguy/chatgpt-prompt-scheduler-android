package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public final class QueueStore {
    private static final String PREFS = "scheduler_queue";
    private static final String KEY = "queue";
    private final SharedPreferences preferences;

    public static final class EnqueueResult {
        public final String runId;
        public final boolean added;
        public final String state;

        private EnqueueResult(String runId, boolean added, String state) {
            this.runId = runId;
            this.added = added;
            this.state = state;
        }
    }

    public QueueStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized EnqueueResult enqueue(String scheduleId, boolean manual) {
        JSONArray queue = read();
        for (int i = 0; i < queue.length(); i++) {
            JSONObject item = queue.optJSONObject(i);
            if (item == null || !scheduleId.equals(item.optString("scheduleId"))) continue;
            String state = item.optString("state");
            if ("queued".equals(state) || "running".equals(state)) {
                return new EnqueueResult(item.optString("runId"), false, state);
            }
        }

        String runId = UUID.randomUUID().toString();
        JSONObject item = new JSONObject();
        try {
            item.put("runId", runId);
            item.put("scheduleId", scheduleId);
            item.put("manual", manual);
            item.put("state", "queued");
            item.put("enqueuedAt", System.currentTimeMillis());
            queue.put(item);
            save(queue);
        } catch (JSONException e) {
            throw new IllegalStateException("실행 대기열을 만들지 못했습니다.", e);
        }
        return new EnqueueResult(runId, true, "queued");
    }

    public synchronized JSONObject claimNext() {
        JSONArray queue = read();
        for (int i = 0; i < queue.length(); i++) {
            JSONObject item = queue.optJSONObject(i);
            if (item != null && "queued".equals(item.optString("state"))) {
                try {
                    item.put("state", "running");
                    item.put("startedAt", System.currentTimeMillis());
                    save(queue);
                    return new JSONObject(item.toString());
                } catch (JSONException e) {
                    throw new IllegalStateException("실행 대기열 상태를 갱신하지 못했습니다.", e);
                }
            }
        }
        return null;
    }

    public synchronized boolean markSubmissionAttempted(String runId, long attemptedAt) {
        JSONArray queue = read();
        for (int i = 0; i < queue.length(); i++) {
            JSONObject item = queue.optJSONObject(i);
            if (item == null || !runId.equals(item.optString("runId"))) continue;
            try {
                item.put("submitAttemptedAt", attemptedAt);
                save(queue);
                return true;
            } catch (JSONException e) {
                throw new IllegalStateException("전송 시도 상태를 저장하지 못했습니다.", e);
            }
        }
        return false;
    }

    public synchronized void clearSubmissionAttempted(String runId) {
        JSONArray queue = read();
        for (int i = 0; i < queue.length(); i++) {
            JSONObject item = queue.optJSONObject(i);
            if (item == null || !runId.equals(item.optString("runId"))) continue;
            item.remove("submitAttemptedAt");
            save(queue);
            return;
        }
    }

    public synchronized void finish(String runId) {
        JSONArray queue = read();
        JSONArray next = new JSONArray();
        for (int i = 0; i < queue.length(); i++) {
            JSONObject item = queue.optJSONObject(i);
            if (item == null || !runId.equals(item.optString("runId"))) next.put(item);
        }
        save(next);
    }

    public synchronized boolean hasQueued() {
        JSONArray queue = read();
        for (int i = 0; i < queue.length(); i++) {
            JSONObject item = queue.optJSONObject(i);
            if (item != null && "queued".equals(item.optString("state"))) return true;
        }
        return false;
    }

    public synchronized void recoverRunning() {
        JSONArray queue = read();
        boolean changed = false;
        for (int i = 0; i < queue.length(); i++) {
            JSONObject item = queue.optJSONObject(i);
            if (item != null && "running".equals(item.optString("state"))) {
                try {
                    item.put("state", "queued");
                    changed = true;
                } catch (JSONException e) {
                    throw new IllegalStateException("실행 대기열을 복구하지 못했습니다.", e);
                }
            }
        }
        if (changed) save(queue);
    }

    private JSONArray read() {
        try {
            return new JSONArray(preferences.getString(KEY, "[]"));
        } catch (JSONException e) {
            throw new IllegalStateException("실행 대기열 데이터가 손상되었습니다.", e);
        }
    }

    private void save(JSONArray queue) {
        if (!preferences.edit().putString(KEY, queue.toString()).commit()) {
            throw new IllegalStateException("실행 대기열을 저장하지 못했습니다.");
        }
    }
}
