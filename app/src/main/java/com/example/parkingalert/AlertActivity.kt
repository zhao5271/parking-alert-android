package com.example.parkingalert

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.parkingalert.databinding.ActivityAlertBinding

class AlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertBinding
    private val mainHandler = Handler(Looper.getMainLooper())

    private val dimScreenRunnable = Runnable {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        if (!isFinishing) {
            moveTaskToBack(true)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }

        val payload = intent.toAlertPayload()
        binding.alertTitleText.text = payload?.title?.ifBlank {
            getString(R.string.alert_screen_title)
        } ?: getString(R.string.alert_screen_title)
        binding.alertMessageText.text = payload?.body?.ifBlank {
            intent.getStringExtra(AlertService.EXTRA_MESSAGE).orEmpty()
                .ifBlank { getString(R.string.alert_notification_text) }
        } ?: intent.getStringExtra(AlertService.EXTRA_MESSAGE).orEmpty()
            .ifBlank { getString(R.string.alert_notification_text) }

        binding.stopAlertButton.setOnClickListener {
            stopAlert()
        }
    }

    override fun onResume() {
        super.onResume()
        mainHandler.removeCallbacks(dimScreenRunnable)
        mainHandler.postDelayed(dimScreenRunnable, SCREEN_ON_DURATION_MS)
    }

    override fun onPause() {
        mainHandler.removeCallbacks(dimScreenRunnable)
        super.onPause()
    }

    private fun stopAlert() {
        mainHandler.removeCallbacks(dimScreenRunnable)
        val intent = Intent(this, AlertService::class.java).apply {
            action = AlertService.ACTION_STOP
        }
        startService(intent)
        finish()
    }

    companion object {
        private const val SCREEN_ON_DURATION_MS = 30_000L
    }
}
