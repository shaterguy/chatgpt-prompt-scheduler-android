package com.shaterguy.chatgptpromptscheduler;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebStorage;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.webkit.WebStorageCompat;
import androidx.webkit.WebViewFeature;

public final class LoginActivity extends Activity {
    private static final String OBSERVE_PROJECT_SCRIPT =
            "(()=>{" +
            "if(location.protocol!=='https:'||location.hostname!=='chatgpt.com'||location.port!=='')return '';" +
            "const p=location.pathname.split('/');" +
            "const id=p.length>2&&p[1]==='g'?p[2]:'';" +
            "if(!/^g-p-[A-Za-z0-9_-]+$/.test(id))return JSON.stringify({href:location.href,name:''});" +
            "const canonical='/g/'+id+'/project';" +
            "const clean=v=>String(v||'').replace(/\\s+/g,' ').trim().slice(0,120);" +
            "let name='';" +
            "const atRoot=location.pathname===canonical||location.pathname===canonical+'/';" +
            "if(atRoot){let t=clean(document.title);if(t.startsWith('ChatGPT - ')||t.startsWith('ChatGPT | '))name=clean(t.slice(10));else for(const suf of [' | ChatGPT',' - ChatGPT',' · ChatGPT',' — ChatGPT',' – ChatGPT']){if(t.endsWith(suf)){name=clean(t.slice(0,-suf.length));break;}}}" +
            "if(!name){for(const a of document.querySelectorAll('a[href]')){try{const u=new URL(a.getAttribute('href'),location.origin);if(u.origin!==location.origin||u.pathname!==canonical)continue;const label=clean(a.innerText||a.textContent||a.getAttribute('aria-label'));if(label){name=label;break;}}catch(e){}}}" +
            "return JSON.stringify({href:location.href,name:name});" +
            "})()";

