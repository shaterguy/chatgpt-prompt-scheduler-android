package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArchivedJobWorkspaceTest {
    @Test
    public void nonCurrentJobOpensAsIndependentArchivedWorkspace() {
        assertTrue(OrchestrationActivity.isArchivedJob("JOB-OLD", "JOB-CURRENT"));
        assertFalse(OrchestrationActivity.isArchivedJob("JOB-CURRENT", "JOB-CURRENT"));
        assertFalse(OrchestrationActivity.isArchivedJob("", "JOB-CURRENT"));
    }

    @Test
    public void completionDependsOnlyOnPersistedTerminalSignal() {
        assertEquals("미완료", OrchestrationActivity.completionLabel(false, "[AR_DONE JOB S001 R001]"));
        assertEquals("완료 · AR_DONE", OrchestrationActivity.completionLabel(true, "[AR_DONE JOB S001 R001]"));
        assertEquals("일시정지 terminal · AR_PAUSE",
                OrchestrationActivity.completionLabel(true, "[AR_PAUSE JOB S001 R001]"));
        assertEquals("중단 terminal · AR_ABORTED",
                OrchestrationActivity.completionLabel(true, "[AR_ABORTED JOB S001 R001]"));
        assertEquals("중지 · 사용자 요청",
                OrchestrationActivity.completionLabel(true, "", true));
    }

    @Test
    public void archivedResumeRequiresIncompleteJobAndFullWorkspace() {
        assertTrue(OrchestrationActivity.canResumeArchived(false, true));
        assertFalse(OrchestrationActivity.canResumeArchived(true, true));
        assertFalse(OrchestrationActivity.canResumeArchived(false, false));
    }

    @Test
    public void archivedResumeStaysAvailableAndDetectsWhenAJobSwitchNeedsConfirmation() {
        assertTrue(OrchestrationActivity.canResumeArchived(false, true));
        assertTrue(OrchestrationActivity.hasCompetingActiveJob(
                true, false, "JOB-PAUSED-A", "JOB-RUNNING-B"));
        assertFalse(OrchestrationActivity.hasCompetingActiveJob(
                false, false, "JOB-PAUSED-A", "JOB-PAUSED-B"));
        assertFalse(OrchestrationActivity.hasCompetingActiveJob(
                true, false, "JOB-RUNNING-A", "JOB-RUNNING-A"));
    }

    @Test
    public void newJobIsBlockedOnlyWhileAnotherNonterminalJobIsActivelyRunning() {
        assertFalse(OrchestrationActivity.canStartNewJob(true, false, true));
        assertTrue(OrchestrationActivity.canStartNewJob(false, false, true));
        assertTrue(OrchestrationActivity.canStartNewJob(false, true, true));
        assertTrue(OrchestrationActivity.canStartNewJob(false, false, false));
    }

    @Test
    public void liveControlsFollowDurableActivePausedAndTerminalState() {
        assertTrue(OrchestrationActivity.canResumeLive(true, false, false));
        assertFalse(OrchestrationActivity.canResumeLive(true, true, false));
        assertFalse(OrchestrationActivity.canResumeLive(false, false, false));
        assertFalse(OrchestrationActivity.canResumeLive(true, false, true));

        assertTrue(OrchestrationActivity.canPauseLive(true, false, false));
        assertFalse(OrchestrationActivity.canPauseLive(true, true, false));
        assertFalse(OrchestrationActivity.canPauseLive(true, false, true));

        assertTrue(OrchestrationActivity.canStopLive(true, false));
        assertFalse(OrchestrationActivity.canStopLive(false, false));
        assertFalse(OrchestrationActivity.canStopLive(true, true));
    }

    @Test
    public void runningJobRemainsLiveAfterLeavingAndReopeningItsHistoryCard() {
        String runningJobId = "AR-20260809-RUNNING-TEST";

        assertFalse(OrchestrationActivity.isArchivedJob(runningJobId, runningJobId));
        assertFalse(OrchestrationActivity.canResumeLive(true, true, false));
        assertTrue(OrchestrationActivity.canPauseLive(true, false, false));
        assertTrue(OrchestrationActivity.canStopLive(true, false));
    }

    @Test
    public void pausedFailedOrWaitingJobCanResumeOrStopButRunningJobCannotResumeAgain() {
        assertTrue(OrchestrationActivity.canResumeLive(true, false, false));
        assertTrue(OrchestrationActivity.canStopLive(true, false));
        assertFalse(OrchestrationActivity.canResumeLive(true, true, false));
        assertFalse(OrchestrationActivity.canPauseLive(false, true, false));
    }

    @Test
    public void invalidDraftNeverReplacesLastValidProjectDefault() {
        String valid = "https://chatgpt.com/g/project-one";
        assertEquals("https://chatgpt.com/g/project-two", OrchestrationActivity.projectDefaultToPersist(
                "https://chatgpt.com/g/project-two", valid));
        assertEquals(valid, OrchestrationActivity.projectDefaultToPersist(
                "https://chatgpt.com/", valid));
        assertEquals(valid, OrchestrationActivity.projectDefaultToPersist("", valid));
    }

    @Test
    public void historyCardExplainsCurrentRunningResumableAndTerminalRoles() {
        assertEquals("현재 실행", OrchestrationHistoryActivity.jobRole(true, true, false, true));
        assertEquals("현재 선택 · 재개 가능",
                OrchestrationHistoryActivity.jobRole(true, false, false, true));
        assertEquals("재개 가능", OrchestrationHistoryActivity.jobRole(false, false, false, true));
        assertEquals("기록 전용", OrchestrationHistoryActivity.jobRole(false, false, false, false));
        assertEquals("종료", OrchestrationHistoryActivity.jobRole(false, false, true, true));
    }

    @Test
    public void deleteIsBlockedOnlyForLiveActiveIncompleteJob() {
        assertFalse(OrchestrationActivity.canHideJob(false, true, false));
        assertTrue(OrchestrationActivity.canHideJob(false, false, false));
        assertTrue(OrchestrationActivity.canHideJob(false, true, true));
        assertTrue(OrchestrationActivity.canHideJob(true, true, false));
    }
}
