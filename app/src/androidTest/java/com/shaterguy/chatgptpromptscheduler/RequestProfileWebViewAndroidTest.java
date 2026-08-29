package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class RequestProfileWebViewAndroidTest {
    private static final String BASE_URL = "https://chatgpt.com";

    @Test public void documentStartEnginePreexistsAndPatchesFetchAndXhrWithoutNetwork() throws Exception {
        String scenario = """
                window.__testResult={preexisting:
                  window.__chatgptPromptSchedulerRequestProfileEngine?.version==='scheduler-request-profile-engine-v1'};
                const engine=window.__chatgptPromptSchedulerRequestProfileEngine;
                engine.begin('work');
                engine.setWorkModel('terra');
                engine.setWorkReasoning('high');
                const payload={action:'next',messages:[{author:'user',content:{content_type:'text',parts:['opaque']}}],
                  conversation_id:'conversation-opaque',parent_message_id:'parent-opaque',
                  model:'native-model',thinking_effort:'native-effort',conversation_origin:'native-origin',
                  service_tier:'native-tier',untouched:{nested:['value']}};
                const xhr=new XMLHttpRequest();
                xhr.open('POST','/backend-api/f/conversation/?query=1');
                xhr.send(JSON.stringify(payload));
                Promise.all([
                  fetch('/backend-api/conversation?query=1',{method:'POST',body:JSON.stringify(payload)}),
                  fetch('/backend-api/conversation/extra',{method:'POST',body:JSON.stringify(payload)})
                ]).then(()=>{
                  window.__testResult.fetchCalls=window.__nativeFetchCalls;
                  window.__testResult.xhrCalls=window.__nativeXhrCalls;
                  window.__testDone=true;
                }).catch(()=>{
                  window.__testResult.unexpectedFailure=true;
                  window.__testDone=true;
                });
                """;
        JSONObject result = runScenario(scenario);
        assertTrue(result.getBoolean("preexisting"));
        assertFalse(result.optBoolean("unexpectedFailure"));

        JSONArray fetchCalls = result.getJSONArray("fetchCalls");
        assertEquals(2, fetchCalls.length());
        JSONObject target = callForPath(fetchCalls, "/backend-api/conversation?query=1");
        JSONObject passthrough = callForPath(fetchCalls, "/backend-api/conversation/extra");
        JSONObject patchedBody = new JSONObject(target.getString("body"));
        JSONObject nativeBody = new JSONObject(passthrough.getString("body"));
        assertEquals("gpt-5.6-terra-wm", patchedBody.getString("model"));
        assertEquals("extended", patchedBody.getString("thinking_effort"));
        assertEquals("tpp", patchedBody.getString("conversation_origin"));
        assertEquals("standard", patchedBody.getString("service_tier"));
        assertEquals("conversation-opaque", patchedBody.getString("conversation_id"));
        assertEquals("parent-opaque", patchedBody.getString("parent_message_id"));
        assertEquals("value", patchedBody.getJSONObject("untouched").getJSONArray("nested").getString(0));
        assertEquals("opaque", patchedBody.getJSONArray("messages").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getString(0));
        assertEquals("native-model", nativeBody.getString("model"));
        assertEquals("native-effort", nativeBody.getString("thinking_effort"));

        JSONArray xhrCalls = result.getJSONArray("xhrCalls");
        assertEquals(1, xhrCalls.length());
        JSONObject xhrBody = new JSONObject(xhrCalls.getJSONObject(0).getString("body"));
        assertEquals("gpt-5.6-terra-wm", xhrBody.getString("model"));
        assertEquals("extended", xhrBody.getString("thinking_effort"));
    }

    @Test public void targetNotReadyMalformedSchemaAndNonTextBlockWithoutNativeFallback() throws Exception {
        String scenario = """
                window.__testResult={preexisting:
                  window.__chatgptPromptSchedulerRequestProfileEngine?.version==='scheduler-request-profile-engine-v1'};
                const engine=window.__chatgptPromptSchedulerRequestProfileEngine;
                const blocked=async body=>{try{
                  await fetch('/backend-api/conversation',{method:'POST',body});
                  return false;
                }catch(_){return true;}};
                (async()=>{
                  const valid=JSON.stringify({action:'next',messages:[]});
                  window.__testResult.notReady=await blocked(valid);
                  engine.begin('chat');
                  engine.setChatReasoning('instant');
                  window.__testResult.malformed=await blocked('{');
                  window.__testResult.unknownSchema=await blocked(JSON.stringify({action:'next'}));
                  let nonText=false;
                  try{
                    const xhr=new XMLHttpRequest();
                    xhr.open('POST','/backend-api/f/conversation');
                    xhr.send(new Blob(['opaque']));
                  }catch(_){nonText=true;}
                  window.__testResult.nonText=nonText;
                  await Promise.all([
                    fetch('/backend-api/conversation//',{method:'POST',body:'{'}),
                    fetch('https://example.com/backend-api/conversation',{method:'POST',body:'{'})
                  ]);
                  window.__testResult.fetchCalls=window.__nativeFetchCalls;
                  window.__testResult.xhrCalls=window.__nativeXhrCalls;
                  window.__testDone=true;
                })().catch(()=>{
                  window.__testResult.unexpectedFailure=true;
                  window.__testDone=true;
                });
                """;
        JSONObject result = runScenario(scenario);
        assertTrue(result.getBoolean("preexisting"));
        assertTrue(result.getBoolean("notReady"));
        assertTrue(result.getBoolean("malformed"));
        assertTrue(result.getBoolean("unknownSchema"));
        assertTrue(result.getBoolean("nonText"));
        assertFalse(result.optBoolean("unexpectedFailure"));
        assertEquals(2, result.getJSONArray("fetchCalls").length());
        assertEquals(0, result.getJSONArray("xhrCalls").length());
    }

    private static JSONObject callForPath(JSONArray calls, String path) throws Exception {
        for (int i = 0; i < calls.length(); i++) {
            JSONObject call = calls.getJSONObject(i);
            if (call.getString("url").contains(path)) return call;
        }
        fail("missing call for " + path);
        return new JSONObject();
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private static JSONObject runScenario(String scenario) throws Exception {
        assertTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT));
        Context context = ApplicationProvider.getApplicationContext();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<WebView> webViewRef = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            WebView webView = new WebView(context);
            webViewRef.set(webView);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            String documentStart = networkHarness() + RequestProfileScript.documentStartScript();
            WebViewCompat.addDocumentStartJavaScript(webView, documentStart,
                    Set.of("https://chatgpt.com", "https://www.chatgpt.com"));
            webView.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) {
                    pollResult(view, result, done, 0);
                }
            });
            String html = "<!doctype html><html><body><script>" + scenario + "</script></body></html>";
            webView.loadDataWithBaseURL(BASE_URL, html, "text/html", "UTF-8", null);
        });
        assertTrue("scenario timed out", done.await(12, TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            WebView view = webViewRef.get();
            if (view != null) view.destroy();
        });
        assertNotNull(result.get());
        return new JSONObject(result.get());
    }

    private static void pollResult(WebView webView, AtomicReference<String> result,
                                   CountDownLatch done, int attempt) {
        webView.evaluateJavascript(
                "window.__testDone===true?JSON.stringify(window.__testResult):null",
                raw -> {
                    if (raw != null && !"null".equals(raw)) {
                        try {
                            Object decoded = new JSONTokener(raw).nextValue();
                            result.set(decoded instanceof String ? (String) decoded : String.valueOf(decoded));
                            done.countDown();
                            return;
                        } catch (Exception ignored) {
                            done.countDown();
                            return;
                        }
                    }
                    if (attempt >= 100) {
                        done.countDown();
                        return;
                    }
                    new Handler(Looper.getMainLooper()).postDelayed(
                            () -> pollResult(webView, result, done, attempt + 1), 100L);
                });
    }

    private static String networkHarness() {
        return """
                window.__testDone=false;
                window.__nativeFetchCalls=[];
                window.__nativeXhrCalls=[];
                window.fetch=async function(input,init){
                  const request=input instanceof Request?input:new Request(input,init);
                  const body=await request.clone().text();
                  window.__nativeFetchCalls.push({url:request.url,method:request.method,body});
                  return new Response('{}',{status:200,headers:{'Content-Type':'application/json'}});
                };
                function FakeXMLHttpRequest(){this.method='';this.url='';}
                FakeXMLHttpRequest.prototype.open=function(method,url){this.method=String(method);this.url=String(url);};
                FakeXMLHttpRequest.prototype.send=function(body){
                  window.__nativeXhrCalls.push({method:this.method,url:new URL(this.url,location.href).href,
                    body:typeof body==='string'?body:'NON_TEXT'});
                };
                window.XMLHttpRequest=FakeXMLHttpRequest;
                """;
    }
}
