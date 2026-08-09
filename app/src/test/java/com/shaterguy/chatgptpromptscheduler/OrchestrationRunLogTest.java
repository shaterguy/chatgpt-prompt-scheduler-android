package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;

import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class OrchestrationRunLogTest {
    @Test
    public void telemetryDetailsRejectSensitiveOrFreeFormValues() {
        assertEquals("tier=2;delay_ms=10000", OrchestrationRunLog.sanitizeDetail("tier=2;delay_ms=10000"));
        assertEquals("redacted", OrchestrationRunLog.sanitizeDetail("https://chatgpt.com/c/secret"));
        assertEquals("redacted", OrchestrationRunLog.sanitizeDetail("raw prompt text"));
        assertEquals("redacted", OrchestrationRunLog.sanitizeDetail("오류 원문"));
        assertEquals("mode.current=work", OrchestrationRunLog.sanitizeDetail("mode.current=work"));
        assertEquals("requested=sol;current=sol;readback=selected_option_readback",
                OrchestrationRunLog.sanitizeDetail(
                        "requested=sol;current=sol;readback=selected_option_readback"));
        assertEquals("requested=xhigh;current=xhigh;readback=trigger_readback",
                OrchestrationRunLog.sanitizeDetail(
                        "requested=xhigh;current=xhigh;readback=trigger_readback"));
    }

    @Test
    public void jsonlSchemaAndWriteFailureCannotEscapeRelay() throws Exception {
        File directory = Files.createTempDirectory("orchestration-log-schema").toFile();
        File blocked = new File(directory, "blocked");
        assertTrue(blocked.createNewFile());
        try {
            OrchestrationRunLog blockedLog = new OrchestrationRunLog(blocked);
            blockedLog.record("AR-TEST", "S001", "R001", "SERVICE_START", "CHAT",
                    "WAITING_RESPONSE", "RUNNING", "source=unit");
            assertEquals(1, blockedLog.writeFailureCount());

            OrchestrationRunLog log = new OrchestrationRunLog(directory);
            log.record("AR-TEST", "S001", "R001", "SERVICE_START", "CHAT",
                    "WAITING_RESPONSE", "RUNNING", "source=unit");
            List<String> lines = log.readRecentLines(1);
            assertEquals(1, lines.size());
            JSONObject item = new JSONObject(lines.get(0));
            assertEquals("AR-TEST", item.getString("job_id"));
            assertEquals("S001", item.getString("step"));
            assertEquals("R001", item.getString("round"));
            assertEquals("SERVICE_START", item.getString("event_code"));
            assertEquals("source=unit", item.getString("detail"));
            assertTrue(item.getString("timestamp_kst").contains("+09:00"));
        } finally {
            File[] files = directory.listFiles();
            if (files != null) for (File file : files) file.delete();
            directory.delete();
        }
    }

    @Test
    public void jsonlRotationKeepsFileAndCountBounds() throws Exception {
        File directory = Files.createTempDirectory("orchestration-log-test").toFile();
        try {
            OrchestrationRunLog log = new OrchestrationRunLog(directory);
            for (int i = 0; i < 20_000; i++) {
                log.record("AR-TEST", "S001", "R001", "POLL_EVALUATE", "CHAT",
                        "WAITING_RESPONSE", "RUNNING", "count=" + i + ";tier=3");
            }
            File[] files = directory.listFiles((dir, name) -> name.startsWith("orchestration-") && name.endsWith(".jsonl"));
            assertTrue(files != null && files.length <= OrchestrationRunLog.MAX_FILES);
            for (File file : files) assertTrue(file.length() <= OrchestrationRunLog.MAX_FILE_BYTES);
            assertTrue(log.readRecentLines(10).size() == 10);
        } finally {
            File[] files = directory.listFiles();
            if (files != null) for (File file : files) file.delete();
            directory.delete();
        }
    }

    @Test
    public void perJobExecutionAndDebugLogsStaySeparated() throws Exception {
        File directory = Files.createTempDirectory("orchestration-job-log-test").toFile();
        try {
            OrchestrationRunLog log = new OrchestrationRunLog(directory);
            log.record("AR-A", "S001", "R001", "POLL_EVALUATE", "CHAT",
                    "WAITING_RESPONSE", "RUNNING", "count=1;tier=0");
            log.record("AR-A", "S001", "R001", "SIGNAL_ACCEPTED", "CHAT",
                    "WAITING_RESPONSE", "RUNNING", "type=SEND_WORK");
            log.record("AR-B", "S002", "R001", "FAILED", "WORK",
                    "FAILED", "PAUSED", "code=NETWORK_ERROR");

            assertEquals(2, log.readJobLines("AR-A", 20).size());
            assertEquals(1, log.readJobLines("AR-B", 20).size());
            List<String> execution = log.readExecutionLines("AR-A", 20);
            assertEquals(1, execution.size());
            assertTrue(execution.get(0).contains("제어 신호 수신"));
            assertFalse(execution.get(0).contains("POLL_EVALUATE"));
            assertTrue(log.exportJob("AR-A").contains("POLL_EVALUATE"));
        } finally {
            File[] files = directory.listFiles();
            if (files != null) for (File file : files) file.delete();
            directory.delete();
        }
    }
}
