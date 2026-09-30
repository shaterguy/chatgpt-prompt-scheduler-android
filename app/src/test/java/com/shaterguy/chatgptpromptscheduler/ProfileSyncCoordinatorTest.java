package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public final class ProfileSyncCoordinatorTest {
    @Test(timeout=7000L) public void delayedAuthorizationAndBlockedFetchDoNotDelayTimeoutCompletion() throws Exception {
        Clock clock = new Clock(); ExecutorService network = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<ProfileSyncCoordinator.Result> delivered = new AtomicReference<>();
        AtomicReference<String> status = new AtomicReference<>();
        ProfileSyncCoordinator coordinator = new ProfileSyncCoordinator(clock, network);
        ProfileSyncCoordinator.Operation operation = coordinator.begin(delivered::set, status::set);
        try {
            clock.advanceTo(50_000L); // Google authorization returns late, then the HTTP call blocks.
            coordinator.execute(operation, () -> await(entered, release));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            clock.advanceTo(60_000L);
            assertNotNull("Timeout completion must not wait behind blocked HTTP", delivered.get());
            assertTrue(delivered.get().failed); assertEquals("SYNC_TIMEOUT", status.get());
            assertEquals(1L, release.getCount()); // Network work is still blocked when waiters resume.
            assertFalse(operation.gate.beginPublication());
        } finally { release.countDown(); network.shutdownNow(); }
    }
    @Test(timeout=7000L) public void timeoutWaitsForPublicationButNotBlockedNetworkAfterPublication() throws Exception {
        Clock clock = new Clock(); ExecutorService network = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger delivered = new AtomicInteger();
        ProfileSyncCoordinator coordinator = new ProfileSyncCoordinator(clock, network);
        ProfileSyncCoordinator.Operation operation = coordinator.begin(result -> delivered.incrementAndGet(), code -> {});
        try {
            coordinator.execute(operation, () -> await(entered, release));assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertTrue(operation.gate.beginPublication()); clock.advanceTo(60_000L); assertEquals(0,delivered.get());
            operation.gate.endPublication(); clock.drain();
            assertEquals(1,delivered.get()); assertEquals(1L,release.getCount());
        } finally { release.countDown(); network.shutdownNow(); }
    }
    @Test public void coalescedWaitersShareOneOperationAndCompleteOnce() {
        Clock clock = new Clock(); ProfileSyncCoordinator coordinator = new ProfileSyncCoordinator(clock,Runnable::run);
        AtomicInteger delivered = new AtomicInteger();
        ProfileSyncCoordinator.Operation operation = coordinator.begin(result -> delivered.incrementAndGet(),code -> {});
        assertNull(coordinator.begin(result -> delivered.incrementAndGet(),code -> {}));
        coordinator.complete(operation,true,false);clock.drain();assertEquals(2,delivered.get());
        clock.advanceTo(60_000L);coordinator.fail(operation,"AUTH_FAILED");clock.drain();assertEquals(2,delivered.get());
    }
    @Test public void lateAuthorizationAfterTimeoutCannotStartFetch() {
        Clock clock = new Clock();ProfileSyncCoordinator coordinator=new ProfileSyncCoordinator(clock,Runnable::run);AtomicInteger calls=new AtomicInteger();
        ProfileSyncCoordinator.Operation operation=coordinator.begin(result -> {},code -> {});
        clock.advanceTo(60_000L);coordinator.execute(operation,calls::incrementAndGet);assertEquals(0,calls.get());
    }
    @Test public void staleOperationCannotFinishNewerOperation() {
        Clock clock = new Clock();ProfileSyncCoordinator coordinator=new ProfileSyncCoordinator(clock,Runnable::run);AtomicInteger delivered=new AtomicInteger();
        ProfileSyncCoordinator.Operation old=coordinator.begin(result -> {},code -> {});clock.advanceTo(60_000L);
        ProfileSyncCoordinator.Operation current=coordinator.begin(result -> delivered.incrementAndGet(),code -> {});
        coordinator.complete(old,true,false);clock.drain();assertEquals(0,delivered.get());assertTrue(coordinator.current(current));
        coordinator.complete(current,true,false);clock.drain();assertEquals(1,delivered.get());
    }
    @Test public void terminalStatusFailureStillReleasesWaiters() {
        Clock clock = new Clock();ProfileSyncCoordinator coordinator=new ProfileSyncCoordinator(clock,Runnable::run);AtomicInteger delivered=new AtomicInteger();
        coordinator.begin(result -> delivered.incrementAndGet(),code -> { throw new IllegalStateException("synthetic"); });
        clock.advanceTo(60_000L);assertEquals(1,delivered.get());
    }
    private static void await(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();try { release.await(5,TimeUnit.SECONDS); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
    private static final class Clock implements ProfileSyncCoordinator.Dispatcher {
        private long now;
        private final List<Runnable> ready=new ArrayList<>();
        private final List<Long> due=new ArrayList<>();
        private final List<Runnable> delayed=new ArrayList<>();
        public synchronized void post(Runnable action) { ready.add(action); }
        public synchronized void postDelayed(Runnable action,long delay) { due.add(now+delay);delayed.add(action); }
        void advanceTo(long value) {
            now=value;
            for(int i=due.size()-1;i>=0;i--) if(due.get(i)<=now){post(delayed.remove(i));due.remove(i);}
            drain();
        }
        void drain() {
            while(true){Runnable action;synchronized(this){if(ready.isEmpty())return;action=ready.remove(0);}action.run();}
        }
    }
}
