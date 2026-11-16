package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DisabledScheduleSkipContractTest {
    @Test public void disabledAlarmIsIgnoredBeforeAddingToQueue() throws Exception {
        String receiver = source("AlarmReceiver");
        int guard = receiver.indexOf("if (schedule != null && !schedule.enabled) return;");
        assertTrue(guard >= 0 && guard < receiver.indexOf("queueStore.enqueue("));
    }

    @Test public void disabledAutomaticQueueItemIsDiscardedBeforeAndAfterProfilePreparation()
            throws Exception {
        String service = source("ExecutionService");
        assertTrue(service.contains("""
                    Schedule queuedSchedule = currentItem.optBoolean("manual", false) ? null
                            : configStore.findSchedule(currentItem.optString("scheduleId"));
                    if (queuedSchedule != null && !queuedSchedule.enabled) {
                        discardClaimedItem();
                        return;
                    }
                    acquireProfilePreparationWakeLock();""".indent(12).stripTrailing()));
        assertTrue(service.contains("""
                    if (!currentItem.optBoolean("manual", false) && !currentSchedule.enabled) {
                        discardClaimedItem();
                        return;
                    }""".indent(8).stripTrailing()));
        assertFalse(service.contains("SCHEDULE_DISABLED"));
    }

    private static String source(String name) throws Exception {
        Path path = Path.of("app/src/main/java/com/shaterguy/chatgptpromptscheduler", name + ".java");
        if (!Files.exists(path)) path = Path.of("..").resolve(path);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
