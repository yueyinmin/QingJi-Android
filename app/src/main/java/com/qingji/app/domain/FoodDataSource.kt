package com.qingji.app.domain

/**
 * ============================================================
 * 数据来源四类标识（v1.5 食物创建体系重构 · 阶段四）
 * ------------------------------------------------------------
 * 工单要求区分：
 *   ① 官方数据    OFFICIAL
 *   ② 公开数据    PUBLIC
 *   ③ 用户手动录入 MANUAL
 *   ④ 用户自建品牌 USER_BRAND
 *
 * 红线：没有可靠营养数据时显示「暂无营养数据」，
 *       禁止虚构数值，禁止把未知标成官方。
 * 这里只定义值 + 中文标签，data 层只存字符串，避免反向依赖。
 * ============================================================
 */
object FoodDataSource {
    const val OFFICIAL = "OFFICIAL"
    const val PUBLIC = "PUBLIC"
    const val MANUAL = "MANUAL"
    const val USER_BRAND = "USER_BRAND"

    val ALL = listOf(OFFICIAL, PUBLIC, MANUAL, USER_BRAND)

    /** 界面标签。未知值一律归到「用户手动录入」，不冒充官方。 */
    fun label(source: String?): String = when (source) {
        OFFICIAL -> "官方数据"
        PUBLIC -> "公开数据"
        USER_BRAND -> "自建品牌数据"
        else -> "用户手动录入"
    }

    fun normalize(source: String?): String =
        if (source in ALL) source!! else MANUAL

    /**
     * 营养数据是否「可信」——只有官方/公开/自建品牌算有来源，
     * 纯手动录入且热量为 0 时，界面应显示「暂无营养数据」。
     * 由调用方结合热量判断，这里只给来源维度。
     */
    fun hasReliableSource(source: String?): Boolean =
        source == OFFICIAL || source == PUBLIC || source == USER_BRAND
}
