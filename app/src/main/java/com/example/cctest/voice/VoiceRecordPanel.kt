package com.example.cctest.voice

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.example.cctest.R
import java.io.File
import java.io.IOException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class VoiceRecordPanel : FrameLayout {

    private val canvasView: VoiceRecordCanvasView
    private val recorder: VoiceAmplitudeRecorder
    private val gestureHandler = Handler(Looper.getMainLooper())
    private val panelLocationOnScreen = IntArray(2)
    private var callback: VoiceRecordCallback? = null
    private var permissionLauncher: ActivityResultLauncher<String>? = null
    private var pendingShowAfterPermission = false
    private var pendingStartRawX: Float? = null
    private var pendingStartRawY: Float? = null
    private var holdStartRunnable: Runnable? = null
    private var holdPointerDown = false
    private var holdTriggered = false
    private var recording = false
    private var ending = false

    constructor(context: Context) : super(context) {
        canvasView = VoiceRecordCanvasView(context)
        recorder = VoiceAmplitudeRecorder(context.applicationContext)
        initialize(null)
    }

    constructor(context: Context, attrs: android.util.AttributeSet?) : super(context, attrs) {
        canvasView = VoiceRecordCanvasView(context)
        recorder = VoiceAmplitudeRecorder(context.applicationContext)
        initialize(null)
    }

    constructor(activity: ComponentActivity) : super(activity) {
        canvasView = VoiceRecordCanvasView(activity)
        recorder = VoiceAmplitudeRecorder(activity.applicationContext)
        initialize(activity)
    }

    fun setCallback(callback: VoiceRecordCallback?) {
        this.callback = callback
    }

    fun bindToHoldTrigger(trigger: View) {
        trigger.setOnClickListener(null)
        trigger.setOnTouchListener { view, event ->
            handleHoldTriggerTouch(view, event)
        }
    }

    fun show() {
        if (parent != null || recording || ending) {
            return
        }
        if (!hasRecordPermission()) {
            requestRecordPermission()
            return
        }
        attachAndStart()
    }

    fun dismiss() {
        pendingShowAfterPermission = false
        cancelHoldStart()
        releaseRecording()
        removeFromParent()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!recording) {
            return true
        }

        val insideRecordArea = canvasView.isPointInsideRecordArea(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                updateFinger(event.x, event.y, insideRecordArea)
            }
            MotionEvent.ACTION_MOVE -> {
                updateFinger(event.x, event.y, insideRecordArea)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                updateFinger(event.x, event.y, insideRecordArea)
                finishRecording(cancelled = !insideRecordArea)
            }
            MotionEvent.ACTION_CANCEL -> {
                updateFinger(event.x, event.y, active = false)
                finishRecording(cancelled = true)
            }
        }
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && recording) {
            finishRecording(cancelled = true)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDetachedFromWindow() {
        cancelHoldStart()
        releaseRecording()
        super.onDetachedFromWindow()
    }

    private fun initialize(activity: ComponentActivity?) {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(
            canvasView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        permissionLauncher = activity?.let(::registerPermissionLauncherIfPossible)
    }

    private fun registerPermissionLauncherIfPossible(
        activity: ComponentActivity
    ): ActivityResultLauncher<String>? {
        return if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            null
        } else {
            activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (pendingShowAfterPermission) {
                    pendingShowAfterPermission = false
                    if (granted) {
                        attachAndStart()
                    } else {
                        callback?.onCancel()
                    }
                }
            }
        }
    }

    private fun attachAndStart() {
        val activity = findActivity(context) ?: return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (parent == null) {
            content.addView(
                this,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        bringToFront()
        requestFocus()
        canvasView.reset()
        canvasView.startAnimating()
        post {
            val startRawX = pendingStartRawX
            val startRawY = pendingStartRawY
            if (startRawX != null && startRawY != null) {
                updateFingerFromScreen(startRawX, startRawY)
            } else {
                canvasView.updateFinger(width / 2f, height.toFloat(), active = true)
            }
            pendingStartRawX = null
            pendingStartRawY = null
        }

        recording = recorder.start { volume ->
            canvasView.updateVolume(volume)
        }
        if (recording) {
            callback?.onStart()
        } else {
            canvasView.stopAnimating()
            removeFromParent()
            callback?.onCancel()
        }
    }

    private fun finishRecording(cancelled: Boolean) {
        if (ending) {
            return
        }
        ending = true
        val wasRecording = recording
        releaseRecording()
        removeFromParent()
        ending = false

        if (wasRecording) {
            if (cancelled) {
                callback?.onCancel()
            } else {
                callback?.onFinish()
            }
        }
    }

    private fun releaseRecording() {
        if (recording) {
            recorder.stop()
        }
        recording = false
        canvasView.stopAnimating()
        canvasView.reset()
    }

    private fun handleHoldTriggerTouch(view: View, event: MotionEvent): Boolean {
        pendingStartRawX = event.rawX
        pendingStartRawY = event.rawY
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                holdPointerDown = true
                holdTriggered = false
                view.isPressed = true
                view.parent?.requestDisallowInterceptTouchEvent(true)
                scheduleHoldStart()
            }
            MotionEvent.ACTION_MOVE -> {
                if (recording) {
                    updateFingerFromScreen(event.rawX, event.rawY)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val shouldCancel = if (recording) {
                    !updateFingerFromScreen(event.rawX, event.rawY)
                } else {
                    true
                }
                cancelHoldStart()
                view.isPressed = false
                view.parent?.requestDisallowInterceptTouchEvent(false)
                holdPointerDown = false
                if (recording) {
                    finishRecording(cancelled = shouldCancel)
                } else if (!holdTriggered) {
                    pendingShowAfterPermission = false
                    view.performClick()
                }
                holdTriggered = false
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelHoldStart()
                view.isPressed = false
                view.parent?.requestDisallowInterceptTouchEvent(false)
                holdPointerDown = false
                if (recording) {
                    finishRecording(cancelled = true)
                } else {
                    pendingShowAfterPermission = false
                }
                holdTriggered = false
            }
        }
        return true
    }

    private fun scheduleHoldStart() {
        cancelHoldStart()
        holdStartRunnable = Runnable {
            if (holdPointerDown && !recording) {
                holdTriggered = true
                show()
            }
        }.also { runnable ->
            gestureHandler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout().toLong())
        }
    }

    private fun cancelHoldStart() {
        holdStartRunnable?.let(gestureHandler::removeCallbacks)
        holdStartRunnable = null
    }

    private fun updateFinger(x: Float, y: Float, active: Boolean) {
        canvasView.updateFinger(x, y, active)
        canvasView.setCancelMode(!active)
    }

    private fun updateFingerFromScreen(rawX: Float, rawY: Float): Boolean {
        getLocationOnScreen(panelLocationOnScreen)
        val panelX = rawX - panelLocationOnScreen[0]
        val panelY = rawY - panelLocationOnScreen[1]
        val insideRecordArea = canvasView.isPointInsideRecordArea(panelX, panelY)
        updateFinger(panelX, panelY, insideRecordArea)
        return insideRecordArea
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRecordPermission() {
        pendingShowAfterPermission = true
        permissionLauncher?.launch(Manifest.permission.RECORD_AUDIO) ?: run {
            findActivity(context)?.let { activity ->
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RECORD_AUDIO_REQUEST_CODE
                )
            }
            pendingShowAfterPermission = false
        }
    }

    private fun removeFromParent() {
        (parent as? ViewGroup)?.removeView(this)
    }

    private fun findActivity(context: Context): Activity? {
        var currentContext = context
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) {
                return currentContext
            }
            currentContext = currentContext.baseContext
        }
        return null
    }

    private class VoiceRecordCanvasView(context: Context) : View(context) {

        private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        private val fingerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.white)
            textAlign = Paint.Align.CENTER
            textSize = resources.getDimension(R.dimen.voice_record_prompt_text_size)
        }
        private val recordPath = Path()
        private val solidRecordPath = Path()
        private val arcFadeBandPath = Path()
        private val barRect = RectF()
        private val normalAreaColor = ContextCompat.getColor(context, R.color.voice_record_area)
        private val normalAreaDeepColor = ContextCompat.getColor(context, R.color.voice_record_area_deep)
        private val cancelAreaColor = ContextCompat.getColor(context, R.color.voice_record_cancel_area)
        private val cancelAreaDeepColor = ContextCompat.getColor(context, R.color.voice_record_cancel_area_deep)
        private val areaTopArcDepth = resources.getDimension(R.dimen.voice_record_area_top_arc_depth)
        private val arcFadeDistance = resources.getDimension(R.dimen.voice_record_arc_fade_distance)
        private val defaultArcAboveVisualizer =
            resources.getDimension(R.dimen.voice_record_default_arc_above_visualizer)
        private val areaMinHeight = resources.getDimension(R.dimen.voice_record_area_min_height)
        private val areaMaxHeight = resources.getDimension(R.dimen.voice_record_area_max_height)
        private val promptBottomOffset = resources.getDimension(R.dimen.voice_record_prompt_bottom_offset)
        private val visualizerBottomOffset = resources.getDimension(R.dimen.voice_record_visualizer_bottom_offset)
        private val visualizerMaxWidth = resources.getDimension(R.dimen.voice_record_visualizer_max_width)
        private val visualizerBarWidth = resources.getDimension(R.dimen.voice_record_visualizer_bar_width)
        private val visualizerBarGap = resources.getDimension(R.dimen.voice_record_visualizer_bar_gap)
        private val visualizerMinBarHeight = resources.getDimension(R.dimen.voice_record_visualizer_min_bar_height)
        private val visualizerMaxBarHeight = resources.getDimension(R.dimen.voice_record_visualizer_max_bar_height)
        private val visualizerBarRadius = resources.getDimension(R.dimen.voice_record_visualizer_bar_radius)
        private val fingerRadius = resources.getDimension(R.dimen.voice_record_finger_radius)
        private val hintSend = resources.getString(R.string.voice_record_hint_send)
        private val hintCancel = resources.getString(R.string.voice_record_hint_cancel)

        private var displayedVolume = 0f
        private var targetVolume = 0f
        private var wavePhase = 0f
        private var currentAreaColor = normalAreaColor
        private var currentDeepColor = normalAreaDeepColor
        private var cancelMode = false
        private var promptText = hintSend
        private var fingerX = 0f
        private var fingerY = 0f
        private var targetFingerX = 0f
        private var targetFingerY = 0f
        private var fingerActiveAmount = 0f
        private var targetFingerActiveAmount = 0f
        private var pulseAnimator: ValueAnimator? = null
        private var colorAnimator: ValueAnimator? = null

        fun reset() {
            displayedVolume = 0f
            targetVolume = 0f
            wavePhase = 0f
            cancelMode = false
            promptText = hintSend
            currentAreaColor = normalAreaColor
            currentDeepColor = normalAreaDeepColor
            fingerX = width / 2f
            fingerY = height.toFloat()
            targetFingerX = fingerX
            targetFingerY = fingerY
            fingerActiveAmount = 0f
            targetFingerActiveAmount = 0f
            colorAnimator?.cancel()
            invalidate()
        }

        fun startAnimating() {
            if (pulseAnimator?.isStarted == true) {
                return
            }
            pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = WAVE_DURATION_MS
                repeatCount = ValueAnimator.INFINITE
                interpolator = null
                addUpdateListener { animator ->
                    wavePhase = animator.animatedValue as Float
                    displayedVolume += (targetVolume - displayedVolume) * VOLUME_EASING
                    fingerX += (targetFingerX - fingerX) * FINGER_EASING
                    fingerY += (targetFingerY - fingerY) * FINGER_EASING
                    fingerActiveAmount += (targetFingerActiveAmount - fingerActiveAmount) * FINGER_EASING
                    invalidate()
                }
                start()
            }
        }

        fun stopAnimating() {
            pulseAnimator?.cancel()
            pulseAnimator = null
            colorAnimator?.cancel()
            colorAnimator = null
        }

        fun updateVolume(volume: Int) {
            targetVolume = volume.coerceIn(MIN_VOLUME, MAX_VOLUME).toFloat()
        }

        fun updateFinger(x: Float, y: Float, active: Boolean) {
            targetFingerX = x.coerceIn(0f, width.toFloat())
            targetFingerY = y.coerceIn(0f, height.toFloat())
            if (fingerX == 0f && fingerY == 0f) {
                fingerX = targetFingerX
                fingerY = targetFingerY
            }
            targetFingerActiveAmount = if (active) 1f else 0f
        }

        fun setCancelMode(cancel: Boolean) {
            if (cancelMode == cancel) {
                return
            }
            cancelMode = cancel
            promptText = if (cancel) hintCancel else hintSend
            animateAreaColor(
                targetAreaColor = if (cancel) cancelAreaColor else normalAreaColor,
                targetDeepColor = if (cancel) cancelAreaDeepColor else normalAreaDeepColor
            )
        }

        fun isPointInsideRecordArea(x: Float, y: Float): Boolean {
            if (width == 0 || height == 0) {
                return false
            }
            return y >= recordTopBoundaryAt(x)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            buildRecordPath()
            drawRecordArea(canvas)
            drawPrompt(canvas)
            drawVisualizer(canvas)
        }

        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            super.onSizeChanged(width, height, oldWidth, oldHeight)
            if (oldWidth == 0 && oldHeight == 0) {
                fingerX = width / 2f
                fingerY = height.toFloat()
                targetFingerX = fingerX
                targetFingerY = fingerY
            }
        }

        private fun drawRecordArea(canvas: Canvas) {
            areaPaint.shader = null
            areaPaint.color = currentAreaColor
            val solidInset = (
                arcFadeDistance -
                    resources.displayMetrics.density * ARC_SOLID_UNDERLAP_DP
                ).coerceAtLeast(0f)
            buildInsetRecordPath(solidRecordPath, solidInset)
            canvas.drawPath(solidRecordPath, areaPaint)
            val highlightRadius = if (fingerActiveAmount > 0.01f) {
                fingerRadius * (0.72f + 0.28f * fingerActiveAmount)
            } else {
                null
            }
            if (fingerActiveAmount > 0.01f) {
                canvas.save()
                canvas.clipPath(solidRecordPath)
                drawFingerHighlightCircle(
                    canvas = canvas,
                    radius = highlightRadius ?: fingerRadius,
                    highlightColor = currentDeepColor,
                    alphaScale = 1f
                )
                canvas.restore()
            }
            drawArcFadeBand(canvas, highlightRadius)
            areaPaint.shader = null
        }

        private fun drawFingerHighlightCircle(
            canvas: Canvas,
            radius: Float,
            highlightColor: Int,
            alphaScale: Float,
        ) {
            fingerPaint.shader = RadialGradient(
                fingerX,
                fingerY,
                radius,
                intArrayOf(
                    withAlpha(highlightColor, (150 * fingerActiveAmount * alphaScale).toInt()),
                    withAlpha(highlightColor, (60 * fingerActiveAmount * alphaScale).toInt()),
                    withAlpha(highlightColor, 0)
                ),
                floatArrayOf(0f, 0.56f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(fingerX, fingerY, radius, fingerPaint)
            fingerPaint.shader = null
        }

        private fun drawArcFadeBand(canvas: Canvas, highlightRadius: Float?) {
            val segmentCount = ARC_FADE_BAND_SEGMENT_COUNT
            val bandOverlap = resources.displayMetrics.density * ARC_FADE_BAND_OVERLAP_DP
            for (segment in 0 until segmentCount) {
                val rawStartOffset = arcFadeDistance * segment / segmentCount
                val rawEndOffset = arcFadeDistance * (segment + 1) / segmentCount
                val startOffset = (rawStartOffset - bandOverlap).coerceAtLeast(0f)
                val endOffset = rawEndOffset + bandOverlap
                val progress = if (segmentCount <= 0) {
                    1f
                } else {
                    ((segment + 0.5f) / segmentCount).coerceIn(0f, 1f)
                }
                val easedProgress = smoothStep(progress)
                val layerAlpha = if (segment == segmentCount - 1) {
                    255
                } else {
                    (255 * easedProgress).toInt()
                }
                val color = lighten(
                    currentAreaColor,
                    ARC_FADE_LIGHTEN_AMOUNT * (1f - easedProgress)
                )
                buildArcFadeBandPath(startOffset, endOffset)
                canvas.save()
                canvas.clipPath(arcFadeBandPath)
                canvas.saveLayerAlpha(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    layerAlpha
                )
                areaPaint.color = color
                canvas.drawPath(arcFadeBandPath, areaPaint)
                highlightRadius?.let { radius ->
                    drawFingerHighlightCircle(
                        canvas = canvas,
                        radius = radius,
                        highlightColor = lighten(
                            currentDeepColor,
                            ARC_FADE_HIGHLIGHT_LIGHTEN_AMOUNT * (1f - easedProgress)
                        ),
                        alphaScale = 1f
                    )
                }
                canvas.restore()
                canvas.restore()
            }
        }

        private fun smoothStep(progress: Float): Float {
            return progress * progress * (3f - 2f * progress)
        }

        private fun drawPrompt(canvas: Canvas) {
            val promptBaseline = height - promptBottomOffset
            val fontMetrics = textPaint.fontMetrics
            val centeredBaseline = promptBaseline - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(promptText, width / 2f, centeredBaseline, textPaint)
        }

        private fun drawVisualizer(canvas: Canvas) {
            val availableWidth = (width - VISUALIZER_HORIZONTAL_PADDING_RATIO * width).coerceAtLeast(0f)
            val visualizerWidth = min(visualizerMaxWidth, availableWidth)
            val slotWidth = visualizerBarWidth + visualizerBarGap
            val barCount = max(MIN_BAR_COUNT, (visualizerWidth / slotWidth).toInt())
            val totalWidth = barCount * visualizerBarWidth + (barCount - 1) * visualizerBarGap
            val startX = (width - totalWidth) / 2f
            val centerIndex = (barCount - 1) / 2f
            val volumeRatio = (displayedVolume / MAX_VOLUME).coerceIn(0f, 1f)
            val baselineY = height - visualizerBottomOffset

            for (index in 0 until barCount) {
                val distanceFromCenter = if (centerIndex == 0f) {
                    0f
                } else {
                    abs(index - centerIndex) / centerIndex
                }
                val envelope = (1f - distanceFromCenter * 0.72f).coerceIn(0.24f, 1f)
                val wave = ((sin((wavePhase * FULL_CIRCLE) + index * BAR_PHASE_STEP) + 1f) / 2f)
                    .toFloat()
                val amplifiedVolumeRatio = (volumeRatio * VISUALIZER_VOLUME_AMPLITUDE_MULTIPLIER)
                    .coerceIn(0f, 1f)
                val activeHeight = visualizerMinBarHeight +
                    (visualizerMaxBarHeight - visualizerMinBarHeight) *
                    (0.12f + amplifiedVolumeRatio * (0.28f + 0.72f * wave) * envelope)
                val x = startX + index * slotWidth
                val top = baselineY - activeHeight / 2f
                val bottom = baselineY + activeHeight / 2f
                val alpha = (130 + 125 * envelope * (0.35f + volumeRatio * 0.65f)).toInt()
                    .coerceIn(120, 255)
                barPaint.color = withAlpha(ContextCompat.getColor(context, R.color.white), alpha)
                barRect.set(x, top, x + visualizerBarWidth, bottom)
                canvas.drawRoundRect(barRect, visualizerBarRadius, visualizerBarRadius, barPaint)
            }
        }

        private fun animateAreaColor(targetAreaColor: Int, targetDeepColor: Int) {
            colorAnimator?.cancel()
            val startAreaColor = currentAreaColor
            val startDeepColor = currentDeepColor
            colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = COLOR_ANIMATION_DURATION_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    val fraction = animator.animatedFraction
                    currentAreaColor = ArgbEvaluator().evaluate(
                        fraction,
                        startAreaColor,
                        targetAreaColor
                    ) as Int
                    currentDeepColor = ArgbEvaluator().evaluate(
                        fraction,
                        startDeepColor,
                        targetDeepColor
                    ) as Int
                    invalidate()
                }
                start()
            }
        }

        private fun buildRecordPath() {
            val centerTopY = recordTopCenterY()
            val edgeTopY = centerTopY + areaTopArcDepth
            recordPath.reset()
            recordPath.moveTo(0f, edgeTopY)
            recordPath.quadTo(width / 2f, centerTopY - areaTopArcDepth, width.toFloat(), edgeTopY)
            recordPath.lineTo(width.toFloat(), height.toFloat())
            recordPath.lineTo(0f, height.toFloat())
            recordPath.close()
        }

        private fun buildInsetRecordPath(path: Path, inset: Float) {
            path.reset()
            path.moveTo(0f, recordTopBoundaryAt(0f) + inset)
            for (segment in 1..ARC_DRAW_SEGMENT_COUNT) {
                val x = width * segment / ARC_DRAW_SEGMENT_COUNT.toFloat()
                path.lineTo(x, recordTopBoundaryAt(x) + inset)
            }
            path.lineTo(width.toFloat(), height.toFloat())
            path.lineTo(0f, height.toFloat())
            path.close()
        }

        private fun buildArcFadeBandPath(startOffset: Float, endOffset: Float) {
            arcFadeBandPath.reset()
            arcFadeBandPath.moveTo(0f, recordTopBoundaryAt(0f) + startOffset)
            for (segment in 1..ARC_DRAW_SEGMENT_COUNT) {
                val x = width * segment / ARC_DRAW_SEGMENT_COUNT.toFloat()
                arcFadeBandPath.lineTo(x, recordTopBoundaryAt(x) + startOffset)
            }
            for (segment in ARC_DRAW_SEGMENT_COUNT downTo 0) {
                val x = width * segment / ARC_DRAW_SEGMENT_COUNT.toFloat()
                arcFadeBandPath.lineTo(x, recordTopBoundaryAt(x) + endOffset)
            }
            arcFadeBandPath.close()
        }

        private fun recordTopCenterY(): Float {
            val maxHeightForScreen = min(areaMaxHeight, height * MAX_AREA_HEIGHT_RATIO)
            val minHeightForScreen = min(areaMinHeight, height * MIN_AREA_HEIGHT_RATIO)
            val defaultHeight = visualizerBottomOffset + defaultArcAboveVisualizer
            val defaultHeightForScreen = defaultHeight.coerceIn(minHeightForScreen, maxHeightForScreen)
            val usableMaxHeight = max(maxHeightForScreen, defaultHeightForScreen)
            val volumeRatio = (displayedVolume / MAX_VOLUME).coerceIn(0f, 1f)
            val areaHeight = defaultHeightForScreen + (usableMaxHeight - defaultHeightForScreen) * volumeRatio
            return height - areaHeight
        }

        private fun recordTopBoundaryAt(x: Float): Float {
            val centerTopY = recordTopCenterY()
            val edgeTopY = centerTopY + areaTopArcDepth
            val progress = if (width == 0) {
                0f
            } else {
                (x / width).coerceIn(0f, 1f)
            }
            return edgeTopY - 4f * areaTopArcDepth * progress * (1f - progress)
        }

        private fun lighten(color: Int, amount: Float): Int {
            val red = ((color shr 16) and 0xFF)
            val green = ((color shr 8) and 0xFF)
            val blue = color and 0xFF
            val lighterRed = red + ((255 - red) * amount).toInt()
            val lighterGreen = green + ((255 - green) * amount).toInt()
            val lighterBlue = blue + ((255 - blue) * amount).toInt()
            return (0xFF shl 24) or (lighterRed shl 16) or (lighterGreen shl 8) or lighterBlue
        }

        private fun withAlpha(color: Int, alpha: Int): Int {
            return (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
        }
    }

    private class VoiceAmplitudeRecorder(private val context: Context) {
        private val handler = Handler(Looper.getMainLooper())
        private var recorder: MediaRecorder? = null
        private var outputFile: File? = null
        private var onVolumeChanged: ((Int) -> Unit)? = null

        private val sampleRunnable = object : Runnable {
            override fun run() {
                val amplitude = try {
                    recorder?.maxAmplitude ?: 0
                } catch (_: RuntimeException) {
                    0
                }
                onVolumeChanged?.invoke(normalizeAmplitude(amplitude))
                handler.postDelayed(this, SAMPLE_INTERVAL_MS)
            }
        }

        fun start(onVolumeChanged: (Int) -> Unit): Boolean {
            stop()
            this.onVolumeChanged = onVolumeChanged
            val tempFile = try {
                File.createTempFile(RECORD_FILE_PREFIX, RECORD_FILE_SUFFIX, context.cacheDir)
            } catch (_: IOException) {
                return false
            }
            outputFile = tempFile

            return try {
                @Suppress("DEPRECATION")
                val mediaRecorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(AUDIO_BIT_RATE)
                    setAudioSamplingRate(AUDIO_SAMPLE_RATE)
                    setOutputFile(tempFile.absolutePath)
                    prepare()
                    start()
                }
                recorder = mediaRecorder
                handler.post(sampleRunnable)
                true
            } catch (_: IOException) {
                releaseRecorder()
                false
            } catch (_: SecurityException) {
                releaseRecorder()
                false
            } catch (_: RuntimeException) {
                releaseRecorder()
                false
            }
        }

        fun stop() {
            handler.removeCallbacks(sampleRunnable)
            try {
                recorder?.stop()
            } catch (_: RuntimeException) {
                // Stopping can fail when the session is too short to produce audio data.
            }
            releaseRecorder()
        }

        private fun normalizeAmplitude(amplitude: Int): Int {
            if (amplitude <= 0) {
                return MIN_VOLUME
            }
            val normalized = sqrt(amplitude.coerceAtMost(MAX_RAW_AMPLITUDE) / MAX_RAW_AMPLITUDE.toFloat())
            return (normalized * MAX_VOLUME).toInt().coerceIn(MIN_VOLUME, MAX_VOLUME)
        }

        private fun releaseRecorder() {
            recorder?.reset()
            recorder?.release()
            recorder = null
            outputFile?.delete()
            outputFile = null
            onVolumeChanged = null
        }
    }

    companion object {
        private const val RECORD_AUDIO_REQUEST_CODE = 4071
        private const val MIN_VOLUME = 0
        private const val MAX_VOLUME = 100
        private const val MAX_RAW_AMPLITUDE = 32767
        private const val SAMPLE_INTERVAL_MS = 60L
        private const val WAVE_DURATION_MS = 1100L
        private const val COLOR_ANIMATION_DURATION_MS = 180L
        private const val AUDIO_BIT_RATE = 64000
        private const val AUDIO_SAMPLE_RATE = 44100
        private const val RECORD_FILE_PREFIX = "voice_record_"
        private const val RECORD_FILE_SUFFIX = ".m4a"
        private const val MIN_BAR_COUNT = 24
        private const val FULL_CIRCLE = (PI * 2).toFloat()
        private const val BAR_PHASE_STEP = 0.42f
        private const val VOLUME_EASING = 0.54f
        private const val VISUALIZER_VOLUME_AMPLITUDE_MULTIPLIER = 2f
        private const val FINGER_EASING = 0.22f
        private const val VISUALIZER_HORIZONTAL_PADDING_RATIO = 0.18f
        private const val MIN_AREA_HEIGHT_RATIO = 0.34f
        private const val MAX_AREA_HEIGHT_RATIO = 0.76f
        private const val ARC_DRAW_SEGMENT_COUNT = 96
        private const val ARC_FADE_BAND_SEGMENT_COUNT = 160
        private const val ARC_FADE_BAND_OVERLAP_DP = 0.75f
        private const val ARC_SOLID_UNDERLAP_DP = 4f
        private const val ARC_FADE_LIGHTEN_AMOUNT = 0.42f
        private const val ARC_FADE_HIGHLIGHT_LIGHTEN_AMOUNT = 0.58f
    }
}
