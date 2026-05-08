package com.adwa.companion

import android.animation.ValueAnimator
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
    private var blinkRunnable: Runnable? = null
    private var pollRunnable: Runnable? = null

    private var solPrice = 0.0
    private var solChange = 0.0
    private var equity = 0.0
    private var cash = 0.0
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
        params.x = 80
        params.y = 400
        wm.addView(eyeView, params)
        startBlinks()
    }

    private fun stopOverlay() {
        try {
            handler.removeCallbacksAndMessages(null)
            if (::eyeView.isInitialized && eyeView.parent != null) {
                wm.removeView(eyeView)
            }
        } catch (_: Exception) {}
    }

    private fun startBlinks() {
        blinkRunnable = object : Runnable {
            override fun run() {
                eyeView.blink()
                handler.postDelayed(this, 3000 + Random().nextInt(4000).toLong())
            }
        }
        handler.postDelayed(blinkRunnable!!, 2000)
    }

    private fun startPolling() {
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
                cash = pf.optDouble("cash", 0.0)
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
            .setContentText("SOL: $$solPrice  |  Equity: $$equity")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPi)
            .setContentIntent(openPi)
            .build()
    }

    // ═══════════════════════════════════════════════════════════════
    //  EYE VIEW — Modern glass-morphism design
    // ═══════════════════════════════════════════════════════════════

    inner class EyeView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var blinkAmount = 0f
        private var pupilOffset = 0f
        private var glowColor = 0xFF00c853.toInt()
        private val baseColor = Color.parseColor("#07070d")

        // Drag state
        private var isDragging = false
        private var dragStartX = 0f
        private var dragStartY = 0f
        private var initX = 0
        private var initY = 0

        // Tap tracking
        private var lastTapTime = 0L

        // Popup state
        private var showPopup = false
        private var popupAlpha = 1f
        private var popupFadeRunnable: Runnable? = null

        // Close button state
        private var showClose = false
        private var closeAlpha = 0f
        private var closeFadeRunnable: Runnable? = null

        // Pupil idle animation
        private var pupilAnimPhase = 0f
        private var pupilRunnable: Runnable? = null

        // View size — hardcoded 600px for reliability across devices
        private val eyeSizePx = 600

        init {
            setBackgroundColor(Color.TRANSPARENT)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            startPupilIdle()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            pupilRunnable?.let { handler.removeCallbacks(it) }
        }

        private fun startPupilIdle() {
            pupilRunnable = object : Runnable {
                override fun run() {
                    pupilAnimPhase += 0.05f
                    pupilOffset = sin(pupilAnimPhase * PI).toFloat() * 0.25f
                    invalidate()
                    handler.postDelayed(this, 50)
                }
            }
            handler.post(pupilRunnable!!)
        }

        fun blink() {
            val anim = ValueAnimator.ofFloat(0f, 1f, 0f)
            anim.duration = 180
            anim.addUpdateListener {
                blinkAmount = it.animatedValue as Float
                invalidate()
            }
            anim.start()
        }

        fun updateGlow(dir: String) {
            glowColor = when (dir) {
                "up" -> Color.parseColor("#00e676")
                "down" -> Color.parseColor("#ff1744")
                else -> Color.parseColor("#ffab00")
            }
        }

        private fun showDataPopup() {
            popupFadeRunnable?.let { handler.removeCallbacks(it) }
            showPopup = true
            popupAlpha = 1f
            invalidate()
            popupFadeRunnable = Runnable {
                showPopup = false
                invalidate()
            }
            handler.postDelayed(popupFadeRunnable!!, 3000)
        }

        private fun showCloseButton() {
            closeFadeRunnable?.let { handler.removeCallbacks(it) }
            showClose = true
            closeAlpha = 1f
            invalidate()
            closeFadeRunnable = Runnable {
                showClose = false
                invalidate()
            }
            handler.postDelayed(closeFadeRunnable!!, 5000)
        }

        private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val size = eyeSizePx
            setMeasuredDimension(size, size)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val rx = w * 0.32f
            val ry = (w * 0.28f) * (1f - blinkAmount * 0.7f)

            // ── Outer ambient glow (multi-layer) ──
            paint.style = Paint.Style.FILL
            for (layer in 3 downTo 1) {
                val alpha = (12 / layer) + (blinkAmount * 8).toInt()
                val radius = 18f * layer
                paint.color = glowColor
                paint.alpha = alpha
                paint.maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
                canvas.drawCircle(cx, cy, rx * 1.1f + radius * 0.5f, paint)
            }
            paint.maskFilter = null

            // ── Glass ring border ──
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.argb(80, 255, 255, 255)
            paint.alpha = 80
            canvas.drawCircle(cx, cy, rx * 1.22f, paint)

            // Thin inner ring
            paint.strokeWidth = 1f
            paint.color = glowColor
            paint.alpha = 100
            canvas.drawCircle(cx, cy, rx * 1.28f, paint)

            // ── Sclera (off-white with subtle gradient effect) ──
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(245, 248, 248, 252)
            paint.alpha = 245
            canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)

            // Subtle sclera shadow edge
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = Color.argb(30, 0, 0, 0)
            paint.alpha = 30
            canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)

            // ── Iris ──
            paint.style = Paint.Style.FILL
            val irisRx = rx * 0.58f
            val irisRy = ry * 0.58f * (1f - blinkAmount * 0.5f)
            val px = cx + pupilOffset * rx * 0.2f

            // Iris gradient (radial effect via concentric circles)
            for (i in 3 downTo 0) {
                val frac = i / 3f
                paint.color = when (i) {
                    3 -> Color.parseColor("#1a1a2e")
                    2 -> Color.parseColor("#242440")
                    1 -> Color.parseColor("#2a2a4a")
                    else -> Color.parseColor("#32325a")
                }
                paint.alpha = 255
                val ir = irisRx * (0.6f + frac * 0.4f)
                val iy = irisRy * (0.6f + frac * 0.4f)
                canvas.drawOval(RectF(px - ir, cy - iy, px + ir, cy + iy), paint)
            }

            // Iris texture (fine radial lines)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            paint.color = Color.argb(40, 255, 255, 255)
            paint.alpha = 40
            for (angle in 0 until 360 step 20) {
                val rad = Math.toRadians(angle.toDouble())
                val sx = px + irisRx * 0.15f * cos(rad).toFloat()
                val sy = cy + irisRy * 0.15f * sin(rad).toFloat()
                val ex = px + irisRx * 0.85f * cos(rad).toFloat()
                val ey = cy + irisRy * 0.85f * sin(rad).toFloat()
                canvas.drawLine(sx, sy, ex, ey, paint)
            }

            // ── Pupil ──
            paint.style = Paint.Style.FILL
            val pSize = rx * 0.22f * (1f - blinkAmount * 0.3f)
            paint.color = Color.parseColor("#000000")
            canvas.drawCircle(px, cy, pSize, paint)

            // Pupil inner depth
            paint.color = Color.parseColor("#0a0a14")
            canvas.drawCircle(px, cy, pSize * 0.65f, paint)

            // ── Highlights (multi-point reflections) ──
            // Main highlight
            paint.color = Color.argb(220, 255, 255, 255)
            canvas.drawCircle(px + rx * 0.14f, cy - ry * 0.16f, rx * 0.1f, paint)
            // Secondary highlight
            paint.color = Color.argb(150, 255, 255, 255)
            canvas.drawCircle(px + rx * 0.08f, cy - ry * 0.2f, rx * 0.05f, paint)
            // Tiny accent highlight
            paint.color = Color.argb(200, 255, 255, 255)
            canvas.drawCircle(px + rx * 0.04f, cy - ry * 0.24f, rx * 0.025f, paint)

            // Sclera reflection
            paint.color = Color.argb(60, 255, 255, 255)
            canvas.drawCircle(cx - rx * 0.5f, cy - ry * 0.45f, rx * 0.22f, paint)

            // ── Eyelids (blink) ──
            if (blinkAmount > 0.01f) {
                paint.style = Paint.Style.FILL
                paint.color = baseColor
                paint.alpha = 255
                val lidH = h * 0.5f * blinkAmount
                canvas.drawRect(RectF(0f, cy - lidH - ry, w, lidH + ry), paint)
                canvas.drawRect(RectF(0f, cy + ry - lidH * 0.5f, w, lidH + ry), paint)
            }

            // ── Compact data line below eye ──
            paint.style = Paint.Style.FILL
            paint.typeface = Typeface.MONOSPACE
            paint.maskFilter = null
            val textY = h - 10f

            val solText = "SOL $$solPrice"
            val eqText = "EQ $$equity"
            val dirIcon = when (direction) {
                "up" -> "▲" ; "down" -> "▼" ; else -> "—"
            }

            paint.textSize = spToPx(13f)
            paint.color = glowColor
            paint.alpha = 220
            canvas.drawText(dirIcon, cx - rx * 1.1f, textY, paint)

            paint.color = Color.argb(200, 255, 255, 255)
            paint.textSize = spToPx(12f)
            canvas.drawText(solText, cx - rx * 0.7f, textY - 3f, paint)
            canvas.drawText(eqText, cx - rx * 0.7f, textY + 15f, paint)
            canvas.drawText("🔋$battery%  ${temperature.toInt()}°C", cx - rx * 0.7f, textY + 33f, paint)

            // ── Data popup (on tap) ──
            if (showPopup) {
                val pw = w * 1.4f
                val ph = h * 0.55f
                val ppx = cx - pw / 2f
                val ppy = -ph - 10f

                // Card background (glass effect)
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(230, 7, 7, 13)
                paint.maskFilter = null
                val card = RectF(ppx, ppy, ppx + pw, ppy + ph)
                canvas.drawRoundRect(card, 16f, 16f, paint)

                // Card border with glow
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                paint.color = glowColor
                paint.alpha = 120
                canvas.drawRoundRect(card, 16f, 16f, paint)

                // Inner glow
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.color = glowColor
                paint.alpha = 40
                paint.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
                canvas.drawRoundRect(card, 16f, 16f, paint)
                paint.maskFilter = null

                // Title
                paint.style = Paint.Style.FILL
                paint.typeface = Typeface.MONOSPACE
                paint.textSize = spToPx(14f)
                paint.color = glowColor
                paint.alpha = 240
                val title = "👁 ADWA"
                canvas.drawText(title, ppx + 16f, ppy + 26f, paint)

                // Divider line
                paint.color = Color.argb(50, 255, 255, 255)
                paint.strokeWidth = 1f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(ppx + 16f, ppy + 34f, ppx + pw - 16f, ppy + 34f, paint)

                // Data rows
                paint.style = Paint.Style.FILL
                dataRow(canvas, "SOL", "$$solPrice", directionLabel(), ppx + 16f, ppy + 58f, glowColor)
                dataRow(canvas, "24h", "${if (solChange >= 0) "+" else ""}$solChange%", "", ppx + 16f, ppy + 80f, 
                    if (solChange >= 0) Color.parseColor("#00e676") else Color.parseColor("#ff1744"))
                dataRow(canvas, "Equity", "$$equity", "", ppx + 16f, ppy + 102f, Color.WHITE)
                dataRow(canvas, "Cash", "$$cash", "", ppx + 16f, ppy + 124f, Color.WHITE)
                dataRow(canvas, "Battery", "$battery%", "", ppx + 16f, ppy + 146f, Color.WHITE)
                dataRow(canvas, "Temp", "${temperature.toInt()}°C", "", ppx + 16f, ppy + 168f, Color.WHITE)
            }

            // ── Close button (appears after drag) ──
            if (showClose && closeAlpha > 0.01f) {
                val closeSize = 48f
                val closeCx = w + 10f
                val closeCy = -10f

                // Circle background
                paint.style = Paint.Style.FILL
                paint.maskFilter = null
                paint.color = Color.argb((180 * closeAlpha).toInt(), 20, 20, 30)
                canvas.drawCircle(closeCx, closeCy, closeSize / 2f, paint)

                // Border
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.color = Color.argb((180 * closeAlpha).toInt(), 255, 80, 80)
                canvas.drawCircle(closeCx, closeCy, closeSize / 2f, paint)

                // X mark
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = Color.argb((220 * closeAlpha).toInt(), 255, 80, 80)
                val xPad = closeSize * 0.28f
                canvas.drawLine(closeCx - xPad, closeCy - xPad, closeCx + xPad, closeCy + xPad, paint)
                canvas.drawLine(closeCx + xPad, closeCy - xPad, closeCx - xPad, closeCy + xPad, paint)
            }
        }

        private fun directionLabel(): String = when (direction) {
            "up" -> "▲ UP" ; "down" -> "▼ DOWN" ; else -> "— FLAT"
        }

        private fun dataRow(canvas: Canvas, label: String, value: String, suffix: String, x: Float, y: Float, valueColor: Int) {
            paint.textSize = spToPx(12f)
            paint.typeface = Typeface.MONOSPACE
            paint.color = Color.argb(150, 255, 255, 255)
            canvas.drawText(label, x, y, paint)

            paint.color = valueColor
            paint.alpha = 240
            paint.textSize = spToPx(13f)
            val vw = paint.measureText(value + suffix)
            val pw = 260f // approximate popup width
            val totalW = pw - 32f
            canvas.drawText(value + suffix, x + totalW - vw - 8f, y, paint)
        }

        // ── Touch handling ──

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
                    if (abs(dx) > 6 || abs(dy) > 6) {
                        isDragging = true
                        params.x = (initX + dx).toInt()
                        params.y = (initY + dy).toInt()
                        try { wm.updateViewLayout(this, params) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        // Show close button after drag
                        showCloseButton()
                    } else {
                        // Check if close button was tapped
                        val relX = event.x
                        val relY = event.y
                        val closeCx = width + 10f
                        val closeCy = -10f
                        val closeRad = 24f
                        val distToClose = sqrt((relX - closeCx).pow(2) + (relY - closeCy).pow(2))

                        if (showClose && distToClose < closeRad + 10f) {
                            // Stop overlay
                            val stopIntent = Intent(this@OverlayService, OverlayService::class.java)
                                .apply { action = ACTION_STOP }
                            startService(stopIntent)
                            stopSelf()
                            return true
                        }

                        // Normal tap
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 400) {
                            // Double tap → Telegram
                            lastTapTime = 0
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, 
                                    Uri.parse("https://t.me/BitcoinsignalsMessay_bot"))
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
