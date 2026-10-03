package tg.graceshop.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Grace Shop : fenêtre plein écran sur la boutique en ligne. */
public class MainActivity extends Activity {

    private static final String HOME = "https://grace-shop.github.io/";
    private static final String HOST = "grace-shop.github.io";
    private static final int PICK_FILES = 41;
    private static final int ASK_GPS = 42;
    private GeolocationPermissions.Callback gpsCb;
    private String gpsOrigin;
    private static final int ASK_GPS2 = 43;

    /** Pont appelé par la boutique : vérifie et active la localisation sans que la cliente cherche dans les réglages. */
    public class Bridge {
        @JavascriptInterface public boolean gpsOn() {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        }
        @JavascriptInterface public boolean allowed() {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        @JavascriptInterface public void fixLocation() {
            runOnUiThread(() -> {
                SharedPreferences sp = getSharedPreferences("gs", MODE_PRIVATE);
                if (!allowed()) {
                    boolean asked = sp.getBoolean("gpsAsked", false);
                    if (!asked || shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        sp.edit().putBoolean("gpsAsked", true).apply();
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, ASK_GPS2);
                    } else {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                        try { startActivity(i); } catch (Exception ignored) { }
                    }
                } else if (!gpsOn()) {
                    try { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); } catch (Exception ignored) { }
                } else {
                    resumeWeb();
                }
            });
        }
    }

    private void resumeWeb() {
        if (web != null) web.evaluateJavascript("window.dispatchEvent(new Event('graceResume'))", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumeWeb();
    }

    private WebView web;
    private ValueCallback<Uri[]> pending;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0B0907"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        web.addJavascriptInterface(new Bridge(), "GraceShop");
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setAllowFileAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " GraceShopApp/1");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme();
                if (("https".equals(scheme) || "http".equals(scheme)) && HOST.equals(u.getHost())) {
                    return false; // reste dans l'application
                }
                openOutside(u); // WhatsApp, téléphone, TikTok, Instagram…
                return true;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    v.loadUrl("file:///android_asset/offline.html");
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    cb.invoke(origin, true, true);
                } else {
                    gpsCb = cb; gpsOrigin = origin;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, ASK_GPS);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (pending != null) pending.onReceiveValue(null);
                pending = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                String[] types = p.getAcceptTypes();
                String type = (types != null && types.length > 0 && types[0] != null && !types[0].isEmpty()) ? types[0] : "*/*";
                if (type.startsWith(".")) type = "*/*";
                i.setType(type);
                if (types != null && types.length > 1) i.putExtra(Intent.EXTRA_MIME_TYPES, types);
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, p.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try {
                    startActivityForResult(Intent.createChooser(i, "Choisir"), PICK_FILES);
                } catch (ActivityNotFoundException e) {
                    pending = null;
                    return false;
                }
                return true;
            }
        });

        if (state != null) web.restoreState(state);
        else web.loadUrl(HOME);
    }

    private void openOutside(Uri u) {
        try {
            if ("intent".equals(u.getScheme())) {
                startActivity(Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME));
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, u));
            }
        } catch (Exception ignored) { }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == PICK_FILES && pending != null) {
            Uri[] out = null;
            if (res == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    out = new Uri[n];
                    for (int k = 0; k < n; k++) out[k] = data.getClipData().getItemAt(k).getUri();
                } else if (data.getData() != null) {
                    out = new Uri[]{data.getData()};
                }
            }
            pending.onReceiveValue(out);
            pending = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == ASK_GPS2) { getSharedPreferences("gs", MODE_PRIVATE).edit().putBoolean("gpsAsked", true).apply(); resumeWeb(); return; }
        if (req == ASK_GPS) getSharedPreferences("gs", MODE_PRIVATE).edit().putBoolean("gpsAsked", true).apply();
        if (req == ASK_GPS && gpsCb != null) {
            boolean ok = false;
            for (int r : res) if (r == PackageManager.PERMISSION_GRANTED) ok = true;
            gpsCb.invoke(gpsOrigin, ok, ok);
            gpsCb = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }
}
