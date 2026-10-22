package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.Assert.*;

public final class RequestProfileVersionContractTest {
    @Test public void producerUsesCanonicalEngineAndProfileVersions() throws Exception {
        String producer = source("app/src/main/java/com/shaterguy/chatgptpromptscheduler/RequestProfileScript.java");
        assertTrue(producer.contains("static final String ENGINE_VERSION"));
        assertTrue(producer.contains("__ENGINE_VERSION__"));
        assertTrue(producer.contains("__PROFILE_VERSION__"));
        assertTrue(producer.contains("RequestProfileEngine.PROFILE_VERSION"));
        String script = RequestProfileScript.documentStartScript();
        assertEquals(2, occurrences(script, RequestProfileScript.ENGINE_VERSION));
        assertEquals(2, occurrences(script, RequestProfileEngine.PROFILE_VERSION));
    }

    @Test public void interceptorScopeAndFailClosedContractAreExplicit() {
        assertEquals(Set.of("https://chatgpt.com", "https://www.chatgpt.com"), RequestProfileScript.CHATGPT_ORIGINS);
        String script = RequestProfileScript.documentStartScript();
        assertTrue(script.contains("path==='/backend-api/conversation'"));
        assertTrue(script.contains("path==='/backend-api/f/conversation'"));
        assertTrue(script.contains("path==='/backend-api/conversation/'"));
        assertFalse(script.contains("path.includes("));
        assertTrue(script.contains("if(!probe.eligible)return nativeFetch(input,init)"));
        assertTrue(script.contains("typeof text!=='string'"));
        assertTrue(script.contains("!Array.isArray(body.messages)"));
        assertTrue(script.contains("target_not_ready"));
        assertTrue(script.contains("invalid_conversation_json"));
        assertTrue(script.contains("operation_set_incomplete"));
        assertFalse(script.contains("console."));
        assertFalse(script.contains("addJavascriptInterface"));
        assertFalse(script.contains("WebMessage"));
    }

    @Test public void activeAutomationHasNoModeModelOrReasoningDomPreferenceWiring() {
        Schedule chat = new Schedule();
        chat.chatReasoning = "medium";
        chat.resolvedRequestProfile = LegacyProfileFixtures.profile(RequestProfileEngine.Mode.CHAT, "", "medium");
        chat.requestProfileRegistryResolved = true;
        String compose = AutomationScript.build(chat, "opaque prompt", "opaque-run", 0);
        assertTrue(compose.contains("__chatgptPromptSchedulerRequestProfileEngine"));
        assertTrue(compose.contains("profileEngine.configure(\"chat\""));
        assertFalse(compose.contains("menuitemradio"));
        assertFalse(compose.contains("desiredModelOption"));
        assertFalse(compose.contains("desiredEffortOption"));
        assertFalse(compose.contains("modeTrigger"));
        assertFalse(compose.contains("ModeBootstrapScript"));
        assertFalse(compose.contains("ChatReasoningScript"));
        assertFalse(compose.contains("actualPreview"));
        assertFalse(compose.contains("htmlPreview"));

        Schedule existing = new Schedule();
        existing.targetType = "existing";
        existing.targetUrl = "https://chatgpt.com/c/opaque";
        String inherited = AutomationScript.build(existing, "opaque prompt", "opaque-run", 0);
        assertTrue(inherited.contains("action:'native-inherit'"));
        assertFalse(inherited.contains("__chatgptPromptSchedulerRequestProfileEngine"));
    }

    @Test public void devIdentityAndPriorStableSignerContractRemainPinned() throws Exception {
        String gradle = source("app/build.gradle");
        assertEquals(1, occurrences(gradle, "androidx.webkit:webkit:1.17.0"));
        assertTrue(gradle.contains("applicationId 'com.shaterguy.chatgptpromptscheduler.dev'"));
        assertTrue(gradle.contains("versionCode 6010001"));
        assertTrue(gradle.contains("versionName '0.6.1-dev1'"));
        String strings = source("app/src/main/res/values/strings.xml");
        assertTrue(strings.contains("<string name=\"app_name\">ChatGPT Prompt Scheduler DEV</string>"));
        assertTrue(strings.contains("Scheduler DEV"));
        String devWorkflow = source(".github/workflows/android-dev.yml");
        assertTrue(devWorkflow.contains("DEV_PACKAGE: com.shaterguy.chatgptpromptscheduler.dev"));
        assertTrue(devWorkflow.contains("DEV_VERSION_CODE: '6010001'"));
        assertTrue(devWorkflow.contains("DEV_VERSION_NAME: 0.6.1-dev1"));
        assertTrue(devWorkflow.contains("DEV_UNSIGNED_NAME: chatgpt-prompt-scheduler-dev-v0.6.1-dev1-unsigned.apk"));
        String workflow = source(".github/workflows/android-build.yml");
        assertTrue(workflow.contains("APK_NAME: chatgpt-prompt-scheduler-android-v0.6.0.apk"));
        assertTrue(workflow.contains("SOURCE_NAME: chatgpt-prompt-scheduler-android-v0.6.0-source.zip"));
        assertTrue(workflow.contains("PACKAGE_NAME: com.shaterguy.chatgptpromptscheduler"));
        assertTrue(workflow.contains("VERSION_CODE: '2100000012'"));
        assertTrue(workflow.contains("VERSION_NAME: '0.6.0'"));
        assertTrue(workflow.contains("UPDATE_BASELINE_TAG: v0.5.6"));
        assertTrue(workflow.contains("OLD_VERSION_CODE: '2100000011'"));
        assertTrue(workflow.contains("3cfe95acd09077a89cd8de85434cbd5d8bb3e2021d8e9eacb804a8da9ccce52a"));
        assertTrue(workflow.contains("RELEASE_NOTES-v0.6.0.md"));
        assertTrue(workflow.contains("gh release create v0.6.0"));
        assertTrue(workflow.contains(":app:testDebugUnitTest"));
        assertTrue(workflow.contains("apksigner"));
        assertTrue(workflow.contains("SHA256SUMS.txt"));
        assertFalse(workflow.contains("android-emulator-runner"));
        assertFalse(workflow.contains("connectedDebugAndroidTest"));
        assertTrue(workflow.contains("actions/checkout@11d5960a326750d5838078e36cf38b85af677262"));
    }

    private static String source(String relative) throws Exception {
        Path path = Paths.get(relative);
        if (!Files.exists(path)) path = Paths.get("..").resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        for (int at = value.indexOf(needle); at >= 0;
             at = value.indexOf(needle, at + needle.length())) count++;
        return count;
    }
}
