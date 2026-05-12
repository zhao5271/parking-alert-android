package com.example.parkingalert

import android.content.Context
import android.provider.Telephony

data class MatchedSmsResult(
    val address: String?,
    val body: String,
    val timestamp: Long,
    val matchedRuleName: String?,
)

class SmsHistoryTester(
    private val context: Context,
    private val ruleRepository: SmsRuleRepository,
) {

    fun findLatestMatchedMessage(limit: Int = DEFAULT_SCAN_LIMIT): MatchedSmsResult? {
        val enabledRules = ruleRepository.getRules().filter(SmsRule::enabled)
        if (enabledRules.isEmpty()) {
            return null
        }

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )

        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
            var scannedCount = 0

            while (cursor.moveToNext() && scannedCount < limit) {
                scannedCount += 1
                val body = cursor.getString(bodyIndex).orEmpty().trim()
                if (body.isBlank()) {
                    continue
                }

                val matchedRule = enabledRules.firstOrNull { it.matches(body) } ?: continue
                val address = if (addressIndex >= 0) cursor.getString(addressIndex) else null
                val timestamp = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L
                return MatchedSmsResult(
                    address = address,
                    body = body,
                    timestamp = timestamp,
                    matchedRuleName = matchedRule.name,
                )
            }
        }

        return null
    }

    companion object {
        const val DEFAULT_SCAN_LIMIT = 80
    }
}
