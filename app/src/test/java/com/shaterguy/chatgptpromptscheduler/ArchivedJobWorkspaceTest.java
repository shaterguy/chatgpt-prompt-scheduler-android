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
    }

    @Test
    public void archivedResumeRequiresIncompleteJobAndFullWorkspace() {
        assertTrue(OrchestrationActivity.canResumeArchived(false, true));
        assertFalse(OrchestrationActivity.canResumeArchived(true, true));
        assertFalse(OrchestrationActivity.canResumeArchived(false, false));
    }

    @Test
    public void liveControlsFollowDurableActivePausedAndTerminalState() {
        assertTrue(OrchestrationActivity.canResumeLive(true, false));
        assertFalse(OrchestrationActivity.canResumeLive(false, false));
        assertFalse(OrchestrationActivity.canResumeLive(true, true));

        assertTrue(OrchestrationActivity.canPauseLive(true, false, false));
        assertFalse(OrchestrationActivity.canPauseLive(true, true, false));
        assertFalse(OrchestrationActivity.canPauseLive(true, false, true));

        assertTrue(OrchestrationActivity.canStopLive(true, false));
        assertFalse(OrchestrationActivity.canStopLive(false, false));
        assertFalse(OrchestrationActivity.canStopLive(true, true));
    }

    @Test
    public void deleteIsBlockedOnlyForLiveActiveIncompleteJob() {
        assertFalse(OrchestrationActivity.canHideJob(false, true, false));
        assertTrue(OrchestrationActivity.canHideJob(false, false, false));
        assertTrue(OrchestrationActivity.canHideJob(false, true, true));
        assertTrue(OrchestrationActivity.canHideJob(true, true, false));
    }
}
