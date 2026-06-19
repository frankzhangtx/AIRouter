package com.example.cctest.widget;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import com.example.cctest.R;
import com.example.cctest.voice.PicVoiceRecordPanel;
import com.example.cctest.voice.VoiceRecordCallback;

public class BottomInputPanelView extends LinearLayout {

    private static final int TEXT_INPUT_MAX_LINES = 4;

    private View inputBottomFill;
    private View attachmentPanel;
    private View optionAttachImage;
    private View optionSendProduct;
    private View aiAvatarButton;
    private View bottomInputBar;
    private ImageButton buttonVoiceInput;
    private ImageButton buttonAddContent;
    private EditText consultInput;
    private PicVoiceRecordPanel voiceRecordPanel;
    private VoiceRecordCallback voiceRecordCallback;
    private ActionListener actionListener;
    private ModeChangeListener modeChangeListener;
    private View contentRoot;
    private ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener;
    private OnLayoutChangeListener inputBarLayoutChangeListener;
    private TextWatcher inputTextWatcher;
    private boolean manualModeEnabled = true;
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

    public BottomInputPanelView(Context context) {
        super(context);
        initialize(context);
    }

    public BottomInputPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    public BottomInputPanelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context);
    }

    public void setActionListener(ActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void setVoiceRecordCallback(VoiceRecordCallback voiceRecordCallback) {
        this.voiceRecordCallback = voiceRecordCallback;
    }

    public void setModeChangeListener(ModeChangeListener modeChangeListener) {
        this.modeChangeListener = modeChangeListener;
    }

    public void setManualModeEnabled(boolean enabled) {
        setManualModeEnabled(enabled, true);
    }

    public boolean isManualModeEnabled() {
        return manualModeEnabled;
    }

    public String getInputText() {
        if (consultInput == null || consultInput.getText() == null) {
            return "";
        }
        return consultInput.getText().toString();
    }

    public void setInputText(CharSequence text) {
        if (consultInput == null) {
            return;
        }
        consultInput.setText(text == null ? "" : text);
        consultInput.setSelection(consultInput.getText().length());
        keepTextInputCursorOnBottomLine();
        updateTextInputActionState();
        updateContentInsetsForInputBar();
    }

    public void clearInputText() {
        setInputText("");
    }

    public void closeAttachmentPanel() {
        setAttachmentPanelVisible(false);
    }

    public void setVoiceInputModeEnabled(boolean enabled) {
        setVoiceInputMode(enabled);
    }

    public boolean isVoiceInputModeEnabled() {
        return voiceInputMode;
    }

    public void release() {
        detachKeyboardAvoidance();
        if (consultInput != null && inputTextWatcher != null) {
            consultInput.removeTextChangedListener(inputTextWatcher);
            inputTextWatcher = null;
        }
        if (voiceRecordPanel != null) {
            voiceRecordPanel.dismiss();
        }
        actionListener = null;
        modeChangeListener = null;
        voiceRecordCallback = null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        configureKeyboardAvoidance();
    }

    @Override
    protected void onDetachedFromWindow() {
        detachKeyboardAvoidance();
        if (voiceRecordPanel != null) {
            voiceRecordPanel.dismiss();
        }
        super.onDetachedFromWindow();
    }

    private void initialize(Context context) {
        setOrientation(VERTICAL);
        setBackgroundResource(R.color.baidu_web_input_container_background);
        setClipChildren(false);
        setClipToPadding(false);
        LayoutInflater.from(context).inflate(R.layout.view_bottom_input_panel, this, true);

        inputBottomFill = findViewById(R.id.input_bottom_fill);
        attachmentPanel = findViewById(R.id.input_attachment_panel);
        optionAttachImage = findViewById(R.id.option_attach_image);
        optionSendProduct = findViewById(R.id.option_send_product);
        aiAvatarButton = findViewById(R.id.button_ai_avatar);
        bottomInputBar = findViewById(R.id.bottom_input_bar);
        buttonVoiceInput = findViewById(R.id.button_voice_input);
        buttonAddContent = findViewById(R.id.button_add_content);
        consultInput = findViewById(R.id.edit_text_consult_content);

        configureManualModeToggle();
        configureTextInputWrapping();
        configureVoiceRecordPanel(context);
        configureVoiceInputToggle();
        configureAttachmentOptions();
        updateManualModeUi();
    }

    private void configureManualModeToggle() {
        if (aiAvatarButton == null) {
            return;
        }
        aiAvatarButton.setOnClickListener(view -> setManualModeEnabled(true));
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
        if (keyboardLayoutListener != null) {
            return;
        }

        Activity activity = findActivity(getContext());
        if (activity != null) {
            setWindowAdjustResize(activity);
            contentRoot = activity.findViewById(android.R.id.content);
        }
        if (contentRoot == null) {
            contentRoot = getRootView();
        }
        if (contentRoot == null) {
            return;
        }

        inputBarBaseBottomMargin = getBottomMargin(this);
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
        inputBarLayoutChangeListener = (view, left, top, right, bottom, oldLeft, oldTop,
            oldRight, oldBottom) -> updateContentInsetsForInputBar();
        addOnLayoutChangeListener(inputBarLayoutChangeListener);
        post(this::updateContentInsetsForInputBar);
    }

    private void configureVoiceRecordPanel(Context context) {
        if (consultInput == null) {
            return;
        }
        consultInput.setLongClickable(false);
        voiceRecordPanel = createVoiceRecordPanel(context);
        voiceRecordPanel.setRecordPanelAnchorView(bottomInputBar);
        voiceRecordPanel.setCallback(new VoiceRecordCallback() {
            @Override
            public void onStart() {
                hideKeyboard();
                consultInput.clearFocus();
                if (voiceRecordCallback != null) {
                    voiceRecordCallback.onStart();
                }
            }

            @Override
            public void onCancel() {
                if (voiceRecordCallback != null) {
                    voiceRecordCallback.onCancel();
                }
            }

            @Override
            public void onFinish() {
                if (voiceRecordCallback != null) {
                    voiceRecordCallback.onFinish();
                }
            }
        });
        bindTextInputHoldTrigger(consultInput);
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

    private void configureAttachmentOptions() {
        if (optionAttachImage != null) {
            optionAttachImage.setOnClickListener(view -> {
                setAttachmentPanelVisible(false);
                if (actionListener != null) {
                    actionListener.onImageRequested();
                }
            });
        }
        if (optionSendProduct != null) {
            optionSendProduct.setOnClickListener(view -> {
                setAttachmentPanelVisible(false);
                if (actionListener != null) {
                    actionListener.onProductRequested();
                }
            });
        }
    }

    private void setManualModeEnabled(boolean enabled, boolean notifyListener) {
        if (manualModeEnabled == enabled) {
            updateManualModeUi();
            updateTextInputActionState();
            return;
        }
        manualModeEnabled = enabled;
        if (manualModeEnabled) {
            closeAttachmentPanel();
        }
        updateManualModeUi();
        updateTextInputActionState();
        if (notifyListener && modeChangeListener != null) {
            modeChangeListener.onManualModeChanged(manualModeEnabled);
        }
    }

    private void updateManualModeUi() {
        if (aiAvatarButton != null) {
            aiAvatarButton.setVisibility(manualModeEnabled ? View.GONE : View.VISIBLE);
        }
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
            bindActiveHoldTrigger();
            buttonVoiceInput.setImageResource(R.drawable.ic_baidu_web_keyboard);
            buttonVoiceInput.setContentDescription(
                getResources().getString(R.string.baidu_web_keyboard_content_description)
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
            bindActiveHoldTrigger();
            buttonVoiceInput.setImageResource(R.drawable.ic_baidu_web_voice);
            buttonVoiceInput.setContentDescription(
                getResources().getString(R.string.baidu_web_voice_content_description)
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
        boolean showSendAction = isSendActionVisible();

        buttonVoiceInput.setVisibility(hasInputText ? View.GONE : View.VISIBLE);
        if (showSendAction) {
            setAttachmentPanelVisible(false);
            buttonAddContent.setImageResource(R.drawable.ic_baidu_web_send);
            buttonAddContent.setContentDescription(
                getResources().getString(R.string.baidu_web_send_content_description)
            );
            buttonAddContent.setPadding(0, 0, 0, 0);
        } else {
            int iconResource = manualModeEnabled
                ? R.drawable.ic_baidu_web_plus
                : R.drawable.ic_baidu_web_ai_grid;
            int contentDescriptionResource = manualModeEnabled
                ? R.string.baidu_web_plus_content_description
                : R.string.baidu_web_ai_grid_content_description;
            buttonAddContent.setImageResource(iconResource);
            buttonAddContent.setContentDescription(
                getResources().getString(contentDescriptionResource)
            );
            int iconPadding = getResources().getDimensionPixelSize(
                R.dimen.baidu_web_input_icon_padding
            );
            buttonAddContent.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
        }
    }

    private void bindTextInputHoldTrigger(View trigger) {
        if (voiceRecordPanel == null || trigger == null) {
            return;
        }
        voiceRecordPanel.bindToHoldTriggerPreservingClickWhen(
            trigger,
            () -> !keyboardVisible && !hasTextInputContent()
        );
    }

    private void bindActiveHoldTrigger() {
        if (voiceRecordPanel == null || consultInput == null) {
            return;
        }
        if (voiceInputMode) {
            voiceRecordPanel.bindToImmediateHoldTrigger(consultInput);
            if (bottomInputBar != null) {
                voiceRecordPanel.bindToImmediateHoldTrigger(bottomInputBar);
            }
        } else {
            bindTextInputHoldTrigger(consultInput);
            if (bottomInputBar != null) {
                bindTextInputHoldTrigger(bottomInputBar);
                bottomInputBar.setOnClickListener(view -> focusTextInput());
            }
        }
    }

    private void handleTrailingActionClick() {
        if (isSendActionVisible()) {
            setAttachmentPanelVisible(false);
            if (actionListener != null) {
                actionListener.onSendText(getInputText());
            }
            return;
        }

        if (isAiGridActionVisible()) {
            setAttachmentPanelVisible(false);
            Toast.makeText(
                getContext(),
                R.string.baidu_web_more_products,
                Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (!isPlusActionVisible()) {
            setAttachmentPanelVisible(false);
            return;
        }

        boolean shouldShowPanel = attachmentPanel == null
            || attachmentPanel.getVisibility() != View.VISIBLE;
        setAttachmentPanelVisible(shouldShowPanel);
    }

    private boolean isPlusActionVisible() {
        return manualModeEnabled
            && !keyboardVisible
            && (voiceInputMode || !hasTextInputContent());
    }

    private boolean isAiGridActionVisible() {
        return !manualModeEnabled && !isSendActionVisible();
    }

    private boolean isSendActionVisible() {
        return !voiceInputMode && (hasTextInputContent() || keyboardVisible);
    }

    private boolean isAttachmentPanelVisible() {
        return attachmentPanel != null && attachmentPanel.getVisibility() == View.VISIBLE;
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
        bindActiveHoldTrigger();
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
        boolean attachmentPanelVisible = attachmentPanel != null
            && attachmentPanel.getVisibility() == View.VISIBLE;
        int inputBottomFillHeight = keyboardVisible
            ? inputBarKeyboardBottomMargin
            : getResources().getDimensionPixelSize(R.dimen.baidu_web_input_bottom_margin);
        setInputBottomFillVisible(!attachmentPanelVisible, inputBottomFillHeight);
        int inputBarBottomMargin = keyboardVisible
            ? currentKeyboardHeight
            : inputBarBaseBottomMargin;
        setBottomMargin(this, inputBarBottomMargin);
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
        View focusedView = null;
        Activity activity = findActivity(getContext());
        if (activity != null) {
            focusedView = activity.getCurrentFocus();
        }
        if (focusedView == null) {
            focusedView = consultInput;
        }
        if (focusedView == null) {
            return;
        }
        InputMethodManager inputMethodManager =
            (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
        }
    }

    private void focusTextInput() {
        if (consultInput == null || voiceInputMode) {
            return;
        }
        consultInput.requestFocus();
        consultInput.setSelection(consultInput.getText().length());
        consultInput.post(() -> {
            InputMethodManager inputMethodManager =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.showSoftInput(consultInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void detachKeyboardAvoidance() {
        if (contentRoot != null && keyboardLayoutListener != null) {
            contentRoot.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardLayoutListener);
            keyboardLayoutListener = null;
        }
        if (inputBarLayoutChangeListener != null) {
            removeOnLayoutChangeListener(inputBarLayoutChangeListener);
            inputBarLayoutChangeListener = null;
        }
        contentRoot = null;
    }

    private PicVoiceRecordPanel createVoiceRecordPanel(Context context) {
        ComponentActivity activity = findComponentActivity(context);
        if (activity != null) {
            return new PicVoiceRecordPanel(activity);
        }
        return new PicVoiceRecordPanel(context);
    }

    private void setWindowAdjustResize(Activity activity) {
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        int softInputMode = window.getAttributes().softInputMode;
        int stateMode = softInputMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE;
        window.setSoftInputMode(stateMode | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private Activity findActivity(Context context) {
        Context currentContext = context;
        while (currentContext instanceof ContextWrapper) {
            if (currentContext instanceof Activity) {
                return (Activity) currentContext;
            }
            currentContext = ((ContextWrapper) currentContext).getBaseContext();
        }
        return null;
    }

    private ComponentActivity findComponentActivity(Context context) {
        Context currentContext = context;
        while (currentContext instanceof ContextWrapper) {
            if (currentContext instanceof ComponentActivity) {
                return (ComponentActivity) currentContext;
            }
            currentContext = ((ContextWrapper) currentContext).getBaseContext();
        }
        return null;
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

    public interface ActionListener {
        default void onSendText(String text) {
            // Optional override.
        }

        default void onImageRequested() {
            // Optional override.
        }

        default void onProductRequested() {
            // Optional override.
        }
    }

    public interface ModeChangeListener {
        void onManualModeChanged(boolean manualModeEnabled);
    }
}
