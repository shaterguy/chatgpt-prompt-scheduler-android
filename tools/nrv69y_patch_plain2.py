from pathlib import Path
import re

def read(p): return Path(p).read_text(encoding="utf-8")
def write(p,s): Path(p).write_text(s,encoding="utf-8")
def rep(p, old, new, n=1):
    s=read(p)
    if s.count(old)<n: raise SystemExit(f"missing {p}: {old[:120]!r} count={s.count(old)}")
    write(p,s.replace(old,new,n))
def sub(p, pat, new, n=1):
    s=read(p)
    s2,c=re.subn(pat,new,s,count=n,flags=re.S)
    if c!=n: raise SystemExit(f"regex mismatch {p}: {pat[:120]!r} got={c}")
    write(p,s2)

p="app/src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationService.java"
rep(p,'''ChatGPTPromptScheduler/0.1.21 Orchestration/3.3.2''','''ChatGPTPromptScheduler/0.1.22 Orchestration/3.3.2''')
rep(p,
'''                    if (!matchesExpectedTarget(url)) {
                        if (store.initialStartPending()) {
                            // about:blank/home can be a transient SPA hop before ChatGPT restores
                            // the requested conversation. Never authorize JS on this URL.
                            log("INITIAL_START_TRANSIENT_ROUTE", "phase=page_start");
                        } else {
                            pauseWithError("TARGET_CHANGED", "중계 대상 대화가 바뀌었습니다.");
                        }
                    }''',
'''                    if (!matchesExpectedTarget(url)) {
                        if (store.initialStartPending() || isTransientExpectedTarget(url)) {
                            log("TARGET_TRANSIENT_ROUTE", "phase=page_start");
                            reloadInitialStartTarget("page_start");
                        } else {
                            pauseTargetChanged(url, "중계 대상 대화가 바뀌었습니다.");
                        }
                    }''')
rep(p,
'''                    if (!matchesExpectedTarget(url)) {
                        if (store.initialStartPending()) reloadInitialStartTarget("page_finish");
                        else pauseWithError("TARGET_CHANGED", "중계 대상 대화가 바뀌었습니다.");
                        return;
                    }''',
'''                    if (!matchesExpectedTarget(url)) {
                        if (store.initialStartPending() || isTransientExpectedTarget(url))
                            reloadInitialStartTarget("page_finish");
                        else pauseTargetChanged(url, "중계 대상 대화가 바뀌었습니다.");
                        return;
                    }''')
rep(p,
'''                    if (matchesExpectedTarget(requested)) return false;
                    if (store.initialStartPending()) handler.post(() -> reloadInitialStartTarget("navigation"));
                    return true;''',
'''                    if (matchesExpectedTarget(requested)) return false;
                    if (store.initialStartPending() || isTransientExpectedTarget(requested))
                        handler.post(() -> reloadInitialStartTarget("navigation"));
                    else handler.post(() -> pauseTargetChanged(requested, "다른 대화로의 탐색을 차단했습니다."));
                    return true;''')
rep(p,
'''                    if (request.isForMainFrame()) {
                        log("WEBVIEW_ERROR", "type=http;code=" + response.getStatusCode());
                        pauseWithError("HTTP_ERROR", "중계 대화 서버가 오류 응답을 반환했습니다.");
                    }''',
'''                    if (request.isForMainFrame()) {
                        int code = response.getStatusCode();
                        log("WEBVIEW_ERROR", "type=http;code=" + code);
                        if (code == 429) {
                            log("RATE_LIMIT_RETRY", "code=429");
                            reloadInitialStartTarget("http_429");
                        } else {
                            pauseWithError("HTTP_ERROR", "중계 대화 서버가 오류 응답을 반환했습니다.");
                        }
                    }''')
