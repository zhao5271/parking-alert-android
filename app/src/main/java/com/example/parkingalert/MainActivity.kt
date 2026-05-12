package com.example.parkingalert

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.parkingalert.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissionStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.requestPermissionsButton.setOnClickListener {
            requestNeededPermissions()
        }

        binding.testAlarmButton.setOnClickListener {
            startAlertService(getString(R.string.test_alert_message))
        }

        binding.stopAlarmButton.setOnClickListener {
            stopAlertService()
        }

        refreshPermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun requestNeededPermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filterNot(::hasPermission)

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            refreshPermissionStatus()
        }
    }

    private fun refreshPermissionStatus() {
        val smsGranted = hasPermission(Manifest.permission.RECEIVE_SMS)
        val cameraGranted = hasPermission(Manifest.permission.CAMERA)
        val notificationGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)

        binding.permissionStatusText.text = getString(
            R.string.permission_status_template,
            if (smsGranted) getString(R.string.status_granted) else getString(R.string.status_missing),
            if (notificationGranted) getString(R.string.status_granted) else getString(R.string.status_missing),
            if (cameraGranted) getString(R.string.status_granted) else getString(R.string.status_missing),
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun startAlertService(message: String) {
        val intent = Intent(this, AlertService::class.java).apply {
            action = AlertService.ACTION_START
            putExtra(AlertService.EXTRA_MESSAGE, message)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopAlertService() {
        val intent = Intent(this, AlertService::class.java).apply {
            action = AlertService.ACTION_STOP
        }
        startService(intent)
    }
}
