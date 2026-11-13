package com.qingji.app.domain

/**
 * ============================================================
 * 自律管理模块：全局常量与可选项（SD-005 / SD-006 / SD-007）
 * ------------------------------------------------------------
 * 把「状态 token」「下拉选项」集中在这里，UI 和 Repository 都从这里取，
 * 以后要改文案只改这一处，避免散落写死。
 * 说明：选项直接存中文标签（无国际化需求），状态用短 token 更稳。
 * ============================================================
 */

object SelfDisciplineContracts {
    // ---------- 目标类型（管理范围） ----------
    const val GOAL_PORN = "porn"        // 减少色情内容
    const val GOAL_BEHAVIOR = "behavior" // 管理个人行为
    const val GOAL_CUSTOM = "custom"     // 自定义

    val GOAL_TYPE_LABELS = mapOf(
        GOAL_PORN to "减少色情内容",
        GOAL_BEHAVIOR to "管理个人行为",
        GOAL_CUSTOM to "自定义目标"
    )

    // ---------- 打卡状态 ----------
    const val STATUS_ON_TARGET = "on_target"  // 符合目标
    const val STATUS_OFF_TARGET = "off_target" // 目标偏离
    const val STATUS_SKIP = "skip"            // 暂不记录

    val STATUS_LABELS = mapOf(
        STATUS_ON_TARGET to "符合目标",
        STATUS_OFF_TARGET to "目标偏离",
        STATUS_SKIP to "暂不记录"
    )

    // ---------- 周期状态 / 结束原因 ----------
    const val PERIOD_ACTIVE = "active"
    const val PERIOD_ENDED = "ended"
    const val REASON_DEVIATION = "deviation" // 目标偏离
    const val REASON_COMPLETED = "completed" // 达成
    const val REASON_MANUAL = "manual"      // 手动结束

    // ---------- 打卡可选项 ----------
    /** 情绪：非必填，可空。 */
    val MOODS = listOf("平静", "开心", "焦虑", "疲惫", "低落", "其他")

    /** 主要诱因（多选）。 */
    val TRIGGERS = listOf("无聊", "压力", "孤独", "熬夜", "网络内容", "其他")

    /** 应对方式（多选）。 */
    val COPING_METHODS = listOf("运动", "散步", "休息", "呼吸练习", "转移注意力", "其他")

    /** 冲动程度范围。 */
    const val URGE_MIN = 1
    const val URGE_MAX = 5

    // ---------- 设置 key ----------
    const val SETTING_ENABLED = "module_enabled"   // 模块是否启用（"0"/"1"）
    const val SETTING_DEFAULT_GOAL = "default_goal_id" // 主要目标 id
    const val SETTING_PRIVACY_HIDDEN = "privacy_hidden" // 隐私：隐藏敏感明细（"0"/"1"）
    // 用户自己新增的选项（逗号分隔），打卡 / 冲动 / 偏离三个页面共用同一份
    const val SETTING_CUSTOM_TRIGGERS = "custom_triggers"
    const val SETTING_CUSTOM_COPING = "custom_coping"
    const val SETTING_CUSTOM_MOODS = "custom_moods"

    /** 把逗号分隔的多选字符串拆成列表。 */
    fun splitCsv(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** 把多选列表拼成逗号分隔字符串。 */
    fun joinCsv(items: List<String>): String = items.joinToString(",")
}
