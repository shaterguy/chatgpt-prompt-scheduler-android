package com.shaterguy.chatgptpromptscheduler;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ExecutionService extends Service {
    private static final int NOTIFICATION_ID = 7010;
    private static final int MAX_ROUTE_RECOVERIES = 3;
    private static final int MAX_TRACE_EVENTS = 120;
    private static final long MAX_RATE_LIMIT_WAIT_MS = 45_000L;
    private static final long MAX_UI_WAIT_MS = 45_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private final Runnable automationRunnable = this::runAutomationStep;
    private final Runnable watchdogRunnable = this::watchdog;
    private final Runnable rateLimitRecoveryRunnable = this::performRateLimitRecovery;
    private final Runnable targetRecoveryRunnable = this::performTargetRouteRecovery;
    private final RecoveryBackoff rateLimitBackoff = new RecoveryBackoff();
    private final RecoveryBackoff targetRecoveryBackoff = new RecoveryBackoff();
    private QueueStore queueStore;
    private ConfigStore configStore;
    private RunLogStore logStore;
    private HeadlessWebViewHost webViewHost;
    private WebView webView;
    private PowerManager.WakeLock wakeLock;
    private JSONObject currentItem;
    private Schedule currentSchedule;
    private long startedAt;
    private long deadline;
    private int pageAttempts;
    private int engineAttempt;
    private int routeRecoveryAttempts;
    private int uiWaitAttempts;
    private int navigationGeneration;
    private int traceDropped;
    private boolean submitted;
    private boolean stepInFlight;
    private String stampedPrompt;
    private String lastObservedUrl = "";
    private String lastRetryDetail = "";
    private long rateLimitStartedAt;
    private long uiWaitStartedAt;
    private long targetRecoveryStartedAt;
    private boolean rateLimitWaiting;
    private boolean rateLimitRecoveryScheduled;
    private boolean targetRecoveryScheduled;
    private JSONArray traceEvents = new JSONArray();

    @Override
    public void onCreate() {
        super.onCreate();
        queueStore = new QueueStore(this);
        configStore = new ConfigStore(this);
        logStore = new RunLogStore(this);
        queueStore.recoverRunning();
        NotificationHelper.ensureChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AutomationRuntimeGate.setScheduleActive(true);
        String scheduleId = intent == null ? null : intent.getStringExtra("scheduleId");
        boolean manual = intent != null && intent.getBooleanExtra("manual", false);
        if (scheduleId != null && !scheduleId.isBlank()) queueStore.enqueue(scheduleId, manual);
        startAsForeground("대기열 준비 중");
        if (processing.compareAndSet(false, true)) handler.post(this::processNext);
        return START_REDELIVER_INTENT;
    }

    private void startAsForeground(String text) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, NotificationHelper.active(this, text), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, NotificationHelper.active(this, text));
        }
    }

    private void processNext() {
        cleanupEngine();
        try {
            currentItem = queueStore.claimNext();
        } catch (RuntimeException error) {
            AutomationRuntimeGate.setScheduleActive(false);
            processing.set(false);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }
        if (currentItem == null) {
            AutomationRuntimeGate.setScheduleActive(false);
            processing.set(false);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }
        AutomationRuntimeGate.setScheduleActive(true);
        startedAt = 0L;
        resetTrace();
        trace("QUEUE_CLAIMED", object("runId", currentItem.optString("runId"), "manual", currentItem.optBoolean("manual", false)));
        String scheduleId = currentItem.optString("scheduleId");
        currentSchedule = configStore.findSchedule(scheduleId);
        if (currentSchedule == null) {
            finish(false, "SCHEDULE_NOT_FOUND", "예약을 찾지 못했습니다.");
            return;
        }
        trace("SCHEDULE_LOADED", object("scheduleId", currentSchedule.id, "name", currentSchedule.name,
                "targetType", currentSchedule.targetType, "targetUrl", currentSchedule.targetUrl));
        if (!currentItem.optBoolean("manual", false) && !currentSchedule.enabled) {
            finish(false, "SCHEDULE_DISABLED", "예약이 비활성화되어 있습니다.");
            return;
        }
        if (!TargetParser.isSupported(currentSchedule.targetUrl)) {
            finish(false, "TARGET_URL_INVALID", "지원하지 않는 ChatGPT URL입니다.");
            return;
        }
        if (!networkAvailable()) {
            finish(false, "NETWORK_UNAVAILABLE", "네트워크 연결이 없습니다.");
            return;
        }
        startedAt = System.currentTimeMillis();
        deadline = startedAt + configStore.settings().optInt("executionTimeoutSeconds", 90) * 1000L;
        pageAttempts = 0;
        engineAttempt = 0;
        routeRecoveryAttempts = 0;
        navigationGeneration = 0;
        boolean recoveredSubmission = currentItem.optLong("submitAttemptedAt", 0L) > 0L;
        String persistedPrompt = currentItem.optString("submitPrompt", "");
        if (recoveredSubmission && persistedPrompt.isBlank()) {
            finish(false, "SUBMIT_RECONCILIATION_REQUIRED", "전송 경계는 남았지만 재확인할 프롬프트가 없습니다.");
            return;
        }
        submitted = recoveredSubmission;
        stepInFlight = false;
        lastObservedUrl = "";
        lastRetryDetail = "";
        uiWaitAttempts = 0;
        rateLimitStartedAt = 0L;
        uiWaitStartedAt = 0L;
        targetRecoveryStartedAt = 0L;
        rateLimitWaiting = false;
        rateLimitRecoveryScheduled = false;
        targetRecoveryScheduled = false;
        rateLimitBackoff.reset();
        targetRecoveryBackoff.reset();
        stampedPrompt = recoveredSubmission ? persistedPrompt : TimestampUtil.prefix(startedAt, currentSchedule.prompt);
        trace("RUN_STARTED", object("timeoutSeconds", Math.max(1L, (deadline - startedAt) / 1000L),
                "promptLength", stampedPrompt.length(), "recoveredSubmission", recoveredSubmission));
        acquireWakeLock();
        startAsForeground(currentSchedule.name + " 실행 중");
        launchEngine();
    }

    private boolean networkAvailable() {
        ConnectivityManager manager = getSystemService(ConnectivityManager.class);
        Network network = manager.getActiveNetwork();
        if (network == null) {
            trace("NETWORK_CHECK", object("available", false, "reason", "no-active-network"));
            return false;
        }
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        boolean available = capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        trace("NETWORK_CHECK", object("available", available,
                "validated", capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)));
        return available;
    }

    private void acquireWakeLock() {
        PowerManager manager = getSystemService(PowerManager.class);
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChatGPTPromptScheduler:Run");
        wakeLock.acquire(Math.max(60_000L, deadline - System.currentTimeMillis() + 15_000L));
        trace("WAKE_LOCK_ACQUIRED", object("held", wakeLock.isHeld()));
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void launchEngine() {
        if (System.currentTimeMillis() >= deadline) {
            finish(false, "EXECUTION_TIMEOUT", timeoutDetail());
            return;
        }
        engineAttempt++;
        trace("ENGINE_LAUNCH", object("engineAttempt", engineAttempt));
        try {
            webViewHost = HeadlessWebViewHost.create(this);
            webView = webViewHost.webView();
            trace("WEBVIEW_HOST_CREATED", object("windowAttached", webViewHost.isWindowAttached(),
                    "viewFocused", webView.isFocused(), "windowFocused", webView.hasWindowFocus()));
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(true);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(false);
            String desktopUserAgent = settings.getUserAgentString()
                    .replaceFirst("\\([^)]*Android[^)]*\\)", "(X11; Linux x86_64)")
                    .replace(" Version/4.0", "");
            settings.setUserAgentString(desktopUserAgent + " ChatGPTPromptScheduler/0.1.14");
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onConsoleMessage(ConsoleMessage message) {
                    if (message == null) return false;
                    ConsoleMessage.MessageLevel level = message.messageLevel();
                    if (level == ConsoleMessage.MessageLevel.ERROR || level == ConsoleMessage.MessageLevel.WARNING) {
                        trace("WEB_CONSOLE", object("level", String.valueOf(level), "message", clip(message.message(), 2000),
                                "source", clip(message.sourceId(), 500), "line", message.lineNumber()));
                    }
                    return false;
                }
            });
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    trace("PAGE_STARTED", object("url", url));
                    recordNavigation(url);
                    cancelAutomationStep();
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    lastObservedUrl = valueOrEmpty(url);
                    trace("PAGE_FINISHED", object("url", url, "progress", view.getProgress(),
                            "windowAttached", view.isAttachedToWindow(), "viewFocused", view.isFocused(),
                            "windowFocused", view.hasWindowFocus()));
                    if (!rateLimitWaiting && !targetRecoveryScheduled) scheduleAutomationStep(2500L);
                }

                @Override
                public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                    if (!valueOrEmpty(url).equals(lastObservedUrl)) {
                        trace("HISTORY_UPDATED", object("url", url, "reload", isReload));
                        recordNavigation(url);
                        if (!rateLimitWaiting && !targetRecoveryScheduled) scheduleAutomationStep(1800L);
                    }
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (!request.isForMainFrame()) return;
                    if (error != null && RecoveryDecisionPolicy.isRateLimitWebViewError(error.getErrorCode())) {
                        trace("RATE_LIMIT_DETECTED", object("source", "WEBVIEW_ERROR_TOO_MANY_REQUESTS",
                                "url", String.valueOf(request.getUrl()), "code", error.getErrorCode()));
                        recoverFromRateLimit("WEBVIEW_ERROR_TOO_MANY_REQUESTS", String.valueOf(error.getDescription()));
                        return;
                    }
                    int errorCode = error == null ? 0 : error.getErrorCode();
                    String description = error == null ? "" : String.valueOf(error.getDescription());
                    trace("PAGE_LOAD_ERROR", object("url", String.valueOf(request.getUrl()), "code", errorCode,
                            "description", description));
                    recoverEngine("PAGE_LOAD_FAILED", description);
                }

                @Override
                public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                    if (!request.isForMainFrame()) return;
                    int statusCode = response == null ? 0 : response.getStatusCode();
                    String reason = response == null ? "" : response.getReasonPhrase();
                    trace("HTTP_ERROR", object("url", String.valueOf(request.getUrl()), "statusCode", statusCode,
                            "reason", reason));
                    if (RecoveryDecisionPolicy.isRateLimitHttp(statusCode)) {
                        trace("RATE_LIMIT_DETECTED", object("source", "HTTP_429",
                                "url", String.valueOf(request.getUrl()), "statusCode", statusCode));
                        recoverFromRateLimit("HTTP_429", reason);
                    }
                }

                @Override
                public void onReceivedSslError(WebView view, SslErrorHandler sslHandler, SslError error) {
                    trace("SSL_ERROR", object("primaryError", error.getPrimaryError(), "url", error.getUrl()));
                    sslHandler.cancel();
                    recoverEngine("SSL_ERROR", "SSL 인증서 오류");
                }

                @Override
                public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    trace("RENDER_PROCESS_GONE", object("didCrash", detail.didCrash(), "priorityAtExit", detail.rendererPriorityAtExit()));
                    webView = null;
                    if (webViewHost != null) {
                        webViewHost.destroy();
                        webViewHost = null;
                    }
                    recoverEngine("RENDER_PROCESS_GONE", detail.didCrash() ? "WebView 렌더러가 비정상 종료되었습니다." : "WebView 렌더러가 종료되었습니다.");
                    return true;
                }
            });
            webView.loadUrl(currentSchedule.targetUrl);
            handler.removeCallbacks(watchdogRunnable);
            handler.postDelayed(watchdogRunnable, 10_000L);
        } catch (Throwable error) {
            trace("ENGINE_START_EXCEPTION", object("type", error.getClass().getName(), "message", valueOrEmpty(error.getMessage())));
            recoverEngine("ENGINE_START_FAILED", error.getMessage());
        }
    }

    private void recordNavigation(String url) {
        navigationGeneration++;
        stepInFlight = false;
        lastObservedUrl = valueOrEmpty(url);
    }

    private void scheduleAutomationStep(long delayMs) {
        if (currentItem == null || webView == null || rateLimitWaiting || targetRecoveryScheduled) return;
        handler.removeCallbacks(automationRunnable);
        handler.postDelayed(automationRunnable, delayMs);
    }

    private void cancelAutomationStep() {
        handler.removeCallbacks(automationRunnable);
    }

    private void watchdog() {
        if (currentItem == null) return;
        trace("WATCHDOG", object("remainingMs", Math.max(0, deadline - System.currentTimeMillis()),
                "pageAttempts", pageAttempts, "engineAttempt", engineAttempt, "submitted", submitted,
                "url", lastObservedUrl));
        if (System.currentTimeMillis() >= deadline) {
            finish(false, "EXECUTION_TIMEOUT", timeoutDetail());
            return;
        }
        handler.postDelayed(watchdogRunnable, 10_000L);
    }

    private void runAutomationStep() {
        if (webView == null || currentItem == null || stepInFlight) return;
        if (rateLimitWaiting) return;
        if (System.currentTimeMillis() >= deadline) {
            finish(false, "EXECUTION_TIMEOUT", timeoutDetail());
            return;
        }
        WebView activeWebView = webView;
        int generation = navigationGeneration;
        stepInFlight = true;
        trace("SCRIPT_EVALUATE", object("phase", submitted ? "verify" : "compose", "pageAttempts", pageAttempts,
                "generation", generation, "url", lastObservedUrl, "windowAttached", activeWebView.isAttachedToWindow(),
                "viewFocused", activeWebView.isFocused(), "windowFocused", activeWebView.hasWindowFocus()));
        String script = submitted
                ? AutomationScript.verify(currentSchedule, stampedPrompt)
                : AutomationScript.build(currentSchedule, stampedPrompt, currentItem.optString("runId"), pageAttempts);
        if (!submitted && !markSubmissionBoundary()) {
            stepInFlight = false;
            finish(false, "SUBMIT_BOUNDARY_SAVE_FAILED", "전송 직전 영속 실행 경계를 저장하지 못했습니다.");
            return;
        }
        activeWebView.evaluateJavascript(script, raw -> {
            stepInFlight = false;
            if (currentItem == null || activeWebView != webView || generation != navigationGeneration) {
                trace("SCRIPT_RESULT_IGNORED", object("reason", "stale-callback", "generation", generation,
                        "currentGeneration", navigationGeneration));
                return;
            }
            if (submitted) handleVerification(raw); else handleAutomationResult(raw);
        });
    }

    private boolean markSubmissionBoundary() {
        if (currentItem == null || queueStore == null) return false;
        if (currentItem.optLong("submitAttemptedAt", 0L) > 0L) return true;
        long attemptedAt = System.currentTimeMillis();
        boolean saved = queueStore.markSubmissionAttempted(currentItem.optString("runId"), attemptedAt, stampedPrompt);
        if (!saved) return false;
        try {
            currentItem.put("submitAttemptedAt", attemptedAt);
            currentItem.put("submitPrompt", stampedPrompt);
        } catch (JSONException error) {
            return false;
        }
        trace("SUBMIT_BOUNDARY_MARKED", object("attemptedAt", attemptedAt, "promptLength", stampedPrompt.length()));
        return true;
    }

    private void clearSubmissionBoundaryIfUnclicked(String reason) {
        if (currentItem == null || submitted) return;
        queueStore.clearSubmissionAttempted(currentItem.optString("runId"));
        currentItem.remove("submitAttemptedAt");
        currentItem.remove("submitPrompt");
        trace("SUBMIT_BOUNDARY_CLEARED", object("reason", reason));
    }

    private void handleAutomationResult(String raw) {
        JSONObject result = parseObject(raw);
        String status = result.optString("status", "SCRIPT_RESULT_INVALID");
        String detail = result.optString("detail", "");
        String resultUrl = result.optString("url", "");
        if (!resultUrl.isBlank()) lastObservedUrl = resultUrl;
        RecoveryDecisionPolicy.Decision policyDecision = recoveryDecision(status, detail, resultUrl);
        trace("SCRIPT_RESULT", object("status", status, "detail", detail, "url", resultUrl,
                "decision", policyDecision.name(), "diagnostics", result.optJSONObject("diagnostics"), "raw", clip(raw, 12_000)));
        switch (status) {
            case "SUBMITTED" -> {
                submitted = true;
                pageAttempts = 0;
                clearUiWaitRecovery("submitted");
                clearRateLimitRecovery("submitted");
                clearTargetRecovery("submitted");
                lastRetryDetail = detail;
                scheduleAutomationStep(2500L);
            }
            case "RATE_LIMIT" -> recoverFromRateLimit("SCRIPT_RATE_LIMIT", detail);
            case "UI_WAIT" -> {
                clearSubmissionBoundaryIfUnclicked("ui_wait");
                handleUiWait(detail);
            }
            case "RECONCILE_SEND" -> {
                submitted = true;
                clearUiWaitRecovery("reconcile_send");
                trace("SUBMIT_RECONCILE", object("detail", detail,
                        "diagnostics", result.optJSONObject("diagnostics")));
                scheduleAutomationStep(1_000L);
            }
            case "RETRY" -> {
                clearSubmissionBoundaryIfUnclicked("action_retry");
                clearUiWaitRecovery("action_retry");
                clearRateLimitRecovery("action_retry");
                pageAttempts++;
                lastRetryDetail = detail;
                if (pageAttempts > 48) finish(false, retryFailureStatus(detail), contextualDetail(detail));
                else scheduleAutomationStep(1200L);
            }
            case "TARGET_TRANSIENT" -> {
                clearSubmissionBoundaryIfUnclicked("target_transient");
                recoverTargetRoute(detail);
            }
            case "TARGET_CONTEXT_MISMATCH" -> {
                if (policyDecision == RecoveryDecisionPolicy.Decision.TARGET_CHANGED
                        || !isRecoverableTarget(resultUrl)) finish(false, "TARGET_CHANGED", contextualDetail(detail));
                else recoverTargetRoute(detail);
            }
            case "MODE_SELECTION_FAILED", "MODE_SELECTION_AMBIGUOUS" ->
                    finish(false, "WORK_MODE_SELECT_FAILED", contextualDetail(detail));
            case "AUTH_REQUIRED", "DRAFT_PRESENT" -> finish(false, status, contextualDetail(detail));
            default -> finish(false, status, contextualDetail(detail.isBlank() ? "자동화 스크립트가 실패했습니다." : detail));
        }
    }

    private RecoveryDecisionPolicy.Decision recoveryDecision(String status, String detail, String resultUrl) {
        if (currentSchedule == null) return RecoveryDecisionPolicy.Decision.FAIL;
        String actual = resultUrl == null || resultUrl.isBlank() ? lastObservedUrl : resultUrl;
        RecoveryDecisionPolicy.NetworkState network = "RATE_LIMIT".equals(status)
                ? RecoveryDecisionPolicy.NetworkState.RATE_LIMIT
                : RecoveryDecisionPolicy.NetworkState.OK;
        RecoveryDecisionPolicy.UiReadiness readiness = RecoveryDecisionPolicy.UiReadiness.READY;
        if (RecoveryDecisionPolicy.isUiWaitStatus(status)) {
            String value = detail == null ? "" : detail.toLowerCase();
            if (value.contains("로딩") || value.contains("loading")) readiness = RecoveryDecisionPolicy.UiReadiness.LOADING;
            else if (value.contains("모드")) readiness = RecoveryDecisionPolicy.UiReadiness.MODE_PENDING;
            else if (value.contains("모델")) readiness = RecoveryDecisionPolicy.UiReadiness.MODEL_PENDING;
            else if (value.contains("추론")) readiness = RecoveryDecisionPolicy.UiReadiness.REASONING_PENDING;
            else readiness = RecoveryDecisionPolicy.UiReadiness.COMPOSER_MISSING;
        }
        RecoveryDecisionPolicy.SendState sendState = submitted
                ? RecoveryDecisionPolicy.SendState.DOM_CONFIRMED
                : "RECONCILE_SEND".equals(status)
                ? RecoveryDecisionPolicy.SendState.AMBIGUOUS
                : RecoveryDecisionPolicy.SendState.NOT_STARTED;
        return RecoveryDecisionPolicy.decide(
                RecoveryDecisionPolicy.targetIntent(currentSchedule.targetType, false, ""),
                RecoveryDecisionPolicy.classify(currentSchedule.targetType, currentSchedule.targetUrl, actual),
                network, readiness, sendState);
    }

    private void handleVerification(String raw) {
        JSONObject result = parseObject(raw);
        String status = result.optString("status", "SCRIPT_RESULT_INVALID");
        String detail = result.optString("detail", "");
        String resultUrl = result.optString("url", "");
        if (!resultUrl.isBlank()) lastObservedUrl = resultUrl;
        trace("VERIFY_RESULT", object("status", status, "detail", detail, "url", resultUrl,
                "diagnostics", result.optJSONObject("diagnostics"), "raw", clip(raw, 12_000)));
        switch (status) {
            case "VERIFIED" -> {
                clearUiWaitRecovery("verified");
                clearRateLimitRecovery("verified");
                clearTargetRecovery("verified");
                finish(true, "VERIFIED", "프롬프트 전송을 확인했습니다.");
            }
            case "RATE_LIMIT" -> recoverFromRateLimit("VERIFY_RATE_LIMIT", detail);
            case "UI_WAIT" -> handleUiWait(detail);
            case "RETRY" -> {
                clearUiWaitRecovery("verify_retry");
                clearRateLimitRecovery("verify_retry");
                pageAttempts++;
                lastRetryDetail = detail;
                if (pageAttempts > 45) finish(false, "SUBMIT_VERIFICATION_FAILED", contextualDetail("전송된 사용자 메시지를 확인하지 못했습니다."));
                else scheduleAutomationStep(1400L);
            }
            case "TARGET_TRANSIENT" -> recoverTargetRoute(detail);
            case "TARGET_CONTEXT_MISMATCH" -> {
                if (isRecoverableTarget(resultUrl)) recoverTargetRoute(detail);
                else finish(false, "TARGET_CHANGED", contextualDetail(detail));
            }
            default -> finish(false, status, contextualDetail(detail.isBlank() ? "전송 검증 스크립트가 실패했습니다." : detail));
        }
    }

    private void handleUiWait(String detail) {
        long now = System.currentTimeMillis();
        if (uiWaitStartedAt == 0L) uiWaitStartedAt = now;
        uiWaitAttempts++;
        lastRetryDetail = detail;
        trace("UI_WAIT", object("detail", detail, "attempt", uiWaitAttempts,
                "elapsedMs", Math.max(0L, now - uiWaitStartedAt)));
        if (now - uiWaitStartedAt >= MAX_UI_WAIT_MS || now >= deadline) {
            finish(false, "UI_NOT_READY", contextualDetail(detail));
            return;
        }
        scheduleAutomationStep(Math.min(1_500L, RecoveryBackoff.delayForAttempt(uiWaitAttempts)));
    }

    private void recoverFromRateLimit(String source, String detail) {
        if (currentItem == null) return;
        clearTargetRecovery("rate_limit_detected");
        long now = System.currentTimeMillis();
        if (rateLimitStartedAt == 0L) {
            rateLimitStartedAt = now;
            rateLimitBackoff.reset();
        }
        if (now - rateLimitStartedAt >= MAX_RATE_LIMIT_WAIT_MS || now >= deadline) {
            finish(false, "RATE_LIMIT_TIMEOUT", contextualDetail(detail == null ? source : detail));
            return;
        }
        rateLimitWaiting = true;
        lastRetryDetail = detail == null || detail.isBlank() ? source : detail;
        if (rateLimitRecoveryScheduled) return;
        RecoveryBackoff.Decision decision = rateLimitBackoff.next();
        rateLimitRecoveryScheduled = true;
        cancelAutomationStep();
        trace("RATE_LIMIT_RECOVERY_WAIT", object("source", source, "detail", valueOrEmpty(detail),
                "attempt", decision.attempt, "delayMs", decision.delayMs,
                "elapsedMs", Math.max(0L, now - rateLimitStartedAt)));
        handler.postDelayed(rateLimitRecoveryRunnable, decision.delayMs);
    }

    private void performRateLimitRecovery() {
        rateLimitRecoveryScheduled = false;
        if (currentItem == null || !rateLimitWaiting) return;
        long now = System.currentTimeMillis();
        if (now >= deadline || now - rateLimitStartedAt >= MAX_RATE_LIMIT_WAIT_MS) {
            finish(false, "RATE_LIMIT_TIMEOUT", timeoutDetail());
            return;
        }
        if (webView == null) {
            clearRateLimitRecovery("webview_missing");
            recoverEngine("RATE_LIMIT_WEBVIEW_MISSING", "rate-limit recovery lost the WebView");
            return;
        }
        if (webView.getProgress() < 100) {
            recoverFromRateLimit("REOBSERVE_LOADING", "rate-limit recovery is still observing page load");
            return;
        }
        String actual = valueOrEmpty(webView.getUrl());
        lastObservedUrl = actual;
        if (matchesCurrentTarget(actual)) {
            clearRateLimitRecovery("target_reobserved");
            trace("RATE_LIMIT_REOBSERVED", object("url", actual, "progress", webView.getProgress()));
            scheduleAutomationStep(0L);
            return;
        }
        boolean recoverable = isRecoverableTarget(actual);
        clearRateLimitRecovery(recoverable ? "target_restore_after_wait" : "target_changed_after_wait");
        if (recoverable) recoverTargetRoute("rate-limit recovery target reobserve");
        else finish(false, "TARGET_CHANGED", contextualDetail("rate-limit recovery observed a different conversation"));
    }

    private void clearRateLimitRecovery(String reason) {
        if (rateLimitWaiting || rateLimitRecoveryScheduled) {
            trace("RATE_LIMIT_RECOVERY_CLEAR", object("reason", reason,
                    "attempt", rateLimitBackoff.attempt()));
        }
        handler.removeCallbacks(rateLimitRecoveryRunnable);
        rateLimitWaiting = false;
        rateLimitRecoveryScheduled = false;
        rateLimitStartedAt = 0L;
        rateLimitBackoff.reset();
    }

    private void clearUiWaitRecovery(String reason) {
        if (uiWaitStartedAt != 0L) {
            trace("UI_WAIT_CLEAR", object("reason", reason, "attempt", uiWaitAttempts));
        }
        uiWaitStartedAt = 0L;
        uiWaitAttempts = 0;
    }

    private void clearTargetRecovery(String reason) {
        if (targetRecoveryStartedAt != 0L || targetRecoveryScheduled) {
            trace("TARGET_RECOVERY_CLEAR", object("reason", reason, "attempt", targetRecoveryBackoff.attempt()));
        }
        handler.removeCallbacks(targetRecoveryRunnable);
        targetRecoveryScheduled = false;
        targetRecoveryStartedAt = 0L;
        targetRecoveryBackoff.reset();
        routeRecoveryAttempts = 0;
    }

    private boolean matchesCurrentTarget(String actualUrl) {
        if (currentSchedule == null) return false;
        if ("existing".equals(currentSchedule.targetType)) {
            return TargetParser.matchesConversationIdentity(currentSchedule.targetUrl, actualUrl);
        }
        return TargetParser.matchesTarget(currentSchedule.targetType, currentSchedule.targetUrl, actualUrl);
    }

    private boolean isRecoverableTarget(String actualUrl) {
        if (actualUrl == null || actualUrl.isBlank() || "about:blank".equalsIgnoreCase(actualUrl)) return true;
        if (!TargetParser.isSupported(actualUrl) || currentSchedule == null) return false;
        RecoveryDecisionPolicy.ObservedLocation location = RecoveryDecisionPolicy.classify(
                currentSchedule.targetType, currentSchedule.targetUrl, actualUrl);
        return location != RecoveryDecisionPolicy.ObservedLocation.DIFFERENT_CONVERSATION;
    }

    private void recoverTargetRoute(String detail) {
        lastRetryDetail = detail;
        trace("TARGET_ROUTE_MISMATCH", object("detail", detail, "attempt", routeRecoveryAttempts,
                "requested", currentSchedule == null ? "" : currentSchedule.targetUrl, "actual", lastObservedUrl));
        if (currentSchedule == null || !isRecoverableTarget(lastObservedUrl)) {
            finish(false, "TARGET_CHANGED", contextualDetail(detail));
            return;
        }
        if (routeRecoveryAttempts >= MAX_ROUTE_RECOVERIES || System.currentTimeMillis() >= deadline) {
            finish(false, "TARGET_ROUTE_RECOVERY_FAILED", contextualDetail(detail));
            return;
        }
        if (targetRecoveryStartedAt == 0L) targetRecoveryStartedAt = System.currentTimeMillis();
        if (System.currentTimeMillis() - targetRecoveryStartedAt >= MAX_RATE_LIMIT_WAIT_MS) {
            finish(false, "TARGET_ROUTE_RECOVERY_FAILED", contextualDetail(detail));
            return;
        }
        if (targetRecoveryScheduled) return;
        routeRecoveryAttempts++;
        pageAttempts = 0;
        clearUiWaitRecovery("target_restore");
        startAsForeground(currentSchedule.name + " 대상 대화 복구 중 " + routeRecoveryAttempts + "/" + MAX_ROUTE_RECOVERIES);
        cancelAutomationStep();
        stepInFlight = false;
        RecoveryBackoff.Decision decision = targetRecoveryBackoff.next();
        targetRecoveryScheduled = true;
        trace("TARGET_ROUTE_RECOVERY_WAIT", object("attempt", routeRecoveryAttempts,
                "backoffAttempt", decision.attempt, "delayMs", decision.delayMs,
                "requested", currentSchedule.targetUrl, "actual", lastObservedUrl));
        handler.postDelayed(targetRecoveryRunnable, decision.delayMs);
    }

    private void performTargetRouteRecovery() {
        targetRecoveryScheduled = false;
        if (currentItem == null || currentSchedule == null || webView == null) return;
        long now = System.currentTimeMillis();
        if (now >= deadline || targetRecoveryStartedAt == 0L
                || now - targetRecoveryStartedAt >= MAX_RATE_LIMIT_WAIT_MS) {
            finish(false, "TARGET_ROUTE_RECOVERY_FAILED", timeoutDetail());
            return;
        }
        String actual = valueOrEmpty(webView.getUrl());
        lastObservedUrl = actual;
        if (matchesCurrentTarget(actual)) {
            clearTargetRecovery("target_reobserved");
            trace("TARGET_ROUTE_REOBSERVED", object("url", actual, "progress", webView.getProgress()));
            scheduleAutomationStep(0L);
            return;
        }
        if (webView.getProgress() < 100) {
            recoverTargetRoute("target restoration is still loading");
            return;
        }
        clearRateLimitRecovery("target_restore_navigation");
        navigationGeneration++;
        stepInFlight = false;
        trace("TARGET_ROUTE_RESTORE", object("attempt", routeRecoveryAttempts,
                "requested", currentSchedule.targetUrl, "actual", actual));
        webView.loadUrl(currentSchedule.targetUrl);
    }

    private String retryFailureStatus(String detail) {
        if (detail.contains("입력창 대기")) return "COMPOSER_NOT_FOUND";
        if (detail.contains("예약 프롬프트 입력") || detail.contains("입력 반영")) return "COMPOSER_INPUT_FAILED";
        if (detail.contains("Work 모드") || detail.contains("모드 실제 적용") || detail.contains("모드 전환"))
            return "WORK_MODE_SELECT_FAILED";
        if (detail.contains("전송 버튼")) return "SEND_BUTTON_UNAVAILABLE";
        if (detail.contains("전송 검증")) return "SUBMISSION_STATE_STALLED";
        return "AUTOMATION_RETRY_EXHAUSTED";
    }

    private String timeoutDetail() {
        return contextualDetail(lastRetryDetail.isBlank() ? "웹 자동화 제한 시간을 초과했습니다." : lastRetryDetail);
    }

    private String contextualDetail(String detail) {
        String actual = lastObservedUrl.isBlank() ? "확인 불가" : lastObservedUrl;
        return detail + " | requested=" + (currentSchedule == null ? "" : currentSchedule.targetUrl) + " | actual=" + actual;
    }

    private JSONObject parseObject(String raw) {
        try {
            Object value = new JSONTokener(raw).nextValue();
            if (value instanceof String) return new JSONObject((String) value);
            if (value instanceof JSONObject) return (JSONObject) value;
        } catch (JSONException error) {
            trace("SCRIPT_PARSE_ERROR", object("message", error.getMessage(), "raw", clip(raw, 12_000)));
        }
        return new JSONObject();
    }

    private void recoverEngine(String status, String detail) {
        int maxRetries = currentSchedule == null ? configStore.settings().optInt("maxRetries", 2)
                : Math.max(currentSchedule.retryCount, configStore.settings().optInt("maxRetries", 2));
        trace("ENGINE_RECOVERY", object("status", status, "detail", valueOrEmpty(detail), "engineAttempt", engineAttempt,
                "maxRetries", maxRetries));
        if (engineAttempt <= maxRetries && System.currentTimeMillis() < deadline) {
            cleanupWebViewOnly();
            handler.postDelayed(this::launchEngine, 1800L);
        } else {
            finish(false, status, contextualDetail(detail == null ? "웹 엔진을 시작하지 못했습니다." : detail));
        }
    }

    private void finish(boolean success, String status, String detail) {
        if (currentItem == null) return;
        long finishedAt = System.currentTimeMillis();
        String runId = currentItem.optString("runId");
        String scheduleId = currentItem.optString("scheduleId");
        boolean manual = currentItem.optBoolean("manual", false);
        trace("RUN_FINISHED", object("success", success, "status", status, "detail", detail,
                "durationMs", Math.max(0, finishedAt - (startedAt == 0 ? finishedAt : startedAt)),
                "traceDropped", traceDropped));
        String effectiveStatus = status;
        String effectiveDetail = detail == null ? "" : detail;
        try {
            logStore.append(runId, scheduleId, currentSchedule == null ? "예약" : currentSchedule.name, status, effectiveDetail,
                    startedAt == 0 ? finishedAt : startedAt, finishedAt, currentSchedule == null ? "" : currentSchedule.targetUrl,
                    success, traceEvents, environment());
        } catch (RuntimeException logError) {
            effectiveStatus = status + "_LOG_SAVE_FAILED";
            effectiveDetail = effectiveDetail + " | 실행 기록 저장 실패: " + valueOrEmpty(logError.getMessage());
        }
        if (currentSchedule != null) {
            currentSchedule.lastRunAt = finishedAt;
            currentSchedule.lastStatus = effectiveStatus;
            if (!manual && "once".equals(currentSchedule.recurrence)) currentSchedule.enabled = false;
            configStore.saveSchedule(currentSchedule);
            if (currentSchedule.enabled) AlarmEngine.scheduleNext(this, currentSchedule, finishedAt + 1000L);
            JSONObject settings = configStore.settings();
            if ((success && settings.optBoolean("notifySuccess", true)) || (!success && settings.optBoolean("notifyFailure", true))) {
                NotificationHelper.result(this, success, success ? "예약 프롬프트 전송 완료" : "예약 프롬프트 실행 실패",
                        currentSchedule.name + " · " + effectiveStatus, runId);
            }
        }
        try {
            queueStore.finish(runId);
        } catch (RuntimeException ignored) {
        }
        cleanupEngine();
        currentItem = null;
        currentSchedule = null;
        handler.postDelayed(this::processNext, 250L);
    }

    private JSONObject environment() {
        JSONObject value = new JSONObject();
        try {
            PackageInfo app = getPackageManager().getPackageInfo(getPackageName(), 0);
            value.put("appVersion", app.versionName == null ? "" : app.versionName);
            long appVersionCode = Build.VERSION.SDK_INT >= 28 ? app.getLongVersionCode() : app.versionCode;
            value.put("appVersionCode", appVersionCode);
            value.put("sdkInt", Build.VERSION.SDK_INT);
            value.put("androidRelease", Build.VERSION.RELEASE);
            value.put("manufacturer", Build.MANUFACTURER);
            value.put("model", Build.MODEL);
            PackageInfo webViewPackage = WebView.getCurrentWebViewPackage();
            if (webViewPackage != null) {
                value.put("webViewPackage", webViewPackage.packageName);
                value.put("webViewVersion", webViewPackage.versionName);
            }
            value.put("traceDropped", traceDropped);
        } catch (Exception error) {
            try { value.put("environmentError", valueOrEmpty(error.getMessage())); } catch (JSONException ignored) {}
        }
        return value;
    }

    private void resetTrace() {
        traceEvents = new JSONArray();
        traceDropped = 0;
    }

    private void trace(String type, JSONObject data) {
        if (traceEvents.length() >= MAX_TRACE_EVENTS) {
            traceDropped++;
            return;
        }
        JSONObject event = new JSONObject();
        try {
            long now = System.currentTimeMillis();
            event.put("at", now);
            event.put("elapsedMs", startedAt == 0 ? 0 : Math.max(0, now - startedAt));
            event.put("type", type);
            event.put("data", data == null ? new JSONObject() : data);
            traceEvents.put(event);
        } catch (JSONException ignored) {
        }
    }

    private JSONObject object(Object... pairs) {
        JSONObject object = new JSONObject();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            try {
                Object value = pairs[i + 1];
                object.put(String.valueOf(pairs[i]), value == null ? JSONObject.NULL : value);
            } catch (JSONException ignored) {
            }
        }
        return object;
    }

    private static String clip(String value, int maxLength) {
        String safe = valueOrEmpty(value);
        if (safe.length() <= maxLength) return safe;
        return safe.substring(0, maxLength) + "…(" + safe.length() + ")";
    }

    private void cleanupWebViewOnly() {
        cancelAutomationStep();
        handler.removeCallbacks(rateLimitRecoveryRunnable);
        handler.removeCallbacks(targetRecoveryRunnable);
        rateLimitWaiting = false;
        rateLimitRecoveryScheduled = false;
        targetRecoveryScheduled = false;
        rateLimitStartedAt = 0L;
        targetRecoveryStartedAt = 0L;
        rateLimitBackoff.reset();
        targetRecoveryBackoff.reset();
        navigationGeneration++;
        stepInFlight = false;
        if (webViewHost != null) {
            webViewHost.destroy();
            webViewHost = null;
            webView = null;
            return;
        }
        if (webView != null) {
            try {
                webView.setWebViewClient(null);
                webView.setWebChromeClient(null);
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.clearHistory();
                webView.removeAllViews();
                webView.destroy();
            } catch (Throwable ignored) {
            }
            webView = null;
        }
    }

    private void cleanupEngine() {
        handler.removeCallbacks(automationRunnable);
        handler.removeCallbacks(watchdogRunnable);
        cleanupWebViewOnly();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void onDestroy() {
        cleanupEngine();
        AutomationRuntimeGate.setScheduleActive(false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
