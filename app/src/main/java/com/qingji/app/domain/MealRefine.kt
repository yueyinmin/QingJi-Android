package com.qingji.app.domain

/**
 * ============================================================
 * 重新识别时的「口味与做法」参数
 * ------------------------------------------------------------
 * 为什么需要：默认按「家常炒」估，但同样是番茄炒蛋，
 * 少油少糖和多油多糖能差出 100 多千卡。
 * 让用户把这几点勾一下，模型估算会准很多。
 *
 * 这里只负责把选项拼成给模型看的中文规则，纯函数、可单测。
 * ============================================================
 */
data class RefineOptions(
    /** 用户手写的补充说明，比如「米饭只吃了半碗，剩下倒了」 */
    val note: String = "",
    /** 含糖量：无糖 / 少糖 / 正常 / 多糖 */
    val sugar: String = SUGAR_NORMAL,
    /** 含油量：少油 / 正常 / 多油 / 重油 */
    val oil: String = OIL_NORMAL,
    /** 含盐量：清淡 / 正常 / 偏咸 */
    val salt: String = SALT_NORMAL,
    /** 烹饪方式 */
    val cooking: String = COOKING_STIR_FRY,
    /** 是否加了酱料（酱油、蚝油、沙拉酱、辣椒酱等） */
    val sauce: Boolean = false,
    /** true = 生食（凉拌、刺身、沙拉），false = 熟食 */
    val raw: Boolean = false
) {

    companion object {
        const val SUGAR_NONE = "无糖"
        const val SUGAR_LESS = "少糖"
        const val SUGAR_NORMAL = "正常"
        const val SUGAR_MORE = "多糖"

        const val OIL_LESS = "少油"
        const val OIL_NORMAL = "正常"
        const val OIL_MORE = "多油"
        const val OIL_HEAVY = "重油"

        const val SALT_LIGHT = "清淡"
        const val SALT_NORMAL = "正常"
        const val SALT_SALTY = "偏咸"

        const val COOKING_STIR_FRY = "家常炒"
        const val COOKING_STEAM = "清蒸"
        const val COOKING_BOIL = "水煮"
        const val COOKING_FRY = "油炸"
        const val COOKING_ROAST = "烤"
        const val COOKING_STEW = "炖煮"
        const val COOKING_COLD = "凉拌"

        val SUGAR_OPTIONS = listOf(SUGAR_NONE, SUGAR_LESS, SUGAR_NORMAL, SUGAR_MORE)
        val OIL_OPTIONS = listOf(OIL_LESS, OIL_NORMAL, OIL_MORE, OIL_HEAVY)
        val SALT_OPTIONS = listOf(SALT_LIGHT, SALT_NORMAL, SALT_SALTY)
        val COOKING_OPTIONS = listOf(
            COOKING_STIR_FRY, COOKING_STEAM, COOKING_BOIL,
            COOKING_FRY, COOKING_ROAST, COOKING_STEW, COOKING_COLD
        )
    }

    /**
     * 拼成给模型的规则文本。
     * 只输出对估算有影响的项，默认值也写出来，避免模型自己发挥。
     */
    fun toPrompt(): String = buildString {
        appendLine("【这一餐的口味与做法】请严格按下面的条件估算，不要再用默认假设：")
        appendLine("- 烹饪方式：$cooking")
        appendLine("- 含油量：$oil。${oilHint()}")
        appendLine("- 含糖量：$sugar。${sugarHint()}")
        appendLine("- 含盐量：$salt。${saltHint()}")
        appendLine(if (sauce) "- 加了酱料：按 15g 酱料计入（约 25 千卡，钠较高）" else "- 没有加酱料")
        appendLine(if (raw) "- 食物状态：生食（按生重和生食营养估算，不要按熟食折算）" else "- 食物状态：熟食")
        if (note.isNotBlank()) {
            appendLine("- 用户补充说明：${note.trim()}")
        }
        append("如果某个条件与常识冲突（例如「清蒸」和「重油」同时出现），以用户写的为准。")
    }

    /** 油量对热量的影响提示，帮模型落到具体数字上。 */
    private fun oilHint(): String = when (oil) {
        OIL_LESS -> "每份食用油按 3g 计"
        OIL_MORE -> "每份食用油按 15g 计"
        OIL_HEAVY -> "每份食用油按 25g 计，按饭店做法算"
        else -> "每份食用油按 8g 计"
    }

    private fun sugarHint(): String = when (sugar) {
        SUGAR_NONE -> "不额外加糖"
        SUGAR_LESS -> "按常规用量的三分之一加糖"
        SUGAR_MORE -> "按常规用量的两倍加糖"
        else -> "按常规用量加糖"
    }

    private fun saltHint(): String = when (salt) {
        SALT_LIGHT -> "盐和含钠调料减半"
        SALT_SALTY -> "盐和含钠调料加倍"
        else -> "正常调味"
    }
}
