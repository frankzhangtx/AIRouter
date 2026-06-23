package com.example.cctest.widget;

import android.view.MotionEvent;

final class BottomOutsideKeyboardDismissController {

    private final Callback callback;
    private boolean pendingKeyboardDismissOutsideTextInput;

    BottomOutsideKeyboardDismissController(Callback callback) {
        this.callback = callback;
    }

    void prepare(MotionEvent event) {
        if (event == null) {
            return;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pendingKeyboardDismissOutsideTextInput = callback != null
                && callback.shouldDismissKeyboardForTouch(event);
        } else if (action == MotionEvent.ACTION_CANCEL) {
            pendingKeyboardDismissOutsideTextInput = false;
        }
    }

    void finish(MotionEvent event) {
        if (event == null) {
            return;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) {
            pendingKeyboardDismissOutsideTextInput = false;
            return;
        }
        if (action != MotionEvent.ACTION_UP || !pendingKeyboardDismissOutsideTextInput) {
            return;
        }
        pendingKeyboardDismissOutsideTextInput = false;
        if (callback != null && callback.shouldDismissKeyboardForTouch(event)) {
            callback.dismissKeyboardAndClearFocus();
        }
    }

    void reset() {
        pendingKeyboardDismissOutsideTextInput = false;
    }

    interface Callback {
        boolean shouldDismissKeyboardForTouch(MotionEvent event);

        void dismissKeyboardAndClearFocus();
    }
}
