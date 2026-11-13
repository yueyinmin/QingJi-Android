package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 训练中心的纯计算：改一行公式就能在这里立刻发现，不用装 App 点半天。 */
class TrainingMathTest {

    @Test
    fun `1RM 估算用 Epley 公式`() {
        assertEquals(10.0, TrainingMath.estimate1RM(10.0, 1), 0.001)   // 只举 1 次就是重量本身
        assertEquals(14.0, TrainingMath.estimate1RM(10.0, 12), 0.001)  // 10 × (1 + 12/30)
    }

    @Test
    fun `非法输入返回 0 而不是负数或崩溃`() {
        assertEquals(0.0, TrainingMath.estimate1RM(0.0, 10), 0.001)
        assertEquals(0.0, TrainingMath.estimate1RM(10.0, 0), 0.001)
        assertEquals(0.0, TrainingMath.estimate1RM(-5.0, 10), 0.001)
    }

    @Test
    fun `训练量等于每组重量乘次数再加总`() {
        val sets = listOf(WorkoutSet(10.0, 12), WorkoutSet(12.5, 10), WorkoutSet(0.0, 30))
        assertEquals(245.0, TrainingMath.sessionVolume(sets), 0.001)
    }

    @Test
    fun `kg 与 lb 往返换算不丢精度`() {
        val lb = TrainingMath.kgToLb(20.0)
        assertEquals(20.0, TrainingMath.lbToKg(lb), 0.0001)
    }

    @Test
    fun `组数据打包再解开保持原样`() {
        val sets = listOf(
            WorkoutSet(12.5, 10, 8, 90),
            WorkoutSet(0.0, 30, 0, 0)
        )
        val decoded = TrainingMath.decodeSets(TrainingMath.encodeSets(sets))
        assertEquals(sets, decoded)
    }

    @Test
    fun `坏掉的组数据不会让整页历史崩掉`() {
        assertTrue(TrainingMath.decodeSets(null).isEmpty())
        assertTrue(TrainingMath.decodeSets("").isEmpty())
        assertTrue(TrainingMath.decodeSets("这不是 JSON").isEmpty())
        // 一条坏记录跳过，好记录照常读出来
        val decoded = TrainingMath.decodeSets("[{\"w\":10,\"r\":8},{\"w\":\"坏\",\"r\":5}]")
        assertEquals(2, decoded.size)
        assertEquals(0.0, decoded[1].weightKg, 0.001)
    }

    @Test
    fun `时长文案按秒换算`() {
        assertEquals("00:45", TrainingMath.formatDuration(45))
        assertEquals("01:02:03", TrainingMath.formatDuration(3723))
        assertEquals("00:00", TrainingMath.formatDuration(-5))
    }
}
