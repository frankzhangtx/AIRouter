package com.example.cctest;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.TextWatcher;
import android.view.Gravity;
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
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import com.example.cctest.voice.PicVoiceRecordPanel;
import com.example.cctest.voice.VoiceRecordCallback;
import com.google.android.material.appbar.MaterialToolbar;

public class BaiduWebViewActivity extends AppCompatActivity {

    private static final String BAIDU_URL = "https://www.baidu.com";
    private static final int TEXT_INPUT_MAX_LINES = 4;

    private WebView webView;
    private View contentRoot;
    private View bottomInputContainer;
    private View bottomInputBar;
    private View inputBottomFill;
    private View attachmentPanel;
    private ImageButton buttonVoiceInput;
    private ImageButton buttonAddContent;
    private EditText consultInput;
    private PicVoiceRecordPanel voiceRecordPanel;
    private ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener;
    private View.OnLayoutChangeListener inputBarLayoutChangeListener;
    private TextWatcher inputTextWatcher;
    private boolean voiceInputMode;
    private boolean keyboardVisible;
    private String textInputDraft = "";
    private int textInputOriginalInputType;
    private int textInputOriginalImeOptions;
    private int textInputOriginalGravity;
    private Typeface textInputOriginalTypeface;
    private int inputBarBaseBottomMargin;
    private int keyboardVisibilityThreshold;
    private int inputBarKeyboardBottomMargin;
    private int currentKeyboardHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(R.layout.activity_baidu_web_view);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        webView = findViewById(R.id.web_view);
        bottomInputContainer = findViewById(R.id.bottom_input_container);
        bottomInputBar = findViewById(R.id.bottom_input_bar);
        inputBottomFill = findViewById(R.id.input_bottom_fill);
        attachmentPanel = findViewById(R.id.input_attachment_panel);
        buttonVoiceInput = findViewById(R.id.button_voice_input);
        buttonAddContent = findViewById(R.id.button_add_content);
        consultInput = findViewById(R.id.edit_text_consult_content);
        contentRoot = findViewById(android.R.id.content);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.baidu_web_view_title);
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        configureWebView(webView);
        configureTextInputWrapping();
        configureKeyboardAvoidance();
        configureVoiceRecordPanel();
        configureVoiceInputToggle();
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

    private void configureTextInputWrapping() {
        if (consultInput == null) {
            return;
        }

        applyTextInputWrapping();
        consultInput.setVerticalScrollBarEnabled(false);
        consultInput.setOverScrollMode(View.OVER_SCROLL_NEVER);
        inputTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                // No-op.
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                // No-op.
            }

            @Override
            public void afterTextChanged(Editable text) {
                keepTextInputCursorOnBottomLine();
                updateTextInputActionState();
                updateContentInsetsForInputBar();
            }
        };
        consultInput.addTextChangedListener(inputTextWatcher);
    }

    private void configureKeyboardAvoidance() {
        if (contentRoot == null || bottomInputContainer == null) {
            return;
        }

        inputBarBaseBottomMargin = getBottomMargin(bottomInputContainer);
        keyboardVisibilityThreshold = dpToPx(80);
        inputBarKeyboardBottomMargin = dpToPx(10);
        final Rect visibleFrame = new Rect();
        final int[] rootLocation = new int[2];

        keyboardLayoutListener = () -> {
            contentRoot.getWindowVisibleDisplayFrame(visibleFrame);
            contentRoot.getLocationOnScreen(rootLocation);

            int visibleBottomInRoot = visibleFrame.bottom - rootLocation[1];
            currentKeyboardHeight = Math.max(0, contentRoot.getHeight() - visibleBottomInRoot);
            keyboardVisible = currentKeyboardHeight >= keyboardVisibilityThreshold;
            updateTextInputActionState();
            updateContentInsetsForInputBar();
        };
        contentRoot.getViewTreeObserver().addOnGlobalLayoutListener(keyboardLayoutListener);
        inputBarLayoutChangeListener = (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
            updateContentInsetsForInputBar();
        bottomInputContainer.addOnLayoutChangeListener(inputBarLayoutChangeListener);
        bottomInputContainer.post(this::updateContentInsetsForInputBar);
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
        bindTextInputHoldTrigger();
    }

    private void configureVoiceInputToggle() {
        if (
            buttonVoiceInput == null
                || buttonAddContent == null
                || consultInput == null
                || voiceRecordPanel == null
        ) {
            return;
        }

        textInputOriginalInputType = consultInput.getInputType();
        textInputOriginalImeOptions = consultInput.getImeOptions();
        textInputOriginalGravity = consultInput.getGravity();
        textInputOriginalTypeface = consultInput.getTypeface();
        buttonVoiceInput.setOnClickListener(view -> setVoiceInputMode(!voiceInputMode));
        buttonAddContent.setOnClickListener(view -> handleTrailingActionClick());
        setVoiceInputMode(false);
        updateTextInputActionState();
    }

    private void setVoiceInputMode(boolean enabled) {
        if (
            buttonVoiceInput == null
                || buttonAddContent == null
                || consultInput == null
                || voiceRecordPanel == null
        ) {
            return;
        }

        if (enabled && !voiceInputMode) {
            textInputDraft = consultInput.getText().toString();
        }
        voiceInputMode = enabled;

        if (enabled) {
            hideKeyboard();
            setAttachmentPanelVisible(false);
            consultInput.clearFocus();
            voiceRecordPanel.bindToImmediateHoldTrigger(consultInput);
            buttonVoiceInput.setImageResource(R.drawable.ic_baidu_web_keyboard);
            buttonVoiceInput.setContentDescription(
                getString(R.string.baidu_web_keyboard_content_description)
            );
            consultInput.setInputType(InputType.TYPE_NULL);
            consultInput.setFocusable(false);
            consultInput.setFocusableInTouchMode(false);
            consultInput.setCursorVisible(false);
            consultInput.setTextIsSelectable(false);
            consultInput.setSingleLine(true);
            consultInput.setMinLines(1);
            consultInput.setMaxLines(1);
            consultInput.setHorizontallyScrolling(false);
            consultInput.setGravity(Gravity.CENTER);
            consultInput.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            consultInput.setText(R.string.baidu_web_hold_to_talk);
            updateTextInputActionState();
            updateContentInsetsForInputBar();
        } else {
            bindTextInputHoldTrigger();
            buttonVoiceInput.setImageResource(R.drawable.ic_baidu_web_voice);
            buttonVoiceInput.setContentDescription(
                getString(R.string.baidu_web_voice_content_description)
            );
            consultInput.setFocusable(true);
            consultInput.setFocusableInTouchMode(true);
            consultInput.setInputType(textInputOriginalInputType);
            consultInput.setCursorVisible(true);
            consultInput.setTextIsSelectable(false);
            applyTextInputWrapping();
            consultInput.setImeOptions(textInputOriginalImeOptions);
            consultInput.setGravity(textInputOriginalGravity);
            consultInput.setTypeface(textInputOriginalTypeface);
            consultInput.setHint(R.string.baidu_web_input_hint);
            consultInput.setText(textInputDraft);
            consultInput.setSelection(consultInput.getText().length());
            keepTextInputCursorOnBottomLine();
            updateTextInputActionState();
            updateContentInsetsForInputBar();
        }
    }

    private void updateTextInputActionState() {
        if (buttonVoiceInput == null || buttonAddContent == null || consultInput == null) {
            return;
        }

        boolean hasInputText = !voiceInputMode && hasTextInputContent();
        boolean showSendAction = !voiceInputMode && (hasInputText || keyboardVisible);

        buttonVoiceInput.setVisibility(hasInputText ? View.GONE : View.VISIBLE);
        if (showSendAction) {
            setAttachmentPanelVisible(false);
            buttonAddContent.setImageResource(R.drawable.ic_baidu_web_send);
            buttonAddContent.setContentDescription(
                getString(R.string.baidu_web_send_content_description)
            );
            buttonAddContent.setPadding(0, 0, 0, 0);
        } else {
            buttonAddContent.setImageResource(R.drawable.ic_baidu_web_plus);
            buttonAddContent.setContentDescription(
                getString(R.string.baidu_web_plus_content_description)
            );
            int iconPadding = getResources().getDimensionPixelSize(
                R.dimen.baidu_web_input_icon_padding
            );
            buttonAddContent.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
        }
    }

    private void bindTextInputHoldTrigger() {
        if (voiceRecordPanel == null || consultInput == null) {
            return;
        }
        voiceRecordPanel.bindToHoldTriggerPreservingClickWhen(
            consultInput,
            () -> !keyboardVisible && !hasTextInputContent()
        );
    }

    private void handleTrailingActionClick() {
        if (!isPlusActionVisible()) {
            setAttachmentPanelVisible(false);
            return;
        }

        boolean shouldShowPanel = attachmentPanel == null
            || attachmentPanel.getVisibility() != View.VISIBLE;
        setAttachmentPanelVisible(shouldShowPanel);
    }

    private boolean isPlusActionVisible() {
        return !voiceInputMode && !hasTextInputContent() && !keyboardVisible;
    }

    private boolean hasTextInputContent() {
        return consultInput != null
            && consultInput.getText() != null
            && consultInput.getText().toString().trim().length() > 0;
    }

    private void setAttachmentPanelVisible(boolean visible) {
        if (attachmentPanel == null) {
            return;
        }
        int targetVisibility = visible ? View.VISIBLE : View.GONE;
        if (attachmentPanel.getVisibility() == targetVisibility) {
            return;
        }
        attachmentPanel.setVisibility(targetVisibility);
        updateContentInsetsForInputBar();
    }

    private void applyTextInputWrapping() {
        if (consultInput == null) {
            return;
        }
        consultInput.setSingleLine(false);
        consultInput.setMinLines(1);
        consultInput.setMaxLines(TEXT_INPUT_MAX_LINES);
        consultInput.setHorizontallyScrolling(false);
    }

    private void keepTextInputCursorOnBottomLine() {
        final EditText input = consultInput;
        if (input == null || voiceInputMode) {
            return;
        }
        input.post(() -> scrollTextInputToCursorLine(input));
    }

    private void scrollTextInputToCursorLine(EditText input) {
        if (input != consultInput || voiceInputMode) {
            return;
        }
        Layout layout = input.getLayout();
        if (layout == null) {
            return;
        }
        if (layout.getLineCount() <= TEXT_INPUT_MAX_LINES) {
            input.scrollTo(input.getScrollX(), 0);
            return;
        }

        int selection = input.getSelectionEnd();
        if (selection < 0) {
            selection = input.length();
        }
        selection = Math.min(selection, input.length());
        int cursorLine = layout.getLineForOffset(selection);
        int visibleTextHeight = input.getHeight()
            - input.getCompoundPaddingTop()
            - input.getCompoundPaddingBottom();
        if (visibleTextHeight <= 0) {
            return;
        }

        int targetScrollY = Math.max(0, layout.getLineBottom(cursorLine) - visibleTextHeight);
        int maxScrollY = Math.max(0, layout.getHeight() - visibleTextHeight);
        input.scrollTo(input.getScrollX(), Math.min(targetScrollY, maxScrollY));
    }

    private void updateContentInsetsForInputBar() {
        if (bottomInputContainer == null) {
            return;
        }
        boolean attachmentPanelVisible = attachmentPanel != null
            && attachmentPanel.getVisibility() == View.VISIBLE;
        int inputBottomFillHeight = keyboardVisible
            ? inputBarKeyboardBottomMargin
            : getResources().getDimensionPixelSize(R.dimen.baidu_web_input_bottom_margin);
        setInputBottomFillVisible(!attachmentPanelVisible, inputBottomFillHeight);
        int inputBarBottomMargin = keyboardVisible
            ? currentKeyboardHeight
            : inputBarBaseBottomMargin;
        setBottomMargin(bottomInputContainer, inputBarBottomMargin);
    }

    private void setInputBottomFillVisible(boolean visible, int height) {
        if (inputBottomFill == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = inputBottomFill.getLayoutParams();
        if (layoutParams != null && layoutParams.height != height) {
            layoutParams.height = height;
            inputBottomFill.setLayoutParams(layoutParams);
        }
        int targetVisibility = visible ? View.VISIBLE : View.GONE;
        if (inputBottomFill.getVisibility() != targetVisibility) {
            inputBottomFill.setVisibility(targetVisibility);
        }
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
        if (bottomInputContainer != null && inputBarLayoutChangeListener != null) {
            bottomInputContainer.removeOnLayoutChangeListener(inputBarLayoutChangeListener);
            inputBarLayoutChangeListener = null;
        }
        if (consultInput != null && inputTextWatcher != null) {
            consultInput.removeTextChangedListener(inputTextWatcher);
            inputTextWatcher = null;
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
        buttonAddContent = null;
        buttonVoiceInput = null;
        attachmentPanel = null;
        inputBottomFill = null;
        bottomInputBar = null;
        bottomInputContainer = null;
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
