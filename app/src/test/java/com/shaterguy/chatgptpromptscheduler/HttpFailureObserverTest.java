package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public final class HttpFailureObserverTest {
    @Test public void fetchAndXhrCaptureDifferentFailuresWithoutReplacingResponses() throws Exception {
        String code = """
                const assert=require('node:assert/strict');
                const [script]=process.argv.slice(1);
                const messages=[];
                global.window=global;
                global.top=global;
                global.location=new URL('https://chatgpt.com/');
                global.SchedulerHttpDiagnostics={postMessage:s=>messages.push(JSON.parse(s))};
                class Xhr extends EventTarget {
                    open(method,url){this.status=0;this.responseType='';}
                    send(){this.status=403;this.responseText='denied by server';this.dispatchEvent(new Event('loadend'));}
                    getResponseHeader(name){return name==='x-request-id'?'xhr-test':null;}
                }
                global.XMLHttpRequest=Xhr;
                let response, nativePromise, failure;
                global.fetch=(...args)=>nativePromise=failure ? Promise.reject(failure) : Promise.resolve(response);
                eval(script);
                (async()=>{
                    for (const status of [400,401,429,500,503]) {
                        response=new Response('server error '+status,{status,headers:{'x-request-id':'id-'+status}});
                        const promise=fetch('/backend-api/f/conversation',{method:'POST'});
                        assert.equal(promise,nativePromise);
                        assert.equal(await promise,response);
                    }
                    response=new Response('success',{status:200});
                    await fetch('/backend-api/f/conversation');
                    response=new Response('unrelated',{status:500});
                    await fetch('https://another.example/backend-api/f/conversation');
                    const xhr=new Xhr();xhr.open('POST','/backend-api/f/conversation/prepare');xhr.send();
                    response=new Response('x'.repeat(6000),{status:502});
                    await fetch('/backend-api/f/conversation');
                    response=new Response('',{status:404});
                    await fetch('/backend-api/f/conversation');
                    failure=new TypeError('offline');
                    await assert.rejects(fetch('/backend-api/f/conversation'), error=>error===failure);
                    await new Promise(resolve=>setTimeout(resolve,200));
                    const byCode=Object.fromEntries(messages.map(m=>[m.code,m]));
                    assert.equal(messages.length,9);
                    assert.equal(byCode[0].bodyState,'network_error');
                    assert.equal(byCode[429].body,'server error 429');
                    assert.equal(byCode[503].requestId,'id-503');
                    assert.equal(byCode[403].body,'denied by server');
                    assert.equal(byCode[502].body.length,4096);
                    assert.equal(byCode[502].bodyState,'truncated');
                    assert.equal(byCode[404].bodyState,'empty');
                })().catch(error=>{console.error(error);process.exitCode=1;});
                """;
        Process process = new ProcessBuilder("node", "-e", code, HttpFailureObserver.script())
                .redirectErrorStream(true).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8), 0, process.exitValue());
    }

    @Test public void responseSanitizerRemovesSecretsAndKeepsErrorDetails() {
        String cleaned = HttpFailureObserver.sanitizeBody(
                "{\"message\":\"server overloaded\",\"access_token\":\"private123\",\"detail\":\"Bearer secret456\"}");
        assertTrue(cleaned.contains("server overloaded"));
        assertFalse(cleaned.contains("private123"));
        assertFalse(cleaned.contains("secret456"));
    }
}
