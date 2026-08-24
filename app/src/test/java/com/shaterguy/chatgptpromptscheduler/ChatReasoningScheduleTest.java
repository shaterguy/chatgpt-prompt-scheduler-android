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
    public void chatReasoningLabelsMapBackToPersistedValues() {
        assertEquals("keep", ScheduleEditorActivity.chatReasoningValue("현재 Chat 설정 유지"));
        assertEquals("instant", ScheduleEditorActivity.chatReasoningValue("Instant"));
        assertEquals("medium", ScheduleEditorActivity.chatReasoningValue("Medium"));
        assertEquals("high", ScheduleEditorActivity.chatReasoningValue("High"));
        assertEquals("xhigh", ScheduleEditorActivity.chatReasoningValue("Extra High"));
        assertEquals("pro", ScheduleEditorActivity.chatReasoningValue("Pro"));
        assertEquals("keep", ScheduleEditorActivity.chatReasoningValue("unknown"));

        assertEquals("xhigh", Schedule.normalizedChatReasoning(
                "chat", ScheduleEditorActivity.chatReasoningValue("Extra High")));
        assertEquals("pro", Schedule.normalizedChatReasoning(
                "chat", ScheduleEditorActivity.chatReasoningValue("Pro")));
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

        assertTrue(script.contains("if(modeDiagnostics.candidateFound&&!modeDiagnostics.selected)return result('RETRY','Chat 모드 선택 상태 확인 대기'"));
        assertTrue(script.contains("const __cpsWanted=\"xhigh\""));
        assertTrue(script.contains("chatgpt-prompt-scheduler:chat-reasoning:"));
        assertTrue(script.contains("data-animated-slider-trigger"));
        assertTrue(script.contains("advanced-menu"));
        assertTrue(script.contains("CHAT_REASONING_READBACK_MISMATCH"));
        assertFalse(script.contains("const desiredEffort=\"xhigh\""));
    }

    @Test
    public void chatModeWaitsForSelectedReadbackWithoutRepeatingModeClick() {
        Schedule schedule = new Schedule();
        schedule.targetType = "general";
        schedule.experience = "chat";
        schedule.chatReasoning = "medium";

        String script = AutomationScript.build(schedule, "hello", "run-mode-readback", 0);
        String clickGate = "if(mode&&!modeSelected&&!modePrior)";
        String readbackGate = "if(modeDiagnostics.candidateFound&&!modeDiagnostics.selected)return result('RETRY','Chat 모드 선택 상태 확인 대기'";

        assertTrue(script.contains(clickGate));
        assertTrue(script.contains("priorClick:!!modePrior"));
        assertTrue(script.contains(readbackGate));
        assertTrue(script.indexOf(clickGate) < script.indexOf(readbackGate));
        assertTrue(script.indexOf(readbackGate) < script.indexOf("const __cpsWanted=\"medium\""));
    }

    @Test
    public void chatReasoningScopesFallbackPopupsToOpenReasoningUi() {
        Schedule schedule = new Schedule();
        schedule.targetType = "general";
        schedule.experience = "chat";
        schedule.chatReasoning = "medium";

        String script = AutomationScript.build(schedule, "hello", "run-popup-scope", 0);

        assertTrue(script.contains("const __cpsTriggerOpen=!!__cpsTrigger&&"));
        assertTrue(script.contains("const __cpsReasoningPopup=popup=>"));
        assertTrue(script.contains("return levels.length>=2"));
        assertTrue(script.contains("const __cpsFallbackPopups=__cpsTriggerOpen?__cpsOpenPopups.filter(__cpsReasoningPopup):[]"));
        assertTrue(script.contains("const __cpsPopups=[__cpsControlled,...__cpsFallbackPopups]"));
        assertFalse(script.contains("const __cpsPopups=[__cpsControlled,...__cpsOpenPopups]"));
        assertTrue(script.contains("globalPopupCandidates:__cpsOpenPopups.length"));
        assertTrue(script.contains("fallbackPopupCandidates:__cpsFallbackPopups.length"));
    }

    @Test
    public void mediumSelectionPreservesOptionClickAndSelectedReadbackPath() {
        Schedule schedule = new Schedule();
        schedule.targetType = "general";
        schedule.experience = "chat";
        schedule.chatReasoning = "medium";

        String script = AutomationScript.build(schedule, "hello", "run-medium", 0);

        assertTrue(script.contains("const __cpsWanted=\"medium\""));
        assertTrue(script.contains("const __cpsWantedOption=__cpsDirectEntries.find(entry=>entry.level===__cpsWanted)"));
        assertTrue(script.contains("if(__cpsWantedOption&&selectedState(__cpsWantedOption.element))"));
        assertTrue(script.contains("__cpsActivate(__cpsWantedOption.element)"));
        assertTrue(script.contains("selected-option-readback"));
        assertTrue(script.contains("trigger-readback"));
    }

    @Test
    public void keepSkipsChatReasoningAndWorkPathRemainsSeparate() {
        Schedule chat = new Schedule();
        chat.targetType = "general";
        chat.experience = "chat";
        chat.chatReasoning = "keep";
        String chatScript = AutomationScript.build(chat, "hello", "run-keep", 0);
        assertTrue(chatScript.contains("if(modeDiagnostics.candidateFound&&!modeDiagnostics.selected)return result('RETRY','Chat 모드 선택 상태 확인 대기'"));
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
        assertFalse(workScript.contains("Chat 모드 선택 상태 확인 대기"));
        assertFalse(workScript.contains("chatgpt-prompt-scheduler:chat-reasoning:"));
    }
}
