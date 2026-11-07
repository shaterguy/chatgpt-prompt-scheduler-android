package com.shaterguy.chatgptpromptscheduler;

import org.json.JSONObject;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/** Runs the production script and recovery policy together without network access. */
public final class ComposerRecoveryRegressionTest {
    private static final long DEADLINE = 90_000L;

    @Test(timeout = 20_000) public void stalledDocumentsRecoverAndLateComposerSubmitsExactlyOnce() throws Exception {
        ComposerRecoveryPolicy policy = new ComposerRecoveryPolicy();
        policy.reset();
        try (Page page = new Page()) {
            long now = 0L;
            int reentries = 0;
            for (int i = 0; i < 60; i++) {
                JSONObject result = page.evaluate();
                assertEquals("RETRY", result.getString("status"));
                assertEquals("입력창 대기", result.getString("detail"));
                JSONObject diagnostics = result.getJSONObject("diagnostics");
                assertEquals("complete", diagnostics.getString("readyState"));
                assertEquals(0, diagnostics.getInt("forms"));
                assertTrue(diagnostics.getBoolean("composerMissing"));
                assertFalse(diagnostics.getBoolean("inputStarted"));
                policy.observeInputStarted(diagnostics.getBoolean("inputStarted"));
                ComposerRecoveryPolicy.Decision decision = policy.onRetry(true, false, now, DEADLINE);
                assertNotEquals(ComposerRecoveryPolicy.Decision.EXHAUSTED, decision);
                assertNotEquals(ComposerRecoveryPolicy.Decision.TIMED_OUT, decision);
                if (decision == ComposerRecoveryPolicy.Decision.REENTER) {
                    reentries++;
                    page.navigate("https://chatgpt.com/g/g-p-fixture/project");
                }
                now += 1_200L;
            }
            assertEquals(2, reentries);
            assertEquals(2, policy.recoveryAttempts());
            assertEquals("Waiting must not consume DOM retries", 0, policy.retryAttempts());

            page.setComposer("");
            JSONObject input = page.evaluate();
            assertEquals("RETRY", input.getString("status"));
            assertEquals("input", input.getJSONObject("diagnostics").getString("phase"));
            policy.observeInputStarted(input.getJSONObject("diagnostics").getBoolean("inputStarted"));
            assertTrue(policy.inputStarted());
            assertEquals(ComposerRecoveryPolicy.Decision.WAIT,
                    policy.onRetry(false, false, now, DEADLINE));
            assertEquals(1, policy.retryAttempts());
            assertEquals(0, page.clicks);

            assertEquals("SUBMITTED", page.evaluate().getString("status"));
            assertEquals(1, page.clicks);
            page.navigate("https://chatgpt.com/g/g-p-fixture/project");
            JSONObject recovered = page.evaluate();
            assertEquals("SUBMITTED", recovered.getString("status"));
            assertTrue(recovered.getJSONObject("diagnostics").getBoolean("recoveredAfterNavigation"));
            assertEquals("The shared submit marker must prevent a second click", 1, page.clicks);
        }
    }

    @Test(timeout = 20_000) public void clearAndInputLatchSurviveComposerDisappearance() throws Exception {
        for (String initial : new String[]{"existing draft", ""}) {
            ComposerRecoveryPolicy policy = new ComposerRecoveryPolicy();
            policy.reset();
            try (Page page = new Page()) {
                page.setComposer(initial);
                JSONObject first = page.evaluate();
                assertEquals("RETRY", first.getString("status"));
                JSONObject diagnostics = first.getJSONObject("diagnostics");
                assertEquals(initial.isEmpty() ? "input" : "clear", diagnostics.getString("phase"));
                assertTrue(diagnostics.getBoolean("inputStarted"));
                policy.observeInputStarted(diagnostics.getBoolean("inputStarted"));
                policy.onRetry(false, false, 0L, DEADLINE);

                page.removeComposer();
                JSONObject absent = page.evaluate();
                assertEquals("입력창 대기", absent.getString("detail"));
                assertTrue(absent.getJSONObject("diagnostics").getBoolean("inputStarted"));
                policy.observeInputStarted(absent.getJSONObject("diagnostics").getBoolean("inputStarted"));
                assertEquals(ComposerRecoveryPolicy.Decision.WAIT, policy.onRetry(true, false, 1_000L, DEADLINE));
                assertEquals(ComposerRecoveryPolicy.Decision.WAIT, policy.onRetry(true, false, 30_000L, DEADLINE));
                assertEquals(0, policy.recoveryAttempts());
                assertEquals(0, page.clicks);
                assertEquals(ComposerRecoveryPolicy.Decision.TIMED_OUT,
                        policy.onRetry(true, false, DEADLINE, DEADLINE));
            }
        }
    }

