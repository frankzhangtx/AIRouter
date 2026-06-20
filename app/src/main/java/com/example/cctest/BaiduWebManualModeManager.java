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
    static final String CONTROL_TOGGLE_MANUAL_MODE = "toggle_manual_mode";
    static final String CONTROL_TOGGLE_MANUAL_ONLINE = "toggle_manual_online";
    static final String CONTROL_TOGGLE_MANUAL_AGENT_TYPE = "toggle_manual_agent_type";
    static final String CONTROL_APPEND_SUGGESTION_ITEMS = "append_suggestion_items";
    static final String CONTROL_TOGGLE_SUGGESTION_LIST = "toggle_suggestion_list";
    static final String CONTROL_REPLACE_SUGGESTION_ITEMS = "replace_suggestion_items";
    static final String CONTROL_TOGGLE_BOTTOM_OVERLAY = "toggle_bottom_overlay";
    static final String CONTROL_SHOW_BOTTOM_PANEL_HEIGHT = "show_bottom_panel_height";

    interface Listener {
        void onManualControlStateChanged();

        default void onBottomOverlayToggleRequested() {
            // Optional override.
        }

        default void onBottomPanelHeightRequested() {
            // Optional override.
        }
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

    void handleManualControlClick(String controlName) {
        if (controlName == null) {
            return;
        }
        switch (controlName) {
            case CONTROL_TOGGLE_MANUAL_MODE:
                setManualModeEnabledFromActivity(!manualModeEnabled);
                break;
            case CONTROL_TOGGLE_MANUAL_ONLINE:
                manualAgentOnline = !manualAgentOnline;
                if (bottomInputPanel != null) {
                    bottomInputPanel.setManualAgentOnline(manualAgentOnline);
                }
                notifyManualControlStateChanged();
                break;
            case CONTROL_TOGGLE_MANUAL_AGENT_TYPE:
                manualAgentType = manualAgentType == ManualAgentType.ONLINE_SERVICE
                    ? ManualAgentType.INSURANCE_PLANNER
                    : ManualAgentType.ONLINE_SERVICE;
                if (bottomInputPanel != null) {
                    bottomInputPanel.setManualAgentType(manualAgentType);
                }
                notifyManualControlStateChanged();
                break;
            case CONTROL_APPEND_SUGGESTION_ITEMS:
                if (bottomInputPanel != null) {
                    bottomInputPanel.appendAdditionalHorizontalSuggestions();
                }
                ensureSuggestionListVisible();
                break;
            case CONTROL_TOGGLE_SUGGESTION_LIST:
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
                break;
            case CONTROL_REPLACE_SUGGESTION_ITEMS:
                if (bottomInputPanel != null) {
                    bottomInputPanel.replaceHorizontalSuggestions();
                }
                ensureSuggestionListVisible();
                break;
            case CONTROL_TOGGLE_BOTTOM_OVERLAY:
                if (listener != null) {
                    listener.onBottomOverlayToggleRequested();
                }
                break;
            case CONTROL_SHOW_BOTTOM_PANEL_HEIGHT:
                if (listener != null) {
                    listener.onBottomPanelHeightRequested();
                }
                break;
            default:
                break;
        }
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