    private static final String CHATGPT_HOME = "https://chatgpt.com/";
    private final Runnable openSession = this::openSession;
    private LinearLayout root;
    private Button resetButton;
    private WebView webView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable observeRunnable = this::observeVisitedProject;
    private ProjectCatalog catalog;
    private TextView status;
    private boolean resumed;
    private int observerGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(Ui.actionGrid(this,
                Ui.button(this, "뒤로", v -> navigateBack()),
                Ui.button(this, "새로고침", v -> { if (webView != null) webView.reload(); }),
                Ui.button(this, "ChatGPT 홈", v -> { if (webView != null) webView.loadUrl(CHATGPT_HOME); }),
                Ui.button(this, "닫기", v -> finish())));
        root.addView(Ui.body(this,
                "로그인 상태를 확인하고 예약에 사용할 프로젝트를 직접 한 번씩 여세요. " +
                        "앱은 현재 방문한 프로젝트 주소와 화면에서 확인된 프로젝트 이름만 등록하며 목록을 자동으로 누르지 않습니다."));
        resetButton = Ui.button(this, "ChatGPT 세션 초기화", v -> confirmReset());
        root.addView(resetButton);
        status = Ui.body(this, SessionResetGate.INSTANCE.isResetting()
                ? "ChatGPT 세션 초기화 중" : "프로젝트 방문 대기");
        root.addView(status);
        catalog = new ProjectCatalog(this);
        Ui.setContent(this, root);
        SessionResetGate.INSTANCE.whenReady(openSession);
    }

    private void openSession() {
        if (isFinishing() || isDestroyed() || webView != null) return;
        createWebView();
        webView.loadUrl(CHATGPT_HOME);
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void createWebView() {
        webView = new WebView(this);
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
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView view, String title) { scheduleObservation(80L); }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) { scheduleObservation(100L); }
            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) { scheduleObservation(100L); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                scheduleObservation(350L);
                return false;
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void confirmReset() {
        if (SessionResetGate.INSTANCE.isResetting()) return;
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            status.setText("세션을 초기화하려면 Android System WebView를 업데이트해 주세요.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("ChatGPT 세션 초기화")
                .setMessage("앱 안의 ChatGPT 로그인과 웹 저장 데이터를 지웁니다. 다시 로그인해야 합니다. "
                        + "예약, 등록한 프로젝트, 앱 설정, 실행 기록은 보존됩니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("초기화", (dialog, which) -> resetSession())
                .show();
    }

    private void resetSession() {
        SessionResetGate gate = SessionResetGate.INSTANCE;
        if (!gate.beginReset()) {
            status.setText("예약 실행이 끝난 뒤 세션을 초기화해 주세요.");
            return;
        }
        resetButton.setEnabled(false);
        status.setText("ChatGPT 세션 초기화 중");
        destroyWebView();
        WebStorage storage = WebStorage.getInstance();
        try {
            WebStorageCompat.deleteBrowsingDataForSite(storage, "chatgpt.com", () -> {
                try {
                    WebStorageCompat.deleteBrowsingDataForSite(storage, "openai.com", () -> finishReset(true));
                } catch (RuntimeException error) {
                    finishReset(false);
                }
            });
        } catch (RuntimeException error) {
            finishReset(false);
        }
    }

    private void finishReset(boolean success) {
        SessionResetGate.INSTANCE.finishReset();
        if (isFinishing() || isDestroyed()) return;
        resetButton.setEnabled(true);
        status.setText(success ? "초기화했습니다. ChatGPT에 다시 로그인해 주세요."
                : "세션 초기화를 마치지 못했습니다. 다시 시도해 주세요.");
        openSession();
    }

    private void destroyWebView() {
        observerGeneration++;
        handler.removeCallbacks(observeRunnable);
        if (webView == null) return;
        webView.setWebViewClient(null);
        webView.setWebChromeClient(null);
        webView.stopLoading();
        root.removeView(webView);
        webView.destroy();
        webView = null;
    }

    @Override
    public void onBackPressed() {
        navigateBack();
    }

    private void navigateBack() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        observerGeneration++;
        scheduleObservation(150L);
    }

    @Override
    protected void onPause() {
        resumed = false;
        observerGeneration++;
        handler.removeCallbacks(observeRunnable);
        super.onPause();
    }

    private void scheduleObservation(long delayMs) {
        if (!resumed || webView == null) return;
        handler.removeCallbacks(observeRunnable);
        handler.postDelayed(observeRunnable, delayMs);
    }

    private void observeVisitedProject() {
        if (!resumed || isFinishing()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())
                || webView == null) return;
        String current = webView.getUrl();
        if (!ProjectUrlPolicy.isTrustedChatgptPage(current)) return;
        final int epoch = observerGeneration;
        final WebView observed = webView;
        observed.evaluateJavascript(OBSERVE_PROJECT_SCRIPT, raw -> {
            if (!resumed || epoch != observerGeneration || observed != webView || isFinishing()
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) return;
            ProjectVisit visit = jsonProjectVisit(raw);
            ProjectUrlPolicy.ProjectRef ref = ProjectUrlPolicy.parseProject(visit.href);
            if (ref != null && catalog.addVisitedProject(ref.canonicalUrl, visit.name)) {
                status.setText(catalog.displayName(ref) + " 등록/업데이트됨");
            }
            if (resumed) scheduleObservation(500L);
        });
    }

    private static ProjectVisit jsonProjectVisit(String raw) {
        try {
            Object outer = new org.json.JSONTokener(raw == null ? "" : raw).nextValue();
            org.json.JSONObject result = new org.json.JSONObject(
                    outer instanceof String ? (String) outer : String.valueOf(outer));
            return new ProjectVisit(result.optString("href", ""), result.optString("name", ""));
        } catch (Throwable ignored) {
            return new ProjectVisit("", "");
        }
    }

    private static final class ProjectVisit {
        final String href;
        final String name;
        ProjectVisit(String href, String name) {
            this.href = href == null ? "" : href;
            this.name = ProjectCatalog.normalizeDisplayName(name);
        }
    }

    @Override
    protected void onDestroy() {
        resumed = false;
        observerGeneration++;
        handler.removeCallbacks(observeRunnable);
        SessionResetGate.INSTANCE.cancelWait(openSession);
        if (webView != null) CookieManager.getInstance().flush();
        destroyWebView();
        super.onDestroy();
    }
}
