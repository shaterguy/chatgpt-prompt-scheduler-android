package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProjectUrlPolicyTest {
    @Test
    public void visitedProjectIsCanonicalizedFromProjectConversation() {
        ProjectUrlPolicy.ProjectRef ref = ProjectUrlPolicy.parseProject(
                "https://chatgpt.com/g/g-p-demo_123/c/conversation_456");
        assertNotNull(ref);
        assertEquals("g-p-demo_123", ref.projectId);
        assertEquals("https://chatgpt.com/g/g-p-demo_123/project", ref.canonicalUrl);
    }

    @Test
    public void capturedProjectRequiresExactHttpsChatgptHostAndCleanPath() {
        assertNull(ProjectUrlPolicy.parseProject("http://chatgpt.com/g/g-p-demo/project"));
        assertNull(ProjectUrlPolicy.parseProject("https://www.chatgpt.com/g/g-p-demo/project"));
        assertNull(ProjectUrlPolicy.parseProject("https://user@chatgpt.com/g/g-p-demo/project"));
        assertNull(ProjectUrlPolicy.parseProject("https://chatgpt.com:443/g/g-p-demo/project"));
        assertNull(ProjectUrlPolicy.parseProject("https://chatgpt.com/g/g-p-demo/project?x=1"));
        assertNull(ProjectUrlPolicy.parseProject("https://chatgpt.com/g/g-p-demo/project#fragment"));
        assertNull(ProjectUrlPolicy.parseProject("https://chatgpt.com/g/not-a-project/project"));
    }

    @Test
    public void loginObservationMayInspectOnlyTrustedChatgptPages() {
        assertTrue(ProjectUrlPolicy.isTrustedChatgptPage("https://chatgpt.com/"));
        assertTrue(ProjectUrlPolicy.isTrustedChatgptPage("https://chatgpt.com/g/g-p-demo/project?view=1"));
        assertFalse(ProjectUrlPolicy.isTrustedChatgptPage("https://www.chatgpt.com/"));
        assertFalse(ProjectUrlPolicy.isTrustedChatgptPage("https://example.com/"));
    }

    @Test
    public void projectSchedulesNoLongerUseManualUrlInput() {
        assertTrue(ScheduleEditorActivity.showsProjectSelection("project"));
        assertFalse(ScheduleEditorActivity.usesManualTargetUrl("project"));
        assertTrue(ScheduleEditorActivity.usesManualTargetUrl("existing"));
    }
}
