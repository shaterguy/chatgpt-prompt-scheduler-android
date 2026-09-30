package com.shaterguy.chatgptpromptscheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Testable coordinator: waiter deadlines never depend on queued HTTP work or disk status writes. */
final class ProfileSyncCoordinator {
    static final long TIMEOUT_MS = 60_000L;
    interface Dispatcher {
        void post(Runnable action);
        void postDelayed(Runnable action, long delayMillis);
    }
    interface Callback { void complete(Result result); }
    static final class Result {
        final boolean updated, failed;
        final String terminalError;
        Result(boolean updated, boolean failed, String terminalError) {
            this.updated = updated; this.failed = failed; this.terminalError = terminalError;
        }
    }
    static final class Operation {
        final ProfileSyncOperation gate = new ProfileSyncOperation();
        final Consumer<String> terminalStatus;
        Operation(Consumer<String> terminalStatus) { this.terminalStatus = terminalStatus; }
    }
    private final Dispatcher dispatcher;
    private final Executor network;
    private final List<Callback> waiters = new ArrayList<>();
    private Operation active;

    ProfileSyncCoordinator(Dispatcher dispatcher, Executor network) {
        this.dispatcher = dispatcher; this.network = network;
    }

    Operation begin(Callback callback, Consumer<String> terminalStatus) {
        Operation operation;
        synchronized (this) {
            if (callback != null) waiters.add(callback);
            if (active != null) return null;
            operation = new Operation(terminalStatus);
            active = operation;
        }
        dispatcher.postDelayed(() -> fail(operation, "SYNC_TIMEOUT"), TIMEOUT_MS);
        return operation;
    }

    synchronized boolean current(Operation operation) { return operation != null && active == operation && operation.gate.active(); }

    void execute(Operation operation, Runnable fetch) {
        if (!current(operation)) return;
        network.execute(() -> { if (current(operation)) fetch.run(); });
    }

    void complete(Operation operation, boolean updated, boolean failed) {
        finish(operation, new Result(updated, failed, null));
    }

    void fail(Operation operation, String code) {
        finish(operation, new Result(false, true, code));
    }

    private void finish(Operation operation, Result result) {
        synchronized (this) { if (active != operation) return; }
        // Wait only for an already-admitted publication lease. Never queue completion behind HTTP.
        operation.gate.stop(() -> dispatchFinish(operation, result));
    }

    private void dispatchFinish(Operation operation, Result result) {
        dispatcher.post(() -> {
            List<Callback> callbacks;
            synchronized (ProfileSyncCoordinator.this) {
                if (active != operation) return;
                active = null;
                callbacks = new ArrayList<>(waiters); waiters.clear();
            }
            if (result.terminalError != null && operation.terminalStatus != null) {
                try { operation.terminalStatus.accept(result.terminalError); } catch (RuntimeException ignored) { }
            }
            for (Callback callback : callbacks) {
                try { callback.complete(result); } catch (RuntimeException ignored) { }
            }
        });
    }
}
