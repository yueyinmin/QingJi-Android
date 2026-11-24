package com.qingji.app.domain

/**
 * 饮水快捷累加口径（QJ-WATER-SHEET-V2.1）。
 *
 * 快捷按钮 100/200/300ml 是「在当前待记录水量上累加」，不是覆盖；
 * 只算本地待记录值，不写库。边界统一收敛到这个函数，避免多个按钮各写一套判断。
 */
object WaterQuickAdd {
    const val MIN_ML = 1
    const val MAX_ML = 3000

    /** 在 base 上累加 delta，夹紧到 [MIN_ML, MAX_ML]；非法 base 从 0 起算。 */
    fun add(base: String, delta: Int): Int {
        val current = base.toIntOrNull()?.takeIf { it > 0 } ?: 0
        return (current + delta).coerceIn(MIN_ML, MAX_ML)
    }

    /** 加减按钮：步长 50ml，同样夹紧。 */
    fun step(base: Int, delta: Int): Int = (base + delta).coerceIn(MIN_ML, MAX_ML)

    /** 最终提交校验。 */
    fun isValid(value: String): Boolean {
        val n = value.toIntOrNull() ?: return false
        return n in MIN_ML..MAX_ML
    }
}
