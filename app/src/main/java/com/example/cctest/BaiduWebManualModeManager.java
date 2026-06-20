package com.example.cctest;

import android.os.Bundle;
import com.example.cctest.widget.BottomInputPanelView;
import com.example.cctest.widget.BottomInputPanelView.ManualAgentType;

class BaiduWebManualModeManager {

    private static final String KEY_MANUAL_MODE_ENABLED = "manual_mode_enabled";
    private static final String KEY_MANUAL_AGENT_ONLINE = "manual_agent_online";
    private static final String KEY_MANUAL_AGENT_TYPE = "manual_agent_type";
    private static final String KEY_HORIZONTAL_SUGGESTION_LIST_VISIBLE =
        "horizontal_suggestion_list_visible";

    interface Listener {
        void onManualControlStateChanged();
    }

    private BottomInputPanelView bottomInputPanel;
    private Listener listener;
    private boolean manualModeEnabled;
    private boolean manualAgentOnline = true;
    private ManualAgentType manualAgentType = ManualAgentType.ONLINE_SERVICE;
    private boolean horizontalSuggestionListVisible;

    BaiduWebManualModeManager(
        Bundle savedInstanceState,
        BottomInputPanelView bottomInputPanel,
        Listener listener
    ) {
        this.bottomInputPanel = bottomInputPanel;
        this.listener = listener;
        restoreState(savedInstanceState);
        configureBottomInputPanel();
    }

    void handleManualToggleButtonClick() {
        setManualModeEnabledFromActivity(!manualModeEnabled);
    }

    void handleManualOnlineToggleClick() {
        manualAgentOnline = !manualAgentOnline;
        if (bottomInputPanel != null) {
            bottomInputPanel.setManualAgentOnline(manualAgentOnline);
        }
        notifyManualControlStateChanged();
    }

    void handleManualAgentTypeToggleClick() {
        manualAgentType = manualAgentType == ManualAgentType.ONLINE_SERVICE
            ? ManualAgentType.INSURANCE_PLANNER
            : ManualAgentType.ONLINE_SERVICE;
        if (bottomInputPanel != null) {
            bottomInputPanel.setManualAgentType(manualAgentType);
        }
        notifyManualControlStateChanged();
    }

    void handleAppendSuggestionItemsClick() {
        if (bottomInputPanel != null) {
            bottomInputPanel.appendAdditionalHorizontalSuggestions();
        }
        ensureSuggestionListVisible();
    }

    void handleSuggestionListToggleClick() {
        boolean nextVisible = !horizontalSuggestionListVisible;
        if (bottomInputPanel != null) {
            if (nextVisible) {
                bottomInputPanel.ensureDefaultHorizontalSuggestions();
            } else {
                bottomInputPanel.clearHorizontalSuggestions();
            }
            bottomInputPanel.setHorizontalSuggestionListVisible(nextVisible);
        }
        horizontalSuggestionListVisible = nextVisible;
        notifyManualControlStateChanged();
    }

    void handleReplaceSuggestionItemsClick() {
        if (bottomInputPanel != null) {
            bottomInputPanel.replaceHorizontalSuggestions();
        }
        ensureSuggestionListVisible();
    }

    int getManualToggleTextResource() {
        return manualModeEnabled
            ? R.string.baidu_web_toggle_manual_exit
            : R.string.baidu_web_toggle_manual_enter;
    }

    int getManualOnlineTextResource() {
        return manualAgentOnline
            ? R.string.baidu_web_manual_online
            : R.string.baidu_web_manual_offline;
    }

    int getManualAgentTypeTextResource() {
        return manualAgentType == ManualAgentType.ONLINE_SERVICE
            ? R.string.baidu_web_manual_type_online_service
            : R.string.baidu_web_manual_type_insurance_planner;
    }

    int getSuggestionListToggleTextResource() {
        return horizontalSuggestionListVisible
            ? R.string.baidu_web_remove_suggestion_list
            : R.string.baidu_web_add_suggestion_list;
    }

    void saveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_MANUAL_MODE_ENABLED, manualModeEnabled);
        outState.putBoolean(KEY_MANUAL_AGENT_ONLINE, manualAgentOnline);
        outState.putString(KEY_MANUAL_AGENT_TYPE, manualAgentType.name());
        outState.putBoolean(
            KEY_HORIZONTAL_SUGGESTION_LIST_VISIBLE,
            horizontalSuggestionListVisible
        );
    }

    void release() {
        if (bottomInputPanel != null) {
            bottomInputPanel.setModeChangeListener(null);
        }
        bottomInputPanel = null;
        listener = null;
    }

    private void restoreState(Bundle savedInstanceState) {
        manualModeEnabled = savedInstanceState != null
            && savedInstanceState.getBoolean(KEY_MANUAL_MODE_ENABLED);
        manualAgentOnline = savedInstanceState == null
            || savedInstanceState.getBoolean(KEY_MANUAL_AGENT_ONLINE, true);
        manualAgentType = readManualAgentType(savedInstanceState);
        horizontalSuggestionListVisible = savedInstanceState != null
            && savedInstanceState.getBoolean(KEY_HORIZONTAL_SUGGESTION_LIST_VISIBLE);
    }

    private void configureBottomInputPanel() {
        if (bottomInputPanel == null) {
            return;
        }
        bottomInputPanel.setModeChangeListener(enabled -> {
            manualModeEnabled = enabled;
            notifyManualControlStateChanged();
        });
        bottomInputPanel.setManualModeEnabled(manualModeEnabled);
        bottomInputPanel.setManualAgentOnline(manualAgentOnline);
        bottomInputPanel.setManualAgentType(manualAgentType);
        bottomInputPanel.setHorizontalSuggestionListVisible(horizontalSuggestionListVisible);
    }

    private void setManualModeEnabledFromActivity(boolean enabled) {
        manualModeEnabled = enabled;
        if (bottomInputPanel != null) {
            bottomInputPanel.setManualModeEnabled(manualModeEnabled);
        }
        notifyManualControlStateChanged();
    }

    private void ensureSuggestionListVisible() {
        if (horizontalSuggestionListVisible) {
            return;
        }
        horizontalSuggestionListVisible = true;
        if (bottomInputPanel != null) {
            bottomInputPanel.setHorizontalSuggestionListVisible(true);
        }
        notifyManualControlStateChanged();
    }

    private void notifyManualControlStateChanged() {
        if (listener != null) {
            listener.onManualControlStateChanged();
        }
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
}
