package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

/** Verifies Android entry points remain wired to the separately unit-tested lifecycle gates. */
public final class ProfileLifecycleContractTest {
    private static String source(String file) throws Exception {
        Path path = Path.of("app/src/main/java/com/shaterguy/chatgptpromptscheduler", file);
        if (!Files.exists(path)) path = Path.of("..").resolve(path);
        return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
    }
    @Test public void disabledAndDestroyedSettingsInvalidatePendingAuthorization() throws Exception {
        String value = source("SettingsActivity.java");
        assertTrue("Disable must invalidate before persisting", value.contains("authorizationAttempt.cancel();\n                        profileRegistry.setSyncEnabled(false)"));
        assertTrue("Destroyed activities must invalidate", value.contains("authorizationAttempt.cancel();\n        super.onDestroy()"));
        assertTrue("Results must claim a launched attempt", value.contains("authorizationAttempt.takeConsentResult()"));
        assertTrue("Callbacks must capture their own attempt", value.contains("authorizationCallback(long attempt)"));
    }
    @Test public void scheduledRefreshHoldsBoundedPreparationLeaseUntilHandoff() throws Exception {
        String value = source("ExecutionService.java");
        int start = value.indexOf("final String claimedRunId");
        int refresh = value.indexOf("ProfileRegistrySync.refresh", start);
        assertTrue("Preparation lock must precede refresh", value.indexOf("acquireProfilePreparationWakeLock();", start) < refresh
                && value.indexOf("acquireProfilePreparationWakeLock();", start) > start);
        assertTrue(value.contains("finally { profilePreparation.close(); }"));
        assertTrue(value.substring(value.indexOf("private void cleanupEngine()")).contains("profilePreparation.close();"));
        assertTrue(value.contains("lock.acquire(ProfilePreparationLease.TIMEOUT_MS)"));
    }
    @Test public void publicationChecksOperationAtStorageBoundary() throws Exception {
        String registry = source("RequestProfileRegistry.java");
        assertTrue(registry.contains("operation.beginPublication()"));
        assertTrue(registry.contains("operation.endPublication()"));
        String sync = source("ProfileRegistrySync.java");
        assertTrue(sync.contains("COORDINATOR.execute(operation,"));
        assertTrue(source("ProfileSyncCoordinator.java").contains("operation.gate.stop("));
        assertTrue(sync.contains("replaceCanonicalSnapshot(mode, raw, after.version, gate)"));
    }
}
