package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ProjectDirectoryNavigationScriptTest {
    private static final String PROJECT = "g-p-6a507cce80cc81919eeb9ba553b6ad9e";

    @Test public void projectEntryUsesSavedCanonicalProjectUrl() {
        Schedule project = new Schedule();
        project.targetType = "project";
        project.targetUrl = "https://chatgpt.com/g/" + PROJECT + "/project";
        assertEquals(project.targetUrl, ProjectDirectoryNavigationScript.entryUrl(project));

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
        assertFalse(ProjectDirectoryNavigationScript.needsDirectoryStep(project,
                "https://chatgpt.com/g/g-p-7a507cce80cc81919eeb9ba553b6ad9e/project"));
        assertFalse(ProjectDirectoryNavigationScript.needsDirectoryStep(project,
                "https://chatgpt.com/g/" + PROJECT + "-vibe-coding/project"));
    }

    @Test public void greetingIsNeverUsedAsProjectName() {
        assertEquals("", ProjectCatalog.validDisplayName("진수님, 반갑습니다."));
        assertEquals("", ProjectCatalog.validDisplayName("오늘은 무엇을 해볼까요?"));
        assertEquals("💾 Vibe Coding", ProjectCatalog.validDisplayName("💾 Vibe Coding"));
    }

    @Test public void generatedScriptTargetsCapturedSelectableRows() {
        String script = ProjectDirectoryNavigationScript.build("https://chatgpt.com/g/" + PROJECT + "/project", 1);
        assertTrue(script.contains("location.pathname!=='/projects'"));
        assertTrue(script.contains("a[href]"));
        assertTrue(script.contains("normalize(p[2]"));
        assertTrue(script.contains("chosen.click()"));
        assertTrue(script.contains("const ordinal=1"));
        assertTrue(script.contains(PROJECT));
    }
}
