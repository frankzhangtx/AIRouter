package com.example.cctest.voice;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import com.example.cctest.R;
import java.io.File;
import java.io.IOException;
import java.util.Random;

public class NewVoiceRecordPanel extends FrameLayout {

    private final NewVoiceRecordCanvasView canvasView;
    private final NewVoiceAmplitudeRecorder recorder;
    private final Handler gestureHandler = new Handler(Looper.getMainLooper());
    private final int[] panelLocationOnScreen = new int[2];
    private VoiceRecordCallback callback;
    private ActivityResultLauncher<String> permissionLauncher;
    private boolean pendingShowAfterPermission;
    private Float pendingStartRawX;
    private Float pendingStartRawY;
    private Runnable holdStartRunnable;
    private boolean holdPointerDown;
    private boolean holdTriggered;
    private boolean recording;
    private boolean ending;

    public NewVoiceRecordPanel(Context context) {
        super(context);
        canvasView = new NewVoiceRecordCanvasView(context);
        recorder = new NewVoiceAmplitudeRecorder(context.getApplicationContext());
        initialize(null);
    }

    public NewVoiceRecordPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        canvasView = new NewVoiceRecordCanvasView(context);
        recorder = new NewVoiceAmplitudeRecorder(context.getApplicationContext());
        initialize(null);
    }

    public NewVoiceRecordPanel(ComponentActivity activity) {
        super(activity);
        canvasView = new NewVoiceRecordCanvasView(activity);
        recorder = new NewVoiceAmplitudeRecorder(activity.getApplicationContext());
        initialize(activity);
    }

    public void setCallback(VoiceRecordCallback callback) {
        this.callback = callback;
    }

    public void setRecordPanelAnchorView(View anchorView) {
        canvasView.setPanelAnchorView(anchorView);
    }

    public void bindToHoldTrigger(View trigger) {
        trigger.setOnClickListener(null);
        trigger.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                return handleHoldTriggerTouch(view, event, true, false);
            }
        });
    }

    public void bindToImmediateHoldTrigger(View trigger) {
        trigger.setOnClickListener(null);
        trigger.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                return handleHoldTriggerTouch(view, event, true, true);
            }
        });
    }

    public void bindToHoldTriggerPreservingClick(View trigger) {
        trigger.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                return handleHoldTriggerTouch(view, event, false, false);
            }
        });
    }

    public void bindToHoldTriggerPreservingClickWhen(
        View trigger,
        HoldTriggerCondition condition
    ) {
        trigger.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                boolean alreadyHandlingHold = recording || holdTriggered;
                if (!alreadyHandlingHold
                    && condition != null
                    && !condition.shouldEnableHoldTrigger()) {
                    return false;
                }
                return handleHoldTriggerTouch(view, event, false, false);
            }
        });
    }

    public void show() {
        if (getParent() != null || recording || ending) {
            return;
        }
        if (!hasRecordPermission()) {
            requestRecordPermission();
            return;
        }
        attachAndStart();
    }

    public void dismiss() {
        pendingShowAfterPermission = false;
        cancelHoldStart();
        releaseRecording();
        removeFromParent();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!recording) {
            return true;
        }

        boolean insideRecordArea = canvasView.isPointInsideRecordArea(event.getX(), event.getY());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                updateFinger(event.getX(), event.getY(), insideRecordArea);
                break;
            case MotionEvent.ACTION_MOVE:
                updateFinger(event.getX(), event.getY(), insideRecordArea, true);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                updateFinger(event.getX(), event.getY(), insideRecordArea);
                finishRecording(!insideRecordArea);
                break;
            case MotionEvent.ACTION_CANCEL:
                updateFinger(event.getX(), event.getY(), false);
                finishRecording(true);
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
            && event.getAction() == KeyEvent.ACTION_UP
            && recording) {
            finishRecording(true);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelHoldStart();
        releaseRecording();
        super.onDetachedFromWindow();
    }

    private void initialize(ComponentActivity activity) {
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(
            canvasView,
            new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        );
        permissionLauncher = activity == null ? null : registerPermissionLauncherIfPossible(activity);
    }

    private ActivityResultLauncher<String> registerPermissionLauncherIfPossible(
        ComponentActivity activity
    ) {
        if (activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            return null;
        }
        return activity.registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            new ActivityResultCallback<Boolean>() {
                @Override
                public void onActivityResult(Boolean granted) {
                    if (pendingShowAfterPermission) {
                        pendingShowAfterPermission = false;
                        if (Boolean.TRUE.equals(granted)) {
                            attachAndStart();
                        } else if (callback != null) {
                            callback.onCancel();
                        }
                    }
                }
            }
        );
    }

    private void attachAndStart() {
        Activity activity = findActivity(getContext());
        if (activity == null) {
            return;
        }
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        if (getParent() == null) {
            content.addView(
                this,
                new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            );
        }
        bringToFront();
        requestFocus();
        canvasView.reset();
        canvasView.startAnimating();
        post(new Runnable() {
            @Override
            public void run() {
                Float startRawX = pendingStartRawX;
                Float startRawY = pendingStartRawY;
                if (startRawX != null && startRawY != null) {
                    updateFingerFromScreen(startRawX, startRawY);
                } else {
                    canvasView.updateFinger(getWidth() / 2f, getHeight(), true);
                }
                pendingStartRawX = null;
                pendingStartRawY = null;
            }
        });

        recording = recorder.start(new VolumeChangedListener() {
            @Override
            public void onVolumeChanged(int volume) {
                canvasView.updateVolume(volume);
            }
        });
        if (recording) {
            vibrateForVoicePanelFeedback();
            if (callback != null) {
                callback.onStart();
            }
        } else {
            canvasView.stopAnimating();
            removeFromParent();
            if (callback != null) {
                callback.onCancel();
            }
        }
    }

    private void finishRecording(boolean cancelled) {
        if (ending) {
            return;
        }
        ending = true;
        boolean wasRecording = recording;
        releaseRecording();
        removeFromParent();
        ending = false;

        if (wasRecording && callback != null) {
            if (cancelled) {
                callback.onCancel();
            } else {
                callback.onFinish();
            }
        }
    }

    private void releaseRecording() {
        if (recording) {
            recorder.stop();
        }
        recording = false;
        canvasView.stopAnimating();
        canvasView.reset();
    }

    private boolean handleHoldTriggerTouch(
        View view,
        MotionEvent event,
        boolean consumeTouchBeforeHold,
        boolean startImmediately
    ) {
        pendingStartRawX = event.getRawX();
        pendingStartRawY = event.getRawY();
        boolean wasHoldGesture = recording || holdTriggered;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                holdPointerDown = true;
                holdTriggered = false;
                view.setPressed(true);
                requestDisallowParentIntercept(view, true);
                if (startImmediately) {
                    holdTriggered = true;
                    show();
                } else {
                    scheduleHoldStart();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (recording) {
                    updateFingerFromScreen(event.getRawX(), event.getRawY(), true);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                wasHoldGesture = recording || holdTriggered;
                boolean shouldCancel = recording ? !updateFingerFromScreen(event.getRawX(), event.getRawY()) : true;
                cancelHoldStart();
                view.setPressed(false);
                requestDisallowParentIntercept(view, false);
                holdPointerDown = false;
                if (recording) {
                    finishRecording(shouldCancel);
                } else if (!holdTriggered && consumeTouchBeforeHold) {
                    pendingShowAfterPermission = false;
                    view.performClick();
                }
                holdTriggered = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                wasHoldGesture = recording || holdTriggered;
                cancelHoldStart();
                view.setPressed(false);
                requestDisallowParentIntercept(view, false);
                holdPointerDown = false;
                if (recording) {
                    finishRecording(true);
                } else {
                    pendingShowAfterPermission = false;
                }
                holdTriggered = false;
                break;
            default:
                break;
        }
        return consumeTouchBeforeHold || wasHoldGesture;
    }

    private void requestDisallowParentIntercept(View view, boolean disallow) {
        ViewParent parent = view.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private void scheduleHoldStart() {
        cancelHoldStart();
        holdStartRunnable = new Runnable() {
            @Override
            public void run() {
                if (holdPointerDown && !recording) {
                    holdTriggered = true;
                    show();
                }
            }
        };
        gestureHandler.postDelayed(
            holdStartRunnable,
            ViewConfiguration.getLongPressTimeout()
        );
    }

    private void cancelHoldStart() {
        if (holdStartRunnable != null) {
            gestureHandler.removeCallbacks(holdStartRunnable);
        }
        holdStartRunnable = null;
    }

    private void updateFinger(float x, float y, boolean active) {
        updateFinger(x, y, active, false);
    }

    private void updateFinger(float x, float y, boolean active, boolean vibrateOnModeChange) {
        canvasView.updateFinger(x, y, active);
        boolean cancelModeChanged = canvasView.setCancelMode(!active);
        if (vibrateOnModeChange && cancelModeChanged) {
            vibrateForVoicePanelFeedback();
        }
    }

    private boolean updateFingerFromScreen(float rawX, float rawY) {
        return updateFingerFromScreen(rawX, rawY, false);
    }

    private boolean updateFingerFromScreen(float rawX, float rawY, boolean vibrateOnModeChange) {
        getLocationOnScreen(panelLocationOnScreen);
        float panelX = rawX - panelLocationOnScreen[0];
        float panelY = rawY - panelLocationOnScreen[1];
        boolean insideRecordArea = canvasView.isPointInsideRecordArea(panelX, panelY);
        updateFinger(panelX, panelY, insideRecordArea, vibrateOnModeChange);
        return insideRecordArea;
    }

    private void vibrateForVoicePanelFeedback() {
        Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(
                MODE_CHANGE_VIBRATION_DURATION_MS,
                VibrationEffect.DEFAULT_AMPLITUDE
            ));
        } else {
            vibrator.vibrate(MODE_CHANGE_VIBRATION_DURATION_MS);
        }
    }

    private boolean hasRecordPermission() {
        return ContextCompat.checkSelfPermission(
            getContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRecordPermission() {
        pendingShowAfterPermission = true;
        if (permissionLauncher != null) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        Activity activity = findActivity(getContext());
        if (activity != null) {
            ActivityCompat.requestPermissions(
                activity,
                new String[] { Manifest.permission.RECORD_AUDIO },
                RECORD_AUDIO_REQUEST_CODE
            );
        }
        pendingShowAfterPermission = false;
    }

    private void removeFromParent() {
        ViewParent parent = getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(this);
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

    private interface VolumeChangedListener {
        void onVolumeChanged(int volume);
    }

    public interface HoldTriggerCondition {
        boolean shouldEnableHoldTrigger();
    }

    private static class NewVoiceRecordCanvasView extends View {

        private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF panelRect = new RectF();
        private final RectF backgroundRect = new RectF();
        private final RectF barRect = new RectF();
        private final Random visualizerRandom = new Random();
        private final int[] anchorLocationOnScreen = new int[2];
        private final int[] canvasLocationOnScreen = new int[2];
        private final int panelBackgroundColor;
        private final int normalPanelColor;
        private final int normalPanelCenterColor;
        private final int cancelPanelColor;
        private final int cancelPanelCenterColor;
        private final int normalPromptColor;
        private final int cancelPromptColor;
        private final float panelHorizontalMargin;
        private final float panelBottomOffset;
        private final float panelHeight;
        private final float panelCornerRadius;
        private final float promptCenterToPanelTopGap;
        private final float backgroundAbovePromptGap;
        private final float backgroundFadeHeight;
        private final float visualizerMaxWidth;
        private final float visualizerBarWidth;
        private final float visualizerBarGap;
        private final float visualizerMinBarHeight;
        private final float visualizerMaxBarHeight;
        private final float visualizerBarRadius;
        private final String hintSend;
        private final String hintCancel;

        private float displayedVolume;
        private float targetVolume;
        private float wavePhase;
        private int currentPanelColor;
        private int currentPanelCenterColor;
        private int currentPromptColor;
        private boolean cancelMode;
        private String promptText;
        private float fingerX;
        private float fingerY;
        private float targetFingerX;
        private float targetFingerY;
        private float[] currentBarProfile = new float[0];
        private float[] targetBarProfile = new float[0];
        private int waveProfileHotspotCount = -1;
        private long lastWaveProfileUpdatedAt;
        private ValueAnimator pulseAnimator;
        private ValueAnimator colorAnimator;
        private View panelAnchorView;

        NewVoiceRecordCanvasView(Context context) {
            super(context);
            panelBackgroundColor = ContextCompat.getColor(
                context,
                R.color.baidu_web_input_container_background
            );
            normalPanelColor = ContextCompat.getColor(context, R.color.pic_voice_record_panel);
            normalPanelCenterColor = ContextCompat.getColor(context, R.color.pic_voice_record_panel_center);
            cancelPanelColor = ContextCompat.getColor(context, R.color.pic_voice_record_cancel_panel);
            cancelPanelCenterColor = ContextCompat.getColor(context, R.color.pic_voice_record_cancel_panel_center);
            normalPromptColor = ContextCompat.getColor(context, R.color.pic_voice_record_prompt);
            cancelPromptColor = ContextCompat.getColor(context, R.color.pic_voice_record_cancel_prompt);
            panelHorizontalMargin = getResources().getDimension(R.dimen.pic_voice_record_panel_horizontal_margin);
            panelBottomOffset = getResources().getDimension(R.dimen.pic_voice_record_panel_bottom_offset);
            panelHeight = getResources().getDimension(R.dimen.pic_voice_record_panel_height);
            panelCornerRadius = getResources().getDimension(R.dimen.pic_voice_record_panel_corner_radius);
            promptCenterToPanelTopGap = getResources().getDimension(
                R.dimen.new_voice_record_prompt_center_to_panel_top_gap
            );
            backgroundAbovePromptGap = getResources().getDimension(
                R.dimen.new_voice_record_background_above_prompt_gap
            );
            backgroundFadeHeight = getResources().getDimension(
                R.dimen.new_voice_record_background_fade_height
            );
            visualizerMaxWidth = getResources().getDimension(R.dimen.pic_voice_record_visualizer_max_width);
            visualizerBarWidth = getResources().getDimension(R.dimen.pic_voice_record_visualizer_bar_width);
            visualizerBarGap = getResources().getDimension(R.dimen.pic_voice_record_visualizer_bar_gap);
            visualizerMinBarHeight = getResources().getDimension(R.dimen.pic_voice_record_visualizer_min_bar_height);
            visualizerMaxBarHeight = getResources().getDimension(R.dimen.pic_voice_record_visualizer_max_bar_height);
            visualizerBarRadius = getResources().getDimension(R.dimen.pic_voice_record_visualizer_bar_radius);
            hintSend = getResources().getString(R.string.pic_voice_record_hint_send);
            hintCancel = getResources().getString(R.string.voice_record_hint_cancel);

            backgroundPaint.setColor(panelBackgroundColor);
            textPaint.setColor(normalPromptColor);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(getResources().getDimension(
                R.dimen.new_voice_record_prompt_text_size
            ));
            currentPanelColor = normalPanelColor;
            currentPanelCenterColor = normalPanelCenterColor;
            currentPromptColor = normalPromptColor;
            promptText = hintSend;
        }

        void setPanelAnchorView(View panelAnchorView) {
            this.panelAnchorView = panelAnchorView;
            invalidate();
        }

        void reset() {
            displayedVolume = 0f;
            targetVolume = 0f;
            wavePhase = 0f;
            cancelMode = false;
            promptText = hintSend;
            currentPanelColor = normalPanelColor;
            currentPanelCenterColor = normalPanelCenterColor;
            currentPromptColor = normalPromptColor;
            fingerX = getWidth() / 2f;
            fingerY = getHeight();
            targetFingerX = fingerX;
            targetFingerY = fingerY;
            resetVisualizerProfile();
            if (colorAnimator != null) {
                colorAnimator.cancel();
            }
            invalidate();
        }

        void startAnimating() {
            if (pulseAnimator != null && pulseAnimator.isStarted()) {
                return;
            }
            pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
            pulseAnimator.setDuration(WAVE_DURATION_MS);
            pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnimator.setInterpolator(null);
            pulseAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animator) {
                    wavePhase = (Float) animator.getAnimatedValue();
                    displayedVolume += (targetVolume - displayedVolume) * VOLUME_EASING;
                    fingerX += (targetFingerX - fingerX) * FINGER_EASING;
                    fingerY += (targetFingerY - fingerY) * FINGER_EASING;
                    invalidate();
                }
            });
            pulseAnimator.start();
        }

        void stopAnimating() {
            if (pulseAnimator != null) {
                pulseAnimator.cancel();
            }
            pulseAnimator = null;
            if (colorAnimator != null) {
                colorAnimator.cancel();
            }
            colorAnimator = null;
        }

        void updateVolume(int volume) {
            targetVolume = coerceIn(volume, MIN_VOLUME, MAX_VOLUME);
        }

        void updateFinger(float x, float y, boolean active) {
            targetFingerX = coerceIn(x, 0f, getWidth());
            targetFingerY = coerceIn(y, 0f, getHeight());
            if (fingerX == 0f && fingerY == 0f) {
                fingerX = targetFingerX;
                fingerY = targetFingerY;
            }
        }

        boolean setCancelMode(boolean cancel) {
            if (cancelMode == cancel) {
                return false;
            }
            cancelMode = cancel;
            promptText = cancel ? hintCancel : hintSend;
            animatePanelColors(
                cancel ? cancelPanelColor : normalPanelColor,
                cancel ? cancelPanelCenterColor : normalPanelCenterColor,
                cancel ? cancelPromptColor : normalPromptColor
            );
            return true;
        }

        boolean isPointInsideRecordArea(float x, float y) {
            if (getWidth() == 0 || getHeight() == 0) {
                return false;
            }
            updatePanelBounds();
            return isPointInsideRoundedRect(x, y);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            updatePanelBounds();
            drawPanelBackground(canvas);
            drawPrompt(canvas);
            drawPanel(canvas);
            drawVisualizer(canvas);
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            if (oldWidth == 0 && oldHeight == 0) {
                fingerX = width / 2f;
                fingerY = height;
                targetFingerX = fingerX;
                targetFingerY = fingerY;
            }
        }

        private void drawPanelBackground(Canvas canvas) {
            float promptTop = getPromptBaseline() + textPaint.getFontMetrics().ascent;
            float solidBackgroundTop = Math.max(0f, promptTop - backgroundAbovePromptGap);
            float fadeTop = Math.max(0f, solidBackgroundTop - backgroundFadeHeight);

            if (fadeTop < solidBackgroundTop) {
                backgroundPaint.setShader(new LinearGradient(
                    0f,
                    fadeTop,
                    0f,
                    solidBackgroundTop,
                    withAlpha(panelBackgroundColor, 0),
                    panelBackgroundColor,
                    Shader.TileMode.CLAMP
                ));
                backgroundRect.set(0f, fadeTop, getWidth(), solidBackgroundTop);
                canvas.drawRect(backgroundRect, backgroundPaint);
                backgroundPaint.setShader(null);
            }

            backgroundPaint.setColor(panelBackgroundColor);
            backgroundRect.set(0f, solidBackgroundTop, getWidth(), getHeight());
            canvas.drawRect(backgroundRect, backgroundPaint);
        }

        private void drawPrompt(Canvas canvas) {
            textPaint.setColor(currentPromptColor);
            canvas.drawText(promptText, getWidth() / 2f, getPromptBaseline(), textPaint);
        }

        private float getPromptBaseline() {
            float promptCenterY = panelRect.top - promptCenterToPanelTopGap;
            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
            return promptCenterY - (fontMetrics.ascent + fontMetrics.descent) / 2f;
        }

        private void drawPanel(Canvas canvas) {
            panelPaint.setShader(new LinearGradient(
                panelRect.left,
                panelRect.centerY(),
                panelRect.right,
                panelRect.centerY(),
                new int[] { currentPanelColor, currentPanelCenterColor, currentPanelColor },
                new float[] { 0f, 0.52f, 1f },
                Shader.TileMode.CLAMP
            ));
            float radius = Math.min(panelCornerRadius, panelRect.height() / 2f);
            canvas.drawRoundRect(panelRect, radius, radius, panelPaint);
            panelPaint.setShader(null);
        }

        private void drawVisualizer(Canvas canvas) {
            float availableWidth = panelRect.width() * VISUALIZER_WIDTH_RATIO;
            float visualizerWidth = Math.min(visualizerMaxWidth, availableWidth);
            float slotWidth = visualizerBarWidth + visualizerBarGap;
            int barCount = Math.max(MIN_BAR_COUNT, (int) (visualizerWidth / slotWidth));
            float totalWidth = barCount * visualizerBarWidth + (barCount - 1) * visualizerBarGap;
            float startX = panelRect.centerX() - totalWidth / 2f;
            float centerIndex = (barCount - 1) / 2f;
            float volumeRatio = coerceIn(displayedVolume / MAX_VOLUME, 0f, 1f);
            float pulse = (float) ((Math.sin(wavePhase * FULL_CIRCLE) + 1f) / 2f);
            float baselineY = panelRect.centerY();
            updateRandomWaveProfile(barCount, volumeRatio);

            for (int index = 0; index < barCount; index++) {
                float distanceFromCenter = centerIndex == 0f ? 0f : Math.abs(index - centerIndex) / centerIndex;
                float envelope = coerceIn(1f - distanceFromCenter * 0.72f, 0.24f, 1f);
                float randomBarProfile = currentBarProfile.length == barCount
                    ? currentBarProfile[index]
                    : 0f;
                float amplifiedVolumeRatio = coerceIn(
                    volumeRatio * VISUALIZER_VOLUME_AMPLITUDE_MULTIPLIER,
                    0f,
                    1f
                );
                float activeHeight = visualizerMinBarHeight
                    + (visualizerMaxBarHeight - visualizerMinBarHeight)
                    * (0.12f
                        + volumeRatio * 0.08f * envelope
                        + amplifiedVolumeRatio
                            * (0.46f + 0.54f * pulse)
                            * randomBarProfile
                            * envelope);
                float x = startX + index * slotWidth;
                float top = baselineY - activeHeight / 2f;
                float bottom = baselineY + activeHeight / 2f;
                int alpha = coerceIn(
                    (int) (150 + 105 * envelope * (0.35f + volumeRatio * 0.65f)),
                    140,
                    255
                );
                barPaint.setColor(withAlpha(ContextCompat.getColor(getContext(), R.color.white), alpha));
                barRect.set(x, top, x + visualizerBarWidth, bottom);
                canvas.drawRoundRect(barRect, visualizerBarRadius, visualizerBarRadius, barPaint);
            }
        }

        private void updateRandomWaveProfile(int barCount, float volumeRatio) {
            boolean forceRefresh = ensureVisualizerProfileSize(barCount);
            int hotspotCount = resolveWaveProfileHotspotCount(barCount, volumeRatio);
            long now = System.currentTimeMillis();
            if (forceRefresh
                || hotspotCount != waveProfileHotspotCount
                || now - lastWaveProfileUpdatedAt >= RANDOM_WAVE_PROFILE_INTERVAL_MS) {
                generateRandomWaveProfile(barCount, hotspotCount);
                waveProfileHotspotCount = hotspotCount;
                lastWaveProfileUpdatedAt = now;
            }

            for (int index = 0; index < barCount; index++) {
                currentBarProfile[index] += (targetBarProfile[index] - currentBarProfile[index])
                    * RANDOM_WAVE_PROFILE_EASING;
            }
        }

        private boolean ensureVisualizerProfileSize(int barCount) {
            if (currentBarProfile.length == barCount && targetBarProfile.length == barCount) {
                return false;
            }
            currentBarProfile = new float[barCount];
            targetBarProfile = new float[barCount];
            waveProfileHotspotCount = -1;
            lastWaveProfileUpdatedAt = 0L;
            return true;
        }

        private void resetVisualizerProfile() {
            for (int index = 0; index < currentBarProfile.length; index++) {
                currentBarProfile[index] = 0f;
            }
            for (int index = 0; index < targetBarProfile.length; index++) {
                targetBarProfile[index] = 0f;
            }
            waveProfileHotspotCount = -1;
            lastWaveProfileUpdatedAt = 0L;
        }

        private int resolveWaveProfileHotspotCount(int barCount, float volumeRatio) {
            if (volumeRatio < RANDOM_WAVE_SILENCE_THRESHOLD) {
                return 0;
            }
            int maxHotspotCount = Math.min(RANDOM_WAVE_MAX_HOTSPOTS, Math.max(1, barCount / 3));
            float responsiveVolumeRatio = (float) Math.pow(volumeRatio, RANDOM_WAVE_COUNT_VOLUME_POWER);
            return coerceIn(
                1 + Math.round((maxHotspotCount - 1) * responsiveVolumeRatio),
                1,
                maxHotspotCount
            );
        }

        private void generateRandomWaveProfile(int barCount, int hotspotCount) {
            for (int index = 0; index < barCount; index++) {
                targetBarProfile[index] = 0f;
            }
            for (int hotspotIndex = 0; hotspotIndex < hotspotCount; hotspotIndex++) {
                float center = pickCenterBiasedBarIndex(barCount);
                float span = RANDOM_WAVE_MIN_BAR_SPAN
                    + visualizerRandom.nextFloat()
                        * (RANDOM_WAVE_MAX_BAR_SPAN - RANDOM_WAVE_MIN_BAR_SPAN);
                float strength = RANDOM_WAVE_MIN_STRENGTH
                    + visualizerRandom.nextFloat()
                        * (RANDOM_WAVE_MAX_STRENGTH - RANDOM_WAVE_MIN_STRENGTH);
                float shapePower = RANDOM_WAVE_MIN_SHAPE_POWER
                    + visualizerRandom.nextFloat()
                        * (RANDOM_WAVE_MAX_SHAPE_POWER - RANDOM_WAVE_MIN_SHAPE_POWER);
                int startIndex = Math.max(0, (int) Math.floor(center - span));
                int endIndex = Math.min(barCount - 1, (int) Math.ceil(center + span));
                for (int index = startIndex; index <= endIndex; index++) {
                    float distanceRatio = Math.abs(index - center) / span;
                    float falloff = 1f - coerceIn(distanceRatio, 0f, 1f);
                    float profile = strength * (float) Math.pow(falloff, shapePower);
                    targetBarProfile[index] = Math.max(targetBarProfile[index], profile);
                }
            }
        }

        private float pickCenterBiasedBarIndex(int barCount) {
            if (barCount <= 1) {
                return 0f;
            }
            float maxIndex = barCount - 1f;
            float centerIndex = maxIndex / 2f;
            if (visualizerRandom.nextFloat() < RANDOM_WAVE_CENTER_BIAS_PROBABILITY) {
                float offset = (float) visualizerRandom.nextGaussian()
                    * barCount
                    * RANDOM_WAVE_CENTER_STANDARD_DEVIATION_RATIO;
                return coerceIn(centerIndex + offset, 0f, maxIndex);
            }
            return visualizerRandom.nextFloat() * maxIndex;
        }

        private void animatePanelColors(
            int targetPanelColor,
            int targetPanelCenterColor,
            int targetPromptColor
        ) {
            if (colorAnimator != null) {
                colorAnimator.cancel();
            }
            final int startPanelColor = currentPanelColor;
            final int startPanelCenterColor = currentPanelCenterColor;
            final int startPromptColor = currentPromptColor;
            final ArgbEvaluator evaluator = new ArgbEvaluator();
            colorAnimator = ValueAnimator.ofFloat(0f, 1f);
            colorAnimator.setDuration(COLOR_ANIMATION_DURATION_MS);
            colorAnimator.setInterpolator(new DecelerateInterpolator());
            colorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animator) {
                    float fraction = animator.getAnimatedFraction();
                    currentPanelColor = (Integer) evaluator.evaluate(
                        fraction,
                        startPanelColor,
                        targetPanelColor
                    );
                    currentPanelCenterColor = (Integer) evaluator.evaluate(
                        fraction,
                        startPanelCenterColor,
                        targetPanelCenterColor
                    );
                    currentPromptColor = (Integer) evaluator.evaluate(
                        fraction,
                        startPromptColor,
                        targetPromptColor
                    );
                    invalidate();
                }
            });
            colorAnimator.start();
        }

        private void updatePanelBounds() {
            panelRect.left = panelHorizontalMargin;
            panelRect.right = getWidth() - panelHorizontalMargin;
            float bottom = resolvePanelBottom();
            float top = bottom - panelHeight;
            panelRect.top = top;
            panelRect.bottom = bottom;
        }

        private float resolvePanelBottom() {
            View anchorView = panelAnchorView;
            if (anchorView == null
                || anchorView.getWidth() <= 0
                || anchorView.getHeight() <= 0
                || getHeight() <= 0) {
                return getHeight() - panelBottomOffset;
            }
            anchorView.getLocationOnScreen(anchorLocationOnScreen);
            getLocationOnScreen(canvasLocationOnScreen);
            float anchorCenterY = anchorLocationOnScreen[1]
                - canvasLocationOnScreen[1]
                + anchorView.getHeight() / 2f;
            float top = anchorCenterY - panelHeight / 2f;
            float clampedTop = coerceIn(top, 0f, Math.max(0f, getHeight() - panelHeight));
            return clampedTop + panelHeight;
        }

        private boolean isPointInsideRoundedRect(float x, float y) {
            if (!panelRect.contains(x, y)) {
                return false;
            }
            float radius = Math.min(panelCornerRadius, panelRect.height() / 2f);
            float leftCenterX = panelRect.left + radius;
            float rightCenterX = panelRect.right - radius;
            float topCenterY = panelRect.top + radius;
            float bottomCenterY = panelRect.bottom - radius;
            float checkX;
            if (x < leftCenterX) {
                checkX = leftCenterX;
            } else if (x > rightCenterX) {
                checkX = rightCenterX;
            } else {
                checkX = x;
            }
            float checkY;
            if (y < topCenterY) {
                checkY = topCenterY;
            } else if (y > bottomCenterY) {
                checkY = bottomCenterY;
            } else {
                checkY = y;
            }
            float dx = x - checkX;
            float dy = y - checkY;
            return dx * dx + dy * dy <= radius * radius;
        }

        private int withAlpha(int color, int alpha) {
            return (color & 0x00FFFFFF) | (coerceIn(alpha, 0, 255) << 24);
        }
    }

    private static class NewVoiceAmplitudeRecorder {

        private final Context context;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private MediaRecorder recorder;
        private File outputFile;
        private VolumeChangedListener onVolumeChanged;

        private final Runnable sampleRunnable = new Runnable() {
            @Override
            public void run() {
                int amplitude;
                try {
                    amplitude = recorder == null ? 0 : recorder.getMaxAmplitude();
                } catch (RuntimeException ignored) {
                    amplitude = 0;
                }
                if (onVolumeChanged != null) {
                    onVolumeChanged.onVolumeChanged(normalizeAmplitude(amplitude));
                }
                handler.postDelayed(this, SAMPLE_INTERVAL_MS);
            }
        };

        NewVoiceAmplitudeRecorder(Context context) {
            this.context = context;
        }

        boolean start(VolumeChangedListener onVolumeChanged) {
            stop();
            this.onVolumeChanged = onVolumeChanged;
            File tempFile;
            try {
                tempFile = File.createTempFile(RECORD_FILE_PREFIX, RECORD_FILE_SUFFIX, context.getCacheDir());
            } catch (IOException ignored) {
                return false;
            }
            outputFile = tempFile;

            try {
                MediaRecorder mediaRecorder = new MediaRecorder();
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setAudioEncodingBitRate(AUDIO_BIT_RATE);
                mediaRecorder.setAudioSamplingRate(AUDIO_SAMPLE_RATE);
                mediaRecorder.setOutputFile(tempFile.getAbsolutePath());
                mediaRecorder.prepare();
                mediaRecorder.start();
                recorder = mediaRecorder;
                handler.post(sampleRunnable);
                return true;
            } catch (IOException ignored) {
                releaseRecorder();
                return false;
            } catch (SecurityException ignored) {
                releaseRecorder();
                return false;
            } catch (RuntimeException ignored) {
                releaseRecorder();
                return false;
            }
        }

        void stop() {
            handler.removeCallbacks(sampleRunnable);
            try {
                if (recorder != null) {
                    recorder.stop();
                }
            } catch (RuntimeException ignored) {
                // Stopping can fail when the session is too short to produce audio data.
            }
            releaseRecorder();
        }

        private int normalizeAmplitude(int amplitude) {
            if (amplitude <= 0) {
                return MIN_VOLUME;
            }
            float normalized = (float) Math.sqrt(
                Math.min(amplitude, MAX_RAW_AMPLITUDE) / (float) MAX_RAW_AMPLITUDE
            );
            return coerceIn((int) (normalized * MAX_VOLUME), MIN_VOLUME, MAX_VOLUME);
        }

        private void releaseRecorder() {
            if (recorder != null) {
                recorder.reset();
                recorder.release();
                recorder = null;
            }
            if (outputFile != null) {
                outputFile.delete();
                outputFile = null;
            }
            onVolumeChanged = null;
        }
    }

    private static int coerceIn(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float coerceIn(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final int RECORD_AUDIO_REQUEST_CODE = 4072;
    private static final int MIN_VOLUME = 0;
    private static final int MAX_VOLUME = 100;
    private static final int MAX_RAW_AMPLITUDE = 32767;
    private static final long SAMPLE_INTERVAL_MS = 35L;
    private static final long WAVE_DURATION_MS = 743L;
    private static final long COLOR_ANIMATION_DURATION_MS = 180L;
    private static final long MODE_CHANGE_VIBRATION_DURATION_MS = 20L;
    private static final int AUDIO_BIT_RATE = 64000;
    private static final int AUDIO_SAMPLE_RATE = 44100;
    private static final String RECORD_FILE_PREFIX = "pic_voice_record_";
    private static final String RECORD_FILE_SUFFIX = ".m4a";
    private static final int MIN_BAR_COUNT = 24;
    private static final float FULL_CIRCLE = (float) (Math.PI * 2);
    private static final int RANDOM_WAVE_MAX_HOTSPOTS = 9;
    private static final long RANDOM_WAVE_PROFILE_INTERVAL_MS = 150L;
    private static final float RANDOM_WAVE_PROFILE_EASING = 0.72f;
    private static final float RANDOM_WAVE_SILENCE_THRESHOLD = 0.04f;
    private static final float RANDOM_WAVE_COUNT_VOLUME_POWER = 0.72f;
    private static final float RANDOM_WAVE_CENTER_BIAS_PROBABILITY = 0.78f;
    private static final float RANDOM_WAVE_CENTER_STANDARD_DEVIATION_RATIO = 0.22f;
    private static final float RANDOM_WAVE_MIN_BAR_SPAN = 1.1f;
    private static final float RANDOM_WAVE_MAX_BAR_SPAN = 2.8f;
    private static final float RANDOM_WAVE_MIN_STRENGTH = 0.48f;
    private static final float RANDOM_WAVE_MAX_STRENGTH = 1f;
    private static final float RANDOM_WAVE_MIN_SHAPE_POWER = 0.72f;
    private static final float RANDOM_WAVE_MAX_SHAPE_POWER = 1.65f;
    private static final float VOLUME_EASING = 0.82f;
    private static final float VISUALIZER_VOLUME_AMPLITUDE_MULTIPLIER = 2.15f;
    private static final float FINGER_EASING = 0.22f;
    private static final float VISUALIZER_WIDTH_RATIO = 0.64f;
}
