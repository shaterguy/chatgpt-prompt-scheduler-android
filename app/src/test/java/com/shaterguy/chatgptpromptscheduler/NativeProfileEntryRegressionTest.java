package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public final class NativeProfileEntryRegressionTest {
    @Test public void composeAfterEntryDoesNotReadNativeModuleStateAgain() throws Exception {
        Schedule schedule = new Schedule();
        schedule.chatReasoning = "medium";
        schedule.resolvedRequestProfile = LegacyProfileFixtures.profile(RequestProfileEngine.Mode.CHAT,"","medium");
        schedule.requestProfileRegistryResolved = true;
        String activate = RequestProfileScript.activate(schedule);
        String script = """
            const assert=require('node:assert/strict');
            global.window=globalThis;
            Object.defineProperty(window,'__chatgptPromptSchedulerNativeProfile',{get(){throw Error('Unexpected target-page revalidation');}});
            const result=status=>status;
            """ + "const next=(()=>{" + activate + "return 'COMPOSE';})();assert.equal(next,'COMPOSE');";
        Process process = new ProcessBuilder("node","-e",script).redirectErrorStream(true).start();
        boolean done=process.waitFor(20,TimeUnit.SECONDS);
        if(!done)process.destroyForcibly();
        assertTrue("Entry regression test timed out",done);
        assertEquals(new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8),0,process.exitValue());
    }
}
