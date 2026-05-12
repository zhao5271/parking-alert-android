package com.example.parkingalert

import android.content.Intent

enum class ReminderScene {
    PARKING_MOVE,
    CREDIT_CARD_REPAYMENT,
    GENERIC_IMPORTANT,
}

enum class ReminderUrgency {
    IMMEDIATE,
    DEADLINE,
    NORMAL,
}

data class ReminderAlertPayload(
    val scene: ReminderScene,
    val urgency: ReminderUrgency,
    val title: String,
    val body: String,
    val sourceMessage: String,
    val sender: String? = null,
    val matchedRuleName: String? = null,
)

fun Intent.putAlertPayload(payload: ReminderAlertPayload): Intent {
    putExtra(EXTRA_ALERT_SCENE, payload.scene.name)
    putExtra(EXTRA_ALERT_URGENCY, payload.urgency.name)
    putExtra(EXTRA_ALERT_TITLE, payload.title)
    putExtra(EXTRA_ALERT_BODY, payload.body)
    putExtra(EXTRA_ALERT_SOURCE_MESSAGE, payload.sourceMessage)
    putExtra(EXTRA_ALERT_SENDER, payload.sender)
    putExtra(EXTRA_ALERT_RULE_NAME, payload.matchedRuleName)
    return this
}

fun Intent.toAlertPayload(): ReminderAlertPayload? {
    val title = getStringExtra(EXTRA_ALERT_TITLE).orEmpty().trim()
    val body = getStringExtra(EXTRA_ALERT_BODY).orEmpty().trim()
    if (title.isBlank() && body.isBlank()) {
        return null
    }

    val scene = getStringExtra(EXTRA_ALERT_SCENE)
        ?.let(ReminderScene::valueOf)
        ?: ReminderScene.GENERIC_IMPORTANT
    val urgency = getStringExtra(EXTRA_ALERT_URGENCY)
        ?.let(ReminderUrgency::valueOf)
        ?: ReminderUrgency.NORMAL

    return ReminderAlertPayload(
        scene = scene,
        urgency = urgency,
        title = title,
        body = body,
        sourceMessage = getStringExtra(EXTRA_ALERT_SOURCE_MESSAGE)
            .orEmpty()
            .ifBlank { getStringExtra(AlertService.EXTRA_MESSAGE).orEmpty() },
        sender = getStringExtra(EXTRA_ALERT_SENDER),
        matchedRuleName = getStringExtra(EXTRA_ALERT_RULE_NAME),
    )
}

object ReminderCopyGenerator {

    fun generate(
        messageBody: String,
        matchedRuleName: String? = null,
        senderHint: String? = null,
    ): ReminderAlertPayload {
        val normalizedMessage = messageBody.trim().replace(Regex("\\s+"), " ")
        val sender = extractSender(normalizedMessage) ?: normalizeSenderHint(senderHint)
        val scene = classifyScene(normalizedMessage, matchedRuleName)
        val urgency = classifyUrgency(normalizedMessage, scene)

        val (title, body) = when (scene) {
            ReminderScene.PARKING_MOVE -> buildParkingCopy(normalizedMessage, urgency)
            ReminderScene.CREDIT_CARD_REPAYMENT -> buildRepaymentCopy(normalizedMessage, sender, urgency)
            ReminderScene.GENERIC_IMPORTANT -> buildGenericCopy(sender)
        }

        return ReminderAlertPayload(
            scene = scene,
            urgency = urgency,
            title = title,
            body = body,
            sourceMessage = normalizedMessage,
            sender = sender,
            matchedRuleName = matchedRuleName,
        )
    }

    private fun classifyScene(messageBody: String, matchedRuleName: String?): ReminderScene {
        val compactMessage = messageBody.compactForMatching()
        val compactRuleName = matchedRuleName.orEmpty().compactForMatching()

        return when {
            parkingKeywords.any(compactMessage::contains) || parkingKeywords.any(compactRuleName::contains) -> {
                ReminderScene.PARKING_MOVE
            }

            repaymentKeywords.any(compactMessage::contains) || repaymentKeywords.any(compactRuleName::contains) -> {
                ReminderScene.CREDIT_CARD_REPAYMENT
            }

            else -> ReminderScene.GENERIC_IMPORTANT
        }
    }

    private fun classifyUrgency(messageBody: String, scene: ReminderScene): ReminderUrgency {
        val compactMessage = messageBody.compactForMatching()
        return when {
            scene == ReminderScene.PARKING_MOVE &&
                parkingImmediateKeywords.any(compactMessage::contains) -> ReminderUrgency.IMMEDIATE

            scene == ReminderScene.CREDIT_CARD_REPAYMENT &&
                repaymentDeadlineKeywords.any(compactMessage::contains) -> ReminderUrgency.DEADLINE

            else -> ReminderUrgency.NORMAL
        }
    }

    private fun buildParkingCopy(
        messageBody: String,
        urgency: ReminderUrgency,
    ): Pair<String, String> {
        val title = if (urgency == ReminderUrgency.IMMEDIATE) "立即挪车" else "违停提醒"
        val location = extractParkingLocation(messageBody)
        val action = when {
            parkingImmediateKeywords.any(messageBody.compactForMatching()::contains) -> "请立即驶离"
            messageBody.contains("处罚") || messageBody.contains("接受处理") -> "请尽快处理"
            else -> "请尽快查看"
        }

        val body = when {
            !location.isNullOrBlank() -> "$location，$action"
            else -> action
        }
        return title to body
    }

