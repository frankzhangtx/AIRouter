package com.example.cctest.widget;

import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import com.example.cctest.R;

final class BottomInputActionController {

    private final ImageButton buttonVoiceInput;
    private final ImageButton buttonAddContent;
    private final Callback callback;
    private boolean trailingActionStartedWithTextInputInteraction;

    BottomInputActionController(
        ImageButton buttonVoiceInput,
        ImageButton buttonAddContent,
        Callback callback
    ) {
        this.buttonVoiceInput = buttonVoiceInput;
        this.buttonAddContent = buttonAddContent;
        this.callback = callback;
        configure();
    }

    void setVoiceInputModeIcon(boolean enabled) {
        if (buttonVoiceInput == null) {
            return;
        }
        if (enabled) {
            buttonVoiceInput.setImageResource(R.drawable.ic_baidu_web_keyboard);
            buttonVoiceInput.setContentDescription(
                buttonVoiceInput.getResources().getString(
                    R.string.baidu_web_keyboard_content_description
                )
            );
        } else {
            buttonVoiceInput.setImageResource(R.drawable.ic_baidu_web_voice);
            buttonVoiceInput.setContentDescription(
                buttonVoiceInput.getResources().getString(
                    R.string.baidu_web_voice_content_description
                )
            );
        }
    }

    void updateState() {
        if (buttonVoiceInput == null || buttonAddContent == null || callback == null) {
            return;
        }

        boolean hasInputText = !callback.isVoiceInputMode() && callback.hasTextInputContent();
        boolean showSendAction = isSendActionVisible();

        buttonVoiceInput.setVisibility(hasInputText ? View.GONE : View.VISIBLE);
        if (showSendAction) {
            callback.setAttachmentPanelVisible(false);
            buttonAddContent.setImageResource(R.drawable.ic_baidu_web_send);
            int contentDescriptionResource = callback.isKeyboardVisible() && !hasInputText
                ? R.string.baidu_web_hide_keyboard_content_description
                : R.string.baidu_web_send_content_description;
            buttonAddContent.setContentDescription(
                buttonAddContent.getResources().getString(contentDescriptionResource)
            );
            buttonAddContent.setPadding(0, 0, 0, 0);
        } else {
            int iconResource = callback.isManualModeEnabled()
                ? R.drawable.ic_baidu_web_plus
                : R.drawable.ic_baidu_web_ai_grid;
            int contentDescriptionResource = callback.isManualModeEnabled()
                ? R.string.baidu_web_plus_content_description
                : R.string.baidu_web_ai_grid_content_description;
            buttonAddContent.setImageResource(iconResource);
            buttonAddContent.setContentDescription(
                buttonAddContent.getResources().getString(contentDescriptionResource)
            );
            int iconPadding = buttonAddContent.getResources().getDimensionPixelSize(
                R.dimen.baidu_web_input_icon_padding
            );
            buttonAddContent.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
        }
    }

    private void configure() {
        if (buttonVoiceInput == null || buttonAddContent == null || callback == null) {
            return;
        }
        buttonVoiceInput.setOnClickListener(view -> callback.onVoiceInputToggleRequested());
        buttonAddContent.setOnTouchListener((view, event) -> {
            if (event == null) {
                return false;
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                callback.refreshKeyboardVisibilityForAction();
                trailingActionStartedWithTextInputInteraction = isTextInputInteractionActive();
            } else if (action == MotionEvent.ACTION_CANCEL) {
                trailingActionStartedWithTextInputInteraction = false;
            }
            return false;
        });
        buttonAddContent.setOnClickListener(view -> handleTrailingActionClick());
    }

    private void handleTrailingActionClick() {
        try {
            callback.refreshKeyboardVisibilityForAction();
            if (!callback.isVoiceInputMode() && callback.hasTextInputContent()) {
                String inputText = callback.getInputText();
                callback.setAttachmentPanelVisible(false);
                callback.onToastRequested(R.string.baidu_web_send_toast_format, inputText);
                if (callback.isKeyboardVisible()) {
                    callback.dismissKeyboardAndClearFocus();
                }
                callback.onSendText(inputText);
                return;
            }

            if (isKeyboardDismissActionVisible()) {
                callback.setAttachmentPanelVisible(false);
                callback.dismissKeyboardAndClearFocus();
                return;
            }

            if (isSendActionVisible()) {
                callback.setAttachmentPanelVisible(false);
                callback.onSendText(callback.getInputText());
                return;
            }

            if (isAiGridActionVisible()) {
                callback.setAttachmentPanelVisible(false);
                callback.onToastRequested(R.string.baidu_web_more_products);
                return;
            }

            if (!isPlusActionVisible()) {
                callback.setAttachmentPanelVisible(false);
                return;
            }

            callback.setAttachmentPanelVisible(!callback.isAttachmentPanelVisible());
        } finally {
            trailingActionStartedWithTextInputInteraction = false;
        }
    }

    private boolean isPlusActionVisible() {
        return callback.isManualModeEnabled()
            && !callback.isKeyboardVisible()
            && (callback.isVoiceInputMode() || !callback.hasTextInputContent());
    }

    private boolean isAiGridActionVisible() {
        return !callback.isManualModeEnabled()
            && !isSendActionVisible()
            && !isTextInputInteractionActive();
    }

    private boolean isKeyboardDismissActionVisible() {
        return !callback.isVoiceInputMode()
            && isTextInputInteractionActive()
            && !callback.hasTextInputContent();
    }

    private boolean isSendActionVisible() {
        return !callback.isVoiceInputMode()
            && (callback.hasTextInputContent() || callback.isKeyboardVisible());
    }

    private boolean isTextInputInteractionActive() {
        return callback.isKeyboardVisible()
            || trailingActionStartedWithTextInputInteraction
            || callback.isTextInputFocused();
    }

    interface Callback {
        boolean isVoiceInputMode();

        boolean hasTextInputContent();

        boolean isKeyboardVisible();

        boolean isManualModeEnabled();

        boolean isTextInputFocused();

        boolean isAttachmentPanelVisible();

        String getInputText();

        void setAttachmentPanelVisible(boolean visible);

        void refreshKeyboardVisibilityForAction();

        void dismissKeyboardAndClearFocus();

        void onVoiceInputToggleRequested();

        void onSendText(String text);

        void onToastRequested(int messageResource, Object... formatArgs);
    }
}
