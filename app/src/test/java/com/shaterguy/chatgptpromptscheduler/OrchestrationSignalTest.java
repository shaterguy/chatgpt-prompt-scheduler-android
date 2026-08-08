package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

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
        assertTrue(OrchestrationStore.isTerminalSignal(OrchestrationSignal.Type.DONE));
        assertTrue(OrchestrationStore.isTerminalSignal(OrchestrationSignal.Type.ABORTED));
        assertTrue(OrchestrationStore.isTerminalSignal(OrchestrationSignal.Type.PAUSE));
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
    public void detailedParserClassifiesSafeFailureCodesWithoutResponseBody() {
        assertEquals(OrchestrationSignal.ErrorCode.NO_SIGNAL,
                OrchestrationSignal.parseDetailed("정상 본문만 있음", "JOB-7").errorCode);
        assertEquals(OrchestrationSignal.ErrorCode.WRONG_JOB,
                OrchestrationSignal.parseDetailed("[AR_SEND_CHAT OTHER S001 R001]", "JOB-7").errorCode);
        assertEquals(OrchestrationSignal.ErrorCode.PARSE_FAILED,
                OrchestrationSignal.parseDetailed("[AR_SEND_CHAT JOB-7 S1 R1]", "JOB-7").errorCode);
        assertEquals(OrchestrationSignal.ErrorCode.PARSE_FAILED,
                OrchestrationSignal.parseDetailed("[AR_SEND_WORK JOB-7 S001 R001]\n[AR_DONE JOB-7]", "JOB-7").errorCode);
    }

    @Test
    public void validatorSeparatesDuplicateStaleDirectionSequenceAndWorkTerminal() {
        assertEquals(OrchestrationSignal.ErrorCode.DUPLICATE,
                OrchestrationSignal.validate("[AR_SEND_CHAT JOB S002 R002]", "JOB",
                        OrchestrationStore.SIDE_WORK, "S002", "R002",
                        "[AR_SEND_CHAT JOB S002 R002]").errorCode);
        assertEquals(OrchestrationSignal.ErrorCode.STALE,
                OrchestrationSignal.validate("[AR_SEND_CHAT JOB S001 R001]", "JOB",
                        OrchestrationStore.SIDE_WORK, "S002", "R002", "").errorCode);
        assertEquals(OrchestrationSignal.ErrorCode.WRONG_DIRECTION,
                OrchestrationSignal.validate("[AR_SEND_WORK JOB S001 R001]", "JOB",
                        OrchestrationStore.SIDE_WORK, "", "", "").errorCode);
        assertEquals(OrchestrationSignal.ErrorCode.WRONG_STEP_ROUND,
                OrchestrationSignal.validate("[AR_SEND_WORK JOB S009 R001]", "JOB",
                        OrchestrationStore.SIDE_CHAT, "", "", "").errorCode);
        assertEquals(OrchestrationSignal.ErrorCode.WORK_TERMINAL,
                OrchestrationSignal.validate("[AR_DONE JOB]", "JOB",
                        OrchestrationStore.SIDE_WORK, "S001", "R001", "").errorCode);
    }

    @Test
    public void parsesUserActionRequiredOnlyFromGeneralChat() {
        OrchestrationSignal action = OrchestrationSignal.parse(
                "[AR_USER_ACTION_REQUIRED JOB-7 S001 R001 MAIL-VERIFY-1]", "JOB-7");
        assertEquals(OrchestrationSignal.Type.USER_ACTION_REQUIRED, action.type);
        assertEquals("MAIL-VERIFY-1", action.actionId);
        assertTrue(action.routesFrom(OrchestrationStore.SIDE_CHAT));
        assertFalse(action.routesFrom(OrchestrationStore.SIDE_WORK));
        assertTrue(action.isValidNextRoute(OrchestrationStore.SIDE_CHAT, "", ""));
        assertEquals("[AUTOMATION_USER_RESOLVED JOB-7 MAIL-VERIFY-1]",
                OrchestrationStore.userResolvedPrompt("JOB-7", "MAIL-VERIFY-1"));
    }

    @Test
    public void routeSignalsProduceExactOppositeConversationPrompts() {
        OrchestrationSignal toWork = OrchestrationSignal.parse("[AR_SEND_WORK JOB S001 R001]", "JOB");
        OrchestrationSignal toChat = OrchestrationSignal.parse("[AR_SEND_CHAT JOB S001 R001]", "JOB");
        assertEquals("[AUTOMATION_WORK_STEP JOB S001 R001]", OrchestrationStore.promptFor(toWork));
        assertEquals("[AUTOMATION_CHAT_REVIEW JOB S001 R001]", OrchestrationStore.promptFor(toChat));
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
        String confirmation = OrchestrationScript.confirmSubmission(prompt);
        String observe = OrchestrationScript.observe(prompt);
        assertTrue(prepare.contains("validHost"));
        assertTrue(prepare.contains("ALREADY_SUBMITTED"));
        assertTrue(prepare.contains("DRAFT_PRESENT"));
        assertTrue(prepare.contains("READY"));
        assertFalse(prepare.contains("send.click()"));
        assertTrue(commit.contains("send.click()"));
        assertTrue(commit.contains("composer.closest('form')"));
        assertTrue(recovery.contains("RECOVERY_ABSENT"));
        assertFalse(confirmation.contains("send.click()"));
        assertTrue(confirmation.contains("전송된 사용자 턴을 확인했습니다."));
        assertTrue(confirmation.contains("전송된 사용자 턴 반영 대기"));
        assertTrue(observe.contains("stop-button"));
        assertTrue(observe.contains("data-is-streaming"));
        assertTrue(observe.contains("USER_TURN_MISSING"));
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

    @Test
    public void serviceHasNoElapsedPollTimeoutAndKeepsScheduleGate() throws Exception {
        Path source = Path.of("src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationService.java");
        if (!Files.exists(source)) source = Path.of("app").resolve(source);
        String service = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        assertFalse(service.contains("MAX_POLLS"));
        assertFalse(service.contains("pollCount() >="));
        assertTrue(service.contains("Elapsed time is telemetry only"));
        assertTrue(service.contains("scheduleHasPriority()"));
        assertTrue(service.contains("store.markSubmitting()"));
        assertTrue(service.contains("store.ensureStampedPrompt()"));
        assertTrue(service.contains("store.deliveryPrompt()"));
        assertFalse(service.contains("OrchestrationScript.prepare(store.pendingPrompt())"));
        assertFalse(service.contains("OrchestrationScript.observe(store.pendingPrompt())"));
        assertTrue(service.contains("recoverSubmission"));
        assertTrue(service.contains("confirmSubmission"));
        assertTrue(service.contains("DOM_COMPOSER_NOT_FOUND"));
        assertTrue(service.contains("recoveryProbeStartedAt"));
        assertTrue(service.contains("fingerprint.matches"));
        assertTrue(service.contains("store.observeCandidate(fingerprint) < 3"));
    }
    @Test
    public void authRequiredUsesVisibleStructuralGateInsteadOfPageText() {
        String prepare = OrchestrationScript.prepare("[AUTOMATION_START JOB-7]");
        String observe = OrchestrationScript.observe("[AUTOMATION_START JOB-7]");
        assertTrue(prepare.contains("visibleAuthGate"));
        assertTrue(prepare.contains("hasConversation"));
        assertTrue(prepare.contains("AUTH_REQUIRED"));
        assertTrue(observe.contains("visibleAuthGate"));
        assertFalse(prepare.contains("document.body?.innerText"));
        assertFalse(prepare.contains("body.includes('log in')"));
        assertFalse(observe.contains("pageBody.includes"));
    }

    @Test
    public void startAndResumePreferDurableStateAndDoNotFailClosedOnAlerts() throws Exception {
        Path activityPath = Path.of("src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationActivity.java");
        Path storePath = Path.of("src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationStore.java");
        Path servicePath = Path.of("src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationService.java");
        if (!Files.exists(activityPath)) activityPath = Path.of("app").resolve(activityPath);
        if (!Files.exists(storePath)) storePath = Path.of("app").resolve(storePath);
        if (!Files.exists(servicePath)) servicePath = Path.of("app").resolve(servicePath);
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        String store = new String(Files.readAllBytes(storePath), StandardCharsets.UTF_8);
        String service = new String(Files.readAllBytes(servicePath), StandardCharsets.UTF_8);

        assertFalse(activity.contains("ensureNotifications"));
        assertFalse(activity.contains("store.newRunError"));
        assertFalse(activity.contains("실행 설정이 변경되었습니다"));
        assertFalse(activity.contains("store.pendingPrompt().isEmpty()"));
        assertTrue(activity.contains("restoreDurableRunConfiguration"));
        assertTrue(activity.contains("store.resumeBlockReason"));
        assertTrue(activity.contains("NonCredentialEditText"));
        assertTrue(activity.contains("getAutofillType"));
        assertTrue(activity.contains("AUTOFILL_TYPE_NONE"));
        assertTrue(activity.contains("setImportantForContentCapture"));
        assertFalse(service.contains("NOTIFICATION_DISABLED"));
        assertTrue(service.contains("오류 알림 꺼짐"));
        assertFalse(store.contains("usedJobIds.contains(candidate)"));
        assertTrue(store.contains("restored = DELIVERY_SUBMITTING"));
        assertTrue(store.contains("자동 재전송 없음"));
    }

}
