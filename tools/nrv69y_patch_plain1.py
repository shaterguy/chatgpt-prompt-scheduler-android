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

p="app/src/main/java/com/shaterguy/chatgptpromptscheduler/TargetParser.java"
rep(p,
'''public final class TargetParser {
    private TargetParser() {}''',
'''public final class TargetParser {
    public enum ConversationTargetState { MATCH, TRANSIENT, DIFFERENT }

    private TargetParser() {}''')
rep(p,
'''            case "existing" -> expectedConversation != null
                    && expectedConversation.equals(actualConversation)
                    && (expectedProject == null ? actualProject == null : expectedProject.equals(actualProject));''',
'''            case "existing" -> expectedConversation != null
                    && expectedConversation.equals(actualConversation);''')
anchor='''    /**
     * Startup-only identity check. ChatGPT may normalize a project conversation URL to another
     * SPA path while retaining the same /c/{conversationId}; that is still the same room.
     */
'''
insert='''    /**
     * Conversation IDs are the canonical room identity. A temporary home/project-root/about:blank
     * route is recoverable; only a concrete different /c/{id} proves a room change.
     */
    public static ConversationTargetState classifyConversationTarget(String expectedUrl, String actualUrl) {
        String expectedConversation = conversationId(expectedUrl);
        if (expectedConversation == null) return ConversationTargetState.DIFFERENT;
        if (actualUrl == null || actualUrl.isBlank() || "about:blank".equalsIgnoreCase(actualUrl))
            return ConversationTargetState.TRANSIENT;
        if (!isSupported(actualUrl)) return ConversationTargetState.DIFFERENT;
        String actualConversation = conversationId(actualUrl);
        if (expectedConversation.equals(actualConversation)) return ConversationTargetState.MATCH;
        if (actualConversation != null) return ConversationTargetState.DIFFERENT;
        if (isHomePath(actualUrl)) return ConversationTargetState.TRANSIENT;
        String expectedProject = projectId(expectedUrl);
        String actualProject = projectId(actualUrl);
        if (expectedProject != null && expectedProject.equals(actualProject) && isProjectHome(actualUrl))
            return ConversationTargetState.TRANSIENT;
        return ConversationTargetState.DIFFERENT;
    }

    public static boolean isTransientConversationRoute(String expectedUrl, String actualUrl) {
        return classifyConversationTarget(expectedUrl, actualUrl) == ConversationTargetState.TRANSIENT;
    }

'''
rep(p,anchor,insert+anchor)
rep(p,
'''        if (!isSupported(expectedUrl) || !isSupported(actualUrl)) return false;
        String expectedConversation = conversationId(expectedUrl);
        String actualConversation = conversationId(actualUrl);
        return expectedConversation != null && expectedConversation.equals(actualConversation);''',
'''        return classifyConversationTarget(expectedUrl, actualUrl) == ConversationTargetState.MATCH;''')
rep(p,
'''        return "type=" + targetType + " expected=" + expectedUrl + " actual=" + (actualUrl == null ? "" : actualUrl);''',
'''        return "type=" + targetType
                + " expected_project=" + value(projectId(expectedUrl))
                + " expected_conversation=" + value(conversationId(expectedUrl))
                + " actual_project=" + value(projectId(actualUrl))
                + " actual_conversation=" + value(conversationId(actualUrl));''')
rep(p,
'''    private static boolean isHomePath(String url) {''',
'''    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static boolean isHomePath(String url) {''')

