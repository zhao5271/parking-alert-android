package com.example.parkingalert

import java.util.UUID

enum class RuleCandidateSource {
    AUTO,
    MANUAL,
}

enum class RuleCandidateType {
    SENDER,
    VIOLATION,
    ACTION,
    LOCATION,
    OTHER,
}

data class RuleCandidate(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val source: RuleCandidateSource,
    val type: RuleCandidateType,
)

object RuleGenerator {

    fun inferCandidateType(text: String): RuleCandidateType {
        return classifyCandidateType(text.trim())
    }

    fun extractTags(sampleText: String): List<String> {
        return extractCandidates(sampleText).map(RuleCandidate::text)
    }

    fun extractCandidates(sampleText: String): List<RuleCandidate> {
        val normalizedSample = sampleText.trim()
        if (normalizedSample.length < MIN_SAMPLE_LENGTH) {
            return emptyList()
        }

        val candidates = linkedMapOf<String, RuleCandidate>()

        extractSenderCandidate(normalizedSample)?.let { candidate ->
            candidates[candidate.text] = candidate
        }

        extractQuotedCandidates(normalizedSample).forEach { candidate ->
            candidates.putIfAbsent(candidate.text, candidate)
        }

        extractSeedCandidates(normalizedSample).forEach { candidate ->
            candidates.putIfAbsent(candidate.text, candidate)
        }

        extractClauseCandidates(normalizedSample).forEach { candidate ->
            candidates.putIfAbsent(candidate.text, candidate)
        }

        return candidates.values.take(MAX_KEYWORDS)
    }

    fun extractCandidatesForGeneration(sampleText: String): List<RuleCandidate> {
        val extractedCandidates = extractCandidates(sampleText)
        if (extractedCandidates.isEmpty()) {
            return emptyList()
        }

        if (generate("", sampleText, extractedCandidates) != null) {
            return extractedCandidates
        }

        val prioritizedCandidates = extractedCandidates.sortedWith(
            compareBy<RuleCandidate>(
                { candidatePriority(it = it) },
                { it.text.length },
            ),
        )

        val selectedCandidates = mutableListOf<RuleCandidate>()
        for (candidate in prioritizedCandidates) {
            selectedCandidates += candidate
            if (generate("", sampleText, selectedCandidates) != null) {
                return selectedCandidates.toList()
            }
        }

        return extractedCandidates
    }

