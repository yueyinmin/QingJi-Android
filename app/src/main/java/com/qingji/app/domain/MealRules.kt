package com.qingji.app.domain


/**
 * 餐次的统一规矩。
 * 数据库里存英文（breakfast/lunch/dinner/snack），界面上显示中文。
 * 放在这里是为了「手动搜索」和「AI 识别」猜出来的餐次完全一致。
 */
object MealRules {
    const val BREAKFAST = "breakfast"
    const val LUNCH = "lunch"
    const val DINNER = "dinner"
    const val SNACK = "snack"

    val ALL = listOf(BREAKFAST, LUNCH, DINNER, SNACK)

    fun label(type: String): String = when (type) {
        BREAKFAST -> "早餐"
        LUNCH -> "午餐"
        DINNER -> "晚餐"
        else -> "加餐"
    }

    fun isValid(type: String): Boolean = type in ALL

    /** 按当前钟点猜一个默认餐次，用户随时能改。 */
    fun guess(): String {
        // P1-05：按业务时区猜餐次（用户跨时区时餐次与记录日期一致）
        val hour = AppClock.now().hour
        return when {
            hour < 10 -> BREAKFAST
            hour < 15 -> LUNCH
            hour < 21 -> DINNER
            else -> SNACK
        }
    }
}
