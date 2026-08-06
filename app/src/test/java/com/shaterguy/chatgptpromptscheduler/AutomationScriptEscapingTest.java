package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutomationScriptEscapingTest {
    @Test
    public void generatedScriptsPreserveJavascriptEscapes() {
        Schedule schedule = new Schedule();
        schedule.targetType = "existing";
        schedule.targetUrl = "https://chatgpt.com/g/proj/c/abc";

        String compose = AutomationScript.build(schedule, "line1\nline2", "run-escape", 0);
        String verify = AutomationScript.verify(schedule, "line1\nline2");

        assertTrue(compose.contains("replace(/\\r\\n?/g,'\\n')"));
        assertTrue(compose.contains("replace(/ *\\n+ */g,'\\n')"));
        assertTrue(compose.contains("line1\\nline2"));
        assertTrue(verify.contains("replace(/\\r\\n?/g,'\\n')"));

        assertFalse(compose.contains("\r"));
        assertFalse(compose.contains("\n"));
        assertFalse(verify.contains("\r"));
        assertFalse(verify.contains("\n"));
    }
}
