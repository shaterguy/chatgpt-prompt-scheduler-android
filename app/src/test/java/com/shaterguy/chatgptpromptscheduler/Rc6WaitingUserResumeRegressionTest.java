package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Rc6WaitingUserResumeRegressionTest {
    @Test
    public void fullyProvisionedWaitingUserJobStillSendsUserResolvedPrompt() {
        assertEquals(OrchestrationActivity.ResumePath.USER_ACTION_RESOLVED,
                OrchestrationActivity.resumePath(true, true));
        assertEquals("[AUTOMATION_USER_RESOLVED AR-20260810-101754-6XZR4U REFRESH-DOORAY-DBINS-ACTIONS]",
                OrchestrationStore.userResolvedPrompt(
                        "AR-20260810-101754-6XZR4U",
                        "REFRESH-DOORAY-DBINS-ACTIONS"));
    }

    @Test
    public void ordinaryFullRelayResumeStillUsesReconciliation() {
        assertEquals(OrchestrationActivity.ResumePath.RECONCILE,
                OrchestrationActivity.resumePath(false, true));
    }
}
