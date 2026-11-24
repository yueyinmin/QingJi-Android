package com.qingji.app.ai

import com.qingji.app.domain.AppClock
import com.qingji.app.ui.navigation.QingJiRoutes
import com.qingji.app.ui.screen.aiAnalysisDateLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * QJ-AI-HISTORY-DATE-V1：导航日期参数解析 + 页面日期文案。
 * 纯 JVM 单测，不需要 Robolectric。
 */
class AiHistoryDateNavigationTest {

    private val today = AppClock.today()

    @Test
    fun `路由构造函数生成 ISO 日期`() {
        val date = LocalDate.of(2026, 11, 20)
        assertEquals("ai_analysis/2026-11-20", QingJiRoutes.aiAnalysis(date))
        assertTrue(QingJiRoutes.AiAnalysisWithDate.contains("{date}"))
    }

    @Test
    fun `解析合法日期参数`() {
        assertEquals(LocalDate.of(2026, 11, 20), QingJiRoutes.aiAnalysisDate("2026-11-20"))
    }

    @Test
    fun `空参数默认今天`() {
        assertEquals(today, QingJiRoutes.aiAnalysisDate(null))
        assertEquals(today, QingJiRoutes.aiAnalysisDate(""))
    }

    @Test
    fun `非法日期参数回退今天`() {
        assertEquals(today, QingJiRoutes.aiAnalysisDate("not-a-date"))
        assertEquals(today, QingJiRoutes.aiAnalysisDate("2026-13-40"))
        assertEquals(today, QingJiRoutes.aiAnalysisDate("2026/11/20"))
    }

    @Test
    fun `带日期路由已注册为全屏页面`() {
        assertTrue(QingJiRoutes.AiAnalysis in QingJiRoutes.fullScreenRoutes)
        assertTrue(QingJiRoutes.AiAnalysisWithDate in QingJiRoutes.fullScreenRoutes)
    }

    @Test
    fun `页面文案 今日 昨日 历史日期`() {
        assertEquals("今日复盘", aiAnalysisDateLabel(today, today))
        assertEquals("昨日复盘", aiAnalysisDateLabel(today.minusDays(1), today))
        assertEquals("2026年11月20日复盘", aiAnalysisDateLabel(LocalDate.of(2026, 11, 20), today))
    }
}
