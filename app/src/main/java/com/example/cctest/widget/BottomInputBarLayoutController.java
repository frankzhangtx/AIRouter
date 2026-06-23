package com.example.cctest.widget;

import android.view.View;
import android.view.ViewGroup;
import com.example.cctest.R;

final class BottomInputBarLayoutController {

    private final View inputBarRow;
    private final int normalRowStartMargin;
    private final int normalRowEndMargin;
    private final int manualOfflineHorizontalMargin;
    private boolean manualOfflineStyleEnabled;

    BottomInputBarLayoutController(View rootView) {
        inputBarRow = rootView.findViewById(R.id.input_bar_row);
        normalRowStartMargin = getStartMargin(inputBarRow);
        normalRowEndMargin = getEndMargin(inputBarRow);
        manualOfflineHorizontalMargin = rootView.getResources().getDimensionPixelSize(
            R.dimen.baidu_web_manual_offline_input_horizontal_margin
        );
    }

    void setManualOfflineStyleEnabled(boolean enabled) {
        if (manualOfflineStyleEnabled == enabled) {
            return;
        }
        manualOfflineStyleEnabled = enabled;
        applyRowMargins(enabled);
    }

    private void applyRowMargins(boolean enabled) {
        if (inputBarRow == null) {
            return;
        }
        int horizontalMargin = enabled ? manualOfflineHorizontalMargin : normalRowStartMargin;
        int endMargin = enabled ? manualOfflineHorizontalMargin : normalRowEndMargin;
        ViewGroup.LayoutParams layoutParams = inputBarRow.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams marginLayoutParams =
            (ViewGroup.MarginLayoutParams) layoutParams;
        if (marginLayoutParams.getMarginStart() == horizontalMargin
            && marginLayoutParams.getMarginEnd() == endMargin) {
            return;
        }
        marginLayoutParams.setMarginStart(horizontalMargin);
        marginLayoutParams.setMarginEnd(endMargin);
        inputBarRow.setLayoutParams(marginLayoutParams);
    }

    private int getStartMargin(View view) {
        ViewGroup.LayoutParams layoutParams = view == null ? null : view.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            return ((ViewGroup.MarginLayoutParams) layoutParams).getMarginStart();
        }
        return 0;
    }

    private int getEndMargin(View view) {
        ViewGroup.LayoutParams layoutParams = view == null ? null : view.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            return ((ViewGroup.MarginLayoutParams) layoutParams).getMarginEnd();
        }
        return 0;
    }
}
