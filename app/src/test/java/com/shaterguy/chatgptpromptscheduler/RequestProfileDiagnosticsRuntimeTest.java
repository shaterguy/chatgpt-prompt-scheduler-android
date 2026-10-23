package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public final class RequestProfileDiagnosticsRuntimeTest {
    private static final String NATIVE = """
            const assert=require('node:assert/strict');
            global.window=globalThis;
            global.location=new URL('https://chatgpt.com/');
            const calls=[];
            window.fetch=async(input,init)=>{const r=new Request(input,init);calls.push(JSON.parse(await r.text()));return new Response('{}');};
            class Xhr {open(method,url){this.method=method;this.url=url;}send(body){calls.push(JSON.parse(body));}}
            global.XMLHttpRequest=Xhr;
            """;
    private static final String CONFIGURE = """
            const engine=window.__chatgptPromptSchedulerRequestProfileEngine;
            const configure=()=>engine.configure('chat','','fixture.high',[
              ['set','model','fixture-model'],['set','thinking_effort','extended'],
              ['remove','conversation_origin'],['remove','service_tier']]);
            """;

    private static void run(String source) throws Exception {
        Process process = new ProcessBuilder("node", "-e", source).redirectErrorStream(true).start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        assertTrue("Node diagnostic test exceeded its bound", finished);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(output, 0, process.exitValue());
    }

    @Test public void receiptsSurviveReconfigurationAndHooksAreObservationOnly() throws Exception {
        run(NATIVE + RequestProfileScript.documentStartScript() + CONFIGURE + """
                (async()=>{
                  configure();
                  let d=engine.diagnostics();
                  assert.equal(d.configureCount,1);
                  assert.equal(d.configuredOperations.length,4);
                  assert.deepEqual(d.hooks,{fetch:true,xhrOpen:true,xhrSend:true});
                  const payload={messages:[],model:'native-model',thinking_effort:'standard',opaque:{keep:true}};
                  await fetch('https://chatgpt.com/backend-api/conversation',{method:'POST',body:JSON.stringify(payload)});
                  d=engine.diagnostics();
                  assert.equal(d.eligibleFetchCount,1);
                  assert.equal(d.eligibleXHRCount,0);
                  assert.equal(d.patchCount,1);
                  assert.equal(d.lastPatch.before.thinking_effort,'standard');
                  assert.equal(d.lastPatch.after.thinking_effort,'extended');
                  assert.equal(d.lastPatch.after.conversation_origin,null);
                  assert.equal(d.lastPatch.route,'/backend-api/conversation');
                  assert.equal(typeof d.lastPatch.at,'number');
                  assert.equal(calls[0].model,'fixture-model');
                  assert.deepEqual(calls[0].opaque,payload.opaque);
                  const first=d.lastPatch.at;
                  configure();
                  d=engine.diagnostics();
                  assert.equal(d.configureCount,2);
                  assert.equal(d.patchCount,1);
                  assert.equal(d.lastPatch.at,first);
                  assert.equal(d.lastPatch.after.thinking_effort,'extended');
                  const xhr=new XMLHttpRequest();xhr.open('POST','/backend-api/f/conversation');
                  xhr.send(JSON.stringify(payload));
                  d=engine.diagnostics();
                  assert.equal(d.eligibleXHRCount,1);
                  assert.equal(d.patchCount,2);
                  const request=new Request('https://chatgpt.com/backend-api/f/conversation',{method:'POST',body:JSON.stringify(payload)});
                  await fetch(request);
                  d=engine.diagnostics();
                  assert.equal(d.eligibleFetchCount,2);
                  assert.equal(d.patchCount,3);
                  window.fetch=async()=>new Response('{}');
                  XMLHttpRequest.prototype.open=function(){};
                  d=engine.diagnostics();
                  assert.equal(d.hooks.fetch,false);
                  assert.equal(d.hooks.xhrOpen,false);
                  assert.equal(d.hooks.xhrSend,true);
                  assert.equal(engine.target().ready,true);
                  assert.equal(d.patchCount,3);
                })().catch(e=>{console.error(e);process.exitCode=1;});
                """);
    }

    @Test public void receiptsExcludePayloadHeadersQueryAndNestedControlObjects() throws Exception {
        run(NATIVE + RequestProfileScript.documentStartScript() + CONFIGURE + """
                (async()=>{
                  configure();
                  const payload={messages:[{content:'message-secret'}],parent_message_id:'parent-secret',
                    model:{nested:'control-object-secret'},thinking_effort:['control-array-secret'],
                    conversation_origin:'x'.repeat(300),service_tier:false};
                  await fetch('https://chatgpt.com/backend-api/f/conversation?token=query-secret',{
                    method:'POST',headers:{Authorization:'Bearer header-secret'},body:JSON.stringify(payload)});
                  const d=engine.diagnostics(),text=JSON.stringify(d);
                  for(const secret of ['message-secret','parent-secret','control-object-secret','control-array-secret','query-secret','header-secret','Authorization','messages','parent_message_id'])
                    assert.equal(text.includes(secret),false,secret);
                  assert.equal(d.lastPatch.before.model,'[object]');
                  assert.equal(d.lastPatch.before.thinking_effort,'[array]');
                  assert.equal(d.lastPatch.before.conversation_origin.length,128);
                  assert.equal(d.lastPatch.before.service_tier,false);
                  assert.deepEqual(Object.keys(d.lastPatch.before).sort(),['conversation_origin','model','service_tier','thinking_effort']);
                  assert.equal(d.lastPatch.route,'/backend-api/f/conversation');
                  d.lastPatch.after.model='external-mutation';
                  assert.equal(engine.diagnostics().lastPatch.after.model,'fixture-model');
                })().catch(e=>{console.error(e);process.exitCode=1;});
                """);
    }

    @Test public void composeAndBothVerificationScriptsExposeLaterReceiptWithoutChangingStatus() throws Exception {
        Schedule schedule = new Schedule();
        schedule.targetType = "general";
        schedule.targetUrl = "https://chatgpt.com/";
        String compose = AutomationScript.build(schedule, "fixture", "diagnostic-run", 0);
        String legacyVerify = AutomationScript.verify(schedule, "fixture");
        String verify = ConversationVerificationScript.build(schedule, "fixture");
        run(NATIVE + RequestProfileScript.documentStartScript() + CONFIGURE
                + "const compose=" + org.json.JSONObject.quote(compose) + ";"
                + "const legacyVerify=" + org.json.JSONObject.quote(legacyVerify) + ";"
                + "const verify=" + org.json.JSONObject.quote(verify) + ";"
                + """
                (async()=>{
                  configure();
                  await fetch('https://chatgpt.com/backend-api/conversation',{method:'POST',body:JSON.stringify({messages:[],model:'native'})});
                  global.location=new URL('https://chatgpt.com/c/fixture-conversation');
                  global.document={querySelectorAll:()=>[],readyState:'complete',forms:[],activeElement:null};
                  global.localStorage={getItem:()=>null};global.sessionStorage={getItem:()=>null};
                  for(const script of [compose,legacyVerify,verify]){
                    const result=JSON.parse(eval(script));
                    assert.equal(result.status,'RETRY');
                    assert.equal(result.diagnostics.requestProfile.patchCount,1);
                    assert.equal(result.diagnostics.requestProfile.lastPatch.after.model,'fixture-model');
                  }
                  assert.equal(engine.diagnostics().configureCount,1);
                  Object.defineProperty(window,'__chatgptPromptSchedulerRequestProfileEngine',{configurable:true,get(){throw new Error('secret-diagnostic-error');}});
                  for(const script of [compose,legacyVerify,verify]){
                    const result=JSON.parse(eval(script));
                    assert.equal(result.status,'RETRY');
                    assert.deepEqual(result.diagnostics.requestProfile,{unavailable:true});
                    assert.equal(JSON.stringify(result).includes('secret-diagnostic-error'),false);
                  }
                })().catch(e=>{console.error(e);process.exitCode=1;});
                """);
    }
}
