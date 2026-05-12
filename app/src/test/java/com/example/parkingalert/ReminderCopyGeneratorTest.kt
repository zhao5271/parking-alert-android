package com.example.parkingalert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderCopyGeneratorTest {

    @Test
    fun `generate should build concise parking reminder copy`() {
        val payload = ReminderCopyGenerator.generate(
            messageBody = "【上海交警】您的小型新能源汽车沪BA63671于2026年5月12日10时42分在亮景路进博云路南约37米未按规定停放已被记录，请立即驶离，未及时驶离的，将依法予以处罚。",
            matchedRuleName = "上海交警违停短信",
        )

        assertEquals(ReminderScene.PARKING_MOVE, payload.scene)
        assertEquals(ReminderUrgency.IMMEDIATE, payload.urgency)
        assertEquals("立即挪车", payload.title)
        assertEquals("亮景路附近，请立即驶离", payload.body)
    }

    @Test
    fun `generate should build concise repayment reminder copy`() {
        val payload = ReminderCopyGenerator.generate(
            messageBody = "【招商银行】您本期信用卡账单今日到期，请尽快还款，以免影响征信。",
            matchedRuleName = "招行信用卡还款",
        )

        assertEquals(ReminderScene.CREDIT_CARD_REPAYMENT, payload.scene)
        assertEquals(ReminderUrgency.DEADLINE, payload.urgency)
        assertEquals("今日还款", payload.title)
        assertEquals("招行信用卡今日到期", payload.body)
    }

    @Test
    fun `generate should fallback to generic important copy`() {
        val payload = ReminderCopyGenerator.generate(
            messageBody = "【物业中心】您有新的社区通知，请尽快查看。",
        )

        assertEquals(ReminderScene.GENERIC_IMPORTANT, payload.scene)
        assertEquals("重要提醒", payload.title)
        assertTrue(payload.body.contains("请尽快查看"))
    }
}