rep(p,
'''        if (!matchesExpectedTarget(actualUrl)) {
            if (store.initialStartPending()) reloadInitialStartTarget("step_guard");
            else pauseWithError("TARGET_CHANGED", "중계 대상 대화가 바뀌어 자동 전송을 멈췄습니다.");
            return;
        }''',
'''        if (!matchesExpectedTarget(actualUrl)) {
            if (store.initialStartPending() || isTransientExpectedTarget(actualUrl))
                reloadInitialStartTarget("step_guard");
            else pauseTargetChanged(actualUrl, "중계 대상 대화가 바뀌어 자동 전송을 멈췄습니다.");
            return;
        }''')
rep(p,
'''            if (!matchesExpectedTarget(active.getUrl())) {
                if (store.initialStartPending()) reloadInitialStartTarget("evaluation_guard");
                else pauseWithError("TARGET_CHANGED", "스크립트 실행 중 중계 대상 대화가 바뀌었습니다.");
                return;
            }''',
'''            if (!matchesExpectedTarget(active.getUrl())) {
                if (store.initialStartPending() || isTransientExpectedTarget(active.getUrl()))
                    reloadInitialStartTarget("evaluation_guard");
                else pauseTargetChanged(active.getUrl(), "스크립트 실행 중 중계 대상 대화가 바뀌었습니다.");
                return;
            }''',1)
rep(p,
'''            if (!matchesExpectedTarget(active.getUrl())) {
                pauseReconciliationError("TARGET_CHANGED", "재개 재구성 중 대상 대화가 바뀌었습니다.");
                return;
            }''',
'''            if (!matchesExpectedTarget(active.getUrl())) {
                if (isTransientExpectedTarget(active.getUrl()))
                    reloadInitialStartTarget("reconcile_evaluation");
                else pauseReconciliationError("TARGET_CHANGED", "재개 재구성 중 다른 대화 ID가 확인되었습니다.");
                return;
            }''')
sub(p,
r'''    private void reloadInitialStartTarget\(String reason\) \{.*?
    \}

    private void resetInitialTargetRetry''',
'''    private void reloadInitialStartTarget(String reason) {
        if (webView == null || scheduleHasPriority() || initialTargetReloadScheduled) return;
        AdaptivePolling.Decision decision = initialTargetPolling.onRetry(store.epoch());
        initialTargetReloadScheduled = true;
        initialTargetReloadReason = safeCode(reason.toUpperCase());
        log("TARGET_RETRY", "reason=" + initialTargetReloadReason
                + ";retry=" + decision.retryCount + ";tier=" + decision.tier
                + ";delay_ms=" + decision.delayMs);
        handler.postDelayed(initialTargetReloadRunnable, decision.delayMs);
    }

    private void resetInitialTargetRetry''')
sub(p,
r'''    private void performInitialTargetReload\(\) \{.*?
    \}

    private void resetInitialTargetRetry''',
'''    private void performInitialTargetReload() {
        initialTargetReloadScheduled = false;
        if (webView == null) return;
        if (scheduleHasPriority()) {
            yieldForSchedule();
            return;
        }
        if (matchesExpectedTarget(webView.getUrl())) {
            resetInitialTargetRetry();
            scheduleStep(500L);
            return;
        }
        String expected = currentRelayTargetUrl();
        if (expected.isEmpty()) {
            pauseWithError("TARGET_URL_MISSING", "복구할 중계 대화 URL이 없습니다.");
            return;
        }
        store.setStatus(OrchestrationStore.sideLabel(activeTargetSide()) + " 대화 다시 여는 중");
        log("TARGET_RELOAD", "reason=" + initialTargetReloadReason);
        webView.stopLoading();
        webView.loadUrl(expected);
    }

    private void resetInitialTargetRetry''')
