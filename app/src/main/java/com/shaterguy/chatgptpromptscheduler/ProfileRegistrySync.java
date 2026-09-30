package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Coalesced refresh; only the foreground settings flow can present Google consent. */
final class ProfileRegistrySync {
    static final String CHAT_DOCUMENT_ID = "1CblWAg3XWuVYbGIuUt0TLKGuIyhRX3yW2KCmvAmZ75Q";
    static final String WORK_DOCUMENT_ID = "1TB5_H84_2ypC5XFAdEaopySyBpkKBHLOcPB5s3naDOo";
    interface Callback { void complete(Result result); }
    static final class Result {
        final boolean updated;
        final boolean failed;
        Result(boolean updated, boolean failed) { this.updated = updated; this.failed = failed; }
    }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "scheduler-profile-sync"); thread.setDaemon(true); return thread;
    });
    private static final ProfileSyncCoordinator COORDINATOR = new ProfileSyncCoordinator(
            new ProfileSyncCoordinator.Dispatcher() {
                public void post(Runnable action) { MAIN.post(action); }
                public void postDelayed(Runnable action, long delayMillis) { MAIN.postDelayed(action, delayMillis); }
            }, EXECUTOR);

    static void refresh(Context context, Callback callback) {
        RequestProfileRegistry registry = new RequestProfileRegistry(context);
        if (!registry.syncEnabled()) { if (callback != null) callback.complete(new Result(false, false)); return; }
        ProfileSyncCoordinator.Operation operation = begin(registry, callback);
        if (operation == null) return;
        try {
            ProfileDriveAuthorization.request(context.getApplicationContext(), false, new ProfileDriveAuthorization.Callback() {
                public void authorized(String token) { fetch(registry, token, operation); }
                public void resolution(android.app.PendingIntent ignored) { failAuthorization(registry, operation, "AUTH_REQUIRED"); }
                public void failed() { failAuthorization(registry, operation, "AUTH_FAILED"); }
            });
        } catch (RuntimeException error) { failAuthorization(registry, operation, "AUTH_FAILED"); }
    }

    static void refreshWithToken(Context context, String token, Callback callback) {
        RequestProfileRegistry registry = new RequestProfileRegistry(context);
        if (!registry.syncEnabled()) { if (callback != null) callback.complete(new Result(false, false)); return; }
        ProfileSyncCoordinator.Operation operation = begin(registry, callback);
        if (operation != null) fetch(registry, token, operation);
    }

    private static ProfileSyncCoordinator.Operation begin(RequestProfileRegistry registry, Callback callback) {
        return COORDINATOR.begin(callback == null ? null
                        : result -> callback.complete(new Result(result.updated, result.failed)),
                code -> {
                    // In-memory status does not delay deadlines behind either HTTP or disk writes.
                    for (RequestProfileEngine.Mode mode : RequestProfileEngine.Mode.values()) registry.recordTransientSyncFailure(mode, code);
                });
    }

    private static void failAuthorization(RequestProfileRegistry registry, ProfileSyncCoordinator.Operation operation, String code) {
        COORDINATOR.fail(operation, code);
    }

    private static void fetch(RequestProfileRegistry registry, String token, ProfileSyncCoordinator.Operation operation) {
        ProfileSyncOperation gate = operation.gate;
        COORDINATOR.execute(operation, () -> {
            boolean updated = false, failed = false;
            long deadline = System.currentTimeMillis() + 45_000L;
            ProfileDriveClient client = new ProfileDriveClient();
            for (RequestProfileEngine.Mode mode : RequestProfileEngine.Mode.values()) {
                if (!gate.active() || !registry.syncEnabled()) break;
                try {
                    String id = mode == RequestProfileEngine.Mode.CHAT ? CHAT_DOCUMENT_ID : WORK_DOCUMENT_ID;
                    ProfileDriveClient.Metadata before = client.metadata(token, id, deadline);
                    if (!gate.active()) break;
                    if (before.version.equals(registry.sourceVersion(mode)) && registry.hasCanonicalSnapshot(mode)) {
                        registry.recordSyncSuccess(mode, gate);
                        continue;
                    }
                    String raw = client.readDocument(token, id, deadline);
                    if (!gate.active()) break;
                    ProfileDriveClient.Metadata after = client.metadata(token, id, deadline);
                    if (!before.version.equals(after.version)) throw new ProfileDriveClient.Failure("DOCUMENT_INVALID");
                    if (!registry.replaceCanonicalSnapshot(mode, raw, after.version, gate)) break;
                    updated = true;
                } catch (Exception error) {
                    failed = true;
                    String code = error instanceof ProfileDriveClient.Failure ? ((ProfileDriveClient.Failure)error).code
                            : error instanceof java.io.IOException ? "NETWORK"
                            : error instanceof IllegalStateException ? "PERSISTENCE" : "DOCUMENT_INVALID";
                    registry.recordSyncFailure(mode, code, gate);
                }
            }
            COORDINATOR.complete(operation, updated, failed);
        });
    }

    private ProfileRegistrySync() {}
}
