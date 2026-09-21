package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class ConversationVerificationWebViewAndroidTest {
    private static final String PROJECT = "g-p-6a507cce80cc81919eeb9ba553b6ad9e";
    private static final String OTHER_PROJECT = "g-p-7a507cce80cc81919eeb9ba553b6ad9e";
    private static final String PROMPT = "conversation verification prompt";

    @Test public void projectRootWithOptimisticMessageWaitsForConversationAddress() throws Exception {
        Schedule schedule = projectSchedule();
        JSONObject result = runVerification(schedule,
                "https://chatgpt.com/g/" + PROJECT + "/project", PROMPT, userMessage(PROMPT));
        assertEquals("RETRY", result.getString("status"));
        assertEquals("실제 대화 주소 생성 대기", result.getString("detail"));
    }

    @Test public void projectConversationWithPromptVerifies() throws Exception {
        Schedule schedule = projectSchedule();
        String url = "https://chatgpt.com/g/" + PROJECT + "-vibe-coding/c/conversation_1";
        JSONObject result = runVerification(schedule, url, PROMPT, userMessage(PROMPT));
        assertEquals("VERIFIED", result.getString("status"));
        assertEquals(url, result.getString("url"));
    }

    @Test public void generalHomeWithOptimisticMessageWaitsForConversationAddress() throws Exception {
        Schedule schedule = generalSchedule();
        JSONObject result = runVerification(schedule, "https://chatgpt.com/", PROMPT, userMessage(PROMPT));
        assertEquals("RETRY", result.getString("status"));
        assertEquals("실제 대화 주소 생성 대기", result.getString("detail"));
    }

    @Test public void generalConversationWithPromptVerifies() throws Exception {
        Schedule schedule = generalSchedule();
        String url = "https://chatgpt.com/c/general_conversation";
        JSONObject result = runVerification(schedule, url, PROMPT, userMessage(PROMPT));
        assertEquals("VERIFIED", result.getString("status"));
        assertEquals(url, result.getString("url"));
    }

    @Test public void wrongProjectAndMissingPromptDoNotVerify() throws Exception {
        Schedule project = projectSchedule();
        JSONObject wrongProject = runVerification(project,
                "https://chatgpt.com/g/" + OTHER_PROJECT + "/c/conversation_1", PROMPT, userMessage(PROMPT));
        assertEquals("TARGET_CONTEXT_MISMATCH", wrongProject.getString("status"));

        JSONObject missingPrompt = runVerification(project,
                "https://chatgpt.com/g/" + PROJECT + "/c/conversation_1", PROMPT, userMessage("different prompt"));
        assertEquals("RETRY", missingPrompt.getString("status"));
        assertEquals("전송된 사용자 메시지 대기", missingPrompt.getString("detail"));
    }

    private static Schedule projectSchedule() {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = "https://chatgpt.com/g/" + PROJECT + "/project";
        return schedule;
    }

    private static Schedule generalSchedule() {
        Schedule schedule = new Schedule();
        schedule.targetType = "general";
        schedule.targetUrl = "https://chatgpt.com/";
        return schedule;
    }

    private static String userMessage(String text) {
        return "<article data-turn='user' data-message-author-role='user'>" + text + "</article>";
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private static JSONObject runVerification(Schedule schedule, String currentUrl, String prompt, String body) throws Exception {
        String script = ConversationVerificationScript.build(schedule, prompt);
        Context context = ApplicationProvider.getApplicationContext();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> rawResult = new AtomicReference<>();
        AtomicReference<HeadlessWebViewHost> hostRef = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            HeadlessWebViewHost host = HeadlessWebViewHost.create(context);
            hostRef.set(host);
            WebView webView = host.webView();
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            webView.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) {
                    view.evaluateJavascript(script, raw -> {
                        rawResult.set(raw);
                        done.countDown();
                    });
                }
            });
            String html = "<!doctype html><html><body>" + body + "</body></html>";
            webView.loadDataWithBaseURL(currentUrl, html, "text/html", "UTF-8", null);
        });

        assertTrue("verification scenario timed out", done.await(12, TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            HeadlessWebViewHost host = hostRef.get();
            if (host != null) host.destroy();
        });

        assertNotNull(rawResult.get());
        Object decoded = new JSONTokener(rawResult.get()).nextValue();
        String json = decoded instanceof String ? (String) decoded : String.valueOf(decoded);
        return new JSONObject(json);
    }
}