anchor='''    private String activeTargetSide() {'''
helpers='''    private boolean isTransientExpectedTarget(String actualUrl) {
        if (store.bootstrapProvisioning()) return false;
        String expected = currentRelayTargetUrl();
        return TargetParser.isTransientConversationRoute(expected, actualUrl);
    }

    private void pauseTargetChanged(String actualUrl, String detail) {
        log("TARGET_MISMATCH", TargetParser.mismatchDetail("existing", currentRelayTargetUrl(), actualUrl));
        pauseWithError("TARGET_CHANGED", detail);
    }

'''
rep(p,anchor,helpers+anchor)
sub(p,
r'''    private void handleReconciliationRoom\(JSONObject result, String side\) \{.*?
    \}

    private void handleReconciliationConfirmationRoom''',
'''    private void handleReconciliationRoom(JSONObject result, String side) {
        String status = result.optString("status", "SCRIPT_RESULT_INVALID");
        log(OrchestrationStore.SIDE_CHAT.equals(side) ? "RESUME_ROOM_SCAN_CHAT" : "RESUME_ROOM_SCAN_WORK",
                "status=" + safeCode(status));
        log("RESUME_ROOM_SCAN_RESULT", "side=" + safeCode(side) + ";status=" + safeCode(status));
        if ("AUTH_REQUIRED".equals(status)) {
            pauseReconciliationError("AUTH_REQUIRED", "재개 재구성 중 로그인 세션을 확인하지 못했습니다.");
            return;
        }
        if ("TARGET_CONTEXT_MISMATCH".equals(status) || "NETWORK_ERROR".equals(status)) {
            pauseReconciliationError(status, fixedScriptMessage(status));
            return;
        }
        if ("RETRY".equals(status)) {
            store.setStatus(OrchestrationStore.sideLabel(side) + " 재개 상태 확인 대기");
            scheduleReconciliationRetry("room_retry");
            return;
        }
        if (!"SCAN".equals(status)) {
            pauseReconciliationError("RECONCILE_SCAN_FAILED", "재개 대화 상태를 읽지 못했습니다.");
            return;
        }
        ResumeReconciliation.RoomScan scan = parseRoomScan(result, side);
        if (scan.authRequired) {
            pauseReconciliationError("AUTH_REQUIRED", "재개 재구성 중 명시적 로그인 화면을 확인했습니다.");
            return;
        }
        if (scan.generating) {
            log("RESUME_WAITING_FOR_IDLE", "side=" + safeCode(side));
            restartReconciliation("room_generating", true);
            scheduleReconciliationRetry("room_generating");
            return;
        }
        log("ROOM_IDLE_CONFIRMED", "side=" + safeCode(side));
        if (OrchestrationStore.SIDE_CHAT.equals(side)) {
            reconciliationChatScan = scan;
            reconciliationWorkScan = null;
            store.setReconciliationSide(OrchestrationStore.SIDE_WORK,
                    "재개 상태 재구성 중 · Work 대화 확인");
            resetReconciliationPolling("room_switch");
            cleanupWebView();
            handler.post(this::ensureEngine);
            return;
        }
        reconciliationWorkScan = scan;
        handleReconciliationDecision(ResumeReconciliation.select(reconciliationChatScan, reconciliationWorkScan));
    }

    private void handleReconciliationConfirmationRoom''')
