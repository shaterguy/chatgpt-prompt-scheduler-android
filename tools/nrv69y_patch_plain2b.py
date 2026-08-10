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
rep(p,
'''    private void resetInitialTargetRetry() {''',
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

    private void resetInitialTargetRetry() {''')
anchor='''    private String activeTargetSide() {'''
helpers='''    private boolean isTransientExpectedTarget(String actualUrl) {
        if (store.bootstrapProvisioning()) return false;
        return TargetParser.isTransientConversationRoute(currentRelayTargetUrl(), actualUrl);
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
                    log("RESUME_STATE_REBUILT", "state=WAITING_USER;side=" + safeCode(decision.selected.sourceSide));
                    NotificationHelper.orchestrationUserAction(this, decision.selected.sourceSide,
                            store.runJobId(), decision.selected.signal.step, decision.selected.signal.round,
                            decision.selected.signal.actionId);
                    stopRelay();
                }
            }
            case TERMINAL -> {
                store.finish(decision.selected.signal, decision.selected.sourceSide);
                log("RESUME_STATE_REBUILT", "state=TERMINAL;type=" + safeCode(decision.selected.signal.type.name()));
                NotificationHelper.orchestrationTerminal(this, decision.selected.signal.type, store.runJobId());
                stopRelay();
            }
            case ROUTE -> {
                String target = decision.targetSide();
                store.setReconciliationTarget(target, OrchestrationStore.sideLabel(target) + " 재개 전달 중복 여부 확인");
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
        if ("TARGET_CONTEXT_MISMATCH".equals(status) || "NETWORK_ERROR".equals(status) || "AUTH_REQUIRED".equals(status)) {
            pauseReconciliationError(status, fixedScriptMessage(status));
            return;
        }
        if ("RETRY".equals(status)) { scheduleReconciliationRetry("target_retry"); return; }
        if ("TARGET_GENERATING".equals(status) || "TARGET_PROMPT_PRESENT_GENERATING".equals(status)) {
            restartReconciliation("target_generating", true);
            scheduleReconciliationRetry("target_generating");
            return;
        }
        if (reconciliationDecision == null || reconciliationDecision.selected == null) {
            restartReconciliation("target_selection_missing"); return;
        }
        if ("TARGET_PROMPT_PRESENT_NO_RESPONSE".equals(status)) {
            store.rebuildForExistingPrompt(reconciliationDecision.selected.signal, reconciliationDecision.selected.sourceSide);
            reconciliationDeliveryInProgress = false;
            log("RESUME_STATE_REBUILT", "state=WAITING_RESPONSE;reason=existing_prompt");
            cleanupWebView(); handler.post(this::ensureEngine); return;
        }
        if ("TARGET_PROMPT_PRESENT_WITH_RESPONSE".equals(status)) {
            restartReconciliation("target_response_present", false);
            cleanupWebView(); handler.post(this::ensureEngine); return;
        }
        if ("TARGET_PROMPT_MULTIPLE".equals(status)) { pauseReconciliationAmbiguous("RESUME_TARGET_PROMPT_MULTIPLE"); return; }
        if (!"TARGET_PROMPT_ABSENT".equals(status)) {
            pauseReconciliationError("RECONCILE_TARGET_SCAN_FAILED", "재개 대상 대화의 중복 여부를 확인하지 못했습니다."); return;
        }
        store.rebuildForReconciliation(reconciliationDecision.selected.signal, reconciliationDecision.selected.sourceSide);
        reconciliationDeliveryInProgress = true;
        log("RESUME_STATE_REBUILT", "state=DELIVERY_PENDING;side=" + safeCode(reconciliationDecision.targetSide()));
        cleanupWebView(); handler.post(this::ensureEngine);
    }

    private void pauseReconciliationAmbiguous''')

write("app/src/test/java/com/shaterguy/chatgptpromptscheduler/UnifiedResumeTest.java",'''package com.shaterguy.chatgptpromptscheduler;\n\nimport static org.junit.Assert.assertEquals;\nimport org.junit.Test;\n\npublic class UnifiedResumeTest {\n @Test public void fullRelayAlwaysReconciles(){ assertEquals(OrchestrationActivity.ResumePath.RECONCILE,OrchestrationActivity.resumePath(true,true)); assertEquals(OrchestrationActivity.ResumePath.RECONCILE,OrchestrationActivity.resumePath(false,true)); }\n @Test public void incompleteBootstrapKeepsLegacyPaths(){ assertEquals(OrchestrationActivity.ResumePath.USER_ACTION_RESOLVED,OrchestrationActivity.resumePath(true,false)); assertEquals(OrchestrationActivity.ResumePath.BOOTSTRAP,OrchestrationActivity.resumePath(false,false)); }\n}\n''')
write("app/src/test/java/com/shaterguy/chatgptpromptscheduler/RelayTargetRecoveryTest.java",'''package com.shaterguy.chatgptpromptscheduler;\n\nimport static org.junit.Assert.*;\nimport org.junit.Test;\n\npublic class RelayTargetRecoveryTest {\n private static final String P="https://chatgpt.com/g/project-1"; private static final String C=P+"/c/conversation-1";\n @Test public void sameConversationSurvivesNormalization(){ assertEquals(TargetParser.ConversationTargetState.MATCH,TargetParser.classifyConversationTarget(C,"https://chatgpt.com/c/conversation-1")); assertTrue(TargetParser.matchesTarget("existing",C,"https://chatgpt.com/c/conversation-1")); }\n @Test public void rootHomeBlankTransient(){ assertEquals(TargetParser.ConversationTargetState.TRANSIENT,TargetParser.classifyConversationTarget(C,P)); assertEquals(TargetParser.ConversationTargetState.TRANSIENT,TargetParser.classifyConversationTarget(C,"https://chatgpt.com/")); assertEquals(TargetParser.ConversationTargetState.TRANSIENT,TargetParser.classifyConversationTarget(C,"about:blank")); }\n @Test public void differentConversationDifferent(){ assertEquals(TargetParser.ConversationTargetState.DIFFERENT,TargetParser.classifyConversationTarget(C,P+"/c/conversation-2")); }\n}\n''')
print("plain2b ok")