    fun generate(nameInput: String, sampleText: String, selectedCandidates: List<RuleCandidate>): SmsRule? {
        val normalizedSample = sampleText.trim()
        if (normalizedSample.length < MIN_SAMPLE_LENGTH) {
            return null
        }

        val displayName = nameInput.trim().ifBlank {
            normalizedSample.replace(Regex("\\s+"), " ").take(RULE_NAME_PREVIEW_LENGTH)
        }

        val normalizedCandidates = selectedCandidates
            .mapNotNull(::normalizeCandidate)
            .distinctBy { it.text }

        if (normalizedCandidates.isEmpty()) {
            return null
        }

        val reclassifiedCandidates = normalizedCandidates.map { candidate ->
            candidate.copy(type = classifyCandidateType(candidate.text))
        }

        val senderCandidates = reclassifiedCandidates
            .filter { it.type == RuleCandidateType.SENDER && isStrongSenderCandidate(it.text) }
            .map(RuleCandidate::text)
        val violationCandidates = reclassifiedCandidates
            .filter { it.type == RuleCandidateType.VIOLATION }
            .map(RuleCandidate::text)
        val actionCandidates = reclassifiedCandidates
            .filter { it.type == RuleCandidateType.ACTION }
            .map(RuleCandidate::text)
        val supplementaryCandidates = reclassifiedCandidates
            .filterNot { it.type == RuleCandidateType.SENDER || it.type == RuleCandidateType.VIOLATION || it.type == RuleCandidateType.ACTION }
            .map(RuleCandidate::text)

        val draftRule = buildRule(
            displayName = displayName,
            sampleText = normalizedSample,
            requiredKeywordGroups = buildList {
                if (senderCandidates.isNotEmpty()) add(senderCandidates)
                if (violationCandidates.isNotEmpty()) add(violationCandidates)
                if (actionCandidates.isNotEmpty()) add(actionCandidates)
            },
            supplementaryKeywords = supplementaryCandidates,
            minimumSupplementaryMatches = calculateMinimumMatches(supplementaryCandidates.size),
        )
        if (draftRule.matches(normalizedSample)) {
            return draftRule
        }

        val relaxedRule = draftRule.copy(minimumSupplementaryMatches = 0)
        if (relaxedRule.matches(normalizedSample)) {
            return relaxedRule
        }

        val actionAsSupplementary = buildRule(
            displayName = displayName,
            sampleText = normalizedSample,
            requiredKeywordGroups = buildList {
                if (senderCandidates.isNotEmpty()) add(senderCandidates)
                if (violationCandidates.isNotEmpty()) add(violationCandidates)
            },
            supplementaryKeywords = (supplementaryCandidates + actionCandidates).distinct(),
            minimumSupplementaryMatches = 0,
        )
        if (actionAsSupplementary.matches(normalizedSample)) {
            return actionAsSupplementary
        }

        val broadestRule = buildRule(
            displayName = displayName,
            sampleText = normalizedSample,
            requiredKeywordGroups = buildList {
                if (senderCandidates.isNotEmpty()) add(senderCandidates)
            },
            supplementaryKeywords = buildList {
                addAll(violationCandidates)
                addAll(actionCandidates)
                addAll(supplementaryCandidates)
                if (senderCandidates.isEmpty()) {
                    addAll(reclassifiedCandidates.map(RuleCandidate::text))
                }
            }.distinct(),
            minimumSupplementaryMatches = calculateBroadMinimumMatches(
                candidateCount = buildList {
                    addAll(violationCandidates)
                    addAll(actionCandidates)
                    addAll(supplementaryCandidates)
                    if (senderCandidates.isEmpty()) {
                        addAll(reclassifiedCandidates.map(RuleCandidate::text))
                    }
                }.distinct().size,
                hasRequiredGroups = senderCandidates.isNotEmpty(),
            ),
        )
        return broadestRule.takeIf { it.matches(normalizedSample) }
    }

    private fun normalizeCandidate(candidate: RuleCandidate): RuleCandidate? {
        val normalizedText = candidate.text.trim()
            .removePrefix("【")
            .removeSuffix("】")
            .replace(Regex("\\s+"), " ")

        if (normalizedText.length < 2) return null
        if (genericWeakKeywords.contains(normalizedText)) return null
        if (isMostlyNumeric(normalizedText)) return null

        return candidate.copy(text = normalizedText)
    }

    private fun buildRule(
        displayName: String,
        sampleText: String,
        requiredKeywordGroups: List<List<String>>,
        supplementaryKeywords: List<String>,
        minimumSupplementaryMatches: Int,
    ): SmsRule {
        return SmsRule(
            id = UUID.randomUUID().toString(),
            name = displayName,
            sampleText = sampleText,
            requiredKeywordGroups = requiredKeywordGroups
                .map { group -> group.map(String::trim).filter { it.length >= 2 }.distinct() }
                .filter(List<String>::isNotEmpty),
            supplementaryKeywords = supplementaryKeywords
                .map(String::trim)
                .filter { it.length >= 2 }
                .filterNot(genericWeakKeywords::contains)
                .distinct(),
            minimumSupplementaryMatches = minimumSupplementaryMatches,
            excludeKeywords = SmsRuleRepository.defaultExcludeKeywords,
            enabled = true,
            isBuiltIn = false,
            createdAt = System.currentTimeMillis(),
        )
    }

    private fun calculateMinimumMatches(candidateCount: Int): Int {
        return when {
            candidateCount >= 4 -> 2
            candidateCount >= 2 -> 1
            else -> 0
        }
    }

