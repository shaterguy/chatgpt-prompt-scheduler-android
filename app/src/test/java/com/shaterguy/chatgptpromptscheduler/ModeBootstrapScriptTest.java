package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ModeBootstrapScriptTest {
    @Test
    public void modeBootstrapUsesExactTargetFiniteRetryAndConfirmedLatch() {
        String script = ModeBootstrapScript.inline("chat", "run-mode-bootstrap");

        assertTrue(script.contains("data-tpp-toggle-value"));
        assertTrue(script.contains("MODE_CONFIRMED"));
        assertTrue(script.contains("stageRegressionBlocked"));
        assertTrue(script.contains("clickAttempts)<2"));
        assertTrue(script.contains("CHAT_MODE_READBACK_FAILED"));
        assertTrue(script.contains("CHAT_MODE_CONTROL_NOT_FOUND"));
        assertTrue(script.contains("if(mode&&!modeSelected&&!modePrior"));
        assertTrue(script.contains("mode&&!modeSelected&&modePrior"));
        assertTrue(script.contains("priorClick:!!modePrior"));
        assertTrue(script.contains("selected:modeSelected"));
        assertFalse(script.contains("modePrior)return result('RETRY'"));
    }

    @Test
    public void modeBootstrapRecognizesBroaderSelectedStateWithoutTrustingPopupDescendants() {
        String script = ModeBootstrapScript.inline("work", "run-selected-state");

        assertTrue(script.contains("aria-current"));
        assertTrue(script.contains("data-active"));
        assertTrue(script.contains("data-selected"));
        assertTrue(script.contains("input[type=\"radio\"]:checked"));
        assertTrue(script.contains("if(e.closest('[role=\"menu\"],[role=\"listbox\"]'))return false"));
        assertTrue(script.contains("const parents=[e.parentElement,owner?.parentElement]"));
    }
}
