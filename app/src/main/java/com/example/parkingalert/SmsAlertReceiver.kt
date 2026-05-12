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
        val authorityKeywords = listOf(
            "交警",
            "公安局交通管理",
            "交通管理支队",
            "交通技术监控设备"
        )
        val parkingViolationKeywords = listOf(
            "违停",
            "停放",
            "临时停车",
            "违法行为"
        )
        val enforcementKeywords = listOf(
            "请立即驶离",
            "拒绝立即驶离",
            "未及时驶离",
            "已被记录",
            "予以处罚",
            "接受处理"
        )

        return authorityKeywords.any(compact::contains) &&
            parkingViolationKeywords.any(compact::contains) &&
            enforcementKeywords.any(compact::contains)
    }

    companion object {
        private const val SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED"
    }
}
