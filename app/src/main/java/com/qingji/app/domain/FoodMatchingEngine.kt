package com.qingji.app.domain

import com.qingji.app.data.local.entity.CatalogFood

data class FoodMatchCandidate(
    val food: CatalogFood,
    val matchedText: String,
    val score: Int,
    val via: String,
    val isEstimated: Boolean
)

/**
 * F 阶段本地匹配引擎。
 *
 * 顺序：完全命中（名称/现有别名/补丁别名/拼音）→ LIKE 包含 → Top20 规则排序 → Top5。
 * 只做召回与排序，营养值一律取本地库；无匹配就返回空，不强行选相似食物。
 */
object FoodMatchingEngine {

    fun match(
        query: String,
        foods: List<CatalogFood>,
        patch: FoodAliasPatch?,
        limit: Int = 5
    ): List<FoodMatchCandidate> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val normalized = NutritionMatch.normalizeName(q)
        val byId = foods.associateBy { it.foodId }
        val pool = LinkedHashMap<String, FoodMatchCandidate>()

        // 1. 库内名称/别名/拼音完全命中
        foods.forEach { food ->
            val names = listOf(food.name) + food.aliases.split("|").map { it.trim() }
            val exactName = names.firstOrNull { NutritionMatch.normalizeName(it) == normalized }
            if (exactName != null) {
                pool.putIfAbsent(
                    "food:${food.foodId}",
                    FoodMatchCandidate(food, exactName, 100, "NAME", false)
                )
                return@forEach
            }
            if (food.pinyin.equals(q, ignoreCase = true) ||
                food.pinyinFull.equals(q, ignoreCase = true)
            ) {
                pool.putIfAbsent(
                    "food:${food.foodId}",
                    FoodMatchCandidate(food, q, 95, "PINYIN", false)
                )
            }
        }

        // 2. 版本化别名补丁：完全命中。多候选全部保留，不自动收敛。
        patch?.aliases?.forEach { entry ->
            val entryNorm = NutritionMatch.normalizeName(entry.alias)
            val normalizedNorm = NutritionMatch.normalizeName(entry.normalizedAlias)
            if (entryNorm != normalized && normalizedNorm != normalized) return@forEach
            entry.candidateFoodIds.mapNotNull { byId[it] }.forEach { food ->
                val key = "patch:${entry.alias}:${food.foodId}"
                pool.putIfAbsent(
                    key,
                    FoodMatchCandidate(
                        food = food,
                        matchedText = entry.alias,
                        score = if (entry.preferredFoodId == food.foodId) 99 else 98,
                        via = "ALIAS_PATCH",
                        isEstimated = false
                    )
                )
            }
        }

        // 3. LIKE 包含召回（仅在前两步不足 Top20 时补位）
        if (pool.size < TOP_CANDIDATES) {
            foods.forEach { food ->
                val hitText = (listOf(food.name) + food.aliases.split("|").map { it.trim() })
                    .firstOrNull { it.isNotBlank() && it.contains(q, ignoreCase = true) }
                if (hitText != null) {
                    pool.putIfAbsent(
                        "like:${food.foodId}",
                        FoodMatchCandidate(
                            food = food,
                            matchedText = hitText,
                            score = NutritionMatch.score(q, hitText),
                            via = "LIKE",
                            isEstimated = true
                        )
                    )
                }
            }
        }

        // 4. 补丁别名也参与 LIKE 补位
        if (pool.size < TOP_CANDIDATES && patch != null) {
            patch.aliases.forEach { entry ->
                // 完全命中已经在第 2 步处理，避免同一条别名重复进池。
                if (entry.alias == q ||
                    NutritionMatch.normalizeName(entry.alias) == normalized ||
                    NutritionMatch.normalizeName(entry.normalizedAlias) == normalized
                ) return@forEach
                if (!entry.alias.contains(q, ignoreCase = true)) return@forEach
                entry.candidateFoodIds.mapNotNull { byId[it] }.forEach { food ->
                    pool.putIfAbsent(
                        "likepatch:${entry.alias}:${food.foodId}",
                        FoodMatchCandidate(
                            food = food,
                            matchedText = entry.alias,
                            score = NutritionMatch.score(q, entry.alias),
                            via = "ALIAS_PATCH_LIKE",
                            isEstimated = true
                        )
                    )
                }
            }
        }

        return pool.values
            .sortedWith(compareByDescending<FoodMatchCandidate> { it.score }
                .thenBy { it.matchedText }
                .thenBy { it.food.foodId })
            .take(TOP_CANDIDATES)
            .take(limit.coerceAtLeast(0))
    }

    /** 给旧识别校正链路用：把补丁别名展开成 (别名, 标准食物) 候选。 */
    fun aliasCandidates(foods: List<CatalogFood>, patch: FoodAliasPatch?): List<Pair<String, CatalogFood>> {
        if (patch == null) return emptyList()
        val byId = foods.associateBy { it.foodId }
        return patch.aliases.flatMap { entry ->
            entry.candidateFoodIds.mapNotNull { id ->
                byId[id]?.let { entry.alias to it }
            }
        }
    }

    private const val TOP_CANDIDATES = 20
}
