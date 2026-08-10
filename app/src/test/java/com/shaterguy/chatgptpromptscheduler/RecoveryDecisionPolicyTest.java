package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RecoveryDecisionPolicyTest {
    private static final String PROJECT = "https://chatgpt.com/g/project-1";
    private static final String TARGET = PROJECT + "/c/conversation-1";

    @Test
    public void recognizesBothRateLimitSignals() {
        assertTrue(RecoveryDecisionPolicy.isRateLimitHttp(429));
        assertTrue(RecoveryDecisionPolicy.isRateLimitWebViewError(-15));
        assertFalse(RecoveryDecisionPolicy.isRateLimitHttp(503));
        assertFalse(RecoveryDecisionPolicy.isRateLimitWebViewError(-6));
    }

    @Test
    public void backoffIsBoundedAndExponential() {
        RecoveryBackoff backoff = new RecoveryBackoff();
        assertEquals(1_000L, backoff.next().delayMs);
        assertEquals(2_000L, backoff.next().delayMs);
        assertEquals(4_000L, backoff.next().delayMs);
        assertEquals(8_000L, backoff.next().delayMs);
        assertEquals(15_000L, backoff.next().delayMs);
        assertEquals(15_000L, backoff.next().delayMs);
    }

    @Test
    public void fixedConversationTreatsHomeAsRestorableButDifferentIdAsChanged() {
        RecoveryDecisionPolicy.TargetIntent intent =
                RecoveryDecisionPolicy.targetIntent("existing", false, "");
        assertEquals(RecoveryDecisionPolicy.Decision.RESTORE_TARGET,
                RecoveryDecisionPolicy.decide(intent,
                        RecoveryDecisionPolicy.ObservedLocation.GLOBAL_NEW_CHAT,
                        RecoveryDecisionPolicy.NetworkState.OK,
                        RecoveryDecisionPolicy.UiReadiness.READY,
                        RecoveryDecisionPolicy.SendState.NOT_STARTED));
        assertEquals(RecoveryDecisionPolicy.Decision.TARGET_CHANGED,
                RecoveryDecisionPolicy.decide(intent,
                        RecoveryDecisionPolicy.ObservedLocation.DIFFERENT_CONVERSATION,
                        RecoveryDecisionPolicy.NetworkState.OK,
                        RecoveryDecisionPolicy.UiReadiness.READY,
                        RecoveryDecisionPolicy.SendState.NOT_STARTED));
    }

    @Test
    public void projectAndGlobalNewChatSurfacesRemainAccepted() {
        assertTrue(TargetParser.matchesTarget("project", PROJECT,
                PROJECT + "/new-chat"));
        assertTrue(TargetParser.matchesTarget("project", PROJECT, PROJECT));
        assertTrue(TargetParser.matchesTarget("general", "https://chatgpt.com/",
                "https://chatgpt.com/new-chat"));
        assertEquals(RecoveryDecisionPolicy.ObservedLocation.PROJECT_NEW_CHAT,
                RecoveryDecisionPolicy.classify("project", PROJECT,
                        PROJECT + "/new-chat"));
        assertEquals(RecoveryDecisionPolicy.ObservedLocation.GLOBAL_NEW_CHAT,
                RecoveryDecisionPolicy.classify("general", "https://chatgpt.com/",
                        "https://chatgpt.com/new-chat"));
    }

    @Test
    public void ambiguousSubmitRequiresReconciliationInsteadOfSecondClick() {
        RecoveryDecisionPolicy.Decision decision = RecoveryDecisionPolicy.decide(
                RecoveryDecisionPolicy.TargetIntent.AUTORUN_WORK_FIXED,
                RecoveryDecisionPolicy.ObservedLocation.PROJECT_ROOT,
                RecoveryDecisionPolicy.NetworkState.OK,
                RecoveryDecisionPolicy.UiReadiness.READY,
                RecoveryDecisionPolicy.SendState.AMBIGUOUS);
        assertEquals(RecoveryDecisionPolicy.Decision.RECONCILE_SEND, decision);
    }

    @Test
    public void uiWaitIsNotActionRetry() {
        assertEquals(RecoveryDecisionPolicy.Decision.UI_WAIT,
                RecoveryDecisionPolicy.decide(
                        RecoveryDecisionPolicy.TargetIntent.PROJECT_NEW_CHAT,
                        RecoveryDecisionPolicy.ObservedLocation.PROJECT_NEW_CHAT,
                        RecoveryDecisionPolicy.NetworkState.OK,
                        RecoveryDecisionPolicy.UiReadiness.COMPOSER_MISSING,
                        RecoveryDecisionPolicy.SendState.NOT_STARTED));
    }
}
