package com.qingji.app.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ============================================================
 * 体重曲线的数学部分（M4）
 * ------------------------------------------------------------
 * 刻意从 Compose 的 Canvas 里抽出来，做成不依赖安卓的纯 Kotlin：
 * 1. 能在电脑上跑单元测试，不用连手机/开模拟器；
 * 2. 「只有 1 条体重记录」这类边界能在测试里钉死，不会回归；
 * 3. 界面只负责把算好的坐标画出来，不夹带计算逻辑。
 * ============================================================
 */
object ChartMath {

    /** 纵轴的上下界。画图前先算好，保证线不会贴着边框、也不会除零。 */
    data class Bounds(val min: Float, val max: Float) {
        val span: Float get() = (max - min).coerceAtLeast(1f)
    }

    /** 画面上的一个点，index 对应第几条体重记录，点按时用它取日期和数值。 */
    data class Point(val x: Float, val y: Float, val index: Int)

    /**
     * 算纵轴范围。
     * @param values 实际体重
     * @param target 目标体重（画虚线时也要落进可见范围，传 null 表示不画）
     * 只有 1 个值、或所有值都一样时，上下各留 1kg，避免 span=0 崩溃。
     */
    fun paddedBounds(values: List<Float>, target: Float? = null): Bounds {
        val all = if (target != null && target > 0) values + target else values
        if (all.isEmpty()) return Bounds(0f, 1f)

        val rawMin = all.minOrNull() ?: 0f
        val rawMax = all.maxOrNull() ?: 1f
        val pad = max((rawMax - rawMin) * 0.2f, 1f)
        return Bounds(rawMin - pad, rawMax + pad)
    }

    /** y 轴换算：值越大越靠上（画布 y 轴朝下）。 */
    fun yOf(value: Float, bounds: Bounds, height: Float): Float =
        height - (value - bounds.min) / bounds.span * height

    /**
     * 生成折线上的点。
     * 只有 1 条记录时放在画布水平中点，这样「不崩、不空图」；
     * 多条记录按时间均匀铺满整个宽度。
     */
    fun points(values: List<Float>, bounds: Bounds, width: Float, height: Float): List<Point> {
        if (values.isEmpty()) return emptyList()
        return values.mapIndexed { index, value ->
            val x = if (values.size == 1) width / 2 else index.toFloat() / (values.size - 1) * width
            Point(x = x, y = yOf(value, bounds, height), index = index)
        }
    }

    /**
     * 目标轨迹（需求 7.3）：从起始体重到目标体重线性插值。
     * count 与实测点数量一致，方便和实线共用横轴。
     */
    fun targetTrajectory(startWeight: Float, targetWeight: Float, count: Int): List<Float> {
        if (count <= 0) return emptyList()
        if (count == 1) return listOf(targetWeight)
        return List(count) { i -> startWeight + (targetWeight - startWeight) * i / (count - 1) }
    }

    /** 手指点按时，找离落点最近的那条记录。空列表返回 -1。 */
    fun nearestIndex(tapX: Float, count: Int, width: Float): Int {
        if (count <= 0 || width <= 0f) return -1
        if (count == 1) return 0
        val ratio = (tapX / width).coerceIn(0f, 1f)
        val raw = ratio * (count - 1)
        val floor = raw.toInt()
        val ceil = min(floor + 1, count - 1)
        return if (abs(raw - floor) <= abs(ceil - raw)) floor else ceil
    }

    /** 底部「最新 xx kg」卡片里的变化量文案。数据不足时给「--」。 */
    fun changeText(first: Float?, last: Float?): String {
        if (first == null || last == null) return "--"
        val delta = last - first
        return when {
            abs(delta) < 0.05f -> "持平"
            delta < 0 -> String.format("%.1f", -delta) + " kg ↓"
            else -> "+" + String.format("%.1f", delta) + " kg ↑"
        }
    }
}
