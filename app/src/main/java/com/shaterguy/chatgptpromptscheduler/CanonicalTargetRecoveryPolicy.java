package com.shaterguy.chatgptpromptscheduler;

/**
 * Side-effect-free timing and budget policy for canonical target re-entry.
 *
 * Loading, rate-limit, and transient-route observations all use this same
 * bounded re-entry budget. Callers still own the WebView side effect and any
 * reason-specific wait that must happen before a fresh observation.
 */
public final class CanonicalTargetRecoveryPolicy {
    public static final long INITIAL_GRACE_MS = 15_000L;
    public static final int MAX_REENTRIES = 3;

    public enum Decision {
        WAIT,
        REENTER,
        READY,
        TARGET_CHANGED,
        EXHAUSTED
    }

    private CanonicalTargetRecoveryPolicy() {}

    public static Decision decide(long now, long startedAt, boolean targetReady,
                                  boolean transientTarget, boolean uiReady,
                                  boolean pageFinished, int reentryAttempts) {
        if (!targetReady && !transientTarget) return Decision.TARGET_CHANGED;
        if (targetReady && uiReady && pageFinished) return Decision.READY;
        if (startedAt <= 0L || now - startedAt < INITIAL_GRACE_MS) return Decision.WAIT;
        return reentryAttempts < MAX_REENTRIES ? Decision.REENTER : Decision.EXHAUSTED;
    }

    public static long delayUntilGrace(long now, long startedAt) {
        if (startedAt <= 0L) return INITIAL_GRACE_MS;
        return Math.max(0L, INITIAL_GRACE_MS - Math.max(0L, now - startedAt));
    }

    public static boolean canReenter(int reentryAttempts) {
        return reentryAttempts >= 0 && reentryAttempts < MAX_REENTRIES;
    }
}
