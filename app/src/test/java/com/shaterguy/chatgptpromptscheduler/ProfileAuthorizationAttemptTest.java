package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ProfileAuthorizationAttemptTest {
    @Test public void delayedSuccessCannotReenableAfterDisable() {
        ProfileAuthorizationAttempt gate = new ProfileAuthorizationAttempt();
        long attempt = gate.begin();
        gate.cancel(); // Settings disables before the asynchronous Google result.
        boolean syncEnabled = false;
        if (gate.finish(attempt)) syncEnabled = true;
        assertFalse(syncEnabled);
    }
    @Test public void delayedResolutionCannotLaunchAfterDisable() {
        ProfileAuthorizationAttempt gate = new ProfileAuthorizationAttempt();
        long attempt = gate.begin(); gate.cancel();
        assertFalse(gate.consentLaunched(attempt));
        assertEquals(0L, gate.takeConsentResult());
    }
    @Test public void staleFailureCannotClearNewAttempt() {
        ProfileAuthorizationAttempt gate = new ProfileAuthorizationAttempt();
        long old = gate.begin(); gate.cancel(); long current = gate.begin();
        assertFalse(gate.finish(old));
        assertTrue(gate.current(current));
        assertTrue(gate.finish(current));
    }
    @Test public void destroyAndRecreateRejectsUnboundResult() {
        ProfileAuthorizationAttempt old = new ProfileAuthorizationAttempt();
        long attempt = old.begin(); assertTrue(old.consentLaunched(attempt)); old.cancel();
        ProfileAuthorizationAttempt recreated = new ProfileAuthorizationAttempt();
        assertEquals(0L, recreated.takeConsentResult());
        assertFalse(old.finish(attempt));
        assertTrue(recreated.begin() > 0L);
    }
    @Test public void currentConsentResultIsConsumedExactlyOnce() {
        ProfileAuthorizationAttempt gate = new ProfileAuthorizationAttempt();
        long attempt = gate.begin(); assertTrue(gate.consentLaunched(attempt));
        assertEquals(attempt, gate.takeConsentResult());
        assertEquals(0L, gate.takeConsentResult());
        assertTrue(gate.finish(attempt)); assertFalse(gate.finish(attempt));
    }
    @Test public void repeatedTapDoesNotReplaceCurrentAttempt() {
        ProfileAuthorizationAttempt gate = new ProfileAuthorizationAttempt();
        long attempt = gate.begin(); assertEquals(0L, gate.begin()); assertTrue(gate.current(attempt));
        assertEquals(0L, gate.takeConsentResult());
    }
}
