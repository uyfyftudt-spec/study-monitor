package com.example.core

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FloatingTimerManager(private val context: Context) {
    private val _isShowing = MutableStateFlow(false)
    val isShowing: StateFlow<Boolean> = _isShowing.asStateFlow()

    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime: StateFlow<Long> = _elapsedTime.asStateFlow()

    private var windowManager: WindowManager? = null
    private var floatingView: FrameLayout? = null
    private var timerTextView: TextView? = null

    private var currentWidthPx = 0
    private var currentHeightPx = 0

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun toggleTimer() {
        if (_isShowing.value) {
            hideTimer()
        } else {
            showTimer()
        }
    }

    fun showTimer() {
        scope.launch(Dispatchers.Main) {
            showTimerInternal()
        }
    }

    private fun showTimerInternal() {
        if (!Settings.canDrawOverlays(context)) return

        _elapsedTime.value = 0L

        // Clean up any existing floating view before recreating
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // View might not be attached
            }
        }
        floatingView = null
        timerTextView = null

        _isShowing.value = true

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        currentWidthPx = dpToPx(context, 220)
        currentHeightPx = dpToPx(context, 110)

        val frame = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dpToPx(context, 18).toFloat()
            }
        }
        floatingView = frame

        val tv = TextView(context).apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
            text = formatTime(_elapsedTime.value)
        }
        timerTextView = tv

        frame.addView(
            tv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        val resizeHandle = ResizeHandleView(context)
        val handleSize = dpToPx(context, 28)
        val handleParams = FrameLayout.LayoutParams(handleSize, handleSize, Gravity.BOTTOM or Gravity.END)
        frame.addView(resizeHandle, handleParams)

        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTextSize()
        }

        val params = WindowManager.LayoutParams(
            currentWidthPx,
            currentHeightPx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        resizeHandle.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.tag = floatArrayOf(event.rawX, event.rawY, currentWidthPx.toFloat(), currentHeightPx.toFloat())
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val tag = view.tag as? FloatArray ?: return@setOnTouchListener true
                    val dx = event.rawX - tag[0]
                    val dy = event.rawY - tag[1]

                    val minWidth = dpToPx(context, 90)
                    val maxWidth = dpToPx(context, 900)
                    val minHeight = dpToPx(context, 45)
                    val maxHeight = dpToPx(context, 700)

                    val newWidth = (tag[2] + dx).toInt().coerceIn(minWidth, maxWidth)
                    val newHeight = (tag[3] + dy).toInt().coerceIn(minHeight, maxHeight)

                    currentWidthPx = newWidth
                    currentHeightPx = newHeight
                    params.width = currentWidthPx
                    params.height = currentHeightPx
                    try {
                        windowManager?.updateViewLayout(floatingView, params)
                    } catch (e: Exception) {
                        // ignore
                    }

                    updateTextSize()
                    true
                }
                else -> false
            }
        }

        frame.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.tag = floatArrayOf(event.rawX, event.rawY, params.x.toFloat(), params.y.toFloat())
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val tag = view.tag as? FloatArray ?: return@setOnTouchListener true
                    val dx = event.rawX - tag[0]
                    val dy = event.rawY - tag[1]
                    params.x = (tag[2] + dx).toInt()
                    params.y = (tag[3] + dy).toInt()
                    try {
                        windowManager?.updateViewLayout(floatingView, params)
                    } catch (e: Exception) {
                        // ignore
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(frame, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        updateTextSize()
        startCounting()
    }

    fun hideTimer() {
        scope.launch(Dispatchers.Main) {
            hideTimerInternal()
        }
    }

    private fun hideTimerInternal() {
        if (!_isShowing.value) return
        _isShowing.value = false
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // ignore
            }
        }
        floatingView = null
        timerTextView = null
        stopCounting()
    }

    fun resetTimer() {
        scope.launch(Dispatchers.Main) {
            _elapsedTime.value = 0L
            updateView()
        }
    }

    private fun startCounting() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                _elapsedTime.value += 1
                updateView()
            }
        }
    }

    private fun stopCounting() {
        timerJob?.cancel()
    }

    private fun updateView() {
        timerTextView?.text = formatTime(_elapsedTime.value)
        updateTextSize()
    }

    private fun formatTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    private fun updateTextSize() {
        val textView = timerTextView ?: return
        if (currentWidthPx <= 0 || currentHeightPx <= 0) return

        val text = textView.text.toString().ifEmpty { "00:00" }
        val availWidth = (currentWidthPx - dpToPx(context, 24)).coerceAtLeast(10)
        val availHeight = (currentHeightPx - dpToPx(context, 16)).coerceAtLeast(10)

        val paint = TextPaint(textView.paint)
        var testSize = availHeight * 0.70f
        paint.textSize = testSize
        val measuredWidth = paint.measureText(text)
        if (measuredWidth > availWidth) {
            testSize = (testSize * (availWidth.toFloat() / measuredWidth))
        }
        val finalSize = testSize.coerceIn(16f, 300f)
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, finalSize)
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (context.resources.displayMetrics.density * dp).toInt()
    }
}

private class ResizeHandleView(context: Context) : View(context) {
    private val paint = android.graphics.Paint().apply {
        color = Color.parseColor("#44FFFFFF")
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }
    private val path = android.graphics.Path()

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        path.reset()
        path.moveTo(w, 0f)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        canvas.drawPath(path, paint)
    }
}
