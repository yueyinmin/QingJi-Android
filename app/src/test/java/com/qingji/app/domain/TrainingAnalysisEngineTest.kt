package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrainingAnalysisEngineTest {

    private val today = LocalDate.parse("2026-11-25")

    @Test
    fun `三类运动分开统计且只算窗口内记录`() {
        val strength = WorkoutItem.Strength(
            itemId = "s1", exerciseUuid = "ex-1", name = "卧推", order = 0,
            sets = listOf(
                WorkoutSet(weightKg = 50.0, reps = 10, rpe = 8),
                WorkoutSet(weightKg = 50.0, reps = 8, rpe = 9)
            )
        )
        val cardio = WorkoutItem.Cardio(
            itemId = "c1", exerciseUuid = "ex-run", name = "跑步", order = 0,
            durationSec = 1800L, intensity = "中"
        )
        val hiit = WorkoutItem.Hiit(
            itemId = "h1", exerciseUuid = "ex-hiit", name = "间歇", order = 0,
            durationSec = 600L, intensity = "高"
        )
        val snapshots = listOf(
            TrainingSessionSnapshot(today.minusDays(1), 40, listOf(strength, cardio)),
            TrainingSessionSnapshot(today.minusDays(3), 20, listOf(hiit)),
            TrainingSessionSnapshot(today.minusDays(60), 30, listOf(strength))
        )

        val analysis = TrainingAnalysisEngine.analyze(snapshots, today, windowDays = 28)

        val s = analysis.summary(ExerciseRecordType.STRENGTH)!!
        assertEquals(1, s.sessions)
        assertEquals(2, s.totalSets)
        assertEquals(900.0, s.volumeKg, 0.001)
        assertEquals(8.5, s.averageRpe!!, 0.001)
        assertEquals(TrustLevel.CALCULATED, s.trust)

        val c = analysis.summary(ExerciseRecordType.CARDIO)!!
        assertEquals(1800L, c.durationSec)
        assertEquals(1, c.sessions)

        val h = analysis.summary(ExerciseRecordType.HIIT)!!
        assertEquals(600L, h.durationSec)
        assertEquals(1, h.sessions)
        assertNull(analysis.summary(ExerciseRecordType.FLEXIBILITY))
    }

    @Test
    fun `数据不足只标UNKNOWN不伪造恢复指标`() {
        val analysis = TrainingAnalysisEngine.analyze(emptyList(), today, windowDays = 28)

        assertTrue(analysis.insufficientData)
        assertTrue(analysis.warnings.any { it.contains("不足 3 次") })
        assertEquals(TrustLevel.UNKNOWN, analysis.summary(ExerciseRecordType.STRENGTH)!!.trust)
        assertNull(analysis.summary(ExerciseRecordType.STRENGTH)!!.minGapDays)
    }
}
