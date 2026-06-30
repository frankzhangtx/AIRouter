package com.example.cctest.widget;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.cctest.R;

final class BottomKeyboardController {

    private final View panelView;
    private final View inputBottomFill;
    private final Callback callback;
    private View contentRoot;
    private ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener;
    private View.OnLayoutChangeListener panelLayoutChangeListener;
    private int inputBarBaseBottomMargin;
    private int keyboardVisibilityThreshold;
    private int inputBarKeyboardBottomMargin;
    private int currentKeyboardHeight;
    private boolean keyboardVisible;

    BottomKeyboardController(View panelView, View inputBottomFill, Callback callback) {
        this.panelView = panelView;
        this.inputBottomFill = inputBottomFill;
        this.callback = callback;
    }

    void attach() {
        if (keyboardLayoutListener != null || panelView == null) {
            return;
        }

        Activity activity = findActivity(panelView.getContext());
        if (activity != null) {
            setWindowAdjustResize(activity);
            contentRoot = activity.findViewById(android.R.id.content);
        }
        if (contentRoot == null) {
            contentRoot = panelView.getRootView();
        }
        if (contentRoot == null) {
            return;
        }

        inputBarBaseBottomMargin = getBottomMargin(panelView);
        keyboardVisibilityThreshold = dpToPx(80);
        inputBarKeyboardBottomMargin = panelView.getResources().getDimensionPixelSize(
            R.dimen.baidu_web_input_keyboard_gap
        );
        final Rect visibleFrame = new Rect();
        final int[] rootLocation = new int[2];

        keyboardLayoutListener = () -> {
            contentRoot.getWindowVisibleDisplayFrame(visibleFrame);
            contentRoot.getLocationOnScreen(rootLocation);

            int visibleBottomInRoot = visibleFrame.bottom - rootLocation[1];
            currentKeyboardHeight = Math.max(0, contentRoot.getHeight() - visibleBottomInRoot);
            keyboardVisible = currentKeyboardHeight >= keyboardVisibilityThreshold;
            if (callback != null) {
                callback.onKeyboardStateChanged();
            }
            updateContentInsets();
        };
        contentRoot.getViewTreeObserver().addOnGlobalLayoutListener(keyboardLayoutListener);
        panelLayoutChangeListener = (view, left, top, right, bottom, oldLeft, oldTop,
            oldRight, oldBottom) -> updateContentInsets();
        panelView.addOnLayoutChangeListener(panelLayoutChangeListener);
        panelView.post(this::updateContentInsets);
    }

    void detach() {
        if (contentRoot != null && keyboardLayoutListener != null) {
            contentRoot.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardLayoutListener);
            keyboardLayoutListener = null;
        }
        if (panelView != null && panelLayoutChangeListener != null) {
            panelView.removeOnLayoutChangeListener(panelLayoutChangeListener);
            panelLayoutChangeListener = null;
        }
        contentRoot = null;
    }

    boolean isKeyboardVisible() {
        return keyboardVisible;
    }

    void forceKeyboardHidden() {
        keyboardVisible = false;
        currentKeyboardHeight = 0;
        updateContentInsets();
    }

    void refreshKeyboardVisibilityForAction() {
        if (panelView == null) {
            return;
        }
        if (contentRoot == null) {
            contentRoot = panelView.getRootView();
        }
        if (contentRoot == null) {
            return;
        }
        WindowInsetsCompat rootWindowInsets = ViewCompat.getRootWindowInsets(contentRoot);
        boolean keyboardVisibleFromInsets = false;
        if (rootWindowInsets != null) {
            keyboardVisibleFromInsets = rootWindowInsets.isVisible(WindowInsetsCompat.Type.ime());
            if (keyboardVisibleFromInsets) {
                currentKeyboardHeight = rootWindowInsets
                    .getInsets(WindowInsetsCompat.Type.ime())
                    .bottom;
            }
        }

        Rect visibleFrame = new Rect();
        int[] rootLocation = new int[2];
        contentRoot.getWindowVisibleDisplayFrame(visibleFrame);
        contentRoot.getLocationOnScreen(rootLocation);
        int visibleBottomInRoot = visibleFrame.bottom - rootLocation[1];
        int keyboardHeightFromFrame = Math.max(
            0,
            contentRoot.getHeight() - visibleBottomInRoot
        );
        boolean keyboardVisibleFromFrame = keyboardHeightFromFrame >= keyboardVisibilityThreshold;
        if (keyboardVisibleFromFrame) {
            currentKeyboardHeight = keyboardHeightFromFrame;
        }
        keyboardVisible = keyboardVisibleFromInsets || keyboardVisibleFromFrame;
        updateContentInsets();
    }

    void updateContentInsets() {
        if (panelView == null) {
            return;
        }
        int inputBottomFillHeight = keyboardVisible
            ? inputBarKeyboardBottomMargin
            : panelView.getResources().getDimensionPixelSize(R.dimen.baidu_web_input_bottom_margin);
        setInputBottomFillVisible(!isAttachmentPanelVisible(), inputBottomFillHeight);
        // adjustResize already moves the panel above the IME; only keep the designed gap.
        setBottomMargin(panelView, inputBarBaseBottomMargin);
    }

    private boolean isAttachmentPanelVisible() {
        return callback != null && callback.isAttachmentPanelVisible();
    }

    private void setInputBottomFillVisible(boolean visible, int height) {
        if (inputBottomFill == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = inputBottomFill.getLayoutParams();
        if (layoutParams != null && layoutParams.height != height) {
            layoutParams.height = height;
            inputBottomFill.setLayoutParams(layoutParams);
        }
        int targetVisibility = visible ? View.VISIBLE : View.GONE;
        if (inputBottomFill.getVisibility() != targetVisibility) {
            inputBottomFill.setVisibility(targetVisibility);
        }
    }

    private void setWindowAdjustResize(Activity activity) {
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        int softInputMode = window.getAttributes().softInputMode;
        int stateMode = softInputMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE;
        window.setSoftInputMode(stateMode | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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

    private int getBottomMargin(View view) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            return ((ViewGroup.MarginLayoutParams) layoutParams).bottomMargin;
        }
        return 0;
    }

    private void setBottomMargin(View view, int bottomMargin) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams marginLayoutParams =
            (ViewGroup.MarginLayoutParams) layoutParams;
        if (marginLayoutParams.bottomMargin == bottomMargin) {
            return;
        }
        marginLayoutParams.bottomMargin = bottomMargin;
        view.setLayoutParams(marginLayoutParams);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * panelView.getResources().getDisplayMetrics().density);
    }

    interface Callback {
        void onKeyboardStateChanged();

        boolean isAttachmentPanelVisible();
    }
}
