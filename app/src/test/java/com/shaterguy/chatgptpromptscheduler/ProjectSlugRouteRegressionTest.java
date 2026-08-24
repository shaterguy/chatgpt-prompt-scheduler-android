package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProjectSlugRouteRegressionTest {
    private static final String PROJECT = "g-p-6a507cce80cc81919eeb9ba553b6ad9e";
    private static final String OTHER_PROJECT = "g-p-7a507cce80cc81919eeb9ba553b6ad9e";

    @Test
    public void targetParserCanonicalizesSluggedProjectSegment() {
        assertEquals(PROJECT, TargetParser.projectId(
                "https://chatgpt.com/g/" + PROJECT + "-health-care/c/conversation_1"));
        assertEquals(PROJECT, TargetParser.projectId(
                "https://chatgpt.com/g/" + PROJECT + "/project"));
    }

    @Test
    public void existingConversationMatchingKeepsProjectAndConversationStrict() {
        String expected = "https://chatgpt.com/g/" + PROJECT + "/c/conversation_1";
        String sameProjectSlugged = "https://chatgpt.com/g/" + PROJECT + "-health-care/c/conversation_1";
        String wrongConversation = "https://chatgpt.com/g/" + PROJECT + "-health-care/c/conversation_2";
        String wrongProject = "https://chatgpt.com/g/" + OTHER_PROJECT + "-health-care/c/conversation_1";

        assertTrue(TargetParser.matchesTarget("existing", expected, sameProjectSlugged));
        assertFalse(TargetParser.matchesTarget("existing", expected, wrongConversation));
        assertFalse(TargetParser.matchesTarget("existing", expected, wrongProject));
    }

    @Test
    public void projectPolicyTreatsSluggedConversationAsSameProjectOnlyForSameToken() {
        String canonical = "https://chatgpt.com/g/" + PROJECT + "/project";
        String sameProjectSlugged = "https://chatgpt.com/g/" + PROJECT + "-health-care/c/conversation_1";
        String wrongProject = "https://chatgpt.com/g/" + OTHER_PROJECT + "-health-care/c/conversation_1";

        assertTrue(ProjectUrlPolicy.sameProject(canonical, sameProjectSlugged));
        assertFalse(ProjectUrlPolicy.sameProject(canonical, wrongProject));
    }

    @Test
    public void automationGuardCanonicalizesActualProjectBeforeComparingContext() {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = "https://chatgpt.com/g/" + PROJECT + "/project";

        String script = AutomationScript.verify(schedule, "[2026.08.24 | 07:49:24]\n테스트");

        assertTrue(script.contains("canonicalProject=value=>"));
        assertTrue(script.contains("rawActualProject=segmentAfter('g')"));
        assertTrue(script.contains("actualProject=canonicalProject(rawActualProject)"));
        assertTrue(script.contains("expectedProject=\"" + PROJECT + "\""));
        assertTrue(script.contains("actualProject===expectedProject"));
        assertTrue(script.contains("rawActualProject,actualProject"));
    }
}
