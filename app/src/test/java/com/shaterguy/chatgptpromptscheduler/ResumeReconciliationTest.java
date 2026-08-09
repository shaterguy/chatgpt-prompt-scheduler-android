package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ResumeReconciliationTest {
    private static final String JOB = "JOB-7";

    private static ResumeReconciliation.Candidate candidate(String side, String signal,
                                                             String predecessor, int predecessorIndex,
                                                             int messageIndex) {
        return ResumeReconciliation.acceptCandidate(JOB, side, signal, predecessor,
                predecessorIndex, messageIndex);
    }

    @Test
    public void domPositionBeatsStaleLocalPositionAndUsesNumericStepRoundOrdering() {
        ResumeReconciliation.Candidate chat = candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_SEND_WORK JOB-7 S004 R002]",
                "[AUTOMATION_CHAT_REVIEW JOB-7 S004 R001]", 3, 8);
        ResumeReconciliation.Candidate olderWork = candidate(OrchestrationStore.SIDE_WORK,
                "[AR_SEND_CHAT JOB-7 S004 R001]",
                "[AUTOMATION_WORK_STEP JOB-7 S004 R001]", 2, 7);

        ResumeReconciliation.Decision decision = ResumeReconciliation.select(
                ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_CHAT, chat),
                ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK, olderWork));

        assertEquals(ResumeReconciliation.DecisionType.ROUTE, decision.type);
        assertSame(chat, decision.selected);
        assertEquals("[AUTOMATION_WORK_STEP JOB-7 S004 R002]", decision.prompt());
    }

    @Test
    public void sameStepRoundUsesCausalPhaseInsteadOfRoomOrStringOrder() {
        ResumeReconciliation.Candidate chatDispatch = candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_SEND_WORK JOB-7 S004 R002]",
                "[AUTOMATION_CHAT_REVIEW JOB-7 S004 R001]", 1, 4);
        ResumeReconciliation.Candidate workReturn = candidate(OrchestrationStore.SIDE_WORK,
                "[AR_SEND_CHAT JOB-7 S004 R002]",
                "[AUTOMATION_WORK_STEP JOB-7 S004 R002]", 2, 5);
        ResumeReconciliation.Candidate chatReviewContinue = candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_CONTINUE_SAME JOB-7 S004 R002]",
                "[AUTOMATION_CHAT_REVIEW JOB-7 S004 R002]", 3, 6);

        ResumeReconciliation.Decision workDecision = ResumeReconciliation.select(
                ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_CHAT, chatDispatch),
                ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK, workReturn));
        assertSame(workReturn, workDecision.selected);

        ResumeReconciliation.Decision reviewDecision = ResumeReconciliation.select(
                new ResumeReconciliation.RoomScan(OrchestrationStore.SIDE_CHAT, true, false, false,
                        java.util.List.of(chatDispatch, chatReviewContinue)),
                ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK, workReturn));
        assertSame(chatReviewContinue, reviewDecision.selected);
    }

    @Test
    public void generatingEitherRoomPreventsReplay() {
        ResumeReconciliation.Candidate chat = candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_SEND_WORK JOB-7 S001 R001]", "[AUTOMATION_START JOB-7]", 0, 1);
        ResumeReconciliation.Decision decision = ResumeReconciliation.select(
                new ResumeReconciliation.RoomScan(OrchestrationStore.SIDE_CHAT, true, true, false,
                        java.util.List.of(chat)),
                ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK));
        assertEquals(ResumeReconciliation.DecisionType.WAIT_FOR_IDLE, decision.type);
    }

    @Test
    public void malformedForeignAndOrphanSignalsAreExcluded() {
        assertNull(candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_SEND_WORK OTHER S001 R001]", "[AUTOMATION_START JOB-7]", 0, 1));
        assertNull(candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_SEND_WORK JOB-7 S1 R001]", "[AUTOMATION_START JOB-7]", 0, 1));
        assertNull(candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_SEND_WORK JOB-7 S001 R001]", "", -1, 1));
        assertNull(candidate(OrchestrationStore.SIDE_WORK,
                "[AR_SEND_WORK JOB-7 S001 R001]", "[AUTOMATION_WORK_STEP JOB-7 S001 R001]", 0, 1));
    }

    @Test
    public void targetPromptMappingNeverUsesStalePromptText() {
        ResumeReconciliation.Candidate work = candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_SEND_WORK JOB-7 S002 R003]",
                "[AUTOMATION_CHAT_REVIEW JOB-7 S002 R002]", 4, 9);
        ResumeReconciliation.Decision decision = ResumeReconciliation.select(
                ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_CHAT, work),
                ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK));
        assertEquals("[AUTOMATION_WORK_STEP JOB-7 S002 R003]", decision.prompt());
        assertTrue(decision.selected.positionStep.equals("S002"));
        assertTrue(decision.selected.positionRound.equals("R003"));
    }

    @Test
    public void userResolvedUsesThePrecedingUserActionPosition() {
        ResumeReconciliation.Candidate next = ResumeReconciliation.acceptCandidate(JOB,
                OrchestrationStore.SIDE_CHAT,
                "[AR_SEND_WORK JOB-7 S005 R001]",
                "[AUTOMATION_USER_RESOLVED JOB-7 ACTION-1]",
                "[AR_USER_ACTION_REQUIRED JOB-7 S004 R002 ACTION-1]", 8, 10);
        assertTrue(next != null);
        assertEquals("S005", next.positionStep);
        assertEquals("R001", next.positionRound);

        assertNull(ResumeReconciliation.acceptCandidate(JOB, OrchestrationStore.SIDE_CHAT,
                "[AR_SEND_WORK JOB-7 S005 R001]",
                "[AUTOMATION_USER_RESOLVED JOB-7 ACTION-1]", "", 8, 10));
    }

    @Test
    public void equalProtocolRankWithDifferentSignalsFailsClosed() {
        ResumeReconciliation.Candidate first = candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_DONE JOB-7]",
                "[AUTOMATION_CHAT_REVIEW JOB-7 S002 R002]", 1, 4);
        ResumeReconciliation.Candidate second = candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_USER_ACTION_REQUIRED JOB-7 S002 R002 ACTION-1]",
                "[AUTOMATION_CHAT_REVIEW JOB-7 S002 R002]", 2, 5);
        assertEquals(ResumeReconciliation.DecisionType.AMBIGUOUS,
                ResumeReconciliation.select(
                        ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_CHAT, first, second),
                        ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_WORK)).type);
    }

    @Test
    public void sameCandidateUsesProtocolIdentityAndIgnoresDomIndexChanges() {
        ResumeReconciliation.Candidate first = candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_SEND_WORK JOB-7 S004 R002]",
                "[AUTOMATION_CHAT_REVIEW JOB-7 S004 R001]", 3, 8);
        ResumeReconciliation.Candidate confirmation = candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_SEND_WORK JOB-7 S004 R002]",
                "[AUTOMATION_CHAT_REVIEW JOB-7 S004 R001]", 9, 14);

        assertTrue(ResumeReconciliation.sameCandidate(first, confirmation));
        assertTrue(ResumeReconciliation.sameCandidate(confirmation,
                ResumeReconciliation.highestCandidate(
                        ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_CHAT, confirmation))));
    }

    @Test
    public void sourceFreshnessRejectsSameRankDifferentSignal() {
        ResumeReconciliation.Candidate first = candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_SEND_WORK JOB-7 S004 R002]",
                "[AUTOMATION_CHAT_REVIEW JOB-7 S004 R001]", 3, 8);
        ResumeReconciliation.Candidate conflict = candidate(OrchestrationStore.SIDE_CHAT,
                "[AR_SEND_WORK JOB-7 S004 R002]",
                "[AUTOMATION_CONTINUE_SAME JOB-7 S004 R001]", 7, 12);

        assertNull(ResumeReconciliation.highestCandidate(
                ResumeReconciliation.RoomScan.idle(OrchestrationStore.SIDE_CHAT, first, conflict)));
    }
}
