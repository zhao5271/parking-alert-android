package com.example.parkingalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import androidx.core.content.ContextCompat

class SmsAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SMS_RECEIVED_ACTION) {
            return
        }

        val bundle = intent.extras ?: return
        val pdus = bundle.get("pdus") as? Array<*> ?: return
        val format = bundle.getString("format")

        val body = buildString {
            pdus.forEach { pdu ->
                val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    SmsMessage.createFromPdu(pdu as ByteArray, format)
                } else {
                    @Suppress("DEPRECATION")
                    SmsMessage.createFromPdu(pdu as ByteArray)
                }
                append(message.messageBody.orEmpty())
            }
        }.trim()

        if (!isParkingReminder(body)) {
            return
        }

        val serviceIntent = Intent(context, AlertService::class.java).apply {
            action = AlertService.ACTION_START
            putExtra(AlertService.EXTRA_MESSAGE, body)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    private fun isParkingReminder(body: String): Boolean {
        val compact = body.replace(" ", "")
        return compact.contains("交警") &&
            (compact.contains("请立即驶离") ||
            compact.contains("未按规定停放"))
    }

    companion object {
        private const val SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED"
    }
}