sub(p,
r'''    private void handleReconciliationDecision\(ResumeReconciliation.Decision decision\) \{.*?
    \}

    private void handleReconciliationTarget''',
'''    private void handleReconciliationDecision(ResumeReconciliation.Decision decision) {
        reconciliationDecision = decision;
        log("RESUME_RECONCILE_DECISION", "type=" + safeCode(decision.type.name())
                + ";reason=" + safeCode(decision.reason));
        if (decision.selected != null) {
            log("SIGNAL_SELECTED", "side=" + safeCode(decision.selected.sourceSide)
                    + ";type=" + safeCode(decision.selected.signal.type.name())
                    + ";step=" + safeCode(decision.selected.positionStep)
                    + ";round=" + safeCode(decision.selected.positionRound));
        }
        switch (decision.type) {
            case WAIT_FOR_IDLE -> {
                log("RESUME_WAITING_FOR_IDLE", "side=both;reason=" + safeCode(decision.reason));
                restartReconciliation(decision.reason, true);
                scheduleReconciliationRetry(decision.reason);
            }
            case AMBIGUOUS -> {
                store.reconciliationAmbiguous("RESUME_RECONCILE_AMBIGUOUS", decision.reason);
                log("RESUME_RECONCILE_AMBIGUOUS", "reason=" + safeCode(decision.reason));
                NotificationHelper.orchestrationError(this, activeTargetSide(), store.runJobId(),
                        store.currentStep(), store.currentRound(), "재개 상태를 자동으로 재구성하지 못했습니다.");
                stopRelay();
            }
            case USER_ACTION -> {
                if (store.resumeUserActionRequested()) {
                    store.rebuildForUserResolved(decision.selected.signal, decision.selected.sourceSide);
                    log("RESUME_STATE_REBUILT", "state=USER_ACTION_REVALIDATION;side=CHAT");
                    cleanupWebView();
                    handler.post(this::ensureEngine);
                } else {
                    store.waitForUser(decision.selected.signal, decision.selected.sourceSide);
                    log("RESUME_STATE_REBUILT", "state=WAITING_USER;side="
                            + safeCode(decision.selected.sourceSide));
                    NotificationHelper.orchestrationUserAction(this, decision.selected.sourceSide,
                            store.runJobId(), decision.selected.signal.step, decision.selected.signal.round,
                            decision.selected.signal.actionId);
                    stopRelay();
                }
            }
            case TERMINAL -> {
                store.finish(decision.selected.signal, decision.selected.sourceSide);
                log("RESUME_STATE_REBUILT", "state=TERMINAL;type="
                        + safeCode(decision.selected.signal.type.name()));
                NotificationHelper.orchestrationTerminal(this, decision.selected.signal.type, store.runJobId());
                stopRelay();
            }
            case ROUTE -> {
                String target = decision.targetSide();
                store.setReconciliationTarget(target, OrchestrationStore.sideLabel(target)
                        + " 재개 전달 중복 여부 확인");
                resetReconciliationPolling("route_selected");
                cleanupWebView();
                handler.post(this::ensureEngine);
            }
        }
    }

    private void handleReconciliationTarget''')
sub(p,
r'''    private void handleReconciliationTarget\(JSONObject result\) \{.*?
    \}

    private void pauseReconciliationAmbiguous''',
'''    private void handleReconciliationTarget(JSONObject result) {
        String status = result.optString("status", "SCRIPT_RESULT_INVALID");
        log("RESUME_TARGET_SCAN_RESULT", "status=" + safeCode(status));
        if ("TARGET_CONTEXT_MISMATCH".equals(status) || "NETWORK_ERROR".equals(status)
                || "AUTH_REQUIRED".equals(status)) {
            pauseReconciliationError(status, fixedScriptMessage(status));
            return;
        }
        if ("RETRY".equals(status)) {
            scheduleReconciliationRetry("target_retry");
            return;
        }
        if ("TARGET_GENERATING".equals(status) || "TARGET_PROMPT_PRESENT_GENERATING".equals(status)) {
            log("RESUME_WAITING_FOR_IDLE", "side=" + safeCode(store.reconciliationSide()));
            restartReconciliation("target_generating", true);
            scheduleReconciliationRetry("target_generating");
            return;
        }
        if (reconciliationDecision == null || reconciliationDecision.selected == null) {
            restartReconciliation("target_selection_missing");
            return;
        }
        if ("TARGET_PROMPT_PRESENT_NO_RESPONSE".equals(status)) {
            log("TARGET_PROMPT_ALREADY_PRESENT", "side=" + safeCode(store.reconciliationSide()));
            store.rebuildForExistingPrompt(reconciliationDecision.selected.signal,
                    reconciliationDecision.selected.sourceSide);
            reconciliationDeliveryInProgress = false;
            log("RESUME_STATE_REBUILT", "state=WAITING_RESPONSE;reason=existing_prompt");
            cleanupWebView();
            handler.post(this::ensureEngine);
            return;
        }
        if ("TARGET_PROMPT_PRESENT_WITH_RESPONSE".equals(status)) {
            log("TARGET_PROMPT_ALREADY_PRESENT", "side=" + safeCode(store.reconciliationSide()));
            restartReconciliation("target_response_present", false);
            cleanupWebView();
            handler.post(this::ensureEngine);
            return;
        }
        if ("TARGET_PROMPT_MULTIPLE".equals(status)) {
            pauseReconciliationAmbiguous("RESUME_TARGET_PROMPT_MULTIPLE");
            return;
        }
        if (!"TARGET_PROMPT_ABSENT".equals(status)) {
            pauseReconciliationError("RECONCILE_TARGET_SCAN_FAILED",
                    "재개 대상 대화의 중복 여부를 확인하지 못했습니다.");
            return;
        }
        store.rebuildForReconciliation(reconciliationDecision.selected.signal,
                reconciliationDecision.selected.sourceSide);
        reconciliationDeliveryInProgress = true;
        log("RESUME_STATE_REBUILT", "state=DELIVERY_PENDING;side="
                + safeCode(reconciliationDecision.targetSide()));
        cleanupWebView();
        handler.post(this::ensureEngine);
    }

    private void pauseReconciliationAmbiguous''')

