package com.qingji.app.data.local

import com.qingji.app.data.local.entity.Food
import com.qingji.app.domain.FoodCategory

/**
 * ============================================================
 * 食物库批量导入解析器（v3.4.0）
 * ------------------------------------------------------------
 * 把 FoodLibraryData.RAW 的竖线分隔文本转成 Room 的 Food 列表。
 * 刻意做成纯 Kotlin、不依赖 Android Context，单测可以直接跑。
 *
 * 三条硬规则（来自后续需求文档 §4.2 的 F1/F2/F6）：
 *  · 一级分类直接采用清单值，**绝不调用 guess()**；
 *  · source 一律写 preset，CSV 的「数据来源」列不进 App；
 *  · brand 一律留空，快餐靠 category == 快餐 分流，不靠品牌。
 * ============================================================
 */
object FoodSeedImporter {

    /** 每行 8 列：名称|拼音|分类|热量|蛋白|碳水|脂肪|默认份量 */
    private const val COLUMNS = 8

    fun parse(raw: String = FoodLibraryData.RAW): List<Food> =
        raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull(::parseLine)

    private fun parseLine(line: String): Food? {
        val p = line.split('|')
        if (p.size != COLUMNS) return null
        val kcal = p[3].toDoubleOrNull() ?: return null
        val protein = p[4].toDoubleOrNull() ?: return null
        val carb = p[5].toDoubleOrNull() ?: return null
        val fat = p[6].toDoubleOrNull() ?: return null
        val grams = p[7].toDoubleOrNull() ?: return null
        val name = p[0].trim()
        if (name.isEmpty()) return null
        return Food(
            name = name,
            pinyin = p[1].trim(),
            category = p[2].trim().ifBlank { FoodCategory.OTHER },
            kcalPer100g = kcal,
            proteinPer100g = protein,
            carbPer100g = carb,
            fatPer100g = fat,
            defaultGrams = grams,
            source = "preset",
            brand = ""
        )
    }
}
