from pathlib import Path

repo = Path('.')

def replace_once(path, old, new):
    p = repo / path
    text = p.read_text(encoding='utf-8')
    if new in text:
        return False
    if old not in text:
        raise SystemExit(f'missing expected token in {path}: {old[:120]!r}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')
    return True

# 1) Resume must see protocol signals even when ChatGPT renders the signal as code.
# Keep blockquotes excluded so quoted historical examples do not become live candidates.
replace_once(
    'app/src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationScript.java',
    "const cleanMessage=e=>{const copy=e.cloneNode(true);copy.querySelectorAll('pre,code,blockquote').forEach(n=>n.remove());return norm(copy.innerText||copy.textContent||'');};",
    "const cleanMessage=e=>{const copy=e.cloneNode(true);copy.querySelectorAll('blockquote').forEach(n=>n.remove());return norm(copy.innerText||copy.textContent||'');};"
)
replace_once(
    'app/src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationScript.java',
    "return out('SCAN','대화 상태 수집 완료',{main_present:true,generating,stop_available:stopButtons.length>0,candidates});",
    "return out('SCAN','대화 상태 수집 완료',{main_present:true,generating,stop_available:stopButtons.length>0,assistant_turns:turns.filter(e=>roleOf(e)==='assistant').length,candidate_count:candidates.length,candidates});"
)

# 2) A Chat -> Work room switch is part of one reconciliation attempt, not progress.
# Resetting adaptive polling here kept every NO_VALID_SIGNAL cycle at retry=1 forever.
replace_once(
    'app/src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationService.java',
    '''            resetReconciliationPolling("room_switch");
            cleanupWebView();''',
    '''            log("RESUME_ROOM_SWITCH", "from=CHAT;to=WORK");
            cleanupWebView();'''
)

# Add a bounded fail-safe only for the specific "both idle, no valid signal" state.
replace_once(
    'app/src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationService.java',
    '''    private static final long STOP_CONFIRMATION_GRACE_MS = 15_000L;
    private final Handler handler''',
    '''    private static final long STOP_CONFIRMATION_GRACE_MS = 15_000L;
    private static final long MAX_NO_SIGNAL_RECONCILIATION_RETRIES = 5L;
    private final Handler handler'''
)
replace_once(
    'app/src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationService.java',
    '''            case WAIT_FOR_IDLE -> {
                log("RESUME_WAITING_FOR_IDLE", "side=both;reason=" + safeCode(decision.reason));
                restartReconciliation(decision.reason, true);
                scheduleReconciliationRetry(decision.reason);
            }''',
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
            }'''
)

# Log only counts, never assistant body text or raw control text.
replace_once(
    'app/src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationService.java',
    '''        ResumeReconciliation.RoomScan scan = parseRoomScan(result, side);
        if (scan.generating) {''',
    '''        ResumeReconciliation.RoomScan scan = parseRoomScan(result, side);
        log("RESUME_ROOM_SCAN_META", "side=" + safeCode(side)
                + ";assistant_turns=" + Math.max(0, result.optInt("assistant_turns", 0))
                + ";script_candidates=" + Math.max(0, result.optInt("candidate_count", 0))
                + ";accepted_candidates=" + scan.candidates.size());
        if (scan.generating) {'''
)

# Every manual Resume starts a fresh bounded reconciliation budget.
replace_once(
    'app/src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationStore.java',
    '''                .putString("status", "재개 상태 재구성 중 · 두 대화방 확인")
                .putString("lastErrorCode", "").putString("error", "").putLong("errorAt", 0L)
                .putLong("phaseStartedAt", System.currentTimeMillis())''',
    '''                .putString("status", "재개 상태 재구성 중 · 두 대화방 확인")
                .putString("lastErrorCode", "").putString("error", "").putLong("errorAt", 0L)
                .putLong("pollCountLong", 0L).putInt("pollCount", 0)
                .putLong("phaseStartedAt", System.currentTimeMillis())'''
)

# RC4 must update an installed RC3.
build = repo / 'app/build.gradle'
build_text = build.read_text(encoding='utf-8')
if 'versionCode 25' in build_text:
    build_text = build_text.replace('versionCode 25', 'versionCode 26', 1)
elif 'versionCode 26' not in build_text:
    raise SystemExit('unexpected versionCode while preparing RC4')
if "versionName '0.1.22-rc3'" in build_text:
    build_text = build_text.replace("versionName '0.1.22-rc3'", "versionName '0.1.22-rc4'", 1)
elif "versionName '0.1.22-rc4'" not in build_text:
    raise SystemExit('unexpected versionName while preparing RC4')
build.write_text(build_text, encoding='utf-8')

# Regression tests: preserve code-rendered signal text; do not reset polling on room switch;
# bounded no-signal failure is specific to resume reconciliation rather than normal response polling.
test = repo / 'app/src/test/java/com/shaterguy/chatgptpromptscheduler/OrchestrationSignalTest.java'
t = test.read_text(encoding='utf-8')
old = '''        assertTrue(scan.contains("querySelectorAll('pre,code,blockquote')"));
        assertTrue(scan.contains("generating"));'''
new = '''        assertTrue(scan.contains("querySelectorAll('blockquote')"));
        assertFalse(scan.contains("querySelectorAll('pre,code,blockquote')"));
        assertTrue(scan.contains("candidate_count:candidates.length"));
        assertTrue(scan.contains("generating"));'''
if old in t:
    t = t.replace(old, new, 1)
elif new not in t:
    raise SystemExit('resume scan test token not found')
needle = '''        assertTrue(service.contains("CONTINUE_SAME_DELIVERY"));
    }'''
addition = '''        assertTrue(service.contains("CONTINUE_SAME_DELIVERY"));
        assertTrue(service.contains("MAX_NO_SIGNAL_RECONCILIATION_RETRIES"));
        assertTrue(service.contains("RESUME_NO_VALID_SIGNAL"));
        assertTrue(service.contains("RESUME_ROOM_SCAN_META"));
        assertFalse(service.contains("resetReconciliationPolling(\\\"room_switch\\\")"));
    }'''
if needle in t:
    t = t.replace(needle, addition, 1)
elif addition not in t:
    raise SystemExit('service retry test token not found')
test.write_text(t, encoding='utf-8')

# Pure routing regression for the user's recovery shape: stale Chat S001/R001 + corrected Work S001/R002.
resume_test = repo / 'app/src/test/java/com/shaterguy/chatgptpromptscheduler/ResumeReconciliationTest.java'
r = resume_test.read_text(encoding='utf-8')
anchor = '''    @Test public void sameStepRoundChatUserActionWinsOtherwiseWorkWins() {'''
case = '''    @Test public void correctedWorkSignalAfterPauseWinsOverOlderChatSignal() {
        ResumeReconciliation.Candidate chat = candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_SEND_WORK JOB-7 S001 R001]");
        ResumeReconciliation.Candidate correctedWork = candidate(OrchestrationStore.SIDE_WORK,
                "[AR_SEND_CHAT JOB-7 S001 R002]");
        ResumeReconciliation.Decision decision = decide(chat, correctedWork);
        assertEquals(ResumeReconciliation.DecisionType.ROUTE, decision.type);
        assertSame(correctedWork, decision.selected);
        assertEquals(OrchestrationStore.SIDE_CHAT, decision.targetSide());
    }

'''
if case not in r:
    if anchor not in r:
        raise SystemExit('resume routing test anchor not found')
    r = r.replace(anchor, case + anchor, 1)
resume_test.write_text(r, encoding='utf-8')

print('RC4 resume reconciliation fix materialized')
