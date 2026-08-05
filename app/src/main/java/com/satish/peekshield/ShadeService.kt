package com.satish.peekshield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat

class ShadeService : Service() {

    companion object {
        const val CHANNEL_ID = "peek_shield_channel"
        const val NOTIF_ID = 421
        const val ACTION_STOP = "com.satish.peekshield.STOP"
        private const val MIN_PEEK_DP = 80f
    }

    private lateinit var windowManager: WindowManager

    private var topMask: FrameLayout? = null
    private var bottomMask: FrameLayout? = null
    private var leftMask: View? = null
    private var rightMask: View? = null

    private var peekRect: Rect = Rect()
    private var maskAlpha: Int = 220

    private val density: Float by lazy { Resources.getSystem().displayMetrics.density }
    private val minPeekPx: Int by lazy { (MIN_PEEK_DP * density).toInt() }

    private var lastDragX = 0f
    private var lastDragY = 0f
    private var lastResizeX = 0f
    private var lastResizeY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIF_ID, buildNotification())
        initOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            0, 0,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun initOverlay() {
        val w = Resources.getSystem().displayMetrics.widthPixels
        val h = Resources.getSystem().displayMetrics.heightPixels
        val pw = (w * 0.85f).toInt()
        val ph = (h * 0.30f).toInt()
        val left = (w - pw) / 2
        val top = (h - ph) / 2
        peekRect = Rect(left, top, left + pw, top + ph)

        addMaskViews()
        updateOverlayLayouts()
        applyAlpha()
    }

    private fun addMaskViews() {
        val topContainer = FrameLayout(this)
        val bottomContainer = FrameLayout(this)

        val dragHandle = TextView(this).apply {
            text = "⠿"
            setTextColor(Color.argb(220, 255, 255, 255))
            setBackgroundColor(Color.argb(120, 0, 0, 0))
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val dragLp = FrameLayout.LayoutParams(dp(44), dp(28)).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        topContainer.addView(dragHandle, dragLp)

        val exitBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.argb(120, 0, 0, 0))
        }
        val exitLp = FrameLayout.LayoutParams(dp(32), dp(32)).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            marginStart = dp(8)
            bottomMargin = dp(2)
        }
        topContainer.addView(exitBtn, exitLp)

        val slider = SeekBar(this).apply {
            max = 255
            progress = maskAlpha
        }
        val sliderLp = FrameLayout.LayoutParams(dp(110), dp(30)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            marginEnd = dp(8)
        }
        topContainer.addView(slider, sliderLp)

        val resizeHandle = TextView(this).apply {
            text = "⤡"
            setTextColor(Color.argb(220, 255, 255, 255))
            setBackgroundColor(Color.argb(120, 0, 0, 0))
            textSize = 16f
            gravity = Gravity.CENTER
        }
        val resizeLp = FrameLayout.LayoutParams(dp(32), dp(32)).apply {
            gravity = Gravity.TOP or Gravity.END
            marginEnd = dp(8)
        }
        bottomContainer.addView(resizeHandle, resizeLp)

        dragHandle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastDragX = e.rawX
                    lastDragY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    movePeekBy(e.rawX - lastDragX, e.rawY - lastDragY)
                    lastDragX = e.rawX
                    lastDragY = e.rawY
                    true
                }
                else -> false
            }
        }

        resizeHandle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastResizeX = e.rawX
                    lastResizeY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    resizePeekBy(e.rawX - lastResizeX, e.rawY - lastResizeY)
                    lastResizeX = e.rawX
                    lastResizeY = e.rawY
                    true
                }
                else -> false
            }
        }

        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                maskAlpha = progress.coerceIn(30, 255)
                applyAlpha()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        exitBtn.setOnClickListener {
            stopSelf()
        }

        topMask = topContainer
        bottomMask = bottomContainer
        leftMask = View(this)
        rightMask = View(this)

        windowManager.addView(topMask, createLayoutParams())
        windowManager.addView(bottomMask, createLayoutParams())
        windowManager.addView(leftMask, createLayoutParams())
        windowManager.addView(rightMask, createLayoutParams())
    }

    private fun movePeekBy(dx: Float, dy: Float) {
        val w = Resources.getSystem().displayMetrics.widthPixels
        val h = Resources.getSystem().displayMetrics.heightPixels
        var left = peekRect.left + dx.toInt()
        var top = peekRect.top + dy.toInt()
        left = left.coerceIn(0, (w - peekRect.width()).coerceAtLeast(0))
        top = top.coerceIn(0, (h - peekRect.height()).coerceAtLeast(0))
        peekRect = Rect(left, top, left + peekRect.width(), top + peekRect.height())
        updateOverlayLayouts()
    }

    private fun resizePeekBy(dx: Float, dy: Float) {
        val w = Resources.getSystem().displayMetrics.widthPixels
        val h = Resources.getSystem().displayMetrics.heightPixels
        var right = peekRect.right + dx.toInt()
        var bottom = peekRect.bottom + dy.toInt()
        right = right.coerceIn(peekRect.left + minPeekPx, w)
        bottom = bottom.coerceIn(peekRect.top + minPeekPx, h)
        peekRect = Rect(peekRect.left, peekRect.top, right, bottom)
        updateOverlayLayouts()
    }

    private fun updateOverlayLayouts() {
        val w = Resources.getSystem().displayMetrics.widthPixels
        val h = Resources.getSystem().displayMetrics.heightPixels

        updateViewBounds(topMask, 0, 0, w, peekRect.top.coerceAtLeast(0))
        updateViewBounds(bottomMask, 0, peekRect.bottom, w, (h - peekRect.bottom).coerceAtLeast(0))
        updateViewBounds(leftMask, 0, peekRect.top, peekRect.left.coerceAtLeast(0), peekRect.height().coerceAtLeast(0))
        updateViewBounds(rightMask, peekRect.right, peekRect.top, (w - peekRect.right).coerceAtLeast(0), peekRect.height().coerceAtLeast(0))
    }

    private fun updateViewBounds(view: View?, x: Int, y: Int, width: Int, height: Int) {
        view ?: return
        val lp = view.layoutParams as? WindowManager.LayoutParams ?: return
        lp.x = x
        lp.y = y
        lp.width = width
        lp.height = height
        try {
            windowManager.updateViewLayout(view, lp)
        } catch (_: Exception) { }
    }

    private fun applyAlpha() {
        val color = Color.argb(maskAlpha, 0, 0, 0)
        topMask?.setBackgroundColor(color)
        bottomMask?.setBackgroundColor(color)
        leftMask?.setBackgroundColor(color)
        rightMask?.setBackgroundColor(color)
    }

    private fun removeOverlay() {
        topMask?.let { runCatching { windowManager.removeView(it) } }
        bottomMask?.let { runCatching { windowManager.removeView(it) } }
        leftMask?.let { runCatching { windowManager.removeView(it) } }
        rightMask?.let { runCatching { windowManager.removeView(it) } }

        topMask = null
        bottomMask = null
        leftMask = null
        rightMask = null
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), Resources.getSystem().displayMetrics
        ).toInt()

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

