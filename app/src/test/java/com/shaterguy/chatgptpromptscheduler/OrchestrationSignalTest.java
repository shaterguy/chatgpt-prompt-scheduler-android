package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OrchestrationSignalTest {
    @Test
    public void parsesExactRouteAndTerminalSignals() {
        OrchestrationSignal toWork = OrchestrationSignal.parse("[AR_SEND_WORK JOB-7 S001 R001]", "JOB-7");
        assertEquals(OrchestrationSignal.Type.SEND_WORK, toWork.type);
        assertEquals("S001", toWork.step);
        assertEquals("R001", toWork.round);
        assertTrue(toWork.routesFrom(OrchestrationStore.SIDE_CHAT));
        assertFalse(toWork.routesFrom(OrchestrationStore.SIDE_WORK));

        OrchestrationSignal done = OrchestrationSignal.parse("[AR_DONE JOB-7]", "JOB-7");
        assertEquals(OrchestrationSignal.Type.DONE, done.type);
    }

    @Test
    public void acceptsOneFinalSignalLineAndRejectsAmbiguousOrMalformedSignals() {
        assertNull(OrchestrationSignal.parse("[AR_SEND_CHAT OTHER S001 R001]", "JOB-7"));
        assertEquals(OrchestrationSignal.Type.DONE,
                OrchestrationSignal.parse("검수 완료\n[AR_DONE JOB-7]", "JOB-7").type);
        assertNull(OrchestrationSignal.parse("[AR_SEND_WORK JOB-7 S001 R001]\n[AR_DONE JOB-7]", "JOB-7"));
        assertNull(OrchestrationSignal.parse("[AR_SEND_WORK JOB-7 S1 R1]", "JOB-7"));
        assertNull(OrchestrationSignal.parse("[AR_SEND_WORK JOB 7 S001 R001]", "JOB-7"));
        assertNull(OrchestrationSignal.parse("[AR_UNKNOWN JOB-7]", "JOB-7"));
    }

    @Test
    public void detectsOlderSequencesAndDirection() {
        OrchestrationSignal olderStep = OrchestrationSignal.parse("[AR_SEND_WORK JOB S001 R999]", "JOB");
        OrchestrationSignal olderRound = OrchestrationSignal.parse("[AR_SEND_CHAT JOB S002 R001]", "JOB");
        OrchestrationSignal current = OrchestrationSignal.parse("[AR_SEND_CHAT JOB S002 R003]", "JOB");
        assertTrue(olderStep.isOlderThan("S002", "R001"));
        assertTrue(olderRound.isOlderThan("S002", "R002"));
        assertFalse(current.isOlderThan("S002", "R002"));
        assertTrue(current.routesFrom(OrchestrationStore.SIDE_WORK));
        assertTrue(OrchestrationSignal.parse("[AR_SEND_CHAT JOB S002 R002]", "JOB")
                .isValidNextRoute(OrchestrationStore.SIDE_WORK, "S002", "R002"));
        assertFalse(OrchestrationSignal.parse("[AR_SEND_WORK JOB S002 R002]", "JOB")
                .isValidNextRoute(OrchestrationStore.SIDE_CHAT, "S002", "R002"));
        assertTrue(OrchestrationSignal.parse("[AR_SEND_WORK JOB S001 R001]", "JOB")
                .isValidNextRoute(OrchestrationStore.SIDE_CHAT, "", ""));
        assertFalse(OrchestrationSignal.parse("[AR_SEND_WORK JOB S002 R001]", "JOB")
                .isValidNextRoute(OrchestrationStore.SIDE_CHAT, "", ""));
        assertTrue(OrchestrationSignal.parse("[AR_SEND_WORK JOB S002 R003]", "JOB")
                .isValidNextRoute(OrchestrationStore.SIDE_CHAT, "S002", "R002"));
        assertTrue(OrchestrationSignal.parse("[AR_SEND_WORK JOB S003 R001]", "JOB")
                .isValidNextRoute(OrchestrationStore.SIDE_CHAT, "S002", "R002"));
        assertFalse(OrchestrationSignal.parse("[AR_SEND_WORK JOB S004 R001]", "JOB")
                .isValidNextRoute(OrchestrationStore.SIDE_CHAT, "S002", "R002"));
    }

    @Test
    public void scriptsGuardHostDeduplicateAndWaitForStableAssistant() {
        String prompt = "[AUTOMATION_START JOB-7]";
        String prepare = OrchestrationScript.prepare(prompt);
        String commit = OrchestrationScript.commit(prompt);
        String recovery = OrchestrationScript.recoverSubmission(prompt);
        String observe = OrchestrationScript.observe(prompt);
        assertTrue(prepare.contains("validHost"));
        assertTrue(prepare.contains("ALREADY_SUBMITTED"));
        assertTrue(prepare.contains("DRAFT_PRESENT"));
        assertTrue(prepare.contains("READY"));
        assertFalse(prepare.contains("send.click()"));
        assertTrue(commit.contains("send.click()"));
        assertTrue(commit.contains("composer.closest('form')"));
        assertTrue(recovery.contains("자동 재전송하지 않습니다."));
        assertTrue(observe.contains("stop-button"));
        assertTrue(observe.contains("CANDIDATE"));
        assertTrue(observe.contains("65536"));
        assertTrue(observe.contains("Math.imul"));
        assertTrue(observe.contains(OrchestrationScript.jsQuote(prompt)));
    }

    @Test
    public void relayConfigRejectsSameConversationAndNonStandardPorts() {
        assertEquals("", OrchestrationStore.configError(
                "https://chatgpt.com/c/chat-1", "https://chatgpt.com/c/work-1", "JOB-8"));
        assertTrue(OrchestrationStore.configError(
                "https://chatgpt.com/c/same", "https://www.chatgpt.com/c/same?view=work", "JOB-8")
                .contains("서로 다른 대화"));
        assertFalse(OrchestrationStore.isAllowedRelayUrl("https://chatgpt.com:444/c/chat-1"));
        assertFalse(OrchestrationStore.isAllowedRelayUrl("https://user@chatgpt.com/c/chat-1"));
    }
}
