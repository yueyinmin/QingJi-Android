package com.qingji.app.domain

import com.qingji.app.data.local.entity.WorkoutExercise
import com.qingji.app.data.local.entity.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 个人最佳：按「动作名快照」聚合，改名/删动作都不影响历史成绩。 */
class PersonalBestCalculatorTest {

    private fun session(uuid: String, date: String) = WorkoutSession(
        uuid = uuid,
        date = LocalDate.parse(date),
        startedAt = 1L, endedAt = 2L, durationMinutes = 30, createdAt = 2L
    )

    private fun row(session: String, name: String, setsJson: String, id: Long = 0L) = WorkoutExercise(
        id = id,
        sessionUuid = session,
        exerciseUuid = "ex-1",
        exerciseNameSnapshot = name,
        setsJson = setsJson
    )

    @Test
    fun `空历史返回空列表`() {
        assertEquals(emptyList<PersonalBestCalculator.Best>(),
            PersonalBestCalculator.compute(emptyList(), emptyList()))
    }

    @Test
    fun `最大重量与估算 1RM 按所有组取最好`() {
        val sessions = listOf(session("s1", "2026-11-01"), session("s2", "2026-11-13"))
        val rows = listOf(
            row("s1", "哑铃卧推", TrainingMath.encodeSets(listOf(WorkoutSet(10.0, 12), WorkoutSet(12.0, 8)))),
            row("s2", "哑铃卧推", TrainingMath.encodeSets(listOf(WorkoutSet(15.0, 5), WorkoutSet(8.0, 20))))
        )
        val best = PersonalBestCalculator.compute(sessions, rows).single()
        assertEquals("哑铃卧推", best.name)
        assertEquals(15.0, best.maxWeightKg, 0.001)
        assertEquals(5, best.repsAtMax)
        assertEquals(2, best.sessionCount)
        assertEquals(4, best.totalSets)
        // 15×(1+5/30)=17.5，8×(1+20/30)=13.33，10×1.4=14，12×1.266=15.2 → 17.5 最大
        assertEquals(17.5, best.best1RM, 0.001)
        assertEquals(LocalDate.parse("2026-11-13"), best.date)
    }

    @Test
    fun `动作改名后历史仍归在原来的快照名下`() {
        val sessions = listOf(session("s1", "2026-11-13"))
        val rows = listOf(
            row("s1", "旧名字", TrainingMath.encodeSets(listOf(WorkoutSet(20.0, 5)))),
            row("s1", "新名字", TrainingMath.encodeSets(listOf(WorkoutSet(30.0, 3))), id = 1L)
        )
        val result = PersonalBestCalculator.compute(sessions, rows)
        assertEquals(2, result.size)
        assertEquals(setOf("旧名字", "新名字"), result.map { it.name }.toSet())
    }

    @Test
    fun `坏掉的组数据不拖累整份统计`() {
        val sessions = listOf(session("s1", "2026-11-13"))
        val rows = listOf(row("s1", "俯卧撑", "不是 JSON"))
        val best = PersonalBestCalculator.compute(sessions, rows).single()
        assertEquals(0.0, best.maxWeightKg, 0.001)
        assertEquals(0, best.totalSets)
    }
}
