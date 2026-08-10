package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.*;
import org.junit.Test;

public class RelayTargetRecoveryTest {
 private static final String P="https://chatgpt.com/g/project-1"; private static final String C=P+"/c/conversation-1";
 @Test public void sameConversationSurvivesNormalization(){ assertEquals(TargetParser.ConversationTargetState.MATCH,TargetParser.classifyConversationTarget(C,"https://chatgpt.com/c/conversation-1")); assertTrue(TargetParser.matchesTarget("existing",C,"https://chatgpt.com/c/conversation-1")); }
 @Test public void rootHomeBlankTransient(){ assertEquals(TargetParser.ConversationTargetState.TRANSIENT,TargetParser.classifyConversationTarget(C,P)); assertEquals(TargetParser.ConversationTargetState.TRANSIENT,TargetParser.classifyConversationTarget(C,"https://chatgpt.com/")); assertEquals(TargetParser.ConversationTargetState.TRANSIENT,TargetParser.classifyConversationTarget(C,"about:blank")); }
 @Test public void differentConversationDifferent(){ assertEquals(TargetParser.ConversationTargetState.DIFFERENT,TargetParser.classifyConversationTarget(C,P+"/c/conversation-2")); }
}
