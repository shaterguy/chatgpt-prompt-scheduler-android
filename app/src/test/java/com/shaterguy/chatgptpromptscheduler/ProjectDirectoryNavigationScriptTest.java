package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ProjectDirectoryNavigationScriptTest {
    private static final String PROJECT = "g-p-6a507cce80cc81919eeb9ba553b6ad9e";

    @Test public void projectEntryUsesProjectsDirectoryInsteadOfDirectProjectColdLoad() {
        Schedule project = new Schedule();
        project.targetType = "project";
        project.targetUrl = "https://chatgpt.com/g/" + PROJECT + "/project";
        assertEquals("https://chatgpt.com/projects", ProjectDirectoryNavigationScript.entryUrl(project));

        Schedule general = new Schedule();
        general.targetType = "general";
        general.targetUrl = "https://chatgpt.com/";
        assertEquals(general.targetUrl, ProjectDirectoryNavigationScript.entryUrl(general));
    }

    @Test public void directoryStepStopsOnceExpectedProjectSpaRouteIsReached() {
        Schedule project = new Schedule();
        project.targetType = "project";
        project.targetUrl = "https://chatgpt.com/g/" + PROJECT + "/project";

        assertTrue(ProjectDirectoryNavigationScript.needsDirectoryStep(project, "https://chatgpt.com/projects"));
        assertTrue(ProjectDirectoryNavigationScript.needsDirectoryStep(project,
                "https://chatgpt.com/g/g-p-7a507cce80cc81919eeb9ba553b6ad9e/project"));
        assertFalse(ProjectDirectoryNavigationScript.needsDirectoryStep(project,
                "https://chatgpt.com/g/" + PROJECT + "-vibe-coding/project"));
    }

    @Test public void generatedScriptTargetsCapturedSelectableRowsAndEscapesProjectName() {
        String script = ProjectDirectoryNavigationScript.build("💾 Vibe \"Coding\"", 1);
        assertTrue(script.contains("location.pathname!=='/projects'"));
        assertTrue(script.contains("[role=\"row\"][data-page-table-selectable-row=\"true\"]"));
        assertTrue(script.contains("button[aria-label]"));
        assertTrue(script.contains("pick.row.click()"));
        assertTrue(script.contains("const ordinal=1"));
        assertTrue(script.contains("💾 Vibe \\\"Coding\\\""));
    }
}
