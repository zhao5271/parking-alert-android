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
        val whitelistKeywords = listOf(
            "学法减分",
            "交管12123",
            "温馨提示",
            "监控设备记录",
            "已被记录",
            "驶离",
            "处罚"
        )
        val blacklistKeywords = listOf(
            "验证码",
            "校验码",
            "登录",
            "注册",
            "支付",
            "退款",
            "快递",
            "外卖",
            "取件",
            "签收",
            "账单",
            "还款",
            "优惠",
            "促销",
            "直播",
            "课程",
            "面试",
            "招聘",
            "酒店",
            "航班",
            "车次",
            "核酸",
            "就诊",
            "体检"
        )

        if (blacklistKeywords.any(compact::contains)) {
            return false
        }

        val authorityHits = authorityKeywords.count(compact::contains)
        val violationHits = parkingViolationKeywords.count(compact::contains)
        val enforcementHits = enforcementKeywords.count(compact::contains)
        val whitelistHits = whitelistKeywords.count(compact::contains)

        if (authorityHits > 0 && violationHits > 0 && enforcementHits > 0) {
            return true
        }

        val totalCoreHits = authorityHits + violationHits + enforcementHits
        return authorityHits > 0 &&
            violationHits > 0 &&
            totalCoreHits >= 3 &&
            whitelistHits >= 1
    }

    companion object {
        private const val SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED"
    }
}
