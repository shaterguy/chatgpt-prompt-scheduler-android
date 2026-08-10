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
        assertFalse(script.contains("if(mode&&!modeSelected&&!modePrior)"));
    }

    @Test
    public void workRestoresVersion015OneShotPriorClickLatch() {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = "https://chatgpt.com/g/project-1/project";
        schedule.experience = "work";

        String script = AutomationScript.preferenceScript(schedule, "'run-project-work'");

        assertTrue(script.contains("const mode=modeCandidate(['work','작업'])"));
        assertTrue(script.contains("let modePrior=''"));
        assertTrue(script.contains("priorClick:!!modePrior"));
        assertTrue(script.contains("if(mode&&!modeSelected&&!modePrior)"));
        assertTrue(script.contains("mode.click()"));
        assertFalse(script.contains("modeClicks>=3"));
        assertFalse(script.contains("Work 모드 실제 적용 상태 대기"));
        assertFalse(script.contains("Work 모드 실제 적용을 확인하지 못했습니다."));
        assertFalse(script.contains("assumedActive:!modeSelected&&!workSelected"));
    }
}
