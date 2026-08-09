package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/** App-private, redacted JSONL telemetry for the Protocol 3.x relay only. */
public final class OrchestrationRunLog {
    public static final long MAX_FILE_BYTES = 512L * 1024L;
    public static final int MAX_FILES = 5;
    public static final long MAX_JOB_FILE_BYTES = 256L * 1024L;
    public static final int MAX_JOB_FILES = 60;
    private static final String DIRECTORY_NAME = "orchestration-logs";
    private static final String CURRENT_NAME = "orchestration-current.jsonl";
    private static final String FILE_PREFIX = "orchestration-";
    private static final String FILE_SUFFIX = ".jsonl";
    private static final String JOB_PREFIX = "job-";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Object FILE_LOCK = new Object();
    private final File directory;
    private final OrchestrationHistoryStore historyStore;
    private int writeFailureCount;

    public OrchestrationRunLog(Context context) {
        directory = new File(context.getNoBackupFilesDir(), DIRECTORY_NAME);
        historyStore = new OrchestrationHistoryStore(context);
    }

    OrchestrationRunLog(File directory) {
        this.directory = directory;
        historyStore = null;
    }

    /** Logging is deliberately best-effort; telemetry must never stop relay or reservation work. */
    public synchronized void record(OrchestrationStore store, String eventCode, String detail) {
        try {
            if (historyStore != null) historyStore.sync(store);
        } catch (Throwable ignored) {
            // History and telemetry are both best-effort and cannot affect relay execution.
        }
        String side = store == null ? "" : OrchestrationStore.DELIVERY_WAITING_RESPONSE.equals(store.deliveryState())
                ? store.monitoringSide() : store.deliveryTarget();
        String lifecycle = lifecycle(store);
        record(store == null ? "" : store.runJobId(), store == null ? "" : store.currentStep(),
                store == null ? "" : store.currentRound(), eventCode, side,
                store == null ? "" : store.deliveryState(), lifecycle, detail);
    }

    public synchronized void record(String jobId, String step, String round, String eventCode, String side,
                                     String deliveryState, String lifecycle, String detail) {
        try {
            if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("log directory unavailable");
            JSONObject item = new JSONObject();
            item.put("timestamp_kst", OffsetDateTime.now(KST).format(TIMESTAMP));
            item.put("job_id", safeToken(jobId));
            item.put("step", safeToken(step));
            item.put("round", safeToken(round));
            item.put("event_code", safeEvent(eventCode));
            item.put("side", safeToken(side));
            item.put("delivery_state", safeToken(deliveryState));
            item.put("lifecycle", safeToken(lifecycle));
            item.put("detail", sanitizeDetail(detail));
            synchronized (FILE_LOCK) {
                appendLine(item.toString());
                appendJobLine(safeToken(jobId), item.toString());
            }
        } catch (Throwable ignored) {
            writeFailureCount = writeFailureCount == Integer.MAX_VALUE ? Integer.MAX_VALUE : writeFailureCount + 1;
        }
    }

