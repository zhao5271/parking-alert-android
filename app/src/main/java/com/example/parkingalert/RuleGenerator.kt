package com.example.parkingalert

import java.util.UUID

object RuleGenerator {

    fun extractTags(sampleText: String): List<String> {
        val normalizedSample = sampleText.trim()
        if (normalizedSample.length < MIN_SAMPLE_LENGTH) {
            return emptyList()
        }

        return extractKeywords(normalizedSample)
    }

    fun generate(nameInput: String, sampleText: String, selectedTags: List<String>): SmsRule? {
        val normalizedSample = sampleText.trim()
        if (normalizedSample.length < MIN_SAMPLE_LENGTH) {
            return null
        }

        val displayName = nameInput.trim().ifBlank {
            normalizedSample.replace(Regex("\\s+"), " ").take(RULE_NAME_PREVIEW_LENGTH)
        }

        val normalizedTags = selectedTags
            .map(String::trim)
            .filter { it.length >= 2 }
            .distinct()

        val strongTagCount = normalizedTags.count(strongKeywordSeeds::contains)
        if (normalizedTags.isEmpty() || strongTagCount == 0) {
            return null
        }

        return SmsRule(
            id = UUID.randomUUID().toString(),
            name = displayName,
            sampleText = normalizedSample,
            requiredKeywordGroups = emptyList(),
            supplementaryKeywords = normalizedTags,
            minimumSupplementaryMatches = when {
                normalizedTags.size >= 4 -> 3
                normalizedTags.size >= 2 -> 2
                else -> 1
            },
            excludeKeywords = SmsRuleRepository.defaultExcludeKeywords,
            enabled = true,
            isBuiltIn = false,
            createdAt = System.currentTimeMillis(),
        )
    }

    private fun extractKeywords(sampleText: String): List<String> {
        val compact = sampleText.compactForMatching()
        val hitsFromSeed = keywordSeeds.filter(compact::contains)

        val clauseCandidates = sampleText
            .replace("【", " ")
            .replace("】", " ")
            .split(Regex("[,，。；;：:\\n]"))
            .map(String::trim)
            .filter { it.length in 4..18 }
            .filterNot(::isMostlyNumeric)
            .filterNot(stopPhrases::contains)
            .filterNot(genericWeakKeywords::contains)
            .sortedByDescending(String::length)

        return (hitsFromSeed + clauseCandidates)
            .map { it.trim() }
            .filter { it.length >= 2 }
            .filterNot(genericWeakKeywords::contains)
            .distinct()
            .take(MAX_KEYWORDS)
    }

    private fun isMostlyNumeric(text: String): Boolean {
        val cleaned = text.filterNot(Char::isWhitespace)
        if (cleaned.isEmpty()) return true
        val nonNumericCount = cleaned.count { !it.isDigit() }
        return nonNumericCount <= 2
    }

    private val keywordSeeds = listOf(
        "上海交警",
        "违停",
        "违章停车",
        "违法停车",
        "未按规定停放",
        "临时停车",
        "驶离",
        "立即驶离",
        "拒绝驶离",
        "挪车",
        "移车",
        "处罚",
        "依法予以处罚",
        "接受处理",
        "已被记录",
        "抓拍",
        "交警",
        "交管",
        "交通管理",
        "监控设备",
        "电子警察",
    )

    private val strongKeywordSeeds = setOf(
        "上海交警",
        "交警",
        "交管",
        "违停",
        "违章停车",
        "违法停车",
        "未按规定停放",
        "已被记录",
        "请立即驶离",
        "未及时驶离",
        "依法予以处罚",
        "处罚",
        "驶离",
    )

    private val genericWeakKeywords = setOf(
        "停车",
        "提醒",
        "通知",
        "车主",
        "机动车",
        "谢谢配合",
    )

    private val stopPhrases = setOf(
        "验证码",
        "校验码",
        "登录",
        "注册",
        "支付",
        "退款",
        "快递",
        "外卖",
        "签收",
        "账单",
    )

    private const val MIN_SAMPLE_LENGTH = 8
    private const val MAX_KEYWORDS = 8
    private const val RULE_NAME_PREVIEW_LENGTH = 16
}
