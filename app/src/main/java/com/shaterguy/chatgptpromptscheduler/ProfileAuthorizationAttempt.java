package com.shaterguy.chatgptpromptscheduler;

/** Activity-local authorization identity. No account, result or token survives recreation. */
final class ProfileAuthorizationAttempt {
    private long generation;
    private boolean active;
    private boolean waitingForConsentResult;

    long begin() {
        if (active) return 0L;
        active = true;
        waitingForConsentResult = false;
        return ++generation;
    }

    boolean current(long attempt) { return active && attempt != 0L && generation == attempt; }

    boolean consentLaunched(long attempt) {
        if (!current(attempt)) return false;
        waitingForConsentResult = true;
        return true;
    }

    long takeConsentResult() {
        if (!active || !waitingForConsentResult) return 0L;
        waitingForConsentResult = false;
        return generation;
    }

    boolean finish(long attempt) {
        if (!current(attempt)) return false;
        active = false;
        waitingForConsentResult = false;
        return true;
    }

    void cancel() {
        ++generation;
        active = false;
        waitingForConsentResult = false;
    }
}