    @Test(timeout = 20_000) public void inputStartPersistsWhenNavigationDiscardsNativeCallback() throws Exception {
        for (String initial : new String[]{"existing draft", ""}) {
            ComposerRecoveryPolicy policy = new ComposerRecoveryPolicy();
            policy.reset();
            try (Page page = new Page()) {
                page.setComposer(initial);
                JSONObject oldDocumentResult = page.evaluate();
                assertTrue(oldDocumentResult.getJSONObject("diagnostics").getBoolean("inputStarted"));
                // Deliberately do not deliver this result to the native policy: navigation fences it out.
                assertFalse(policy.inputStarted());
                page.navigate("https://chatgpt.com/g/g-p-fixture/project");

                JSONObject absent = page.evaluate();
                assertEquals("RETRY", absent.getString("status"));
                assertEquals("입력창 대기", absent.getString("detail"));
                assertTrue("Input-start evidence must survive a lost callback and a new document",
                        absent.getJSONObject("diagnostics").getBoolean("inputStarted"));
                policy.observeInputStarted(absent.getJSONObject("diagnostics").getBoolean("inputStarted"));
                assertEquals(ComposerRecoveryPolicy.Decision.WAIT,
                        policy.onRetry(true, false, 1_000L, DEADLINE));
                assertEquals(ComposerRecoveryPolicy.Decision.WAIT,
                        policy.onRetry(true, false, 30_000L, DEADLINE));
                assertEquals(0, policy.recoveryAttempts());
                assertEquals(0, page.clicks);
            }
        }
    }

    @Test(timeout = 20_000) public void unavailableOrUnreliableStorageStopsBeforeAnyDomMutation() throws Exception {
        for (String mode : new String[]{"throw", "discard"}) {
            for (String initial : new String[]{"existing draft", ""}) {
                try (Page page = new Page()) {
                    page.setComposer(initial);
                    page.command(new JSONObject().put("action", "storage").put("value", mode));
                    JSONObject result = page.evaluate();
                    assertEquals("COMPOSER_INPUT_STATE_FAILED", result.getString("status"));
                    assertEquals("A failed input marker must leave the draft untouched", initial,
                            page.command(new JSONObject().put("action", "snapshot")).getString("composerValue"));
                    assertEquals(0, page.clicks);
                }
            }
        }
    }

    @Test(timeout = 20_000) public void twoReentriesDoNotExtendOriginalDeadline() throws Exception {
        ComposerRecoveryPolicy policy = new ComposerRecoveryPolicy();
        policy.reset();
        try (Page page = new Page()) {
            for (long now : new long[]{0L, 15_000L, 16_000L, 31_000L, 32_000L, 88_000L}) {
                JSONObject result = page.evaluate();
                assertTrue(result.getJSONObject("diagnostics").getBoolean("composerMissing"));
                ComposerRecoveryPolicy.Decision decision = policy.onRetry(true, false, now, DEADLINE);
                if (decision == ComposerRecoveryPolicy.Decision.REENTER) {
                    page.navigate("https://chatgpt.com/g/g-p-fixture/project");
                } else {
                    assertEquals(ComposerRecoveryPolicy.Decision.WAIT, decision);
                }
            }
            assertEquals(2, policy.recoveryAttempts());
            assertEquals(0, policy.retryAttempts());
            assertEquals(ComposerRecoveryPolicy.Decision.TIMED_OUT,
                    policy.onRetry(true, false, DEADLINE, DEADLINE));
            assertEquals(0, page.clicks);
        }
    }

    @Test(timeout = 20_000) public void authenticationAndRouteErrorsKeepTheirOriginalClassification() throws Exception {
        try (Page page = new Page()) {
            page.command(new JSONObject().put("action", "auth").put("value", true));
            assertEquals("AUTH_REQUIRED", page.evaluate().getString("status"));
            assertEquals(0, page.clicks);

            page.navigate("https://chatgpt.com/g/g-p-other-fixture/project");
            assertEquals("TARGET_CONTEXT_MISMATCH", page.evaluate().getString("status"));
            assertEquals(0, page.clicks);

            page.navigate("https://example.invalid/");
            assertEquals("TARGET_CONTEXT_MISMATCH", page.evaluate().getString("status"));
            assertEquals(0, page.clicks);
        }
    }

