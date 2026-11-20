package com.qingji.app.domain.progress

import java.time.LocalDate

/**
 * ============================================================
 * 进展页「日记卡」共用的分组纯函数（工单 P1a/P1b）
 * ------------------------------------------------------------
 * 睡眠日记 / 运动日记 / 饮食日记三种卡片都是「按日期分组的滚动流」，
 * 只是每一行的文案不同。这里只负责把一堆带日期的记录整理成
 * 「日期 → 该日条目列表」，UI 层拿去画，三种卡片共用一套渲染组件。
 *
 * 纯 Kotlin、不依赖 Android / Compose，单测直接跑 JVM。
 * ============================================================
 */
object DiaryGrouping {

    /** 一个分组头 + 它下面的行。 */
    data class DayGroup<T>(
        val date: LocalDate,
        val items: List<T>
    )

    /**
     * 按日期分组，日期从新到旧。
     * 同一天的多条保持传入时的相对顺序（稳定排序）。
     * [limit] 限制返回的天数；null = 不限制。空列表返回空分组。
     */
    fun <T> groupByDate(
        records: List<T>,
        dateOf: (T) -> LocalDate,
        limit: Int? = null
    ): List<DayGroup<T>> {
        if (records.isEmpty()) return emptyList()
        val grouped = records.groupBy(dateOf)
        val days = grouped.keys.sortedDescending()
        val picked = if (limit == null) days else days.take(limit.coerceAtLeast(0))
        return picked.map { DayGroup(it, grouped.getValue(it)) }
    }
}
