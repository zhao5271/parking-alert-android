package com.example.parkingalert

data class SmsRule(
    val id: String,
    val name: String,
    val sampleText: String,
    val requiredKeywordGroups: List<List<String>>,
    val supplementaryKeywords: List<String>,
    val minimumSupplementaryMatches: Int,
    val excludeKeywords: List<String>,
    val enabled: Boolean,
    val isBuiltIn: Boolean,
    val createdAt: Long,
) {
    fun matches(messageBody: String): Boolean {
        if (!enabled) return false

        val compact = messageBody.compactForMatching()
        if (excludeKeywords.any(compact::contains)) return false
        if (requiredKeywordGroups.any { group -> group.none(compact::contains) }) return false

        val supplementaryHits = supplementaryKeywords.count(compact::contains)
        return supplementaryHits >= minimumSupplementaryMatches
    }

    fun keywordPreview(limit: Int = 4): String {
        val keywords = (requiredKeywordGroups.flatten() + supplementaryKeywords).distinct()
        return keywords.take(limit).joinToString(" / ")
    }
}

internal fun String.compactForMatching(): String {
    return lowercase()
        .replace(Regex("\\s+"), "")
        .replace("，", "")
        .replace("。", "")
        .replace("：", "")
        .replace("；", "")
        .replace(",", "")
        .replace(".", "")
        .replace(":", "")
        .replace(";", "")
        .replace("\n", "")
        .replace("\r", "")
}
