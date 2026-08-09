package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OrchestrationScriptSyntaxTest {
    @Test
    public void commonHelpersTerminateDeclarationsBeforeNextConst() {
        String prepare = OrchestrationScript.prepare("[AUTOMATION_START JOB]");
        String observe = OrchestrationScript.observe("[AUTOMATION_START JOB]");
        String reconcile = OrchestrationScript.reconcileScan("JOB");

        assertTrue(prepare.contains("visibility!=='hidden';};const authLabel="));
        assertTrue(observe.contains("visibility!=='hidden';};const authLabel="));
        assertTrue(reconcile.contains("visibility!=='hidden';};const authLabel="));
        assertFalse(prepare.contains("visibility!=='hidden';}const authLabel="));
        assertFalse(observe.contains("visibility!=='hidden';}const authLabel="));
        assertFalse(reconcile.contains("visibility!=='hidden';}const authLabel="));
    }
}
