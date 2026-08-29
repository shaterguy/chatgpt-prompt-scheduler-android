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
        assertEquals(Set.of("https://chatgpt.com", "https://www.chatgpt.com"),
                RequestProfileScript.CHATGPT_ORIGINS);
        String script = RequestProfileScript.documentStartScript();
        assertTrue(script.contains("path==='/backend-api/conversation'"));
        assertTrue(script.contains("path==='/backend-api/f/conversation'"));
        assertTrue(script.contains("path==='/backend-api/conversation/'"));
        assertFalse(script.contains("path.includes("));
        assertFalse(script.contains("replace(/\\/+$/"));
        assertTrue(script.contains("if(!probe.eligible)return nativeFetch(input,init)"));
        assertTrue(script.contains("typeof text!=='string'"));
        assertTrue(script.contains("!Array.isArray(body.messages)"));
        assertTrue(script.contains("target_not_ready"));
        assertTrue(script.contains("invalid_conversation_json"));
        assertFalse(script.contains("console."));
        assertFalse(script.contains("addJavascriptInterface"));
        assertFalse(script.contains("WebMessage"));
    }

    @Test public void activeAutomationHasNoModeModelOrReasoningDomPreferenceWiring() {
        Schedule chat = new Schedule();
        chat.chatReasoning = "medium";
        String compose = AutomationScript.build(chat, "opaque prompt", "opaque-run", 0);
        assertTrue(compose.contains("__chatgptPromptSchedulerRequestProfileEngine"));
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

    @Test public void dependencyStableIdentityAndDevWorkflowStayPinnedAndAttemptSpecific() throws Exception {
        String gradle = source("app/build.gradle");
        assertEquals(1, occurrences(gradle, "androidx.webkit:webkit:1.17.0"));
        assertTrue(gradle.contains("applicationId 'com.shaterguy.chatgptpromptscheduler'"));
        assertTrue(gradle.contains("versionCode 2100000003"));
        assertTrue(gradle.contains("versionName '0.3.2'"));

        String workflow = source(".github/workflows/android-dev.yml");
        assertTrue(workflow.contains("DEV_VERSION_CODE: '3002001'"));
        assertTrue(workflow.contains("DEV_VERSION_NAME: 0.3.2-dev1"));
        assertTrue(workflow.contains("attempt-${{ github.run_attempt }}"));
        assertTrue(workflow.contains(":app:connectedDebugAndroidTest"));
        assertTrue(workflow.contains(":app:testDebugUnitTest :app:assembleRelease"));
        assertTrue(workflow.contains("actions/checkout@11d5960a326750d5838078e36cf38b85af677262"));
        assertTrue(workflow.contains("actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02"));
    }

    private static String source(String relative) throws Exception {
        Path path = Paths.get(relative);
        if (!Files.exists(path)) path = Paths.get("..").resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        for (int at = value.indexOf(needle); at >= 0;
             at = value.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }
}
