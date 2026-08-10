from pathlib import Path

def read(p): return Path(p).read_text(encoding='utf-8')
def write(p,s): Path(p).write_text(s,encoding='utf-8')
def rep(p,old,new,n=1):
    s=read(p)
    if s.count(old)<n: raise SystemExit(f'missing {p}: {old[:100]!r} count={s.count(old)}')
    write(p,s.replace(old,new,n))

p='app/src/test/java/com/shaterguy/chatgptpromptscheduler/CoreLogicTest.java'
rep(p,
'''        assertFalse(TargetParser.matchesTarget("existing", expected, "https://chatgpt.com/c/abc"));''',
'''        assertTrue(TargetParser.matchesTarget("existing", expected, "https://chatgpt.com/c/abc"));
        assertEquals(TargetParser.ConversationTargetState.TRANSIENT,
                TargetParser.classifyConversationTarget(expected, "https://chatgpt.com/g/proj"));''')

p='app/src/test/java/com/shaterguy/chatgptpromptscheduler/OrchestrationSignalTest.java'
rep(p,'''        assertTrue(service.contains("matchesConversationIdentity"));''',
       '''        assertTrue(service.contains("classifyConversationTarget"));''')
rep(p,'''        assertTrue(service.contains("INITIAL_START_TRANSIENT_ROUTE"));''',
       '''        assertTrue(service.contains("TARGET_TRANSIENT_ROUTE"));''')
rep(p,'''        assertTrue(activity.contains("store.beginReconciliation"));''',
       '''        assertTrue(activity.contains("store.beginReconciliation(store.waitingForUser())"));''')
old='''        assertTrue(service.contains("RECONCILIATION_CONFIRM_ROOMS"));
        assertTrue(service.contains("RESUME_STABLE_IDLE_CONFIRMED"));
        assertTrue(service.contains("RESUME_SOURCE_FRESHNESS_CONFIRMED"));
        assertTrue(service.contains("rebuildForExistingPrompt"));
        assertTrue(service.contains("scheduleReconciliationRetry"));
        assertTrue(service.indexOf("RESUME_SOURCE_FRESHNESS_CHECK")
                < service.indexOf("rebuildForExistingPrompt"));'''
new='''        assertTrue(service.contains("rebuildForExistingPrompt"));
        assertTrue(service.contains("rebuildForUserResolved"));
        assertTrue(service.contains("resumeUserActionRequested"));
        assertTrue(service.contains("scheduleReconciliationRetry"));
        assertFalse(service.contains("RESUME_SOURCE_FRESHNESS_CHECK"));'''
rep(p,old,new)