p="app/src/main/java/com/shaterguy/chatgptpromptscheduler/ResumeReconciliation.java"
sub(p,
r'''    public static Candidate acceptCandidate\(String expectedJobId, String sourceSide, String rawSignal,
                                            String predecessorPrompt, String predecessorSignal,
                                            int predecessorIndex, int messageIndex\) \{.*?
    \}

    public static Decision select''',
'''    public static Candidate acceptCandidate(String expectedJobId, String sourceSide, String rawSignal,
                                            String predecessorPrompt, String predecessorSignal,
                                            int predecessorIndex, int messageIndex) {
        OrchestrationSignal.ParseResult parsed = OrchestrationSignal.parseDetailed(rawSignal, expectedJobId);
        if (!parsed.isValid() || messageIndex < 0) return null;
        OrchestrationSignal signal = parsed.signal;
        if (!validSource(signal, sourceSide)) return null;
        String predecessorKind = promptKind(predecessorPrompt, expectedJobId);
        String positionStep = signal.step;
        String positionRound = signal.round;
        if (positionStep.isEmpty() || positionRound.isEmpty()) {
            positionStep = promptStep(predecessorPrompt, expectedJobId);
            positionRound = promptRound(predecessorPrompt, expectedJobId);
        }
        if (positionStep.isEmpty() || positionRound.isEmpty()) {
            if (!OrchestrationStore.isTerminalSignal(signal.type)) return null;
            positionStep = "S999";
            positionRound = "R999";
        }
        if (!validSequence(positionStep, positionRound)) return null;
        return new Candidate(signal, sourceSide, predecessorPrompt, predecessorKind, predecessorSignal,
                predecessorIndex, messageIndex, 0, positionStep, positionRound);
    }

    public static Decision select''')
sub(p,
r'''    public static Decision select\(RoomScan chat, RoomScan work\) \{.*?
    \}

    public static int comparePosition''',
'''    public static Decision select(RoomScan chat, RoomScan work) {
        if (chat == null || work == null) return ambiguous("ROOM_SCAN_MISSING");
        if (!chat.mainPresent || !work.mainPresent) return waitForIdle("ROOM_DOM_NOT_READY");
        if (chat.authRequired || work.authRequired) return ambiguous("AUTH_REQUIRED");
        if (chat.generating || work.generating) return waitForIdle("ROOM_GENERATING");

        RoomChoice chatChoice = chooseRoom(chat);
        RoomChoice workChoice = chooseRoom(work);
        if (chatChoice.conflict || workChoice.conflict) return ambiguous("SAME_ROOM_CONFLICT");
        Candidate c = chatChoice.candidate;
        Candidate w = workChoice.candidate;
        if (c == null && w == null) return waitForIdle("NO_VALID_SIGNAL");
        if (c == null) return classify(w, "ONE_SIDED_WORK");
        if (w == null) return classify(c, "ONE_SIDED_CHAT");

        int position = comparePosition(c, w);
        if (position > 0) return classify(c, "CHAT_NEWER_POSITION");
        if (position < 0) return classify(w, "WORK_NEWER_POSITION");
        if (c.signal.type == OrchestrationSignal.Type.USER_ACTION_REQUIRED)
            return classify(c, "CHAT_USER_ACTION_TIE");
        return classify(w, "WORK_TIE");
    }

    public static int comparePosition''')
sub(p,
r'''    public static int comparePosition\(Candidate left, Candidate right\) \{.*?
    \}

    /\*\*
     \* Returns the highest candidate.*?
    public static Candidate highestCandidate\(RoomScan room\) \{.*?
    \}

    /\*\* Compares the protocol identity.*?
    public static boolean sameCandidate\(Candidate left, Candidate right\) \{.*?
    \}''',
'''    public static int comparePosition(Candidate left, Candidate right) {
        int step = Integer.compare(sequenceNumber(left.positionStep), sequenceNumber(right.positionStep));
        if (step != 0) return step;
        return Integer.compare(sequenceNumber(left.positionRound), sequenceNumber(right.positionRound));
    }

    /** Returns the highest numeric Step/Round candidate from one room, or null on same-position conflict. */
    public static Candidate highestCandidate(RoomScan room) {
        RoomChoice choice = chooseRoom(room);
        return choice.conflict ? null : choice.candidate;
    }

    /** Protocol identity deliberately ignores DOM index and predecessor/causal metadata. */
    public static boolean sameCandidate(Candidate left, Candidate right) {
        if (left == null || right == null) return false;
        return left.sourceSide.equals(right.sourceSide)
                && left.signal.type == right.signal.type
                && left.signal.step.equals(right.signal.step)
                && left.signal.round.equals(right.signal.round)
                && left.signal.actionId.equals(right.signal.actionId)
                && left.raw().equals(right.raw())
                && left.positionStep.equals(right.positionStep)
                && left.positionRound.equals(right.positionRound);
    }''')
