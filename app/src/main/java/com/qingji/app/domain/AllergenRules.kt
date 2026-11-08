package com.qingji.app.domain

/**
 * ============================================================
 * 过敏原与忌口（v2.0）
 * ------------------------------------------------------------
 * 两件事：
 * 1. 判断一条食物名里有没有某种过敏原（食物库打警告、推荐时过滤）；
 * 2. 把用户的忌口/过敏原拼成给 AI 的规则（重新识别时避开）。
 *
 * 关键词判定是「保守」的：宁可多标一次，不能漏标过敏原。
 * 纯函数，方便单测。
 * ============================================================
 */
object AllergenRules {

    const val PEANUT = "花生"
    const val SEAFOOD = "海鲜"
    const val MILK = "牛奶"
    const val EGG = "鸡蛋"
    const val SOY = "大豆"
    const val GLUTEN = "麸质"
    const val NUTS = "坚果"

    /** 界面上展示的过敏原选项。 */
    val ALL = listOf(PEANUT, SEAFOOD, MILK, EGG, SOY, GLUTEN, NUTS)

    /** 每种过敏原对应的关键词。 */
    private val keywords: Map<String, List<String>> = mapOf(
        PEANUT to listOf("花生", "花生酱", "花生米"),
        SEAFOOD to listOf("鱼", "虾", "蟹", "贝", "蛤", "鱿鱼", "海参", "生蚝", "扇贝", "海鲜"),
        MILK to listOf("牛奶", "奶粉", "酸奶", "芝士", "奶酪", "奶油", "黄油", "拿铁"),
        EGG to listOf("蛋", "鸡蛋", "蛋挞", "蛋糕"),
        SOY to listOf("大豆", "黄豆", "豆腐", "豆干", "豆浆", "腐竹", "豆皮", "毛豆"),
        GLUTEN to listOf("面", "面粉", "馒头", "包子", "饺子", "面包", "吐司", "饼", "油条", "糕点"),
        NUTS to listOf("坚果", "核桃", "杏仁", "腰果", "瓜子", "开心果", "榛子")
    )

    /** 这条食物含哪些过敏原（可能多个）。 */
    fun hit(name: String, selected: Collection<String>): List<String> {
        if (name.isBlank() || selected.isEmpty()) return emptyList()
        return selected.filter { allergen ->
            val words = keywords[allergen].orEmpty()
            words.any { name.contains(it) }
        }
    }

    /** 是否命中任意一个（用来在列表里打警告）。 */
    fun isRisky(name: String, selected: Collection<String>): Boolean =
        hit(name, selected).isNotEmpty()

    /**
     * 拼成给模型的规则。忌口食物原样列出，过敏原按名字列出。
     * 没选任何东西时返回空串，调用方拼接时不用判断。
     */
    fun toPrompt(allergens: Collection<String>, avoidFoods: Collection<String>): String {
        if (allergens.isEmpty() && avoidFoods.isEmpty()) return ""
        return buildString {
            appendLine("【必须避开的食材】")
            if (allergens.isNotEmpty()) {
                appendLine("- 用户对这些过敏：${allergens.joinToString("、")}。任何含这些成分的条目都不要出现。")
            }
            if (avoidFoods.isNotEmpty()) {
                appendLine("- 用户不吃：${avoidFoods.joinToString("、")}。名称或配料里含这些的条目一律剔除。")
            }
            append("如果用户描述的食物确实含有上述成分，不要替他改成别的食物，而是照实给出，并在 advice 里提醒他这份含过敏原。")
        }
    }
}
