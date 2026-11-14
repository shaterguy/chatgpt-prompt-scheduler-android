package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public final class SessionResetGateTest {
    @Test public void resetCannotInterruptAClaimedRun() {
        SessionResetGate gate = new SessionResetGate();
        gate.startRun();
        assertFalse(gate.beginReset());
        gate.finishRun();
        assertTrue(gate.beginReset());
    }

    @Test public void resetWaitersResumeOnlyAfterBothSitesAreCleared() {
        SessionResetGate gate = new SessionResetGate();
        List<String> calls = new ArrayList<>();
        assertTrue(gate.beginReset());
        assertFalse(gate.beginReset());
        Runnable stoppedActivity = () -> calls.add("disposed");
        gate.whenReady(stoppedActivity);
        gate.whenReady(() -> calls.add("queue"));
        gate.cancelWait(stoppedActivity);
        assertTrue(calls.isEmpty());
        gate.finishReset();
        gate.whenReady(() -> calls.add("login"));
        assertEquals(List.of("queue", "login"), calls);
    }
}
