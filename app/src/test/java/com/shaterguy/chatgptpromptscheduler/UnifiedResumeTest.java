package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UnifiedResumeTest {
    @Test
    public void waitingUserAlwaysUsesDirectResolvedPath() {
        assertEquals(OrchestrationActivity.ResumePath.USER_ACTION_RESOLVED,
                OrchestrationActivity.resumePath(true, true));
        assertEquals(OrchestrationActivity.ResumePath.USER_ACTION_RESOLVED,
                OrchestrationActivity.resumePath(true, false));
    }

    @Test
    public void fullRelayWithoutWaitingUserReconciles() {
        assertEquals(OrchestrationActivity.ResumePath.RECONCILE,
                OrchestrationActivity.resumePath(false, true));
    }

    @Test
    public void incompleteBootstrapWithoutWaitingUserKeepsBootstrapPath() {
        assertEquals(OrchestrationActivity.ResumePath.BOOTSTRAP,
                OrchestrationActivity.resumePath(false, false));
    }
}
