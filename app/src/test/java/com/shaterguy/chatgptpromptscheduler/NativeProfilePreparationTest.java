package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static com.shaterguy.chatgptpromptscheduler.RequestProfileEngine.*;

public final class NativeProfilePreparationTest {
    private static final String ENV = """
        const assert=require('node:assert/strict');
        const atoms=new Map(),cookies=new Map(),events=[];
        let reject=false,remoteModel='fixture-work',remoteEffort='ultra';
        const env={origin:'https://chatgpt.com',
          atoms:{k:(k,f)=>atoms.has(k)?atoms.get(k):f,u:(k,v)=>atoms.set(k,v),f:()=>events.push('publish')},
          cookie:{a:k=>cookies.get(k),b:(k,v)=>{cookies.set(k,v);return true;}},
          flush:async()=>events.push('durable'),
          api:{safePatch:async(path,args)=>{events.push({path,query:args.parameters.query});return {success:!reject};},
            safeGet:async()=>({settings:{tpp_last_used_model_config:{slugs:{web:remoteModel},juices:{web:{[remoteModel]:remoteEffort}}}}})}};
        """;

    @Test public void profileUsesCanonicalOperationsRatherThanDisplayAliases() {
        TargetProfile p = new TargetProfile(Mode.CHAT, "", "future-display", List.of(
                Operation.set("model", "future-canonical-model"), Operation.set("thinking_effort", "extended"),
                Operation.remove("conversation_origin"), Operation.remove("service_tier")));
        String json = NativeProfilePreparationScript.profileJson(p);
        assertTrue(json.contains("future-canonical-model"));
        assertTrue(json.contains("extended"));
        assertFalse(json.contains("future-display"));
    }

    @Test public void instantAndProClearPreviouslySelectedThinkingEffort() throws Exception {
        run(ENV + NativeProfilePreparationScript.preferenceFunction() + """
            (async()=>{
              for(const model of ['fixture-instant','fixture-pro']){
                atoms.set('chatgpt-last-selected-model-v1',{slug:'prior',thinkingEffort:'max'});
                const p={mode:'chat',model,thinking_effort:null,effortOperation:'REMOVE'};
                const r=await applyPreference(env,p);
                assert.equal(r.nativeMatches,true);
                assert.deepEqual(events.filter(x=>x.query).at(-1).query,{model_slug:model});
                assert.deepEqual(atoms.get('chatgpt-last-selected-home-model-v1'),{slug:model,thinkingEffort:null});
              }
              assert.deepEqual(events.slice(-2),['publish','durable']);
            })().catch(e=>{console.error(e);process.exitCode=1;});
            """);
    }

    @Test public void workUltraPreservesValidLastStartedConfigAndRejectsServerMismatch() throws Exception {
        run(ENV + NativeProfilePreparationScript.preferenceFunction() + """
            (async()=>{
              const prior={modelSettings:{defaultModelSlug:'keep'},lastStartedModelConfig:{modelSlug:'prior',thinkingEffort:'standard'}};
              cookies.set('oai-tpp-model-settings',JSON.stringify(prior));
              const p={mode:'work',model:'fixture-work',thinking_effort:'ultra',effortOperation:'SET'};
              const r=await applyPreference(env,p),c=JSON.parse(cookies.get('oai-tpp-model-settings'));
              assert.equal(r.serverReadback.matches,true);assert.equal(c.thinkingEffort,'ultra');
              assert.equal(c.modelSettings.defaultModelSlug,'keep');
              assert.deepEqual(c.lastStartedModelConfig,prior.lastStartedModelConfig);
              remoteEffort='standard';await assert.rejects(applyPreference(env,p),/SERVER_READBACK_MISMATCH/);
            })().catch(e=>{console.error(e);process.exitCode=1;});
            """);
    }

    @Test public void acknowledgementAndPersistenceFailuresPreventPreparedReceipt() throws Exception {
        run(ENV + NativeProfilePreparationScript.preferenceFunction() + """
            (async()=>{
              const p={mode:'chat',model:'fixture-model',thinking_effort:'extended',effortOperation:'SET'};
              reject=true;await assert.rejects(applyPreference(env,p));assert.equal(atoms.size,0);
              reject=false;env.flush=async()=>{throw Error('durable-failure');};
              let prepared=false;await assert.rejects(applyPreference(env,p).then(()=>prepared=true));assert.equal(prepared,false);
            })().catch(e=>{console.error(e);process.exitCode=1;});
            """);
    }

