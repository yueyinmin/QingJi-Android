package com.qingji.app.domain

import com.qingji.app.domain.BodyMetricsCalculator.BodyFatDisplay
import com.qingji.app.domain.BodyMetricsCalculator.BodyFatSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 身体数据卡片工单 2026-11-21：Deurenberg 体脂率估算 + 实测优先回退单测。
 * 公式：1.2*BMI + 0.23*age - 10.8*S - 5.4（男 S=1 / 女 S=0）。
 */
class BodyFatEstimationTest {

    private fun est(bmi: Double, age: Int, gender: String?) =
        BodyMetricsCalculator.estimateBodyFatDeurenberg(bmi, age, gender)

    // ---------- 公式正确性 ----------

    @Test
    fun `男性按S等于1计算`() {
        // 1.2*24 + 0.23*30 - 10.8 - 5.4 = 28.8+6.9-16.2 = 19.5
        assertEquals(19.5, est(24.0, 30, "male")!!, 0.0001)
    }

    @Test
    fun `女性按S等于0计算`() {
        // 1.2*24 + 0.23*30 - 0 - 5.4 = 28.8+6.9-5.4 = 30.3
        assertEquals(30.3, est(24.0, 30, "female")!!, 0.0001)
    }

    @Test
    fun `年龄变化后估算值更新`() {
        val a = est(24.0, 25, "male")!!
        val b = est(24.0, 35, "male")!!
        assertTrue(b > a)
        assertEquals(0.23 * 10, b - a, 0.0001) // 每长一岁 +0.23
    }

    @Test
    fun `BMI变化后估算值更新且用未舍入值`() {
        // 未舍入 BMI 与舍入后 BMI(24.0) 结果不同，证明用的是未舍入值
        val precise = est(24.04, 30, "male")!!
        val rounded = est(24.0, 30, "male")!!
        assertTrue(precise > rounded)
        assertEquals(1.2 * 0.04, precise - rounded, 0.0001)
    }

    // ---------- 缺失 / 非法 / 边界 ----------

    @Test
    fun `缺少性别不猜测不计算`() {
        assertNull(est(24.0, 30, null))
        assertNull(est(24.0, 30, ""))
        assertNull(est(24.0, 30, "unknown"))
    }

    @Test
    fun `未成年不适用成人公式`() {
        assertNull(est(24.0, 17, "male"))
        assertNull(est(24.0, 10, "female"))
    }

    @Test
    fun `非法BMI与非有限值防护`() {
        assertNull(est(0.0, 30, "male"))
        assertNull(est(-1.0, 30, "male"))
        assertNull(est(Double.NaN, 30, "male"))
        assertNull(est(Double.POSITIVE_INFINITY, 30, "male"))
        assertNull(est(24.0, 0, "male"))
    }

    @Test
    fun `超出合理范围的输出标记不可用`() {
        // 极端 BMI 算出超出 3..70 → null，不展示误导数字
        assertNull(est(60.0, 80, "female"))
        assertNull(est(1.0, 18, "male"))
    }

    // ---------- 数据来源优先级 ----------

    @Test
    fun `实测优先于估算且标注实测`() {
        val measured = BodyMetricsCalculator.BodyFatRecord(22.5, LocalDate.of(2026, 11, 20))
        val d = BodyMetricsCalculator.bodyFatDisplay(measured, 24.0, 30, "male")
        assertEquals(BodyFatSource.MEASURED, d.source)
        assertEquals(22.5, d.value!!, 0.0001)
        assertEquals(measured.date, d.measuredDate)
    }

    @Test
    fun `无实测且资料齐全时展示估算并标注估算`() {
        val d = BodyMetricsCalculator.bodyFatDisplay(null, 24.0, 30, "male")
        assertEquals(BodyFatSource.ESTIMATED, d.source)
        assertEquals(19.5, d.value!!, 0.0001)
        assertNull(d.measuredDate) // 估算无记录日期，不会伪装成实测
    }

    @Test
    fun `缺少年龄显示无数据不猜测`() {
        val d = BodyMetricsCalculator.bodyFatDisplay(null, 24.0, null, "male")
        assertEquals(BodyFatSource.NONE, d.source)
        assertNull(d.value)
    }

    @Test
    fun `缺少性别显示无数据`() {
        val d = BodyMetricsCalculator.bodyFatDisplay(null, 24.0, 30, null)
        assertEquals(BodyFatSource.NONE, d.source)
        assertNull(d.value)
    }

    @Test
    fun `未成年显示暂不支持估算`() {
        val d = BodyMetricsCalculator.bodyFatDisplay(null, 24.0, 17, "female")
        assertEquals(BodyFatSource.UNSUPPORTED, d.source)
        assertNull(d.value)
    }

    @Test
    fun `估算值不写入任何实测数据_纯函数无副作用`() {
        // 纯函数不接收 WeightLog 也不返回记录，无法写入；此测试锁定 API 不含写入路径。
        val methods = BodyMetricsCalculator::class.java.declaredMethods.map { it.name }
        assertTrue(methods.none { it.contains("save") || it.contains("insert") || it.contains("write") })
        val d = BodyMetricsCalculator.bodyFatDisplay(null, 24.0, 30, "female")
        assertEquals(BodyFatSource.ESTIMATED, d.source)
        assertNull(d.measuredDate)
    }
}
