from pathlib import Path


def replace_once(path, old, new, label):
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    p.write_text(text.replace(old, new, 1))


script = "app/src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationScript.java"
replace_once(
    script,
    '                "const generating=busy||stopButtons.length>0;const candidates=[];" +',
    '''                "const generating=busy||stopButtons.length>0;" +
                "const assistantTurns=turns.filter(e=>roleOf(e)==='assistant').length;" +
                "const userTurns=turns.filter(e=>roleOf(e)==='user');" +
                "const jobPromptTurns=userTurns.filter(e=>{const t=cleanMessage(e);return t.includes('[AUTOMATION_')&&t.includes(' '+job);}).length;" +
                "if(jobPromptTurns===0||assistantTurns===0)return out('RETRY','대화 이력 로딩 대기',{main_present:true,history_ready:false,generating,stop_available:stopButtons.length>0,assistant_turns:assistantTurns,user_turns:userTurns.length,job_prompt_turns:jobPromptTurns,candidate_count:0,candidates:[]});" +
                "const candidates=[];" +''',
    "reconcile history-ready precondition",
)
replace_once(
    script,
    '''                "return out('SCAN','대화 상태 수집 완료',{main_present:true,generating,stop_available:stopButtons.length>0,assistant_turns:turns.filter(e=>roleOf(e)==='assistant').length,candidate_count:candidates.length,candidates});" +''',
    '''                "return out('SCAN','대화 상태 수집 완료',{main_present:true,history_ready:true,generating,stop_available:stopButtons.length>0,assistant_turns:assistantTurns,user_turns:userTurns.length,job_prompt_turns:jobPromptTurns,candidate_count:candidates.length,candidates});" +''',
    "reconcile history-ready scan result",
)

store = "app/src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationStore.java"
replace_once(
    store,
    "    private static final int SCHEMA_VERSION = 7;\n",
    '''    private static final int SCHEMA_VERSION = 7;
    private static final String SIGNAL_RETRY_PROMPT =
            "다음 작업을 위한 신호가 누락되었습니다. 현재 상태는 그대로 유지하고, 방금 완료한 작업에 대응하는 올바른 Protocol 앱 제어 신호 한 줄만 다시 출력해 주세요.";
''',
    "signal retry constant",
)
replace_once(
    store,
    '''    public static String startPrompt(String jobId) {
        return "[AUTOMATION_START " + clean(jobId) + "]";
    }
    public String lastDeliveredPrompt() { return preferences.getString("lastDeliveredPrompt", ""); }
''',
    '''    public static String startPrompt(String jobId) {
        return "[AUTOMATION_START " + clean(jobId) + "]";
    }

    public static String signalRetryPrompt() { return SIGNAL_RETRY_PROMPT; }

    public boolean lastDeliveryWasSignalRetry() {
        return lastDeliveredPrompt().contains(SIGNAL_RETRY_PROMPT);
    }

    public void prepareSignalRetry(String sourceSide) {
        if (!SIDE_CHAT.equals(sourceSide) && !SIDE_WORK.equals(sourceSide))
            throw new IllegalArgumentException("신호 재요청 대상 대화방이 올바르지 않습니다.");
        long now = System.currentTimeMillis();
        commit(preferences.edit().putString("deliveryTarget", sourceSide)
                .putString("pendingPrompt", SIGNAL_RETRY_PROMPT).putString("stampedPrompt", "")
                .putString("deliveryState", DELIVERY_PENDING)
                .putString("expectedSignal", expectedFor(sourceSide, currentStep(), currentRound()))
                .putLong("deliveryPreparedAt", 0L).putLong("deliveryAttemptAt", 0L)
                .putString("status", sideLabel(sourceSide) + " 제어 신호 재출력 요청 전송 대기")
                .putString("lastErrorCode", "").putString("error", "").putLong("errorAt", 0L)
                .putString("candidateFingerprint", "").putInt("candidateStability", 0)
                .putLong("phaseStartedAt", now).putLong("pollCountLong", 0L).putInt("pollCount", 0)
                .putLong("epoch", epoch() + 1L));
        resetResponseTiming("SIGNAL_RETRY_REQUESTED");
    }

    public String lastDeliveredPrompt() { return preferences.getString("lastDeliveredPrompt", ""); }
''',
    "durable same-room signal retry",
)

