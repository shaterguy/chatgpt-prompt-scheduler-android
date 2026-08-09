package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

/** Guards user-visible lifecycle rules that also require durable SharedPreferences writes. */
public class AutorunUxStatePolicyTest {
    @Test
    public void nonRunningRecoveryStatesReleaseTheSingleAutorunSlot() throws Exception {
        String source = source("OrchestrationStore.java");
        assertTrue(method(source, "public void waitForUser", "public boolean resolveUserAction")
                .contains("putBoolean(\"active\", false)"));
        assertTrue(method(source, "public void fail", "public void ambiguous")
                .contains("putBoolean(\"active\", false)"));
        assertTrue(method(source, "public void ambiguous", "public boolean resume")
                .contains("putBoolean(\"active\", false)"));
        assertTrue(method(source, "public void reconciliationAmbiguous", "public void finish")
                .contains("putBoolean(\"active\", false)"));
    }

    @Test
    public void userStopIsTerminalAndCannotBeConfusedWithPause() throws Exception {
        String source = source("OrchestrationStore.java");
        String stop = method(source, "public void stop()", "public static boolean stopWorkspace");
        assertTrue(stop.contains("putBoolean(\"paused\", false)"));
        assertTrue(stop.contains("putBoolean(\"terminal\", true)"));
        assertTrue(stop.contains("putBoolean(\"userStopped\", true)"));
        assertTrue(stop.contains("BOOTSTRAP_STOPPED"));
    }

    @Test
    public void activeJobSwitchNeverAutomaticallyStopsAndRestartsServices() throws Exception {
        String source = source("OrchestrationActivity.java");
        String resumeArchived = method(source, "private void resumeArchivedJob",
                "private void restoreAndResumeArchivedJob");
        assertTrue(resumeArchived.contains("현재 작업 열기"));
        assertFalse(resumeArchived.contains("store.pause("));
        assertFalse(resumeArchived.contains("stopService("));
        assertFalse(source.contains("전환 및 재개"));
    }

    @Test
    public void stoppedAndMissingJobsHaveFailClosedUiPaths() throws Exception {
        String source = source("OrchestrationActivity.java");
        assertTrue(source.contains("private boolean isLiveJobMode() { return !newJobMode && archivedJob == null && !missingJob; }"));
        assertTrue(source.contains("중지하면 이 Job은 앱에서 다시 재개할 수 없습니다"));
        assertTrue(source.contains("현재 Job의 상태는 대신 표시하지 않습니다"));
    }

    @Test
    public void verifiedWorkPreferencesLeaveRequestedAndActualDebugEvidence() throws Exception {
        String source = source("OrchestrationService.java");
        assertTrue(source.contains("WORK_PREFERENCES_VERIFIED"));
        assertTrue(source.contains("WORK_MODEL_VERIFIED"));
        assertTrue(source.contains("WORK_REASONING_VERIFIED"));
        assertTrue(source.contains(";current="));
        assertTrue(source.contains(";readback="));
        assertTrue(source.contains("selected_option_readback"));
        assertTrue(source.contains("safeDiagnosticValue(requestedModel)"));
        assertTrue(source.contains("[A-Za-z0-9_.-]{1,64}"));
    }

    private static String method(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        if (start < 0 || end < 0) throw new AssertionError("method boundary not found: " + startToken);
        return source.substring(start, end);
    }

    private static String source(String name) throws Exception {
        Path path = Path.of("src/main/java/com/shaterguy/chatgptpromptscheduler/" + name);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