anchor='''    private static boolean validSource(OrchestrationSignal signal, String sourceSide) {'''
helpers='''    private static final class RoomChoice {
        final Candidate candidate;
        final boolean conflict;
        RoomChoice(Candidate candidate, boolean conflict) {
            this.candidate = candidate;
            this.conflict = conflict;
        }
    }

    private static RoomChoice chooseRoom(RoomScan room) {
        if (room == null || !room.mainPresent || room.authRequired || room.generating || room.candidates.isEmpty())
            return new RoomChoice(null, false);
        List<Candidate> values = new ArrayList<>(room.candidates);
        values.sort(Comparator.comparingInt((Candidate value) -> sequenceNumber(value.positionStep))
                .thenComparingInt(value -> sequenceNumber(value.positionRound)).reversed());
        Candidate best = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            Candidate candidate = values.get(i);
            if (comparePosition(candidate, best) != 0) break;
            if (!sameCandidate(candidate, best)) return new RoomChoice(null, true);
            if (candidate.messageIndex > best.messageIndex) best = candidate;
        }
        return new RoomChoice(best, false);
    }

    private static Decision classify(Candidate candidate, String reason) {
        if (candidate == null) return waitForIdle("NO_VALID_SIGNAL");
        if (candidate.signal.type == OrchestrationSignal.Type.USER_ACTION_REQUIRED)
            return new Decision(DecisionType.USER_ACTION, candidate, reason);
        if (OrchestrationStore.isTerminalSignal(candidate.signal.type))
            return new Decision(DecisionType.TERMINAL, candidate, reason);
        return new Decision(DecisionType.ROUTE, candidate, reason);
    }

    private static Decision waitForIdle(String reason) {
        return new Decision(DecisionType.WAIT_FOR_IDLE, null, reason);
    }

'''
rep(p,anchor,helpers+anchor)

p="app/src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationScript.java"
rep(p,
'''if(predecessor)candidates.push({signal,predecessor:predecessor.raw,predecessor_kind:predecessor.kind,predecessor_signal:predecessorSignal,predecessor_index:predecessorIndex,message_index:i});''',
'''candidates.push({signal,predecessor:predecessor?.raw||'',predecessor_kind:predecessor?.kind||'',predecessor_signal:predecessorSignal,predecessor_index:predecessorIndex,message_index:i});''')

