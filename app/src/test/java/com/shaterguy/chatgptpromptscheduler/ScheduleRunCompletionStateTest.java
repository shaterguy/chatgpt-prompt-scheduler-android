package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;

import static org.junit.Assert.*;

public final class ScheduleRunCompletionStateTest {
    @Test public void manualRunPreservesStateEnabledAfterItStarted() {
        Schedule runningSnapshot = new Schedule();
        runningSnapshot.enabled = false;
        runningSnapshot.recurrence = "daily";
        Schedule latestPersisted = new Schedule();
        latestPersisted.enabled = true;
        latestPersisted.recurrence = "daily";
        latestPersisted.prompt = "edited during execution";

        ConfigStore.mergeRunCompletion(latestPersisted, 1234L, "VERIFIED", false);

        assertFalse(runningSnapshot.enabled);
        assertTrue(latestPersisted.enabled);
        assertEquals("edited during execution", latestPersisted.prompt);
        assertEquals(1234L, latestPersisted.lastRunAt);
        assertEquals("VERIFIED", latestPersisted.lastStatus);
    }

    @Test public void manualRunDoesNotReactivateScheduleDisabledDuringExecution() {
        Schedule latest = new Schedule();
        latest.enabled = false;
        ConfigStore.mergeRunCompletion(latest, 2222L, "TIMEOUT", false);
        assertFalse(latest.enabled);
        assertEquals("TIMEOUT", latest.lastStatus);
    }

    @Test public void automaticOnceStillDisablesTheSchedule() {
        Schedule latest = new Schedule();
        latest.enabled = true;
        latest.recurrence = "once";
        ConfigStore.mergeRunCompletion(latest, 3333L, "VERIFIED", true);
        assertFalse(latest.enabled);
    }

    @Test public void changingRepeatRuleDuringExecutionIsRespected() {
        Schedule latest = new Schedule();
        latest.enabled = true;
        latest.recurrence = "weekly";
        ConfigStore.mergeRunCompletion(latest, 4444L, "VERIFIED", true);
        assertTrue(latest.enabled);
    }

    @Test public void executionServiceUsesLatestScheduleInsteadOfSavingRunningSnapshot() throws Exception {
        java.nio.file.Path path = java.nio.file.Path.of(
                "app/src/main/java/com/shaterguy/chatgptpromptscheduler/ExecutionService.java");
        if (!java.nio.file.Files.exists(path)) path = java.nio.file.Path.of("..").resolve(path);
        String source = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(source.contains("configStore.recordRunCompletion("));
        assertFalse(source.contains("configStore.saveSchedule(currentSchedule)"));
    }
}
