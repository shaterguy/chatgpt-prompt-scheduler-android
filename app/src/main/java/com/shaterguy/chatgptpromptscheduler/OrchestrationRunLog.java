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
    private static final String DIRECTORY_NAME = "orchestration-logs";
    private static final String CURRENT_NAME = "orchestration-current.jsonl";
    private static final String FILE_PREFIX = "orchestration-";
    private static final String FILE_SUFFIX = ".jsonl";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Object FILE_LOCK = new Object();
    private final File directory;
    private int writeFailureCount;

    public OrchestrationRunLog(Context context) {
        directory = new File(context.getNoBackupFilesDir(), DIRECTORY_NAME);
    }

    OrchestrationRunLog(File directory) {
        this.directory = directory;
    }

    /** Logging is deliberately best-effort; telemetry must never stop relay or reservation work. */
    public synchronized void record(OrchestrationStore store, String eventCode, String detail) {
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

    private static String safeToken(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.matches("[A-Za-z0-9._-]{0,64}") ? value : "redacted";
    }

    private static String safeEvent(String value) {
        if (value == null || !value.matches("[A-Z0-9_]{1,64}")) return "UNKNOWN_EVENT";
        return value;
    }
}
