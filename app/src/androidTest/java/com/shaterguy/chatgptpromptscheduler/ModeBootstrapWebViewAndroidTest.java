package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
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

/** Exercises the real scheduler AutomationScript in the same headless WebView host used by schedules. */
@RunWith(AndroidJUnit4.class)
public final class ModeBootstrapWebViewAndroidTest {
    private static final String PROJECT_URL = "https://chatgpt.com/g/g-p-test/project";

    @Test
    public void alreadyChatSkipsModeClickAndSubmitsAfterReasoningSelection() throws Exception {
        Fixture fixture = load(fixture(false, false));
        try {
            JSONObject submitted = runToSubmitted(fixture.web, "CPS-CHAT-READY");

            assertEquals("SUBMITTED", submitted.getString("status"));
            assertEquals("0", read(fixture.web, "String(window.chatClicks)"));
            assertEquals("1", read(fixture.web, "String(window.instantClicks)"));
            assertEquals("1", read(fixture.web, "String(window.sendClicks)"));
        } finally {
            destroy(fixture.host);
        }
    }

    @Test
    public void workToChatClicksModeExactlyOnceBeforeReasoningAndSubmit() throws Exception {
        Fixture fixture = load(fixture(true, false));
        try {
            JSONObject submitted = runToSubmitted(fixture.web, "CPS-WORK-TO-CHAT");

            assertEquals("SUBMITTED", submitted.getString("status"));
            assertEquals("1", read(fixture.web, "String(window.chatClicks)"));
            assertEquals("1", read(fixture.web, "String(window.instantClicks)"));
            assertEquals("1", read(fixture.web, "String(window.sendClicks)"));
        } finally {
            destroy(fixture.host);
        }
    }

    @Test
    public void confirmedChatSurvivesReasoningPickerRemovingModeSelectedSignals() throws Exception {
        Fixture fixture = load(fixture(false, true));
        try {
            String runId = "CPS-MODE-LATCH";
            JSONObject submitted = runToSubmitted(fixture.web, runId);

            assertEquals("SUBMITTED", submitted.getString("status"));
            assertEquals("0", read(fixture.web, "String(window.chatClicks)"));
            assertEquals("1", read(fixture.web, "String(window.instantClicks)"));
            assertEquals("1", read(fixture.web, "String(window.sendClicks)"));
            assertEquals("MODE_CONFIRMED", read(fixture.web,
                    "(()=>{const k='chatgpt-prompt-scheduler:mode-stage:" + runId
                            + "';const v=localStorage.getItem(k)||sessionStorage.getItem(k);return JSON.parse(v).stage;})()"));
        } finally {
            destroy(fixture.host);
        }
    }

    private static JSONObject runToSubmitted(WebView web, String runId) throws Exception {
        JSONObject result = null;
        Schedule schedule = schedule();
        for (int attempt = 0; attempt < 14; attempt++) {
            result = evaluate(web, AutomationScript.build(schedule, "hello", runId, attempt));
            String status = result.getString("status");
            if ("SUBMITTED".equals(status)) break;
            assertEquals(result.toString(), "RETRY", status);
        }
        assertNotNull(result);
        assertEquals(result.toString(), "SUBMITTED", result.getString("status"));
        return result;
    }

    private static Schedule schedule() {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = PROJECT_URL;
        schedule.experience = "chat";
        schedule.chatReasoning = "instant";
        schedule.workModel = "inherit";
        schedule.reasoningEffort = "inherit";
        return schedule;
    }

