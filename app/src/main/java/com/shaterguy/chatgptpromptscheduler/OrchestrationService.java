package com.shaterguy.chatgptpromptscheduler;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Optional Protocol 3.x relay. Reservation execution always has priority. */
public final class OrchestrationService extends Service implements AutomationRuntimeGate.Listener {
    public static final String ACTION_RUN = "com.shaterguy.chatgptpromptscheduler.ORCHESTRATION_RUN";
    private static final int NOTIFICATION_ID = 7020;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable stepRunnable = this::runStep;
    private final Runnable resumeRunnable = this::ensureEngine;
    private OrchestrationStore store;
    private HeadlessWebViewHost host;
    private WebView webView;
    private int generation;
    private boolean evaluationInFlight;
    private boolean commitAuthorized;
    private String loadedTarget = "";
    private PowerManager.WakeLock wakeLock;
    private int consecutiveEngineFailures;
    private long recoveryProbeStartedAt;
    private int missingUserTurnProbes;

    @Override
    public void onCreate() {
        super.onCreate();
        store = new OrchestrationStore(this);
        NotificationHelper.ensureChannels(this);
        AutomationRuntimeGate.addListener(this);
        PowerManager power = getSystemService(PowerManager.class);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                getPackageName() + ":orchestration-relay");
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // A null intent is Android's START_STICKY recreation. Every external start must be explicit.
        if (intent != null && !ACTION_RUN.equals(intent.getAction())) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        startAsForeground(store.status());
        if (!NotificationHelper.orchestrationAlertsEnabled(this)) {
            store.fail("NOTIFICATION_DISABLED", "오토런 오류 알림 권한 또는 알림 채널이 꺼져 있습니다.");
            stopRelay();
            return START_NOT_STICKY;
        }
        if (!canRun()) {
            stopRelay();
            return START_NOT_STICKY;
        }
        handler.post(this::ensureEngine);
        return START_STICKY;
    }

    private boolean canRun() {
        String state = store.deliveryState();
        return store.active() && !store.paused() && !store.terminal() && !store.waitingForUser()
                && !OrchestrationStore.DELIVERY_AMBIGUOUS.equals(state)
                && !OrchestrationStore.DELIVERY_FAILED.equals(state);
    }

    private void startAsForeground(String text) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, NotificationHelper.orchestrationActive(this, text),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, NotificationHelper.orchestrationActive(this, text));
        }
    }

    private void ensureEngine() {
        handler.removeCallbacks(resumeRunnable);
        if (!canRun()) {
            stopRelay();
            return;
        }
        String configError = store.runtimeConfigError();
        if (!configError.isEmpty()) {
            pauseWithError("CONFIG_INVALID", configError);
            return;
        }
        if (scheduleHasPriority()) {
            yieldForSchedule();
            return;
        }
        acquireWakeLock();
        store.setSchedulePreempted(false);
        if (OrchestrationStore.DELIVERY_PREPARING.equals(store.deliveryState()) && !commitAuthorized)
            store.resetPreparing();
        if (webView != null && loadedTarget.equals(store.targetUrl())) {
            scheduleStep(500L);
            return;
        }
        launchEngine();
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void launchEngine() {
        cleanupWebView();
        if (scheduleHasPriority()) {
            yieldForSchedule();
            return;
        }
        loadedTarget = store.targetUrl();
        store.setStatus(OrchestrationStore.sideLabel(activeTargetSide()) + " 대화 여는 중");
        startAsForeground(store.status());
        try {
            host = HeadlessWebViewHost.create(this);
            webView = host.webView();
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            String userAgent = settings.getUserAgentString();
            settings.setUserAgentString(userAgent + " ChatGPTPromptScheduler/0.1.15 Orchestration/3.1");
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    generation++;
                    evaluationInFlight = false;
                    handler.removeCallbacks(stepRunnable);
                    if (!matchesExpectedTarget(url)) pauseWithError("TARGET_CHANGED", "중계 대상 대화가 바뀌었습니다.");
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    if (!matchesExpectedTarget(url)) {
                        pauseWithError("TARGET_CHANGED", "중계 대상 대화가 바뀌었습니다.");
                        return;
                    }
                    scheduleStep(1800L);
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request.isForMainFrame()) pauseWithError("NETWORK_ERROR", "중계 대화를 불러오는 중 네트워크 오류가 발생했습니다.");
                }

                @Override
                public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                    if (request.isForMainFrame()) pauseWithError("HTTP_ERROR", "중계 대화 서버가 오류 응답을 반환했습니다.");
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return request.isForMainFrame() && !matchesExpectedTarget(String.valueOf(request.getUrl()));
                }

                @Override
                public void onReceivedSslError(WebView view, SslErrorHandler sslHandler, SslError error) {
                    sslHandler.cancel();
                    pauseWithError("SSL_ERROR", "SSL 인증서 오류로 중계를 멈췄습니다.");
                }

                @Override
                public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    webView = null;
                    if (host != null) {
                        host.destroy();
                        host = null;
                    }
                    generation++;
                    evaluationInFlight = false;
                    consecutiveEngineFailures++;
                    if (consecutiveEngineFailures >= 3) {
                        pauseWithError("RENDERER_FAILED", "WebView 렌더러가 연속으로 종료되어 중계를 멈췄습니다.");
                    } else {
                        store.setStatus("WebView 렌더러 복구 중");
                        handler.postDelayed(resumeRunnable, 2000L);
                    }
                    return true;
                }
            });
            webView.loadUrl(loadedTarget);
        } catch (Throwable error) {
            cleanupWebView();
            consecutiveEngineFailures++;
            if (consecutiveEngineFailures >= 3) {
                pauseWithError("WEBVIEW_INIT_FAILED", "WebView를 연속으로 초기화하지 못해 중계를 멈췄습니다.");
            } else {
                store.setStatus("WebView 복구 대기");
                handler.postDelayed(resumeRunnable, 2500L);
            }
        }
    }

    private void scheduleStep(long delayMs) {
        handler.removeCallbacks(stepRunnable);
        if (webView != null && canRun()) handler.postDelayed(stepRunnable, delayMs);
    }

    private void runStep() {
        if (scheduleHasPriority()) {
            yieldForSchedule();
            return;
        }
        if (webView == null || evaluationInFlight || !canRun()) return;
        String actualUrl = webView.getUrl();
        if (!matchesExpectedTarget(actualUrl)) {
            pauseWithError("TARGET_CHANGED", "중계 대상 대화가 바뀌어 자동 전송을 멈췄습니다.");
            return;
        }

        String state = store.deliveryState();
        boolean clickAttempt = OrchestrationStore.DELIVERY_PREPARING.equals(state) && commitAuthorized;
        if (OrchestrationStore.DELIVERY_PREPARING.equals(state) && !clickAttempt) {
            store.resetPreparing();
            scheduleStep(0L);
            return;
        }
        if (clickAttempt) {
            if (scheduleHasPriority()) {
                yieldForSchedule();
                return;
            }
            commitAuthorized = false;
            store.markSubmitting(); // durable fail-closed boundary immediately before click-capable JS
            state = OrchestrationStore.DELIVERY_SUBMITTING;
        }

        String deliveryPrompt = OrchestrationStore.DELIVERY_PENDING.equals(state)
                ? store.ensureStampedPrompt() : store.deliveryPrompt();
        if (deliveryPrompt.isEmpty()) {
            pauseWithError("DELIVERY_PROMPT_MISSING", "현재 중계 전달 프롬프트를 복구하지 못했습니다.");
            return;
        }

        String script;
        if (OrchestrationStore.DELIVERY_PENDING.equals(state)) script = OrchestrationScript.prepare(deliveryPrompt);
        else if (OrchestrationStore.DELIVERY_SUBMITTING.equals(state)) {
            script = clickAttempt ? OrchestrationScript.commit(deliveryPrompt)
                    : OrchestrationScript.recoverSubmission(deliveryPrompt);
        } else if (OrchestrationStore.DELIVERY_SUBMITTED.equals(state)) {
            script = OrchestrationScript.confirmSubmission(deliveryPrompt);
        } else if (OrchestrationStore.DELIVERY_WAITING_RESPONSE.equals(state)) {
            script = OrchestrationScript.observe(deliveryPrompt);
        } else {
            pauseWithError("STATE_INVALID", "복구할 수 없는 중계 전달 상태입니다.");
            return;
        }

        WebView active = webView;
        int activeGeneration = generation;
        long activeEpoch = store.epoch();
        String evaluatedState = state;
        boolean evaluatedClickAttempt = clickAttempt;
        evaluationInFlight = true;
        active.evaluateJavascript(script, raw -> {
            evaluationInFlight = false;
            if (active != webView || activeGeneration != generation || activeEpoch != store.epoch()) return;
            if (scheduleHasPriority()) {
                yieldForSchedule();
                return;
            }
            if (!matchesExpectedTarget(active.getUrl())) {
                pauseWithError("TARGET_CHANGED", "스크립트 실행 중 중계 대상 대화가 바뀌었습니다.");
                return;
            }
            JSONObject result = parseObject(raw);
            if (result.has("status")) consecutiveEngineFailures = 0;
            if (OrchestrationStore.DELIVERY_PENDING.equals(evaluatedState)) handlePrepare(result);
            else if (OrchestrationStore.DELIVERY_SUBMITTING.equals(evaluatedState))
                handleSubmission(result, evaluatedClickAttempt);
            else if (OrchestrationStore.DELIVERY_SUBMITTED.equals(evaluatedState)) handleConfirmation(result);
            else handleObservation(result);
        });
    }

    private void handlePrepare(JSONObject result) {
        String status = result.optString("status", "SCRIPT_RESULT_INVALID");
        switch (status) {
            case "READY" -> {
                store.markPreparing();
                commitAuthorized = true;
                scheduleStep(0L);
            }
            case "ALREADY_SUBMITTED" -> {
                store.markSubmitted();
                scheduleStep(250L);
            }
            case "RETRY" -> {
                if (System.currentTimeMillis() - store.phaseStartedAt() >= 60_000L)
                    pauseWithError("DOM_COMPOSER_NOT_FOUND", "60초 동안 ChatGPT 프롬프트 입력창을 준비하지 못했습니다.");
                else retry("프롬프트 입력 준비 대기", 1000L);
            }
            case "AUTH_REQUIRED", "DRAFT_PRESENT", "TARGET_CONTEXT_MISMATCH", "NETWORK_ERROR", "DOM_STRUCTURE_ERROR" ->
                    pauseWithError(status, fixedScriptMessage(status));
            default -> pauseWithError("SUBMIT_SCRIPT_ERROR", "중계 전송 준비 스크립트 오류: " + safeCode(status));
        }
    }

    private void handleSubmission(JSONObject result, boolean clickAttempt) {
        String status = result.optString("status", "SCRIPT_RESULT_INVALID");
        if (clickAttempt && ("SUBMITTED".equals(status) || "ALREADY_SUBMITTED".equals(status))) {
            store.markSubmitted();
            startAsForeground(store.status());
            scheduleStep(500L);
            return;
        }
        if (!clickAttempt && "SUBMITTED".equals(status)) {
            recoveryProbeStartedAt = 0L;
            store.markWaiting();
            startAsForeground(store.status());
            scheduleStep(2500L);
            return;
        }
        if (!clickAttempt && "RECOVERY_ABSENT".equals(status)) {
            long now = System.currentTimeMillis();
            if (recoveryProbeStartedAt == 0L) recoveryProbeStartedAt = now;
            if (now - recoveryProbeStartedAt < 15_000L) {
                scheduleStep(1800L);
            } else {
                pauseAmbiguous("프로세스 복구 후 전송된 사용자 턴을 확인할 수 없어 자동 재전송하지 않습니다.");
            }
            return;
        }
        if ("AMBIGUOUS".equals(status)) {
            pauseAmbiguous("전송 커밋 결과를 확인할 수 없어 자동 재전송하지 않습니다.");
            return;
        }
        if ("TARGET_CONTEXT_MISMATCH".equals(status)) {
            pauseWithError(status, fixedScriptMessage(status));
            return;
        }
        pauseWithError("COMMIT_SCRIPT_ERROR", "중계 제출 스크립트 오류: " + safeCode(status));
    }

    private void handleConfirmation(JSONObject result) {
        String status = result.optString("status", "SCRIPT_RESULT_INVALID");
        switch (status) {
            case "SUBMITTED" -> {
                store.markWaiting();
                startAsForeground(store.status());
                scheduleStep(2500L);
            }
            case "RETRY" -> {
                long startedAt = store.deliveryAttemptAt() > 0L ? store.deliveryAttemptAt() : store.phaseStartedAt();
                if (System.currentTimeMillis() - startedAt >= 60_000L) {
                    pauseAmbiguous("제출 클릭 후 60초 동안 사용자 턴을 확인하지 못해 자동 재전송하지 않습니다.");
                } else retry("제출 사용자 턴 반영 대기", 1000L);
            }
            case "AMBIGUOUS" -> pauseAmbiguous("제출 결과가 불명확하여 자동 재전송하지 않습니다.");
            case "TARGET_CONTEXT_MISMATCH" -> pauseWithError(status, fixedScriptMessage(status));
            default -> pauseWithError("CONFIRM_SCRIPT_ERROR", "제출 확인 스크립트 오류: " + safeCode(status));
        }
    }

    private void handleObservation(JSONObject result) {
        String status = result.optString("status", "SCRIPT_RESULT_INVALID");
        if ("USER_TURN_MISSING".equals(status)) {
            missingUserTurnProbes++;
            if (missingUserTurnProbes >= 3)
                pauseWithError("DOM_USER_TURN_MISSING", "제출 확인된 사용자 턴을 ChatGPT 대화 DOM에서 찾지 못했습니다.");
            else scheduleStep(1500L);
            return;
        }
        missingUserTurnProbes = 0;
        if ("RETRY".equals(status)) {
            retry("정상 응답 대기", 3000L);
            return;
        }
        if ("AUTH_REQUIRED".equals(status) || "NETWORK_ERROR".equals(status)
                || "DOM_STRUCTURE_ERROR".equals(status) || "TARGET_CONTEXT_MISMATCH".equals(status)) {
            pauseWithError(status, fixedScriptMessage(status));
            return;
        }
        if (!"CANDIDATE".equals(status)) {
            pauseWithError("OBSERVE_SCRIPT_ERROR", "중계 응답 스크립트 오류: " + safeCode(status));
            return;
        }
        String fingerprint = result.optString("fingerprint", "");
        if (!fingerprint.matches("\\d{1,6}:[0-9a-f]{1,8}")) {
            pauseWithError("FINGERPRINT_INVALID", "응답 안정성 지문 형식이 올바르지 않습니다.");
            return;
        }
        if (store.observeCandidate(fingerprint) < 3) {
            scheduleStep(1800L);
            return;
        }
        acceptSignal(result.optString("text", ""));
    }

    private void acceptSignal(String response) {
        if (store.terminal()) {
            stopRelay();
            return;
        }
        String sourceSide = store.monitoringSide();
        OrchestrationSignal.ParseResult parsed = OrchestrationSignal.validate(response, store.runJobId(),
                sourceSide, store.currentStep(), store.currentRound(), store.lastAcceptedSignal());
        if (!parsed.isValid()) {
            pauseWithProtocolError(parsed.errorCode, sourceSide);
            return;
        }
        OrchestrationSignal signal = parsed.signal;
        if (signal.type == OrchestrationSignal.Type.DONE || signal.type == OrchestrationSignal.Type.PAUSE
                || signal.type == OrchestrationSignal.Type.ABORTED) {
            store.finish(signal, sourceSide);
            NotificationHelper.orchestrationTerminal(this, signal.type, store.runJobId());
            stopRelay();
            return;
        }
        if (signal.type == OrchestrationSignal.Type.USER_ACTION_REQUIRED) {
            store.waitForUser(signal, sourceSide);
            NotificationHelper.orchestrationUserAction(this, sourceSide, store.runJobId(),
                    signal.step, signal.round, signal.actionId);
            stopRelay();
            return;
        }
        store.transition(signal, sourceSide);
        cleanupWebView();
        handler.post(this::ensureEngine);
    }

    /** Elapsed time is telemetry only; a normal response wait never times out. */
    private void retry(String detail, long delayMs) {
        store.incrementPoll();
        String label = OrchestrationStore.DELIVERY_WAITING_RESPONSE.equals(store.deliveryState())
                ? OrchestrationStore.sideLabel(store.monitoringSide()) : OrchestrationStore.sideLabel(store.deliveryTarget());
        store.setStatus(label + " · " + safeDetail(detail));
        if (store.pollCountLong() % 20L == 0L) startAsForeground(store.status());
        scheduleStep(delayMs);
    }

    private void pauseWithProtocolError(OrchestrationSignal.ErrorCode code, String side) {
        String detail = switch (code) {
            case NO_SIGNAL -> "최종 응답에서 기대한 제어 신호를 찾지 못했습니다.";
            case PARSE_FAILED -> "제어 신호 형식 또는 위치가 Protocol 규칙과 맞지 않습니다.";
            case WRONG_JOB -> "다른 Job ID의 제어 신호를 거부했습니다.";
            case WRONG_DIRECTION -> "현재 대화방 방향과 맞지 않는 제어 신호를 거부했습니다.";
            case WRONG_STEP_ROUND -> "기대한 Step/Round 순서와 맞지 않는 제어 신호를 거부했습니다.";
            case DUPLICATE -> "이미 처리한 중복 제어 신호를 거부했습니다.";
            case STALE -> "과거 Step/Round 제어 신호를 거부했습니다.";
            case WORK_TERMINAL -> "Work 대화에서 발생한 terminal 제어 신호를 거부했습니다.";
            default -> "유효하지 않은 제어 신호를 거부했습니다.";
        };
        store.fail(code.name(), detail);
        NotificationHelper.orchestrationError(this, side, store.runJobId(), store.currentStep(), store.currentRound(), detail);
        stopRelay();
    }

    private void pauseWithError(String code, String detail) {
        String safe = safeDetail(detail);
        store.fail(code, safe);
        NotificationHelper.orchestrationError(this, activeTargetSide(), store.runJobId(),
                store.currentStep(), store.currentRound(), safe);
        stopRelay();
    }

    private void pauseAmbiguous(String detail) {
        String safe = safeDetail(detail);
        store.ambiguous(safe);
        NotificationHelper.orchestrationError(this, store.deliveryTarget(), store.runJobId(),
                store.currentStep(), store.currentRound(), safe);
        stopRelay();
    }

    private void yieldForSchedule() {
        if (!canRun()) return;
        if (OrchestrationStore.DELIVERY_PREPARING.equals(store.deliveryState())) store.resetPreparing();
        commitAuthorized = false;
        cleanupWebView();
        store.setSchedulePreempted(true);
        store.setStatus("예약 실행 우선 · 오토런 중계 일시 양보");
        releaseWakeLock();
        startAsForeground(store.status());
        handler.removeCallbacks(resumeRunnable);
        handler.postDelayed(resumeRunnable, 750L);
    }

    @Override
    public void onSchedulePriorityChanged(boolean active) {
        handler.post(active ? this::yieldForSchedule : this::ensureEngine);
    }

    private JSONObject parseObject(String raw) {
        try {
            Object value = new JSONTokener(raw).nextValue();
            if (value instanceof String) return new JSONObject((String) value);
            if (value instanceof JSONObject) return (JSONObject) value;
        } catch (JSONException ignored) {
        }
        return new JSONObject();
    }

    private boolean scheduleHasPriority() {
        if (AutomationRuntimeGate.isScheduleActive()) return true;
        try {
            return new QueueStore(this).hasActive();
        } catch (RuntimeException error) {
            return true;
        }
    }

    private boolean matchesExpectedTarget(String actualUrl) {
        return TargetParser.matchesTarget("existing", store.targetUrl(), actualUrl);
    }

    private String activeTargetSide() {
        return OrchestrationStore.DELIVERY_WAITING_RESPONSE.equals(store.deliveryState())
                ? store.monitoringSide() : store.deliveryTarget();
    }

    private static String safeDetail(String detail) {
        String value = detail == null ? "" : detail.replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() > 240 ? value.substring(0, 240) : value;
    }

    private static String safeCode(String code) {
        if (code == null || !code.matches("[A-Z0-9_]{1,64}")) return "UNKNOWN";
        return code;
    }

    private static String fixedScriptMessage(String status) {
        return switch (status) {
            case "AUTH_REQUIRED" -> "ChatGPT 로그인 세션을 확인해야 합니다.";
            case "DRAFT_PRESENT" -> "대상 대화 입력창에 다른 초안이 있어 자동 전송을 멈췄습니다.";
            case "TARGET_CONTEXT_MISMATCH" -> "중계 대상 대화가 일치하지 않습니다.";
            case "NETWORK_ERROR" -> "네트워크 연결을 확인해야 합니다.";
            case "DOM_STRUCTURE_ERROR" -> "ChatGPT 대화 구조를 확인하지 못했습니다.";
            default -> "오토런 WebView 상태를 확인해야 합니다.";
        };
    }

    private void cleanupWebView() {
        handler.removeCallbacks(stepRunnable);
        generation++;
        evaluationInFlight = false;
        commitAuthorized = false;
        recoveryProbeStartedAt = 0L;
        missingUserTurnProbes = 0;
        loadedTarget = "";
        if (host != null) {
            host.destroy();
            host = null;
            webView = null;
        } else if (webView != null) {
            try { webView.destroy(); } catch (Throwable ignored) {}
            webView = null;
        }
    }

    private void stopRelay() {
        handler.removeCallbacks(stepRunnable);
        handler.removeCallbacks(resumeRunnable);
        cleanupWebView();
        releaseWakeLock();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        AutomationRuntimeGate.removeListener(this);
        handler.removeCallbacksAndMessages(null);
        cleanupWebView();
        releaseWakeLock();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }
}