    @Test public void preparedEntryNeverIncludesModelOrEffortParameters() {
        assertEquals("https://chatgpt.com/?surface=chat&disable_auto_send=1", NativeProfilePreparationScript.homeUrl(Mode.CHAT));
        assertEquals("https://chatgpt.com/?surface=work&disable_auto_send=1", NativeProfilePreparationScript.homeUrl(Mode.WORK));
        Schedule s = new Schedule();s.targetType="project";s.targetUrl="https://chatgpt.com/g/g-p-fixture/project";
        assertEquals(s.targetUrl, NativeProfilePreparationScript.targetUrl(s, Mode.WORK));
    }

    @Test public void preparationPollingPatchesOnceAndKeepsCompletedReceipt() throws Exception {
        TargetProfile p = chatProfile();
        // Official CDN modules cannot load in Node; provide their API boundary, keeping the full polling script.
        String prepare = NativeProfilePreparationScript.build(p,"run-fixture")
                .replace("await import(runtime)", "await Promise.resolve({__webpack_require__:nativeRequire})");
        run(ENV + browserBoundary() + "const prepare=" + RequestProfileScript.jsQuote(prepare) + ";" + """
            (async()=>{
              const tick=()=>new Promise(r=>setImmediate(r));
              let release,patchCount=0;
              env.api.safePatch=async()=>{patchCount++;await new Promise(r=>release=r);return {success:true};};
              const poll=source=>JSON.parse(eval(source));
              assert.equal(poll(prepare).status,'RETRY');await tick();
              for(let i=0;i<10;i++)assert.equal(poll(prepare).status,'RETRY');
              assert.equal(patchCount,1);release();await tick();
              assert.equal(poll(prepare).status,'PREPARED');assert.equal(patchCount,1);
              for(let i=0;i<10;i++)assert.equal(poll(prepare).status,'PREPARED');
              assert.equal(patchCount,1);
            })().catch(e=>{console.error(e);process.exitCode=1;});
            """);
    }

    @Test public void priorSendMarkerSkipsAllPreferenceWrites() throws Exception {
        String script=NativeProfilePreparationScript.build(chatProfile(),"run-fixture");
        run(ENV + browserBoundary() + "const script=" + RequestProfileScript.jsQuote(script) + ";" + """
            window['chatgpt-prompt-scheduler:submit:run-fixture']='already-clicked';
            assert.equal(JSON.parse(eval(script)).status,'SUBMITTED');
            assert.equal(events.length,0);assert.equal(window.__chatgptPromptSchedulerNativeProfile,undefined);
            """);
    }

    private static TargetProfile chatProfile() {
        return new TargetProfile(Mode.CHAT,"","fixture-signal",List.of(
                Operation.set("model","fixture-model"),Operation.set("thinking_effort","extended"),
                Operation.remove("conversation_origin"),Operation.remove("service_tier")));
    }

    private static String browserBoundary() {
        return """
            global.window=globalThis;global.location=new URL('https://chatgpt.com/');
            global.localStorage={getItem:()=>null};global.sessionStorage={getItem:()=>null};
            global.document={readyState:'complete',querySelectorAll:s=>s.includes('script[src]')?[]:[{isConnected:true,offsetParent:{}}]};
            Object.defineProperty(globalThis,'performance',{value:{getEntriesByType:()=>[{name:'https://chatgpt.com/cdn/assets/633146.123abc.js'}]},configurable:true});
            const nativeRequire=id=>({'Kwur':{Request:env.api},'kpIY':env.atoms,'ZhRn':env.cookie,'YAwS':{flushBrowserHostStorageWrites:env.flush}}[id]);
            """;
    }

    private static void run(String source) throws Exception {
        Process process = new ProcessBuilder("node", "-e", source).redirectErrorStream(true).start();
        boolean done=process.waitFor(20, TimeUnit.SECONDS);
        if(!done)process.destroyForcibly();
        assertTrue("Native preference test timed out",done);
        assertEquals(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8),0,process.exitValue());
    }
}
