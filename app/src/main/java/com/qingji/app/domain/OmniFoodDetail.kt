package com.qingji.app.domain

import com.qingji.app.data.local.entity.FoodComposition
import com.qingji.app.data.local.entity.NutritionSample
import com.qingji.app.data.local.entity.OmniFoodEntity
import com.qingji.app.data.local.entity.OmniFoodNutrition

/**
 * ============================================================
 * OmniFood8K 一条食物的「细账」——组成明细 + 样本来源 + 异常提示
 * ------------------------------------------------------------
 * v1.3.2 界面接入：主表（food_catalog）只能搜到这道菜、给出每 100g 的四个大数，
 * 细账在这几个只读新表里：这道菜是由哪些配料组成、原始样本多重、
 * 机器校验时有没有标记异常。
 *
 * 数据链路（全部在 food_catalog.db 只读库里）：
 *   food_entity（按中文名定位）
 *     → food_nutrition.source_sample_id
 *       → nutrition_sample（样本本体）
 *         → food_composition（组成明细，按 parent_sample_id）
 *         → quality_issue（异常标记，按 sample_id）
 *
 * 查不到细账（比如旧库、或非 OmniFood8K 来源）时返回 null，界面就当没有这一块。
 * ============================================================
 */
data class OmniFoodDetail(
    /** 命名食物本体（omni:N），带中英文名与数据集版本 */
    val entity: OmniFoodEntity,
    /** 营养细账：参考克重、原始 kJ、换算 kcal、三大营养素 */
    val nutrition: OmniFoodNutrition?,
    /** 营养值来自哪个原始样本（一份实测记录） */
    val sample: NutritionSample?,
    /** 这份样本由哪些配料组成（重量、类型） */
    val composition: List<FoodComposition>,
    /** 机器校验标记的异常（如 component_weight_sum_diff_gt_10g）；空 = 没有异常标记 */
    val issueFlags: List<String>
) {
    /** 组成明细里配料重量合计（克）。样本总重和它对不上时，界面会提示仅供参考。 */
    val compositionWeightSum: Double
        get() = composition.sumOf { it.ingredientWeightG }
}
