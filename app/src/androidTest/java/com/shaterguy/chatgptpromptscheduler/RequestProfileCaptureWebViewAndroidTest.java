package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.webkit.WebViewCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class RequestProfileCaptureWebViewAndroidTest {
    private static final String HARNESS = """
            window.__testDone=false;window.__calls=[];window.__xhr=[];
            window.fetch=async function(input,init){
              const request=new Request(input,init);
              const body=await request.text();
              window.__calls.push({url:request.url,method:request.method,body});
              return new Response('native-response',{status:200});
            };
            window.XMLHttpRequest=function(){};
            XMLHttpRequest.prototype.open=function(method,url){this.method=method;this.url=url;};
            XMLHttpRequest.prototype.send=function(body){window.__xhr.push({method:this.method,url:this.url,body});};
            """;

    @Test public void chatFetchOneShotDoesNotChangeNativeRequestOrCollectPrompt() throws Exception {
        Result result = run("""
                const e=window.__schedulerProfileCapture;
                e.arm('chat-session','chat');
                const body=JSON.stringify({messages:[{content:'private-prompt-marker'}],model:'captured-chat',thinking_effort:'extended',conversation_id:'private-id'});
                const response=await fetch('/backend-api/f/conversation?test=1',{method:'POST',body});
                await fetch('/backend-api/conversation',{method:'POST',body});
                window.__testResult={calls:window.__calls,xhr:window.__xhr,response:await response.text(),body};
                """, 1);
        assertEquals(1, result.messages.size());
        String captured = result.messages.get(0);
        assertFalse(captured.contains("private-prompt-marker"));
        assertFalse(captured.contains("private-id"));
        CapturedRequestProfile profile = CapturedRequestProfile.parse(captured, RequestProfileEngine.Mode.CHAT, "chat-session");
        assertEquals("captured-chat", profile.operations.get(0).value);
        assertEquals(2, result.result.getJSONArray("calls").length());
        assertEquals(result.result.getString("body"), result.result.getJSONArray("calls").getJSONObject(0).getString("body"));
        assertEquals("native-response", result.result.getString("response"));
    }

    @Test public void requestObjectCapturePreservesNativeBodyAndWorkControls() throws Exception {
        Result result = run("""
                window.__schedulerProfileCapture.arm('request-session','work');
                const body=JSON.stringify({messages:[],model:'captured-work',thinking_effort:'max',conversation_origin:'tpp',service_tier:'standard'});
                const request=new Request('https://chatgpt.com/backend-api/conversation/',{method:'POST',body});
                await fetch(request);
                window.__testResult={calls:window.__calls,body};
                """, 1);
        CapturedRequestProfile profile = CapturedRequestProfile.parse(result.messages.get(0), RequestProfileEngine.Mode.WORK, "request-session");
        assertEquals("captured-work", profile.operations.get(0).value);
        assertEquals("tpp", profile.operations.get(2).value);
        assertEquals("standard", profile.operations.get(3).value);
        assertEquals(result.result.getString("body"), result.result.getJSONArray("calls").getJSONObject(0).getString("body"));
    }

    @Test public void xhrRoutesCancelAndRearmRemainOneShot() throws Exception {
        Result result = run("""
                const e=window.__schedulerProfileCapture;
                const body=JSON.stringify({messages:[],model:'xhr-model'});
                e.arm('cancelled','chat');e.cancel();
                await fetch('/backend-api/conversation',{method:'POST',body});
                e.arm('xhr-session','work');
                await fetch('https://example.com/backend-api/conversation',{method:'POST',body});
                await fetch('/backend-api/conversation/extra',{method:'POST',body});
                await fetch('/backend-api/conversation//',{method:'POST',body});
                const xhr=new XMLHttpRequest();xhr.open('POST','/backend-api/f/conversation/');xhr.send(body);
                const second=new XMLHttpRequest();second.open('POST','/backend-api/conversation');second.send(body);
                window.__testResult={calls:window.__calls,xhr:window.__xhr,body};
                """, 1);
        assertEquals(1, result.messages.size());
        CapturedRequestProfile profile = CapturedRequestProfile.parse(result.messages.get(0), RequestProfileEngine.Mode.WORK, "xhr-session");
        assertEquals("xhr-model", profile.operations.get(0).value);
        assertEquals(RequestProfileEngine.OperationKind.REMOVE, profile.operations.get(1).kind);
        assertEquals(4, result.result.getJSONArray("calls").length());
        assertEquals(2, result.result.getJSONArray("xhr").length());
        assertEquals(result.result.getString("body"), result.result.getJSONArray("xhr").getJSONObject(0).getString("body"));
    }

    @Test public void unsupportedControlReportsOnlySanitizedErrorAndDoesNotBlockNativeSend() throws Exception {
        Result result = run("""
                window.__schedulerProfileCapture.arm('invalid-session','chat');
                const body=JSON.stringify({messages:[{content:'private-error-marker'}],model:{unexpected:'object'}});
                await fetch('/backend-api/conversation',{method:'POST',body});
                window.__testResult={calls:window.__calls,body};
                """, 1);
        JSONObject error = new JSONObject(result.messages.get(0));
        assertEquals("control_value_unsupported", error.getString("error"));
        assertEquals(3, error.length());
        assertFalse(error.toString().contains("private-error-marker"));
        assertEquals(result.result.getString("body"), result.result.getJSONArray("calls").getJSONObject(0).getString("body"));
    }

    @Test public void staleAsyncCaptureCannotReplaceNewSession() throws Exception {
        Result result = run("""
                const e=window.__schedulerProfileCapture;
                e.arm('old-session','chat');
                const old=new Request('https://chatgpt.com/backend-api/conversation',{method:'POST',body:JSON.stringify({messages:[],model:'old-model'})});
                const pending=fetch(old);
                e.cancel();e.arm('new-session','chat');
                await fetch('/backend-api/conversation',{method:'POST',body:JSON.stringify({messages:[],model:'new-model'})});
                await pending;
                window.__testResult={calls:window.__calls};
                """, 1);
        assertEquals(1, result.messages.size());
        CapturedRequestProfile profile = CapturedRequestProfile.parse(result.messages.get(0), RequestProfileEngine.Mode.CHAT, "new-session");
        assertEquals("new-model", profile.operations.get(0).value);
        assertEquals(2, result.result.getJSONArray("calls").length());
    }

    @Test public void capturedRegistryImmediatelyResolvesSchedulesAndSurvivesReload() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        android.content.SharedPreferences prefs = context.getSharedPreferences("scheduler_request_profile_registry_v1", Context.MODE_PRIVATE);
        String beforeChat = prefs.getString("chat_profiles", null), beforeWork = prefs.getString("work_profiles", null);
        try {
            Result result = run("""
                    window.__schedulerProfileCapture.arm('registry-session','work');
                    await fetch('/backend-api/conversation',{method:'POST',body:JSON.stringify({messages:[],model:'captured-registry-model',thinking_effort:'ultra',conversation_origin:'tpp'})});
                    window.__testResult={calls:window.__calls};
                    """, 1);
            CapturedRequestProfile captured = CapturedRequestProfile.parse(result.messages.get(0), RequestProfileEngine.Mode.WORK, "registry-session");
            RequestProfileRegistry registry = new RequestProfileRegistry(context);
            List<String> existingChat = new ArrayList<>(registry.chatReasonings());
            String portable = captured.portableJson("capture-fixture", "ultra", "fixture");
            registry.importWork(portable);
            RequestProfileRegistry reloaded = new RequestProfileRegistry(context);
            assertTrue(reloaded.workModels().contains("capture-fixture"));
            assertTrue(reloaded.workReasoningsForModel("capture-fixture").contains("ultra"));
            assertEquals(existingChat, reloaded.chatReasonings());
            Schedule schedule = new Schedule();
            schedule.targetType = "general";schedule.experience = "work";
            schedule.workModel = "capture-fixture";schedule.reasoningEffort = "ultra";
            reloaded.attach(schedule);
            assertTrue(captured.matches(RequestProfileEngine.forSchedule(schedule)));
            assertNotNull(captured.findDuplicate(reloaded));
            int count = reloaded.count(RequestProfileEngine.Mode.WORK);
            assertEquals(1, reloaded.importWork(portable).unchanged);
            assertEquals(count, reloaded.count(RequestProfileEngine.Mode.WORK));
            schedule.targetType = "existing";
            assertNull(RequestProfileEngine.forSchedule(schedule));
        } finally {
            android.content.SharedPreferences.Editor editor = prefs.edit();
            if (beforeChat == null) editor.remove("chat_profiles"); else editor.putString("chat_profiles", beforeChat);
            if (beforeWork == null) editor.remove("work_profiles"); else editor.putString("work_profiles", beforeWork);
            assertTrue(editor.commit());
        }
    }

    private static final class Result {
        final JSONObject result;
        final List<String> messages;
        Result(JSONObject result, List<String> messages) { this.result = result;this.messages = messages; }
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private static Result run(String scenario, int expectedMessages) throws Exception {
        assertTrue("WebView capture prerequisites", RequestProfileCaptureScript.supported());
        CountDownLatch done = new CountDownLatch(1), captured = new CountDownLatch(expectedMessages);
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<WebView> viewRef = new AtomicReference<>();
        List<String> messages = Collections.synchronizedList(new ArrayList<>());
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                WebView view = new WebView(ApplicationProvider.getApplicationContext());
                viewRef.set(view);
                view.getSettings().setJavaScriptEnabled(true);
                WebViewCompat.addWebMessageListener(view, RequestProfileCaptureScript.BRIDGE, RequestProfileCaptureScript.ORIGINS,
                        (sender, message, origin, mainFrame, reply) -> {
                            if (!mainFrame || !CapturedRequestProfile.trustedPage(origin.toString())) return;
                            messages.add(message.getData());captured.countDown();
                        });
                WebViewCompat.addDocumentStartJavaScript(view, HARNESS + RequestProfileCaptureScript.documentStartScript(), RequestProfileCaptureScript.ORIGINS);
                view.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(WebView v, String url) { poll(v, result, done, 0); }
                });
                String js = "(async()=>{" + scenario + "window.__testDone=true;})().catch(e=>{window.__testResult={error:String(e)};window.__testDone=true;});";
                view.loadDataWithBaseURL("https://chatgpt.com/", "<!doctype html><html><body><script>" + js + "</script></body></html>", "text/html", "UTF-8", null);
            });
            assertTrue("scenario result timeout", done.await(12, TimeUnit.SECONDS));
            assertNotNull(result.get());
            JSONObject value = new JSONObject(result.get());
            assertFalse(value.toString(), value.has("error"));
            assertTrue("capture bridge timeout", captured.await(5, TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            return new Result(value, new ArrayList<>(messages));
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                WebView view = viewRef.get();
                if (view != null) view.destroy();
            });
        }
    }

    private static void poll(WebView view, AtomicReference<String> result, CountDownLatch done, int attempt) {
        view.evaluateJavascript("window.__testDone===true?JSON.stringify(window.__testResult):null", raw -> {
            if (raw != null && !"null".equals(raw)) {
                try { result.set(String.valueOf(new JSONTokener(raw).nextValue())); }
                catch (Exception ignored) {}
                done.countDown();
            } else if (attempt >= 100) done.countDown();
            else new Handler(Looper.getMainLooper()).postDelayed(() -> poll(view, result, done, attempt + 1), 100L);
        });
    }
}
