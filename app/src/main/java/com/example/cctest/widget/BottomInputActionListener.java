package com.example.cctest.widget;

public interface BottomInputActionListener {
    default void onSendText(String text) {
        // Optional override.
    }

    default void onImageRequested() {
        // Optional override.
    }

    default void onProductRequested() {
        // Optional override.
    }

    default void onToastRequested(int messageResource, Object... formatArgs) {
        // Optional override.
    }
}