service = "app/src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationService.java"
replace_once(
    service,
    '''                        log("WEBVIEW_ERROR", "type=network");
                        pauseWithError("NETWORK_ERROR", "중계 대화를 불러오는 중 네트워크 오류가 발생했습니다.");''',
    '''                        log("WEBVIEW_ERROR", "type=network");
                        recoverTransientNetwork("WEBVIEW_MAIN_FRAME");''',
    "main-frame network recovery",
)
replace_once(
    service,
    '''            if (RecoveryDecisionPolicy.isUiWaitStatus(status) || "RETRY".equals(status)) {''',
    '''            if ("NETWORK_ERROR".equals(status)) {
                recoverTransientNetwork("BOOTSTRAP_SCRIPT");
                return;
            }
            if (RecoveryDecisionPolicy.isUiWaitStatus(status) || "RETRY".equals(status)) {''',
    "bootstrap network recovery",
)
replace_once(
    service,
    '''        if ("TARGET_CONTEXT_MISMATCH".equals(status) || "NETWORK_ERROR".equals(status)) {
            pauseReconciliationError(status, fixedScriptMessage(status));
            return;
        }
        if ("RETRY".equals(status)) {
            store.setStatus(OrchestrationStore.sideLabel(side) + " 재개 상태 확인 대기");
            scheduleReconciliationRetry("room_retry");
            return;
        }''',
    '''        if ("NETWORK_ERROR".equals(status)) {
            recoverTransientNetwork("RECONCILIATION_SCAN");
            return;
        }
        if ("TARGET_CONTEXT_MISMATCH".equals(status)) {
            pauseReconciliationError(status, fixedScriptMessage(status));
            return;
        }
        if ("RETRY".equals(status)) {
            reconciliationRescanAttempts = 0;
            log("RESUME_ROOM_NOT_READY", "side=" + safeCode(side)
                    + ";assistant_turns=" + Math.max(0, result.optInt("assistant_turns", 0))
                    + ";job_prompt_turns=" + Math.max(0, result.optInt("job_prompt_turns", 0)));
            store.setStatus(OrchestrationStore.sideLabel(side) + " 재개 상태 확인 대기");
            scheduleReconciliationRetry("room_retry");
            return;
        }''',
    "reconciliation room transient states",
)
replace_once(
    service,
    '''        log("RESUME_ROOM_SCAN_META", "side=" + safeCode(side)
                + ";assistant_turns=" + Math.max(0, result.optInt("assistant_turns", 0))
                + ";script_candidates=" + Math.max(0, result.optInt("candidate_count", 0))
                + ";accepted_candidates=" + scan.candidates.size());''',
    '''        log("RESUME_ROOM_SCAN_META", "side=" + safeCode(side)
                + ";history_ready=" + (result.optBoolean("history_ready", false) ? "1" : "0")
                + ";assistant_turns=" + Math.max(0, result.optInt("assistant_turns", 0))
                + ";job_prompt_turns=" + Math.max(0, result.optInt("job_prompt_turns", 0))
                + ";script_candidates=" + Math.max(0, result.optInt("candidate_count", 0))
                + ";accepted_candidates=" + scan.candidates.size());''',
    "reconciliation scan diagnostics",
)
replace_once(
    service,
    '''            case WAIT_FOR_IDLE -> {
                log("RESUME_WAITING_FOR_IDLE", "side=both;reason=" + safeCode(decision.reason)
                        + ";retry=" + store.pollCountLong());
                if ("NO_VALID_SIGNAL".equals(decision.reason)
                        && store.pollCountLong() >= MAX_NO_SIGNAL_RECONCILIATION_RETRIES) {
                    pauseReconciliationError("RESUME_NO_VALID_SIGNAL",
                            "두 대화방을 반복 확인했지만 최신 Protocol 제어 신호를 찾지 못했습니다.");
                    return;
                }
                restartReconciliation(decision.reason, true);
                scheduleReconciliationRetry(decision.reason);
            }''',
    '''            case WAIT_FOR_IDLE -> {
                log("RESUME_WAITING_FOR_IDLE", "side=both;reason=" + safeCode(decision.reason)
                        + ";retry=" + reconciliationRescanAttempts);
                if ("NO_VALID_SIGNAL".equals(decision.reason)) {
                    reconciliationRescanAttempts++;
                    log("RESUME_NO_SIGNAL_RETRY", "attempt=" + reconciliationRescanAttempts);
                    if (reconciliationRescanAttempts >= MAX_NO_SIGNAL_RECONCILIATION_RETRIES) {
                        pauseReconciliationError("RESUME_NO_VALID_SIGNAL",
                                "대화 이력 로딩이 확인된 뒤에도 두 대화방에서 최신 Protocol 제어 신호를 찾지 못했습니다.");
                        return;
                    }
                } else {
                    reconciliationRescanAttempts = 0;
                }
                restartReconciliation(decision.reason, true);
                scheduleReconciliationRetry(decision.reason);
            }''',
    "dedicated no-signal retry budget",
)
replace_once(
    service,
    '''        if ("TARGET_CONTEXT_MISMATCH".equals(status) || "NETWORK_ERROR".equals(status) || "AUTH_REQUIRED".equals(status)) {
            pauseReconciliationError(status, fixedScriptMessage(status));
            return;
        }''',
    '''        if ("NETWORK_ERROR".equals(status)) {
            recoverTransientNetwork("RECONCILIATION_TARGET");
            return;
        }
        if ("TARGET_CONTEXT_MISMATCH".equals(status) || "AUTH_REQUIRED".equals(status)) {
            pauseReconciliationError(status, fixedScriptMessage(status));
            return;
        }''',
    "reconciliation target network recovery",
)
replace_once(
    service,
    '''            case "AUTH_REQUIRED", "DRAFT_PRESENT", "TARGET_CONTEXT_MISMATCH", "NETWORK_ERROR", "DOM_STRUCTURE_ERROR" -> {
                if ("AUTH_REQUIRED".equals(status)) log("AUTH_REQUIRED", "reason=structural_gate");
                pauseWithError(status, fixedScriptMessage(status));
            }''',
    '''            case "NETWORK_ERROR" -> recoverTransientNetwork("PREPARE_SCRIPT");
            case "AUTH_REQUIRED", "DRAFT_PRESENT", "TARGET_CONTEXT_MISMATCH", "DOM_STRUCTURE_ERROR" -> {
                if ("AUTH_REQUIRED".equals(status)) log("AUTH_REQUIRED", "reason=structural_gate");
                pauseWithError(status, fixedScriptMessage(status));
            }''',
    "prepare network recovery",
)
replace_once(
    service,
    '''            case "TARGET_CONTEXT_MISMATCH", "NETWORK_ERROR" ->
                    pauseWithError(status, fixedScriptMessage(status));''',
    '''            case "NETWORK_ERROR" -> recoverTransientNetwork("STOP_GENERATION_SCRIPT");
            case "TARGET_CONTEXT_MISMATCH" -> pauseWithError(status, fixedScriptMessage(status));''',
    "stop-generation network recovery",
)
replace_once(
    service,
    '''            if ("AUTH_REQUIRED".equals(status) || "NETWORK_ERROR".equals(status)
                    || "DOM_STRUCTURE_ERROR".equals(status) || "TARGET_CONTEXT_MISMATCH".equals(status)) {
                pauseWithError(status, fixedScriptMessage(status));
                return;
            }''',
    '''            if ("NETWORK_ERROR".equals(status)) {
                recoverTransientNetwork("OBSERVE_SCRIPT");
                return;
            }
            if ("AUTH_REQUIRED".equals(status)
                    || "DOM_STRUCTURE_ERROR".equals(status) || "TARGET_CONTEXT_MISMATCH".equals(status)) {
                pauseWithError(status, fixedScriptMessage(status));
                return;
            }''',
    "observation stop-confirmation network recovery",
)
replace_once(
    service,
    '''        if ("AUTH_REQUIRED".equals(status) || "NETWORK_ERROR".equals(status)
                || "DOM_STRUCTURE_ERROR".equals(status) || "TARGET_CONTEXT_MISMATCH".equals(status)) {
            pauseWithError(status, fixedScriptMessage(status));
            return;
        }''',
    '''        if ("NETWORK_ERROR".equals(status)) {
            recoverTransientNetwork("OBSERVE_SCRIPT");
            return;
        }
        if ("AUTH_REQUIRED".equals(status)
                || "DOM_STRUCTURE_ERROR".equals(status) || "TARGET_CONTEXT_MISMATCH".equals(status)) {
            pauseWithError(status, fixedScriptMessage(status));
            return;
        }''',
    "observation network recovery",
)
replace_once(
    service,
    '''        if (!parsed.isValid()) {
            log("SIGNAL_REJECTED", "reason=" + safeCode(parsed.errorCode.name()));
            pauseWithProtocolError(parsed.errorCode, sourceSide);
            return;
        }''',
    '''        if (!parsed.isValid()) {
            log("SIGNAL_REJECTED", "reason=" + safeCode(parsed.errorCode.name()));
            if (store.lastDeliveryWasSignalRetry()) {
                log("SIGNAL_RETRY_EXHAUSTED", "side=" + safeCode(sourceSide)
                        + ";reason=" + safeCode(parsed.errorCode.name()));
                pauseWithError("SIGNAL_RETRY_EXHAUSTED",
                        "제어 신호 재출력을 한 번 요청했지만 다음 응답에도 올바른 Protocol 앱 제어 신호가 없었습니다.");
                return;
            }
            if (parsed.errorCode == OrchestrationSignal.ErrorCode.NO_SIGNAL) {
                store.prepareSignalRetry(sourceSide);
                log("SIGNAL_RETRY_REQUESTED", "side=" + safeCode(sourceSide));
                resetResponsePolling("SIGNAL_RETRY_REQUESTED");
                cleanupWebView();
                handler.post(this::ensureEngine);
                return;
            }
            pauseWithProtocolError(parsed.errorCode, sourceSide);
            return;
        }''',
    "one-time same-room signal retry",
)
replace_once(
    service,
    '''    private void restartReconciliation(String reason, boolean preservePolling) {
        reconciliationChatScan = null;''',
    '''    private void restartReconciliation(String reason, boolean preservePolling) {
        if (!"NO_VALID_SIGNAL".equals(reason)) reconciliationRescanAttempts = 0;
        reconciliationChatScan = null;''',
    "reset no-signal budget on other causes",
)
replace_once(
    service,
    '''    private void pauseWithError(String code, String detail) {''',
    '''    private void recoverTransientNetwork(String source) {
        if (!canRun()) return;
        if (scheduleHasPriority()) {
            yieldForSchedule();
            return;
        }
        if (store.reconciling()) reconciliationRescanAttempts = 0;
        log("TRANSIENT_NETWORK_RECOVERY", "source=" + safeCode(source)
                + ";side=" + safeCode(activeTargetSide())
                + ";state=" + safeCode(store.deliveryState())
                + ";reconciling=" + (store.reconciling() ? "1" : "0"));
        store.setStatus(OrchestrationStore.sideLabel(activeTargetSide()) + " · 네트워크 복구 후 대화 재확인");
        reloadInitialStartTarget("network_" + source.toLowerCase(), true);
    }

    private void pauseWithError(String code, String detail) {''',
    "transient network recovery helper",
)