    private fun calculateBroadMinimumMatches(candidateCount: Int, hasRequiredGroups: Boolean): Int {
        if (candidateCount <= 0) return 0
        if (hasRequiredGroups) return 1.coerceAtMost(candidateCount)
        return when {
            candidateCount >= 4 -> 2
            candidateCount >= 2 -> 2
            else -> 1
        }
    }

    private fun extractSenderCandidate(sampleText: String): RuleCandidate? {
        val match = senderPattern.find(sampleText) ?: return null
        val senderText = match.groupValues[1]
            .trim()
            .replace(Regex("\\s+"), "")
            .take(MAX_SENDER_LENGTH)

        if (senderText.length !in 2..MAX_SENDER_LENGTH) return null
        if (isMostlyNumeric(senderText)) return null
        if (senderStopPhrases.any(senderText::contains)) return null

        return RuleCandidate(
            text = senderText,
            source = RuleCandidateSource.AUTO,
            type = RuleCandidateType.SENDER,
        )
    }

    private fun extractQuotedCandidates(sampleText: String): List<RuleCandidate> {
        return quotedPhrasePattern.findAll(sampleText)
            .map { it.groupValues[1].trim() }
            .filter { it.length in 4..MAX_CLAUSE_LENGTH }
            .filterNot(::isMostlyNumeric)
            .filterNot(stopPhrases::contains)
            .map { candidateText ->
                RuleCandidate(
                    text = candidateText,
                    source = RuleCandidateSource.AUTO,
                    type = classifyCandidateType(candidateText),
                )
            }
            .toList()
    }

    private fun extractSeedCandidates(sampleText: String): List<RuleCandidate> {
        val compact = sampleText.compactForMatching()
        return keywordSeeds
            .filter(compact::contains)
            .map { seed ->
                RuleCandidate(
                    text = seed,
                    source = RuleCandidateSource.AUTO,
                    type = classifyCandidateType(seed),
                )
            }
    }

    private fun extractClauseCandidates(sampleText: String): List<RuleCandidate> {
        return sampleText
            .replace("【", " ")
            .replace("】", " ")
            .split(Regex("[,，。；;：:\\n]"))
            .map(String::trim)
            .filter { it.length in 4..MAX_CLAUSE_LENGTH }
            .filterNot(::isMostlyNumeric)
            .filterNot(stopPhrases::contains)
            .filterNot(genericWeakKeywords::contains)
            .filterNot(::looksLikeVehiclePlate)
            .sortedByDescending(String::length)
            .map { clause ->
                RuleCandidate(
                    text = clause,
                    source = RuleCandidateSource.AUTO,
                    type = classifyCandidateType(clause),
                )
            }
    }

    private fun classifyCandidateType(text: String): RuleCandidateType {
        return when {
            violationKeywords.any(text::contains) -> RuleCandidateType.VIOLATION
            actionKeywords.any(text::contains) -> RuleCandidateType.ACTION
            isStrongSenderCandidate(text) -> RuleCandidateType.SENDER
            locationKeywords.any(text::contains) -> RuleCandidateType.LOCATION
            else -> RuleCandidateType.OTHER
        }
    }

    private fun isStrongSenderCandidate(text: String): Boolean {
        val normalizedText = text.trim()
            .removePrefix("【")
            .removeSuffix("】")
            .replace(Regex("\\s+"), "")

        if (normalizedText.length !in 2..MAX_SENDER_LENGTH) return false
        if (normalizedText.any(Char::isDigit)) return false
        if (looksLikeVehiclePlate(normalizedText)) return false
        if (senderStopWords.any(normalizedText::contains)) return false
        if (senderExactPhrases.contains(normalizedText)) return true

        return senderKeywords.any(normalizedText::contains) &&
            senderEntitySuffixes.any(normalizedText::endsWith)
    }

    private fun looksLikeVehiclePlate(text: String): Boolean {
        return vehiclePlatePattern.containsMatchIn(text)
    }

