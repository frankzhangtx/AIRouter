package com.example.cctest.widget;

import android.view.View;
import com.example.cctest.R;

final class BottomAttachmentPanelController {

    private final View attachmentPanel;
    private final View optionAttachImage;
    private final View optionSendProduct;
    private final Callback callback;

    BottomAttachmentPanelController(View rootView, Callback callback) {
        this.callback = callback;
        attachmentPanel = rootView.findViewById(R.id.input_attachment_panel);
        optionAttachImage = rootView.findViewById(R.id.option_attach_image);
        optionSendProduct = rootView.findViewById(R.id.option_send_product);
        configureOptions();
    }

    void setVisible(boolean visible) {
        if (attachmentPanel == null) {
            return;
        }
        int targetVisibility = visible ? View.VISIBLE : View.GONE;
        if (attachmentPanel.getVisibility() == targetVisibility) {
            return;
        }
        attachmentPanel.setVisibility(targetVisibility);
        if (callback != null) {
            callback.onVisibilityChanged();
        }
    }

    boolean isVisible() {
        return attachmentPanel != null && attachmentPanel.getVisibility() == View.VISIBLE;
    }

    void setManualAgentType(ManualAgentType manualAgentType) {
        if (optionSendProduct == null) {
            return;
        }
        optionSendProduct.setVisibility(
            manualAgentType == ManualAgentType.INSURANCE_PLANNER ? View.VISIBLE : View.GONE
        );
    }

    private void configureOptions() {
        if (optionAttachImage != null) {
            optionAttachImage.setOnClickListener(view -> {
                setVisible(false);
                if (callback != null) {
                    callback.onImageRequested();
                }
            });
        }
        if (optionSendProduct != null) {
            optionSendProduct.setOnClickListener(view -> {
                setVisible(false);
                if (callback != null) {
                    callback.onProductRequested();
                }
            });
        }
    }

    interface Callback {
        void onVisibilityChanged();

        void onImageRequested();

        void onProductRequested();
    }
}
