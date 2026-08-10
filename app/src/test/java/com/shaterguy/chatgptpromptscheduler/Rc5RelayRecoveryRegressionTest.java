package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Rc5RelayRecoveryRegressionTest {
    private static String source(String relative) throws Exception {
        Path path = Path.of("src/main/java/com/shaterguy/chatgptpromptscheduler/" + relative);
        if (!Files.exists(path)) path = Path.of("app/src/main/java/com/shaterguy/chatgptpromptscheduler/" + relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void reconciliationWaitsForHydratedHistoryBeforeNoSignalDecision() {
        String scan = OrchestrationScript.reconcileScan("JOB-7");
        assertTrue(scan.contains("history_ready:false"));
        assertTrue(scan.contains("job_prompt_turns"));
        assertTrue(scan.contains("assistantTurns===0"));
        assertTrue(scan.contains("history_ready:true"));
    }

    @Test
    public void serviceSeparatesTransientRecoveryFromTrueNoSignalBudget() throws Exception {
        String service = source("OrchestrationService.java");
        assertTrue(service.contains("TRANSIENT_NETWORK_RECOVERY"));
        assertTrue(service.contains("recoverTransientNetwork(\"WEBVIEW_MAIN_FRAME\")"));
        assertTrue(service.contains("RESUME_ROOM_NOT_READY"));
        assertTrue(service.contains("reconciliationRescanAttempts++"));
        assertFalse(service.contains("store.pollCountLong() >= MAX_NO_SIGNAL_RECONCILIATION_RETRIES"));
    }

    @Test
    public void ordinaryNoSignalGetsExactlyOneSameRoomRetryPath() throws Exception {
        String service = source("OrchestrationService.java");
        String store = source("OrchestrationStore.java");
        assertTrue(store.contains("SIGNAL_RETRY_PROMPT"));
        assertTrue(store.contains("prepareSignalRetry"));
        assertTrue(service.contains("SIGNAL_RETRY_REQUESTED"));
        assertTrue(service.contains("SIGNAL_RETRY_EXHAUSTED"));
        assertTrue(service.contains("lastDeliveryWasSignalRetry"));
    }
}
