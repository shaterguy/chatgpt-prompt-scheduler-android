package com.shaterguy.chatgptpromptscheduler;

import android.app.Presentation;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.view.Surface;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.FrameLayout;

/**
 * Hosts the automation WebView on a private virtual display. The WebView is attached to a real
 * window and can receive focus, but nothing is shown on the device's physical display.
 */
public final class HeadlessWebViewHost {
    private static final int WIDTH = 1440;
    private static final int HEIGHT = 900;
    private static final int DENSITY_DPI = 160;

    private final WebView webView;
    private final Presentation presentation;
    private final VirtualDisplay virtualDisplay;
    private final Surface surface;
    private final SurfaceTexture surfaceTexture;
    private final boolean windowAttached;

    private HeadlessWebViewHost(WebView webView, Presentation presentation, VirtualDisplay virtualDisplay,
                                Surface surface, SurfaceTexture surfaceTexture, boolean windowAttached) {
        this.webView = webView;
        this.presentation = presentation;
        this.virtualDisplay = virtualDisplay;
        this.surface = surface;
        this.surfaceTexture = surfaceTexture;
        this.windowAttached = windowAttached;
    }

    public static HeadlessWebViewHost create(Context context) {
        SurfaceTexture texture = null;
        Surface surface = null;
        VirtualDisplay display = null;
        Presentation presentation = null;
        try {
            texture = new SurfaceTexture(false);
            texture.setDefaultBufferSize(WIDTH, HEIGHT);
            surface = new Surface(texture);
            DisplayManager manager = context.getSystemService(DisplayManager.class);
            int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                    | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
            display = manager.createVirtualDisplay("ChatGPTPromptScheduler", WIDTH, HEIGHT, DENSITY_DPI, surface, flags);
            if (display == null || display.getDisplay() == null) throw new IllegalStateException("가상 디스플레이를 만들지 못했습니다.");

            presentation = new Presentation(context, display.getDisplay(), android.R.style.Theme_DeviceDefault_NoActionBar);
            FrameLayout root = new FrameLayout(presentation.getContext());
            WebView webView = new WebView(presentation.getContext());
            webView.setFocusable(true);
            webView.setFocusableInTouchMode(true);
            root.addView(webView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            presentation.setContentView(root);
            presentation.show();
            Window window = presentation.getWindow();
            if (window != null) window.setLayout(WIDTH, HEIGHT);
            webView.requestFocus();
            return new HeadlessWebViewHost(webView, presentation, display, surface, texture, webView.isAttachedToWindow());
        } catch (Throwable error) {
            if (presentation != null) {
                try { presentation.dismiss(); } catch (Throwable ignored) {}
            }
            if (display != null) {
                try { display.release(); } catch (Throwable ignored) {}
            }
            if (surface != null) {
                try { surface.release(); } catch (Throwable ignored) {}
            }
            if (texture != null) {
                try { texture.release(); } catch (Throwable ignored) {}
            }
            WebView fallback = new WebView(context);
            fallback.setFocusable(true);
            fallback.setFocusableInTouchMode(true);
            fallback.requestFocus();
            return new HeadlessWebViewHost(fallback, null, null, null, null, false);
        }
    }

    public WebView webView() {
        return webView;
    }

    public boolean isWindowAttached() {
        return windowAttached || webView.isAttachedToWindow();
    }

    public void destroy() {
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
        if (presentation != null) {
            try { presentation.dismiss(); } catch (Throwable ignored) {}
        }
        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (Throwable ignored) {}
        }
        if (surface != null) {
            try { surface.release(); } catch (Throwable ignored) {}
        }
        if (surfaceTexture != null) {
            try { surfaceTexture.release(); } catch (Throwable ignored) {}
        }
    }
}
