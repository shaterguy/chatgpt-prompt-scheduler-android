package com.shaterguy.chatgptpromptscheduler;

import java.util.ArrayList;
import java.util.List;

/** Serializes WebView data deletion with reservation execution on the main thread. */
final class SessionResetGate {
    static final SessionResetGate INSTANCE = new SessionResetGate();

    private final List<Runnable> waiters = new ArrayList<>();
    private boolean running;
    private boolean resetting;

    boolean beginReset() {
        if (running || resetting) return false;
        resetting = true;
        return true;
    }

    void finishReset() {
        resetting = false;
        List<Runnable> ready = new ArrayList<>(waiters);
        waiters.clear();
        for (Runnable callback : ready) callback.run();
    }

    boolean isResetting() {
        return resetting;
    }

    void whenReady(Runnable callback) {
        if (resetting) {
            if (!waiters.contains(callback)) waiters.add(callback);
        } else {
            callback.run();
        }
    }

    void cancelWait(Runnable callback) {
        waiters.remove(callback);
    }

    void startRun() {
        running = true;
    }

    void finishRun() {
        running = false;
    }
}
