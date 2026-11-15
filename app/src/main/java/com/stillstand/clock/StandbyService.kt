package com.stillstand.clock

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.*

class StandbyService : Service(), SensorEventListener {
    companion object {
        const val ACTION_START = "com.stillstand.clock.START"
        const val ACTION_STOP = "com.stillstand.clock.STOP"
        private const val CHANNEL = "stillstand_detection"
        private const val NOTIFICATION_ID = 7314
    }

    private lateinit var sensorManager: SensorManager
    private var accel: Sensor? = null
    private var gyro: Sensor? = null
    private lateinit var wm: WindowManager
    private var overlay: ClockOverlayView? = null
    private var landscape = false
    private var stillSince = 0L
    private var lastGyro = 99f
    private var accelMotion = 99f
    private var previous = FloatArray(3)
    private var shown = false
    private var rearmAfterMovement = false
    private val prefs by lazy { getSharedPreferences("stillstand", Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.edit().putBoolean("enabled", false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!prefs.getBoolean("enabled", false)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!SettingsCompat.canDraw(this)) {
            prefs.edit().putBoolean("enabled", false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        sensorManager.unregisterListener(this)
        accel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyro?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!prefs.getBoolean("enabled", false)) {
            stopSelf()
            return
        }
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            landscape = abs(x) > abs(y) * 1.12f && abs(x) > 5.0f
            accelMotion = sqrt(
                (x - previous[0]).pow(2) +
                    (y - previous[1]).pow(2) +
                    (z - previous[2]).pow(2)
            )
            previous = floatArrayOf(x, y, z)
        } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            lastGyro = sqrt(event.values[0].pow(2) + event.values[1].pow(2) + event.values[2].pow(2))
        }

        val still = (gyro == null || lastGyro < 0.10f) && accelMotion < 0.45f
        if (!landscape || !still) {
            stillSince = 0L
            if (rearmAfterMovement) rearmAfterMovement = false
        } else if (stillSince == 0L) {
            stillSince = SystemClock.elapsedRealtime()
        }

        if (landscape && still && !shown && !rearmAfterMovement &&
            stillSince != 0L && SystemClock.elapsedRealtime() - stillSince >= 5000L
        ) {
            showOverlay()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun showOverlay() {
        if (shown || !SettingsCompat.canDraw(this)) return
        val view = ClockOverlayView(this) { dismissOverlay() }
        val flags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            wm.addView(view, lp)
            overlay = view
            shown = true
            @Suppress("DEPRECATION")
            view.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        } catch (_: Exception) {
            overlay = null
            shown = false
        }
    }

    private fun dismissOverlay() {
        val v = overlay ?: return
        try {
            wm.removeView(v)
        } catch (_: Exception) {
        }
        overlay = null
        shown = false
        stillSince = 0L
        rearmAfterMovement = true
    }

    private fun buildNotification(): Notification {
        val stop = Intent(this, StandbyService::class.java).setAction(ACTION_STOP)
        val pi = PendingIntent.getService(
            this, 7315, stop,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Stillstand is active")
            .setContentText("Waiting for five seconds of still landscape orientation")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, "Turn off", pi).build())
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL, "Stillstand standby detection", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows while Stillstand is watching for a stable landscape position"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        dismissOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

private object SettingsCompat {
    fun canDraw(context: Context): Boolean = android.provider.Settings.canDrawOverlays(context)
}

private class ClockOverlayView(
    context: Context,
    private val onDismiss: () -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs = context.getSharedPreferences("stillstand", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    private val pulse = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, 40)
        }
    }

