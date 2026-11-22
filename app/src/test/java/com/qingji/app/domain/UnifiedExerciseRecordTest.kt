package com.qingji.app.domain

import com.qingji.app.data.local.entity.ExerciseLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** G2 复核：UnifiedExerciseRecord 去重语义——按来源+关联关系，不只看字段空否。 */
class UnifiedExerciseRecordTest {

    private fun log(sourceId: String, type: String = "跑步") = ExerciseLog(
        date = LocalDate.of(2026, 11, 21), type = type, minutes = 30,
        kcal = 200.0, sourceId = sourceId
    )

    @Test
    fun `独立快速补记sourceId空串参与统计`() {
        val r = UnifiedExerciseRecord.fromExerciseLog(log(""))
        assertNotNull(r)
        assertEquals(UnifiedExerciseRecord.Source.QUICK_LOG, r!!.source)
        assertEquals(30 * 60L, r.durationSec)
    }

    @Test
    fun `正式训练日记投影带sessionUuid被过滤`() {
        val r = UnifiedExerciseRecord.fromExerciseLog(
            log("session-abc", "力量"), sessionUuids = setOf("session-abc")
        )
        assertNull(r) // 一场训练一场，不重复统计
    }

    @Test
    fun `历史导入带sourceId但无对应session的独立记录不被误删`() {
        // ChatGPT 质疑点：sourceId 非空 ≠ 一定是日记投影
        val r = UnifiedExerciseRecord.fromExerciseLog(
            log("legacy-import-123"), sessionUuids = setOf("session-abc")
        )
        assertNotNull(r)
        assertEquals(UnifiedExerciseRecord.Source.QUICK_LOG, r!!.source)
    }

    @Test
    fun `未知来源ID不会误过滤且类型安全降级`() {
        val r = UnifiedExerciseRecord.fromExerciseLog(
            log("unknown-source", "奇怪运动"), sessionUuids = emptySet()
        )
        assertNotNull(r)
        assertEquals(ExerciseRecordType.OTHER, r!!.type)
    }
}
