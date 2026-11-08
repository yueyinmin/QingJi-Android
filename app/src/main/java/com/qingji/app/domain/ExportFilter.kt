package com.qingji.app.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * 饮食 CSV 导出的筛选规则（v3.4.0）。
 *
 * 为什么单独放 domain：这是纯逻辑，不碰 Room、不碰文件，JUnit 能直接测。
 * 约定：
 * - month == null 表示「全部时间」，不按月份过滤；
 * - mealTypes 为空表示「全部餐次」，非空时只留这几种；
 * - 两个条件同时存在时是「且」的关系。
 */
object ExportFilter {

    fun matches(
        date: LocalDate,
        mealType: String,
        month: YearMonth?,
        mealTypes: Set<String>
    ): Boolean {
        if (month != null && YearMonth.from(date) != month) return false
        if (mealTypes.isNotEmpty() && mealType !in mealTypes) return false
        return true
    }

    /** 界面上给用户看的一句话，说明这次会导出哪些记录。 */
    fun describe(month: YearMonth?, mealTypes: Set<String>): String {
        val time = month?.let { "${it.year} 年 ${it.monthValue} 月" } ?: "全部时间"
        val meals = if (mealTypes.isEmpty()) {
            "全部餐次"
        } else {
            MealRules.ALL
                .filter { it in mealTypes }
                .joinToString("、") { MealRules.label(it) }
                .ifBlank { "全部餐次" }
        }
        return "$time · $meals"
    }

    /**
     * 导出文件名中段。带筛选时把月份写进名字，避免用户导了好几份分不清。
     * 例如 food-logs-2026-11；不筛选时维持老名字 food-logs，兼容以前的习惯。
     */
    fun filePrefix(month: YearMonth?): String =
        month?.let { "food-logs-%04d-%02d".format(it.year, it.monthValue) } ?: "food-logs"
}
