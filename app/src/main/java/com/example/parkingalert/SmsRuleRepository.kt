package com.example.parkingalert

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class SmsRuleRepository(private val context: Context) {

    fun getRules(): List<SmsRule> {
        if (!prefs.contains(PREF_RULES)) {
            saveRules(builtInRules)
        }

        return readPersistedRules()
            .sortedWith(compareByDescending<SmsRule> { it.isBuiltIn }.thenByDescending { it.createdAt })
    }

    fun addRule(rule: SmsRule) {
        val updatedRules = getRules().filterNot { it.id == rule.id } + rule
        saveRules(updatedRules)
    }

    fun updateRuleEnabled(ruleId: String, enabled: Boolean) {
        val updatedRules = getRules().map { rule ->
            if (rule.id == ruleId) rule.copy(enabled = enabled) else rule
        }
        saveRules(updatedRules)
    }

    fun deleteRule(ruleId: String) {
        val updatedRules = getRules().filterNot { it.id == ruleId }
        saveRules(updatedRules)
    }

    private fun readPersistedRules(): List<SmsRule> {
        val raw = prefs.getString(PREF_RULES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toRule())
                }
            }
        } catch (_: JSONException) {
            prefs.edit().remove(PREF_RULES).apply()
            emptyList()
        }
    }

    private fun saveRules(rules: List<SmsRule>) {
        val json = JSONArray().apply {
            rules.forEach { put(it.toJson()) }
        }
        prefs.edit().putString(PREF_RULES, json.toString()).apply()
    }

    private fun SmsRule.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("sampleText", sampleText)
            put("requiredKeywordGroups", JSONArray().apply {
                requiredKeywordGroups.forEach { group ->
                    put(JSONArray().apply { group.forEach(::put) })
                }
            })
            put("supplementaryKeywords", JSONArray().apply { supplementaryKeywords.forEach(::put) })
            put("minimumSupplementaryMatches", minimumSupplementaryMatches)
            put("excludeKeywords", JSONArray().apply { excludeKeywords.forEach(::put) })
            put("enabled", enabled)
            put("isBuiltIn", isBuiltIn)
            put("createdAt", createdAt)
        }
    }

    private fun JSONObject.toRule(): SmsRule {
        return SmsRule(
            id = getString("id"),
            name = getString("name"),
            sampleText = getString("sampleText"),
            requiredKeywordGroups = getJSONArray("requiredKeywordGroups").toStringGroups(),
            supplementaryKeywords = getJSONArray("supplementaryKeywords").toStringList(),
            minimumSupplementaryMatches = getInt("minimumSupplementaryMatches"),
            excludeKeywords = getJSONArray("excludeKeywords").toStringList(),
            enabled = getBoolean("enabled"),
            isBuiltIn = getBoolean("isBuiltIn"),
            createdAt = getLong("createdAt"),
        )
    }

    private fun JSONArray.toStringList(): List<String> {
        return buildList {
            for (index in 0 until length()) {
                add(getString(index))
            }
        }
    }

    private fun JSONArray.toStringGroups(): List<List<String>> {
        return buildList {
            for (index in 0 until length()) {
                add(getJSONArray(index).toStringList())
            }
        }
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREFS_NAME = "parking_alert_rules"
        private const val PREF_RULES = "rules"

        val defaultExcludeKeywords = listOf(
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
            "体检",
        )

        private val builtInRules = listOf(
            SmsRule(
                id = "builtin-shanghai-parking",
                name = "上海交警违停短信",
                sampleText = "【上海交警】您的小型新能源汽车沪BA63671于2026年5月12日10时42分在亮景路进博云路南约37米未按规定停放已被记录，请立即驶离，未及时驶离的，将依法予以处罚。",
                requiredKeywordGroups = listOf(
                    listOf("上海交警"),
                    listOf("未按规定停放", "已被记录"),
                    listOf("请立即驶离", "未及时驶离", "依法予以处罚"),
                ),
                supplementaryKeywords = emptyList(),
                minimumSupplementaryMatches = 0,
                excludeKeywords = defaultExcludeKeywords,
                enabled = true,
                isBuiltIn = true,
                createdAt = 1L,
            ),
        )
    }
}
