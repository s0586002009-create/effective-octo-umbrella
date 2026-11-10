package com.screenprank

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class OverlayService : Service(), SensorEventListener {
    companion object {
        const val ACTION_START = "com.screenprank.START"
        const val ACTION_STOP = "com.screenprank.STOP"
        const val ACTION_PAUSE = "com.screenprank.PAUSE"
        const val ACTION_UPDATE = "com.screenprank.UPDATE"
        const val ACTION_CRACK = "com.screenprank.CRACK"
        const val ACTION_DIRT = "com.screenprank.DIRT"
        const val ACTION_CLEAR = "com.screenprank.CLEAR"
        const val ACTION_STEAM = "com.screenprank.STEAM"
        const val ACTION_FINGERPRINT = "com.screenprank.FINGERPRINT"
        const val ACTION_COFFEE = "com.screenprank.COFFEE"
        const val ACTION_INK = "com.screenprank.INK"
        const val ACTION_SNOW = "com.screenprank.SNOW"
        const val ACTION_SOUND = "com.screenprank.SOUND"
        private const val CHANNEL_ID = "screen_prank_running"
        private const val NOTIFICATION_ID = 8041
        @Volatile var active: Boolean = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: android.content.SharedPreferences
    private var overlay: CreatureView? = null
    private var paused = false
    private var receiverRegistered = false
    private var sensorManager: SensorManager? = null
    private var lastShake = 0L
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timedStop = Runnable { stopEverything() }
    private var toneGenerator: ToneGenerator? = null

    private fun overlayAllowed(): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> overlay?.setPaused(true)
                Intent.ACTION_SCREEN_ON -> overlay?.setPaused(paused)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("screen_prank", MODE_PRIVATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenReceiver, filter)
        receiverRegistered = true
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        updateShakeListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                paused = !paused
                overlay?.setPaused(paused)
                updateNotification()
            }
            ACTION_UPDATE -> {
                overlay?.updateSettings()
                    updateShakeListener()
                updateNotification()
            }
            ACTION_CRACK -> overlay?.showCrack()
            ACTION_DIRT -> overlay?.showDirt()
            ACTION_CLEAR -> overlay?.clearEffects()
            ACTION_STEAM -> overlay?.showExtraEffect(1)
            ACTION_FINGERPRINT -> overlay?.showExtraEffect(2)
            ACTION_COFFEE -> overlay?.showExtraEffect(3)
            ACTION_INK -> overlay?.showExtraEffect(4)
            ACTION_SNOW -> overlay?.showExtraEffect(5)
            ACTION_SOUND -> playSound()
            ACTION_START -> {
                if (!overlayAllowed()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startAsForeground()
                addOverlay()
                scheduleTimedStop()
                if (prefs.getBoolean("sound_enabled", false)) playSound()
                updateShakeListener()
            }
        }
        return START_STICKY
    }

    private fun scheduleTimedStop() {
        timerHandler.removeCallbacks(timedStop)
        val minutes = prefs.getInt("duration_minutes", 0).coerceIn(0, 180)
        if (minutes > 0) timerHandler.postDelayed(timedStop, minutes * 60_000L)
    }

    private fun playSound() {
        try {
            if (toneGenerator == null) toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 180)
        } catch (_: Exception) { }
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        active = true
    }

    private fun addOverlay() {
        if (overlay != null) {
            overlay?.updateSettings()
            return
        }
        val view = CreatureView(this).apply {
            setPaused(paused)
            updateSettings()
        }
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        var windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        // Overlay is always non-touchable so taps pass through to the app underneath.
        windowFlags = windowFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            windowFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "ScreenPrankOverlay"
        }
        try {
            windowManager.addView(view, params)
            overlay = view
        } catch (_: SecurityException) {
            stopEverything()
        } catch (_: WindowManager.BadTokenException) {
            stopEverything()
        }
    }

    private fun updateNotification() {
        if (!active) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 10, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("מסך מופרע פעיל")
            .setContentText(if (paused) "האפקטים מושהים" else "היצורים מטיילים על המסך")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
        if (prefs.getBoolean("notification_actions", true)) {
            builder.addAction(0, if (paused) "המשך" else "השהה", servicePending(ACTION_PAUSE, 11))
            builder.addAction(0, "עצור הכול", servicePending(ACTION_STOP, 12))
        }
        return builder.build()
    }

    private fun servicePending(action: String, requestCode: Int): PendingIntent {
        val i = Intent(this, OverlayService::class.java).setAction(action)
        return PendingIntent.getService(
            this, requestCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "מסך מופרע – פעילות", NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "התראה קבועה הנדרשת בזמן הצגת האפקטים"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun updateShakeListener() {
        val manager = sensorManager ?: return
        manager.unregisterListener(this)
        if (active && prefs.getBoolean("shake_stop", false)) {
            manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val magnitude = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        val now = System.currentTimeMillis()
        if (magnitude > 22f && now - lastShake > 1800) {
            lastShake = now
            stopEverything()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun stopEverything() {
        timerHandler.removeCallbacks(timedStop)
        active = false
        overlay?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        overlay = null
        sensorManager?.unregisterListener(this)
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        active = false
        overlay?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        overlay = null
        sensorManager?.unregisterListener(this)
        toneGenerator?.release()
        toneGenerator = null
        timerHandler.removeCallbacksAndMessages(null)
        if (receiverRegistered) {
            try { unregisterReceiver(screenReceiver) } catch (_: Exception) { }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
