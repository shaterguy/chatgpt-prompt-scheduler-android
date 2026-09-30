package com.shaterguy.chatgptpromptscheduler;

import android.content.SharedPreferences;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public final class ProfileSyncOperationTest {
    @Test public void timeoutBeforePublicationLeavesSnapshotAndVersionUnchanged() throws Exception {
        RequestProfileRegistry registry = registry(); ProfileSyncOperation gate = new ProfileSyncOperation();
        AtomicBoolean finished = new AtomicBoolean(); assertTrue(gate.stop(() -> finished.set(true)));
        assertTrue(finished.get());
        assertFalse(registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK, ProfileRegistrySyncTest.document("late", "low"), "v2", gate));
        assertEquals(List.of("old"), registry.workModels()); assertEquals("v1", registry.sourceVersion(RequestProfileEngine.Mode.WORK));
    }
    @Test public void disablingBeforePublicationLeavesSnapshotAndVersionUnchanged() throws Exception {
        RequestProfileRegistry registry = registry(); ProfileSyncOperation gate = new ProfileSyncOperation();
        registry.setSyncEnabled(false);
        assertFalse(registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK, ProfileRegistrySyncTest.document("late", "low"), "v2", gate));
        assertEquals(List.of("old"), registry.workModels()); assertEquals("v1", registry.sourceVersion(RequestProfileEngine.Mode.WORK));
    }
    @Test public void staleMetadataUpdatesAreRejected() throws Exception {
        SharedPreferences prefs = ProfileRegistrySyncTest.memoryPreferences();
        RequestProfileRegistry registry = new RequestProfileRegistry(prefs); registry.setSyncEnabled(true);
        ProfileSyncOperation gate = new ProfileSyncOperation(); gate.stop(() -> {});
        assertFalse(registry.recordSyncSuccess(RequestProfileEngine.Mode.WORK, gate));
        assertFalse(registry.recordSyncFailure(RequestProfileEngine.Mode.WORK, "NETWORK", gate));
        assertFalse(prefs.contains("checked_WORK")); assertFalse(prefs.contains("error_WORK"));
    }
    @Test(timeout=7000L) public void timeoutDuringCommitDefersCompletionWithoutBlockingCaller() throws Exception {
        SharedPreferences backing = ProfileRegistrySyncTest.memoryPreferences();
        AtomicBoolean block = new AtomicBoolean(); CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        SharedPreferences prefs = (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(), new Class[]{SharedPreferences.class}, (p,m,a) -> {
            Object result = m.invoke(backing,a);
            if (!m.getName().equals("edit")) return result;
            SharedPreferences.Editor editor = (SharedPreferences.Editor)result;
            Object[] wrapped = new Object[1];
            wrapped[0] = Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},(ep,em,ea)->{
                if (em.getName().equals("commit") && block.get()) { entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); }
                Object value = em.invoke(editor,ea); return value==editor ? wrapped[0] : value;
            }); return wrapped[0];
        });
        RequestProfileRegistry registry = new RequestProfileRegistry(prefs);
        registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK,ProfileRegistrySyncTest.document("old","high"),"v1");registry.setSyncEnabled(true);
        ProfileSyncOperation gate = new ProfileSyncOperation();AtomicReference<Throwable> error = new AtomicReference<>();AtomicBoolean finished = new AtomicBoolean();
        block.set(true);
        Thread writer = new Thread(() -> { try { assertTrue(registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK,ProfileRegistrySyncTest.document("new","high"),"v2",gate)); } catch(Throwable failure){error.set(failure);} });
        writer.start();assertTrue(entered.await(5,TimeUnit.SECONDS));
        try {
            assertTrue(gate.stop(() -> finished.set(true))); // Must return while the disk writer remains blocked.
            assertFalse(finished.get()); assertFalse(gate.active()); assertFalse(gate.beginPublication());
        } finally { release.countDown(); }
        writer.join(5000); assertFalse(writer.isAlive()); if(error.get()!=null)throw new AssertionError(error.get());
        assertTrue(finished.get()); assertEquals("v2",registry.sourceVersion(RequestProfileEngine.Mode.WORK));
        assertFalse(registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK,ProfileRegistrySyncTest.document("late","high"),"v3",gate));
        assertEquals("v2",registry.sourceVersion(RequestProfileEngine.Mode.WORK));
    }
    @Test public void failedCommitReleasesPublicationLeaseAndKeepsLastGood() throws Exception {
        boolean[] failWrites = {false};
        RequestProfileRegistry registry = new RequestProfileRegistry(ProfileRegistrySyncTest.memoryPreferences(failWrites));
        registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK,ProfileRegistrySyncTest.document("old","high"),"v1");registry.setSyncEnabled(true);
        ProfileSyncOperation gate = new ProfileSyncOperation(); failWrites[0] = true;
        assertThrows(IllegalStateException.class, () -> registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK,ProfileRegistrySyncTest.document("late","high"),"v2",gate));
        AtomicBoolean finished = new AtomicBoolean(); gate.stop(() -> finished.set(true)); assertTrue(finished.get());
        assertEquals(List.of("old"),registry.workModels());assertEquals("v1",registry.sourceVersion(RequestProfileEngine.Mode.WORK));
    }

    @Test public void stopCompletionRunsOnceAndFirstOutcomeWins() {
        ProfileSyncOperation gate = new ProfileSyncOperation();int[] count={0};
        assertTrue(gate.beginPublication()); assertTrue(gate.stop(()->count[0]++));
        assertFalse(gate.stop(()->count[0]+=100)); assertEquals(0,count[0]);
        gate.endPublication();assertEquals(1,count[0]);assertFalse(gate.beginPublication());
    }
    private static RequestProfileRegistry registry() throws Exception {
        RequestProfileRegistry registry = new RequestProfileRegistry(ProfileRegistrySyncTest.memoryPreferences());
        registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK,ProfileRegistrySyncTest.document("old","high"),"v1");registry.setSyncEnabled(true);return registry;
    }
}
