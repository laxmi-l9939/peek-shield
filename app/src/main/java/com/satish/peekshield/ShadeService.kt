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

    // साफ़ एरिया के चारों तरफ के 4 व्यू
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            0, 0,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun initOverlay() {
        val displayMetrics = Resources.getSystem().displayMetrics
        val w = displayMetrics.widthPixels
        val h = displayMetrics.heightPixels

        val pw = (w * 0.85f).toInt()
        val ph = (h * 0.30f).toInt()
        val left = (w - pw) / 2
        val top = (h - ph) / 2
        peekRect = Rect(left, top, left + pw, top + ph)

        createMaskViews()
        updateMaskLayouts()
        applyAlpha()
    }

    private fun createMaskViews() {
        // 1. ऊपर का मास्क (इसमें ड्रैग, एग्जिट और ब्राइटनेस/अल्फा कंट्रोलर है)
        topMask = FrameLayout(this).apply {
            val dragHandle = TextView(context).apply {
                text = "⠿  Drag"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.argb(120, 50, 50, 50))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(4), dp(12), dp(4))
            }
            val dragLp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(28),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
            addView(dragHandle, dragLp)

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

            // बंद करने का बटन
            val exitBtn = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setBackgroundColor(Color.argb(120, 0, 0, 0))
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }
            val exitLp = FrameLayout.LayoutParams(
                dp(32), dp(32),
                Gravity.TOP or Gravity.START
            ).apply {
                topMargin = dp(24)
                marginStart = dp(12)
            }
            addView(exitBtn, exitLp)
            exitBtn.setOnClickListener { stopSelf() }

            // डार्कनेस स्लाइडर
            val slider = SeekBar(context).apply {
                max = 255
                progress = maskAlpha
            }
            val sliderLp = FrameLayout.LayoutParams(
                dp(110), dp(32),
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = dp(24)
                marginEnd = dp(12)
            }
            addView(slider, sliderLp)

            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    maskAlpha = progress.coerceIn(50, 255)
                    applyAlpha()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        // 2. नीचे का मास्क (इसमें साइज़ बदलने का बटन है)
        bottomMask = FrameLayout(this).apply {
            val resizeHandle = TextView(context).apply {
                text = "⤡  Resize"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.argb(120, 50, 50, 50))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }
            val resizeLp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(26),
                Gravity.TOP or Gravity.END
            )
            addView(resizeHandle, resizeLp)

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
        }

        // 3. बायाँ और दायाँ मास्क
        leftMask = View(this)
        rightMask = View(this)

        windowManager.addView(topMask, createLayoutParams())
        windowManager.addView(bottomMask, createLayoutParams())
        windowManager.addView(leftMask, createLayoutParams())
        windowManager.addView(rightMask, createLayoutParams())
    }

    private fun movePeekBy(dx: Float, dy: Float) {
        val displayMetrics = Resources.getSystem().displayMetrics
        val w = displayMetrics.widthPixels
        val h = displayMetrics.heightPixels

        val currentW = peekRect.width()
        val currentH = peekRect.height()

        var left = peekRect.left + dx.toInt()
        var top = peekRect.top + dy.toInt()

        left = left.coerceIn(0, w - currentW)
        top = top.coerceIn(dp(60), h - currentH)

        peekRect = Rect(left, top, left + currentW, top + currentH)
        updateMaskLayouts()
    }

    private fun resizePeekBy(dx: Float, dy: Float) {
        val displayMetrics = Resources.getSystem().displayMetrics
        val w = displayMetrics.widthPixels
        val h = displayMetrics.heightPixels

        var right = peekRect.right + dx.toInt()
        var bottom = peekRect.bottom + dy.toInt()

        right = right.coerceIn(peekRect.left + minPeekPx, w)
        bottom = bottom.coerceIn(peekRect.top + minPeekPx, h)

        peekRect = Rect(peekRect.left, peekRect.top, right, bottom)
        updateMaskLayouts()
    }

    private fun updateMaskLayouts() {
        val displayMetrics = Resources.getSystem().displayMetrics
        val w = displayMetrics.widthPixels
        val h = displayMetrics.heightPixels

        updateViewBounds(topMask, 0, 0, w, peekRect.top.coerceAtLeast(0))

        val bottomHeight = (h - peekRect.bottom).coerceAtLeast(0)
        updateViewBounds(bottomMask, 0, peekRect.bottom, w, bottomHeight)

        val middleHeight = peekRect.height().coerceAtLeast(0)
        updateViewBounds(leftMask, 0, peekRect.top, peekRect.left.coerceAtLeast(0), middleHeight)

        val rightWidth = (w - peekRect.right).coerceAtLeast(0)
        updateViewBounds(rightMask, peekRect.right, peekRect.top, rightWidth, middleHeight)
    }

    private fun updateViewBounds(view: View?, x: Int, y: Int, width: Int, height: Int) {
        view ?: return
        val lp = (view.layoutParams as WindowManager.LayoutParams).apply {
            this.x = x
            this.y = y
            this.width = width
            this.height = height
        }
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
            nm.createNotificationChannel(channel)
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
 */
class ShadeService : Service() {

    companion object {
        const val CHANNEL_ID = "peek_shield_channel"
        const val NOTIF_ID = 421
        const val ACTION_STOP = "com.satish.peekshield.STOP"
        private const val MIN_PEEK_DP = 80f
    }

    private lateinit var windowManager: WindowManager
    private var maskView: MaskView? = null
    private var clearView: View? = null
    private var controlsView: View? = null

    private var peekRect: Rect = Rect()
    private var maskAlpha: Int = 255

    private val density: Float by lazy { Resources.getSystem().displayMetrics.density }
    private val minPeekPx: Int by lazy { (MIN_PEEK_DP * density).toInt() }

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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
    }

    private fun baseLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun initOverlay() {
        val w = Resources.getSystem().displayMetrics.widthPixels
        val h = Resources.getSystem().displayMetrics.heightPixels
        val pw = (w * 0.6f).toInt()
        val ph = (h * 0.35f).toInt()
        val left = (w - pw) / 2
        val top = (h - ph) / 2
        peekRect = Rect(left, top, left + pw, top + ph)

        addMaskView()
        addClearView()
        addControlsView()
        applyAlpha()
    }

    private fun addMaskView() {
        val v = MaskView(this)
        v.setPeekRect(peekRect)
        v.setAlpha(1f)
        maskView = v

        val lp = baseLayoutParams().apply {
            gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(v, lp)
    }

    private fun addClearView() {
        val v = View(this)
        clearView = v

        val lp = baseLayoutParams().apply {
            x = peekRect.left
            y = peekRect.top
            width = peekRect.width()
            height = peekRect.height()
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(v, lp)
    }

    private fun addControlsView() {
        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        val dragHandle = TextView(this).apply {
            text = "⠿"
            setTextColor(Color.argb(180, 255, 255, 255))
            setBackgroundColor(Color.argb(60, 0, 0, 0))
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        val dragLp = FrameLayout.LayoutParams(
            dp(40), dp(28),
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { topMargin = -dp(14) }
        container.addView(dragHandle, dragLp)

        val resizeHandle = TextView(this).apply {
            text = "⤡"
            setTextColor(Color.argb(200, 255, 255, 255))
            setBackgroundColor(Color.argb(80, 0, 0, 0))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        val resizeLp = FrameLayout.LayoutParams(
            dp(28), dp(28),
            Gravity.BOTTOM or Gravity.END
        ).apply {
            bottomMargin = -dp(14)
            marginEnd = -dp(14)
        }
        container.addView(resizeHandle, resizeLp)

        val slider = SeekBar(this).apply {
            max = 255
            progress = maskAlpha
            rotation = 270f
        }
        val sliderLp = FrameLayout.LayoutParams(
            dp(180), dp(28),
            Gravity.END or Gravity.CENTER_VERTICAL
        ).apply { marginEnd = -dp(90) }
        container.addView(slider, sliderLp)

        val exitBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.argb(120, 0, 0, 0))
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val exitLp = FrameLayout.LayoutParams(
            dp(32), dp(32),
            Gravity.TOP or Gravity.START
        ).apply {
            topMargin = -dp(16)
            marginStart = -dp(16)
        }
        container.addView(exitBtn, exitLp)

        val lp = baseLayoutParams().apply {
            x = peekRect.left
            y = peekRect.top
            width = peekRect.width()
            height = peekRect.height()
            gravity = Gravity.TOP or Gravity.START
        }

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
                maskAlpha = progress.coerceIn(0, 255)
                applyAlpha()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        exitBtn.setOnClickListener {
            stopSelf()
        }

        controlsView = container
        windowManager.addView(container, lp)
    }

    private var lastDragX = 0f
    private var lastDragY = 0f
    private var lastResizeX = 0f
    private var lastResizeY = 0f

    private fun movePeekBy(dx: Float, dy: Float) {
        val w = Resources.getSystem().displayMetrics.widthPixels
        val h = Resources.getSystem().displayMetrics.heightPixels
        var left = peekRect.left + dx.toInt()
        var top = peekRect.top + dy.toInt()
        left = left.coerceIn(0, w - peekRect.width())
        top = top.coerceIn(0, h - peekRect.height())
        peekRect = Rect(left, top, left + peekRect.width(), top + peekRect.height())
        updateViews()
    }

    private fun resizePeekBy(dx: Float, dy: Float) {
        var right = peekRect.right + dx.toInt()
        var bottom = peekRect.bottom + dy.toInt()
        val w = Resources.getSystem().displayMetrics.widthPixels
        val h = Resources.getSystem().displayMetrics.heightPixels
        right = right.coerceIn(peekRect.left + minPeekPx, w)
        bottom = bottom.coerceIn(peekRect.top + minPeekPx, h)
        peekRect = Rect(peekRect.left, peekRect.top, right, bottom)
        updateViews()
    }

    private fun updateViews() {
        maskView?.setPeekRect(peekRect)
        updateViewLayout(clearView, peekRect.left, peekRect.top, peekRect.width(), peekRect.height())
        updateViewLayout(controlsView, peekRect.left, peekRect.top, peekRect.width(), peekRect.height())
    }

    private fun updateViewLayout(view: View?, x: Int, y: Int, w: Int, h: Int) {
        view ?: return
        val lp = (view.layoutParams as WindowManager.LayoutParams).apply {
            this.x = x
            this.y = y
            width = w
            height = h
        }
        try {
            windowManager.updateViewLayout(view, lp)
        } catch (_: Exception) { }
    }

    private fun applyAlpha() {
        maskView?.setMaskAlpha(maskAlpha)
    }

    private fun removeOverlay() {
        maskView?.let { runCatching { windowManager.removeView(it) } }
        clearView?.let { runCatching { windowManager.removeView(it) } }
        controlsView?.let { runCatching { windowManager.removeView(it) } }
        maskView = null
        clearView = null
        controlsView = null
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
            nm.createNotificationChannel(channel)
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
