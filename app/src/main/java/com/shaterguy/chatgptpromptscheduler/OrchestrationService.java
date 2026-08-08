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
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Optional Protocol 3.0 relay. The reservation execution service always has priority. */
public final class OrchestrationService extends Service implements AutomationRuntimeGate.Listener {
    public static final String ACTION_RUN = "com.shaterguy.chatgptpromptscheduler.ORCHESTRATION_RUN";
    private static final int NOTIFICATION_ID = 7020;
    private static final int MAX_POLLS = 300;
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

    @Override
    public void onCreate() {
        super.onCreate();
        store = new OrchestrationStore(this);
        NotificationHelper.ensureChannels(this);
        AutomationRuntimeGate.addListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground(store.status());
        if (!store.active() || store.paused()) {
            stopRelay();
            return START_NOT_STICKY;
        }
        handler.post(this::ensureEngine);
        return START_STICKY;
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
        if (!store.active() || store.paused()) {
            stopRelay();
            return;
        }
        String configError = store.runtimeConfigError();
        if (!configError.isEmpty()) {
            pauseWithError(configError);
            return;
        }
        if (scheduleHasPriority()) {
            yieldForSchedule();
            return;
        }
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
        store.setStatus(store.sideLabel() + " 대화 여는 중");
        startAsForeground(store.status());
        try {
            host = HeadlessWebViewHost.create(this);
            webView = host.webView();
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(true);
            String userAgent = settings.getUserAgentString();
            settings.setUserAgentString(userAgent + " ChatGPTPromptScheduler/0.1.14 Orchestration/3.0");
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    generation++;
                    evaluationInFlight = false;
                    handler.removeCallbacks(stepRunnable);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    scheduleStep(1800L);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return request.isForMainFrame() && !OrchestrationStore.isAllowedRelayUrl(String.valueOf(request.getUrl()));
                }

                @Override
                public void onReceivedSslError(WebView view, SslErrorHandler sslHandler, SslError error) {
                    sslHandler.cancel();
                    pauseWithError("SSL 인증서 오류로 중계를 멈췄습니다.");
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
                    store.setStatus("WebView 렌더러 복구 중");
                    handler.postDelayed(resumeRunnable, 2000L);
                    return true;
                }
            });
            webView.loadUrl(loadedTarget);
        } catch (Throwable error) {
            cleanupWebView();
            store.setStatus("WebView 복구 대기");
            handler.postDelayed(resumeRunnable, 2500L);
        }
    }

    private void scheduleStep(long delayMs) {
        handler.removeCallbacks(stepRunnable);
        if (webView != null && store.active() && !store.paused()) handler.postDelayed(stepRunnable, delayMs);
    }

    private void runStep() {
        if (scheduleHasPriority()) {
            yieldForSchedule();
            return;
        }
        if (webView == null || evaluationInFlight || !store.active() || store.paused()) return;
        String actualUrl = webView.getUrl();
        if (!TargetParser.matchesTarget("existing", store.targetUrl(), actualUrl)) {
            pauseWithError("중계 대상 대화가 바뀌어 자동 전송을 멈췄습니다.");
            return;
        }
        WebView active = webView;
        int activeGeneration = generation;
        long activeEpoch = store.epoch();
        evaluationInFlight = true;
        String activePhase = store.phase();
        boolean authorizedCommit = OrchestrationStore.PHASE_SUBMITTING.equals(activePhase) && commitAuthorized;
        if (authorizedCommit) commitAuthorized = false;
        String script;
        if (OrchestrationStore.PHASE_SUBMIT.equals(activePhase)) script = OrchestrationScript.prepare(store.pendingPrompt());
        else if (OrchestrationStore.PHASE_SUBMITTING.equals(activePhase)) {
            script = authorizedCommit ? OrchestrationScript.commit(store.pendingPrompt())
                    : OrchestrationScript.recoverSubmission(store.pendingPrompt());
        } else script = OrchestrationScript.observe(store.pendingPrompt());
        active.evaluateJavascript(script, raw -> {
            evaluationInFlight = false;
            if (active != webView || activeGeneration != generation || activeEpoch != store.epoch()
                    || scheduleHasPriority()) return;
            JSONObject result = parseObject(raw);
            if (OrchestrationStore.PHASE_SUBMIT.equals(activePhase)) handlePrepare(result);
            else if (OrchestrationStore.PHASE_SUBMITTING.equals(activePhase)) handleCommit(result);
            else handleObservation(result);
        });
    }

    private void handlePrepare(JSONObject result) {
        String status = result.optString("status", "SCRIPT_RESULT_INVALID");
        switch (status) {
            case "READY" -> {
                store.markSubmitting();
                commitAuthorized = true;
                scheduleStep(0L);
            }
            case "ALREADY_SUBMITTED" -> {
                store.markWaiting();
                startAsForeground(store.status());
                scheduleStep(2500L);
            }
            case "RETRY" -> retry(result.optString("detail", "전송 준비 대기"), 1000L);
            case "AUTH_REQUIRED", "DRAFT_PRESENT", "TARGET_CONTEXT_MISMATCH" ->
                    pauseWithError(result.optString("detail", status));
            default -> pauseWithError("중계 전송 스크립트 오류: " + status);
        }
    }

    private void handleCommit(JSONObject result) {
        String status = result.optString("status", "SCRIPT_RESULT_INVALID");
        switch (status) {
            case "SUBMITTED", "ALREADY_SUBMITTED" -> {
                store.markWaiting();
                startAsForeground(store.status());
                scheduleStep(2500L);
            }
            case "AMBIGUOUS" -> pauseWithError(result.optString("detail",
                    "전송 결과가 불명확하여 자동 재전송하지 않습니다."));
            case "TARGET_CONTEXT_MISMATCH" -> pauseWithError(result.optString("detail", status));
            default -> pauseWithError("중계 전송 커밋 스크립트 오류: " + status);
        }
    }

    private void handleObservation(JSONObject result) {
        String status = result.optString("status", "SCRIPT_RESULT_INVALID");
        if ("RETRY".equals(status)) {
            retry(result.optString("detail", "응답 대기"), 3000L);
            return;
        }
        if (!"CANDIDATE".equals(status)) {
            pauseWithError("중계 응답 스크립트 오류: " + status);
            return;
        }
        String fingerprint = result.optString("fingerprint", "");
        if (store.observeCandidate(fingerprint) < 2) {
            scheduleStep(1800L);
            return;
        }
        acceptSignal(result.optString("text", ""));
    }

    private void acceptSignal(String response) {
        OrchestrationSignal signal = OrchestrationSignal.parse(response, store.runJobId());
        if (signal == null) {
            pauseWithError("응답이 Protocol 3.0 제어 신호 한 줄과 정확히 일치하지 않습니다.");
            return;
        }
        if (signal.raw.equals(store.lastSignal())) {
            pauseWithError("중복 제어 신호를 거부했습니다: " + signal.raw);
            return;
        }
        if (signal.isOlderThan(store.lastStep(), store.lastRound())) {
            pauseWithError("이전 Step/Round 제어 신호를 거부했습니다: " + signal.raw);
            return;
        }
        if (signal.type == OrchestrationSignal.Type.DONE || signal.type == OrchestrationSignal.Type.PAUSE
                || signal.type == OrchestrationSignal.Type.ABORTED) {
            if (!OrchestrationStore.SIDE_CHAT.equals(store.side())) {
                pauseWithError("Work 대화에서 온 terminal 제어 신호를 거부했습니다: " + signal.raw);
                return;
            }
            store.finish(signal);
            NotificationHelper.orchestrationResult(this, signal.type == OrchestrationSignal.Type.DONE,
                    "오토런 " + store.status(), signal.raw);
            stopRelay();
            return;
        }
        if (!signal.isValidNextRoute(store.side(), store.lastStep(), store.lastRound())) {
            pauseWithError("현재 대화 방향 또는 Step/Round 순서와 맞지 않는 제어 신호입니다: " + signal.raw);
            return;
        }
        store.transition(signal);
        cleanupWebView();
        handler.post(this::ensureEngine);
    }

    private void retry(String detail, long delayMs) {
        store.incrementPoll();
        if (store.pollCount() >= MAX_POLLS) {
            pauseWithError("중계 응답 제한 시간을 초과했습니다. 마지막 상태: " + detail);
            return;
        }
        store.setStatus(store.sideLabel() + " · " + detail);
        if (store.pollCount() % 5 == 0) startAsForeground(store.status());
        scheduleStep(delayMs);
    }

    private void pauseWithError(String detail) {
        store.pause(detail);
        NotificationHelper.orchestrationResult(this, false, "오토런 일시정지", detail);
        stopRelay();
    }

    private void yieldForSchedule() {
        commitAuthorized = false;
        cleanupWebView();
        store.setStatus("예약 실행 우선 · 오토런 중계 대기");
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

    private void cleanupWebView() {
        handler.removeCallbacks(stepRunnable);
        generation++;
        evaluationInFlight = false;
        commitAuthorized = false;
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
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        AutomationRuntimeGate.removeListener(this);
        handler.removeCallbacksAndMessages(null);
        cleanupWebView();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