    private fun candidatePriority(it: RuleCandidate): Int {
        return when (classifyCandidateType(it.text)) {
            RuleCandidateType.SENDER -> 0
            RuleCandidateType.VIOLATION -> 1
            RuleCandidateType.ACTION -> 2
            RuleCandidateType.LOCATION -> 3
            RuleCandidateType.OTHER -> 4
        }
    }

    private fun isMostlyNumeric(text: String): Boolean {
        val cleaned = text.filterNot(Char::isWhitespace)
        if (cleaned.isEmpty()) return true
        val nonNumericCount = cleaned.count { !it.isDigit() }
        return nonNumericCount <= 2
    }

    private val keywordSeeds = listOf(
        "上海交警",
        "公安局交通管理支队",
        "公安局交通警察支队",
        "违停",
        "违章停车",
        "违法停车",
        "违反规定停放",
        "未按规定停放",
        "临时停车",
        "拒绝立即驶离",
        "请立即驶离",
        "未及时驶离",
        "依法予以处罚",
        "接受处理",
        "已被记录",
        "记录了",
        "交通技术监控设备",
        "电子警察",
    )

    private val strongKeywordSeeds = setOf(
        "上海交警",
        "违停",
        "违章停车",
        "违法停车",
        "违反规定停放",
        "未按规定停放",
        "已被记录",
        "记录了",
        "请立即驶离",
        "拒绝立即驶离",
        "未及时驶离",
        "依法予以处罚",
        "接受处理",
        "处罚",
    )

    private val senderKeywords = listOf(
        "交警",
        "交管",
        "公安局",
        "交通管理",
        "交通警察",
        "支队",
        "大队",
        "中队",
        "总队",
    )

    private val violationKeywords = listOf(
        "违停",
        "违章停车",
        "违法停车",
        "违反规定停放",
        "未按规定停放",
        "临时停车",
        "驾驶人不在现场",
        "拒绝立即驶离",
        "妨碍其他车辆",
    )

    private val actionKeywords = listOf(
        "请立即驶离",
        "未及时驶离",
        "依法予以处罚",
        "接受处理",
        "立即驶离",
    )

    private val coreActionKeywords = listOf(
        "请立即驶离",
        "未及时驶离",
        "依法予以处罚",
        "接受处理",
        "立即驶离",
    )

    private val locationKeywords = listOf(
        "路",
        "大道",
        "路段",
        "街",
        "巷",
        "米",
        "路口",
    )

    private val genericWeakKeywords = setOf(
        "停车",
        "提醒",
        "通知",
        "车主",
        "机动车",
        "谢谢配合",
    )

    private val senderStopPhrases = setOf(
        "验证码",
        "快递",
        "支付",
        "退款",
        "促销",
    )

    private val senderStopWords = setOf(
        "您的",
        "于",
        "在",
        "被",
        "记录",
        "处理",
        "驶离",
        "违法",
        "停车",
        "APP",
        "告知",
        "提示",
    )

    private val senderExactPhrases = setOf(
        "上海交警",
        "交警",
    )

    private val senderEntitySuffixes = listOf(
        "交警",
        "交管",
        "公安局",
        "大队",
        "中队",
        "支队",
        "总队",
        "交通管理支队",
        "交通警察支队",
        "交通警察总队",
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

    private val senderPattern = Regex("^\\s*[【\\[](.*?)[】\\]]")
    private val quotedPhrasePattern = Regex("[『“\"]([^』”\"]{4,40})[』”\"]")
    private val vehiclePlatePattern = Regex("[\\u4e00-\\u9fa5][A-Z][A-Z0-9]{5,6}", RegexOption.IGNORE_CASE)

    private const val MIN_SAMPLE_LENGTH = 8
    private const val MAX_KEYWORDS = 10
    private const val MAX_CLAUSE_LENGTH = 30
    private const val MAX_SENDER_LENGTH = 24
    private const val RULE_NAME_PREVIEW_LENGTH = 16
}
