package com.polygon.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

public class MainActivity extends Activity {

    private WebView webView;
    private ProgressBar progressBar;

    // ── Your deployed frontend URL ───────────────────────────────────────────
    private static final String APP_URL = "https://the-p0-lyg-0-n.vercel.app";
    // If Vercel URL is different, update the line above.
    // ────────────────────────────────────────────────────────────────────────

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full screen — no title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Layout: WebView + ProgressBar on top
        RelativeLayout layout = new RelativeLayout(this);
        layout.setBackgroundColor(0xFF0A1628); // POLYGON dark bg while loading

        webView = new WebView(this);
        webView.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        layout.addView(webView);

        // Loading progress bar
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        RelativeLayout.LayoutParams pbParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, 8
        );
        pbParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        progressBar.setLayoutParams(pbParams);
        progressBar.setProgressTintList(
                android.content.res.ColorStateList.valueOf(0xFF00FF88) // POLYGON green
        );
        progressBar.setMax(100);
        layout.addView(progressBar);

        setContentView(layout);

        setupWebView();
        webView.loadUrl(APP_URL);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();

        // Core
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);

        // Rendering
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        // Caching — cache-first for offline resilience
        s.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        s.setAllowFileAccess(true);

        // Media
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Geolocation (for LGA / heatmap features)
        s.setGeolocationEnabled(true);

        // Cookies
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // User agent — identify as POLYGON Android app
        String defaultUA = s.getUserAgentString();
        s.setUserAgentString(defaultUA + " POLYGONApp/1.0 Android");

        webView.setWebViewClient(new PolygonWebViewClient());
        webView.setWebChromeClient(new PolygonWebChromeClient());
    }

    // ── WebViewClient: handle navigation ────────────────────────────────────
    private class PolygonWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            // Keep all POLYGON routes inside the app
            if (url.startsWith(APP_URL) ||
                    url.startsWith("https://the-p0lyg0n.onrender.com")) {
                return false;
            }

            // Open external links (Squad checkout, etc.) in the browser
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            return true;
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.proceed(); // Allow self-signed certs in dev
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);

            // Inject mobile tweaks: remove any desktop-only elements, set viewport
            webView.evaluateJavascript(
                    "document.querySelector('meta[name=viewport]') && " +
                            "(document.querySelector('meta[name=viewport]').content = " +
                            "'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no');",
                    null
            );
        }
    }

    // ── WebChromeClient: JS, progress, geolocation ──────────────────────────
    private class PolygonWebChromeClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int progress) {
            progressBar.setVisibility(progress < 100 ? View.VISIBLE : View.GONE);
            progressBar.setProgress(progress);
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(
                String origin, GeolocationPermissions.Callback callback) {
            callback.invoke(origin, true, false);
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage msg) {
            // Suppress console noise in production
            return true;
        }

        // Allow window.open() links used by Squad payment pages
        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog,
                                      boolean isUserGesture, Message resultMsg) {
            WebView newWebView = new WebView(MainActivity.this);
            newWebView.getSettings().setJavaScriptEnabled(true);
            newWebView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                    startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl()));
                    return true;
                }
            });
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(newWebView);
            resultMsg.sendToTarget();
            return true;
        }
    }

    // ── Back button: navigate within app ────────────────────────────────────
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        webView.stopLoading();
        webView.destroy();
    }
}