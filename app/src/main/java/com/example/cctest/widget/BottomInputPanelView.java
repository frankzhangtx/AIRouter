package com.example.cctest.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import com.example.cctest.R;
import com.example.cctest.voice.VoiceRecordCallback;

public class BottomInputPanelView extends LinearLayout {

    private View inputBottomFill;
    private View inputTopExtension;
    private View aiAvatarButton;
    private View bottomInputBar;
    private ImageButton buttonVoiceInput;
    private ImageButton buttonAddContent;
    private EditText consultInput;
    private BottomSuggestionListController suggestionListController;
    private BottomAttachmentPanelController attachmentPanelController;
    private BottomTextInputController textInputController;
    private BottomKeyboardController keyboardController;
    private BottomVoiceRecordController voiceRecordController;
    private BottomInputActionController inputActionController;
    private BottomManualModeController manualModeController;
    private BottomOutsideKeyboardDismissController outsideKeyboardDismissController;
    private BottomInputBarLayoutController inputBarLayoutController;
    private VoiceRecordCallback voiceRecordCallback;
    private BottomInputActionListener actionListener;
    private boolean voiceInputMode;

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

    public void setActionListener(BottomInputActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void setVoiceRecordCallback(VoiceRecordCallback voiceRecordCallback) {
        this.voiceRecordCallback = voiceRecordCallback;
    }

    public void setModeChangeListener(BottomInputModeChangeListener modeChangeListener) {
        if (manualModeController != null) {
            manualModeController.setModeChangeListener(modeChangeListener);
        }
    }

    public void setManualModeEnabled(boolean enabled) {
        if (manualModeController != null) {
            manualModeController.setManualModeEnabled(enabled);
        }
    }

    public boolean isManualModeEnabled() {
        return manualModeController != null && manualModeController.isManualModeEnabled();
    }

    public void setManualAgentOnline(boolean online) {
        if (manualModeController != null) {
            manualModeController.setManualAgentOnline(online);
        }
    }

    public boolean isManualAgentOnline() {
        return manualModeController != null && manualModeController.isManualAgentOnline();
    }

    public void setManualAgentType(ManualAgentType manualAgentType) {
        if (manualModeController != null) {
            manualModeController.setManualAgentType(manualAgentType);
        }
    }

    public ManualAgentType getManualAgentType() {
        return manualModeController == null
            ? ManualAgentType.ONLINE_SERVICE
            : manualModeController.getManualAgentType();
    }

    public void setHorizontalSuggestionListVisible(boolean visible) {
        if (suggestionListController != null) {
            suggestionListController.setVisible(visible);
        }
        updateContentInsetsForInputBar();
    }

    public boolean isHorizontalSuggestionListVisible() {
        return suggestionListController != null && suggestionListController.isVisible();
    }

    public int getVisualHeightForOverlay() {
        if (getVisibility() != View.VISIBLE) {
            return 0;
        }
        return getHeight() + getTopVisualOverflowHeight();
    }

    public void prepareKeyboardDismissIfTouchOutsideTextInput(MotionEvent event) {
        if (outsideKeyboardDismissController != null) {
            outsideKeyboardDismissController.prepare(event);
        }
    }

    public void finishKeyboardDismissIfTouchOutsideTextInput(MotionEvent event) {
        if (outsideKeyboardDismissController != null) {
            outsideKeyboardDismissController.finish(event);
        }
    }

    private boolean shouldDismissKeyboardForTouchOutsideTextInput(MotionEvent event) {
        if (!isKeyboardVisible() || voiceInputMode || textInputController == null) {
            return false;
        }
        return !textInputController.containsRawPoint(event.getRawX(), event.getRawY());
    }

    public void appendAdditionalHorizontalSuggestions() {
        if (suggestionListController != null) {
            suggestionListController.appendAdditionalSuggestions();
        }
    }

    public void ensureDefaultHorizontalSuggestions() {
        if (suggestionListController != null) {
            suggestionListController.ensureDefaultSuggestions();
        }
    }

    public void clearHorizontalSuggestions() {
        if (suggestionListController != null) {
            suggestionListController.clearSuggestions();
        }
    }

    public void replaceHorizontalSuggestions() {
        if (suggestionListController != null) {
            suggestionListController.replaceSuggestions();
        }
    }

    public String getInputText() {
        return textInputController == null ? "" : textInputController.getText();
    }

    public void setInputText(CharSequence text) {
        if (textInputController != null) {
            textInputController.setText(text);
        }
        updateTextInputActionState();
        updateContentInsetsForInputBar();
    }

    public void clearInputText() {
        if (textInputController != null) {
            textInputController.clearText();
        }
    }

    public void closeAttachmentPanel() {
        setAttachmentPanelVisible(false);
    }

    public boolean isAttachmentPanelVisible() {
        return attachmentPanelController != null && attachmentPanelController.isVisible();
    }

    public void setAttachmentPanelVisible(boolean visible) {
        if (attachmentPanelController != null) {
            attachmentPanelController.setVisible(visible);
        }
    }

    public void dismissKeyboardForPanelHide() {
        if (outsideKeyboardDismissController != null) {
            outsideKeyboardDismissController.reset();
        }
        dismissKeyboardAndClearFocus();
        if (keyboardController != null) {
            keyboardController.forceKeyboardHidden();
        }
        updateTextInputActionState();
        updateContentInsetsForInputBar();
    }

    public void setVoiceInputModeEnabled(boolean enabled) {
        setVoiceInputMode(enabled);
    }

    public boolean isVoiceInputModeEnabled() {
        return voiceInputMode;
    }

    public void setUseNewVoiceRecordPanel(boolean useNewVoiceRecordPanel) {
        if (voiceRecordController != null) {
            voiceRecordController.setUseNewVoiceRecordPanel(useNewVoiceRecordPanel);
        }
    }

    public void release() {
        detachKeyboardAvoidance();
        if (textInputController != null) {
            textInputController.release();
        }
        if (voiceRecordController != null) {
            voiceRecordController.dismiss();
        }
        actionListener = null;
        if (manualModeController != null) {
            manualModeController.setModeChangeListener(null);
        }
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
        if (voiceRecordController != null) {
            voiceRecordController.dismiss();
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
        inputTopExtension = findViewById(R.id.input_top_extension);
        aiAvatarButton = findViewById(R.id.button_ai_avatar);
        bottomInputBar = findViewById(R.id.bottom_input_bar);
        buttonVoiceInput = findViewById(R.id.button_voice_input);
        buttonAddContent = findViewById(R.id.button_add_content);
        consultInput = findViewById(R.id.edit_text_consult_content);

        configureManualModeController();
        configureKeyboardController();
        configureOutsideKeyboardDismissController();
        configureInputBarLayoutController();
        configureHorizontalSuggestionList(context);
        configureAttachmentPanel();
        configureTextInputController();
        configureVoiceRecordPanel(context);
        configureVoiceInputToggle();
        updateAttachmentPanelOptions();
    }

    private void configureManualModeController() {
        manualModeController = new BottomManualModeController(
            aiAvatarButton,
            new BottomManualModeController.Callback() {
                @Override
                public void closeAttachmentPanel() {
                    BottomInputPanelView.this.closeAttachmentPanel();
                }

                @Override
                public void onManualModeUiStateChanged() {
                    updateTextInputActionState();
                    updateInputBarLayoutState();
                    updateContentInsetsForInputBar();
                }

                @Override
                public void onManualAgentTypeChanged() {
                    updateAttachmentPanelOptions();
                }
            }
        );
    }

    private void configureInputBarLayoutController() {
        inputBarLayoutController = new BottomInputBarLayoutController(this);
    }

    private void configureHorizontalSuggestionList(Context context) {
        suggestionListController = new BottomSuggestionListController(
            context,
            this,
            this::requestToast
        );
    }

    private void configureKeyboardController() {
        keyboardController = new BottomKeyboardController(
            this,
            inputBottomFill,
            new BottomKeyboardController.Callback() {
                @Override
                public void onKeyboardStateChanged() {
                    updateTextInputActionState();
                }

                @Override
                public boolean isAttachmentPanelVisible() {
                    return BottomInputPanelView.this.isAttachmentPanelVisible();
                }
            }
        );
    }

    private void configureOutsideKeyboardDismissController() {
        outsideKeyboardDismissController = new BottomOutsideKeyboardDismissController(
            new BottomOutsideKeyboardDismissController.Callback() {
                @Override
                public boolean shouldDismissKeyboardForTouch(MotionEvent event) {
                    return shouldDismissKeyboardForTouchOutsideTextInput(event);
                }

                @Override
                public void dismissKeyboardAndClearFocus() {
                    BottomInputPanelView.this.dismissKeyboardAndClearFocus();
                }
            }
        );
    }

    private void configureTextInputController() {
        textInputController = new BottomTextInputController(
            consultInput,
            new BottomTextInputController.Callback() {
                @Override
                public void onTextInputChanged() {
                    updateTextInputActionState();
                    updateContentInsetsForInputBar();
                }

                @Override
                public boolean isVoiceInputMode() {
                    return voiceInputMode;
                }
            }
        );
    }

    private void configureVoiceRecordPanel(Context context) {
        voiceRecordController = new BottomVoiceRecordController(
            context,
            consultInput,
            bottomInputBar,
            new BottomVoiceRecordController.Callback() {
                @Override
                public boolean shouldEnableTextHoldTrigger() {
                    return !isKeyboardVisible() && !hasTextInputContent();
                }

                @Override
                public void focusTextInput() {
                    BottomInputPanelView.this.focusTextInput();
                }

                @Override
                public void onStart() {
                    dismissKeyboardAndClearFocus();
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
            }
        );
    }

    private void configureVoiceInputToggle() {
        if (
            buttonVoiceInput == null
                || buttonAddContent == null
                || consultInput == null
                || voiceRecordController == null
                || !voiceRecordController.isAvailable()
        ) {
            return;
        }

        inputActionController = new BottomInputActionController(
            buttonVoiceInput,
            buttonAddContent,
            new BottomInputPanelActionCallback(this)
        );
        setVoiceInputMode(false);
        updateTextInputActionState();
    }

    private void configureAttachmentPanel() {
        attachmentPanelController = new BottomAttachmentPanelController(
            this,
            new BottomAttachmentPanelController.Callback() {
                @Override
                public void onVisibilityChanged() {
                    bindActiveHoldTrigger();
                    updateContentInsetsForInputBar();
                }

                @Override
                public void onImageRequested() {
                    if (actionListener != null) {
                        actionListener.onImageRequested();
                    }
                }

                @Override
                public void onProductRequested() {
                    if (actionListener != null) {
                        actionListener.onProductRequested();
                    }
                }
            }
        );
    }

    private void updateAttachmentPanelOptions() {
        if (attachmentPanelController != null) {
            attachmentPanelController.setManualAgentType(getManualAgentType());
        }
        updateContentInsetsForInputBar();
    }

    private void setVoiceInputMode(boolean enabled) {
        if (
            buttonVoiceInput == null
                || buttonAddContent == null
                || textInputController == null
                || voiceRecordController == null
                || !voiceRecordController.isAvailable()
        ) {
            return;
        }

        if (enabled && !voiceInputMode) {
            textInputController.captureDraft();
        }
        voiceInputMode = enabled;
        updateInputBarLayoutState();

        if (enabled) {
            setAttachmentPanelVisible(false);
            textInputController.dismissKeyboardAndClearFocus();
            bindActiveHoldTrigger();
            updateVoiceInputModeIcon();
            textInputController.showVoicePrompt();
            updateTextInputActionState();
            updateContentInsetsForInputBar();
        } else {
            bindActiveHoldTrigger();
            updateVoiceInputModeIcon();
            textInputController.restoreTextInput();
            updateTextInputActionState();
            updateContentInsetsForInputBar();
        }
    }

    private void updateTextInputActionState() {
        if (inputActionController != null) {
            inputActionController.updateState();
        }
    }

    private void updateInputBarLayoutState() {
        if (inputBarLayoutController != null) {
            inputBarLayoutController.setManualOfflineStyleEnabled(!isManualAgentOnline());
        }
    }

    private void updateVoiceInputModeIcon() {
        if (inputActionController != null) {
            inputActionController.setVoiceInputModeIcon(voiceInputMode);
        }
    }

    private void bindActiveHoldTrigger() {
        if (voiceRecordController != null) {
            voiceRecordController.bindActiveHoldTrigger(voiceInputMode);
        }
    }

    boolean hasTextInputContent() {
        return textInputController != null && textInputController.hasContent();
    }

    void requestToast(int messageResource, Object... formatArgs) {
        if (actionListener != null) {
            actionListener.onToastRequested(messageResource, formatArgs);
        }
    }

    private void updateContentInsetsForInputBar() {
        if (keyboardController != null) {
            keyboardController.updateContentInsets();
        }
    }

    void refreshKeyboardVisibilityForAction() {
        if (keyboardController != null) {
            keyboardController.refreshKeyboardVisibilityForAction();
        }
    }

    private void configureKeyboardAvoidance() {
        if (keyboardController != null) {
            keyboardController.attach();
        }
    }

    private void detachKeyboardAvoidance() {
        if (keyboardController != null) {
            keyboardController.detach();
        }
    }

    void dismissKeyboardAndClearFocus() {
        if (textInputController != null) {
            textInputController.dismissKeyboardAndClearFocus();
        }
    }

    private void focusTextInput() {
        if (textInputController != null) {
            textInputController.focusTextInput();
        }
    }

    boolean isKeyboardVisible() {
        return keyboardController != null && keyboardController.isKeyboardVisible();
    }

    boolean isTextInputFocused() {
        return consultInput != null && consultInput.hasFocus();
    }

    void toggleVoiceInputMode() {
        setVoiceInputMode(!voiceInputMode);
    }

    void dispatchSendText(String text) {
        if (actionListener != null) {
            actionListener.onSendText(text);
        }
    }

    private int getTopVisualOverflowHeight() {
        if (inputTopExtension == null || inputTopExtension.getVisibility() != View.VISIBLE) {
            return 0;
        }
        int visualTop = Math.round(
            inputTopExtension.getTop() + inputTopExtension.getTranslationY()
        );
        return Math.max(0, -visualTop);
    }

}