write("app/src/test/java/com/shaterguy/chatgptpromptscheduler/UnifiedResumeTest.java", r'''package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class UnifiedResumeTest {
    @Test public void fullRelayAlwaysReconcilesIncludingUserActionResume() {
        assertEquals(OrchestrationActivity.ResumePath.RECONCILE,
                OrchestrationActivity.resumePath(true, true));
        assertEquals(OrchestrationActivity.ResumePath.RECONCILE,
                OrchestrationActivity.resumePath(false, true));
    }
    @Test public void incompleteBootstrapKeepsLegacyPaths() {
        assertEquals(OrchestrationActivity.ResumePath.USER_ACTION_RESOLVED,
                OrchestrationActivity.resumePath(true, false));
        assertEquals(OrchestrationActivity.ResumePath.BOOTSTRAP,
                OrchestrationActivity.resumePath(false, false));
    }
}
''')

write("app/src/test/java/com/shaterguy/chatgptpromptscheduler/ResumeReconciliationTest.java", r'''package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.*;
import org.junit.Test;

public class ResumeReconciliationTest {
    private static final String JOB="JOB-7";
    private ResumeReconciliation.Candidate c(String side, String raw) {
        return ResumeReconciliation.acceptCandidate(JOB, side, raw, "", "", -1, 1);
    }
    @Test public void oneSidedValidSignalRoutes() {
        ResumeReconciliation.Candidate w=c(OrchestrationStore.SIDE_WORK,"[AR_SEND_CHAT JOB-7 S001 R001]");
        ResumeReconciliation.Decision d=ResumeReconciliation.select(
                ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_CHAT),
                ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK,w));
        assertEquals(ResumeReconciliation.DecisionType.ROUTE,d.type); assertSame(w,d.selected);
    }
    @Test public void numericStepWinsBeforeRound() {
        ResumeReconciliation.Candidate chat=c(OrchestrationStore.SIDE_CHAT,"[AR_SEND_WORK JOB-7 S010 R001]");
        ResumeReconciliation.Candidate work=c(OrchestrationStore.SIDE_WORK,"[AR_SEND_CHAT JOB-7 S009 R999]");
        assertSame(chat,ResumeReconciliation.select(ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_CHAT,chat),ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK,work)).selected);
    }
    @Test public void numericRoundWinsWithinStep() {
        ResumeReconciliation.Candidate chat=c(OrchestrationStore.SIDE_CHAT,"[AR_SEND_WORK JOB-7 S003 R010]");
        ResumeReconciliation.Candidate work=c(OrchestrationStore.SIDE_WORK,"[AR_SEND_CHAT JOB-7 S003 R009]");
        assertSame(chat,ResumeReconciliation.select(ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_CHAT,chat),ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK,work)).selected);
    }
    @Test public void samePositionUserActionChatWinsOtherwiseWorkWins() {
        ResumeReconciliation.Candidate action=c(OrchestrationStore.SIDE_CHAT,"[AR_USER_ACTION_REQUIRED JOB-7 S003 R004 ACTION-1]");
        ResumeReconciliation.Candidate work=c(OrchestrationStore.SIDE_WORK,"[AR_SEND_CHAT JOB-7 S003 R004]");
        ResumeReconciliation.Decision d=ResumeReconciliation.select(ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_CHAT,action),ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK,work));
        assertEquals(ResumeReconciliation.DecisionType.USER_ACTION,d.type); assertSame(action,d.selected);
        ResumeReconciliation.Candidate chat=c(OrchestrationStore.SIDE_CHAT,"[AR_SEND_WORK JOB-7 S005 R001]");
        work=c(OrchestrationStore.SIDE_WORK,"[AR_SEND_CHAT JOB-7 S005 R001]");
        assertSame(work,ResumeReconciliation.select(ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_CHAT,chat),ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK,work)).selected);
    }
    @Test public void generatingAndNoSignalWaitInsteadOfFalseAmbiguous() {
        assertEquals(ResumeReconciliation.DecisionType.WAIT_FOR_IDLE,ResumeReconciliation.select(new ResumeReconciliation.RoomScan(OrchestrationStore.SIDE_CHAT,true,true,false,java.util.List.of()),ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK)).type);
        assertEquals(ResumeReconciliation.DecisionType.WAIT_FOR_IDLE,ResumeReconciliation.select(ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_CHAT),ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK)).type);
    }
    @Test public void validAssistantSignalDoesNotRequireRecognizedPredecessor() {
        assertNotNull(ResumeReconciliation.acceptCandidate(JOB,OrchestrationStore.SIDE_WORK,"[AR_SEND_CHAT JOB-7 S001 R001]","unrelated text","",-1,4));
    }
    @Test public void foreignMalformedAndWrongDirectionAreExcluded() {
        assertNull(c(OrchestrationStore.SIDE_CHAT,"[AR_SEND_WORK OTHER S001 R001]"));
        assertNull(c(OrchestrationStore.SIDE_CHAT,"[AR_SEND_WORK JOB-7 S1 R001]"));
        assertNull(c(OrchestrationStore.SIDE_WORK,"[AR_SEND_WORK JOB-7 S001 R001]"));
        assertNull(c(OrchestrationStore.SIDE_WORK,"[AR_DONE JOB-7]"));
    }
}
''')

