package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class RunLogConversationLinkTest {
    private static final String CONVERSATION_ID = "6ad821ae-71e8-83ee-9fe1-40456ed0196a";
    private static final String DIRECT_URL = "https://chatgpt.com/c/" + CONVERSATION_ID;

    @Test public void existingProjectLogsOpenTheSameConversationAtItsDirectUrl() {
        assertEquals(DIRECT_URL, TargetParser.canonicalConversationUrl(
                "https://chatgpt.com/g/g-p-6a507cce80cc81919eeb9ba553b6ad9e-vibe-coding/c/"
                        + CONVERSATION_ID + "?model=keep#message"));
    }

    @Test public void generalConversationLinksUseTheCanonicalHostAndPath() {
        assertEquals(DIRECT_URL, TargetParser.canonicalConversationUrl(DIRECT_URL));
        assertEquals(DIRECT_URL, TargetParser.canonicalConversationUrl(
                "https://www.chatgpt.com/c/" + CONVERSATION_ID + "/"));
    }

    @Test public void missingAndNonConversationRoutesDoNotProduceLinks() {
        assertNull(TargetParser.canonicalConversationUrl(null));
        assertNull(TargetParser.canonicalConversationUrl(""));
        assertNull(TargetParser.canonicalConversationUrl("https://chatgpt.com/g/g-p-test/project"));
        assertNull(TargetParser.canonicalConversationUrl("https://chatgpt.com/share/" + CONVERSATION_ID));
        assertNull(TargetParser.canonicalConversationUrl(DIRECT_URL + "/extra"));
        assertNull(TargetParser.canonicalConversationUrl("https://chatgpt.com/c/local-chatgpt%3A" + CONVERSATION_ID));
        assertNull(TargetParser.canonicalConversationUrl("https://example.com/c/" + CONVERSATION_ID));
        assertNull(TargetParser.canonicalConversationUrl("https://user@chatgpt.com/c/" + CONVERSATION_ID));
    }
}
