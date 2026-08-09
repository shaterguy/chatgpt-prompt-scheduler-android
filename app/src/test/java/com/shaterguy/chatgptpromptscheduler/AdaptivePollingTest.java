package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class AdaptivePollingTest {
    @Test
    public void responseRetryUsesExpectedTiers() {
        assertEquals(3_000L, AdaptivePolling.delayForRetryCount(1));
        assertEquals(3_000L, AdaptivePolling.delayForRetryCount(3));
        assertEquals(5_000L, AdaptivePolling.delayForRetryCount(4));
        assertEquals(10_000L, AdaptivePolling.delayForRetryCount(8));
        assertEquals(15_000L, AdaptivePolling.delayForRetryCount(15));
        assertEquals(15_000L, AdaptivePolling.delayForRetryCount(10_000));
    }

    @Test
    public void epochResetRestoresFastTier() {
        AdaptivePolling polling = new AdaptivePolling();
        polling.onRetry(11L);
        polling.onRetry(11L);
        polling.onRetry(11L);
        polling.onRetry(11L);
        assertEquals(1, polling.tier());
        AdaptivePolling.Decision decision = polling.onRetry(12L);
        assertEquals(1, decision.retryCount);
        assertEquals(0, decision.tier);
        assertEquals(3_000L, decision.delayMs);
    }

    @Test
    public void twoMinuteAdaptiveWaitCutsEvaluationsByAtLeastHalf() {
        long elapsed = 0L;
        int adaptive = 0;
        while (elapsed + AdaptivePolling.delayForRetryCount(adaptive + 1) <= 120_000L) {
            elapsed += AdaptivePolling.delayForRetryCount(++adaptive);
        }
        int fixed = (int) (120_000L / AdaptivePolling.FAST_DELAY_MS);
        assertTrue("adaptive=" + adaptive + ", fixed=" + fixed, adaptive <= fixed / 2);
    }

    @Test
    public void twoMinuteResumeWaitIsFarBelowLegacyEighteenHundredMsCadence() {
        long elapsed = 0L;
        int adaptive = 0;
        while (elapsed + AdaptivePolling.delayForRetryCount(adaptive + 1) <= 120_000L) {
            elapsed += AdaptivePolling.delayForRetryCount(++adaptive);
        }
        int legacyFixed = (int) (120_000L / 1_800L);
        assertTrue("adaptive=" + adaptive + ", legacyFixed=" + legacyFixed,
                adaptive <= legacyFixed / 2);
    }
}
