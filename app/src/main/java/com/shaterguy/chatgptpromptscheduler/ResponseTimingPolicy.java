package com.shaterguy.chatgptpromptscheduler;

/** Pure monotonic-time policy for long assistant responses; it creates no timers or wakeups. */
public final class ResponseTimingPolicy {
    public static final long SOFT_YIELD_MS = 90L * 60L * 1000L;
    public static final long HARD_FALLBACK_MS = 98L * 60L * 1000L;

    private ResponseTimingPolicy() {}

    public static Decision evaluate(long elapsedMs, boolean streaming, boolean softAlreadyDue,
                                    boolean stopGenerationPending) {
        long safeElapsed = Math.max(0L, elapsedMs);
        boolean softDue = !softAlreadyDue && safeElapsed >= SOFT_YIELD_MS;
        boolean hardDue = !stopGenerationPending && streaming && safeElapsed >= HARD_FALLBACK_MS;
        return new Decision(softDue, hardDue, safeElapsed);
    }

    public static final class Decision {
        public final boolean softYieldDue;
        public final boolean hardFallbackDue;
        public final long elapsedMs;

        private Decision(boolean softYieldDue, boolean hardFallbackDue, long elapsedMs) {
            this.softYieldDue = softYieldDue;
            this.hardFallbackDue = hardFallbackDue;
            this.elapsedMs = elapsedMs;
        }
    }
}