write('app/src/test/java/com/shaterguy/chatgptpromptscheduler/ResumeReconciliationTest.java', r'''package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.*;
import org.junit.Test;

public class ResumeReconciliationTest {
    private static final String JOB = "JOB-7";

    private ResumeReconciliation.Candidate candidate(String side, String raw) {
        return ResumeReconciliation.acceptCandidate(JOB, side, raw, "", "", -1, 1);
    }

    private ResumeReconciliation.Decision decide(ResumeReconciliation.Candidate chat,
                                                   ResumeReconciliation.Candidate work) {
        return ResumeReconciliation.select(
                chat == null ? ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_CHAT)
                        : ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_CHAT, chat),
                work == null ? ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK)
                        : ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK, work));
    }

    @Test public void oneSidedValidSignalRoutes() {
        ResumeReconciliation.Candidate work = candidate(OrchestrationStore.SIDE_WORK,
                "[AR_SEND_CHAT JOB-7 S001 R001]");
        ResumeReconciliation.Decision decision = decide(null, work);
        assertEquals(ResumeReconciliation.DecisionType.ROUTE, decision.type);
        assertSame(work, decision.selected);
    }

    @Test public void generatingEitherRoomWaits() {
        ResumeReconciliation.RoomScan generatingChat = new ResumeReconciliation.RoomScan(
                OrchestrationStore.SIDE_CHAT, true, true, false, java.util.List.of());
        assertEquals(ResumeReconciliation.DecisionType.WAIT_FOR_IDLE,
                ResumeReconciliation.select(generatingChat,
                        ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK)).type);
    }

    @Test public void noValidSignalRetriesInsteadOfFailingAmbiguous() {
        assertEquals(ResumeReconciliation.DecisionType.WAIT_FOR_IDLE,
                decide(null, null).type);
    }

    @Test public void numericStepThenRoundDeterminesFreshness() {
        ResumeReconciliation.Candidate chat = candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_SEND_WORK JOB-7 S010 R001]");
        ResumeReconciliation.Candidate work = candidate(OrchestrationStore.SIDE_WORK,
                "[AR_SEND_CHAT JOB-7 S009 R999]");
        assertSame(chat, decide(chat, work).selected);
        chat = candidate(OrchestrationStore.SIDE_CHAT, "[AR_SEND_WORK JOB-7 S003 R010]");
        work = candidate(OrchestrationStore.SIDE_WORK, "[AR_SEND_CHAT JOB-7 S003 R009]");
        assertSame(chat, decide(chat, work).selected);
    }

    @Test public void sameStepRoundChatUserActionWinsOtherwiseWorkWins() {
        ResumeReconciliation.Candidate chat = candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_USER_ACTION_REQUIRED JOB-7 S003 R004 ACTION-1]");
        ResumeReconciliation.Candidate work = candidate(OrchestrationStore.SIDE_WORK,
                "[AR_SEND_CHAT JOB-7 S003 R004]");
        ResumeReconciliation.Decision action = decide(chat, work);
        assertEquals(ResumeReconciliation.DecisionType.USER_ACTION, action.type);
        assertSame(chat, action.selected);

        chat = candidate(OrchestrationStore.SIDE_CHAT, "[AR_SEND_WORK JOB-7 S005 R001]");
        work = candidate(OrchestrationStore.SIDE_WORK, "[AR_SEND_CHAT JOB-7 S005 R001]");
        assertSame(work, decide(chat, work).selected);
    }

    @Test public void recognizedPredecessorIsNotRequiredForValidAssistantSignal() {
        assertNotNull(ResumeReconciliation.acceptCandidate(JOB, OrchestrationStore.SIDE_WORK,
                "[AR_SEND_CHAT JOB-7 S001 R001]", "ordinary user text", "", -1, 4));
    }

    @Test public void duplicateSameSignalInOneRoomDeduplicatesByIdentity() {
        ResumeReconciliation.Candidate first = ResumeReconciliation.acceptCandidate(JOB,
                OrchestrationStore.SIDE_WORK, "[AR_SEND_CHAT JOB-7 S004 R002]", "", "", 1, 3);
        ResumeReconciliation.Candidate later = ResumeReconciliation.acceptCandidate(JOB,
                OrchestrationStore.SIDE_WORK, "[AR_SEND_CHAT JOB-7 S004 R002]", "", "", 7, 19);
        ResumeReconciliation.Decision decision = ResumeReconciliation.select(
                ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_CHAT),
                ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK, first, later));
        assertEquals(ResumeReconciliation.DecisionType.ROUTE, decision.type);
        assertEquals("[AR_SEND_CHAT JOB-7 S004 R002]", decision.selected.raw());
    }

    @Test public void conflictingSameRoomSignalsAtSamePositionFailClosed() {
        ResumeReconciliation.Candidate send = candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_SEND_WORK JOB-7 S004 R002]");
        ResumeReconciliation.Candidate cont = candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_CONTINUE_SAME JOB-7 S004 R002]");
        assertEquals(ResumeReconciliation.DecisionType.AMBIGUOUS,
                ResumeReconciliation.select(
                        ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_CHAT, send, cont),
                        ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK)).type);
    }

    @Test public void malformedForeignWrongDirectionAndWorkTerminalAreExcluded() {
        assertNull(candidate(OrchestrationStore.SIDE_CHAT, "[AR_SEND_WORK OTHER S001 R001]"));
        assertNull(candidate(OrchestrationStore.SIDE_CHAT, "[AR_SEND_WORK JOB-7 S1 R001]"));
        assertNull(candidate(OrchestrationStore.SIDE_WORK, "[AR_SEND_WORK JOB-7 S001 R001]"));
        assertNull(candidate(OrchestrationStore.SIDE_WORK, "[AR_DONE JOB-7]"));
    }

    @Test public void chatTerminalStops() {
        ResumeReconciliation.Candidate done = ResumeReconciliation.acceptCandidate(JOB,
                OrchestrationStore.SIDE_CHAT, "[AR_DONE JOB-7]",
                "[AUTOMATION_CHAT_REVIEW JOB-7 S004 R002]", "", 3, 5);
        assertNotNull(done);
        assertEquals(ResumeReconciliation.DecisionType.TERMINAL, decide(done, null).type);
    }
}
''')

print('test fix ok')
