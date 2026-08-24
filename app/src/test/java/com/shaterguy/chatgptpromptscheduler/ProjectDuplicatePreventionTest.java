package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProjectDuplicatePreventionTest {
    @Test
    public void projectFreshConversationIsAcceptedBeforeSubmit() {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = "https://chatgpt.com/g/proj/project";

        String script = AutomationScript.build(schedule, "prompt", "run-project", 0);

        assertTrue(script.contains("actualProject===expectedProject&&(!actualConversation||afterSubmit||promptAlreadyPresent||users.length===0)"));
        assertTrue(script.contains("promptAlreadyPresent"));
        assertTrue(script.contains("동일 실행 프롬프트가 이미 새 대화에 존재합니다."));
        assertFalse(script.contains("actualProject===expectedProject&&(afterSubmit||!actualConversation)"));
    }

    @Test
    public void modeSelectionUsesFiniteExactTargetContract() {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = "https://chatgpt.com/g/proj/project";
        schedule.experience = "chat";

        String script = AutomationScript.build(schedule, "prompt", "run-mode", 0);

        assertTrue(script.contains("data-tpp-toggle-value"));
        assertTrue(script.contains("MODE_CONFIRMED"));
        assertTrue(script.contains("CHAT_MODE_READBACK_FAILED"));
        assertTrue(script.contains("CHAT_MODE_CONTROL_NOT_FOUND"));
        assertTrue(script.contains("clickAttempts)<2"));
        assertTrue(script.contains("__cpmActivate(mode)"));
        assertTrue(script.contains("priorClick:!!modePrior"));
        assertTrue(script.contains("chatgpt-prompt-scheduler:mode-stage:"));
        assertTrue(script.contains("chatgpt-prompt-scheduler:mode:"));
        assertTrue(script.contains("if(e.closest('[role=\"menu\"],[role=\"listbox\"]'))return false"));
        assertFalse(script.contains("mode.click()"));
        assertFalse(script.contains("native-project-option"));
        assertFalse(script.contains("NATIVE_TAP"));
    }

    @Test
    public void workReasoningSelectionSupportsLocalizedDesktopAndMobileMenus() {
        Schedule schedule = new Schedule();
        schedule.targetType = "general";
        schedule.targetUrl = "https://chatgpt.com/";
        schedule.experience = "work";
        schedule.reasoningEffort = "xhigh";

        String script = AutomationScript.build(schedule, "prompt", "run-reasoning", 0);

        assertTrue(script.contains("desiredEffort=\"xhigh\""));
        assertTrue(script.contains("매우 높음"));
        assertTrue(script.contains("extra high"));
        assertTrue(script.contains("[role=\"menuitemradio\"],[role=\"radio\"],[role=\"option\"],[role=\"menuitem\"]"));
        assertTrue(script.contains("reasoning (level|effort)|추론 (수준|강도|정도)"));
        assertTrue(script.contains("reasoningTriggerExpanded"));
        assertTrue(script.contains("!reasoningTriggerExpanded"));
        assertTrue(script.contains("open-reasoning-menu"));
        assertTrue(script.contains("openMenu(reasoningTrigger)"));
        assertTrue(script.contains("filter(inComposer)"));
        assertTrue(script.contains("triggerInComposer"));
        assertTrue(script.contains("new PointerEvent('pointerdown'"));
        assertTrue(script.contains("open-effort-menu"));
        assertTrue(script.contains("select-effort"));
        assertTrue(script.contains("close-selected-effort-menu"));
        assertTrue(script.contains("reasoningDiagnostics"));
    }

    @Test
    public void ultraReasoningSelectionSupportsKoreanAndEnglishLabels() {
        Schedule schedule = new Schedule();
        schedule.targetType = "general";
        schedule.targetUrl = "https://chatgpt.com/";
        schedule.experience = "work";
        schedule.reasoningEffort = "ultra";

        String script = AutomationScript.build(schedule, "prompt", "run-ultra", 0);

        assertTrue(script.contains("desiredEffort=\"ultra\""));
        assertTrue(script.contains("['울트라','ultra']"));
        assertTrue(script.contains("ultra|울트라|very high"));
        assertTrue(script.indexOf("['울트라','ultra']") < script.indexOf("'xhigh'"));
        assertTrue(script.contains("open-effort-menu"));
        assertTrue(script.contains("select-effort"));
        assertTrue(script.contains("reasoningDiagnostics"));
    }

    @Test
    public void unrelatedExistingProjectConversationStillRecoversTarget() {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = "https://chatgpt.com/g/proj/project";

        String script = AutomationScript.build(schedule, "prompt", "run-unrelated", 0);

        assertTrue(script.contains("users.length===0"));
        assertTrue(script.contains("TARGET_CONTEXT_MISMATCH"));
        assertTrue(script.contains("routeDiagnostics"));
    }

    @Test
    public void existingConversationTargetRemainsStrict() {
        Schedule schedule = new Schedule();
        schedule.targetType = "existing";
        schedule.targetUrl = "https://chatgpt.com/g/proj/c/abc";

        String script = AutomationScript.build(schedule, "prompt", "run-existing", 0);

        assertTrue(script.contains("actualConversation===expectedConversation"));
        assertTrue(script.contains("requested:'inherit'"));
        assertFalse(script.contains("expectedType==='existing'&&users.length===0"));
    }

    @Test
    public void workModelSelectionSupportsSolTerraAndLunaMenus() {
        Schedule schedule = new Schedule();
        schedule.targetType = "general";
        schedule.targetUrl = "https://chatgpt.com/";
        schedule.experience = "work";
        schedule.workModel = "terra";
        schedule.reasoningEffort = "medium";

        String script = AutomationScript.build(schedule, "prompt", "run-model", 0);

        assertTrue(script.contains("desiredModel=\"terra\""));
        assertTrue(script.contains("sol|terra|luna"));
        assertTrue(script.contains("modelLevelItem"));
        assertTrue(script.contains("workSettingsTrigger"));
        assertTrue(script.contains("open-work-settings-menu"));
        assertTrue(script.contains("open-model-menu"));
        assertTrue(script.contains("select-model"));
        assertTrue(script.contains("close-selected-model-menu"));
        assertTrue(script.contains("modelDiagnostics"));
        assertTrue(script.indexOf("const desiredModel") < script.indexOf("const desiredEffort"));
        assertTrue(script.indexOf("currentModel===desiredModel") < script.indexOf("else if(modelLevelItem)"));
        assertTrue(script.contains("else if(workSettingsTrigger&&currentModel===desiredModel){modelReady=true;}"));
        assertTrue(script.indexOf("currentEffort===desiredEffort") < script.indexOf("else if(reasoningLevelItem)"));
    }}
