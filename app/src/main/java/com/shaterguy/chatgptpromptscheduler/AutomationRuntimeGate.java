package com.shaterguy.chatgptpromptscheduler;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

/** In-process priority gate. Scheduled runs always preempt the optional orchestration relay. */
public final class AutomationRuntimeGate {
    public interface Listener {
        void onSchedulePriorityChanged(boolean active);
    }

    private static final AtomicBoolean SCHEDULE_ACTIVE = new AtomicBoolean(false);
    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();

    private AutomationRuntimeGate() {}

    public static boolean isScheduleActive() {
        return SCHEDULE_ACTIVE.get();
    }

    public static void setScheduleActive(boolean active) {
        boolean changed = SCHEDULE_ACTIVE.getAndSet(active) != active;
        if (!changed) return;
        for (Listener listener : LISTENERS) listener.onSchedulePriorityChanged(active);
    }

    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }
}
