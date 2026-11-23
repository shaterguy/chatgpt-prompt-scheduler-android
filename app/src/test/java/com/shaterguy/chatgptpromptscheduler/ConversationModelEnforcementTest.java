package com.shaterguy.chatgptpromptscheduler;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static com.shaterguy.chatgptpromptscheduler.RequestProfileEngine.*;

public final class ConversationModelEnforcementTest {
    private TargetProfile profile() {
        return new TargetProfile(Mode.CHAT, "", "xhigh", List.of(
                Operation.set("model", "gpt-6-thinking"),
                Operation.set("thinking_effort", "max"),
                Operation.remove("conversation_origin"), Operation.remove("service_tier")));
    }

    private JSONObject result(String model, String effort, int count) throws Exception {
        return new JSONObject().put("diagnostics", new JSONObject()
                .put("requestProfile", new JSONObject().put("patchCount", count)
                        .put("lastPatch", new JSONObject().put("after", new JSONObject()
                                .put("model", model).put("thinking_effort", effort)))));
    }

    @Test public void actualModelAndEffortMustBothMatchBeforeFallbackCanSucceed() throws Exception {
        assertTrue(ConversationModelEnforcement.matches(result("gpt-6-thinking", "max", 1), profile()));
        assertFalse(ConversationModelEnforcement.matches(result("gpt-6-thinking", "standard", 1), profile()));
        assertFalse(ConversationModelEnforcement.matches(result("gpt-6", "max", 1), profile()));
        assertFalse(ConversationModelEnforcement.matches(result("gpt-6-thinking", "max", 0), profile()));
        assertFalse(ConversationModelEnforcement.matches(new JSONObject(), profile()));
        assertFalse(ConversationModelEnforcement.matches(null, profile()));
    }

    @Test public void fallbackConfiguresExistingConversationTransformerAndPreservesOtherFields() throws Exception {
        Schedule schedule = new Schedule();
        schedule.targetType = "general";
        schedule.targetUrl = "https://chatgpt.com/";
        schedule.experience = "chat";
        schedule.chatReasoning = "xhigh";
        schedule.resolvedRequestProfile = profile();
        schedule.requestProfileRegistryResolved = true;
        String setup = RequestProfileScript.activate(schedule, true);
        String ordinary = RequestProfileScript.activate(schedule, false);
        assertFalse(ordinary.contains("profileEngine.configure("));
        assertTrue(setup.contains("profileEngine.configure("));
        String js = """
                const assert=require('node:assert/strict');
                global.window=globalThis;
                global.location=new URL('https://chatgpt.com/');
                const calls=[];
                window.fetch=async(input,init)=>{
                  const req=new Request(input,init);
                  calls.push(JSON.parse(await req.text()));
                  return new Response('{}');
                };
                class Xhr {open(method,url){} send(body){calls.push(JSON.parse(body));}}
                global.XMLHttpRequest=Xhr;
                """ + RequestProfileScript.documentStartScript()
                + "(()=>{" + setup + "})();"
                + """
                  (async()=>{
                    const body={messages:[],model:'default-model',thinking_effort:'standard',
                      conversation_origin:'tpp',service_tier:'standard',
                      opaque:{id:'unchanged'}};
                    await fetch('https://chatgpt.com/backend-api/conversation',
                      {method:'POST',body:JSON.stringify(body)});
                    assert.equal(calls.length,1);
                    assert.equal(calls[0].model,'gpt-6-thinking');
                    assert.equal(calls[0].thinking_effort,'max');
                    assert.equal('conversation_origin' in calls[0],false);
                    assert.equal('service_tier' in calls[0],false);
                    assert.deepEqual(calls[0].opaque,{id:'unchanged'});
                    assert.equal(window.__chatgptPromptSchedulerRequestProfileEngine.diagnostics().patchCount,1);
                  })().catch(e=>{console.error(e);process.exitCode=1;});
                """;
        Process process = new ProcessBuilder("node","-e",js).redirectErrorStream(true).start();
        boolean done = process.waitFor(20, TimeUnit.SECONDS);
        if (!done) process.destroyForcibly();
        assertTrue("Fallback Node test timed out", done);
        assertEquals(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8),0,process.exitValue());
    }
}
