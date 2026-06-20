package com.example.cctest;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import com.example.cctest.widget.BottomInputPanelView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class BaiduWebViewActivity extends AppCompatActivity {

    private static final String BAIDU_URL = "https://www.baidu.com";

    private WebView webView;
    private BottomInputPanelView bottomInputPanel;
    private BaiduWebManualModeManager manualModeManager;
    private MaterialButton manualToggleButton;
    private MaterialButton manualOnlineToggleButton;
    private MaterialButton manualAgentTypeToggleButton;
    private MaterialButton appendSuggestionItemsButton;
    private MaterialButton suggestionListToggleButton;
    private MaterialButton replaceSuggestionItemsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_baidu_web_view);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        webView = findViewById(R.id.web_view);
        bottomInputPanel = findViewById(R.id.bottom_input_container);
        manualToggleButton = findViewById(R.id.button_toggle_manual);
        manualOnlineToggleButton = findViewById(R.id.button_toggle_manual_online);
        manualAgentTypeToggleButton = findViewById(R.id.button_toggle_manual_agent_type);
        appendSuggestionItemsButton = findViewById(R.id.button_append_suggestion_items);
        suggestionListToggleButton = findViewById(R.id.button_toggle_suggestion_list);
        replaceSuggestionItemsButton = findViewById(R.id.button_replace_suggestion_items);
        manualModeManager = new BaiduWebManualModeManager(
            savedInstanceState,
            bottomInputPanel,
            this::updateManualControlTexts
        );

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.baidu_web_view_title);
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        configureWebView(webView);
        configureManualControls();
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

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (bottomInputPanel != null) {
            bottomInputPanel.prepareKeyboardDismissIfTouchOutsideTextInput(event);
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (bottomInputPanel != null) {
            bottomInputPanel.finishKeyboardDismissIfTouchOutsideTextInput(event);
        }
        return handled;
    }

    private void configureManualControls() {
        if (manualToggleButton != null) {
            manualToggleButton.setOnClickListener(
                view -> manualModeManager.handleManualToggleButtonClick()
            );
        }
        if (manualOnlineToggleButton != null) {
            manualOnlineToggleButton.setOnClickListener(
                view -> manualModeManager.handleManualOnlineToggleClick()
            );
        }
        if (manualAgentTypeToggleButton != null) {
            manualAgentTypeToggleButton.setOnClickListener(
                view -> manualModeManager.handleManualAgentTypeToggleClick()
            );
        }
        if (appendSuggestionItemsButton != null) {
            appendSuggestionItemsButton.setOnClickListener(
                view -> manualModeManager.handleAppendSuggestionItemsClick()
            );
        }
        if (suggestionListToggleButton != null) {
            suggestionListToggleButton.setOnClickListener(
                view -> manualModeManager.handleSuggestionListToggleClick()
            );
        }
        if (replaceSuggestionItemsButton != null) {
            replaceSuggestionItemsButton.setOnClickListener(
                view -> manualModeManager.handleReplaceSuggestionItemsClick()
            );
        }
        updateManualControlTexts();
    }

    private void updateManualControlTexts() {
        if (manualModeManager == null) {
            return;
        }
        if (manualToggleButton != null) {
            manualToggleButton.setText(manualModeManager.getManualToggleTextResource());
        }
        if (manualOnlineToggleButton != null) {
            manualOnlineToggleButton.setText(manualModeManager.getManualOnlineTextResource());
        }
        if (manualAgentTypeToggleButton != null) {
            manualAgentTypeToggleButton.setText(
                manualModeManager.getManualAgentTypeTextResource()
            );
        }
        if (suggestionListToggleButton != null) {
            suggestionListToggleButton.setText(
                manualModeManager.getSuggestionListToggleTextResource()
            );
        }
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
        if (manualModeManager != null) {
            manualModeManager.saveInstanceState(outState);
        }
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        if (manualModeManager != null) {
            manualModeManager.release();
            manualModeManager = null;
        }
        if (bottomInputPanel != null) {
            bottomInputPanel.release();
            bottomInputPanel = null;
        }
        manualToggleButton = null;
        manualOnlineToggleButton = null;
        manualAgentTypeToggleButton = null;
        appendSuggestionItemsButton = null;
        suggestionListToggleButton = null;
        replaceSuggestionItemsButton = null;
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
