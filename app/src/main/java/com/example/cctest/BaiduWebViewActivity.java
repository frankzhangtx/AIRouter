package com.example.cctest;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import com.example.cctest.widget.BottomInputPanelView;
import com.example.cctest.widget.BottomInputPanelView.ManualAgentType;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class BaiduWebViewActivity extends AppCompatActivity {

    private static final String BAIDU_URL = "https://www.baidu.com";
    private static final String KEY_MANUAL_MODE_ENABLED = "manual_mode_enabled";
    private static final String KEY_MANUAL_AGENT_ONLINE = "manual_agent_online";
    private static final String KEY_MANUAL_AGENT_TYPE = "manual_agent_type";
    private static final String KEY_HORIZONTAL_SUGGESTION_LIST_VISIBLE =
        "horizontal_suggestion_list_visible";

    private WebView webView;
    private BottomInputPanelView bottomInputPanel;
    private MaterialButton manualToggleButton;
    private MaterialButton manualOnlineToggleButton;
    private MaterialButton manualAgentTypeToggleButton;
    private MaterialButton appendSuggestionItemsButton;
    private MaterialButton suggestionListToggleButton;
    private MaterialButton replaceSuggestionItemsButton;
    private boolean manualModeEnabled;
    private boolean manualAgentOnline = true;
    private ManualAgentType manualAgentType = ManualAgentType.ONLINE_SERVICE;
    private boolean horizontalSuggestionListVisible;

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
        manualModeEnabled = savedInstanceState != null
            && savedInstanceState.getBoolean(KEY_MANUAL_MODE_ENABLED);
        manualAgentOnline = savedInstanceState == null
            || savedInstanceState.getBoolean(KEY_MANUAL_AGENT_ONLINE, true);
        manualAgentType = readManualAgentType(savedInstanceState);
        horizontalSuggestionListVisible = savedInstanceState != null
            && savedInstanceState.getBoolean(KEY_HORIZONTAL_SUGGESTION_LIST_VISIBLE);

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
            bottomInputPanel.setManualAgentType(manualAgentType);
            bottomInputPanel.setHorizontalSuggestionListVisible(horizontalSuggestionListVisible);
        }
        if (manualToggleButton == null) {
            return;
        }
        manualToggleButton.setOnClickListener(view -> handleManualToggleButtonClick());
        if (manualOnlineToggleButton != null) {
            manualOnlineToggleButton.setOnClickListener(view -> handleManualOnlineToggleClick());
        }
        if (manualAgentTypeToggleButton != null) {
            manualAgentTypeToggleButton.setOnClickListener(
                view -> handleManualAgentTypeToggleClick()
            );
        }
        if (appendSuggestionItemsButton != null) {
            appendSuggestionItemsButton.setOnClickListener(
                view -> handleAppendSuggestionItemsClick()
            );
        }
        if (suggestionListToggleButton != null) {
            suggestionListToggleButton.setOnClickListener(
                view -> handleSuggestionListToggleClick()
            );
        }
        if (replaceSuggestionItemsButton != null) {
            replaceSuggestionItemsButton.setOnClickListener(
                view -> handleReplaceSuggestionItemsClick()
            );
        }
        updateManualToggleButtonText();
        updateManualOnlineToggleButtonText();
        updateManualAgentTypeToggleButtonText();
        updateSuggestionListToggleButtonText();
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

    private void handleManualAgentTypeToggleClick() {
        manualAgentType = manualAgentType == ManualAgentType.ONLINE_SERVICE
            ? ManualAgentType.INSURANCE_PLANNER
            : ManualAgentType.ONLINE_SERVICE;
        if (bottomInputPanel != null) {
            bottomInputPanel.setManualAgentType(manualAgentType);
        }
        updateManualAgentTypeToggleButtonText();
    }

    private void handleAppendSuggestionItemsClick() {
        if (bottomInputPanel != null) {
            bottomInputPanel.appendAdditionalHorizontalSuggestions();
        }
        ensureSuggestionListVisible();
    }

    private void handleSuggestionListToggleClick() {
        horizontalSuggestionListVisible = !horizontalSuggestionListVisible;
        if (bottomInputPanel != null) {
            bottomInputPanel.setHorizontalSuggestionListVisible(
                horizontalSuggestionListVisible
            );
        }
        updateSuggestionListToggleButtonText();
    }

    private void handleReplaceSuggestionItemsClick() {
        if (bottomInputPanel != null) {
            bottomInputPanel.replaceHorizontalSuggestions();
        }
        ensureSuggestionListVisible();
    }

    private void ensureSuggestionListVisible() {
        if (horizontalSuggestionListVisible) {
            return;
        }
        horizontalSuggestionListVisible = true;
        if (bottomInputPanel != null) {
            bottomInputPanel.setHorizontalSuggestionListVisible(true);
        }
        updateSuggestionListToggleButtonText();
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

    private void updateManualAgentTypeToggleButtonText() {
        if (manualAgentTypeToggleButton == null) {
            return;
        }
        manualAgentTypeToggleButton.setText(
            manualAgentType == ManualAgentType.ONLINE_SERVICE
                ? R.string.baidu_web_manual_type_online_service
                : R.string.baidu_web_manual_type_insurance_planner
        );
    }

    private void updateSuggestionListToggleButtonText() {
        if (suggestionListToggleButton == null) {
            return;
        }
        suggestionListToggleButton.setText(
            horizontalSuggestionListVisible
                ? R.string.baidu_web_remove_suggestion_list
                : R.string.baidu_web_add_suggestion_list
        );
    }

    private ManualAgentType readManualAgentType(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return ManualAgentType.ONLINE_SERVICE;
        }
        String manualAgentTypeName = savedInstanceState.getString(KEY_MANUAL_AGENT_TYPE);
        if (manualAgentTypeName == null) {
            return ManualAgentType.ONLINE_SERVICE;
        }
        try {
            return ManualAgentType.valueOf(manualAgentTypeName);
        } catch (IllegalArgumentException exception) {
            return ManualAgentType.ONLINE_SERVICE;
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
        outState.putBoolean(KEY_MANUAL_MODE_ENABLED, manualModeEnabled);
        outState.putBoolean(KEY_MANUAL_AGENT_ONLINE, manualAgentOnline);
        outState.putString(KEY_MANUAL_AGENT_TYPE, manualAgentType.name());
        outState.putBoolean(
            KEY_HORIZONTAL_SUGGESTION_LIST_VISIBLE,
            horizontalSuggestionListVisible
        );
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
