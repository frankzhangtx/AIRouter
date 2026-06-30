package com.example.cctest;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.example.cctest.widget.BottomInputPanelView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class BaiduWebViewActivity extends AppCompatActivity {

    private static final String BAIDU_URL = "https://www.baidu.com";
    private static final String KEY_BOTTOM_OVERLAY_VISIBLE = "bottom_overlay_visible";
    private static final String KEY_BOTTOM_PANEL_VISIBLE = "bottom_panel_visible";
    private static final String KEY_BOTTOM_PANEL_LAYER_ABOVE_WEB_VIEW =
        "bottom_panel_layer_above_web_view";
    private static final String KEY_BOTTOM_PANEL_ATTACHMENT_VISIBLE_WHEN_HIDDEN =
        "bottom_panel_attachment_visible_when_hidden";

    private WebView webView;
    private BottomInputPanelView bottomInputPanel;
    private View bottomInputOverlay;
    private View manualControls;
    private View.OnLayoutChangeListener bottomInputPanelLayoutChangeListener;
    private BaiduWebManualModeManager manualModeManager;
    private MaterialButton voiceRecordPanelToggleButton;
    private MaterialButton manualToggleButton;
    private MaterialButton manualOnlineToggleButton;
    private MaterialButton manualAgentTypeToggleButton;
    private MaterialButton appendSuggestionItemsButton;
    private MaterialButton suggestionListToggleButton;
    private MaterialButton replaceSuggestionItemsButton;
    private MaterialButton bottomOverlayToggleButton;
    private MaterialButton bottomPanelHeightButton;
    private MaterialButton bottomPanelVisibilityToggleButton;
    private MaterialButton bottomPanelLayerToggleButton;
    private boolean bottomOverlayVisible;
    private boolean bottomPanelVisible;
    private boolean bottomPanelLayerAboveWebView = true;
    private boolean bottomPanelAttachmentVisibleWhenHidden;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_baidu_web_view);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        webView = findViewById(R.id.web_view);
        bottomInputPanel = findViewById(R.id.bottom_input_container);
        bottomInputOverlay = findViewById(R.id.bottom_input_overlay);
        manualControls = findViewById(R.id.manual_controls);
        voiceRecordPanelToggleButton = findViewById(R.id.button_toggle_voice_record_panel);
        manualToggleButton = findViewById(R.id.button_toggle_manual);
        manualOnlineToggleButton = findViewById(R.id.button_toggle_manual_online);
        manualAgentTypeToggleButton = findViewById(R.id.button_toggle_manual_agent_type);
        appendSuggestionItemsButton = findViewById(R.id.button_append_suggestion_items);
        suggestionListToggleButton = findViewById(R.id.button_toggle_suggestion_list);
        replaceSuggestionItemsButton = findViewById(R.id.button_replace_suggestion_items);
        bottomOverlayToggleButton = findViewById(R.id.button_toggle_bottom_overlay);
        bottomPanelHeightButton = findViewById(R.id.button_get_bottom_panel_height);
        bottomPanelVisibilityToggleButton =
            findViewById(R.id.button_toggle_bottom_panel_visibility);
        bottomPanelLayerToggleButton = findViewById(R.id.button_toggle_bottom_panel_layer);
        bottomOverlayVisible = savedInstanceState != null
            && savedInstanceState.getBoolean(KEY_BOTTOM_OVERLAY_VISIBLE);
        bottomPanelVisible = savedInstanceState != null
            && savedInstanceState.getBoolean(KEY_BOTTOM_PANEL_VISIBLE, false);
        bottomPanelLayerAboveWebView = savedInstanceState == null
            || savedInstanceState.getBoolean(KEY_BOTTOM_PANEL_LAYER_ABOVE_WEB_VIEW, true);
        bottomPanelAttachmentVisibleWhenHidden = savedInstanceState != null
            && savedInstanceState.getBoolean(
                KEY_BOTTOM_PANEL_ATTACHMENT_VISIBLE_WHEN_HIDDEN
            );
        manualModeManager = new BaiduWebManualModeManager(
            savedInstanceState,
            bottomInputPanel,
            new BaiduWebManualModeManager.Listener() {
                @Override
                public void onManualControlStateChanged() {
                    updateManualControlTexts();
                }

                @Override
                public void onBottomOverlayToggleRequested() {
                    toggleBottomOverlay();
                }

                @Override
                public void onBottomPanelToggleRequested() {
                    toggleBottomPanelVisibility();
                }
            }
        );

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.baidu_web_view_title);
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        configureWebView(webView);
        configureManualControls();
        updateBottomPanelVisibility();
        configureBottomOverlay();
        applyBottomPanelLayerOrder();
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
        bindManualControlClick(
            voiceRecordPanelToggleButton,
            BaiduWebManualModeManager.CONTROL_TOGGLE_VOICE_RECORD_PANEL
        );
        bindManualControlClick(
            manualToggleButton,
            BaiduWebManualModeManager.CONTROL_TOGGLE_MANUAL_MODE
        );
        bindManualControlClick(
            manualOnlineToggleButton,
            BaiduWebManualModeManager.CONTROL_TOGGLE_MANUAL_ONLINE
        );
        bindManualControlClick(
            manualAgentTypeToggleButton,
            BaiduWebManualModeManager.CONTROL_TOGGLE_MANUAL_AGENT_TYPE
        );
        bindManualControlClick(
            appendSuggestionItemsButton,
            BaiduWebManualModeManager.CONTROL_APPEND_SUGGESTION_ITEMS
        );
        bindManualControlClick(
            suggestionListToggleButton,
            BaiduWebManualModeManager.CONTROL_TOGGLE_SUGGESTION_LIST
        );
        bindManualControlClick(
            replaceSuggestionItemsButton,
            BaiduWebManualModeManager.CONTROL_REPLACE_SUGGESTION_ITEMS
        );
        bindManualControlClick(
            bottomOverlayToggleButton,
            BaiduWebManualModeManager.CONTROL_TOGGLE_BOTTOM_OVERLAY
        );
        bindManualControlClick(
            bottomPanelHeightButton,
            BaiduWebManualModeManager.CONTROL_SHOW_BOTTOM_PANEL_HEIGHT
        );
        bindManualControlClick(
            bottomPanelVisibilityToggleButton,
            BaiduWebManualModeManager.CONTROL_TOGGLE_BOTTOM_PANEL_VISIBILITY
        );
        if (bottomPanelLayerToggleButton != null) {
            bottomPanelLayerToggleButton.setOnClickListener(
                view -> toggleBottomPanelLayerOrder()
            );
        }
        updateManualControlTexts();
    }

    private void bindManualControlClick(MaterialButton button, String controlName) {
        if (button == null) {
            return;
        }
        button.setOnClickListener(view -> {
            if (manualModeManager != null) {
                manualModeManager.handleManualControlClick(controlName);
            }
        });
    }

    private void updateManualControlTexts() {
        if (manualModeManager == null) {
            return;
        }
        if (manualToggleButton != null) {
            manualToggleButton.setText(manualModeManager.getManualToggleTextResource());
        }
        if (voiceRecordPanelToggleButton != null) {
            voiceRecordPanelToggleButton.setText(
                manualModeManager.getVoiceRecordPanelToggleTextResource()
            );
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
        updateBottomOverlayToggleText();
        updateBottomPanelVisibilityToggleText();
        updateBottomPanelLayerToggleText();
    }

    private void configureBottomOverlay() {
        if (bottomInputPanel != null) {
            bottomInputPanelLayoutChangeListener = (
                view,
                left,
                top,
                right,
                bottom,
                oldLeft,
                oldTop,
                oldRight,
                oldBottom
            ) -> updateBottomOverlayFrame();
            bottomInputPanel.addOnLayoutChangeListener(bottomInputPanelLayoutChangeListener);
            bottomInputPanel.post(this::updateBottomOverlayFrame);
        }
        updateBottomOverlayVisibility();
    }

    private void toggleBottomOverlay() {
        bottomOverlayVisible = !bottomOverlayVisible;
        updateBottomOverlayVisibility();
    }

    private void updateBottomOverlayVisibility() {
        if (bottomInputOverlay != null) {
            boolean shouldShowOverlay = bottomOverlayVisible && bottomPanelVisible;
            bottomInputOverlay.setVisibility(shouldShowOverlay ? View.VISIBLE : View.GONE);
            if (shouldShowOverlay) {
                updateBottomOverlayFrame();
            }
        }
        applyBottomPanelLayerOrder();
        updateBottomOverlayToggleText();
    }

    private void updateBottomOverlayToggleText() {
        if (bottomOverlayToggleButton == null) {
            return;
        }
        bottomOverlayToggleButton.setText(
            bottomOverlayVisible
                ? R.string.baidu_web_hide_bottom_overlay
                : R.string.baidu_web_show_bottom_overlay
        );
    }

    private void toggleBottomPanelVisibility() {
        if (bottomPanelVisible && bottomInputPanel != null) {
            bottomPanelAttachmentVisibleWhenHidden =
                bottomInputPanel.isAttachmentPanelVisible();
        }
        bottomPanelVisible = !bottomPanelVisible;
        updateBottomPanelVisibility();
    }

    private void updateBottomPanelVisibility() {
        if (bottomInputPanel != null) {
            if (!bottomPanelVisible) {
                bottomInputPanel.dismissKeyboardForPanelHide();
            }
            bottomInputPanel.setVisibility(bottomPanelVisible ? View.VISIBLE : View.GONE);
            if (bottomPanelVisible) {
                bottomInputPanel.setAttachmentPanelVisible(
                    bottomPanelAttachmentVisibleWhenHidden
                );
                bottomInputPanel.post(this::updateBottomOverlayFrame);
            }
        }
        updateBottomOverlayVisibility();
        updateBottomPanelVisibilityToggleText();
        applyBottomPanelLayerOrder();
    }

    private void updateBottomPanelVisibilityToggleText() {
        if (bottomPanelVisibilityToggleButton == null) {
            return;
        }
        bottomPanelVisibilityToggleButton.setText(
            bottomPanelVisible
                ? R.string.baidu_web_hide_bottom_panel
                : R.string.baidu_web_show_bottom_panel
        );
    }

    private void toggleBottomPanelLayerOrder() {
        bottomPanelLayerAboveWebView = !bottomPanelLayerAboveWebView;
        applyBottomPanelLayerOrder();
    }

    private void applyBottomPanelLayerOrder() {
        if (webView == null) {
            updateBottomPanelLayerToggleText();
            return;
        }
        webView.setTranslationZ(bottomPanelLayerAboveWebView ? 0f : 2f);
        if (bottomInputPanel != null) {
            bottomInputPanel.setTranslationZ(bottomPanelLayerAboveWebView ? 1f : 0f);
            bottomInputPanel.bringToFront();
        }
        if (bottomInputOverlay != null) {
            bottomInputOverlay.setTranslationZ(bottomPanelLayerAboveWebView ? 1f : 0f);
            bottomInputOverlay.bringToFront();
        }
        if (!bottomPanelLayerAboveWebView) {
            webView.bringToFront();
        }
        if (manualControls != null) {
            manualControls.setTranslationZ(3f);
            manualControls.bringToFront();
        }
        updateBottomPanelLayerToggleText();
    }

    private void updateBottomPanelLayerToggleText() {
        if (bottomPanelLayerToggleButton == null) {
            return;
        }
        bottomPanelLayerToggleButton.setText(
            bottomPanelLayerAboveWebView
                ? R.string.baidu_web_bottom_panel_layer_above
                : R.string.baidu_web_bottom_panel_layer_below
        );
    }

    private void updateBottomOverlayFrame() {
        if (bottomInputOverlay == null || bottomInputPanel == null || !bottomPanelVisible) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = bottomInputOverlay.getLayoutParams();
        if (!(layoutParams instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams overlayLayoutParams =
            (FrameLayout.LayoutParams) layoutParams;
        int inputPanelHeight = bottomInputPanel.getVisualHeightForOverlay();
        int inputPanelBottomMargin = getBottomMargin(bottomInputPanel);

        boolean changed = false;
        if (overlayLayoutParams.height != inputPanelHeight) {
            overlayLayoutParams.height = inputPanelHeight;
            changed = true;
        }
        if (overlayLayoutParams.bottomMargin != inputPanelBottomMargin) {
            overlayLayoutParams.bottomMargin = inputPanelBottomMargin;
            changed = true;
        }
        if (overlayLayoutParams.gravity != Gravity.BOTTOM) {
            overlayLayoutParams.gravity = Gravity.BOTTOM;
            changed = true;
        }
        if (changed) {
            bottomInputOverlay.setLayoutParams(overlayLayoutParams);
        }
    }

    private int getBottomMargin(View view) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            return ((ViewGroup.MarginLayoutParams) layoutParams).bottomMargin;
        }
        return 0;
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
        outState.putBoolean(KEY_BOTTOM_OVERLAY_VISIBLE, bottomOverlayVisible);
        outState.putBoolean(KEY_BOTTOM_PANEL_VISIBLE, bottomPanelVisible);
        outState.putBoolean(
            KEY_BOTTOM_PANEL_LAYER_ABOVE_WEB_VIEW,
            bottomPanelLayerAboveWebView
        );
        outState.putBoolean(
            KEY_BOTTOM_PANEL_ATTACHMENT_VISIBLE_WHEN_HIDDEN,
            bottomPanelAttachmentVisibleWhenHidden
        );
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
        if (bottomInputPanel != null && bottomInputPanelLayoutChangeListener != null) {
            bottomInputPanel.removeOnLayoutChangeListener(bottomInputPanelLayoutChangeListener);
            bottomInputPanelLayoutChangeListener = null;
        }
        if (bottomInputPanel != null) {
            bottomInputPanel.release();
            bottomInputPanel = null;
        }
        bottomInputOverlay = null;
        voiceRecordPanelToggleButton = null;
        manualToggleButton = null;
        manualOnlineToggleButton = null;
        manualAgentTypeToggleButton = null;
        appendSuggestionItemsButton = null;
        suggestionListToggleButton = null;
        replaceSuggestionItemsButton = null;
        bottomOverlayToggleButton = null;
        bottomPanelHeightButton = null;
        bottomPanelVisibilityToggleButton = null;
        bottomPanelLayerToggleButton = null;
        manualControls = null;
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
