package com.qingji.app.domain

import com.qingji.app.data.local.entity.WorkoutExercise
import org.junit.Assert.assertEquals
import org.junit.Test

/** 肌群分布：按 UUID 归到主分类，找不到的进「未分类」，不硬塞。 */
class MuscleDistributionCalculatorTest {

    private val library = listOf(
        "ex-bench" to "chest",
        "ex-squat" to "legs",
        "ex-pull" to "back"
    )

    private fun row(uuid: String, sets: List<WorkoutSet>) = WorkoutExercise(
        exerciseUuid = uuid,
        exerciseNameSnapshot = uuid,
        setsJson = TrainingMath.encodeSets(sets)
    )

    @Test
    fun `空历史也返回八个零值桶`() {
        val r = MuscleDistributionCalculator.compute(library, emptyList())
        assertEquals(8, r.size)
        assertEquals(listOf("chest", "back", "shoulders", "arms", "legs", "core", "mobility", "other"),
            r.map { it.key })
        assertEquals(0, r.sumOf { it.sets })
    }

    @Test
    fun `按UUID归到主分类并累加组数和训练量`() {
        val r = MuscleDistributionCalculator.compute(
            library,
            listOf(
                row("ex-bench", listOf(WorkoutSet(50.0, 10), WorkoutSet(50.0, 8))),
                row("ex-bench", listOf(WorkoutSet(55.0, 6)))
            )
        )
        val chest = r.first { it.key == "chest" }
        assertEquals(3, chest.sets)
        assertEquals(50.0 * 10 + 50.0 * 8 + 55.0 * 6, chest.volumeKg, 0.0)
        assertEquals(2, chest.exerciseCount)  // 两条明细
    }

    @Test
    fun `找不到UUID的进未分类 不硬塞`() {
        val r = MuscleDistributionCalculator.compute(
            library,
            listOf(row("deleted-exercise", listOf(WorkoutSet(20.0, 10))))
        )
        assertEquals(0, r.first { it.key == "chest" }.sets)
        assertEquals(1, r.first { it.key == "other" }.sets)
        assertEquals(200.0, r.first { it.key == "other" }.volumeKg, 0.0)
    }

    @Test
    fun `徒手动作训练量为0但组数照算`() {
        val r = MuscleDistributionCalculator.compute(
            library,
            listOf(row("ex-bench", listOf(WorkoutSet(0.0, 20), WorkoutSet(0.0, 15))))
        )
        val chest = r.first { it.key == "chest" }
        assertEquals(2, chest.sets)
        assertEquals(0.0, chest.volumeKg, 0.0)
    }
}
