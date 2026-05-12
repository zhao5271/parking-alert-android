package com.example.parkingalert

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class AlertService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var torchCameraId: String? = null
    private var torchEnabled = false
    private var torchBlinking = false

    private val torchBlinkRunnable = object : Runnable {
        override fun run() {
            if (!torchBlinking) return
            setTorchEnabled(!torchEnabled)
            mainHandler.postDelayed(this, TORCH_BLINK_INTERVAL_MS)
        }
    }

    private val stopTorchRunnable = Runnable {
        stopTorchBlink()
    }

    private val stopVibrationRunnable = Runnable {
        stopVibration()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        torchCameraId = resolveTorchCameraId()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelfSafely()
            ACTION_START, null -> {
                val payload = intent?.toAlertPayload()
                val message = payload?.sourceMessage ?: intent?.getStringExtra(EXTRA_MESSAGE).orEmpty()
                startForeground(NOTIFICATION_ID, buildNotification(payload, message))
                launchAlertScreen(payload, message)
                startAlarm()
                startVibration()
                startTorchBlink()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopAlarm()
        stopTorchBlink()
        stopVibration()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAlarm() {
        if (mediaPlayer?.isPlaying == true) return

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: run {
                stopSelfSafely()
                return
            }

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(applicationContext, soundUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            stopAlarm()
            stopSelfSafely()
        }
    }

    private fun stopAlarm() {
        mediaPlayer?.run {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    private fun launchAlertScreen(payload: ReminderAlertPayload?, message: String) {
        val intent = Intent(this, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_MESSAGE, message)
            payload?.let { putAlertPayload(it) }
        }
        startActivity(intent)
    }

    private fun startVibration() {
        mainHandler.removeCallbacks(stopVibrationRunnable)
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        val pattern = longArrayOf(0, 300, 180, 300, 180)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
        mainHandler.postDelayed(stopVibrationRunnable, VIBRATION_DURATION_MS)
    }

    private fun stopVibration() {
        mainHandler.removeCallbacks(stopVibrationRunnable)
        getSystemService(Vibrator::class.java)?.cancel()
    }

    private fun startTorchBlink() {
        if (torchBlinking) return
        if (!hasCameraPermission() || torchCameraId == null) return
        mainHandler.removeCallbacks(stopTorchRunnable)
        torchBlinking = true
        mainHandler.post(torchBlinkRunnable)
        mainHandler.postDelayed(stopTorchRunnable, TORCH_BLINK_DURATION_MS)
    }

    private fun stopTorchBlink() {
        torchBlinking = false
        mainHandler.removeCallbacks(torchBlinkRunnable)
        mainHandler.removeCallbacks(stopTorchRunnable)
        setTorchEnabled(false)
    }

    private fun setTorchEnabled(enabled: Boolean) {
        val cameraId = torchCameraId ?: return
        val cameraManager = getSystemService(CameraManager::class.java) ?: return
        try {
            cameraManager.setTorchMode(cameraId, enabled)
            torchEnabled = enabled
        } catch (_: Exception) {
            torchEnabled = false
        }
    }

    private fun resolveTorchCameraId(): String? {
        val cameraManager = getSystemService(CameraManager::class.java) ?: return null
        return cameraManager.cameraIdList.firstOrNull { cameraId ->
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val flashAvailable =
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            flashAvailable && lensFacing == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(
        payload: ReminderAlertPayload?,
        message: String,
    ): android.app.Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, AlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_MESSAGE, message)
                payload?.let { putAlertPayload(it) }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, AlertService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificationTitle = payload?.title?.ifBlank {
            getString(R.string.alert_notification_title)
        } ?: getString(R.string.alert_notification_title)
        val notificationBody = payload?.body?.ifBlank {
            message.ifBlank { getString(R.string.alert_notification_text) }
        } ?: message.ifBlank { getString(R.string.alert_notification_text) }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(notificationTitle)
            .setContentText(notificationBody)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    notificationBody,
                ),
            )
            .setContentIntent(openAppIntent)
            .setFullScreenIntent(openAppIntent, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.stop_alarm_button),
                stopIntent,
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.alert_channel_description)
            enableLights(true)
            enableVibration(true)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
    }

    companion object {
        const val ACTION_START = "com.example.parkingalert.action.START"
        const val ACTION_STOP = "com.example.parkingalert.action.STOP"
        const val EXTRA_MESSAGE = "extra_message"

        private const val CHANNEL_ID = "parking_alert_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TORCH_BLINK_INTERVAL_MS = 220L
        private const val TORCH_BLINK_DURATION_MS = 20_000L
        private const val VIBRATION_DURATION_MS = 10_000L
    }
}