    public synchronized List<String> readRecentLines(int maxLines) {
        int limit = Math.max(1, maxLines);
        Deque<String> lines = new ArrayDeque<>();
        for (File file : orderedFiles()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (lines.size() == limit) lines.removeFirst();
                    lines.addLast(line);
                }
            } catch (Exception ignored) {
                // A damaged/vanished rotated file is skipped; newer files remain available.
            }
        }
        return new ArrayList<>(lines);
    }

    public synchronized String exportAll() {
        StringBuilder result = new StringBuilder();
        for (String line : readRecentLines(Integer.MAX_VALUE)) result.append(line).append('\n');
        return result.toString();
    }

    public synchronized List<String> readJobLines(String jobId, int maxLines) {
        String requestedJobId = jobId == null ? "" : jobId;
        int limit = Math.max(1, maxLines);
        Deque<String> matches = new ArrayDeque<>();
        File dedicated = jobFile(requestedJobId);
        if (dedicated != null && dedicated.exists()) {
            Deque<String> dedicatedLines = new ArrayDeque<>();
            readLines(dedicated, Integer.MAX_VALUE, dedicatedLines);
            String firstDedicatedTimestamp = dedicatedLines.isEmpty() ? "" : timestampOf(dedicatedLines.peekFirst());
            if (!firstDedicatedTimestamp.isEmpty()) {
                for (String line : readRecentLines(Integer.MAX_VALUE)) {
                    try {
                        JSONObject item = new JSONObject(line);
                        if (requestedJobId.equals(item.optString("job_id"))
                                && item.optString("timestamp_kst").compareTo(firstDedicatedTimestamp) < 0) {
                            if (matches.size() == limit) matches.removeFirst();
                            matches.addLast(line);
                        }
                    } catch (Exception ignored) {
                        // Corrupt global lines cannot supplement the dedicated log.
                    }
                }
            }
            for (String line : dedicatedLines) {
                if (matches.size() == limit) matches.removeFirst();
                matches.addLast(line);
            }
            return new ArrayList<>(matches);
        }
        for (String line : readRecentLines(Integer.MAX_VALUE)) {
            try {
                JSONObject item = new JSONObject(line);
                if (!requestedJobId.equals(item.optString("job_id"))) continue;
                if (matches.size() == limit) matches.removeFirst();
                matches.addLast(line);
            } catch (Exception ignored) {
                // Corrupt lines are excluded from per-job views.
            }
        }
        return new ArrayList<>(matches);
    }

    public synchronized List<String> readExecutionLines(String jobId, int maxLines) {
        int limit = Math.max(1, maxLines);
        Deque<String> matches = new ArrayDeque<>();
        for (String line : readJobLines(jobId, Integer.MAX_VALUE)) {
            try {
                JSONObject item = new JSONObject(line);
                String event = item.optString("event_code");
                if (!isExecutionEvent(event)) continue;
                String step = item.optString("step");
                String round = item.optString("round");
                String sequence = step.isEmpty() ? "" : " · " + step + "/" + round;
                String detail = item.optString("detail");
                String suffix = detail.isEmpty() ? "" : " · " + detail;
                String formatted = item.optString("timestamp_kst") + " · " + executionLabel(event)
                        + sequence + suffix;
                if (matches.size() == limit) matches.removeFirst();
                matches.addLast(formatted);
            } catch (Exception ignored) {
                // Corrupt lines are excluded from the human-readable execution log.
            }
        }
        return new ArrayList<>(matches);
    }

    public synchronized String exportJob(String jobId) {
        StringBuilder result = new StringBuilder();
        for (String line : readJobLines(jobId, Integer.MAX_VALUE)) result.append(line).append('\n');
        return result.toString();
    }

    public synchronized String exportExecution(String jobId) {
        StringBuilder result = new StringBuilder();
        for (String line : readExecutionLines(jobId, Integer.MAX_VALUE)) result.append(line).append('\n');
        return result.toString();
    }

    public synchronized int writeFailureCount() { return writeFailureCount; }

    public synchronized void clear() {
        for (File file : orderedFiles()) {
            if (!file.delete() && file.exists()) writeFailureCount++;
        }
    }

    public static String sanitizeDetail(String detail) {
        if (detail == null || detail.isEmpty()) return "";
        String value = detail.replace('\n', ' ').replace('\r', ' ').trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("http") || lower.contains("chatgpt.com") || lower.contains("automation")
                || lower.contains("cookie") || lower.contains("authorization") || lower.contains("password")
                || lower.contains("session") || lower.contains("token") || lower.contains("prompt")) {
            return "redacted";
        }
        if (!value.matches("[-A-Za-z0-9_.:=;+ /]{0,120}")) return "redacted";
        return value;
    }

    private void appendLine(String line) throws Exception {
        byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
        File current = new File(directory, CURRENT_NAME);
        if (current.exists() && current.length() + bytes.length > MAX_FILE_BYTES) rotate(current);
        try (FileOutputStream output = new FileOutputStream(current, true)) {
            output.write(bytes);
            output.flush();
        }
        trimFiles();
    }

    private void rotate(File current) throws Exception {
        String base = FILE_PREFIX + System.currentTimeMillis();
        File rotated = new File(directory, base + FILE_SUFFIX);
        int suffix = 1;
        while (rotated.exists()) rotated = new File(directory, base + "-" + suffix++ + FILE_SUFFIX);
        if (!current.renameTo(rotated)) throw new IllegalStateException("log rotation unavailable");
    }

    private void trimFiles() {
        File[] files = orderedFiles();
        for (int i = MAX_FILES; i < files.length; i++) {
            if (!files[i].delete() && files[i].exists()) writeFailureCount++;
        }
    }

    private File[] orderedFiles() {
        if (!directory.exists()) return new File[0];
        File[] files = directory.listFiles((dir, name) -> name.equals(CURRENT_NAME)
                || (name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)));
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        return files;
    }

    private static String lifecycle(OrchestrationStore store) {
        if (store == null) return "UNKNOWN";
        if (store.terminal()) return "TERMINAL";
        if (store.waitingForUser()) return "WAITING_USER";
        if (store.paused()) return "PAUSED";
        if (store.active()) return "RUNNING";
        return "IDLE";
    }

    private void appendJobLine(String jobId, String line) throws Exception {
        File file = jobFile(jobId);
        if (file == null) return;
        byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
        if (file.exists() && file.length() + bytes.length > MAX_JOB_FILE_BYTES) trimJobFile(file);
        try (FileOutputStream output = new FileOutputStream(file, true)) {
            output.write(bytes);
            output.flush();
        }
        trimJobFiles();
    }

    private void trimJobFile(File file) throws Exception {
        Deque<String> lines = new ArrayDeque<>();
        long bytes = 0L;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                long next = line.getBytes(StandardCharsets.UTF_8).length + 1L;
                lines.addLast(line);
                bytes += next;
                while (bytes > MAX_JOB_FILE_BYTES / 2L && !lines.isEmpty()) {
                    String removed = lines.removeFirst();
                    bytes -= removed.getBytes(StandardCharsets.UTF_8).length + 1L;
                }
            }
        }
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            for (String line : lines) output.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
    }

    private void trimJobFiles() {
        File[] files = directory.listFiles((dir, name) -> name.startsWith(JOB_PREFIX) && name.endsWith(FILE_SUFFIX));
        if (files == null || files.length <= MAX_JOB_FILES) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).thenComparing(File::getName).reversed());
        for (int index = MAX_JOB_FILES; index < files.length; index++) {
            if (!files[index].delete() && files[index].exists()) writeFailureCount++;
        }
    }

    private File jobFile(String jobId) {
        String safe = safeToken(jobId);
        if (safe.isEmpty() || "redacted".equals(safe)) return null;
        return new File(directory, JOB_PREFIX + safe + FILE_SUFFIX);
    }

    private static void readLines(File file, int limit, Deque<String> lines) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lines.size() == limit) lines.removeFirst();
                lines.addLast(line);
            }
        } catch (Exception ignored) {
            // A damaged per-job log falls back to an empty view instead of affecting autorun.
        }
    }

    private static String timestampOf(String line) {
        try {
            return new JSONObject(line == null ? "{}" : line).optString("timestamp_kst");
        } catch (Exception ignored) {
            return "";
        }
    }

    static boolean isExecutionEvent(String event) {
        if (event == null) return false;
        return event.startsWith("BOOTSTRAP_") || event.startsWith("SIGNAL_")
                || event.startsWith("SCHEDULE_") || event.startsWith("UI_")
                || event.startsWith("RESUME_RECONCILE_") || event.equals("STATE_TRANSITION")
                || event.equals("FAILED") || event.equals("TERMINAL")
                || event.equals("SERVICE_START") || event.equals("SERVICE_STOP")
                || event.equals("AUTH_REQUIRED");
    }

    private static String executionLabel(String event) {
        if (event == null) return "상태 변경";
        return switch (event) {
            case "UI_START" -> "오토런 시작";
            case "BOOTSTRAP_URL_CAPTURED" -> "대화 URL 확보";
            case "BOOTSTRAP_SEQUENCE_SEEDED" -> "첫 Work 단계 준비";
            case "SIGNAL_ACCEPTED" -> "제어 신호 수신";
            case "STATE_TRANSITION" -> "전달 상태 변경";
            case "FAILED" -> "오류로 일시정지";
            case "TERMINAL" -> "작업 완료";
            case "UI_PAUSE" -> "사용자 일시정지";
            case "UI_RESUME" -> "사용자 재개";
            case "UI_STOP" -> "사용자 중지";
            case "AUTH_REQUIRED" -> "로그인 확인 필요";
            default -> event.replace('_', ' ');
        };
    }

    private static String safeToken(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.matches("[A-Za-z0-9._-]{0,64}") ? value : "redacted";
    }

    private static String safeEvent(String value) {
        if (value == null || !value.matches("[A-Z0-9_]{1,64}")) return "UNKNOWN_EVENT";
        return value;
    }
}
