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

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class DevToolsComposerInputWebViewAndroidTest {
    @Test public void localDevToolsInputReplacesSelectionOnVirtualPresentation() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String marker = "scheduler-devtools-fixture";
        String expected = "한글과 줄바꿈\nNative CDP text";
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<HeadlessWebViewHost> hostRef = new AtomicReference<>();
        AtomicReference<String> focusResult = new AtomicReference<>("");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            HeadlessWebViewHost host = HeadlessWebViewHost.create(context);
            hostRef.set(host);
            WebView web = host.webView();
            web.getSettings().setJavaScriptEnabled(true);
            web.getSettings().setDomStorageEnabled(true);
            web.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) {
                    view.evaluateJavascript("(()=>{window.__chatgptSchedulerInputTarget='"
                                    + marker + "';const editor=document.getElementById('editor');"
                                    + "editor.focus();const range=document.createRange();"
                                    + "range.selectNodeContents(editor);const selection=window.getSelection();"
                                    + "selection.removeAllRanges();selection.addRange(range);"
                                    + "return String(document.activeElement===editor);})()",
                            result -> { focusResult.set(result); ready.countDown(); });
                }
            });
            String html = "<!doctype html><html><body><main><form>"
                    + "<div id='editor' class='ProseMirror' role='textbox' contenteditable='true' "
                    + "style='display:block;min-height:50px'><p>old draft</p></div>"
                    + "</form></main><script>"
                    + "document.getElementById('editor').addEventListener('input',"
                    + "e=>{window.__trustedInput=!!e.isTrusted;});</script></body></html>";
            web.loadDataWithBaseURL("https://chatgpt.com/c/devtools-scheduler-fixture",
                    html, "text/html", "UTF-8", null);
        });
        assertTrue("WebView fixture did not load", ready.await(12, TimeUnit.SECONDS));
        assertEquals("true", decode(focusResult.get()));

        DevToolsComposerInput.Result delivery = null;
        for (int i = 0; i < 10; i++) {
            delivery = DevToolsComposerInput.insertText(marker, expected);
            if (delivery.accepted) break;
            Thread.sleep(250L);
        }
        assertNotNull(delivery);
        assertTrue("CDP input rejected: " + delivery.code, delivery.accepted);

        Thread.sleep(500L);
        CountDownLatch read = new CountDownLatch(1);
        AtomicReference<String> raw = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            HeadlessWebViewHost host = hostRef.get();
            host.webView().evaluateJavascript("JSON.stringify({text:document.getElementById('editor').textContent,"
                    + "trusted:!!window.__trustedInput})",
                    result -> { raw.set(result); read.countDown(); });
        });
        assertTrue("WebView result not readable", read.await(10, TimeUnit.SECONDS));
        JSONObject seen = new JSONObject(decode(raw.get()));
        assertEquals(expected, seen.optString("text"));
        assertTrue("CDP input event should be trusted", seen.optBoolean("trusted"));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            HeadlessWebViewHost host = hostRef.get();
            if (host != null) host.destroy();
        });
    }

    private static String decode(String raw) throws Exception {
        Object decoded = new JSONTokener(raw == null ? "\"\"" : raw).nextValue();
        return decoded instanceof String ? (String) decoded : String.valueOf(decoded);
    }
}
