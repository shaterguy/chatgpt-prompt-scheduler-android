package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class ChatReasoningScheduleTest {
    @Test
    public void legacyScheduleWithoutChatReasoningDefaultsToKeep() throws Exception {
        JSONObject legacy = new JSONObject();
        legacy.put("id", "legacy");
        legacy.put("targetType", "general");
        legacy.put("targetUrl", "https://chatgpt.com/");
        legacy.put("experience", "chat");
        legacy.put("prompt", "legacy prompt");

        Schedule schedule = Schedule.fromJson(legacy);

        assertEquals("keep", schedule.chatReasoning);
        assertEquals("legacy prompt", schedule.prompt);
        assertEquals("chat", schedule.experience);
    }

    @Test
    public void chatReasoningRoundTripPreservesSelection() throws Exception {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = "https://chatgpt.com/g/project-id";
        schedule.experience = "chat";
        schedule.chatReasoning = "xhigh";

        Schedule restored = Schedule.fromJson(schedule.toJson());

        assertEquals("xhigh", restored.chatReasoning);
        assertEquals("inherit", restored.workModel);
        assertEquals("inherit", restored.reasoningEffort);
    }

    @Test
    public void chatReasoningNormalizationIsModeBound() {
        assertEquals("instant", Schedule.normalizedChatReasoning("chat", "instant"));
        assertEquals("medium", Schedule.normalizedChatReasoning("chat", "medium"));
        assertEquals("high", Schedule.normalizedChatReasoning("chat", "high"));
        assertEquals("xhigh", Schedule.normalizedChatReasoning("chat", "xhigh"));
        assertEquals("pro", Schedule.normalizedChatReasoning("chat", "pro"));
        assertEquals("keep", Schedule.normalizedChatReasoning("chat", "unknown"));
        assertEquals("keep", Schedule.normalizedChatReasoning("work", "pro"));
        assertEquals("keep", Schedule.normalizedChatReasoning("inherit", "high"));
    }

    @Test
    public void editorShowsChatReasoningOnlyForSelectableChatMode() {
        assertTrue(ScheduleEditorActivity.showsChatReasoning("general", "chat"));
        assertTrue(ScheduleEditorActivity.showsChatReasoning("project", "chat"));
        assertFalse(ScheduleEditorActivity.showsChatReasoning("general", "work"));
        assertFalse(ScheduleEditorActivity.showsChatReasoning("existing", "chat"));
        assertTrue(ScheduleEditorActivity.showsReasoningEffort("general", "work"));
    }

    @Test
    public void chatAutomationUsesSelfRunStyleHierarchicalSelector() {
        Schedule schedule = new Schedule();
        schedule.targetType = "general";
        schedule.experience = "chat";
        schedule.chatReasoning = "xhigh";

        String script = AutomationScript.build(schedule, "[2026.08.23 | 13:00:00]\nhello", "run-chat", 0);

        assertTrue(script.contains("const __cpsWanted=\"xhigh\""));
        assertTrue(script.contains("chatgpt-prompt-scheduler:chat-reasoning:"));
        assertTrue(script.contains("data-animated-slider-trigger"));
        assertTrue(script.contains("advanced-menu"));
        assertTrue(script.contains("CHAT_REASONING_READBACK_MISMATCH"));
        assertFalse(script.contains("const desiredEffort=\"xhigh\""));
    }

    @Test
    public void keepSkipsChatReasoningAndWorkPathRemainsSeparate() {
        Schedule chat = new Schedule();
        chat.targetType = "general";
        chat.experience = "chat";
        chat.chatReasoning = "keep";
        String chatScript = AutomationScript.build(chat, "hello", "run-keep", 0);
        assertTrue(chatScript.contains("reasoningDiagnostics={requested:'keep',ready:true"));
        assertFalse(chatScript.contains("chatgpt-prompt-scheduler:chat-reasoning:"));

        Schedule work = new Schedule();
        work.targetType = "general";
        work.experience = "work";
        work.workModel = "sol";
        work.reasoningEffort = "ultra";
        work.chatReasoning = "pro";
        String workScript = AutomationScript.build(work, "hello", "run-work", 0);
        assertTrue(workScript.contains("const desiredModel=\"sol\""));
        assertTrue(workScript.contains("const desiredEffort=\"ultra\""));
        assertFalse(workScript.contains("chatgpt-prompt-scheduler:chat-reasoning:"));
    }
}
