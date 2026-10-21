package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ConversationVerificationContractTest {
    private static final String PROJECT = "g-p-6a507cce80cc81919eeb9ba553b6ad9e";
    private static final String OTHER_PROJECT = "g-p-7a507cce80cc81919eeb9ba553b6ad9e";

    @Test public void newConversationTargetsRequireConversationAddressAfterSubmit() {
        String projectTarget = "https://chatgpt.com/g/" + PROJECT + "/project";
        assertFalse(TargetParser.matchesVerifiedConversation("project", projectTarget, projectTarget));
        assertTrue(TargetParser.matchesVerifiedConversation("project", projectTarget,
                "https://chatgpt.com/g/" + PROJECT + "-vibe-coding/c/conversation_1"));
        assertFalse(TargetParser.matchesVerifiedConversation("project", projectTarget,
                "https://chatgpt.com/g/" + OTHER_PROJECT + "/c/conversation_1"));

        String generalTarget = "https://chatgpt.com/";
        assertFalse(TargetParser.matchesVerifiedConversation("general", generalTarget, generalTarget));
        assertTrue(TargetParser.matchesVerifiedConversation("general", generalTarget,
                "https://chatgpt.com/c/general_conversation"));
        assertFalse(TargetParser.matchesVerifiedConversation("general", generalTarget,
                "https://chatgpt.com/g/" + PROJECT + "/c/general_conversation"));
    }

    @Test public void existingConversationVerificationRemainsStrict() {
        String expected = "https://chatgpt.com/g/" + PROJECT + "/c/conversation_1";
        assertTrue(TargetParser.matchesVerifiedConversation("existing", expected,
                "https://chatgpt.com/g/" + PROJECT + "-slug/c/conversation_1"));
        assertFalse(TargetParser.matchesVerifiedConversation("existing", expected,
                "https://chatgpt.com/g/" + PROJECT + "/c/conversation_2"));
        assertFalse(TargetParser.matchesVerifiedConversation("existing", expected,
                "https://chatgpt.com/c/conversation_1"));
    }

    @Test public void missingUserMessageSelectorStillConfirmsStableServerConversation() {
        Schedule general = new Schedule();
        general.targetType = "general";
        general.targetUrl = "https://chatgpt.com/";
        String uuid = "6ad821ae-71e8-83ee-9fe1-40456ed0196a";
        String actual = "https://chatgpt.com/c/" + uuid;
        SubmissionRouteConfirmation confirm = new SubmissionRouteConfirmation();

        // Provisional local routes must never be accepted as a server-created conversation.
        assertFalse(confirm.observe(general,
                "https://chatgpt.com/c/local-chatgpt%3A5742c868-e76a-4a42-8087-d936b319117d"));
        assertFalse(confirm.observe(general, actual));   // First server URL observation.
        assertTrue(confirm.observe(general, actual));    // Consecutive stable observation.
        confirm.reset();
        assertFalse(confirm.observe(general, actual));   // A new run cannot reuse prior proof.
        assertFalse(confirm.observe(general, "https://chatgpt.com/c/not-a-server-uuid"));
        assertFalse(confirm.observe(general, "https://chatgpt.com/g/" + PROJECT + "/c/" + uuid));
        assertFalse(confirm.observe(general, "https://example.com/c/" + uuid));
    }

    @Test public void fallbackIsScopedToTheExpectedProjectAndNeverExistingChats() {
        String uuid = "6ad821ae-71e8-83ee-9fe1-40456ed0196a";
        Schedule project = new Schedule();
        project.targetType = "project";
        project.targetUrl = "https://chatgpt.com/g/" + PROJECT + "/project";
        SubmissionRouteConfirmation confirm = new SubmissionRouteConfirmation();
        assertFalse(confirm.observe(project, "https://chatgpt.com/g/" + OTHER_PROJECT + "/c/" + uuid));
        String created = "https://chatgpt.com/g/" + PROJECT + "-vibe-coding/c/" + uuid;
        assertFalse(confirm.observe(project, created));
        assertTrue(confirm.observe(project, created));

        Schedule existing = new Schedule();
        existing.targetType = "existing";
        existing.targetUrl = "https://chatgpt.com/c/" + uuid;
        confirm.reset();
        assertFalse(confirm.observe(existing, existing.targetUrl));
        assertFalse(confirm.observe(existing, existing.targetUrl));
    }

    @Test public void verificationScriptWaitsForConversationAddress() {
        Schedule project = new Schedule();
        project.targetType = "project";
        project.targetUrl = "https://chatgpt.com/g/" + PROJECT + "/project";
        String projectScript = ConversationVerificationScript.build(project, "prompt");
        assertTrue(projectScript.contains("return result('RETRY','실제 대화 주소 생성 대기'"));
        assertTrue(projectScript.contains("if(!actualConversation)return result('RETRY'"));

        Schedule general = new Schedule();
        general.targetType = "general";
        general.targetUrl = "https://chatgpt.com/";
        String generalScript = ConversationVerificationScript.build(general, "prompt");
        assertTrue(generalScript.contains("if(!actualConversation){if(homePath)return result('RETRY'"));
        assertTrue(generalScript.contains("actualProject)return result('TARGET_CONTEXT_MISMATCH'"));
    }
}
