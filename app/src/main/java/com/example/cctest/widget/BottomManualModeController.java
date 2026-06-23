package com.example.cctest.widget;

import android.view.View;

final class BottomManualModeController {

    private final View aiAvatarButton;
    private final Callback callback;
    private BottomInputModeChangeListener modeChangeListener;
    private boolean manualModeEnabled = true;
    private boolean manualAgentOnline = true;
    private ManualAgentType manualAgentType = ManualAgentType.ONLINE_SERVICE;

    BottomManualModeController(View aiAvatarButton, Callback callback) {
        this.aiAvatarButton = aiAvatarButton;
        this.callback = callback;
        configureToggle();
    }

    void setModeChangeListener(BottomInputModeChangeListener modeChangeListener) {
        this.modeChangeListener = modeChangeListener;
    }

    void setManualModeEnabled(boolean enabled) {
        setManualModeEnabled(enabled, true);
    }

    boolean isManualModeEnabled() {
        return manualModeEnabled;
    }

    void setManualAgentOnline(boolean online) {
        if (manualAgentOnline == online) {
            updateManualModeUi();
            notifyManualModeUiStateChanged();
            return;
        }
        manualAgentOnline = online;
        updateManualModeUi();
        notifyManualModeUiStateChanged();
    }

    boolean isManualAgentOnline() {
        return manualAgentOnline;
    }

    void setManualAgentType(ManualAgentType manualAgentType) {
        ManualAgentType nextManualAgentType = manualAgentType == null
            ? ManualAgentType.ONLINE_SERVICE
            : manualAgentType;
        if (this.manualAgentType != nextManualAgentType) {
            this.manualAgentType = nextManualAgentType;
        }
        if (callback != null) {
            callback.onManualAgentTypeChanged();
        }
    }

    ManualAgentType getManualAgentType() {
        return manualAgentType;
    }

    private void configureToggle() {
        if (aiAvatarButton != null) {
            aiAvatarButton.setOnClickListener(view -> setManualModeEnabled(true));
        }
    }

    private void setManualModeEnabled(boolean enabled, boolean notifyListener) {
        if (manualModeEnabled == enabled) {
            updateManualModeUi();
            notifyManualModeUiStateChanged();
            return;
        }
        manualModeEnabled = enabled;
        if (manualModeEnabled && callback != null) {
            callback.closeAttachmentPanel();
        }
        updateManualModeUi();
        notifyManualModeUiStateChanged();
        if (notifyListener && modeChangeListener != null) {
            modeChangeListener.onManualModeChanged(manualModeEnabled);
        }
    }

    private void updateManualModeUi() {
        if (aiAvatarButton != null) {
            aiAvatarButton.setVisibility(
                !manualModeEnabled && manualAgentOnline ? View.VISIBLE : View.GONE
            );
        }
    }

    private void notifyManualModeUiStateChanged() {
        if (callback != null) {
            callback.onManualModeUiStateChanged();
        }
    }

    interface Callback {
        void closeAttachmentPanel();

        void onManualModeUiStateChanged();

        void onManualAgentTypeChanged();
    }
}
