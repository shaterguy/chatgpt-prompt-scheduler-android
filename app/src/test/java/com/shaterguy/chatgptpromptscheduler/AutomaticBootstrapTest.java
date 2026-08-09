package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public final class AutomaticBootstrapTest {
    @Test public void generatedJobIdsAreProtocolCompatibleAndUnique() {
        Set<String> used = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String value = AutomationJobId.create(used);
            assertTrue(value.matches("AR-\\d{8}-\\d{6}-[A-Z2-9]{6}"));
            assertTrue(used.add(value));
        }
    }

    @Test public void projectIdentityRequiresSameProject() {
        String home = "https://chatgpt.com/g/project-one";
        assertTrue(TargetParser.isProjectHome(home));
        assertTrue(TargetParser.isProjectConversation(home,
                "https://chatgpt.com/g/project-one/c/chat-one"));
        assertFalse(TargetParser.isProjectConversation(home,
                "https://chatgpt.com/g/project-two/c/chat-one"));
        assertFalse(TargetParser.isProjectHome("https://chatgpt.com/g/project-one/c/chat-one"));
    }

    @Test public void bootstrapMarkerMakesAppOwnershipExplicit() {
        String prompt = OrchestrationStore.bootstrapPrompt("AR-20260809-010203-ABC234", "project-one",
                "(오토런)\n기능을 구현한다.");
        assertTrue(prompt.startsWith("(오토런)"));
        assertTrue(prompt.contains("[AUTOMATION_BOOTSTRAP 3.3.0 AR-20260809-010203-ABC234]"));
        assertTrue(prompt.contains("provisioning_owner=android_app"));
        assertTrue(prompt.contains("manual_identifiers_required=false"));
    }

    @Test public void provisioningScriptsSeparatePrepareCommitAndObserve() {
        String project = "https://chatgpt.com/g/project-one";
        String prepared = ProvisioningScript.prepare(OrchestrationStore.SIDE_WORK, project,
                "hello", "AR-20260809-010203-ABC234", "sol", "ultra");
        String committed = ProvisioningScript.commit(project, "hello",
                "AR-20260809-010203-ABC234", OrchestrationStore.SIDE_WORK);
        String observed = ProvisioningScript.observe(project, "hello");
        assertFalse(prepared.contains("send.click()"));
        assertTrue(committed.contains("send.click()"));
        assertFalse(observed.contains("send.click()"));
        assertTrue(prepared.contains("desiredModel=\"sol\""));
        assertTrue(prepared.contains("desiredEffort=\"ultra\""));
        assertTrue(prepared.contains("main form [contenteditable=\"true\"][data-lexical-editor=\"true\"]"));
        assertFalse(prepared.contains("','[contenteditable=\"true\"][data-lexical-editor=\"true\"]'"));
    }
}
