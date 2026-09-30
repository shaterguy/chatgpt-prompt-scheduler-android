package com.shaterguy.chatgptpromptscheduler;

/** Main-thread owner of the bounded wake lock covering the asynchronous profile-preparation step. */
final class ProfilePreparationLease implements AutoCloseable {
    static final long TIMEOUT_MS = 75_000L;
    private Runnable release;

    void hold(Runnable release) {
        close();
        this.release = release;
    }

    @Override public void close() {
        Runnable current = release;
        release = null;
        if (current != null) current.run();
    }
}
