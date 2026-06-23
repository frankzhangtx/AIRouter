package com.example.cctest.widget;

final class BottomInputPanelActionCallback implements BottomInputActionController.Callback {

    private final BottomInputPanelView panelView;

    BottomInputPanelActionCallback(BottomInputPanelView panelView) {
        this.panelView = panelView;
    }

    @Override
    public boolean isVoiceInputMode() {
        return panelView.isVoiceInputModeEnabled();
    }

    @Override
    public boolean hasTextInputContent() {
        return panelView.hasTextInputContent();
    }

    @Override
    public boolean isKeyboardVisible() {
        return panelView.isKeyboardVisible();
    }

    @Override
    public boolean isManualModeEnabled() {
        return panelView.isManualModeEnabled();
    }

    @Override
    public boolean isTextInputFocused() {
        return panelView.isTextInputFocused();
    }

    @Override
    public boolean isAttachmentPanelVisible() {
        return panelView.isAttachmentPanelVisible();
    }

    @Override
    public String getInputText() {
        return panelView.getInputText();
    }

    @Override
    public void setAttachmentPanelVisible(boolean visible) {
        panelView.setAttachmentPanelVisible(visible);
    }

    @Override
    public void refreshKeyboardVisibilityForAction() {
        panelView.refreshKeyboardVisibilityForAction();
    }

    @Override
    public void dismissKeyboardAndClearFocus() {
        panelView.dismissKeyboardAndClearFocus();
    }

    @Override
    public void onVoiceInputToggleRequested() {
        panelView.toggleVoiceInputMode();
    }

    @Override
    public void onSendText(String text) {
        panelView.dispatchSendText(text);
    }

    @Override
    public void onToastRequested(int messageResource, Object... formatArgs) {
        panelView.requestToast(messageResource, formatArgs);
    }
}
