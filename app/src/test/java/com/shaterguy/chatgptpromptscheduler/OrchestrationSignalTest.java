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
    public void bootstrapRebasesFirstGeneralChatSignalThenLeavesStrictValidationAvailable() {
        assertTrue(OrchestrationSignal.validateBootstrap(
                "[AR_SEND_WORK JOB S004 R003]", "JOB", OrchestrationStore.SIDE_CHAT, "").isValid());
        assertTrue(OrchestrationSignal.validateBootstrap(
                "[AR_SEND_WORK JOB S001 R001]", "JOB", OrchestrationStore.SIDE_CHAT, "").isValid());
        assertTrue(OrchestrationSignal.validateBootstrap(
                "[AR_CONTINUE_SAME JOB S004 R003]", "JOB", OrchestrationStore.SIDE_CHAT, "").isValid());
        assertTrue(OrchestrationSignal.validateBootstrap(
                "[AR_USER_ACTION_REQUIRED JOB S004 R003 VERIFY]", "JOB",
                OrchestrationStore.SIDE_CHAT, "").isValid());
        assertTrue(OrchestrationSignal.validateBootstrap(
                "[AR_DONE JOB]", "JOB", OrchestrationStore.SIDE_CHAT, "").isValid());

        assertEquals(OrchestrationSignal.ErrorCode.WRONG_DIRECTION,
                OrchestrationSignal.validateBootstrap("[AR_SEND_CHAT JOB S004 R003]", "JOB",
                        OrchestrationStore.SIDE_CHAT, "").errorCode);
        assertEquals(OrchestrationSignal.ErrorCode.WRONG_DIRECTION,
                OrchestrationSignal.validateBootstrap("[AR_SEND_WORK JOB S004 R003]", "JOB",
                        OrchestrationStore.SIDE_WORK, "").errorCode);
        assertEquals(OrchestrationSignal.ErrorCode.WRONG_JOB,
                OrchestrationSignal.validateBootstrap("[AR_SEND_WORK OTHER S004 R003]", "JOB",
                        OrchestrationStore.SIDE_CHAT, "").errorCode);
        assertEquals(OrchestrationSignal.ErrorCode.DUPLICATE,
                OrchestrationSignal.validateBootstrap("[AR_SEND_WORK JOB S004 R003]", "JOB",
                        OrchestrationStore.SIDE_CHAT, "[AR_SEND_WORK JOB S004 R003]").errorCode);

        // Once seeded at S004/R003, the original strict validator rejects a non-next route.
        assertEquals(OrchestrationSignal.ErrorCode.WRONG_STEP_ROUND,
                OrchestrationSignal.validate("[AR_SEND_WORK JOB S006 R001]", "JOB",
                        OrchestrationStore.SIDE_CHAT, "S004", "R003", "").errorCode);
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
    public void validatesSameSideContinuationWithoutChangingSequence() {
        OrchestrationSignal same = OrchestrationSignal.parse(
                "[AR_CONTINUE_SAME JOB-7 S002 R003]", "JOB-7");
        assertEquals(OrchestrationSignal.Type.CONTINUE_SAME, same.type);
        assertTrue(same.routesFrom(OrchestrationStore.SIDE_CHAT));
        assertTrue(same.routesFrom(OrchestrationStore.SIDE_WORK));
        assertTrue(same.isValidNextRoute(OrchestrationStore.SIDE_CHAT, "S002", "R003"));
        assertFalse(same.isValidNextRoute(OrchestrationStore.SIDE_CHAT, "S002", "R004"));
        assertEquals(OrchestrationSignal.ErrorCode.WRONG_DIRECTION,
                OrchestrationSignal.validate("[AR_CONTINUE_SAME JOB-7 S002 R003]", "JOB-7",
                        "INVALID", "S002", "R003", "").errorCode);
        assertEquals(OrchestrationSignal.ErrorCode.DUPLICATE,
                OrchestrationSignal.validate("[AR_CONTINUE_SAME JOB-7 S002 R003]", "JOB-7",
                        OrchestrationStore.SIDE_WORK, "S002", "R003",
                        "[AR_CONTINUE_SAME JOB-7 S002 R003]", 4L, 4L).errorCode);
        assertTrue(OrchestrationSignal.validate("[AR_CONTINUE_SAME JOB-7 S002 R003]", "JOB-7",
                OrchestrationStore.SIDE_WORK, "S002", "R003",
                "[AR_CONTINUE_SAME JOB-7 S002 R003]", 5L, 4L).isValid());
    }

    @Test
    public void routeSignalsProduceExactOppositeConversationPrompts() {
        OrchestrationSignal toWork = OrchestrationSignal.parse("[AR_SEND_WORK JOB S001 R001]", "JOB");
        OrchestrationSignal toChat = OrchestrationSignal.parse("[AR_SEND_CHAT JOB S001 R001]", "JOB");
        OrchestrationSignal same = OrchestrationSignal.parse("[AR_CONTINUE_SAME JOB S001 R001]", "JOB");
        assertEquals("[AUTOMATION_WORK_STEP JOB S001 R001]", OrchestrationStore.promptFor(toWork));
        assertEquals("[AUTOMATION_CHAT_REVIEW JOB S001 R001]", OrchestrationStore.promptFor(toChat));
        assertEquals("[AUTOMATION_CONTINUE_SAME JOB S001 R001]", OrchestrationStore.promptFor(same));
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
        String stopGeneration = OrchestrationScript.stopGeneration();
        String observe = OrchestrationScript.observe(prompt);
        assertTrue(prepare.contains("validHost"));
        assertTrue(prepare.contains("ALREADY_SUBMITTED"));
        assertTrue(prepare.contains("DRAFT_PRESENT"));
        assertTrue(prepare.contains("draft_length"));
        assertTrue(prepare.contains("main form [contenteditable=\"true\"][data-lexical-editor=\"true\"]"));
        assertFalse(prepare.contains("','[contenteditable=\"true\"][data-lexical-editor=\"true\"]'"));
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
        assertTrue(stopGeneration.contains("STOP_GENERATION_CLICKED"));
        assertTrue(stopGeneration.contains("STOP_GENERATION_AMBIGUOUS"));
        assertTrue(stopGeneration.contains("stop-button"));
        assertTrue(observe.contains("assistant_present"));
        assertTrue(observe.contains("stop_available"));
    }

    @Test
    public void initialStartOverwritesDraftAndRequiresANewUserTurn() {
        String prompt = "[AUTOMATION_START JOB-7]";
        String prepare = OrchestrationScript.prepareInitialStart(prompt);
        String commit = OrchestrationScript.commitInitialStart(prompt);
        String recover = OrchestrationScript.recoverInitialStartSubmission(prompt, 2);
        String confirm = OrchestrationScript.confirmInitialStartSubmission(prompt, 2);

        assertTrue(prepare.contains("시작 문구로 입력창 덮어쓰기"));
        assertTrue(prepare.contains("descriptor.set.call(composer,expected)"));
        assertTrue(prepare.contains("range.selectNodeContents(composer)"));
        assertTrue(prepare.contains("execCommand('delete'"));
        assertTrue(prepare.contains("matching_user_turns"));
        assertFalse(prepare.contains("DRAFT_PRESENT"));
        assertFalse(prepare.contains("ALREADY_SUBMITTED"));
        assertTrue(commit.contains("send.click()"));
        assertFalse(commit.contains("ALREADY_SUBMITTED"));
        assertTrue(recover.contains("const baseline=2"));
        assertTrue(recover.contains("matching>baseline"));
        assertTrue(recover.contains("RECOVERY_ABSENT"));
        assertTrue(confirm.contains("matching>baseline"));
        assertTrue(confirm.contains("return out('RETRY'"));

        // All later deliveries retain the conservative draft and duplicate guards.
        String normalPrepare = OrchestrationScript.prepare("[AUTOMATION_WORK_STEP JOB-7 S001 R001]");
        assertTrue(normalPrepare.contains("DRAFT_PRESENT"));
        assertTrue(normalPrepare.contains("ALREADY_SUBMITTED"));
    }

    @Test
    public void serviceScopesForceStartBeforeNormalRelayAndKeepsSchedulePriority() throws Exception {
        Path source = Path.of("src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationService.java");
        Path storeSource = Path.of("src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationStore.java");
        if (!Files.exists(source)) source = Path.of("app").resolve(source);
        if (!Files.exists(storeSource)) storeSource = Path.of("app").resolve(storeSource);
        String service = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        String store = new String(Files.readAllBytes(storeSource), StandardCharsets.UTF_8);
        assertTrue(service.contains("store.initialStartPending()"));
        assertTrue(service.contains("prepareInitialStart"));
        assertTrue(service.contains("commitInitialStart"));
        assertTrue(service.contains("confirmInitialStartSubmission"));
        assertTrue(service.contains("matchesConversationIdentity"));
        assertTrue(service.contains("acceptInitialStartTargetIfNeeded"));
        assertTrue(service.contains("reloadInitialStartTarget(\"page_finish\")"));
        assertTrue(service.contains("reloadInitialStartTarget(\"step_guard\")"));
        assertTrue(service.contains("INITIAL_START_TRANSIENT_ROUTE"));
        assertTrue(service.contains("validateBootstrap"));
        assertTrue(service.contains("BOOTSTRAP_SEQUENCE_SEEDED"));
        assertTrue(service.contains("continueSameBootstrap"));
        assertTrue(service.contains("initialTargetPolling.onRetry"));
        assertTrue(service.contains("handler.postDelayed(initialTargetReloadRunnable"));
        assertTrue(service.contains("initialTargetReloadScheduled"));
        assertTrue(store.contains("boolean confirmedInitialStart = initialStartPending()"));
        assertTrue(store.contains("putBoolean(\"bootstrapSignalPending\", true)"));
        assertTrue(store.contains("continueSameBootstrap"));
        assertTrue(service.contains("scheduleHasPriority()"));
        assertTrue(service.indexOf("if (scheduleHasPriority())") < service.indexOf("prepareInitialStart"));
    }

    @Test
    public void resumeScriptsScanBothRoomsAndNeverSubmitDuringReconciliation() {
        String scan = OrchestrationScript.reconcileScan("JOB-7");
        String target = OrchestrationScript.reconcileTarget(
                "[AUTOMATION_WORK_STEP JOB-7 S004 R002]", "JOB-7");
        assertTrue(scan.contains("candidates"));
        assertTrue(scan.contains("predecessor_index"));
        assertTrue(scan.contains("AUTOMATION_CHAT_REVIEW"));
        assertTrue(scan.contains("predecessor_signal"));
        assertTrue(scan.contains("querySelectorAll('pre,code,blockquote')"));
        assertTrue(scan.contains("generating"));
        assertFalse(scan.contains("send.click()"));
        assertTrue(target.contains("TARGET_PROMPT_ABSENT"));
        assertTrue(target.contains("TARGET_PROMPT_PRESENT_NO_RESPONSE"));
        assertTrue(target.contains("TARGET_PROMPT_PRESENT_WITH_RESPONSE"));
        assertTrue(target.contains("TARGET_PROMPT_MULTIPLE"));
        assertFalse(target.contains("send.click()"));
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
        assertTrue(service.contains("SystemClock.elapsedRealtime()"));
        assertTrue(service.contains("ResponseTimingPolicy.HARD_FALLBACK_MS"));
        assertTrue(service.contains("STOP_GENERATION_CONFIRMED"));
        assertTrue(service.contains("REBOOT_TIMEBASE_RESET"));
        assertTrue(service.contains("CONTINUE_SAME_DELIVERY"));
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
        assertTrue(activity.contains("store.beginAutomatic"));
        assertFalse(activity.contains("일반 Chat 대화 URL"));
        assertFalse(activity.contains("Work 대화 URL"));
        assertFalse(activity.contains("Job ID\", store.jobId"));
        assertTrue(activity.contains("store.beginReconciliation"));
        assertTrue(activity.contains("RESUME_RECONCILE_STARTED"));
        assertTrue(activity.contains("NonCredentialEditText"));
        assertTrue(activity.contains("getAutofillType"));
        assertTrue(activity.contains("AUTOFILL_TYPE_NONE"));
        assertTrue(activity.contains("setImportantForContentCapture"));
        assertFalse(service.contains("NOTIFICATION_DISABLED"));
        assertTrue(service.contains("오류 알림 꺼짐"));
        assertFalse(store.contains("usedJobIds.contains(candidate)"));
        assertTrue(store.contains("restored = DELIVERY_SUBMITTING"));
        assertTrue(store.contains("reconciling()"));
        assertTrue(store.contains("자동 재전송 없음"));
        assertTrue(service.contains("reconcileScan"));
        assertTrue(service.contains("reconcileTarget"));
        assertTrue(service.contains("SIGNAL_SELECTED"));
        assertTrue(service.contains("TARGET_PROMPT_ALREADY_PRESENT"));
        assertTrue(service.contains("RESUME_ROOM_SCAN_CHAT"));
        assertTrue(service.contains("RESUME_ROOM_SCAN_WORK"));
        assertTrue(service.contains("RESUME_RECONCILE_AMBIGUOUS"));
        assertTrue(service.contains("RECONCILIATION_CONFIRM_ROOMS"));
        assertTrue(service.contains("RESUME_STABLE_IDLE_CONFIRMED"));
        assertTrue(service.contains("RESUME_SOURCE_FRESHNESS_CONFIRMED"));
        assertTrue(service.contains("rebuildForExistingPrompt"));
        assertTrue(service.contains("scheduleReconciliationRetry"));
        assertTrue(service.indexOf("RESUME_SOURCE_FRESHNESS_CHECK")
                < service.indexOf("rebuildForExistingPrompt"));
        assertFalse(service.contains("scheduleStep(1200L)"));
        assertFalse(service.contains("postDelayed(this::ensureEngine, 1800L)"));
    }

}
