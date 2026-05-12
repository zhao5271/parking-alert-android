package com.example.parkingalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import androidx.core.content.ContextCompat

class SmsAlertReceiver : BroadcastReceiver() {

    private var ruleRepository: SmsRuleRepository? = null

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

        val repository = ruleRepository ?: SmsRuleRepository(context.applicationContext).also {
            ruleRepository = it
        }

        val matchedRule = repository.getRules().firstOrNull { it.matches(body) } ?: run {
            return
        }
        val payload = ReminderCopyGenerator.generate(
            messageBody = body,
            matchedRuleName = matchedRule.name,
        )

        val serviceIntent = Intent(context, AlertService::class.java).apply {
            action = AlertService.ACTION_START
            putExtra(AlertService.EXTRA_MESSAGE, body)
            putAlertPayload(payload)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    companion object {
        private const val SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED"
    }
}
