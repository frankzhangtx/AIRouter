package com.example.cctest.widget;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.widget.EditText;
import androidx.activity.ComponentActivity;
import com.example.cctest.voice.NewVoiceRecordPanel;
import com.example.cctest.voice.PicVoiceRecordPanel;
import com.example.cctest.voice.VoiceRecordCallback;

final class BottomVoiceRecordController {

    private final Context context;
    private final EditText consultInput;
    private final View bottomInputBar;
    private final Callback callback;
    private VoiceRecordPanelAdapter voiceRecordPanel;
    private boolean useNewVoiceRecordPanel = true;
    private boolean voiceInputMode;

    BottomVoiceRecordController(
        Context context,
        EditText consultInput,
        View bottomInputBar,
        Callback callback
    ) {
        this.context = context;
        this.consultInput = consultInput;
        this.bottomInputBar = bottomInputBar;
        this.callback = callback;
        configure(context);
    }

    boolean isAvailable() {
        return voiceRecordPanel != null && consultInput != null;
    }

    void bindActiveHoldTrigger(boolean voiceInputMode) {
        this.voiceInputMode = voiceInputMode;
        if (!isAvailable()) {
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
                bottomInputBar.setOnClickListener(view -> {
                    if (callback != null) {
                        callback.focusTextInput();
                    }
                });
            }
        }
    }

    void setUseNewVoiceRecordPanel(boolean useNewVoiceRecordPanel) {
        if (this.useNewVoiceRecordPanel == useNewVoiceRecordPanel) {
            return;
        }
        this.useNewVoiceRecordPanel = useNewVoiceRecordPanel;
        if (voiceRecordPanel != null) {
            voiceRecordPanel.dismiss();
        }
        configureVoiceRecordPanel();
        bindActiveHoldTrigger(voiceInputMode);
    }

    void dismiss() {
        if (voiceRecordPanel != null) {
            voiceRecordPanel.dismiss();
        }
    }

    private void configure(Context context) {
        if (consultInput == null) {
            return;
        }
        consultInput.setLongClickable(false);
        configureVoiceRecordPanel();
        bindTextInputHoldTrigger(consultInput);
    }

    private void configureVoiceRecordPanel() {
        voiceRecordPanel = createVoiceRecordPanel(context);
        voiceRecordPanel.setRecordPanelAnchorView(bottomInputBar);
        voiceRecordPanel.setCallback(new VoiceRecordCallback() {
            @Override
            public void onStart() {
                if (callback != null) {
                    callback.onStart();
                }
            }

            @Override
            public void onCancel() {
                if (callback != null) {
                    callback.onCancel();
                }
            }

            @Override
            public void onFinish() {
                if (callback != null) {
                    callback.onFinish();
                }
            }
        });
    }

    private void bindTextInputHoldTrigger(View trigger) {
        if (voiceRecordPanel == null || trigger == null) {
            return;
        }
        voiceRecordPanel.bindToHoldTriggerPreservingClickWhen(
            trigger,
            () -> callback != null && callback.shouldEnableTextHoldTrigger()
        );
    }

    private VoiceRecordPanelAdapter createVoiceRecordPanel(Context context) {
        ComponentActivity activity = findComponentActivity(context);
        if (useNewVoiceRecordPanel) {
            return new NewVoiceRecordPanelAdapter(
                activity != null ? new NewVoiceRecordPanel(activity) : new NewVoiceRecordPanel(context)
            );
        }
        return new PicVoiceRecordPanelAdapter(
            activity != null ? new PicVoiceRecordPanel(activity) : new PicVoiceRecordPanel(context)
        );
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

    interface Callback {
        boolean shouldEnableTextHoldTrigger();

        void focusTextInput();

        void onStart();

        void onCancel();

        void onFinish();
    }

    private interface HoldTriggerCondition {
        boolean shouldEnableHoldTrigger();
    }

    private interface VoiceRecordPanelAdapter {
        void setRecordPanelAnchorView(View anchorView);

        void setCallback(VoiceRecordCallback callback);

        void bindToImmediateHoldTrigger(View trigger);

        void bindToHoldTriggerPreservingClickWhen(
            View trigger,
            HoldTriggerCondition condition
        );

        void dismiss();
    }

    private static final class NewVoiceRecordPanelAdapter implements VoiceRecordPanelAdapter {

        private final NewVoiceRecordPanel panel;

        NewVoiceRecordPanelAdapter(NewVoiceRecordPanel panel) {
            this.panel = panel;
        }

        @Override
        public void setRecordPanelAnchorView(View anchorView) {
            panel.setRecordPanelAnchorView(anchorView);
        }

        @Override
        public void setCallback(VoiceRecordCallback callback) {
            panel.setCallback(callback);
        }

        @Override
        public void bindToImmediateHoldTrigger(View trigger) {
            panel.bindToImmediateHoldTrigger(trigger);
        }

        @Override
        public void bindToHoldTriggerPreservingClickWhen(
            View trigger,
            HoldTriggerCondition condition
        ) {
            panel.bindToHoldTriggerPreservingClickWhen(
                trigger,
                () -> condition != null && condition.shouldEnableHoldTrigger()
            );
        }

        @Override
        public void dismiss() {
            panel.dismiss();
        }
    }

    private static final class PicVoiceRecordPanelAdapter implements VoiceRecordPanelAdapter {

        private final PicVoiceRecordPanel panel;

        PicVoiceRecordPanelAdapter(PicVoiceRecordPanel panel) {
            this.panel = panel;
        }

        @Override
        public void setRecordPanelAnchorView(View anchorView) {
            panel.setRecordPanelAnchorView(anchorView);
        }

        @Override
        public void setCallback(VoiceRecordCallback callback) {
            panel.setCallback(callback);
        }

        @Override
        public void bindToImmediateHoldTrigger(View trigger) {
            panel.bindToImmediateHoldTrigger(trigger);
        }

        @Override
        public void bindToHoldTriggerPreservingClickWhen(
            View trigger,
            HoldTriggerCondition condition
        ) {
            panel.bindToHoldTriggerPreservingClickWhen(
                trigger,
                () -> condition != null && condition.shouldEnableHoldTrigger()
            );
        }

        @Override
        public void dismiss() {
            panel.dismiss();
        }
    }
}