build = "app/build.gradle"
replace_once(
    build,
    "        versionCode 26\n        versionName '0.1.22-rc4'",
    "        versionCode 27\n        versionName '0.1.22-rc5'",
    "rc5 version",
)

rc = ".github/workflows/rc-apk.yml"
for old, new, label in [
    ("    if: github.head_ref == 'fix/nrv69y-resume-target-v0.1.22'", "    if: github.head_ref == 'fix/skbhds-rc4-recovery-followup'", "rc branch"),
    ("      APK_NAME: chatgpt-prompt-scheduler-android-v0.1.22-rc4.apk", "      APK_NAME: chatgpt-prompt-scheduler-android-v0.1.22-rc5.apk", "rc apk name"),
    ("      VERSION_CODE: '26'", "      VERSION_CODE: '27'", "rc version code"),
    ("      VERSION_NAME: '0.1.22-rc4'", "      VERSION_NAME: '0.1.22-rc5'", "rc version name"),
    ("      PREVIOUS_RC_VERSION_CODE: '25'", "      PREVIOUS_RC_VERSION_CODE: '26'", "rc previous code"),
    ("          name: chatgpt-prompt-scheduler-android-v0.1.22-rc4-update", "          name: chatgpt-prompt-scheduler-android-v0.1.22-rc5-update", "rc artifact name"),
]:
    replace_once(rc, old, new, label)

test = Path("app/src/test/java/com/shaterguy/chatgptpromptscheduler/Rc5RelayRecoveryRegressionTest.java")
if test.exists():
    raise SystemExit("RC5 regression test already exists unexpectedly")
test.write_text('''package com.shaterguy.chatgptpromptscheduler;

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
        return Files.readString(path, StandardCharsets.UTF_8);
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
        assertTrue(service.contains("recoverTransientNetwork(\\\"WEBVIEW_MAIN_FRAME\\\")"));
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
''')
