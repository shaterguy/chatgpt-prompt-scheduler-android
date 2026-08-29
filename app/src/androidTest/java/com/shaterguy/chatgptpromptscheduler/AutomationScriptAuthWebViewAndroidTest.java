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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class AutomationScriptAuthWebViewAndroidTest {
    private static final String TARGET_URL = "https://chatgpt.com/c/auth-regression";

    @Test public void visibleComposerBeatsIncidentalLoginText() throws Exception {
        JSONObject result = runScenario(
                "<p>Help: Log in to another service</p>"
                        + "<textarea id='prompt-textarea' style='display:block;width:400px;height:100px'></textarea>",
                "auth-incidental");
        assertNotEquals("AUTH_REQUIRED", result.getString("status"));
    }

    @Test public void explicitLoginControlWithoutComposerRequiresAuth() throws Exception {
        JSONObject result = runScenario(
                "<main><a href='/auth/login' style='display:block'>Log in</a></main>",
                "auth-explicit");
        assertEquals("AUTH_REQUIRED", result.getString("status"));
    }

    @Test public void missingComposerWithoutAuthControlRetries() throws Exception {
        JSONObject result = runScenario(
                "<main><div>Loading conversation…</div></main>",
                "auth-loading");
        assertEquals("RETRY", result.getString("status"));
        assertEquals("입력창 대기", result.getString("detail"));
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private static JSONObject runScenario(String body, String runId) throws Exception {
        Schedule schedule = new Schedule();
        schedule.targetType = "existing";
        schedule.targetUrl = TARGET_URL;
        String script = AutomationScript.build(schedule, "auth regression prompt", runId, 0);

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
            webView.loadDataWithBaseURL(TARGET_URL, html, "text/html", "UTF-8", null);
        });

        assertTrue("automation auth scenario timed out", done.await(12, TimeUnit.SECONDS));
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
