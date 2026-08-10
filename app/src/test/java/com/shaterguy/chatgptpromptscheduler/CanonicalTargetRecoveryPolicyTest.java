package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CanonicalTargetRecoveryPolicyTest {
    @Test
    public void loadingBeforeGraceDoesNotReenterOrConsumeActionBudget() {
        assertEquals(CanonicalTargetRecoveryPolicy.Decision.WAIT,
                CanonicalTargetRecoveryPolicy.decide(
                        14_001L, 1L, false, true, false, false, 0));
        assertEquals(1_000L,
                CanonicalTargetRecoveryPolicy.delayUntilGrace(14_001L, 1L));
    }

    @Test
    public void loadingAfterGraceReentersEvenWhenProgressIsIncomplete() {
        assertEquals(CanonicalTargetRecoveryPolicy.Decision.REENTER,
                CanonicalTargetRecoveryPolicy.decide(
                        15_001L, 1L, false, true, false, false, 0));
    }

    @Test
    public void readyTargetDoesNotReenter() {
        assertEquals(CanonicalTargetRecoveryPolicy.Decision.READY,
                CanonicalTargetRecoveryPolicy.decide(
                        30_000L, 1L, true, false, true, true, 0));
    }

    @Test
    public void differentConversationNeverReenters() {
        assertEquals(CanonicalTargetRecoveryPolicy.Decision.TARGET_CHANGED,
                CanonicalTargetRecoveryPolicy.decide(
                        30_000L, 1L, false, false, false, false, 0));
    }

    @Test
    public void reentryBudgetIsSharedAndBounded() {
        assertTrue(CanonicalTargetRecoveryPolicy.canReenter(0));
        assertTrue(CanonicalTargetRecoveryPolicy.canReenter(2));
        assertFalse(CanonicalTargetRecoveryPolicy.canReenter(3));
        assertEquals(CanonicalTargetRecoveryPolicy.Decision.EXHAUSTED,
                CanonicalTargetRecoveryPolicy.decide(
                        30_000L, 1L, false, true, false, false, 3));
    }
}
