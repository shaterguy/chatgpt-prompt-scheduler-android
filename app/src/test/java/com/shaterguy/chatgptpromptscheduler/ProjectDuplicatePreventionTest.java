package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProjectDuplicatePreventionTest {
    @Test public void projectFreshConversationIsAcceptedBeforeSubmit() {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = "https://chatgpt.com/g/proj/project";
        String script = AutomationScript.build(schedule, "prompt", "run-project", 0);
        assertTrue(script.contains("actualProject===expectedProject&&(!actualConversation||afterSubmit||promptAlreadyPresent||users.length===0)"));
        assertTrue(script.contains("promptAlreadyPresent"));
        assertTrue(script.contains("동일 실행 프롬프트가 이미 새 대화에 존재합니다."));
        assertFalse(script.contains("actualProject===expectedProject&&(afterSubmit||!actualConversation)"));
    }

    @Test public void chatRequestProfileActivationUsesExactOperationPlan() {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = "https://chatgpt.com/g/proj/project";
        schedule.experience = "chat";
        schedule.chatReasoning = "medium";
        String script = AutomationScript.build(schedule, "prompt", "run-chat-profile", 0);
        assertTrue(script.contains("__chatgptPromptSchedulerRequestProfileEngine"));
        assertTrue(script.contains("profileEngine.configure(\"chat\",\"\",\"medium\""));
        assertTrue(script.contains("[\"set\",\"model\",\"gpt-5-6-thinking\"]"));
        assertTrue(script.contains("[\"remove\",\"service_tier\"]"));
        assertTrue(script.contains("action:'request-profile'"));
        assertFalse(script.contains("data-tpp-toggle-value"));
        assertFalse(script.contains("modeTrigger"));
    }

    @Test public void workRequestProfileActivationUsesOnlyRegisteredCombination() {
        Schedule schedule = new Schedule();
        schedule.targetType = "general";
        schedule.targetUrl = "https://chatgpt.com/";
        schedule.experience = "work";
        schedule.workModel = "terra";
        schedule.reasoningEffort = "xhigh";
        String script = AutomationScript.build(schedule, "prompt", "run-work-profile", 0);
        assertTrue(script.contains("profileEngine.configure(\"work\",\"terra\",\"xhigh\""));
        assertTrue(script.contains("[\"set\",\"model\",\"gpt-5.6-terra-wm\"]"));
        assertTrue(script.contains("[\"set\",\"service_tier\",\"standard\"]"));
        assertFalse(script.contains("menuitemradio"));
        assertFalse(script.contains("open-reasoning-menu"));
        assertFalse(script.contains("openMenu("));
    }

    @Test public void terraUltraPreservesRegistryServiceTierRemoval() {
        Schedule schedule = new Schedule();
        schedule.targetType = "general";
        schedule.targetUrl = "https://chatgpt.com/";
        schedule.experience = "work";
        schedule.workModel = "terra";
        schedule.reasoningEffort = "ultra";
        String script = AutomationScript.build(schedule, "prompt", "run-ultra-profile", 0);
        assertTrue(script.contains("profileEngine.configure(\"work\",\"terra\",\"ultra\""));
        assertTrue(script.contains("[\"remove\",\"service_tier\"]"));
        assertFalse(script.contains("open-effort-menu"));
        assertFalse(script.contains("select-effort"));
    }

    @Test public void unregisteredWorkCartesianCombinationFailsBeforeProfileActivation() {
        Schedule schedule = new Schedule();
        schedule.targetType = "general";
        schedule.targetUrl = "https://chatgpt.com/";
        schedule.experience = "work";
        schedule.workModel = "terra";
        schedule.reasoningEffort = "medium";
        String script = AutomationScript.build(schedule, "prompt", "run-invalid-profile", 0);
        assertTrue(script.contains("REQUEST_PROFILE_INVALID"));
        assertFalse(script.contains("profileEngine.configure(\"work\",\"terra\",\"medium\""));
    }

    @Test public void unrelatedExistingProjectConversationStillRecoversTarget() {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = "https://chatgpt.com/g/proj/project";
        String script = AutomationScript.build(schedule, "prompt", "run-unrelated", 0);
        assertTrue(script.contains("users.length===0"));
        assertTrue(script.contains("TARGET_CONTEXT_MISMATCH"));
        assertTrue(script.contains("routeDiagnostics"));
    }

    @Test public void existingConversationTargetRemainsStrict() {
        Schedule schedule = new Schedule();
        schedule.targetType = "existing";
        schedule.targetUrl = "https://chatgpt.com/g/proj/c/abc";
        String script = AutomationScript.build(schedule, "prompt", "run-existing", 0);
        assertTrue(script.contains("actualConversation===expectedConversation"));
        assertTrue(script.contains("requested:\"inherit\""));
        assertFalse(script.contains("expectedType==='existing'&&users.length===0"));
    }
}