write("app/src/test/java/com/shaterguy/chatgptpromptscheduler/RelayTargetRecoveryTest.java", r'''package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.*;
import org.junit.Test;

public class RelayTargetRecoveryTest {
    private static final String PROJECT="https://chatgpt.com/g/project-1";
    private static final String CHAT=PROJECT+"/c/conversation-1";
    @Test public void sameConversationSurvivesProjectPathNormalization() {
        assertEquals(TargetParser.ConversationTargetState.MATCH,TargetParser.classifyConversationTarget(CHAT,"https://chatgpt.com/c/conversation-1"));
        assertTrue(TargetParser.matchesTarget("existing",CHAT,"https://chatgpt.com/c/conversation-1"));
    }
    @Test public void projectRootHomeAndBlankAreTransient() {
        assertEquals(TargetParser.ConversationTargetState.TRANSIENT,TargetParser.classifyConversationTarget(CHAT,PROJECT));
        assertEquals(TargetParser.ConversationTargetState.TRANSIENT,TargetParser.classifyConversationTarget(CHAT,"https://chatgpt.com/"));
        assertEquals(TargetParser.ConversationTargetState.TRANSIENT,TargetParser.classifyConversationTarget(CHAT,"about:blank"));
    }
    @Test public void concreteDifferentConversationIsDifferent() {
        assertEquals(TargetParser.ConversationTargetState.DIFFERENT,TargetParser.classifyConversationTarget(CHAT,PROJECT+"/c/conversation-2"));
    }
}
''')

print("plain2 ok")
