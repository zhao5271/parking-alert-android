package com.example.parkingalert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleGeneratorTest {

    @Test
    fun `extractCandidates should keep sender entity from bracket prefix`() {
        val sample = "【六安市公安局交通管理支队】您的小型汽车皖N85C85于2025年12月13日15:02在六安市长安路皖西大道至皋城路段，被交通技术监控设备记录了『违反规定停放、临时停车且驾驶人不在现场或驾驶人虽在现场拒绝立即驶离，妨碍其他车辆、行人通行的』的违法行为。请于收到本告知之日起30日内接受处理。"

        val candidates = RuleGenerator.extractCandidates(sample)

        assertTrue(candidates.any { it.text == "六安市公安局交通管理支队" && it.type == RuleCandidateType.SENDER })
    }

    @Test
    fun `generate should reclassify edited text instead of trusting stale sender type`() {
        val sample = "您在处理交通违法后，可登录交管12123APP，参加学法减分教育。"
        val editedCandidate = RuleCandidate(
            text = "交管12123APP",
            source = RuleCandidateSource.AUTO,
            type = RuleCandidateType.SENDER,
        )

        val generatedRule = RuleGenerator.generate(
            nameInput = "",
            sampleText = sample,
            selectedCandidates = listOf(editedCandidate),
        )

        assertNull(generatedRule)
    }

    @Test
    fun `generate should still build a rule for real violation sample`() {
        val sample = "【六安市公安局交通管理支队】您的小型汽车皖N85C85于2025年12月13日15:02在六安市长安路皖西大道至皋城路段，被交通技术监控设备记录了『违反规定停放、临时停车且驾驶人不在现场或驾驶人虽在现场拒绝立即驶离，妨碍其他车辆、行人通行的』的违法行为。请于收到本告知之日起30日内接受处理。（温馨提示）您在处理交通违法后，可登录“交管12123APP”，参加“学法减分”教育，减免交通违法记分。"
        val candidates = RuleGenerator.extractCandidates(sample)

        val generatedRule = RuleGenerator.generate(
            nameInput = "",
            sampleText = sample,
            selectedCandidates = candidates,
        )

        assertNotNull(generatedRule)
        assertTrue(generatedRule!!.matches(sample))
        assertEquals("六安市公安局交通管理支队", generatedRule.requiredKeywordGroups.first().first())
        assertFalse(generatedRule.excludeKeywords.contains("登录"))
    }

    @Test
    fun `generate should allow checked items even without strong semantic type`() {
        val sample = "亮景路进博云路南约37米未按规定停放已被记录"
        val selectedCandidates = listOf(
            RuleCandidate(
                text = "亮景路",
                source = RuleCandidateSource.AUTO,
                type = RuleCandidateType.LOCATION,
            ),
            RuleCandidate(
                text = "进博云路",
                source = RuleCandidateSource.AUTO,
                type = RuleCandidateType.LOCATION,
            ),
        )

        val generatedRule = RuleGenerator.generate(
            nameInput = "",
            sampleText = sample,
            selectedCandidates = selectedCandidates,
        )

        assertNotNull(generatedRule)
        assertTrue(generatedRule!!.matches(sample))
    }

    @Test
    fun `extractCandidatesForGeneration should produce a directly generatable checked subset`() {
        val sample = "【上海交警】您的小型新能源汽车沪BA63671于2026年5月12日10时42分在亮景路进博云路南约37米未按规定停放已被记录，请立即驶离，未及时驶离的，将依法予以处罚。"

        val recommendedCandidates = RuleGenerator.extractCandidatesForGeneration(sample)
        val generatedRule = RuleGenerator.generate(
            nameInput = "",
            sampleText = sample,
            selectedCandidates = recommendedCandidates,
        )

        assertTrue(recommendedCandidates.isNotEmpty())
        assertNotNull(generatedRule)
        assertTrue(generatedRule!!.matches(sample))
    }
}
