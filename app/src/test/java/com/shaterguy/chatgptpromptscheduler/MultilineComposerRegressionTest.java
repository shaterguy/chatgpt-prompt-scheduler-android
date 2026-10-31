package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public final class MultilineComposerRegressionTest {
    @Test public void multilineUsesOneEscapedFragmentAndSingleLineKeepsNativeText() throws Exception {
        Process process = new ProcessBuilder("node", "../scripts/test-dom-composer.cjs")
                .redirectErrorStream(true).start();
        process.getOutputStream().write(DomComposerScript.inputStep().getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        boolean done = process.waitFor(20, TimeUnit.SECONDS);
        if (!done) process.destroyForcibly();
        assertTrue("Multiline regression timed out", done);
        assertEquals(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8),
                0, process.exitValue());
    }
}
