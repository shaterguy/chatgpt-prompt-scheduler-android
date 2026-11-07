package com.shaterguy.chatgptpromptscheduler;

/** Run-scoped composer waiting, with the caller's original deadline as the only time budget. */
final class ComposerRecoveryPolicy {
    static final long STALL_MS = 15_000L;
    static final long MIN_REMAINING_MS = 15_000L;
    static final int MAX_RECOVERIES = 2;
    static final int MAX_INPUT_RETRIES = 48;

    enum Decision { WAIT, REENTER, EXHAUSTED, TIMED_OUT }

    private long missingSince = -1L;
    private int missingPolls;
    private int retryAttempts;
    private int recoveryAttempts;
    private boolean inputStarted;

    void reset() {
        missingSince = -1L;
        missingPolls = 0;
        retryAttempts = 0;
        recoveryAttempts = 0;
        inputStarted = false;
    }

    void observeInputStarted(boolean value) {
        inputStarted |= value;
    }

    Decision onRetry(boolean composerMissing, boolean submitted, long now, long deadline) {
        if (now >= deadline) return Decision.TIMED_OUT;
        if (!composerMissing) {
            missingSince = -1L;
            return ++retryAttempts > MAX_INPUT_RETRIES ? Decision.EXHAUSTED : Decision.WAIT;
        }
        missingPolls++;
        if (missingSince < 0L) missingSince = now;
        if (!submitted && !inputStarted && recoveryAttempts < MAX_RECOVERIES
                && now - missingSince >= STALL_MS && deadline - now >= MIN_REMAINING_MS) {
            recoveryAttempts++;
            missingSince = -1L;
            return Decision.REENTER;
        }
        return Decision.WAIT;
    }

    int retryAttempts() { return retryAttempts; }
    int missingPolls() { return missingPolls; }
    int recoveryAttempts() { return recoveryAttempts; }
    boolean inputStarted() { return inputStarted; }
}
