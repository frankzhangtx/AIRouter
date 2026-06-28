package com.example.cctest.widget;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.widget.EditText;
import androidx.activity.ComponentActivity;
import com.example.cctest.voice.NewVoiceRecordPanel;
import com.example.cctest.voice.VoiceRecordCallback;

final class BottomVoiceRecordController {

    private final EditText consultInput;
    private final View bottomInputBar;
    private final Callback callback;
    private NewVoiceRecordPanel voiceRecordPanel;

    BottomVoiceRecordController(
        Context context,
        EditText consultInput,
        View bottomInputBar,
        Callback callback
    ) {
        this.consultInput = consultInput;
        this.bottomInputBar = bottomInputBar;
        this.callback = callback;
        configure(context);
    }

    boolean isAvailable() {
        return voiceRecordPanel != null && consultInput != null;
    }

    void bindActiveHoldTrigger(boolean voiceInputMode) {
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
        bindTextInputHoldTrigger(consultInput);
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

    private NewVoiceRecordPanel createVoiceRecordPanel(Context context) {
        ComponentActivity activity = findComponentActivity(context);
        if (activity != null) {
            return new NewVoiceRecordPanel(activity);
        }
        return new NewVoiceRecordPanel(context);
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
}
