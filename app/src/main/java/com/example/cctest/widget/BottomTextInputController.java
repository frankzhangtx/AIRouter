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
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import com.example.cctest.R;

final class BottomTextInputController {

    private static final int TEXT_INPUT_MAX_LINES = 4;

    private final EditText input;
    private final Callback callback;
    private TextWatcher inputTextWatcher;
    private String textInputDraft = "";
    private int originalInputType;
    private int originalImeOptions;
    private int originalGravity;
    private Typeface originalTypeface;

    BottomTextInputController(EditText input, Callback callback) {
        this.input = input;
        this.callback = callback;
        configure();
    }

    void release() {
        if (input != null && inputTextWatcher != null) {
            input.removeTextChangedListener(inputTextWatcher);
            inputTextWatcher = null;
        }
    }

    String getText() {
        if (input == null || input.getText() == null) {
            return "";
        }
        return input.getText().toString();
    }

    void setText(CharSequence text) {
        if (input == null) {
            return;
        }
        input.setText(text == null ? "" : text);
        input.setSelection(input.getText().length());
        keepCursorOnBottomLine();
        notifyTextChanged();
    }

    void clearText() {
        setText("");
    }

    boolean hasContent() {
        return input != null
            && input.getText() != null
            && input.getText().toString().trim().length() > 0;
    }

    void captureDraft() {
        if (input != null && input.getText() != null) {
            textInputDraft = input.getText().toString();
        }
    }

    void showVoicePrompt() {
        if (input == null) {
            return;
        }
        input.setInputType(InputType.TYPE_NULL);
        input.setFocusable(false);
        input.setFocusableInTouchMode(false);
        input.setCursorVisible(false);
        input.setTextIsSelectable(false);
        input.setSingleLine(true);
        input.setMinLines(1);
        input.setMaxLines(1);
        input.setHorizontallyScrolling(false);
        input.setGravity(Gravity.CENTER);
        input.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        input.setText(R.string.baidu_web_hold_to_talk);
    }

    void restoreTextInput() {
        if (input == null) {
            return;
        }
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        input.setInputType(originalInputType);
        input.setCursorVisible(true);
        input.setTextIsSelectable(false);
        applyWrapping();
        input.setImeOptions(originalImeOptions);
        input.setGravity(originalGravity);
        input.setTypeface(originalTypeface);
        input.setHint(R.string.baidu_web_input_hint);
        input.setText(textInputDraft);
        input.setSelection(input.getText().length());
        keepCursorOnBottomLine();
    }

    void hideKeyboard() {
        if (input == null) {
            return;
        }
        View focusedView = null;
        Activity activity = findActivity(input.getContext());
        if (activity != null) {
            focusedView = activity.getCurrentFocus();
        }
        if (focusedView == null) {
            focusedView = input;
        }
        InputMethodManager inputMethodManager =
            (InputMethodManager) input.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
        }
    }

    void dismissKeyboardAndClearFocus() {
        hideKeyboard();
        if (input != null) {
            input.clearFocus();
        }
    }

    void focusTextInput() {
        if (input == null || isVoiceInputMode()) {
            return;
        }
        input.requestFocus();
        input.setSelection(input.getText().length());
        input.post(() -> {
            InputMethodManager inputMethodManager =
                (InputMethodManager) input.getContext().getSystemService(
                    Context.INPUT_METHOD_SERVICE
                );
            if (inputMethodManager != null) {
                inputMethodManager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    boolean containsRawPoint(float rawX, float rawY) {
        if (input == null) {
            return false;
        }
        Rect inputBounds = new Rect();
        return input.getGlobalVisibleRect(inputBounds)
            && inputBounds.contains((int) rawX, (int) rawY);
    }

    void keepCursorOnBottomLine() {
        final EditText currentInput = input;
        if (currentInput == null || isVoiceInputMode()) {
            return;
        }
        currentInput.post(() -> scrollToCursorLine(currentInput));
    }

    private void configure() {
        if (input == null) {
            return;
        }
        originalInputType = input.getInputType();
        originalImeOptions = input.getImeOptions();
        originalGravity = input.getGravity();
        originalTypeface = input.getTypeface();
        applyWrapping();
        input.setVerticalScrollBarEnabled(false);
        input.setOverScrollMode(View.OVER_SCROLL_NEVER);
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
                keepCursorOnBottomLine();
                notifyTextChanged();
            }
        };
        input.addTextChangedListener(inputTextWatcher);
    }

    private void applyWrapping() {
        if (input == null) {
            return;
        }
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(TEXT_INPUT_MAX_LINES);
        input.setHorizontallyScrolling(false);
    }

    private void scrollToCursorLine(EditText currentInput) {
        if (currentInput != input || isVoiceInputMode()) {
            return;
        }
        Layout layout = currentInput.getLayout();
        if (layout == null) {
            return;
        }
        if (layout.getLineCount() <= TEXT_INPUT_MAX_LINES) {
            currentInput.scrollTo(currentInput.getScrollX(), 0);
            return;
        }

        int selection = currentInput.getSelectionEnd();
        if (selection < 0) {
            selection = currentInput.length();
        }
        selection = Math.min(selection, currentInput.length());
        int cursorLine = layout.getLineForOffset(selection);
        int visibleTextHeight = currentInput.getHeight()
            - currentInput.getCompoundPaddingTop()
            - currentInput.getCompoundPaddingBottom();
        if (visibleTextHeight <= 0) {
            return;
        }

        int targetScrollY = Math.max(0, layout.getLineBottom(cursorLine) - visibleTextHeight);
        int maxScrollY = Math.max(0, layout.getHeight() - visibleTextHeight);
        currentInput.scrollTo(currentInput.getScrollX(), Math.min(targetScrollY, maxScrollY));
    }

    private boolean isVoiceInputMode() {
        return callback != null && callback.isVoiceInputMode();
    }

    private void notifyTextChanged() {
        if (callback != null) {
            callback.onTextInputChanged();
        }
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

    interface Callback {
        void onTextInputChanged();

        boolean isVoiceInputMode();
    }
}
