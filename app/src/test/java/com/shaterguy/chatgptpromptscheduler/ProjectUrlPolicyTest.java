package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProjectUrlPolicyTest {
    private static final String PROJECT = "g-p-0123456789abcdef0123456789abcdef";

    @Test
    public void canonicalProjectRootAndConversationAreParsed() {
        ProjectUrlPolicy.ProjectRef root = ProjectUrlPolicy.parseProject("https://chatgpt.com/g/" + PROJECT + "/project");
        assertNotNull(root);
        assertEquals(PROJECT, root.projectId);
        assertEquals("", root.conversationId);
        assertEquals("https://chatgpt.com/g/" + PROJECT + "/project", root.canonicalUrl);

        ProjectUrlPolicy.ProjectRef conversation = ProjectUrlPolicy.parseProject("https://chatgpt.com/g/" + PROJECT + "/c/abc_123");
        assertNotNull(conversation);
        assertEquals("abc_123", conversation.conversationId);
        assertEquals(root.canonicalUrl, conversation.canonicalUrl);
    }

    @Test
    public void untrustedOrNonCanonicalInputsAreRejected() {
        assertNull(ProjectUrlPolicy.parseProject("http://chatgpt.com/g/" + PROJECT + "/project"));
        assertNull(ProjectUrlPolicy.parseProject("https://www.chatgpt.com/g/" + PROJECT + "/project"));
        assertNull(ProjectUrlPolicy.parseProject("https://chatgpt.com/g/" + PROJECT + "/project?x=1"));
        assertNull(ProjectUrlPolicy.parseProject("https://chatgpt.com@g.example/g/" + PROJECT + "/project"));
        assertNull(ProjectUrlPolicy.parseProject("https://chatgpt.com/g/not-a-project/project"));
        assertFalse(ProjectUrlPolicy.isTrustedChatgptPage("https://example.com/"));
        assertTrue(ProjectUrlPolicy.isTrustedChatgptPage("https://chatgpt.com/"));
    }

    @Test
    public void displayNamesAreNormalizedAndBounded() {
        assertEquals("Alpha Project", ProjectCatalog.normalizeDisplayName(" Alpha\n\tProject "));
        String longName = "x".repeat(200);
        assertEquals(120, ProjectCatalog.normalizeDisplayName(longName).length());
    }
}
