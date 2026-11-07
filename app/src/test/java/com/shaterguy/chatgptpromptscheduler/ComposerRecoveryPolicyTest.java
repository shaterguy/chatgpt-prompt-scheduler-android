package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ComposerRecoveryPolicyTest {
    @Test public void waitsUntilStallBoundaryAndBoundsReentryWithinOriginalDeadline() {
        ComposerRecoveryPolicy policy = new ComposerRecoveryPolicy();
        assertEquals(ComposerRecoveryPolicy.Decision.WAIT, policy.onRetry(true, false, 10_000, 90_000));
        assertEquals(ComposerRecoveryPolicy.Decision.WAIT, policy.onRetry(true, false, 24_999, 90_000));
        assertEquals(ComposerRecoveryPolicy.Decision.REENTER, policy.onRetry(true, false, 25_000, 90_000));
        assertEquals(ComposerRecoveryPolicy.Decision.WAIT, policy.onRetry(true, false, 30_000, 90_000));
        assertEquals(ComposerRecoveryPolicy.Decision.REENTER, policy.onRetry(true, false, 45_000, 90_000));
        for (long now = 46_000; now < 90_000; now += 1_000) {
            assertEquals(ComposerRecoveryPolicy.Decision.WAIT, policy.onRetry(true, false, now, 90_000));
        }
        assertEquals(2, policy.recoveryAttempts());
        assertEquals(0, policy.retryAttempts());
        assertEquals(ComposerRecoveryPolicy.Decision.TIMED_OUT, policy.onRetry(true, false, 90_000, 90_000));
    }

    @Test public void missingPollsDoNotSpendInputRetries() {
        ComposerRecoveryPolicy policy = new ComposerRecoveryPolicy();
        for (int i = 0; i < 60; i++) policy.onRetry(true, false, i * 1_000L, 90_000);
        assertEquals(60, policy.missingPolls());
        assertEquals(0, policy.retryAttempts());
        for (int i = 1; i <= 48; i++) {
            assertEquals(ComposerRecoveryPolicy.Decision.WAIT, policy.onRetry(false, false, 60_000 + i, 90_000));
        }
        assertEquals(ComposerRecoveryPolicy.Decision.EXHAUSTED, policy.onRetry(false, false, 61_000, 90_000));
    }

    @Test public void inputStartedIsStickyUntilNewRunAndSubmissionPreventsRecovery() {
        ComposerRecoveryPolicy policy = new ComposerRecoveryPolicy();
        policy.observeInputStarted(true);
        policy.observeInputStarted(false);
        assertTrue(policy.inputStarted());
        assertEquals(ComposerRecoveryPolicy.Decision.WAIT, policy.onRetry(true, false, 1_000, 90_000));
        assertEquals(ComposerRecoveryPolicy.Decision.WAIT, policy.onRetry(true, false, 31_000, 90_000));
        assertEquals(0, policy.recoveryAttempts());
        policy.reset();
        assertFalse(policy.inputStarted());
        assertEquals(ComposerRecoveryPolicy.Decision.WAIT, policy.onRetry(true, true, 1_000, 90_000));
        assertEquals(ComposerRecoveryPolicy.Decision.WAIT, policy.onRetry(true, true, 31_000, 90_000));
        assertEquals(0, policy.recoveryAttempts());
    }

    @Test public void rejectsRecoveryWithoutUsefulRemainingBudget() {
        ComposerRecoveryPolicy policy = new ComposerRecoveryPolicy();
        policy.onRetry(true, false, 60_000, 90_000);
        assertEquals(ComposerRecoveryPolicy.Decision.WAIT, policy.onRetry(true, false, 75_001, 90_000));
        assertEquals(0, policy.recoveryAttempts());
        assertEquals(ComposerRecoveryPolicy.Decision.TIMED_OUT, policy.onRetry(false, false, 90_000, 90_000));
    }

    @Test public void availableComposerBreaksContinuousMissingWindowButDoesNotResetRecoveryLimit() {
        ComposerRecoveryPolicy policy = new ComposerRecoveryPolicy();
        policy.onRetry(true, false, 1_000, 90_000);
        policy.onRetry(false, false, 15_000, 90_000);
        assertEquals(ComposerRecoveryPolicy.Decision.WAIT, policy.onRetry(true, false, 16_000, 90_000));
        assertEquals(ComposerRecoveryPolicy.Decision.REENTER, policy.onRetry(true, false, 31_000, 90_000));
        assertEquals(1, policy.retryAttempts());
        assertEquals(1, policy.recoveryAttempts());
    }
}
