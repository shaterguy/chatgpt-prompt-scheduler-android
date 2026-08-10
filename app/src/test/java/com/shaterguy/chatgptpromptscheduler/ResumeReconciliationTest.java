package com.shaterguy.chatgptpromptscheduler;

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

    @Test public void correctedWorkSignalAfterPauseWinsOverOlderChatSignal() {
        ResumeReconciliation.Candidate chat = candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_SEND_WORK JOB-7 S001 R001]");
        ResumeReconciliation.Candidate correctedWork = candidate(OrchestrationStore.SIDE_WORK,
                "[AR_SEND_CHAT JOB-7 S001 R002]");
        ResumeReconciliation.Decision decision = decide(chat, correctedWork);
        assertEquals(ResumeReconciliation.DecisionType.ROUTE, decision.type);
        assertSame(correctedWork, decision.selected);
        assertEquals(OrchestrationStore.SIDE_CHAT, decision.targetSide());
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