    private val palette get() = prefs.getInt("palette", 2).coerceIn(0, 2)
    private val face get() = prefs.getInt("face", 0).coerceIn(0, 5)
    private val red get() = prefs.getBoolean("red", false) || face == 4
    private val ink get() = if (red) Color.rgb(255, 67, 58)
        else intArrayOf(0xFFE9E0D1.toInt(), 0xFFA8B9A2.toInt(), 0xFF168BFF.toInt())[palette]

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        alpha = 0f
        scaleX = .985f
        scaleY = .985f
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        handler.post(pulse)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(pulse)
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > 18f || abs(event.y - downY) > 18f) moved = true
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val threshold = 64f * resources.displayMetrics.density
                if (abs(dx) > threshold && abs(dx) > abs(dy) * 1.25f) {
                    val next = (face + if (dx < 0f) 1 else 5) % 6
                    prefs.edit().putInt("face", next).apply()
                    animate().alpha(.35f).setDuration(90).withEndAction {
                        invalidate()
                        animate().alpha(1f).setDuration(180).start()
                    }.start()
                } else if (!moved || (abs(dx) < 18f && abs(dy) < 18f)) {
                    onDismiss()
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val screenW = width.toFloat()
        val screenH = height.toFloat()
        val now = Date()
        c.drawColor(if (red) Color.rgb(12, 4, 5) else Color.rgb(7, 9, 13))
        if (screenH > screenW) {
            c.save()
            c.translate(screenW, 0f)
            c.rotate(90f)
            drawFace(c, screenH, screenW, now)
            c.restore()
        } else {
            drawFace(c, screenW, screenH, now)
        }
    }

    private fun drawFace(c: Canvas, w: Float, h: Float, now: Date) {
        val cx = w / 2f
        val cy = h / 2f
        val time = timeFormat.format(now)
        val date = dateFormat.format(now).uppercase(Locale.getDefault())
        paint.color = ink
        paint.style = Paint.Style.FILL
        paint.alpha = 255

        when (face) {
            0 -> {
                paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = min(w * .245f, h * .40f)
                paint.isFakeBoldText = true
                c.drawText(time, cx, cy + paint.textSize * .34f, paint)
                paint.isFakeBoldText = false
                paint.textSize = 12f
                paint.alpha = 150
                c.drawText(date, cx, cy + paint.textSize * 3.4f, paint)
            }
            1 -> drawSquare(c, cx, cy, min(w, h) * .34f, now)
            2 -> {
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = Typeface.create("serif", Typeface.NORMAL)
                paint.textSize = min(w * .19f, h * .28f)
                c.drawText(time, cx, cy + 12f, paint)
                paint.textSize = 13f
                c.drawText(date, cx, cy + 52f, paint)
                paint.textSize = 10f
                c.drawText("LOCAL TIME  ·  STILLSTAND", cx, h * .82f, paint)
            }
            3 -> {
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                paint.textSize = min(w * .17f, h * .25f)
                c.drawText(time, cx, cy + 10f, paint)
                paint.strokeWidth = 1.2f
                c.drawLine(cx - w * .18f, cy + 42f, cx + w * .18f, cy + 42f, paint)
                paint.textSize = 11f
                c.drawText(date, cx, cy + 75f, paint)
            }
            4 -> {
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                paint.textSize = min(w * .22f, h * .31f)
                c.drawText(time, cx, cy + 10f, paint)
                paint.textSize = 11f
                c.drawText(date, cx, cy + 55f, paint)
            }
            else -> drawOrbit(c, cx, cy, min(w, h) * .30f, now, w, h)
        }

        paint.alpha = 100
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = 9f
        c.drawText("SWIPE TO CHANGE", cx, h * .94f, paint)
        paint.alpha = 255
    }

    private fun drawSquare(c: Canvas, cx: Float, cy: Float, r: Float, date: Date) {
        paint.color = ink
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        c.drawRoundRect(RectF(cx - r, cy - r, cx + r, cy + r), 8f, 8f, paint)
        for (i in 0..59) {
            val a = Math.PI * i / 30.0
            val outer = r - 12
            val inner = if (i % 5 == 0) r - 26 else r - 18
            paint.strokeWidth = if (i % 5 == 0) 2.2f else 1f
            c.drawLine(
                cx + cos(a).toFloat() * inner,
                cy + sin(a).toFloat() * inner,
                cx + cos(a).toFloat() * outer,
                cy + sin(a).toFloat() * outer,
                paint
            )
        }
        val cal = Calendar.getInstance().apply { time = date }
        val sec = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f
        val minute = cal.get(Calendar.MINUTE) + sec / 60f
        val hour = cal.get(Calendar.HOUR) + minute / 60f
        fun hand(angle: Double, len: Float, width: Float) {
            val a = angle - Math.PI / 2
            paint.strokeWidth = width
            paint.strokeCap = Paint.Cap.ROUND
            c.drawLine(
                cx, cy,
                cx + cos(a).toFloat() * len,
                cy + sin(a).toFloat() * len,
                paint
            )
        }
        hand(hour / 12 * 2 * Math.PI, r * .48f, 4f)
        hand(minute / 60 * 2 * Math.PI, r * .70f, 2.5f)
        hand(sec / 60 * 2 * Math.PI, r * .78f, 1.1f)
        paint.style = Paint.Style.FILL
        c.drawCircle(cx, cy, 4f, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 11f
        c.drawText(dateFormat.format(date).uppercase(Locale.getDefault()), cx, cy + r + 32f, paint)
    }

    private fun drawOrbit(c: Canvas, cx: Float, cy: Float, r: Float, date: Date, w: Float, h: Float) {
        paint.color = ink
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        c.drawCircle(cx, cy, r, paint)
        val cal = Calendar.getInstance().apply { time = date }
        val sec = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f
        val a = sec / 60 * 2 * Math.PI - Math.PI / 2
        paint.style = Paint.Style.FILL
        c.drawCircle(cx + cos(a).toFloat() * r, cy + sin(a).toFloat() * r, 5f, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        paint.textSize = min(w * .14f, h * .20f)
        c.drawText(timeFormat.format(date), cx, cy + 10f, paint)
        paint.textSize = 10f
        c.drawText(dateFormat.format(date).uppercase(Locale.getDefault()), cx, cy + r + 34f, paint)
    }
}
