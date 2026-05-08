package com.adwa.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.animation.ValueAnimator
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.math.*
import kotlin.concurrent.thread

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var eyeView: EyeView
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private var moveRunnable: Runnable? = null
    private var blinkRunnable: Runnable? = null
    private var pollRunnable: Runnable? = null

    private var solPrice = 0.0
    private var solChange = 0.0
    private var equity = 0.0
    private var direction = "flat"
    private var battery = 100
    private var temperature = 0.0

    companion object {
        const val CHANNEL_ID = "adwa_overlay"
        const val NOTIFICATION_ID = 4242
        const val ACTION_STOP = "com.adwa.companion.STOP_OVERLAY"
        const val API_URL = "http://129.80.112.9/adwa-status.json"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopOverlay()
            return START_NOT_STICKY
        }
        showOverlay()
        startPolling()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopOverlay()
        isRunning = false
        super.onDestroy()
    }

    private fun showOverlay() {
        try {
            eyeView = EyeView(this)
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 60
            params.y = 300
            wm.addView(eyeView, params)
            startAnimations()
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun stopOverlay() {
        try {
            handler.removeCallbacksAndMessages(null)
            if (::eyeView.isInitialized && eyeView.parent != null) {
                wm.removeView(eyeView)
            }
        } catch (_: Exception) {}
    }

    private fun startAnimations() {
        blinkRunnable = object : Runnable {
            override fun run() {
                eyeView.blink()
                handler.postDelayed(this, 3000 + Random().nextInt(4000).toLong())
            }
        }
        handler.postDelayed(blinkRunnable!!, 2000)

        moveRunnable = object : Runnable {
            override fun run() {
                val screenW = wm.currentWindowMetrics.bounds.width()
                val screenH = wm.currentWindowMetrics.bounds.height()
                val size = 160
                val targetX = size / 2 + Random().nextInt(maxOf(1, screenW - size))
                val targetY = size / 2 + Random().nextInt(maxOf(1, screenH - size - 120))

                val startX = params.x
                val startY = params.y

                val steps = 60
                val delay = 12L
                for (i in 0..steps) {
                    val frac = i.toFloat() / steps
                    val eased = frac * frac * (3 - 2 * frac) // smoothstep
                    handler.postDelayed({
                        params.x = (startX + (targetX - startX) * eased).toInt()
                        params.y = (startY + (targetY - startY) * eased).toInt()
                        eyeView.setPupilOffset(sin(eased * PI).toFloat() * 0.4f)
                        try { wm.updateViewLayout(eyeView, params) } catch (_: Exception) {}
                    }, delay * i)
                }

                handler.postDelayed(this, 20000 + Random().nextInt(15000).toLong())
            }
        }
        handler.postDelayed(moveRunnable!!, 4000)
    }

    private fun startPolling() {
        // Fetch immediately, then every 30s
        fetchData()
        pollRunnable = object : Runnable {
            override fun run() {
                fetchData()
                handler.postDelayed(this, 30000)
            }
        }
        handler.postDelayed(pollRunnable!!, 30000)
    }

    private fun fetchData() {
        thread {
            try {
                val conn = URL(API_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val json = JSONObject(text)
                val mkts = json.optJSONObject("markets") ?: return@thread
                val sol = mkts.optJSONObject("sol") ?: return@thread
                val pf = json.optJSONObject("portfolio") ?: return@thread
                val sys = json.optJSONObject("system") ?: return@thread

                solPrice = sol.optDouble("price", 0.0)
                solChange = sol.optDouble("change_24h", 0.0)
                equity = pf.optDouble("equity", 0.0)
                direction = sol.optString("direction", "flat")
                battery = sys.optInt("battery", 100)
                temperature = sys.optDouble("temperature", 0.0)

                handler.post {
                    eyeView.updateGlow(direction)
                    eyeView.invalidate()
                }
            } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "ADWA Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps ADWA running on your screen" }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val openIntent = Intent(this, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ADWA")
            .setContentText("Eye is watching • SOL: $$solPrice")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPi)
            .setContentIntent(openPi)
            .build()
    }

    // ─── Eye View ────────────────────────────────────────────────

    inner class EyeView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var blinkAmount = 0f
        private var pupilOffset = 0f
        private var glowColor = 0xFF00c853.toInt()
        private val baseColor = Color.parseColor("#07070d")

        private var isDragging = false
        private var dragStartX = 0f
        private var dragStartY = 0f
        private var initX = 0
        private var initY = 0
        private var lastTapTime = 0L

        // Data popup state
        private var showPopup = false
        private var popupAlpha = 0f
        private var popupFadeRunnable: Runnable? = null

        init {
            setBackgroundColor(Color.TRANSPARENT)
        }

        private fun showDataPopup() {
            popupFadeRunnable?.let { handler.removeCallbacks(it) }
            showPopup = true
            popupAlpha = 1f
            invalidate()
            // Auto-hide after 2.5 seconds
            popupFadeRunnable = Runnable {
                showPopup = false
                popupAlpha = 0f
                invalidate()
            }
            handler.postDelayed(popupFadeRunnable!!, 2500)
        }

        fun blink() {
            blinkAmount = 0f
            val anim = ValueAnimator.ofFloat(0f, 1f, 0f)
            anim.duration = 150
            anim.addUpdateListener {
                blinkAmount = it.animatedValue as Float
                invalidate()
            }
            anim.start()
        }

        fun setPupilOffset(offset: Float) {
            pupilOffset = offset
        }

        fun updateGlow(dir: String) {
            glowColor = when (dir) {
                "up" -> Color.parseColor("#00c853")
                "down" -> Color.parseColor("#ff1744")
                else -> Color.parseColor("#ffab00")
            }
        }

        private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val size = 160
            setMeasuredDimension(size, size)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val rx = width * 0.36f
            val ry = (width * 0.32f) * (1f - blinkAmount * 0.7f)

            // Glow ring
            paint.style = Paint.Style.FILL
            paint.color = glowColor
            paint.alpha = (25 + blinkAmount * 15).toInt()
            paint.maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawOval(RectF(cx - rx * 1.2f, cy - ry * 1.2f, cx + rx * 1.2f, cy + ry * 1.2f), paint)
            paint.maskFilter = null

            // Outer ring
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.alpha = 60
            canvas.drawCircle(cx, cy, rx * 1.15f, paint)

            // Sclera
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(240, 255, 255, 255)
            paint.alpha = 240
            canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)

            // Iris
            val irisRx = rx * 0.6f
            val irisRy = ry * 0.6f * (1f - blinkAmount * 0.5f)
            val pupilX = cx + pupilOffset * rx * 0.25f
            paint.color = Color.parseColor("#1a1a2e")
            paint.alpha = 255
            canvas.drawOval(RectF(pupilX - irisRx, cy - irisRy, pupilX + irisRx, cy + irisRy), paint)

            // Pupil
            val pSize = rx * 0.25f * (1f - blinkAmount * 0.3f)
            paint.color = Color.BLACK
            canvas.drawCircle(pupilX, cy, pSize, paint)

            // Highlight
            paint.color = Color.argb(200, 255, 255, 255)
            canvas.drawCircle(pupilX + rx * 0.12f, cy - ry * 0.18f, rx * 0.1f, paint)
            paint.alpha = 180
            canvas.drawCircle(pupilX + rx * 0.06f, cy - ry * 0.22f, rx * 0.05f, paint)

            // Eyelids (blink)
            if (blinkAmount > 0.01f) {
                paint.color = baseColor
                paint.alpha = 255
                val lidH = height * 0.5f * blinkAmount
                canvas.drawRect(RectF(0f, cy - lidH - ry, width.toFloat(), lidH + ry), paint)
                canvas.drawRect(RectF(0f, cy + ry - lidH * 0.5f, width.toFloat(), lidH + ry), paint)
            }

            // Price / data labels (tiny text)
            paint.color = Color.argb(180, 255, 255, 255)
            paint.textSize = spToPx(10f)
            paint.style = Paint.Style.FILL
            paint.typeface = Typeface.MONOSPACE
            paint.maskFilter = null

            val solText = "SOL $$solPrice"
            val eqText = "EQ $$equity"
            val dirIcon = when (direction) {
                "up" -> "▲"
                "down" -> "▼"
                else -> "—"
            }

            paint.color = glowColor
            paint.alpha = 200
            val textY = height - 14f
            canvas.drawText(dirIcon, cx - rx * 0.5f, textY, paint)
            paint.color = Color.argb(180, 255, 255, 255)
            canvas.drawText(solText, cx + 8f, textY - 2f, paint)
            canvas.drawText(eqText, cx + 8f, textY + 14f, paint)
            canvas.drawText("🔋$battery%", cx + 8f, textY + 30f, paint)

            // Data popup on tap
            if (showPopup && popupAlpha > 0.01f) {
                val popupW = 220f
                val popupH = 80f
                val popupX = cx - popupW / 2
                val popupY = -20f // above the eye

                // Background
                paint.style = Paint.Style.FILL
                paint.color = Color.argb((200 * popupAlpha).toInt(), 7, 7, 13)
                paint.maskFilter = null
                val bgRect = RectF(popupX, popupY, popupX + popupW, popupY + popupH)
                canvas.drawRoundRect(bgRect, 12f, 12f, paint)

                // Border
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                paint.color = Color.argb((100 * popupAlpha).toInt(), 
                    Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
                canvas.drawRoundRect(bgRect, 12f, 12f, paint)

                // Text
                paint.style = Paint.Style.FILL
                paint.typeface = Typeface.MONOSPACE
                paint.textSize = spToPx(11f)
                val dirStr = when (direction) { "up" -> "▲ UP" ; "down" -> "▼ DOWN" ; else -> "— FLAT" }
                paint.color = glowColor
                paint.alpha = (220 * popupAlpha).toInt()
                canvas.drawText(dirStr, popupX + 10f, popupY + 20f, paint)
                paint.color = Color.argb((200 * popupAlpha).toInt(), 255, 255, 255)
                canvas.drawText("SOL: $$solPrice", popupX + 10f, popupY + 38f, paint)
                canvas.drawText("Equity: $$equity  🔋$battery%", popupX + 10f, popupY + 54f, paint)
                canvas.drawText("Tap for data • Dbl-tap TG", popupX + 10f, popupY + 72f, paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    initX = params.x
                    initY = params.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - dragStartX
                    val dy = event.rawY - dragStartY
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        isDragging = true
                        params.x = (initX + dx).toInt()
                        params.y = (initY + dy).toInt()
                        try { wm.updateViewLayout(this, params) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 400) {
                            // Double tap → open Telegram
                            lastTapTime = 0
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/AdwaAuditor"))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(intent)
                            } catch (_: Exception) {}
                        } else {
                            lastTapTime = now
                            showDataPopup()
                            blink()
                        }
                    }
                }
            }
            return true
        }
    }
}
