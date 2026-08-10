package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProjectChatModeRegressionTest {
    @Test
    public void projectChatTreatsAbsenceOfActiveWorkAsChatEvenWithoutSelectedMarker() {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = "https://chatgpt.com/g/project-1/project";
        schedule.experience = "chat";

        String script = AutomationScript.preferenceScript(schedule, "'run-project-chat'");

        assertTrue(script.contains("const workMode=modeCandidate(['work','작업'])"));
        assertTrue(script.contains("workSelected=modeIsSelected(workMode)"));
        assertTrue(script.contains("assumedActive:!modeSelected&&!workSelected"));
        assertTrue(script.contains("if(workSelected){"));
        assertFalse(script.contains("if(mode&&!modeSelected)"));
    }

    @Test
    public void workStillUsesBoundedSelectedStateReadback() {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = "https://chatgpt.com/g/project-1/project";
        schedule.experience = "work";

        String script = AutomationScript.preferenceScript(schedule, "'run-project-work'");

        assertTrue(script.contains("if(mode&&!modeSelected)"));
        assertTrue(script.contains("modeClicks>=3"));
        assertTrue(script.contains("Work 모드 실제 적용을 확인하지 못했습니다."));
        assertFalse(script.contains("assumedActive:!modeSelected&&!workSelected"));
    }
}
