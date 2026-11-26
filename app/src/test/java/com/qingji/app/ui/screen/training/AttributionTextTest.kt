package com.qingji.app.ui.screen.training

import org.junit.Assert.assertEquals
import org.junit.Test

/** 素材署名不能把 AI 生成图泛化成 CC BY-SA。 */
class AttributionTextTest {

    @Test
    fun aiGeneratedImageKeepsItsOwnLicense() {
        val raw = """{"creator":"Seedream (Z.AI)","license":"AI-generated"}"""
        val text = attributionText(raw)
        assertEquals(true, text.contains("Seedream"))
        assertEquals(true, text.contains("AI 生成"))
        assertEquals(false, text.contains("CC BY-SA"))
    }

    @Test
    fun workoutGuideStillUsesCcBySa() {
        val raw = """{"creator":"Bryl Lim","license":"CC BY-SA 4.0"}"""
        assertEquals("Bryl Lim · CC BY-SA 4.0", attributionText(raw))
    }

    @Test
    fun everkineticKeepsJointAttribution() {
        val raw = """{"creator":"Bryl Lim / Everkinetic","license":"CC BY-SA 4.0"}"""
        assertEquals("Bryl Lim / Everkinetic · CC BY-SA 4.0", attributionText(raw))
    }

    @Test
    fun blankOrBrokenAttributionIsSafe() {
        assertEquals("未标注", attributionText(""))
        assertEquals("未标注", attributionText("not json"))
    }
}
