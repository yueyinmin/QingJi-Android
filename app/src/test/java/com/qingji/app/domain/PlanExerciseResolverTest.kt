package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 模板动作解析：UUID 优先，名字只给老数据兜底。 */
class PlanExerciseResolverTest {

    private val uuids = setOf("ex-bench", "ex-squat")
    private val names = mapOf("卧推" to "ex-bench", "深蹲" to "ex-squat")

    @Test
    fun `UUID命中就用UUID 名字改了也不断链`() {
        val r = PlanExerciseResolver.resolve(
            PlanExerciseResolver.Ref("ex-bench", "杠铃卧推（改名后）"), uuids, names
        )
        assertFalse(r.isMissing)
        assertEquals("ex-bench", r.exerciseUuid)
    }

    @Test
    fun `老数据UUID为空 退回名字匹配`() {
        val r = PlanExerciseResolver.resolve(
            PlanExerciseResolver.Ref("", "深蹲"), uuids, names
        )
        assertFalse(r.isMissing)
        assertEquals("ex-squat", r.exerciseUuid)
    }

    @Test
    fun `UUID失效但名字还在 用名字那条兜底`() {
        val r = PlanExerciseResolver.resolve(
            PlanExerciseResolver.Ref("deleted-uuid", "卧推"), uuids, names
        )
        assertFalse(r.isMissing)
        assertEquals("ex-bench", r.exerciseUuid)
    }

    @Test
    fun `UUID和名字都找不到 返回缺失名单`() {
        val r = PlanExerciseResolver.resolve(
            PlanExerciseResolver.Ref("gone-uuid", "已删除的自建动作"), uuids, names
        )
        assertTrue(r.isMissing)
        assertEquals("", r.exerciseUuid)
        assertEquals("已删除的自建动作", r.missingName)
    }

    @Test
    fun `名字为空不算缺失 直接跳过`() {
        val r = PlanExerciseResolver.resolve(
            PlanExerciseResolver.Ref("gone-uuid", ""), uuids, names
        )
        assertFalse(r.isMissing)
    }
}
