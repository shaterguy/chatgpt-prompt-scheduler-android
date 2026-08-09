package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NewJobIsolationTest {
    @Test
    public void newJobStartsWithOnlyTheProjectDefault() {
        OrchestrationActivity.NewJobFormDefaults defaults =
                OrchestrationActivity.newJobFormDefaults("https://chatgpt.com/g/project-7");

        assertEquals("https://chatgpt.com/g/project-7", defaults.projectUrl);
        assertEquals("inherit", defaults.workModel);
        assertEquals("inherit", defaults.reasoningEffort);
        assertEquals("", defaults.requirement);
    }

    @Test
    public void newJobDoesNotRenderTheExistingRuntimeState() {
        assertFalse(OrchestrationActivity.showsCurrentJobState(true));
        assertTrue(OrchestrationActivity.showsCurrentJobState(false));
    }
}