    private fun buildRepaymentCopy(
        messageBody: String,
        sender: String?,
        urgency: ReminderUrgency,
    ): Pair<String, String> {
        val timeHint = extractTimeHint(messageBody)
        val senderLabel = sender?.let(::normalizeBankLabel)

        val title = when {
            timeHint == "今日" -> "今日还款"
            urgency == ReminderUrgency.DEADLINE -> "还款提醒"
            else -> "信用卡待还"
        }

        val body = when {
            !senderLabel.isNullOrBlank() && !timeHint.isNullOrBlank() -> "${senderLabel}信用卡${timeHint}到期"
            !senderLabel.isNullOrBlank() -> "${senderLabel}信用卡请尽快还款"
            !timeHint.isNullOrBlank() -> "${timeHint}前完成还款"
            else -> "请尽快完成还款"
        }
        return title to body
    }

    private fun buildGenericCopy(sender: String?): Pair<String, String> {
        val title = "重要提醒"
        val body = if (sender.isNullOrBlank()) {
            "请尽快查看处理"
        } else {
            "${normalizeGenericSender(sender)}，请尽快查看"
        }
        return title to body
    }

    private fun extractSender(messageBody: String): String? {
        val sender = senderPattern.find(messageBody)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        return normalizeSender(sender)
    }

    private fun normalizeSenderHint(senderHint: String?): String? {
        return normalizeSender(senderHint.orEmpty())
    }

    private fun normalizeSender(rawSender: String): String? {
        val compactSender = rawSender
            .removePrefix("【")
            .removeSuffix("】")
            .replace(Regex("\\s+"), "")
            .trim()
        if (compactSender.length !in 2..20) return null
        if (compactSender.any(Char::isDigit)) return null
        return compactSender
    }

    private fun extractParkingLocation(messageBody: String): String? {
        val locationFromSentence = parkingLocationContextPattern.find(messageBody)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        val roadMatch = roadPattern.find(locationFromSentence.orEmpty()) ?: roadPattern.find(messageBody)
        val road = roadMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (road.isBlank()) return null
        return road.take(10) + "附近"
    }

    private fun extractTimeHint(messageBody: String): String? {
        return when {
            todayPattern.containsMatchIn(messageBody) -> "今日"
            tomorrowPattern.containsMatchIn(messageBody) -> "明日"
            monthDayPattern.find(messageBody) != null -> monthDayPattern.find(messageBody)!!.value
            else -> null
        }
    }

    private fun normalizeBankLabel(sender: String): String {
        return bankShortNames.entries.firstOrNull { (fullName, _) ->
            sender.contains(fullName)
        }?.value ?: sender
    }

    private fun normalizeGenericSender(sender: String): String {
        return normalizeBankLabel(sender).take(8)
    }

    private val senderPattern = Regex("^\\s*[【\\[](.*?)[】\\]]")
    private val parkingLocationContextPattern = Regex("在(.{2,24}?)(?:未按规定停放|已被记录|请立即驶离|被记录)")
    private val roadPattern = Regex("([\\u4e00-\\u9fa5A-Za-z0-9]{2,10}?(?:路|大道|街|巷|路口))")
    private val todayPattern = Regex("今天|今日|今晚")
    private val tomorrowPattern = Regex("明天|明日")
    private val monthDayPattern = Regex("\\d{1,2}月\\d{1,2}日")

    private val parkingKeywords = listOf(
        "交警",
        "违停",
        "违法停车",
        "违章停车",
        "未按规定停放",
        "立即驶离",
        "驶离",
        "处罚",
    )

    private val parkingImmediateKeywords = listOf(
        "请立即驶离",
        "立即驶离",
        "未及时驶离",
        "依法予以处罚",
    )

    private val repaymentKeywords = listOf(
        "信用卡",
        "还款",
        "账单",
        "最后还款日",
        "最低还款额",
        "到期",
        "逾期",
    )

    private val repaymentDeadlineKeywords = listOf(
        "最后还款日",
        "今日到期",
        "今天到期",
        "到期",
        "逾期",
        "今晚",
    )

    private val bankShortNames = linkedMapOf(
        "招商银行" to "招行",
        "中国建设银行" to "建行",
        "建设银行" to "建行",
        "中国工商银行" to "工行",
        "工商银行" to "工行",
        "中国农业银行" to "农行",
        "农业银行" to "农行",
        "交通银行" to "交行",
        "中国银行" to "中行",
        "中信银行" to "中信",
        "平安银行" to "平安",
    )
}

const val EXTRA_ALERT_SCENE = "extra_alert_scene"
const val EXTRA_ALERT_URGENCY = "extra_alert_urgency"
const val EXTRA_ALERT_TITLE = "extra_alert_title"
const val EXTRA_ALERT_BODY = "extra_alert_body"
const val EXTRA_ALERT_SOURCE_MESSAGE = "extra_alert_source_message"
const val EXTRA_ALERT_SENDER = "extra_alert_sender"
const val EXTRA_ALERT_RULE_NAME = "extra_alert_rule_name"
