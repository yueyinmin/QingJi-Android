package com.qingji.app.domain

/**
 * ============================================================
 * 常吃食物置顶（M3.5）
 * ------------------------------------------------------------
 * 需求：按最近 30 天出现频次排序，常吃的排前面。
 * 只在内存里对「搜索出来的一页（最多 50 条）」重排，不改 SQL，
 * 这样不用动数据库索引，也不会让搜索变慢。
 * ============================================================
 */
object FoodRanking {

    /**
     * @param recentCounts 食物名 → 最近 30 天出现次数
     * 排序规则：次数多的在前；次数相同保持原来的字母序（调用方传进来的顺序）。
     */
    fun <T> rank(items: List<T>, nameOf: (T) -> String, recentCounts: Map<String, Int>): List<T> =
        items.sortedByDescending { recentCounts[nameOf(it).trim()] ?: 0 }

    /** 从最近 30 天的饮食流水里统计次数，返回「食物名 → 次数」。 */
    fun countRecent(recentFoodNames: List<String>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        recentFoodNames.forEach { name ->
            val key = name.trim()
            if (key.isNotEmpty()) result[key] = (result[key] ?: 0) + 1
        }
        return result
    }
}
