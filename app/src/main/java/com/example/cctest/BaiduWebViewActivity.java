package com.example.cctest;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import com.example.cctest.widget.BottomInputPanelView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class BaiduWebViewActivity extends AppCompatActivity {

    private static final String BAIDU_URL = "https://www.baidu.com";
    private static final String KEY_MANUAL_MODE_ENABLED = "manual_mode_enabled";

    private WebView webView;
    private BottomInputPanelView bottomInputPanel;
    private MaterialButton manualToggleButton;
    private boolean manualModeEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_baidu_web_view);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        webView = findViewById(R.id.web_view);
        bottomInputPanel = findViewById(R.id.bottom_input_container);
        manualToggleButton = findViewById(R.id.button_toggle_manual);
        manualModeEnabled = savedInstanceState != null
            && savedInstanceState.getBoolean(KEY_MANUAL_MODE_ENABLED);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.baidu_web_view_title);
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        configureWebView(webView);
        configureManualToggleButton();
        if (savedInstanceState == null) {
            webView.loadUrl(BAIDU_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
    }

    private void configureManualToggleButton() {
        if (bottomInputPanel != null) {
            bottomInputPanel.setModeChangeListener(enabled -> {
                manualModeEnabled = enabled;
                updateManualToggleButtonText();
            });
            bottomInputPanel.setManualModeEnabled(manualModeEnabled);
        }
        if (manualToggleButton == null) {
            return;
        }
        manualToggleButton.setOnClickListener(view -> {
            manualModeEnabled = !manualModeEnabled;
            if (bottomInputPanel != null) {
                bottomInputPanel.setManualModeEnabled(manualModeEnabled);
            }
            updateManualToggleButtonText();
        });
        updateManualToggleButtonText();
    }

    private void updateManualToggleButtonText() {
        if (manualToggleButton == null) {
            return;
        }
        manualToggleButton.setText(
            manualModeEnabled
                ? R.string.baidu_web_toggle_manual_exit
                : R.string.baidu_web_toggle_manual_enter
        );
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_MANUAL_MODE_ENABLED, manualModeEnabled);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        if (bottomInputPanel != null) {
            bottomInputPanel.release();
            bottomInputPanel = null;
        }
        manualToggleButton = null;
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
