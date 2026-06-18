package com.example.cctest;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import com.example.cctest.voice.PicVoiceRecordPanel;
import com.example.cctest.voice.VoiceRecordCallback;
import com.google.android.material.appbar.MaterialToolbar;

public class BaiduWebViewActivity extends AppCompatActivity {

    private static final String BAIDU_URL = "https://www.baidu.com";

    private WebView webView;
    private View contentRoot;
    private View bottomInputBar;
    private EditText consultInput;
    private PicVoiceRecordPanel voiceRecordPanel;
    private ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener;
    private int inputBarBaseBottomMargin;
    private int webViewBaseBottomMargin;
    private int webViewInputBarSpacing;
    private int keyboardVisibilityThreshold;
    private int inputBarKeyboardBottomMargin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(R.layout.activity_baidu_web_view);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        webView = findViewById(R.id.web_view);
        bottomInputBar = findViewById(R.id.bottom_input_bar);
        consultInput = findViewById(R.id.edit_text_consult_content);
        contentRoot = findViewById(android.R.id.content);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.baidu_web_view_title);
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        configureWebView(webView);
        configureKeyboardAvoidance();
        configureVoiceRecordPanel();
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

    private void configureKeyboardAvoidance() {
        if (contentRoot == null || bottomInputBar == null || webView == null) {
            return;
        }

        inputBarBaseBottomMargin = getBottomMargin(bottomInputBar);
        webViewBaseBottomMargin = getBottomMargin(webView);
        webViewInputBarSpacing = Math.max(0, webViewBaseBottomMargin - inputBarBaseBottomMargin);
        keyboardVisibilityThreshold = dpToPx(80);
        inputBarKeyboardBottomMargin = dpToPx(10);
        final Rect visibleFrame = new Rect();
        final int[] rootLocation = new int[2];

        keyboardLayoutListener = () -> {
            contentRoot.getWindowVisibleDisplayFrame(visibleFrame);
            contentRoot.getLocationOnScreen(rootLocation);

            int visibleBottomInRoot = visibleFrame.bottom - rootLocation[1];
            int keyboardHeight = Math.max(0, contentRoot.getHeight() - visibleBottomInRoot);
            boolean keyboardVisible = keyboardHeight >= keyboardVisibilityThreshold;

            int inputBarBottomMargin = keyboardVisible
                ? keyboardHeight + inputBarKeyboardBottomMargin
                : inputBarBaseBottomMargin;
            int webViewBottomMargin = keyboardVisible
                ? inputBarBottomMargin + webViewInputBarSpacing
                : webViewBaseBottomMargin;
            setBottomMargin(bottomInputBar, inputBarBottomMargin);
            setBottomMargin(webView, webViewBottomMargin);
        };
        contentRoot.getViewTreeObserver().addOnGlobalLayoutListener(keyboardLayoutListener);
    }

    private void configureVoiceRecordPanel() {
        if (consultInput == null) {
            return;
        }
        consultInput.setLongClickable(false);
        voiceRecordPanel = new PicVoiceRecordPanel(this);
        voiceRecordPanel.setCallback(new VoiceRecordCallback() {
            @Override
            public void onStart() {
                hideKeyboard();
                consultInput.clearFocus();
            }

            @Override
            public void onCancel() {
                // No-op. The panel already shows the cancel state while dragging.
            }

            @Override
            public void onFinish() {
                // No-op. Recording completion is handled by the panel lifecycle.
            }
        });
        voiceRecordPanel.bindToHoldTriggerPreservingClick(consultInput);
    }

    private void hideKeyboard() {
        View focusedView = getCurrentFocus();
        if (focusedView == null) {
            focusedView = consultInput;
        }
        if (focusedView == null) {
            return;
        }
        InputMethodManager inputMethodManager =
            (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
        }
    }

    private int getBottomMargin(View view) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            return ((ViewGroup.MarginLayoutParams) layoutParams).bottomMargin;
        }
        return 0;
    }

    private void setBottomMargin(View view, int bottomMargin) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams marginLayoutParams =
            (ViewGroup.MarginLayoutParams) layoutParams;
        if (marginLayoutParams.bottomMargin == bottomMargin) {
            return;
        }
        marginLayoutParams.bottomMargin = bottomMargin;
        view.setLayoutParams(marginLayoutParams);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        if (contentRoot != null && keyboardLayoutListener != null) {
            contentRoot.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardLayoutListener);
            keyboardLayoutListener = null;
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        if (voiceRecordPanel != null) {
            voiceRecordPanel.dismiss();
            voiceRecordPanel = null;
        }
        consultInput = null;
        bottomInputBar = null;
        contentRoot = null;
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