    private static String fixture(boolean initialWork, boolean dropModeSignalsOnReasoningOpen) {
        return """
                <!doctype html><html><head><style>body{min-height:800px}form{margin-top:360px}#reasoning-menu{position:absolute;min-width:240px}</style></head><body>
                <div id="mode-group">
                  <button id="chat" type="button" role="radio" aria-checked="__CHAT__" data-state="__CHAT_STATE__" data-tpp-toggle-value="chatgpt">Chat</button>
                  <button id="work" type="button" role="radio" aria-checked="__WORK__" data-state="__WORK_STATE__" data-tpp-toggle-value="work">Work</button>
                </div>
                <form>
                  <textarea id="prompt-textarea"></textarea>
                  <button id="reasoning-trigger" type="button" aria-haspopup="menu" aria-controls="reasoning-menu" aria-expanded="false"><span data-animated-slider-trigger="true">Medium</span></button>
                  <button id="send" type="button" data-testid="send-button" aria-label="Send">Send</button>
                </form>
                <div id="reasoning-menu" role="menu" hidden>
                  <button id="instant" type="button" role="menuitemradio" aria-checked="false">Instant</button>
                  <button id="medium" type="button" role="menuitemradio" aria-checked="true">Medium</button>
                  <button id="high" type="button" role="menuitemradio" aria-checked="false">High</button>
                </div>
                <script>
                window.chatClicks=0;window.workClicks=0;window.reasoningClicks=0;window.instantClicks=0;window.sendClicks=0;
                const chat=document.getElementById('chat'),work=document.getElementById('work'),trigger=document.getElementById('reasoning-trigger'),menu=document.getElementById('reasoning-menu');
                const selectMode=value=>{const chatOn=value==='chat';chat.setAttribute('aria-checked',String(chatOn));chat.dataset.state=chatOn?'on':'off';work.setAttribute('aria-checked',String(!chatOn));work.dataset.state=chatOn?'off':'on';};
                chat.onclick=()=>{window.chatClicks++;selectMode('chat');};
                work.onclick=()=>{window.workClicks++;selectMode('work');};
                trigger.onclick=()=>{window.reasoningClicks++;const opening=menu.hidden;menu.hidden=!opening;trigger.setAttribute('aria-expanded',opening?'true':'false');if(opening&&__DROP__){chat.removeAttribute('aria-checked');chat.removeAttribute('data-state');work.removeAttribute('aria-checked');work.removeAttribute('data-state');}};
                document.getElementById('instant').onclick=event=>{window.instantClicks++;for(const option of menu.querySelectorAll('[role=menuitemradio]'))option.setAttribute('aria-checked','false');event.currentTarget.setAttribute('aria-checked','true');trigger.innerHTML='<span data-animated-slider-trigger="true">Instant</span>';trigger.setAttribute('aria-expanded','false');menu.hidden=true;};
                document.getElementById('send').onclick=()=>{window.sendClicks++;};
                </script></body></html>
                """
                .replace("__CHAT_STATE__", initialWork ? "off" : "on")
                .replace("__WORK_STATE__", initialWork ? "on" : "off")
                .replace("__CHAT__", initialWork ? "false" : "true")
                .replace("__WORK__", initialWork ? "true" : "false")
                .replace("__DROP__", dropModeSignalsOnReasoningOpen ? "true" : "false");
    }

    private static Fixture load(String html) throws Exception {
        CountDownLatch loaded = new CountDownLatch(1);
        AtomicReference<Fixture> fixture = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = ApplicationProvider.getApplicationContext();
            HeadlessWebViewHost host = HeadlessWebViewHost.create(context);
            WebView web = host.webView();
            web.getSettings().setJavaScriptEnabled(true);
            web.getSettings().setDomStorageEnabled(true);
            web.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView ignored, String url) {
                    if (url != null && url.startsWith(PROJECT_URL)) loaded.countDown();
                }
            });
            fixture.set(new Fixture(host, web));
            web.loadDataWithBaseURL(PROJECT_URL, html, "text/html", "UTF-8", null);
        });
        assertTrue("Scheduler headless WebView fixture did not load", loaded.await(15, TimeUnit.SECONDS));
        Fixture value = fixture.get();
        assertNotNull(value);
        assertTrue("Scheduler automation must test a window-attached WebView", value.host.isWindowAttached());
        assertNotNull("Android System WebView must be available", WebView.getCurrentWebViewPackage());
        return value;
    }

    private static void destroy(HeadlessWebViewHost host) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(host::destroy);
    }

    private static JSONObject evaluate(WebView web, String script) throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<String> raw = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(script, value -> {
            raw.set(value);
            complete.countDown();
        }));
        assertTrue("WebView script timed out", complete.await(15, TimeUnit.SECONDS));
        Object decoded = new JSONTokener(raw.get()).nextValue();
        return new JSONObject(String.valueOf(decoded));
    }

    private static String read(WebView web, String expression) throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<String> raw = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(expression, value -> {
            raw.set(value);
            complete.countDown();
        }));
        assertTrue("WebView read timed out", complete.await(15, TimeUnit.SECONDS));
        return String.valueOf(new JSONTokener(raw.get()).nextValue());
    }

    private record Fixture(HeadlessWebViewHost host, WebView web) {}
}
