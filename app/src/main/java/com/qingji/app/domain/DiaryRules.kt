package com.qingji.app.domain

import java.time.LocalDate

/**
 * ============================================================
 * 日记的规则（M5）
 * ------------------------------------------------------------
 * 纯 Kotlin，方便单元测试：30 天格子、评分档位、玻璃罐进度、三种排序。
 * 界面只负责把这里算好的档位翻译成颜色。
 * ============================================================
 */
object DiaryRules {

    /** 列表的三种排序（需求 M5）。 */
    enum class SortMode { DATE, UPDATED, RATING }

    /** 格子的四个档：未写 / 低 / 中 / 高。 */
    const val LEVEL_NONE = 0
    const val LEVEL_LOW = 1
    const val LEVEL_MID = 2
    const val LEVEL_HIGH = 3

    /** 玻璃罐攒满需要多少篇。 */
    const val JAR_GOAL = 30

    /**
     * 近 30 天的日期列表，从「29 天前」到「今天」，升序。
     * 界面按 6 列 × 5 行铺，顺序和这里一致。
     */
    fun cells(today: LocalDate, days: Int = 30): List<LocalDate> {
        val safeDays = days.coerceIn(1, 366)
        return List(safeDays) { i -> today.minusDays((safeDays - 1 - i).toLong()) }
    }

    /** 评分 → 档位。0 分或没写 = 未写；1-2 低；3 中；4-5 高。 */
    fun level(rating: Int): Int = when {
        rating <= 0 -> LEVEL_NONE
        rating <= 2 -> LEVEL_LOW
        rating == 3 -> LEVEL_MID
        else -> LEVEL_HIGH
    }

    /** 玻璃罐填充比例，0~1，超过目标按满的处理。 */
    fun jarProgress(count: Int, goal: Int = JAR_GOAL): Float {
        if (goal <= 0) return 0f
        return (count.toFloat() / goal).coerceIn(0f, 1f)
    }

    /**
     * 三种排序统一在这里做，界面不自己写 comparator。
     * 同一天/同分时用 updatedAt 大的排前面，保证顺序稳定不会乱跳。
     */
    fun <T> sort(
        items: List<T>,
        mode: SortMode,
        dateOf: (T) -> LocalDate,
        updatedOf: (T) -> Long,
        ratingOf: (T) -> Int
    ): List<T> = when (mode) {
        SortMode.DATE -> items.sortedWith(
            compareByDescending<T> { dateOf(it) }.thenByDescending { updatedOf(it) }
        )
        SortMode.UPDATED -> items.sortedByDescending { updatedOf(it) }
        SortMode.RATING -> items.sortedWith(
            compareByDescending<T> { ratingOf(it) }.thenByDescending { updatedOf(it) }
        )
    }

    /** 编辑页的摘要：空内容时用日期兜底，列表里不至于一片空白。 */
    fun previewText(content: String, date: LocalDate): String =
        content.trim().ifBlank { "${date.monthValue}月${date.dayOfMonth}日 · 没写内容" }
            .replace('\n', ' ')
            .take(60)
}