p="app/src/main/java/com/shaterguy/chatgptpromptscheduler/AutomationScript.java"
rep(p,
'''                "let modePrior='';try{modePrior=sessionStorage.getItem(modeKey)||'';}catch(_){}" +
                "const modeSelected=!!mode&&(mode.getAttribute('aria-pressed')==='true'||mode.getAttribute('aria-checked')==='true'||/active|selected|checked/.test(exactText(mode.dataset?.state||'')));" +
                "const modeDiagnostics={requested:" + jsQuote(requestedMode) + ",candidateFound:!!mode,candidateLabel:mode?clip(exactText((mode.innerText||'')+' '+(mode.getAttribute('aria-label')||'')),120):'',selected:modeSelected,clicked:false,priorClick:!!modePrior};" +
                "if(mode&&!modeSelected&&!modePrior){const value=JSON.stringify({at:Date.now(),label:modeDiagnostics.candidateLabel});try{sessionStorage.setItem(modeKey,value);}catch(_){}window[modeKey]=value;mode.click();modeDiagnostics.clicked=true;}" +
                "if(modeDiagnostics.clicked)return result('RETRY','모드 전환 반영 대기',{...routeDiagnostics,mode:modeDiagnostics});" +''',
'''                "let modeClicks=0;try{modeClicks=Math.max(0,Number(sessionStorage.getItem(modeKey)||0));}catch(_){}" +
                "const modeSelected=!!mode&&(mode.getAttribute('aria-pressed')==='true'||mode.getAttribute('aria-checked')==='true'||/active|selected|checked/.test(exactText(mode.dataset?.state||'')));" +
                "const modeDiagnostics={requested:" + jsQuote(requestedMode) + ",candidateFound:!!mode,candidateLabel:mode?clip(exactText((mode.innerText||'')+' '+(mode.getAttribute('aria-label')||'')),120):'',selected:modeSelected,clicked:false,clickCount:modeClicks};" +
                "if(mode&&!modeSelected){if(modeClicks>=3)return result('MODE_SELECTION_FAILED','Work/Chat 모드 실제 적용을 확인하지 못했습니다.',{...routeDiagnostics,mode:modeDiagnostics});modeClicks++;try{sessionStorage.setItem(modeKey,String(modeClicks));}catch(_){}mode.click();modeDiagnostics.clicked=true;modeDiagnostics.clickCount=modeClicks;}" +
                "if(modeDiagnostics.clicked)return result('RETRY','모드 전환 반영 대기',{...routeDiagnostics,mode:modeDiagnostics});" +''')
rep(p,
'''if(expectedType==='existing')targetOk=!!expectedConversation&&actualConversation===expectedConversation&&(expectedProject?actualProject===expectedProject:!actualProject);''',
'''if(expectedType==='existing')targetOk=!!expectedConversation&&actualConversation===expectedConversation;''')

p="app/src/main/java/com/shaterguy/chatgptpromptscheduler/ExecutionService.java"
rep(p,
'''            case "TARGET_CONTEXT_MISMATCH" -> recoverTargetRoute(detail);
            case "AUTH_REQUIRED", "DRAFT_PRESENT" -> finish(false, status, contextualDetail(detail));''',
'''            case "TARGET_CONTEXT_MISMATCH" -> recoverTargetRoute(detail);
            case "MODE_SELECTION_FAILED", "MODE_SELECTION_AMBIGUOUS" ->
                    finish(false, "WORK_MODE_SELECT_FAILED", contextualDetail(detail));
            case "AUTH_REQUIRED", "DRAFT_PRESENT" -> finish(false, status, contextualDetail(detail));''')
rep(p,
'''        if (detail.contains("전송 버튼")) return "SEND_BUTTON_UNAVAILABLE";''',
'''        if (detail.contains("Work 모드") || detail.contains("모드 실제 적용") || detail.contains("모드 전환"))
            return "WORK_MODE_SELECT_FAILED";
        if (detail.contains("전송 버튼")) return "SEND_BUTTON_UNAVAILABLE";''')

p="app/src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationActivity.java"
rep(p,
'''    static ResumePath resumePath(boolean waitingForUser, boolean fullRelay) {
        if (waitingForUser) return ResumePath.USER_ACTION_RESOLVED;
        return fullRelay ? ResumePath.RECONCILE : ResumePath.BOOTSTRAP;
    }''',
'''    static ResumePath resumePath(boolean waitingForUser, boolean fullRelay) {
        if (fullRelay) return ResumePath.RECONCILE;
        if (waitingForUser) return ResumePath.USER_ACTION_RESOLVED;
        return ResumePath.BOOTSTRAP;
    }''')
rep(p,'''            case RECONCILE -> store.beginReconciliation();''',
       '''            case RECONCILE -> store.beginReconciliation(store.waitingForUser());''')

