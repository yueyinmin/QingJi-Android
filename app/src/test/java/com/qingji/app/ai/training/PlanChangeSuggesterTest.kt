package com.qingji.app.ai.training

import com.qingji.app.data.local.entity.TrainingPlan
import com.qingji.app.domain.ExerciseRecordType
import com.qingji.app.domain.TrustLevel.CALCULATED
import com.qingji.app.domain.TypeSummary
import com.qingji.app.domain.TrainingAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanChangeSuggesterTest {

    private fun analysis(sessions: Int, minGap: Int?): TrainingAnalysis {
        val strength = TypeSummary(
            type = ExerciseRecordType.STRENGTH,
            sessions = sessions,
            totalSets = sessions * 6,
            volumeKg = sessions * 900.0,
            durationSec = 0L,
            averageRpe = 8.0,
            sessionsPerWeek = sessions * 7.0 / 28,
            minGapDays = minGap,
            trust = CALCULATED
        )
        return TrainingAnalysis(
            windowDays = 28,
            totals = mapOf(ExerciseRecordType.STRENGTH to strength),
            insufficientData = false,
            warnings = emptyList()
        )
    }

    @Test
    fun `训练过密生成停用提案且带当前baseVersion`() {
        val active = TrainingPlan("p1", "当前计划", isActive = true, version = 7L)
        val suggestion = PlanChangeSuggester.suggest(analysis(8, 0), listOf(active))!!

        assertEquals("PLAN_DEACTIVATE", suggestion.proposalType)
        assertEquals("p1", suggestion.targetRecordId)
        assertEquals(7L, suggestion.baseVersion)
        assertTrue(suggestion.beforeJson.contains("\"isActive\":true"))
        assertTrue(suggestion.afterJson.contains("\"isActive\":false"))
        assertTrue(suggestion.afterJson.contains("\"version\":8"))
    }

    @Test
    fun `没有力量训练时生成启用提案`() {
        val inactive = TrainingPlan("p2", "轻量计划", isActive = false, version = 3L)
        val suggestion = PlanChangeSuggester.suggest(analysis(0, null), listOf(inactive))!!

        assertEquals("PLAN_ACTIVATE", suggestion.proposalType)
        assertEquals(3L, suggestion.baseVersion)
        assertTrue(suggestion.reason.contains("没有力量训练"))
    }

    @Test
    fun `没有可操作计划时返回null`() {
        assertNull(PlanChangeSuggester.suggest(analysis(0, null), emptyList()))
    }
}
