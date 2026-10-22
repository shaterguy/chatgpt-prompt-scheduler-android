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
        assertTrue(compose.contains("DOM 입력 확정 대기"));
        assertTrue(compose.contains("domState.phase"));
        assertTrue(verify.contains("replace(/\\r\\n?/g,'\\n')"));

        assertFalse(compose.contains("\r"));
        assertFalse(compose.contains("\n"));
        assertFalse(verify.contains("\r"));
        assertFalse(verify.contains("\n"));
    }

    @Test
    public void composeWaitsForCompletePageBeforeProfileAndInput() {
        Schedule schedule = new Schedule();
        schedule.targetType = "general";
        schedule.targetUrl = "https://chatgpt.com/";

        String compose = AutomationScript.build(schedule, "page load guard", "loading-test", 0);
        String gate = "if(document.readyState!=='complete')return result('RETRY','페이지 로딩 완료 대기',{...routeDiagnostics,readyState:document.readyState});";

        assertTrue(compose.contains(gate));
        assertTrue(compose.indexOf("if(authRequired)") < compose.indexOf(gate));
        assertTrue(compose.indexOf(gate) < compose.indexOf("modeDiagnostics"));
        assertTrue(compose.indexOf(gate) < compose.indexOf("domState.phase"));
        assertTrue(compose.indexOf(gate) < compose.indexOf("send.click()"));
        assertFalse(AutomationScript.verify(schedule, "page load guard").contains(gate));
    }
}
