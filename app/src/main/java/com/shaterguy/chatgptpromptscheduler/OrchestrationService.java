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
import android.os.SystemClock;
import android.provider.Settings;
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;

/** Optional Protocol 3.x relay. Reservation execution always has priority. */
public final class OrchestrationService extends Service implements AutomationRuntimeGate.Listener {
    public static final String ACTION_RUN = "com.shaterguy.chatgptpromptscheduler.ORCHESTRATION_RUN";
    private static final int NOTIFICATION_ID = 7020;
    private static final long WAKE_LOCK_LEASE_MS = 60_000L;
    private static final long SOFT_YIELD_MS = ResponseTimingPolicy.SOFT_YIELD_MS;
    private static final long HARD_FALLBACK_MS = ResponseTimingPolicy.HARD_FALLBACK_MS;
    private static final long STOP_CONFIRMATION_GRACE_MS = 15_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable stepRunnable = this::runStep;
    private final Runnable resumeRunnable = this::ensureEngine;
    private final AdaptivePolling responsePolling = new AdaptivePolling();
    private final AdaptivePolling reconciliationPolling = new AdaptivePolling();
    private final AdaptivePolling initialTargetPolling = new AdaptivePolling();
    private final Runnable initialTargetReloadRunnable = this::performInitialTargetReload;
    private OrchestrationStore store;
    private OrchestrationRunLog runLog;
    private HeadlessWebViewHost host;
    private WebView webView;
    private int generation;
    private boolean evaluationInFlight;
    private boolean commitAuthorized;
    private long provisioningRecoveryStartedAt;
    private String loadedTarget = "";
    private PowerManager.WakeLock wakeLock;
    private int consecutiveEngineFailures;
    private long recoveryProbeStartedAt;
    private int missingUserTurnProbes;
    private long evaluationCount;
    private long wakeLockAcquiredAt;
    private long wakeLockLeaseExpiresAt;
    private long stopConfirmationStartedAt;
    private boolean reconciliationInitialized;
    private boolean reconciliationEvaluationInFlight;
    private ResumeReconciliation.RoomScan reconciliationChatScan;
    private ResumeReconciliation.RoomScan reconciliationWorkScan;
    private ResumeReconciliation.RoomScan reconciliationConfirmationChatScan;
    private ResumeReconciliation.RoomScan reconciliationConfirmationWorkScan;
    private ResumeReconciliation.Decision reconciliationDecision;
    private int reconciliationRescanAttempts;
    private boolean reconciliationDeliveryInProgress;
    private boolean reconciliationFinalTargetScan;
    private long reconciliationPollingEpoch;
    private boolean initialTargetReloadScheduled;
    private String initialTargetReloadReason = "";

    @Override
    public void onCreate() {
        super.onCreate();
        store = new OrchestrationStore(this);
        runLog = new OrchestrationRunLog(this);
        NotificationHelper.ensureChannels(this);
        AutomationRuntimeGate.addListener(this);
        PowerManager power = getSystemService(PowerManager.class);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                getPackageName() + ":orchestration-relay");
        wakeLock.setReferenceCounted(false);
        log("SERVICE_CREATE", "source=process");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // A null intent is Android's START_STICKY recreation. Every external start must be explicit.
        if (intent != null) {
            // A manual Resume can arrive while an older reconciliation is still in memory. Treat
            // the durable begin marker as a new transaction and discard those stale candidates.
            reconciliationInitialized = false;
            reconciliationEvaluationInFlight = false;
            reconciliationFinalTargetScan = false;
        }
        if (intent != null && !ACTION_RUN.equals(intent.getAction())) {
            log("SIGNAL_REJECTED", "reason=invalid_service_action");
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        log(intent == null ? "SERVICE_RECREATE" : "SERVICE_START", intent == null ? "source=sticky" : "source=explicit");
        startAsForeground(store.status());
        if (!NotificationHelper.orchestrationAlertsEnabled(this)) {
            store.setStatus("오토런 중계 실행 중 · 오류 알림 꺼짐");
        }
        if (!canRun()) {
            stopRelay();
            return START_NOT_STICKY;
        }
        initializeReconciliationIfNeeded();
        handler.post(this::ensureEngine);
        return START_STICKY;
    }

    private boolean canRun() {
        if (store.reconciling()) return store.active();
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
        if (store.reconciling()) {
            ensureReconciliationEngine();
            return;
        }
        if (OrchestrationStore.BOOTSTRAP_JOB_CREATED.equals(store.bootstrapState())) {
            store.startChatProvisioning();
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
        if (store.schedulePreempted()) {
            store.setSchedulePreempted(false);
            log("SCHEDULE_RESUME", "source=relay");
        }
        if (OrchestrationStore.DELIVERY_PREPARING.equals(store.deliveryState()) && !commitAuthorized)
            store.resetPreparing();
        if (webView != null && loadedTarget.equals(store.targetUrl())) {
            scheduleStep(500L);
            return;
        }
        launchEngine();
    }

    private void initializeReconciliationIfNeeded() {
        if (!store.reconciling() || reconciliationInitialized) return;
        // In-memory candidates are deliberately discarded after process recreation. Re-scan both
        // rooms from the beginning instead of replaying a partially persisted decision.
        reconciliationInitialized = true;
        reconciliationEvaluationInFlight = false;
        reconciliationChatScan = null;
        reconciliationWorkScan = null;
        reconciliationConfirmationChatScan = null;
        reconciliationConfirmationWorkScan = null;
        reconciliationDecision = null;
        reconciliationRescanAttempts = 0;
        reconciliationDeliveryInProgress = false;
        reconciliationFinalTargetScan = false;
        resetReconciliationPolling("reconcile_init");
        if (!OrchestrationStore.RECONCILIATION_SCAN_ROOMS.equals(store.reconciliationPhase())
                || !OrchestrationStore.SIDE_CHAT.equals(store.reconciliationSide())) {
            store.restartReconciliation();
            log("RESUME_RECONCILE_STARTED", "reason=process_recovery");
            resetReconciliationPolling("process_recovery");
        }
    }

    private void ensureReconciliationEngine() {
        if (scheduleHasPriority()) {
            yieldForSchedule();
            return;
        }
        initializeReconciliationIfNeeded();
        acquireWakeLock();
        String target = currentRelayTargetUrl();
        if (webView != null && loadedTarget.equals(target)) {
            scheduleStep(500L);
            return;
        }
        launchEngine();
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void launchEngine() {
        cleanupWebView();
        resetResponsePolling("WEBVIEW_LAUNCH");
        if (scheduleHasPriority()) {
            yieldForSchedule();
            return;
        }
        loadedTarget = currentRelayTargetUrl();
        log("WEBVIEW_LAUNCH", "generation=" + generation);
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
            settings.setUserAgentString(userAgent + " ChatGPTPromptScheduler/0.1.19 Orchestration/3.3.0");
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    generation++;
                    evaluationInFlight = false;
                    resetResponsePolling("PAGE_START");
                    handler.removeCallbacks(stepRunnable);
                    log("WEBVIEW_PAGE_START", "generation=" + generation);
                    if (!matchesExpectedTarget(url)) {
                        if (store.initialStartPending()) {
                            // about:blank/home can be a transient SPA hop before ChatGPT restores
                            // the requested conversation. Never authorize JS on this URL.
                            log("INITIAL_START_TRANSIENT_ROUTE", "phase=page_start");
                        } else {
                            pauseWithError("TARGET_CHANGED", "중계 대상 대화가 바뀌었습니다.");
                        }
                    }
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    if (!matchesExpectedTarget(url)) {
                        if (store.initialStartPending()) reloadInitialStartTarget("page_finish");
                        else pauseWithError("TARGET_CHANGED", "중계 대상 대화가 바뀌었습니다.");
                        return;
                    }
                    resetInitialTargetRetry();
                    log("WEBVIEW_PAGE_FINISH", "progress=" + view.getProgress());
                    // A page-load delay is a one-off readiness delay, not a recurring
                    // reconciliation cadence. Repeated idle/generating checks use AdaptivePolling.
                    scheduleStep(store.reconciling() ? 500L : 1800L);
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request.isForMainFrame()) {
                        log("WEBVIEW_ERROR", "type=network");
                        pauseWithError("NETWORK_ERROR", "중계 대화를 불러오는 중 네트워크 오류가 발생했습니다.");
                    }
                }

                @Override
                public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                    if (request.isForMainFrame()) {
                        log("WEBVIEW_ERROR", "type=http;code=" + response.getStatusCode());
                        pauseWithError("HTTP_ERROR", "중계 대화 서버가 오류 응답을 반환했습니다.");
                    }
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    if (!request.isForMainFrame()) return false;
                    String requested = String.valueOf(request.getUrl());
                    if (matchesExpectedTarget(requested)) return false;
                    if (store.initialStartPending()) handler.post(() -> reloadInitialStartTarget("navigation"));
                    return true;
                }

