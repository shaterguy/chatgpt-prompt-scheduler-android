package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class RunLogStore {
    private static final String PREFS = "scheduler_logs";
    private static final String KEY_LOGS = "logs";
    private static final String KEY_BACKUP = "logs_backup";
    private static final int MAX_LOGS = 120;
    private final SharedPreferences preferences;
    private final File httpBodies;

    public RunLogStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        httpBodies = new File(context.getFilesDir(), "scheduler_http_bodies");
    }

    public synchronized String newRunId() {
        return UUID.randomUUID().toString();
    }

    public synchronized void append(String runId, String scheduleId, String scheduleName, String status, String detail,
                                    long startedAt, long finishedAt, String targetUrl, boolean success,
                                    JSONArray events, JSONObject environment) {
        append(runId, scheduleId, scheduleName, status, detail, startedAt, finishedAt,
                targetUrl, "", success, events, environment);
    }

    public synchronized void append(String runId, String scheduleId, String scheduleName, String status, String detail,
                                    long startedAt, long finishedAt, String targetUrl, String conversationUrl,
                                    boolean success, JSONArray events, JSONObject environment) {
        JSONArray current = read();
        JSONArray next = new JSONArray();
        JSONObject item = new JSONObject();
        try {
            item.put("schemaVersion", 2);
            item.put("runId", runId);
            item.put("scheduleId", scheduleId);
            item.put("scheduleName", scheduleName);
            item.put("success", success);
            item.put("status", status);
            item.put("detail", detail == null ? "" : detail);
            item.put("startedAt", startedAt);
            item.put("finishedAt", finishedAt);
            item.put("durationMs", Math.max(0, finishedAt - startedAt));
            item.put("targetUrl", targetUrl == null ? "" : targetUrl);
            item.put("conversationUrl", conversationUrl == null ? "" : conversationUrl);
            item.put("environment", copyObject(environment));
            item.put("events", copyArray(events));
            next.put(item);
            for (int i = 0; i < current.length() && next.length() < MAX_LOGS; i++) next.put(current.opt(i));
        } catch (JSONException e) {
            throw new IllegalStateException("실행 기록을 구성하지 못했습니다.", e);
        }
        persist(next);
        pruneHttpBodies(next, current);
    }

    public synchronized JSONArray read() {
        String primary = preferences.getString(KEY_LOGS, "[]");
        JSONArray parsed = parse(primary);
        if (parsed != null) return parsed;

        String backup = preferences.getString(KEY_BACKUP, "[]");
        JSONArray recovered = parse(backup);
        if (recovered != null) {
            if (!preferences.edit().putString(KEY_LOGS, recovered.toString()).commit()) {
                throw new IllegalStateException("백업 실행 기록을 복구하지 못했습니다.");
            }
            return recovered;
        }

        JSONArray diagnostic = new JSONArray();
        JSONObject item = new JSONObject();
        try {
            item.put("schemaVersion", 2);
            item.put("runId", "storage-diagnostic");
            item.put("scheduleId", "");
            item.put("scheduleName", "실행 기록 저장소");
            item.put("success", false);
            item.put("status", "LOG_STORAGE_CORRUPT");
            item.put("detail", "기본 기록과 백업 기록을 모두 읽지 못했습니다.");
            item.put("startedAt", 0L);
            item.put("finishedAt", System.currentTimeMillis());
            item.put("durationMs", 0L);
            item.put("targetUrl", "");
            item.put("environment", new JSONObject());
            item.put("events", new JSONArray());
            diagnostic.put(item);
        } catch (JSONException e) {
            throw new IllegalStateException("실행 기록 손상 상태를 표시하지 못했습니다.", e);
        }
        return diagnostic;
    }

    public synchronized JSONObject find(String runId) {
        JSONArray logs = read();
        for (int i = 0; i < logs.length(); i++) {
            JSONObject item = logs.optJSONObject(i);
            if (item != null && item.optString("runId").equals(runId)) return copyObject(item);
        }
        return null;
    }

    synchronized void appendHttpResponse(String runId, String responseId, String body) throws IOException {
        File output = httpBodyFile(runId, responseId);
        if (output == null) throw new IOException("잘못된 응답 기록 ID");
        File directory = output.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("응답 기록 폴더 생성 실패");
        Files.write(output.toPath(), body.getBytes(StandardCharsets.UTF_8));
    }

    private File httpBodyFile(String runId, String responseId) {
        if (!runId.matches("[A-Za-z0-9_-]{1,80}") || !responseId.matches("[a-z0-9-]{1,64}")) return null;
        return new File(new File(httpBodies, runId), responseId + ".txt");
    }

    private void includeHttpResponseBodies(JSONObject run) throws JSONException {
        JSONArray events = run.optJSONArray("events");
        if (events == null) return;
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null || !"CHATGPT_HTTP_FAILURE".equals(event.optString("type"))) continue;
            JSONObject data = event.optJSONObject("data");
            if (data == null || !data.has("responseId") || "storage_failed".equals(data.optString("bodyState"))) continue;
            File body = httpBodyFile(run.optString("runId"), data.optString("responseId"));
            if (body == null) continue;
            try {
                data.put("responseBody", new String(Files.readAllBytes(body.toPath()), StandardCharsets.UTF_8));
            } catch (IOException error) {
                data.put("bodyState", "storage_unreadable");
            }
        }
    }

    private void pruneHttpBodies(JSONArray... retained) {
        File[] directories = httpBodies.listFiles();
        if (directories == null) return;
        Set<String> keep = new HashSet<>();
        for (JSONArray logs : retained) {
            for (int i = 0; i < logs.length(); i++) {
                JSONObject log = logs.optJSONObject(i);
                if (log != null) keep.add(log.optString("runId"));
            }
        }
        for (File directory : directories) {
            if (keep.contains(directory.getName())) continue;
            File[] files = directory.listFiles();
            if (files != null) for (File file : files) file.delete();
            directory.delete();
        }
    }

    public synchronized JSONObject exportAll() {
        JSONObject exported = new JSONObject();
        try {
            exported.put("schemaVersion", 2);
            exported.put("generatedAt", System.currentTimeMillis());
            JSONArray logs = read();
            for (int i = 0; i < logs.length(); i++) {
                JSONObject log = logs.optJSONObject(i);
                if (log != null) includeHttpResponseBodies(log);
            }
            exported.put("logs", logs);
            return exported;
        } catch (JSONException e) {
            throw new IllegalStateException("전체 실행 로그를 구성하지 못했습니다.", e);
        }
    }

    public synchronized JSONObject exportOne(String runId) {
        JSONObject item = find(runId);
        if (item == null) return null;
        JSONObject exported = new JSONObject();
        try {
            exported.put("schemaVersion", 2);
            exported.put("generatedAt", System.currentTimeMillis());
            includeHttpResponseBodies(item);
            exported.put("log", item);
            return exported;
        } catch (JSONException e) {
            throw new IllegalStateException("실패 실행 로그를 구성하지 못했습니다.", e);
        }
    }

    public synchronized void clear() {
        if (!preferences.edit().putString(KEY_LOGS, "[]").putString(KEY_BACKUP, "[]").commit()) {
            throw new IllegalStateException("실행 기록을 삭제하지 못했습니다.");
        }
    }

    private JSONArray parse(String value) {
        try {
            return new JSONArray(value == null ? "[]" : value);
        } catch (JSONException e) {
            return null;
        }
    }

    private JSONArray copyArray(JSONArray value) {
        if (value == null) return new JSONArray();
        try {
            return new JSONArray(value.toString());
        } catch (JSONException error) {
            return new JSONArray();
        }
    }

    private JSONObject copyObject(JSONObject value) {
        if (value == null) return new JSONObject();
        try {
            return new JSONObject(value.toString());
        } catch (JSONException error) {
            return new JSONObject();
        }
    }

    private void persist(JSONArray logs) {
        String previous = preferences.getString(KEY_LOGS, "[]");
        boolean saved = preferences.edit()
                .putString(KEY_BACKUP, previous)
                .putString(KEY_LOGS, logs.toString())
                .commit();
        if (!saved) throw new IllegalStateException("실행 기록을 저장하지 못했습니다.");
    }
}
