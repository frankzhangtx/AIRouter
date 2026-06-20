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
    private static final String KEY_MANUAL_AGENT_ONLINE = "manual_agent_online";

    private WebView webView;
    private BottomInputPanelView bottomInputPanel;
    private MaterialButton manualToggleButton;
    private MaterialButton manualOnlineToggleButton;
    private boolean manualModeEnabled;
    private boolean manualAgentOnline = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_baidu_web_view);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        webView = findViewById(R.id.web_view);
        bottomInputPanel = findViewById(R.id.bottom_input_container);
        manualToggleButton = findViewById(R.id.button_toggle_manual);
        manualOnlineToggleButton = findViewById(R.id.button_toggle_manual_online);
        manualModeEnabled = savedInstanceState != null
            && savedInstanceState.getBoolean(KEY_MANUAL_MODE_ENABLED);
        manualAgentOnline = savedInstanceState == null
            || savedInstanceState.getBoolean(KEY_MANUAL_AGENT_ONLINE, true);

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
            bottomInputPanel.setManualAgentOnline(manualAgentOnline);
        }
        if (manualToggleButton == null) {
            return;
        }
        manualToggleButton.setOnClickListener(view -> handleManualToggleButtonClick());
        if (manualOnlineToggleButton != null) {
            manualOnlineToggleButton.setOnClickListener(view -> handleManualOnlineToggleClick());
        }
        updateManualToggleButtonText();
        updateManualOnlineToggleButtonText();
    }

    private void handleManualToggleButtonClick() {
        setManualModeEnabledFromActivity(!manualModeEnabled);
    }

    private void setManualModeEnabledFromActivity(boolean enabled) {
        manualModeEnabled = enabled;
        if (bottomInputPanel != null) {
            bottomInputPanel.setManualModeEnabled(manualModeEnabled);
        }
        updateManualToggleButtonText();
    }

    private void handleManualOnlineToggleClick() {
        manualAgentOnline = !manualAgentOnline;
        if (bottomInputPanel != null) {
            bottomInputPanel.setManualAgentOnline(manualAgentOnline);
        }
        updateManualOnlineToggleButtonText();
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

    private void updateManualOnlineToggleButtonText() {
        if (manualOnlineToggleButton == null) {
            return;
        }
        manualOnlineToggleButton.setText(
            manualAgentOnline
                ? R.string.baidu_web_manual_online
                : R.string.baidu_web_manual_offline
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
        outState.putBoolean(KEY_MANUAL_AGENT_ONLINE, manualAgentOnline);
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
        manualOnlineToggleButton = null;
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
