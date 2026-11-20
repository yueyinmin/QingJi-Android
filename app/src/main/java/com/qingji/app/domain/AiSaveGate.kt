package com.qingji.app.domain

/**
 * ============================================================
 * AI 识别结果的「保存闸门」（纯 Kotlin，不依赖 Android，可单测）
 * ------------------------------------------------------------
 * 背景：v1.0.6 已经有 auditNutrition()，但它只在识别结果返回时跑一次，
 * 判定不通过也只是「压置信度 + 往 advice 里写一句」，保存按钮
 * 仍然无条件可点 —— 明显自相矛盾的数据照样能进 food_log。
 * 而且用户手动改完数字后不会重新校验，红字还是识别那一刻的旧快照。
 *
 * 这个文件把校验从「一次性提示」变成「每次编辑都要过的闸门」：
 *   - 输入是界面当前显示的数值（不是识别时的快照）；
 *   - 每条都过一次 AiResultRules.auditNutrition()，复用已有的 4-9-4
 *     与营养素总重规则，不另起一套标准；
 *   - 有一条不合格就 canSave = false，界面禁用保存并写明原因。
 *
 * 刻意**不自动改数、不自动截断、不打折**：数据不确定就如实告诉用户，
 * 让他自己改到自洽或删掉这条。低置信度但数据自洽的条目只提示、不拦截。
 * ============================================================
 */

/** 界面当前这一条食物的数值快照。null = 输入框空着或不是数字。 */
data class AiItemSnapshot(
    val name: String,
    val grams: Double?,
    val kcal: Double?,
    val proteinG: Double?,
    val carbG: Double?,
    val fatG: Double?
)

object AiSaveGate {

    /** 某一条不合格。[index] 对应列表下标，[reason] 直接给用户看。 */
    data class Issue(val index: Int, val reason: String)

    data class Verdict(val issues: List<Issue>) {
        val canSave: Boolean get() = issues.isEmpty()

        /** 一条人话总结，放在保存按钮下方，不重复堆叠多行警告。 */
        val summary: String
            get() = when {
                issues.isEmpty() -> ""
                issues.size == 1 -> "第 ${issues[0].index + 1} 条需要核对：${issues[0].reason}"
                else -> "有 ${issues.size} 条数据需要核对，请先看卡片里的红字"
            }
    }

    fun check(items: List<AiItemSnapshot>): Verdict {
        val issues = mutableListOf<Issue>()
        items.forEachIndexed { index, item ->
            val g = item.grams
            val k = item.kcal
            val p = item.proteinG
            val c = item.carbG
            val f = item.fatG
            val problem = when {
                item.name.isBlank() -> "没填食物名称"
                g == null || k == null || p == null || c == null || f == null ->
                    "有数字没填或不是数字"
                g <= 0.0 -> "克数要大于 0"
                k < 0.0 -> "热量不能是负数"
                p < 0.0 || c < 0.0 || f < 0.0 -> "三大营养素不能是负数"
                else -> {
                    val audit = AiResultRules.auditNutrition(g, k, p, c, f)
                    if (audit.ok) null else audit.note
                }
            }
            if (problem != null) issues += Issue(index, problem)
        }
        return Verdict(issues)
    }
}
