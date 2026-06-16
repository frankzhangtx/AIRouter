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
import android.os.Handler;
import android.os.Looper;
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

public class PicVoiceRecordPanel extends FrameLayout {

    private final PicVoiceRecordCanvasView canvasView;
    private final PicVoiceAmplitudeRecorder recorder;
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

    public PicVoiceRecordPanel(Context context) {
        super(context);
        canvasView = new PicVoiceRecordCanvasView(context);
        recorder = new PicVoiceAmplitudeRecorder(context.getApplicationContext());
        initialize(null);
    }

    public PicVoiceRecordPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        canvasView = new PicVoiceRecordCanvasView(context);
        recorder = new PicVoiceAmplitudeRecorder(context.getApplicationContext());
        initialize(null);
    }

    public PicVoiceRecordPanel(ComponentActivity activity) {
        super(activity);
        canvasView = new PicVoiceRecordCanvasView(activity);
        recorder = new PicVoiceAmplitudeRecorder(activity.getApplicationContext());
        initialize(activity);
    }

    public void setCallback(VoiceRecordCallback callback) {
        this.callback = callback;
    }

    public void bindToHoldTrigger(View trigger) {
        trigger.setOnClickListener(null);
        trigger.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                return handleHoldTriggerTouch(view, event);
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
            case MotionEvent.ACTION_MOVE:
                updateFinger(event.getX(), event.getY(), insideRecordArea);
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

    private boolean handleHoldTriggerTouch(View view, MotionEvent event) {
        pendingStartRawX = event.getRawX();
        pendingStartRawY = event.getRawY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                holdPointerDown = true;
                holdTriggered = false;
                view.setPressed(true);
                requestDisallowParentIntercept(view, true);
                scheduleHoldStart();
                break;
            case MotionEvent.ACTION_MOVE:
                if (recording) {
                    updateFingerFromScreen(event.getRawX(), event.getRawY());
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                boolean shouldCancel = recording ? !updateFingerFromScreen(event.getRawX(), event.getRawY()) : true;
                cancelHoldStart();
                view.setPressed(false);
                requestDisallowParentIntercept(view, false);
                holdPointerDown = false;
                if (recording) {
                    finishRecording(shouldCancel);
                } else if (!holdTriggered) {
                    pendingShowAfterPermission = false;
                    view.performClick();
                }
                holdTriggered = false;
                break;
            case MotionEvent.ACTION_CANCEL:
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
        return true;
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
        canvasView.updateFinger(x, y, active);
        canvasView.setCancelMode(!active);
    }

    private boolean updateFingerFromScreen(float rawX, float rawY) {
        getLocationOnScreen(panelLocationOnScreen);
        float panelX = rawX - panelLocationOnScreen[0];
        float panelY = rawY - panelLocationOnScreen[1];
        boolean insideRecordArea = canvasView.isPointInsideRecordArea(panelX, panelY);
        updateFinger(panelX, panelY, insideRecordArea);
        return insideRecordArea;
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

    private static class PicVoiceRecordCanvasView extends View {

        private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF panelRect = new RectF();
        private final RectF barRect = new RectF();
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
        private final float promptBottomOffset;
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
        private ValueAnimator pulseAnimator;
        private ValueAnimator colorAnimator;

        PicVoiceRecordCanvasView(Context context) {
            super(context);
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
            promptBottomOffset = getResources().getDimension(R.dimen.pic_voice_record_prompt_bottom_offset);
            visualizerMaxWidth = getResources().getDimension(R.dimen.pic_voice_record_visualizer_max_width);
            visualizerBarWidth = getResources().getDimension(R.dimen.pic_voice_record_visualizer_bar_width);
            visualizerBarGap = getResources().getDimension(R.dimen.pic_voice_record_visualizer_bar_gap);
            visualizerMinBarHeight = getResources().getDimension(R.dimen.pic_voice_record_visualizer_min_bar_height);
            visualizerMaxBarHeight = getResources().getDimension(R.dimen.pic_voice_record_visualizer_max_bar_height);
            visualizerBarRadius = getResources().getDimension(R.dimen.pic_voice_record_visualizer_bar_radius);
            hintSend = getResources().getString(R.string.pic_voice_record_hint_send);
            hintCancel = getResources().getString(R.string.voice_record_hint_cancel);

            textPaint.setColor(normalPromptColor);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(getResources().getDimension(R.dimen.pic_voice_record_prompt_text_size));
            currentPanelColor = normalPanelColor;
            currentPanelCenterColor = normalPanelCenterColor;
            currentPromptColor = normalPromptColor;
            promptText = hintSend;
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

        void setCancelMode(boolean cancel) {
            if (cancelMode == cancel) {
                return;
            }
            cancelMode = cancel;
            promptText = cancel ? hintCancel : hintSend;
            animatePanelColors(
                cancel ? cancelPanelColor : normalPanelColor,
                cancel ? cancelPanelCenterColor : normalPanelCenterColor,
                cancel ? cancelPromptColor : normalPromptColor
            );
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

        private void drawPrompt(Canvas canvas) {
            float promptBaseline = getHeight() - promptBottomOffset;
            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
            float centeredBaseline = promptBaseline - (fontMetrics.ascent + fontMetrics.descent) / 2f;
            textPaint.setColor(currentPromptColor);
            canvas.drawText(promptText, getWidth() / 2f, centeredBaseline, textPaint);
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
            float baselineY = panelRect.centerY();

            for (int index = 0; index < barCount; index++) {
                float distanceFromCenter = centerIndex == 0f ? 0f : Math.abs(index - centerIndex) / centerIndex;
                float envelope = coerceIn(1f - distanceFromCenter * 0.72f, 0.24f, 1f);
                float wave = (float) ((Math.sin((wavePhase * FULL_CIRCLE) + index * BAR_PHASE_STEP) + 1f) / 2f);
                float amplifiedVolumeRatio = coerceIn(
                    volumeRatio * VISUALIZER_VOLUME_AMPLITUDE_MULTIPLIER,
                    0f,
                    1f
                );
                float activeHeight = visualizerMinBarHeight
                    + (visualizerMaxBarHeight - visualizerMinBarHeight)
                    * (0.12f + amplifiedVolumeRatio * (0.28f + 0.72f * wave) * envelope);
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
            float left = panelHorizontalMargin;
            float right = getWidth() - panelHorizontalMargin;
            float bottom = getHeight() - panelBottomOffset;
            float top = bottom - panelHeight;
            panelRect.set(left, top, right, bottom);
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

    private static class PicVoiceAmplitudeRecorder {

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

        PicVoiceAmplitudeRecorder(Context context) {
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
    private static final long SAMPLE_INTERVAL_MS = 60L;
    private static final long WAVE_DURATION_MS = 1100L;
    private static final long COLOR_ANIMATION_DURATION_MS = 180L;
    private static final int AUDIO_BIT_RATE = 64000;
    private static final int AUDIO_SAMPLE_RATE = 44100;
    private static final String RECORD_FILE_PREFIX = "pic_voice_record_";
    private static final String RECORD_FILE_SUFFIX = ".m4a";
    private static final int MIN_BAR_COUNT = 24;
    private static final float FULL_CIRCLE = (float) (Math.PI * 2);
    private static final float BAR_PHASE_STEP = 0.42f;
    private static final float VOLUME_EASING = 0.54f;
    private static final float VISUALIZER_VOLUME_AMPLITUDE_MULTIPLIER = 2f;
    private static final float FINGER_EASING = 0.22f;
    private static final float VISUALIZER_WIDTH_RATIO = 0.64f;
}
