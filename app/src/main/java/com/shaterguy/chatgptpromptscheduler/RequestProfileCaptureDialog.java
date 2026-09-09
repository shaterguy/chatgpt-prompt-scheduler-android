package com.shaterguy.chatgptpromptscheduler;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.WebViewCompat;

import org.json.JSONObject;

import java.util.UUID;

/** Foreground-only native capture UI; the scheduler execution WebView is never reused. */
final class RequestProfileCaptureDialog extends Dialog {
    private final Activity owner;
    private final RequestProfileEngine.Mode mode;
    private final RequestProfileRegistry registry;
    private final Runnable registered;
    private WebView webView;
    private TextView status;
    private AlertDialog registrationDialog;
    private AlertDialog replacementDialog;
    private String captureId;
    private boolean pageReady;
    private boolean received;
    private boolean closed;
    private boolean bridgeInstalled;

    RequestProfileCaptureDialog(Activity owner, RequestProfileEngine.Mode mode, Runnable registered) {
        super(owner);
        this.owner = owner;
        this.mode = mode;
        this.registered = registered;
        this.registry = new RequestProfileRegistry(owner);
        setOwnerActivity(owner);
        setCanceledOnTouchOutside(false);
    }

    @Override @SuppressWarnings("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(owner);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        int padding = Ui.dp(owner, 12);
        root.setPadding(padding, padding, padding, padding);
        root.addView(Ui.title(owner, mode == RequestProfileEngine.Mode.CHAT
                ? "일반 Chat 모델 · 추론 캡처" : "Work 모델 · 추론 캡처"));
        root.addView(Ui.body(owner, "ChatGPT에서 원하는 모드·모델·추론 정도를 직접 선택하고 짧은 프롬프트를 한 번 전송하세요. 메뉴 선택만으로는 캡처되지 않습니다."));
        root.addView(Ui.actionGrid(owner,
                Ui.button(owner, "다시 캡처", v -> retry()),
                Ui.button(owner, "닫기 / 취소", v -> dismiss())));
        status = Ui.body(owner, "ChatGPT 화면을 준비하고 있습니다.");
        root.addView(status);
        setContentView(root);
        if (!RequestProfileCaptureScript.supported()) {
            status.setText("현재 Android System WebView에서 요청 캡처를 지원하지 않습니다. WebView 업데이트 후 다시 시도하세요. 기존 JSON 가져오기는 계속 사용할 수 있습니다.");
            return;
        }
        captureId = UUID.randomUUID().toString();
        webView = new WebView(owner);
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
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        webView.setWebChromeClient(new WebChromeClient());
        RequestProfileCaptureScript.install(webView, (view, message, origin, mainFrame, reply) -> {
            if (closed || received || !isShowing() || owner.isFinishing() || view != webView
                    || !mainFrame || !CapturedRequestProfile.trustedPage(origin.toString())) return;
            String raw;
            try { raw = message.getData(); } catch (RuntimeException ignored) { return; }
            receive(raw);
        });
        bridgeInstalled = true;
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                pageReady = false;
                if (!received && !closed) status.setText("화면 이동 중입니다. 캡처 준비 표시 후 전송하세요.");
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = CapturedRequestProfile.trustedPage(url)
                        && CapturedRequestProfile.trustedPage(view.getUrl());
                if (!closed && !received && pageReady) arm();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.isForMainFrame()
                        && !CapturedRequestProfile.trustedPage(request.getUrl().toString())) {
                    status.setText("캡처는 ChatGPT 화면에서만 가능합니다. 로그인이 필요하면 닫고 홈의 로그인/세션에서 로그인한 뒤 다시 여세요.");
                    return true;
                }
                return false;
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        webView.loadUrl("https://chatgpt.com/");
    }

    @Override protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void arm() {
        if (closed || received || !pageReady || webView == null || captureId == null) return;
        final String expected = captureId;
        webView.evaluateJavascript(RequestProfileCaptureScript.arm(expected, mode), raw -> {
            if (closed || received || !expected.equals(captureId)) return;
            status.setText("true".equals(raw)
                    ? "캡처 준비 완료 · 원하는 조합으로 실제 프롬프트를 한 번 전송하세요."
                    : "캡처 엔진을 확인하지 못했습니다. 화면 준비 후 다시 캡처를 누르세요.");
        });
    }

    private void retry() {
        if (closed || webView == null) return;
        dismissChildren();
        received = false;
        captureId = UUID.randomUUID().toString();
        if (pageReady) arm();
        else status.setText("화면 준비 후 자동으로 캡처를 시작합니다.");
    }

    private void receive(String raw) {
        if (raw == null || raw.length() > CapturedRequestProfile.MAX_MESSAGE_CHARS || captureId == null) return;
        try {
            JSONObject envelope = new JSONObject(raw);
            if (!captureId.equals(envelope.opt("captureId"))
                    || !mode.name().toLowerCase(java.util.Locale.ROOT).equals(envelope.opt("mode"))) return;
            if (envelope.has("error")) {
                received = true;
                status.setText("이 요청에서는 지원되는 모델·추론 조합을 캡처하지 못했습니다. 다시 캡처를 누른 뒤 짧은 텍스트를 전송하세요.");
                return;
            }
            CapturedRequestProfile captured = CapturedRequestProfile.parse(raw, mode, captureId);
            received = true;
            webView.evaluateJavascript(RequestProfileCaptureScript.cancel(), ignored -> {});
            status.setText("모델·추론 조합을 캡처했습니다. 등록할 신호명을 확인하세요.");
            RequestProfileEngine.TargetProfile duplicate = captured.findDuplicate(registry);
            if (duplicate != null) {
                registrationDialog = new AlertDialog.Builder(owner)
                        .setTitle("이미 등록된 조합입니다")
                        .setMessage("등록 이름: " + CapturedRequestProfile.signalLabel(duplicate)
                                + "\n\n" + captured.actualCombination())
                        .setPositiveButton("확인", (dialog, which) -> dismiss())
                        .setNegativeButton("다른 조합 캡처", (dialog, which) -> retry()).create();
                registrationDialog.show();
            } else showRegistration(captured);
        } catch (Exception ignored) {
            received = true;
            status.setText("캡처 결과를 안전하게 해석하지 못했습니다. 저장하지 않았습니다. 다시 캡처를 누르세요.");
        }
    }

    private void showRegistration(CapturedRequestProfile captured) {
        LinearLayout fields = new LinearLayout(owner);
        fields.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(owner, 16);
        fields.setPadding(pad, pad, pad, pad);
        fields.addView(Ui.body(owner, "실제 캡처 값\n" + captured.actualCombination()));
        fields.addView(Ui.body(owner, "예약에서 선택할 영문 신호명을 입력하세요. 셀프런과 같은 이름을 사용할 수 있습니다."));
        EditText model = mode == RequestProfileEngine.Mode.WORK ? field(fields, "모델 신호명 (예: astra)") : null;
        EditText reasoning = field(fields, mode == RequestProfileEngine.Mode.CHAT
                ? "조합 신호명 (예: astra-high)" : "추론 신호명 (예: high)");
        ScrollView scroll = new ScrollView(owner);
        scroll.addView(fields);
        registrationDialog = new AlertDialog.Builder(owner)
                .setTitle("캡처 조합 등록").setView(scroll)
                .setNegativeButton("취소", (dialog, which) -> dismiss())
                .setPositiveButton("등록", null).create();
        registrationDialog.setOnCancelListener(dialog -> dismiss());
        registrationDialog.setOnShowListener(ignored -> registrationDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (closed || !isShowing()) return;
                    try {
                        String modelSignal = model == null ? "" : CapturedRequestProfile.normalizeSignal(model.getText().toString());
                        String reasoningSignal = CapturedRequestProfile.normalizeSignal(reasoning.getText().toString());
                        String portable = captured.portableJson(modelSignal, reasoningSignal, BuildConfig.VERSION_NAME);
                        RequestProfileEngine.TargetProfile prior = registry.find(mode, modelSignal, reasoningSignal);
                        if (prior != null && !captured.matches(prior)) {
                            replacementDialog = new AlertDialog.Builder(owner)
                                    .setTitle("같은 신호명 갱신")
                                    .setMessage("이 신호명을 사용하는 예약의 모델·추론 설정도 새 캡처 값으로 변경됩니다. 갱신할까요?")
                                    .setNegativeButton("취소", null)
                                    .setPositiveButton("갱신", (dialog, which) -> save(captured, portable, modelSignal, reasoningSignal)).create();
                            replacementDialog.show();
                        } else save(captured, portable, modelSignal, reasoningSignal);
                    } catch (Exception error) { toast("등록하지 못했습니다: " + error.getMessage()); }
                }));
        registrationDialog.show();
    }

    private EditText field(LinearLayout fields, String hint) {
        EditText input = new EditText(owner);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        fields.addView(input);
        return input;
    }

    private void save(CapturedRequestProfile captured, String portable, String model, String reasoning) {
        if (closed || !isShowing() || !received || owner.isFinishing()) return;
        try {
            RequestProfileRegistry.ImportResult result = mode == RequestProfileEngine.Mode.CHAT
                    ? registry.importChat(portable) : registry.importWork(portable);
            if (!captured.matches(registry.find(mode, model, reasoning)))
                throw new IllegalStateException("저장 결과를 확인하지 못했습니다.");
            if (registered != null) registered.run();
            toast(result.updated > 0 ? "캡처 조합을 갱신했습니다." : "캡처 조합을 등록했습니다. 예약 편집에서 선택할 수 있습니다.");
            dismiss();
        } catch (Exception error) { toast("등록하지 못했습니다: " + error.getMessage()); }
    }

    private void toast(String message) { Toast.makeText(owner, message, Toast.LENGTH_LONG).show(); }

    private void dismissChildren() {
        AlertDialog replacement = replacementDialog, registration = registrationDialog;
        replacementDialog = null;
        registrationDialog = null;
        if (replacement != null) replacement.dismiss();
        if (registration != null) registration.dismiss();
    }

    @Override public void dismiss() {
        if (!closed) {
            closed = true;
            captureId = null;
            pageReady = false;
            dismissChildren();
            WebView view = webView;
            webView = null;
            if (view != null) {
                view.evaluateJavascript(RequestProfileCaptureScript.cancel(), ignored -> {});
                if (bridgeInstalled) WebViewCompat.removeWebMessageListener(view, RequestProfileCaptureScript.BRIDGE);
                view.stopLoading();
                if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
                view.destroy();
                CookieManager.getInstance().flush();
            }
        }
        super.dismiss();
    }
}
