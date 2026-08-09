package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ResponseTimingPolicyTest {
    @Test
    public void softAndHardBoundariesUseMonotonicElapsedTime() {
        ResponseTimingPolicy.Decision beforeSoft = ResponseTimingPolicy.evaluate(
                ResponseTimingPolicy.SOFT_YIELD_MS - 1L, true, false, false);
        assertFalse(beforeSoft.softYieldDue);
        assertFalse(beforeSoft.hardFallbackDue);

        ResponseTimingPolicy.Decision atSoft = ResponseTimingPolicy.evaluate(
                ResponseTimingPolicy.SOFT_YIELD_MS, true, false, false);
        assertTrue(atSoft.softYieldDue);
        assertFalse(atSoft.hardFallbackDue);

        ResponseTimingPolicy.Decision beforeHard = ResponseTimingPolicy.evaluate(
                ResponseTimingPolicy.HARD_FALLBACK_MS - 1L, true, true, false);
        assertFalse(beforeHard.hardFallbackDue);

        ResponseTimingPolicy.Decision notStreaming = ResponseTimingPolicy.evaluate(
                ResponseTimingPolicy.HARD_FALLBACK_MS, false, true, false);
        assertFalse(notStreaming.hardFallbackDue);

        ResponseTimingPolicy.Decision atHard = ResponseTimingPolicy.evaluate(
                ResponseTimingPolicy.HARD_FALLBACK_MS, true, true, false);
        assertTrue(atHard.hardFallbackDue);

        ResponseTimingPolicy.Decision alreadyPending = ResponseTimingPolicy.evaluate(
                ResponseTimingPolicy.HARD_FALLBACK_MS, true, true, true);
        assertFalse(alreadyPending.hardFallbackDue);
    }
}
