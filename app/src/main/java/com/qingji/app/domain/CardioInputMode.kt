package com.qingji.app.domain

/**
 * 训练中心有氧项目的录入方式（2026-11-24）。
 *
 * 步行/跑步/骑行/徒步/爬楼这类「位移型」有氧，除时长外还能按步数、公里记；
 * 划船机/椭圆机/风阻单车/跳绳等固定形式有氧只按时长记。
 *
 * 判定按 UUID 白名单（与 B1.1 映射表一致，禁止按名称猜测）。
 */
object CardioInputMode {
    const val MODE_MINUTES = "minutes"
    const val MODE_STEPS = "steps"
    const val MODE_KM = "km"

    /** 可用全部三种方式的动作 uuid。 */
    private val WALKABLE_UUIDS = setOf(
        "wg:walking",              // 步行
        "wg:running",              // 跑步
        "wg:cycling",              // 骑行
        "wg:hiking",               // 徒步
        "wg:stair-climber",        // 爬楼机
        "wg:treadmill-incline-walk" // 跑步机上坡走
    )

    fun modes(): List<Pair<String, String>> = listOf(
        MODE_MINUTES to "时长",
        MODE_STEPS to "步数",
        MODE_KM to "公里"
    )

    /** 该动作是否支持步数/公里模式。 */
    fun supportsDistanceModes(uuid: String?): Boolean = uuid in WALKABLE_UUIDS

    /**
     * 按记录运动页的换算口径，把步数/公里折算成分钟。
     * 步数 → 100 步/分钟；公里 → 10 分钟/公里（约 6km/h）。
     */
    fun toMinutes(mode: String, amount: Double): Double = when (mode) {
        MODE_STEPS -> amount / ExerciseCalculator.STEPS_PER_MINUTE
        MODE_KM -> amount * ExerciseCalculator.MINUTES_PER_KM
        else -> amount
    }

    /** 展示用单位。 */
    fun unit(mode: String): String = when (mode) {
        MODE_STEPS -> "步"
        MODE_KM -> "公里"
        else -> "分钟"
    }
}
