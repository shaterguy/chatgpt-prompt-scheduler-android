package com.shaterguy.chatgptpromptscheduler;

/**
 * Small deterministic backoff policy for recoverable WebView states.
 *
 * The policy deliberately has no clock or Android dependency. Callers own the
 * deadline and schedule the returned delay on their existing handler. This
 * keeps rate-limit and target-restore waiting separate from action attempts.
 */
public final class RecoveryBackoff {
    public static final long[] DELAYS_MS = {1_000L, 2_000L, 4_000L, 8_000L, 15_000L};

    private int attempt;

    public Decision next() {
        attempt = attempt == Integer.MAX_VALUE ? Integer.MAX_VALUE : attempt + 1;
        return new Decision(attempt, delayForAttempt(attempt));
    }

    public void reset() {
        attempt = 0;
    }

    public int attempt() {
        return attempt;
    }

    public static long delayForAttempt(int attempt) {
        int index = Math.max(0, Math.min(Math.max(1, attempt) - 1, DELAYS_MS.length - 1));
        return DELAYS_MS[index];
    }

    public static final class Decision {
        public final int attempt;
        public final long delayMs;

        private Decision(int attempt, long delayMs) {
            this.attempt = attempt;
            this.delayMs = delayMs;
        }
    }
}
