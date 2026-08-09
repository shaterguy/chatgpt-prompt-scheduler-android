package com.shaterguy.chatgptpromptscheduler;

/**
 * Deterministic response-wait polling policy. The epoch prevents a long wait in one delivery from
 * slowing down a later delivery or a recovered WebView state.
 */
public final class AdaptivePolling {
    public static final long FAST_DELAY_MS = 3_000L;
    public static final long[] DELAYS_MS = {3_000L, 5_000L, 10_000L, 15_000L};

    private long epoch = Long.MIN_VALUE;
    private int retryCount;
    private int tier;

    public Decision onRetry(long responseEpoch) {
        if (epoch != responseEpoch) reset(responseEpoch);
        retryCount = retryCount == Integer.MAX_VALUE ? Integer.MAX_VALUE : retryCount + 1;
        int previousTier = tier;
        tier = tierForRetryCount(retryCount);
        return new Decision(delayForTier(tier), retryCount, tier, previousTier != tier);
    }

    public void reset(long responseEpoch) {
        epoch = responseEpoch;
        retryCount = 0;
        tier = 0;
    }

    public long epoch() { return epoch; }
    public int retryCount() { return retryCount; }
    public int tier() { return tier; }

    public static int tierForRetryCount(int count) {
        if (count >= 15) return 3;
        if (count >= 8) return 2;
        if (count >= 4) return 1;
        return 0;
    }

    public static long delayForRetryCount(int count) {
        return delayForTier(tierForRetryCount(Math.max(1, count)));
    }

    private static long delayForTier(int tier) {
        return DELAYS_MS[Math.max(0, Math.min(tier, DELAYS_MS.length - 1))];
    }

    public static final class Decision {
        public final long delayMs;
        public final int retryCount;
        public final int tier;
        public final boolean tierChanged;

        private Decision(long delayMs, int retryCount, int tier, boolean tierChanged) {
            this.delayMs = delayMs;
            this.retryCount = retryCount;
            this.tier = tier;
            this.tierChanged = tierChanged;
        }
    }
}
