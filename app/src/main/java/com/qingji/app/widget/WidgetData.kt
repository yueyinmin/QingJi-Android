package com.qingji.app.widget

import java.time.LocalDate

/**
 * 分项读取结果三态（验收补充工单 P0）：
 * - SUCCESS：查询成功且有值；
 * - EMPTY：查询成功但确实没有记录（0 是真实值，可展示）；
 * - FAILED：读库失败（UI 必须显示 --，绝不能用默认值冒充真实数据）。
 */
enum class ItemState { SUCCESS, EMPTY, FAILED }

/**
 * 桌面小组件一次读取的展示数据。
 *
 * 可用性语义（P0）：每个分项都有独立 [ItemState]，数值与可用性分离；
 * 剩余热量只有在目标/已吃/运动消耗三项都可用时才展示。
 *
 * W-01/W-03/W-07：快照带结构版本、新鲜度时间与训练/睡眠分项可用状态——
 * 失败不能伪装成真实的 0 分钟，旧快照不能冒充今天的新数据。
 */
data class WidgetData(
    val goalKcal: Int = 2000,
    val eatenKcal: Int = 0,
    /** 今日剩余（含运动补偿），与首页 remainingWithBonus 同口径。仅当 canShowRemaining 为 true 时可展示。 */
    val remainingKcal: Int = 2000,
    /** 首页同款：目标模式 lose/gain/keep，标题右侧胶囊。 */
    val goalMode: String = "lose",
    /** 首页同款：当日运动消耗（千卡），用于橙色小字。 */
    val exerciseKcal: Int = 0,
    /** 首页同款：预期缺口（纯饮食剩余，不含补偿）。 */
    val remainingFoodOnly: Int = 0,
    val protein: Int = 0,
    val proteinGoal: Int = 0,
    val carb: Int = 0,
    val carbGoal: Int = 0,
    val fat: Int = 0,
    val fatGoal: Int = 0,
    val water: Int = 0,
    val waterGoal: Int = 3200,

    // ---- P0：分项可用状态（与数值分离，序列化保存） ----
    /** 用户资料（热量/营养目标、单位）。FAILED 时 goal/proteinGoal 等不可信。 */
    val profileState: ItemState = ItemState.FAILED,
    /** 当日饮食汇总（热量与三营养素）。EMPTY = 真的没记录，0 是真值。 */
    val dietState: ItemState = ItemState.FAILED,
    /** 当日饮水。EMPTY = 真的没喝，0 ml 是真值。 */
    val waterState: ItemState = ItemState.FAILED,
    /** 当日运动消耗（补偿用）。EMPTY = 真的没运动，0 kcal 是真值。 */
    val exerciseState: ItemState = ItemState.FAILED,
    /** 当前体重。EMPTY = 还没有体重记录。 */
    val weightState: ItemState = ItemState.FAILED,

    /** 整体读库失败：展示的是快照或空态，不是刚查到的真实数据。 */
    val stale: Boolean = false,
    /** 是否真的拿到过一次成功快照（false = 首次失败，UI 应显示空态而非伪数值）。 */
    val hasSnapshot: Boolean = false,
    val latestWeight: Double? = null,
    val unit: String = "kg",

    // ---- P1：训练与睡眠各自独立可用状态 ----
    /** SUCCESS = 有真实值；EMPTY = 查询成功但无记录（0 分钟是真值）；FAILED = UI 必须 --。 */
    val workoutState: ItemState = ItemState.FAILED,
    val sleepState: ItemState = ItemState.FAILED,
    val workoutMinutes: Int = 0,
    val sleepMinutes: Int = 0,

    // ---- 身体数据仪表盘（对齐进展页 Card） ----
    /** BMI 值（未舍入）；FAILED/无资料时 0 且 bodyState=FAILED/EMPTY。 */
    val bmi: Double = 0.0,
    /** BMI 分类：偏瘦/正常/超重/肥胖。 */
    val bmiCategory: String = "",
    /** 体脂率（实测优先，无实测用估算；单位%）。 */
    val bodyFat: Double? = null,
    /** 体脂来源：measured / estimated / none。 */
    val bodyFatSource: String = "none",
    /** 目标体重（kg/lb 数值，与 unit 一致）。 */
    val targetWeight: Double = 0.0,
    /** 起始体重（档案 startWeight）。 */
    val startWeight: Double = 0.0,
    /** 减重进度 0..1。 */
    val lossProgress: Float = 0f,
    /** 距目标还差多少（正数=还需减）。 */
    val remainToTarget: Double = 0.0,
    /** 身体数据整体可用性：资料/体重任一失败则不可用。 */
    val bodyState: ItemState = ItemState.FAILED,

    /** 数据归属业务日期（AppClock 记录时区下的今天）。 */
    val dataDate: LocalDate? = null,
    /** 生成快照时的记录时区 ID。 */
    val zoneId: String = "",
    /** 快照生成时间（epoch millis），用于新鲜度判断与更新时间展示。 */
    val snapshotAt: Long = 0L
) {
    /** 兼容旧调用方的派生布尔：只有 SUCCESS/EMPTY 才可用。 */
    val workoutAvailable: Boolean get() = workoutState != ItemState.FAILED
    val sleepAvailable: Boolean get() = sleepState != ItemState.FAILED

    /** 剩余热量的三个输入（目标/已吃/运动）全部可用才允许展示，否则不得用默认值冒充。 */
    val canShowRemaining: Boolean get() =
        profileState != ItemState.FAILED &&
            dietState != ItemState.FAILED &&
            exerciseState != ItemState.FAILED

    companion object {
        /**
         * P0：新增分项状态，schema 1 旧快照无法表达「真 0 与失败」区别，
         * 升到 2 后旧快照在 fromJson 直接作废（安全作废，不静默迁移）。
         */
        const val SCHEMA_VERSION = 4  // 身体数据仪表盘字段
    }
}
