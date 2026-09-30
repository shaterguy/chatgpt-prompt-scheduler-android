package com.shaterguy.chatgptpromptscheduler;

/** Publication/cancellation decision, independent of Android and disk I/O. */
final class ProfileSyncOperation {
    private boolean stopped;
    private boolean publishing;
    private Runnable afterPublication;

    synchronized boolean active() { return !stopped; }

    synchronized boolean beginPublication() {
        if (stopped || publishing) return false;
        publishing = true;
        return true;
    }

    void endPublication() {
        Runnable completion;
        synchronized (this) {
            publishing = false;
            completion = afterPublication;
            afterPublication = null;
        }
        if (completion != null) completion.run();
    }

    /** Never waits for a writer. A writer already admitted must finish before completion is delivered. */
    boolean stop(Runnable completion) {
        synchronized (this) {
            if (stopped) return false;
            stopped = true;
            if (publishing) {
                afterPublication = completion;
                return true;
            }
        }
        completion.run();
        return true;
    }
}
