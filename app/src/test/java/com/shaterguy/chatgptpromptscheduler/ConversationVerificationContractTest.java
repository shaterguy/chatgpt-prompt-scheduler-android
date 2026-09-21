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
                "https://chatgpt.com/g/" + PROJECT + "-vibe-coding/c/WEB:12345678-1234-1234-1234-123456789abc"));
        assertFalse(TargetParser.matchesVerifiedConversation("project", projectTarget,
                "https://chatgpt.com/g/" + OTHER_PROJECT + "/c/conversation_1"));

        String generalTarget = "https://chatgpt.com/";
        assertFalse(TargetParser.matchesVerifiedConversation("general", generalTarget, generalTarget));
        assertTrue(TargetParser.matchesVerifiedConversation("general", generalTarget,
                "https://chatgpt.com/c/general_conversation"));
        assertFalse(TargetParser.matchesVerifiedConversation("general", generalTarget,
                "https://chatgpt.com/c/WEB:12345678-1234-1234-1234-123456789abc"));
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

    @Test public void verificationScriptWaitsForConversationAddress() {
        Schedule project = new Schedule();
        project.targetType = "project";
        project.targetUrl = "https://chatgpt.com/g/" + PROJECT + "/project";
        String projectScript = ConversationVerificationScript.build(project, "prompt");
        assertTrue(projectScript.contains("return result('RETRY','실제 대화 주소 생성 대기'"));
        assertTrue(projectScript.contains("if(!persistentConversation)return result('RETRY'"));
        assertTrue(projectScript.contains("!/^WEB:/i.test(actualConversation)"));

        Schedule general = new Schedule();
        general.targetType = "general";
        general.targetUrl = "https://chatgpt.com/";
        String generalScript = ConversationVerificationScript.build(general, "prompt");
        assertTrue(generalScript.contains("if(!actualConversation){if(homePath)return result('RETRY'"));
        assertTrue(generalScript.contains("actualProject)return result('TARGET_CONTEXT_MISMATCH'"));
    }
}
