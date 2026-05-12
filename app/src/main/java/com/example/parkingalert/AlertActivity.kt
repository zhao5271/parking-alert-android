package com.example.parkingalert

import android.content.Intent
import android.graphics.Color
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
    private var flashIndex = 0

    private val flashColors = intArrayOf(
        Color.BLACK,
        Color.YELLOW,
        Color.WHITE,
        Color.YELLOW,
    )

    private val flashRunnable = object : Runnable {
        override fun run() {
            binding.alertRoot.setBackgroundColor(flashColors[flashIndex % flashColors.size])
            binding.alertTitleText.setTextColor(
                if (flashColors[flashIndex % flashColors.size] == Color.BLACK) Color.YELLOW else Color.BLACK,
            )
            flashIndex += 1
            mainHandler.postDelayed(this, SCREEN_FLASH_INTERVAL_MS)
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

        binding.alertMessageText.text =
            intent.getStringExtra(AlertService.EXTRA_MESSAGE).orEmpty().ifBlank {
                getString(R.string.alert_notification_text)
            }

        binding.stopAlertButton.setOnClickListener {
            stopAlert()
        }
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(flashRunnable)
    }

    override fun onPause() {
        mainHandler.removeCallbacks(flashRunnable)
        super.onPause()
    }

    private fun stopAlert() {
        val intent = Intent(this, AlertService::class.java).apply {
            action = AlertService.ACTION_STOP
        }
        startService(intent)
        finish()
    }

    companion object {
        private const val SCREEN_FLASH_INTERVAL_MS = 180L
    }
}