p="app/src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationStore.java"
rep(p,'''    private static final int SCHEMA_VERSION = 6;''','''    private static final int SCHEMA_VERSION = 7;''')
rep(p,
'''    public boolean waitingForUser() { return preferences.getBoolean("waitingForUser", false); }
    public String actionId() { return preferences.getString("actionId", ""); }''',
'''    public boolean waitingForUser() { return preferences.getBoolean("waitingForUser", false); }
    public boolean resumeUserActionRequested() {
        return preferences.getBoolean("resumeUserActionRequested", false);
    }
    public String actionId() { return preferences.getString("actionId", ""); }''')
sub(p,
r'''    public boolean beginReconciliation\(\) \{
.*?
        return true;
    \}''',
'''    public boolean beginReconciliation() {
        return beginReconciliation(false);
    }

    public boolean beginReconciliation(boolean userActionRequested) {
        if (runJobId().isEmpty() || runChatUrl().isEmpty() || runWorkUrl().isEmpty()) return false;
        commit(preferences.edit().putBoolean("active", true).putBoolean("paused", false)
                .putBoolean("reconciling", true).putString("reconciliationPhase", RECONCILIATION_SCAN_ROOMS)
                .putString("reconciliationSide", SIDE_CHAT)
                .putBoolean("resumeUserActionRequested", userActionRequested || resumeUserActionRequested())
                .putString("status", "재개 상태 재구성 중 · 두 대화방 확인")
                .putString("lastErrorCode", "").putString("error", "").putLong("errorAt", 0L)
                .putLong("phaseStartedAt", System.currentTimeMillis())
                .putString("candidateFingerprint", "").putInt("candidateStability", 0)
                .putLong("epoch", epoch() + 1L));
        return true;
    }''')
anchor='''    public void incrementPoll() {'''
insert='''    public void rebuildForUserResolved(OrchestrationSignal signal, String sourceSide) {
        if (signal == null || signal.type != OrchestrationSignal.Type.USER_ACTION_REQUIRED
                || !SIDE_CHAT.equals(sourceSide) || signal.actionId.isEmpty())
            throw new IllegalArgumentException("재개 가능한 사용자 조치 신호가 아닙니다.");
        long now = System.currentTimeMillis();
        String prompt = userResolvedPrompt(runJobId(), signal.actionId);
        commit(preferences.edit().putBoolean("active", true).putBoolean("paused", false)
                .putBoolean("terminal", false).putBoolean("waitingForUser", false).putString("actionId", "")
                .putBoolean("resumeUserActionRequested", false)
                .putBoolean("reconciling", false).putString("reconciliationPhase", RECONCILIATION_NONE)
                .putString("lastSignalSource", sourceSide).putString("lastAcceptedSignal", signal.raw)
                .putLong("lastSignalAt", now)
                .putString("currentStep", signal.step).putString("currentRound", signal.round)
                .putString("deliveryTarget", SIDE_CHAT).putString("pendingPrompt", prompt)
                .putString("stampedPrompt", "").putString("deliveryState", DELIVERY_PENDING)
                .putString("expectedSignal", "전송 완료 후 일반 Chat의 재검증 결과")
                .putLong("deliveryPreparedAt", 0L).putLong("deliveryAttemptAt", 0L)
                .putString("status", "일반 Chat 사용자 조치 재검증 요청 전송 대기")
                .putString("lastErrorCode", "").putString("error", "").putLong("errorAt", 0L)
                .putLong("phaseStartedAt", now).putLong("pollCountLong", 0L).putInt("pollCount", 0)
                .putString("candidateFingerprint", "").putInt("candidateStability", 0)
                .putString("bootstrapState", FLOW_AUTO_BOOTSTRAP.equals(flowMode())
                        ? BOOTSTRAP_RELAY_ACTIVE : bootstrapState())
                .putLong("epoch", epoch() + 1L));
        resetResponseTiming("RESUME_USER_ACTION_REVALIDATION");
    }

'''
rep(p,anchor,insert+anchor)

print("plain1 ok")