    private static final class Page implements AutoCloseable {
        private final Process process;
        private final BufferedWriter input;
        private final BufferedReader output;
        private int clicks;

        Page() throws Exception {
            Schedule schedule = new Schedule();
            schedule.targetType = "project";
            schedule.targetUrl = "https://chatgpt.com/g/g-p-fixture/project";
            String compose = AutomationScript.build(schedule, "fixture recovery prompt", "fixture-recovery-run", 0);
            String server = "const compose=" + RequestProfileScript.jsQuote(compose) + ";" + """
                    const vm=require('node:vm');
                    const readline=require('node:readline');
                    const storage=new Map();
                    let clicks=0,context,state;
                    let storageMode='normal';
                    const saved={
                      getItem:key=>{if(storageMode==='throw')throw Error('Fixture storage unavailable');return storage.get(key)||null;},
                      setItem:(key,value)=>{if(storageMode==='throw')throw Error('Fixture storage unavailable');
                        if(storageMode!=='discard')storage.set(key,value);}
                    };
                    function navigate(url){
                      state={composer:null,auth:false};
                      const localState=state;
                      const send={dataset:{testid:'send-button'},disabled:false,title:'',
                        getAttribute:()=>null,click:()=>clicks++};
                      const auth={isConnected:true,offsetParent:{},innerText:'Log in',
                        getAttribute:key=>key==='href'?'/auth/login':null};
                      const document={readyState:'complete',forms:[],activeElement:{tagName:'BODY'},
                        hasFocus:()=>false,
                        querySelectorAll:selector=>{
                          if(selector==='textarea#prompt-textarea')return localState.composer?[localState.composer]:[];
                          if(selector==='a[href],button,[role="button"]')return localState.auth?[auth]:[];
                          return [];
                        }};
                      localState.createComposer=value=>({value,isConnected:true,offsetParent:{},tagName:'TEXTAREA',
                        getAttribute:()=>null,focus(){document.activeElement=this;},dispatchEvent:()=>true,
                        closest:()=>({querySelectorAll:()=>[send]})});
                      const page={location:new URL(url),document,localStorage:saved,sessionStorage:saved,
                        Event:class Event{constructor(type,options){this.type=type;Object.assign(this,options);}}};
                      page.window=page;
                      context=vm.createContext(page);
                    }
                    navigate('https://chatgpt.com/g/g-p-fixture/project');
                    readline.createInterface({input:process.stdin}).on('line',line=>{
                      try{
                        const request=JSON.parse(line);
                        let result=null;
                        if(request.action==='navigate')navigate(request.url);
                        else if(request.action==='composer')state.composer=state.createComposer(request.value);
                        else if(request.action==='remove')state.composer=null;
                        else if(request.action==='auth')state.auth=request.value;
                        else if(request.action==='storage')storageMode=request.value;
                        else if(request.action==='snapshot'){}
                        else if(request.action==='evaluate')result=JSON.parse(vm.runInContext(compose,context,{timeout:1000}));
                        else throw Error('Unknown fixture action');
                        process.stdout.write(JSON.stringify({result,clicks,composerValue:state.composer?state.composer.value:null})+'\\n');
                      }catch(error){process.stdout.write(JSON.stringify({error:String(error.stack||error)})+'\\n');}
                    });
                    """;
            process = new ProcessBuilder("node", "-e", server).redirectErrorStream(true).start();
            input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        }

        JSONObject command(JSONObject request) throws Exception {
            input.write(request.toString());
            input.newLine();
            input.flush();
            String line = output.readLine();
            assertNotNull("Node fixture exited without a result", line);
            JSONObject reply = new JSONObject(line);
            assertFalse(reply.toString(), reply.has("error"));
            clicks = reply.getInt("clicks");
            return reply;
        }

        JSONObject evaluate() throws Exception {
            return command(new JSONObject().put("action", "evaluate")).getJSONObject("result");
        }

        void navigate(String url) throws Exception {
            command(new JSONObject().put("action", "navigate").put("url", url));
        }

        void setComposer(String value) throws Exception {
            command(new JSONObject().put("action", "composer").put("value", value));
        }

        void removeComposer() throws Exception {
            command(new JSONObject().put("action", "remove"));
        }

        @Override public void close() throws Exception {
            input.close();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
            output.close();
        }
    }
}
