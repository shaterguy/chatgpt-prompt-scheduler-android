package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public final class NativeProfileEntryRegressionTest {
    @Test public void interactiveTargetInputsAndSubmitsOnceWithoutNativeRevalidation() throws Exception {
        Schedule schedule=new Schedule();
        schedule.targetType="project";
        schedule.targetUrl="https://chatgpt.com/g/g-p-fixture/project";
        String compose=AutomationScript.build(schedule,"interactive prompt","interactive-run",0);
        String script="""
            const assert=require('node:assert/strict');
            global.window=globalThis;global.location=new URL('https://chatgpt.com/g/g-p-fixture/project');
            const storage=new Map();let clicks=0;
            global.localStorage=global.sessionStorage={getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v)};
            Object.defineProperty(window,'__chatgptPromptSchedulerNativeProfile',{get(){throw Error('Unexpected revalidation');}});
            const send={dataset:{testid:'send-button'},disabled:false,getAttribute:()=>null,click:()=>clicks++};
            const composer={value:'',isConnected:true,offsetParent:{},tagName:'TEXTAREA',
              getAttribute:()=>null,focus(){document.activeElement=this;},dispatchEvent:()=>true,
              closest:()=>({querySelectorAll:()=>[send]})};
            global.document={readyState:'interactive',forms:[],activeElement:null,hasFocus:()=>true,
              querySelectorAll:s=>s==='textarea#prompt-textarea'?[composer]:[]};
            """ + "const compose="+RequestProfileScript.jsQuote(compose)+";"+"""
            assert.equal(JSON.parse(eval(compose)).detail,'DOM 입력 확정 대기');
            assert.equal(composer.value,'interactive prompt');assert.equal(clicks,0);
            assert.equal(JSON.parse(eval(compose)).status,'SUBMITTED');assert.equal(clicks,1);
            assert.equal(JSON.parse(eval(compose)).status,'SUBMITTED');assert.equal(clicks,1);
            """;
        run(script);
    }

    @Test public void composeAfterEntryDoesNotReadNativeModuleStateAgain() throws Exception {
        Schedule schedule = new Schedule();
        schedule.chatReasoning = "medium";
        schedule.resolvedRequestProfile = new RequestProfileEngine.TargetProfile(RequestProfileEngine.Mode.CHAT,"","medium",
                java.util.List.of(RequestProfileEngine.Operation.set("model","fixture-thinking"),
                        RequestProfileEngine.Operation.set("thinking_effort","standard"),
                        RequestProfileEngine.Operation.remove("conversation_origin"),
                        RequestProfileEngine.Operation.remove("service_tier")));
        schedule.requestProfileRegistryResolved = true;
        String activate = RequestProfileScript.activate(schedule);
        String script = """
            const assert=require('node:assert/strict');
            global.window=globalThis;
            Object.defineProperty(window,'__chatgptPromptSchedulerNativeProfile',{get(){throw Error('Unexpected target-page revalidation');}});
            const result=status=>status;
            """ + "const next=(()=>{" + activate + "return 'COMPOSE';})();assert.equal(next,'COMPOSE');";
        run(script);
    }

    private static void run(String script) throws Exception {
        Process process = new ProcessBuilder("node","-e",script).redirectErrorStream(true).start();
        boolean done=process.waitFor(20,TimeUnit.SECONDS);
        if(!done)process.destroyForcibly();
        assertTrue("Entry regression test timed out",done);
        assertEquals(new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8),0,process.exitValue());
    }
}