                @Override
                public void onReceivedSslError(WebView view, SslErrorHandler sslHandler, SslError error) {
                    sslHandler.cancel();
                    log("WEBVIEW_ERROR", "type=ssl");
                    pauseWithError("SSL_ERROR", "SSL 인증서 오류로 중계를 멈췄습니다.");
                }

                @Override
                public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    log("RENDERER_GONE", "crash=" + (detail != null && detail.didCrash()));
                    webView = null;
                    if (host != null) {
                        host.destroy();
                        host = null;
                    }
                    generation++;
                    evaluationInFlight = false;
                    resetResponsePolling("RENDERER_GONE");
                    consecutiveEngineFailures++;
                    if (consecutiveEngineFailures >= 3) {
                        pauseWithError("RENDERER_FAILED", "WebView 렌더러가 연속으로 종료되어 중계를 멈췄습니다.");
                    } else {
                        store.setStatus("WebView 렌더러 복구 중");
                        log("RENDERER_RECOVERY", "attempt=" + consecutiveEngineFailures);
                        handler.postDelayed(resumeRunnable, 2000L);
                    }
                    return true;
                }
            });
            webView.loadUrl(loadedTarget);
        } catch (Throwable error) {
            log("WEBVIEW_INIT_FAILED", "attempt=" + (consecutiveEngineFailures + 1));
            cleanupWebView();
            consecutiveEngineFailures++;
            if (consecutiveEngineFailures >= 3) {
                pauseWithError("WEBVIEW_INIT_FAILED", "WebView를 연속으로 초기화하지 못해 중계를 멈췄습니다.");
            } else {
                store.setStatus("WebView 복구 대기");
                log("RENDERER_RECOVERY", "attempt=" + consecutiveEngineFailures);
                handler.postDelayed(resumeRunnable, 2500L);
            }
        }
    }

    private void scheduleStep(long delayMs) {
        handler.removeCallbacks(stepRunnable);
        if (webView != null && canRun()) {
            acquireWakeLock();
            handler.postDelayed(stepRunnable, delayMs);
        }
    }

    private void runStep() {
        if (scheduleHasPriority()) {
            yieldForSchedule();
            return;
        }
        if (webView == null || evaluationInFlight || !canRun()) return;
        if (store.reconciling()) {
            runReconciliationStep();
            return;
        }
        if (store.bootstrapProvisioning()) {
            runProvisioningStep();
            return;
        }
        String actualUrl = webView.getUrl();
        if (!matchesExpectedTarget(actualUrl)) {
            if (store.initialStartPending()) reloadInitialStartTarget("step_guard");
            else pauseWithError("TARGET_CHANGED", "중계 대상 대화가 바뀌어 자동 전송을 멈췄습니다.");
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
            String previous = store.deliveryState();
            store.markSubmitting(); // durable fail-closed boundary immediately before click-capable JS
            logStateTransition(previous);
            state = OrchestrationStore.DELIVERY_SUBMITTING;
        }

        String deliveryPrompt = OrchestrationStore.DELIVERY_PENDING.equals(state)
                ? store.ensureStampedPrompt() : store.deliveryPrompt();
        if (deliveryPrompt.isEmpty()) {
            pauseWithError("DELIVERY_PROMPT_MISSING", "현재 중계 전달 프롬프트를 복구하지 못했습니다.");
            return;
        }

        boolean initialStart = store.initialStartPending();
        String script;
        if (OrchestrationStore.DELIVERY_PENDING.equals(state)) {
            script = initialStart ? OrchestrationScript.prepareInitialStart(deliveryPrompt)
                    : OrchestrationScript.prepare(deliveryPrompt);
        } else if (OrchestrationStore.DELIVERY_SUBMITTING.equals(state)) {
            if (clickAttempt) {
                script = initialStart ? OrchestrationScript.commitInitialStart(deliveryPrompt)
                        : OrchestrationScript.commit(deliveryPrompt);
            } else {
                script = initialStart ? OrchestrationScript.recoverInitialStartSubmission(
                        deliveryPrompt, store.initialStartBaselineCount())
                        : OrchestrationScript.recoverSubmission(deliveryPrompt);
            }
        } else if (OrchestrationStore.DELIVERY_SUBMITTED.equals(state)) {
            script = initialStart ? OrchestrationScript.confirmInitialStartSubmission(
                    deliveryPrompt, store.initialStartBaselineCount())
                    : OrchestrationScript.confirmSubmission(deliveryPrompt);
        } else if (OrchestrationStore.DELIVERY_WAITING_RESPONSE.equals(state)) {
            script = store.stopGenerationSubmitting()
                    ? OrchestrationScript.stopGeneration() : OrchestrationScript.observe(deliveryPrompt);
        } else {
            pauseWithError("STATE_INVALID", "복구할 수 없는 중계 전달 상태입니다.");
            return;
        }

        WebView active = webView;
        int activeGeneration = generation;
        long activeEpoch = store.epoch();
        String evaluatedState = state;
        boolean evaluatedClickAttempt = clickAttempt;
        boolean evaluatedStopGeneration = OrchestrationStore.DELIVERY_WAITING_RESPONSE.equals(state)
                && store.stopGenerationSubmitting();
        evaluationInFlight = true;
        evaluationCount = evaluationCount == Long.MAX_VALUE ? Long.MAX_VALUE : evaluationCount + 1L;
        log("POLL_EVALUATE", "count=" + evaluationCount + ";state=" + safeCode(state)
                + ";tier=" + responsePolling.tier());
        active.evaluateJavascript(script, raw -> {
            evaluationInFlight = false;
            if (active != webView || activeGeneration != generation || activeEpoch != store.epoch()) return;
            if (scheduleHasPriority()) {
                yieldForSchedule();
                return;
            }
            if (!matchesExpectedTarget(active.getUrl())) {
                if (store.initialStartPending()) reloadInitialStartTarget("evaluation_guard");
                else pauseWithError("TARGET_CHANGED", "스크립트 실행 중 중계 대상 대화가 바뀌었습니다.");
                return;
            }
            JSONObject result = parseObject(raw);
            if (result.has("status")) consecutiveEngineFailures = 0;
            if (OrchestrationStore.DELIVERY_PENDING.equals(evaluatedState)) handlePrepare(result);
            else if (OrchestrationStore.DELIVERY_SUBMITTING.equals(evaluatedState))
                handleSubmission(result, evaluatedClickAttempt);
            else if (OrchestrationStore.DELIVERY_SUBMITTED.equals(evaluatedState)) handleConfirmation(result);
            else if (evaluatedStopGeneration) handleStopGeneration(result);
            else handleObservation(result);
        });
    }

    private void runProvisioningStep() {
        String actualUrl = webView.getUrl();
        if (!TargetParser.matchesProjectIdentity(store.runProjectUrl(), actualUrl)) {
            pauseWithError("PROJECT_ENTRY_FAILED", "지정한 ChatGPT 프로젝트에 진입하지 못했습니다.");
            return;
        }
        String side = store.runChatUrl().isEmpty() ? OrchestrationStore.SIDE_CHAT : OrchestrationStore.SIDE_WORK;
        String state = store.bootstrapState();
        String prompt = store.stampedPrompt();
        if (prompt.isEmpty()) prompt = store.ensureStampedPrompt();
        if (prompt.isEmpty()) {
            pauseWithError("BOOTSTRAP_PROMPT_MISSING", "영속 bootstrap 프롬프트를 복구하지 못했습니다.");
            return;
        }
        boolean submitting = OrchestrationStore.BOOTSTRAP_CHAT_SUBMITTING.equals(state)
                || OrchestrationStore.BOOTSTRAP_WORK_SUBMITTING.equals(state);
        boolean clickAttempt = !submitting && commitAuthorized;
        if (clickAttempt) {
            if (scheduleHasPriority()) { yieldForSchedule(); return; }
            commitAuthorized = false;
            store.markBootstrapSubmitting(side);
            submitting = true;
            state = store.bootstrapState();
        }
        if (OrchestrationStore.SIDE_WORK.equals(side)
                && OrchestrationStore.BOOTSTRAP_WORK_PROVISIONING.equals(state)) {
            store.markWorkPreferencesSetting();
            state = store.bootstrapState();
        }
        String script;
        if (submitting && !clickAttempt) {
            script = ProvisioningScript.observe(store.runProjectUrl(), prompt);
            if (provisioningRecoveryStartedAt == 0L) provisioningRecoveryStartedAt = SystemClock.elapsedRealtime();
        } else if (clickAttempt) {
            script = ProvisioningScript.commit(store.runProjectUrl(), prompt, store.runJobId(), side);
        } else {
            script = ProvisioningScript.prepare(side, store.runProjectUrl(), prompt, store.runJobId(),
                    store.runWorkModel(), store.runReasoningEffort());
        }
        WebView active = webView;
        int activeGeneration = generation;
        long activeEpoch = store.epoch();
        boolean evaluatedClick = clickAttempt;
        boolean evaluatedSubmitting = submitting;
        evaluationInFlight = true;
        active.evaluateJavascript(script, raw -> {
            evaluationInFlight = false;
            if (active != webView || activeGeneration != generation || activeEpoch != store.epoch()) return;
            if (scheduleHasPriority()) { yieldForSchedule(); return; }
            JSONObject result = parseObject(raw);
            String status = result.optString("status", "");
            String url = result.optString("url", active.getUrl());
            if ("CONFIRMED".equals(status)) {
                try {
                    store.confirmBootstrapSubmission(side, url);
                } catch (IllegalArgumentException error) {
                    pauseWithError("CONVERSATION_IDENTITY_INVALID", error.getMessage());
                    return;
                }
                provisioningRecoveryStartedAt = 0L;
                log("BOOTSTRAP_URL_CAPTURED", "side=" + safeCode(side));
                cleanupWebView();
                handler.post(this::ensureEngine);
                return;
            }
            if ("READY".equals(status)) {
                if (OrchestrationStore.SIDE_WORK.equals(side)) store.markWorkPreferencesVerified();
                commitAuthorized = true;
                provisioningRecoveryStartedAt = 0L;
                scheduleStep(0L);
                return;
            }
            if ("SUBMITTED".equals(status)) {
                provisioningRecoveryStartedAt = SystemClock.elapsedRealtime();
                scheduleStep(900L);
                return;
            }
            if ("RETRY".equals(status)) {
                if (evaluatedSubmitting && !evaluatedClick && provisioningRecoveryStartedAt > 0L
                        && SystemClock.elapsedRealtime() - provisioningRecoveryStartedAt >= 20_000L) {
                    pauseAmbiguous(OrchestrationStore.SIDE_WORK.equals(side)
                            ? "Work 첫 요청의 제출 여부를 확인할 수 없어 자동 재전송하지 않습니다."
                            : "일반 Chat 첫 요청의 제출 여부를 확인할 수 없어 자동 재전송하지 않습니다.");
                    return;
                }
                if (!evaluatedSubmitting && System.currentTimeMillis() - store.phaseStartedAt() >= 120_000L) {
                    String retryDetail = result.optString("detail", "");
                    String code;
                    if (!OrchestrationStore.SIDE_WORK.equals(side)) code = "CHAT_CREATE_FAILED";
                    else if (retryDetail.contains("모드")) code = "WORK_MODE_SELECT_FAILED";
                    else if (retryDetail.contains("모델")) code = "WORK_MODEL_SELECT_FAILED";
                    else if (retryDetail.contains("추론")) code = "WORK_REASONING_SELECT_FAILED";
                    else code = "WORK_PREFERENCES_NOT_VERIFIED";
                    pauseWithError(code, OrchestrationStore.SIDE_WORK.equals(side)
                            ? "지정한 Work 모드/모델/추론 정도의 실제 적용을 확인하지 못했습니다."
                            : "프로젝트 새 일반 Chat 입력 화면을 확인하지 못했습니다.");
                    return;
                }
                retry(result.optString("detail", "bootstrap 준비 대기"), 900L);
                return;
            }
            String code = switch (status) {
                case "AUTH_REQUIRED" -> "AUTH_REQUIRED";
                case "PROJECT_MISMATCH", "TARGET_CONTEXT_MISMATCH" -> "PROJECT_MISMATCH";
                case "EXISTING_CONVERSATION", "WRONG_CONVERSATION" -> "CONVERSATION_NOT_NEW";
                case "DRAFT_CHANGED" -> "BOOTSTRAP_DRAFT_CHANGED";
                case "SEND_UNAVAILABLE", "DOM_STRUCTURE_ERROR" -> "DOM_STRUCTURE_ERROR";
                default -> "BOOTSTRAP_SCRIPT_ERROR";
            };
            pauseWithError(code, result.optString("detail", "bootstrap 화면 상태를 확인하지 못했습니다."));
        });
    }

    private void runReconciliationStep() {
        if (reconciliationEvaluationInFlight) return;
        String phase = store.reconciliationPhase();
        String script;
        if (OrchestrationStore.RECONCILIATION_TARGET_SCAN.equals(phase)) {
            if (reconciliationDecision == null || reconciliationDecision.selected == null) {
                restartReconciliation("target_decision_missing");
                return;
            }
            script = OrchestrationScript.reconcileTarget(reconciliationDecision.prompt(), store.runJobId());
        } else {
            if (!OrchestrationStore.RECONCILIATION_SCAN_ROOMS.equals(phase)
                    && !OrchestrationStore.RECONCILIATION_CONFIRM_ROOMS.equals(phase)
                    && !OrchestrationStore.RECONCILIATION_SOURCE_FRESHNESS.equals(phase)
                    && !OrchestrationStore.RECONCILIATION_WAITING_IDLE.equals(phase)) {
                restartReconciliation("phase_recovery");
                return;
            }
            script = OrchestrationScript.reconcileScan(store.runJobId());
        }
        WebView active = webView;
        int activeGeneration = generation;
        long activeEpoch = store.epoch();
        String evaluatedPhase = phase;
        reconciliationEvaluationInFlight = true;
        evaluationInFlight = true;
        log("RESUME_RECONCILE_EVALUATE", "phase=" + safeCode(phase)
                + ";side=" + safeCode(store.reconciliationSide()));
        active.evaluateJavascript(script, raw -> {
            reconciliationEvaluationInFlight = false;
            evaluationInFlight = false;
            if (active != webView || activeGeneration != generation || activeEpoch != store.epoch()) return;
            if (scheduleHasPriority()) {
                yieldForSchedule();
                return;
            }
            if (!matchesExpectedTarget(active.getUrl())) {
                pauseReconciliationError("TARGET_CHANGED", "재개 재구성 중 대상 대화가 바뀌었습니다.");
                return;
            }
            JSONObject result = parseObject(raw);
            if (OrchestrationStore.RECONCILIATION_TARGET_SCAN.equals(evaluatedPhase))
                handleReconciliationTarget(result);
            else handleReconciliationRoom(result, store.reconciliationSide());
        });
    }

    private void handleReconciliationRoom(JSONObject result, String side) {
        String status = result.optString("status", "SCRIPT_RESULT_INVALID");
        log(OrchestrationStore.SIDE_CHAT.equals(side) ? "RESUME_ROOM_SCAN_CHAT" : "RESUME_ROOM_SCAN_WORK",
                "status=" + safeCode(status));
        log("RESUME_ROOM_SCAN_RESULT", "side=" + safeCode(side) + ";status=" + safeCode(status));
        if ("AUTH_REQUIRED".equals(status)) {
            pauseReconciliationError("AUTH_REQUIRED", "재개 재구성 중 로그인 세션을 확인하지 못했습니다.");
            return;
        }
        if ("TARGET_CONTEXT_MISMATCH".equals(status) || "NETWORK_ERROR".equals(status)) {
            pauseReconciliationError(status, fixedScriptMessage(status));
            return;
        }
        if ("RETRY".equals(status)) {
            store.setStatus(OrchestrationStore.sideLabel(side) + " 재개 상태 확인 대기");
            scheduleReconciliationRetry("room_retry");
            return;
        }
        if (!"SCAN".equals(status)) {
            pauseReconciliationError("RECONCILE_SCAN_FAILED", "재개 대화 상태를 읽지 못했습니다.");
            return;
        }
        ResumeReconciliation.RoomScan scan = parseRoomScan(result, side);
        if (scan.authRequired) {
            pauseReconciliationError("AUTH_REQUIRED", "재개 재구성 중 명시적 로그인 화면을 확인했습니다.");
            return;
        }
        if (scan.generating) {
            log("RESUME_WAITING_FOR_IDLE", "side=" + safeCode(side));
            restartReconciliation("room_generating", true);
            scheduleReconciliationRetry("room_generating");
            return;
        }
        String phase = store.reconciliationPhase();
        if (OrchestrationStore.RECONCILIATION_CONFIRM_ROOMS.equals(phase)) {
            handleReconciliationConfirmationRoom(scan, side);
            return;
        }
        if (OrchestrationStore.RECONCILIATION_SOURCE_FRESHNESS.equals(phase)) {
            handleReconciliationSourceFreshness(scan, side);
            return;
        }
        log("ROOM_IDLE_CONFIRMED", "side=" + safeCode(side));
        if (OrchestrationStore.SIDE_CHAT.equals(side)) {
            reconciliationChatScan = scan;
            reconciliationWorkScan = null;
            store.setReconciliationSide(OrchestrationStore.SIDE_WORK,
                    "재개 상태 재구성 중 · Work 대화 확인");
            resetReconciliationPolling("discovery_room_switch");
            cleanupWebView();
            handler.post(this::ensureEngine);
            return;
        }
        reconciliationWorkScan = scan;
        ResumeReconciliation.Decision decision = ResumeReconciliation.select(
                reconciliationChatScan, reconciliationWorkScan);
        handleReconciliationDecision(decision);
    }

    private void handleReconciliationConfirmationRoom(ResumeReconciliation.RoomScan scan, String side) {
        log("RESUME_CONFIRMATION_ROOM", "side=" + safeCode(side));
        if (OrchestrationStore.SIDE_CHAT.equals(side)) {
            reconciliationConfirmationChatScan = scan;
            reconciliationConfirmationWorkScan = null;
            store.setReconciliationConfirmationSide(OrchestrationStore.SIDE_WORK,
                    "재개 후보 안정성 확인 중 · Work 재확인");
            resetReconciliationPolling("confirmation_room_switch");
            cleanupWebView();
            handler.post(this::ensureEngine);
            return;
        }

        reconciliationConfirmationWorkScan = scan;
        ResumeReconciliation.Decision confirmed = ResumeReconciliation.select(
                reconciliationConfirmationChatScan, reconciliationConfirmationWorkScan);
        if (reconciliationDecision == null || reconciliationDecision.selected == null
                || confirmed.type != ResumeReconciliation.DecisionType.ROUTE
                || confirmed.selected == null
                || !ResumeReconciliation.sameCandidate(reconciliationDecision.selected, confirmed.selected)) {
            log("RESUME_RECONCILE_STARTED", "reason=confirmation_candidate_changed");
            restartReconciliation("confirmation_candidate_changed", false);
            cleanupWebView();
            handler.post(this::ensureEngine);
            return;
        }

        log("RESUME_STABLE_IDLE_CONFIRMED", "source="
                + safeCode(confirmed.selected.sourceSide) + ";type="
                + safeCode(confirmed.selected.signal.type.name()) + ";step="
                + safeCode(confirmed.selected.positionStep) + ";round="
                + safeCode(confirmed.selected.positionRound));
        reconciliationFinalTargetScan = false;
        String target = reconciliationDecision.targetSide();
        store.setReconciliationTarget(target, OrchestrationStore.sideLabel(target)
                + " 재개 전달 직전 중복 여부 확인");
        resetReconciliationPolling("confirmation_complete");
        cleanupWebView();
        handler.post(this::ensureEngine);
    }

    private void handleReconciliationSourceFreshness(ResumeReconciliation.RoomScan scan, String side) {
        ResumeReconciliation.Candidate fresh = ResumeReconciliation.highestCandidate(scan);
        if (reconciliationDecision == null || reconciliationDecision.selected == null
                || fresh == null
                || !ResumeReconciliation.sameCandidate(reconciliationDecision.selected, fresh)) {
            log("RESUME_RECONCILE_STARTED", "reason=source_freshness_changed");
            restartReconciliation("source_freshness_changed", false);
            cleanupWebView();
            handler.post(this::ensureEngine);
            return;
        }
        log("RESUME_SOURCE_FRESHNESS_CONFIRMED", "side=" + safeCode(side)
                + ";type=" + safeCode(fresh.signal.type.name())
                + ";step=" + safeCode(fresh.positionStep)
                + ";round=" + safeCode(fresh.positionRound));
        reconciliationFinalTargetScan = true;
        String target = reconciliationDecision.targetSide();
        store.setReconciliationTarget(target, OrchestrationStore.sideLabel(target)
                + " 최종 재개 전달 중복 확인");
        resetReconciliationPolling("source_freshness_complete");
        cleanupWebView();
        handler.post(this::ensureEngine);
    }

    private ResumeReconciliation.RoomScan parseRoomScan(JSONObject result, String side) {
        List<ResumeReconciliation.Candidate> candidates = new ArrayList<>();
        JSONArray values = result.optJSONArray("candidates");
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                ResumeReconciliation.Candidate candidate = ResumeReconciliation.acceptCandidate(
                        store.runJobId(), side, value.optString("signal", ""),
                        value.optString("predecessor", ""), value.optString("predecessor_signal", ""),
                        value.optInt("predecessor_index", -1),
                        value.optInt("message_index", -1));
                if (candidate == null) continue;
                candidates.add(candidate);
                log("SIGNAL_CANDIDATE_FOUND", "side=" + safeCode(side)
                        + ";type=" + safeCode(candidate.signal.type.name())
                        + ";step=" + safeCode(candidate.positionStep)
                        + ";round=" + safeCode(candidate.positionRound));
            }
        }
        return new ResumeReconciliation.RoomScan(side, result.optBoolean("main_present", false),
                result.optBoolean("generating", false), false, candidates);
    }

    private void handleReconciliationDecision(ResumeReconciliation.Decision decision) {
        reconciliationDecision = decision;
        log("RESUME_RECONCILE_DECISION", "type=" + safeCode(decision.type.name())
                + ";reason=" + safeCode(decision.reason));
        if (decision.selected != null) {
            log("SIGNAL_SELECTED", "side=" + safeCode(decision.selected.sourceSide)
                    + ";type=" + safeCode(decision.selected.signal.type.name())
                    + ";step=" + safeCode(decision.selected.positionStep)
                    + ";round=" + safeCode(decision.selected.positionRound));
        }
        switch (decision.type) {
            case WAIT_FOR_IDLE -> {
                log("RESUME_WAITING_FOR_IDLE", "side=both");
                restartReconciliation("both_rooms_not_idle", true);
                scheduleReconciliationRetry("both_rooms_not_idle");
            }
            case AMBIGUOUS -> {
                store.reconciliationAmbiguous("RESUME_RECONCILE_AMBIGUOUS", decision.reason);
                log("RESUME_RECONCILE_AMBIGUOUS", "reason=" + safeCode(decision.reason));
                NotificationHelper.orchestrationError(this, activeTargetSide(), store.runJobId(),
                        store.currentStep(), store.currentRound(), "재개 상태를 자동으로 재구성하지 못했습니다.");
                stopRelay();
            }
            case USER_ACTION -> {
                store.waitForUser(decision.selected.signal, decision.selected.sourceSide);
                log("RESUME_STATE_REBUILT", "state=WAITING_USER;side="
                        + safeCode(decision.selected.sourceSide));
                NotificationHelper.orchestrationUserAction(this, decision.selected.sourceSide,
                        store.runJobId(), decision.selected.signal.step, decision.selected.signal.round,
                        decision.selected.signal.actionId);
                stopRelay();
            }
            case TERMINAL -> {
                store.finish(decision.selected.signal, decision.selected.sourceSide);
                log("RESUME_STATE_REBUILT", "state=TERMINAL;type="
                        + safeCode(decision.selected.signal.type.name()));
                NotificationHelper.orchestrationTerminal(this, decision.selected.signal.type, store.runJobId());
                stopRelay();
            }
            case ROUTE -> {
                reconciliationConfirmationChatScan = null;
                reconciliationConfirmationWorkScan = null;
                store.beginReconciliationConfirmation();
                resetReconciliationPolling("confirmation_start");
                cleanupWebView();
                handler.post(this::ensureEngine);
            }
        }
    }

    private void handleReconciliationTarget(JSONObject result) {
        String status = result.optString("status", "SCRIPT_RESULT_INVALID");
        log("RESUME_TARGET_SCAN_RESULT", "status=" + safeCode(status));
        if ("TARGET_CONTEXT_MISMATCH".equals(status) || "NETWORK_ERROR".equals(status)
                || "AUTH_REQUIRED".equals(status)) {
            pauseReconciliationError(status, fixedScriptMessage(status));
            return;
        }
        if ("RETRY".equals(status)) {
            scheduleReconciliationRetry("target_retry");
            return;
        }
        if ("TARGET_GENERATING".equals(status)
                || "TARGET_PROMPT_PRESENT_GENERATING".equals(status)) {
            if ("TARGET_PROMPT_PRESENT_GENERATING".equals(status)) {
                log("TARGET_PROMPT_ALREADY_PRESENT", "side=" + safeCode(store.reconciliationSide()));
            }
            log("RESUME_WAITING_FOR_IDLE", "side=" + safeCode(store.reconciliationSide()));
            restartReconciliation("target_generating", true);
            scheduleReconciliationRetry("target_generating");
            return;
        }
        if ("TARGET_PROMPT_PRESENT_WITH_RESPONSE".equals(status)) {
            log("TARGET_PROMPT_ALREADY_PRESENT", "side=" + safeCode(store.reconciliationSide()));
            if (reconciliationRescanAttempts++ < 1) {
                log("RESUME_RECONCILE_STARTED", "reason=target_response_recheck");
                restartReconciliation("target_response_recheck", false);
                cleanupWebView();
                handler.post(this::ensureEngine);
            } else {
                pauseReconciliationAmbiguous("RESUME_TARGET_PROMPT_PRESENT_UNRESOLVED");
            }
            return;
        }
        if ("TARGET_PROMPT_PRESENT_NO_RESPONSE".equals(status)) {
            log("TARGET_PROMPT_ALREADY_PRESENT", "side=" + safeCode(store.reconciliationSide()));
            log("RESUME_TARGET_PROMPT_PRESENT_NO_RESPONSE", "side=" + safeCode(store.reconciliationSide()));
            if (reconciliationDecision == null || reconciliationDecision.selected == null) {
                restartReconciliation("existing_prompt_selection_missing", false);
                cleanupWebView();
                handler.post(this::ensureEngine);
                return;
            }
            if (!reconciliationFinalTargetScan) {
                String source = reconciliationDecision.selected.sourceSide;
                log("RESUME_SOURCE_FRESHNESS_CHECK", "side=" + safeCode(source)
                        + ";reason=existing_prompt");
                store.setReconciliationSourceFreshness(source,
                        OrchestrationStore.sideLabel(source) + " 기존 프롬프트 복구 직전 후보 최신성 확인");
                resetReconciliationPolling("source_freshness_start_existing_prompt");
                cleanupWebView();
                handler.post(this::ensureEngine);
                return;
            }
            store.rebuildForExistingPrompt(reconciliationDecision.selected.signal,
                    reconciliationDecision.selected.sourceSide);
            reconciliationDeliveryInProgress = false;
            log("RESUME_STATE_REBUILT", "state=WAITING_RESPONSE;reason=existing_prompt");
            log("RESUME_EXISTING_PROMPT_MONITOR", "side="
                    + safeCode(reconciliationDecision.targetSide()));
            cleanupWebView();
            handler.post(this::ensureEngine);
            return;
        }
        if ("TARGET_PROMPT_MULTIPLE".equals(status)) {
            log("TARGET_PROMPT_ALREADY_PRESENT", "side=" + safeCode(store.reconciliationSide()));
            pauseReconciliationAmbiguous("RESUME_TARGET_PROMPT_MULTIPLE");
            return;
        }
        if (!"TARGET_PROMPT_ABSENT".equals(status)) {
            pauseReconciliationError("RECONCILE_TARGET_SCAN_FAILED", "재개 대상 대화의 중복 여부를 확인하지 못했습니다.");
            return;
        }
        if (reconciliationDecision == null || reconciliationDecision.selected == null) {
            restartReconciliation("target_selection_missing");
            return;
        }
        if (!reconciliationFinalTargetScan) {
            String source = reconciliationDecision.selected.sourceSide;
            log("RESUME_SOURCE_FRESHNESS_CHECK", "side=" + safeCode(source));
            store.setReconciliationSourceFreshness(source,
                    OrchestrationStore.sideLabel(source) + " 재개 후보 최신성 확인");
            resetReconciliationPolling("source_freshness_start");
            cleanupWebView();
            handler.post(this::ensureEngine);
            return;
        }
        log("RESUME_REPLAY_SUBMITTING", "side=" + safeCode(reconciliationDecision.targetSide())
                + ";type=" + safeCode(reconciliationDecision.selected.signal.type.name()));
        store.rebuildForReconciliation(reconciliationDecision.selected.signal,
                reconciliationDecision.selected.sourceSide);
        reconciliationDeliveryInProgress = true;
        log("RESUME_STATE_REBUILT", "state=DELIVERY_PENDING;side="
                + safeCode(reconciliationDecision.targetSide()));
        cleanupWebView();
        handler.post(this::ensureEngine);
    }

    private void pauseReconciliationAmbiguous(String code) {
        store.reconciliationAmbiguous(code, "재개 대상 대화에 이미 대응 프롬프트가 있어 자동 재전송하지 않았습니다.");
        log("RESUME_RECONCILE_AMBIGUOUS", "reason=" + safeCode(code));
        NotificationHelper.orchestrationError(this, activeTargetSide(), store.runJobId(),
                store.currentStep(), store.currentRound(), "재개 대상 프롬프트 상태가 불명확합니다.");
        stopRelay();
    }

    private void pauseReconciliationError(String code, String detail) {
        store.fail(code, safeDetail(detail));
        log("FAILED", "code=" + safeCode(code));
        NotificationHelper.orchestrationError(this, activeTargetSide(), store.runJobId(),
                store.currentStep(), store.currentRound(), safeDetail(detail));
        stopRelay();
    }

    private void restartReconciliation(String reason) {
        restartReconciliation(reason, false);
    }

    /**
     * Drops every in-memory DOM snapshot. When the same room remains generating, keep the
     * adaptive cadence; all real phase/candidate changes reset it for prompt responsiveness.
     */
    private void restartReconciliation(String reason, boolean preservePolling) {
        reconciliationChatScan = null;
        reconciliationWorkScan = null;
        reconciliationConfirmationChatScan = null;
        reconciliationConfirmationWorkScan = null;
        reconciliationDecision = null;
        reconciliationEvaluationInFlight = false;
        reconciliationFinalTargetScan = false;
        store.restartReconciliation();
        if (!preservePolling) resetReconciliationPolling(reason);
        log("RESUME_RECONCILE_STARTED", "reason=" + safeCode(reason));
    }

    private void resetReconciliationPolling(String reason) {
        reconciliationPollingEpoch = reconciliationPollingEpoch == Long.MAX_VALUE
                ? Long.MAX_VALUE : reconciliationPollingEpoch + 1L;
        reconciliationPolling.reset(reconciliationPollingEpoch);
        log("POLLING_RESET", "phase=resume;reason=" + safeCode(reason));
    }

    private void scheduleReconciliationRetry(String reason) {
        AdaptivePolling.Decision decision = reconciliationPolling.onRetry(reconciliationPollingEpoch);
        if (decision.tierChanged) {
            log("POLLING_TIER_CHANGED", "phase=resume;tier=" + decision.tier
                    + ";delay_ms=" + decision.delayMs + ";retry=" + decision.retryCount);
        }
        log("POLL_RETRY", "phase=resume;retry=" + decision.retryCount
                + ";tier=" + decision.tier + ";delay_ms=" + decision.delayMs);
        store.incrementPoll();
        store.setStatus(OrchestrationStore.sideLabel(activeTargetSide()) + " · 재개 상태 확인 대기");
        if (webView != null && loadedTarget.equals(currentRelayTargetUrl())) {
            scheduleStep(decision.delayMs);
            return;
        }
        cleanupWebView();
        handler.removeCallbacks(resumeRunnable);
        handler.postDelayed(resumeRunnable, decision.delayMs);
    }

    private void handlePrepare(JSONObject result) {
        String status = result.optString("status", "SCRIPT_RESULT_INVALID");
        logScriptResult("PREPARE_RESULT", status);
        switch (status) {
            case "READY" -> {
                if (store.initialStartPending()) {
                    store.setInitialStartBaselineCount(result.optInt("matching_user_turns", 0));
                    log("INITIAL_START_READY", "existing_turns=" + store.initialStartBaselineCount());
                }
                String previous = store.deliveryState();
                store.markPreparing();
                logStateTransition(previous);
                resetResponsePolling("PREPARE_READY");
                commitAuthorized = true;
                scheduleStep(0L);
            }
            case "ALREADY_SUBMITTED" -> {
                String previous = store.deliveryState();
                store.markSubmitted();
                logStateTransition(previous);
                if (reconciliationDeliveryInProgress) {
                    log("RESUME_REPLAY_CONFIRMED", "result=ALREADY_SUBMITTED");
                    reconciliationDeliveryInProgress = false;
                }
                resetResponsePolling("PREPARE_ALREADY_SUBMITTED");
                scheduleStep(250L);
            }
            case "RETRY" -> {
                if (System.currentTimeMillis() - store.phaseStartedAt() >= 60_000L)
                    pauseWithError("DOM_COMPOSER_NOT_FOUND", "60초 동안 ChatGPT 프롬프트 입력창을 준비하지 못했습니다.");
                else retry("프롬프트 입력 준비 대기", 1000L);
            }
            case "AUTH_REQUIRED", "DRAFT_PRESENT", "TARGET_CONTEXT_MISMATCH", "NETWORK_ERROR", "DOM_STRUCTURE_ERROR" -> {
                if ("AUTH_REQUIRED".equals(status)) log("AUTH_REQUIRED", "reason=structural_gate");
                pauseWithError(status, fixedScriptMessage(status));
            }
            default -> pauseWithError("SUBMIT_SCRIPT_ERROR", "중계 전송 준비 스크립트 오류: " + safeCode(status));
        }
    }

    private void handleSubmission(JSONObject result, boolean clickAttempt) {
        String status = result.optString("status", "SCRIPT_RESULT_INVALID");
        log("SUBMISSION_RESULT", "status=" + safeCode(status) + ";click=" + (clickAttempt ? "1" : "0"));
        if (clickAttempt && ("SUBMITTED".equals(status) || "ALREADY_SUBMITTED".equals(status))) {
            String previous = store.deliveryState();
            store.markSubmitted();
            logStateTransition(previous);
            if (reconciliationDeliveryInProgress) {
                log("RESUME_REPLAY_CONFIRMED", "result=" + safeCode(status));
                reconciliationDeliveryInProgress = false;
            }
            resetResponsePolling("SUBMITTED");
            startAsForeground(store.status());
            scheduleStep(500L);
            return;
        }
        if (!clickAttempt && "SUBMITTED".equals(status)) {
            recoveryProbeStartedAt = 0L;
            String previous = store.deliveryState();
            acceptInitialStartTargetIfNeeded();
            store.markWaiting();
            log("RESPONSE_EPOCH_RESET", "reason=RECOVERY_SUBMITTED");
            logStateTransition(previous);
            resetResponsePolling("RECOVERY_SUBMITTED");
            startAsForeground(store.status());
            scheduleStep(2500L);
            return;
        }
        if (!clickAttempt && "RECOVERY_ABSENT".equals(status)) {
            long now = System.currentTimeMillis();
            if (recoveryProbeStartedAt == 0L) recoveryProbeStartedAt = now;
            if (now - recoveryProbeStartedAt < 15_000L) {
                log("PROCESS_RECOVERY_PROBE", "status=absent");
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
        logScriptResult("CONFIRM_RESULT", status);
        switch (status) {
            case "SUBMITTED" -> {
                String previous = store.deliveryState();
                acceptInitialStartTargetIfNeeded();
                store.markWaiting();
                log("RESPONSE_EPOCH_RESET", "reason=CONFIRM_SUBMITTED");
                logStateTransition(previous);
                resetResponsePolling("CONFIRM_SUBMITTED");
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

    private void handleStopGeneration(JSONObject result) {
        String status = result.optString("status", "SCRIPT_RESULT_INVALID");
        logScriptResult("STOP_GENERATION_RESULT", status);
        switch (status) {
            case "STOP_GENERATION_CLICKED" -> {
                store.markStopGenerationClicked();
                stopConfirmationStartedAt = SystemClock.elapsedRealtime();
                log("STOP_GENERATION_CLICKED", "response_epoch=" + store.responseEpoch());
                scheduleStep(1000L);
            }
            case "STOP_GENERATION_UNAVAILABLE", "STOP_GENERATION_AMBIGUOUS" -> {
                store.markStopGenerationAmbiguous();
                log("STOP_GENERATION_AMBIGUOUS", "reason=" + safeCode(status));
                pauseAmbiguous("98분 장시간 보호를 실행했지만 생성 중지 버튼 상태가 명확하지 않습니다. 자동 재클릭하지 않습니다.");
            }
            case "TARGET_CONTEXT_MISMATCH", "NETWORK_ERROR" ->
                    pauseWithError(status, fixedScriptMessage(status));
            default -> {
                store.markStopGenerationAmbiguous();
                log("STOP_GENERATION_AMBIGUOUS", "reason=unexpected_result");
                pauseAmbiguous("생성 중지 결과가 불명확하여 자동 재클릭하지 않습니다.");
            }
        }
    }

    private void handleObservation(JSONObject result) {
        String status = result.optString("status", "SCRIPT_RESULT_INVALID");
        logScriptResult("OBSERVE_RESULT", status);
        boolean assistantPresent = result.optBoolean("assistant_present", false);
        boolean streaming = result.optBoolean("streaming", false);
        boolean stopAvailable = result.optBoolean("stop_available", false);

        if (assistantPresent) {
            String bootIdentity = currentBootIdentity();
            String previousBoot = store.responseStartBootIdentity();
            long previousStart = store.responseStartElapsed();
            boolean rebootTimebaseChanged = previousStart > 0L && !previousBoot.isEmpty()
                    && !previousBoot.equals(bootIdentity);
            if (store.ensureResponseEpoch(bootIdentity, SystemClock.elapsedRealtime())) {
                if (rebootTimebaseChanged) log("REBOOT_TIMEBASE_RESET", "response_epoch=" + store.responseEpoch());
                log("RESPONSE_EPOCH_STARTED", "response_epoch=" + store.responseEpoch());
            }
            long responseAge = store.responseAgeMs(bootIdentity, SystemClock.elapsedRealtime());
            ResponseTimingPolicy.Decision timing = ResponseTimingPolicy.evaluate(responseAge, streaming,
                    store.softYieldDue(), !OrchestrationStore.STOP_GENERATION_NONE.equals(store.stopGenerationState()));
            if (timing.softYieldDue && store.markSoftYieldDue()) {
                log("SOFT_YIELD_DUE", "response_epoch=" + store.responseEpoch());
            }
            if (timing.hardFallbackDue) {
                if (stopAvailable && store.markStopGenerationSubmitting()) {
                    log("HARD_FALLBACK_DUE", "response_epoch=" + store.responseEpoch());
                    log("STOP_GENERATION_SUBMITTING", "response_epoch=" + store.responseEpoch());
                    scheduleStep(0L);
                    return;
                }
                store.markStopGenerationAmbiguous();
                pauseAmbiguous("98분 장시간 보호 시점에 생성 중지 버튼을 확인할 수 없어 자동 중단하지 않습니다.");
                return;
            }
        }

        if (store.stopGenerationClicked()) {
            boolean confirmed = assistantPresent && !streaming && !stopAvailable
                    && ("RETRY".equals(status) || "CANDIDATE".equals(status));
            if (confirmed) {
                store.markStopGenerationConfirmed();
                log("STOP_GENERATION_CONFIRMED", "response_epoch=" + store.responseEpoch());
                String sourceSide = store.monitoringSide();
                store.prepareSameSideFallback(sourceSide);
                log("CONTINUE_SAME_DELIVERY", "source=" + safeCode(sourceSide)
                        + ";reason=hard_fallback;continuation_epoch=" + store.continuationEpoch());
                log("RESPONSE_EPOCH_RESET", "reason=HARD_FALLBACK_CONTINUATION");
                stopConfirmationStartedAt = 0L;
                resetResponsePolling("HARD_FALLBACK_CONFIRMED");
                cleanupWebView();
                handler.post(this::ensureEngine);
                return;
            }
            if ("AUTH_REQUIRED".equals(status) || "NETWORK_ERROR".equals(status)
                    || "DOM_STRUCTURE_ERROR".equals(status) || "TARGET_CONTEXT_MISMATCH".equals(status)) {
                pauseWithError(status, fixedScriptMessage(status));
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (stopConfirmationStartedAt == 0L) stopConfirmationStartedAt = now;
            if (now - stopConfirmationStartedAt >= STOP_CONFIRMATION_GRACE_MS) {
                store.markStopGenerationAmbiguous();
                log("STOP_GENERATION_AMBIGUOUS", "reason=confirmation_timeout");
                pauseAmbiguous("생성 중지 후 스트리밍 종료를 확인하지 못해 자동 continuation을 만들지 않습니다.");
            } else {
                retry("생성 중지 후 종료 상태 확인", AdaptivePolling.FAST_DELAY_MS);
            }
            return;
        }

        if ("USER_TURN_MISSING".equals(status)) {
            resetResponsePolling("USER_TURN_MISSING");
            missingUserTurnProbes++;
            if (missingUserTurnProbes >= 3)
                pauseWithError("DOM_USER_TURN_MISSING", "제출 확인된 사용자 턴을 ChatGPT 대화 DOM에서 찾지 못했습니다.");
            else scheduleStep(1500L);
            return;
        }
        missingUserTurnProbes = 0;
        if ("RETRY".equals(status)) {
            retry("정상 응답 대기", AdaptivePolling.FAST_DELAY_MS);
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
        resetResponsePolling("CANDIDATE");
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
        boolean bootstrap = store.bootstrapSignalPending();
        OrchestrationSignal.ParseResult parsed = bootstrap
                ? OrchestrationSignal.validateBootstrap(response, store.runJobId(), sourceSide,
                        store.lastAcceptedSignal())
                : OrchestrationSignal.validate(response, store.runJobId(), sourceSide,
                        store.currentStep(), store.currentRound(), store.lastAcceptedSignal(),
                        store.responseEpoch(), store.lastSignalResponseEpoch());
        if (!parsed.isValid()) {
            log("SIGNAL_REJECTED", "reason=" + safeCode(parsed.errorCode.name()));
            pauseWithProtocolError(parsed.errorCode, sourceSide);
            return;
        }
        OrchestrationSignal signal = parsed.signal;
        log("SIGNAL_ACCEPTED", "type=" + safeCode(signal.type.name())
                + ";bootstrap=" + (bootstrap ? "1" : "0"));
        if (signal.type == OrchestrationSignal.Type.DONE || signal.type == OrchestrationSignal.Type.PAUSE
                || signal.type == OrchestrationSignal.Type.ABORTED) {
            log("TERMINAL_COMMIT", "type=" + safeCode(signal.type.name()));
            store.finish(signal, sourceSide);
            NotificationHelper.orchestrationTerminal(this, signal.type, store.runJobId());
            stopRelay();
            return;
        }
        if (signal.type == OrchestrationSignal.Type.USER_ACTION_REQUIRED) {
            log("USER_ACTION_REQUIRED", "source=" + safeCode(sourceSide));
            store.waitForUser(signal, sourceSide);
            NotificationHelper.orchestrationUserAction(this, sourceSide, store.runJobId(),
                    signal.step, signal.round, signal.actionId);
            stopRelay();
            return;
        }
        if (signal.type == OrchestrationSignal.Type.CONTINUE_SAME) {
            String previous = store.deliveryState();
            if (bootstrap) {
                store.continueSameBootstrap(signal, sourceSide);
                log("BOOTSTRAP_SEQUENCE_SEEDED", "step=" + safeCode(signal.step)
                        + ";round=" + safeCode(signal.round) + ";type=CONTINUE_SAME");
            } else {
                store.continueSame(signal, sourceSide);
            }
            log("CONTINUE_SAME_ACCEPTED", "source=" + safeCode(sourceSide)
                    + ";response_epoch=" + store.lastSignalResponseEpoch()
                    + ";continuation_epoch=" + store.continuationEpoch());
            log("CONTINUE_SAME_DELIVERY", "source=" + safeCode(sourceSide)
                    + ";reason=signal;continuation_epoch=" + store.continuationEpoch());
            logStateTransition(previous);
            resetResponsePolling("CONTINUE_SAME");
            log("RESPONSE_EPOCH_RESET", "reason=CONTINUE_SAME");
            cleanupWebView();
            handler.post(this::ensureEngine);
            return;
        }
        String previous = store.deliveryState();
        store.transition(signal, sourceSide);
        if (bootstrap) {
            log("BOOTSTRAP_SEQUENCE_SEEDED", "step=" + safeCode(signal.step)
                    + ";round=" + safeCode(signal.round) + ";type=" + safeCode(signal.type.name()));
        }
        logStateTransition(previous);
        log("RESPONSE_EPOCH_RESET", "reason=SIGNAL_TRANSITION");
        resetResponsePolling("SIGNAL_TRANSITION");
        cleanupWebView();
        handler.post(this::ensureEngine);
    }

    /** Elapsed time is telemetry only; a normal response wait never times out. */
    private void retry(String detail, long delayMs) {
        long actualDelay = delayMs;
        String state = store.deliveryState();
        if (OrchestrationStore.DELIVERY_WAITING_RESPONSE.equals(state)) {
            AdaptivePolling.Decision decision = responsePolling.onRetry(store.epoch());
            actualDelay = decision.delayMs;
            if (decision.tierChanged) {
                log("POLLING_TIER_CHANGED", "tier=" + decision.tier + ";delay_ms=" + decision.delayMs
                        + ";retry=" + decision.retryCount);
            }
            log("POLL_RETRY", "retry=" + decision.retryCount + ";tier=" + decision.tier
                    + ";delay_ms=" + decision.delayMs);
        } else {
            responsePolling.reset(store.epoch());
            log("POLL_RETRY", "phase=fast;delay_ms=" + delayMs);
        }
        store.incrementPoll();
        String label = OrchestrationStore.DELIVERY_WAITING_RESPONSE.equals(store.deliveryState())
                ? OrchestrationStore.sideLabel(store.monitoringSide()) : OrchestrationStore.sideLabel(store.deliveryTarget());
        store.setStatus(label + " · " + safeDetail(detail));
        if (store.pollCountLong() % 20L == 0L) startAsForeground(store.status());
        scheduleStep(actualDelay);
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
        log("FAILED", "code=" + safeCode(code.name()));
        NotificationHelper.orchestrationError(this, side, store.runJobId(), store.currentStep(), store.currentRound(), detail);
        stopRelay();
    }

    private void pauseWithError(String code, String detail) {
        String safe = safeDetail(detail);
        store.fail(code, safe);
        log("FAILED", "code=" + safeCode(code));
        NotificationHelper.orchestrationError(this, activeTargetSide(), store.runJobId(),
                store.currentStep(), store.currentRound(), safe);
        stopRelay();
    }

    private void pauseAmbiguous(String detail) {
        String safe = safeDetail(detail);
        store.ambiguous(safe);
        log("AMBIGUOUS", "state=" + safeCode(store.deliveryState()));
        NotificationHelper.orchestrationError(this, store.deliveryTarget(), store.runJobId(),
                store.currentStep(), store.currentRound(), safe);
        stopRelay();
    }

    private void yieldForSchedule() {
        if (!canRun()) return;
        if (!store.schedulePreempted()) log("SCHEDULE_PREEMPT", "source=reservation");
        if (store.reconciling()) {
            restartReconciliation("schedule_preemption");
        }
        if (OrchestrationStore.DELIVERY_PREPARING.equals(store.deliveryState())) store.resetPreparing();
        commitAuthorized = false;
        provisioningRecoveryStartedAt = 0L;
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
        log("SCHEDULE_PRIORITY", "active=" + (active ? "1" : "0"));
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
            log("SCHEDULE_GATE_ERROR", "fallback=priority");
            return true;
        }
    }

    private boolean matchesExpectedTarget(String actualUrl) {
        if (store.bootstrapProvisioning()) {
            return TargetParser.matchesProjectIdentity(store.runProjectUrl(), actualUrl);
        }
        if (!store.reconciling() && store.initialStartPending()) {
            return TargetParser.matchesConversationIdentity(currentRelayTargetUrl(), actualUrl);
        }
        return TargetParser.matchesTarget("existing", currentRelayTargetUrl(), actualUrl);
    }

    private void acceptInitialStartTargetIfNeeded() {
        if (!store.initialStartPending() || webView == null) return;
        String actualUrl = webView.getUrl();
        store.acceptInitialChatTarget(actualUrl);
        log("INITIAL_START_CONFIRMED", "target=chat;conversation="
                + safeDetail(TargetParser.conversationId(actualUrl)));
    }

    /**
     * A startup mismatch never receives composer JavaScript. Re-open the configured Chat URL and
     * wait until its conversation identity is visible instead of failing on transient SPA routes.
     */
    private void reloadInitialStartTarget(String reason) {
        if (!store.initialStartPending() || webView == null || scheduleHasPriority()
                || initialTargetReloadScheduled) return;
        AdaptivePolling.Decision decision = initialTargetPolling.onRetry(store.epoch());
        initialTargetReloadScheduled = true;
        initialTargetReloadReason = safeCode(reason.toUpperCase());
        log("INITIAL_START_TARGET_RETRY", "reason=" + initialTargetReloadReason
                + ";retry=" + decision.retryCount + ";tier=" + decision.tier
                + ";delay_ms=" + decision.delayMs);
        handler.postDelayed(initialTargetReloadRunnable, decision.delayMs);
    }

    private void performInitialTargetReload() {
        initialTargetReloadScheduled = false;
        if (!store.initialStartPending() || webView == null) return;
        if (scheduleHasPriority()) {
            yieldForSchedule();
            return;
        }
        if (matchesExpectedTarget(webView.getUrl())) {
            resetInitialTargetRetry();
            scheduleStep(500L);
            return;
        }
        String expected = store.runChatUrl();
        store.setStatus("일반 Chat 시작 대화 다시 여는 중");
        log("INITIAL_START_TARGET_RELOAD", "reason=" + initialTargetReloadReason);
        webView.stopLoading();
        webView.loadUrl(expected);
    }

    private void resetInitialTargetRetry() {
        handler.removeCallbacks(initialTargetReloadRunnable);
        initialTargetReloadScheduled = false;
        initialTargetReloadReason = "";
        initialTargetPolling.reset(store.epoch());
    }

    private String activeTargetSide() {
        if (store.reconciling()) return store.reconciliationSide();
        if (store.bootstrapProvisioning()) {
            return store.runChatUrl().isEmpty() ? OrchestrationStore.SIDE_CHAT : OrchestrationStore.SIDE_WORK;
        }
        return OrchestrationStore.DELIVERY_WAITING_RESPONSE.equals(store.deliveryState())
                ? store.monitoringSide() : store.deliveryTarget();
    }

    private String currentRelayTargetUrl() {
        if (store.reconciling()) {
            String side = store.reconciliationSide();
            return OrchestrationStore.SIDE_WORK.equals(side) ? store.runWorkUrl() : store.runChatUrl();
        }
        return store.targetUrl();
    }

    /** Uses Android's monotonic boot counter; the elapsed-time fallback is only a local identity. */
    private String currentBootIdentity() {
        String bootCount = Settings.Global.getString(getContentResolver(), "boot_count");
        if (bootCount != null && !bootCount.trim().isEmpty()) return bootCount.trim();
        long wallMinusElapsed = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        return "fallback-" + (wallMinusElapsed / 60_000L);
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
        handler.removeCallbacks(initialTargetReloadRunnable);
        initialTargetReloadScheduled = false;
        generation++;
        evaluationInFlight = false;
        commitAuthorized = false;
        provisioningRecoveryStartedAt = 0L;
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
        log("SERVICE_STOP", "source=relay");
        handler.removeCallbacks(stepRunnable);
        handler.removeCallbacks(resumeRunnable);
        cleanupWebView();
        releaseWakeLock();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        log("SERVICE_DESTROY", "source=system");
        AutomationRuntimeGate.removeListener(this);
        handler.removeCallbacksAndMessages(null);
        cleanupWebView();
        releaseWakeLock();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        log("SERVICE_TASK_REMOVED", "source=recent_apps");
        if (canRun()) handler.post(this::ensureEngine);
        super.onTaskRemoved(rootIntent);
    }

    private void acquireWakeLock() {
        if (wakeLock == null) return;
        long now = System.currentTimeMillis();
        boolean held = wakeLock.isHeld();
        if (held && now < wakeLockLeaseExpiresAt) return;
        if (held) {
            try { wakeLock.release(); } catch (RuntimeException ignored) {}
            log("WAKELOCK_TIMEOUT", "held_ms=" + Math.max(0L, now - wakeLockAcquiredAt));
            wakeLockAcquiredAt = 0L;
            wakeLockLeaseExpiresAt = 0L;
        } else if (wakeLockLeaseExpiresAt > 0L && now >= wakeLockLeaseExpiresAt) {
            log("WAKELOCK_TIMEOUT", "held_ms=" + Math.max(0L, wakeLockLeaseExpiresAt - wakeLockAcquiredAt));
            wakeLockAcquiredAt = 0L;
            wakeLockLeaseExpiresAt = 0L;
        }
        try {
            wakeLock.acquire(WAKE_LOCK_LEASE_MS);
            wakeLockAcquiredAt = now;
            wakeLockLeaseExpiresAt = now + WAKE_LOCK_LEASE_MS;
            log("WAKELOCK_ACQUIRE", "lease_ms=" + WAKE_LOCK_LEASE_MS);
        } catch (RuntimeException error) {
            log("WAKELOCK_ACQUIRE_FAILED", "source=runtime");
        }
    }

    private void releaseWakeLock() {
        if (wakeLock == null) return;
        long now = System.currentTimeMillis();
        if (wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (RuntimeException ignored) {}
            log("WAKELOCK_RELEASE", "held_ms=" + Math.max(0L, now - wakeLockAcquiredAt));
        } else if (wakeLockLeaseExpiresAt > 0L && now >= wakeLockLeaseExpiresAt) {
            log("WAKELOCK_TIMEOUT", "held_ms=" + Math.max(0L, wakeLockLeaseExpiresAt - wakeLockAcquiredAt));
        }
        wakeLockAcquiredAt = 0L;
        wakeLockLeaseExpiresAt = 0L;
    }

    private void resetResponsePolling(String reason) {
        if (responsePolling.retryCount() > 0 || responsePolling.tier() > 0)
            log("POLLING_RESET", "reason=" + safeCode(reason));
        responsePolling.reset(store == null ? Long.MIN_VALUE : store.epoch());
    }

    private void logScriptResult(String event, String status) {
        log(event, "status=" + safeCode(status));
    }

    private void logStateTransition(String previous) {
        log("STATE_TRANSITION", "from=" + safeCode(previous) + ";to=" + safeCode(store.deliveryState()));
    }

    private void log(String event, String detail) {
        if (runLog != null) runLog.record(store, event, detail);
    }
}
