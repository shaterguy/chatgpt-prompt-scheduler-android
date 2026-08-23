package com.shaterguy.chatgptpromptscheduler;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

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
            "for(const a of document.querySelectorAll('a[href]')){try{" +
            "const u=new URL(a.getAttribute('href'),location.origin);" +
            "if(u.origin===location.origin&&u.pathname===canonical){const t=clean(a.innerText||a.textContent);if(t){name=t;break;}}" +
            "}catch(e){}}" +
            "const atRoot=location.pathname===canonical||location.pathname===canonical+'/';" +
            "if(!name&&atRoot){const h=document.querySelector('main h1,[role=\"main\"] h1,h1');name=clean(h&&(h.innerText||h.textContent));}" +
            "if(!name&&atRoot){let t=clean(document.title);for(const s of [' | ChatGPT',' - ChatGPT',' · ChatGPT',' — ChatGPT',' – ChatGPT'])if(t.endsWith(s)){t=clean(t.slice(0,-s.length));break;}if(t&&t.toLowerCase()!=='chatgpt')name=t;}" +
            "return JSON.stringify({href:location.href,name:name});" +
            "})()";

    private WebView webView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable observeRunnable = this::observeVisitedProject;
    private ProjectCatalog catalog;
    private TextView status;
    private boolean resumed;
    private int observerGeneration;

    @Override
    @SuppressWarnings("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(Ui.actionGrid(this,
                Ui.button(this, "뒤로", v -> { if (webView.canGoBack()) webView.goBack(); else finish(); }),
                Ui.button(this, "새로고침", v -> webView.reload()),
                Ui.button(this, "ChatGPT 홈", v -> webView.loadUrl("https://chatgpt.com/")),
                Ui.button(this, "닫기", v -> finish())));
        root.addView(Ui.body(this, "로그인 상태를 확인하거나 예약에 사용할 프로젝트를 직접 여세요. 이 화면에서 방문한 ChatGPT 프로젝트 주소를 자동 등록합니다."));
        status = Ui.body(this, "프로젝트 방문 대기");
        root.addView(status);

        webView = new WebView(this);
        catalog = new ProjectCatalog(this);
        catalog.seedFromSchedules(new ConfigStore(this).loadSchedules());
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
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
        root.addView(webView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        Ui.setContent(this, root);
        webView.loadUrl("https://chatgpt.com/");
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
        if (!resumed || webView == null || isFinishing()
                || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
        String current = webView.getUrl();
        if (!ProjectUrlPolicy.isTrustedChatgptPage(current)) return;
        final int generation = observerGeneration;
        final WebView observed = webView;
        observed.evaluateJavascript(OBSERVE_PROJECT_SCRIPT, raw -> {
            if (!resumed || generation != observerGeneration || observed != webView || isFinishing()
                    || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
            ProjectVisit visit = jsonProjectVisit(raw);
            ProjectUrlPolicy.ProjectRef ref = ProjectUrlPolicy.parseProject(visit.href);
            if (ref != null) {
                try {
                    boolean changed = catalog.addVisitedProject(ref.canonicalUrl, visit.name);
                    String suffix = visit.name.isEmpty() ? " 등록됨" : " 등록/업데이트됨";
                    status.setText(catalog.displayName(ref) + (changed ? suffix : " · 등록됨"));
                } catch (RuntimeException error) {
                    status.setText("프로젝트 등록 실패");
                }
            }
            if (resumed) scheduleObservation(500L);
        });
    }

    private static ProjectVisit jsonProjectVisit(String raw) {
        try {
            Object outer = new org.json.JSONTokener(raw == null ? "" : raw).nextValue();
            org.json.JSONObject result = new org.json.JSONObject(outer instanceof String ? (String) outer : String.valueOf(outer));
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
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        resumed = false;
        observerGeneration++;
        handler.removeCallbacks(observeRunnable);
        if (webView != null) {
            CookieManager.getInstance().flush();
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
