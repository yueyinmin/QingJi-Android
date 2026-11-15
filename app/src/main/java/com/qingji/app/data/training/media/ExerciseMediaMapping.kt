package com.qingji.app.data.training.media

/**
 * ============================================================
 * 动作库插画映射（V2 工单）
 * ------------------------------------------------------------
 * 素材来源：Workout Guide（github.com/bryllim/workout-guide）
 *   · 作者 Bryl Lim，上游素材 Everkinetic（github.com/everkinetic/data）
 *   · 授权 CC BY-SA 4.0，见根目录 THIRD_PARTY_NOTICES.md
 * 资产位置：assets/workout-guide/<slug>.svg
 *
 * 与工单原文的差异（实测，非改需求）：
 * 上游 manifest 里这 38 个动作只有 **SVG 帧**，没有 frame-1.png。
 * 因此这里存的是 512×512 透明底 SVG 首帧，用 coil-svg 本地离线解码。
 * 素材是纯白剪影（fill="#fff"），所以 UI 层会用 ColorFilter 染成主题深色，
 * 底板仍用浅色 —— 既不改原始素材，也不会白图贴浅底看不见。
 *
 * 规则：
 * - 键一律用 TrainingSeed 里写死的动作 UUID，绝不按中文名拼路径；
 * - 不把图片路径写进 Room，训练历史 / 计划 / 备份结构一律不动；
 * - 自建动作 / 未映射动作返回 null，界面显示统一默认占位；
 * - requiresReview = true 的 6 个动作是近似匹配，详情会标注「动作示意，姿势待核对」。
 * ============================================================
 */
object ExerciseMediaMapping {

    data class ExerciseMedia(
        val slug: String,
        val requiresReview: Boolean = false
    )

    /** 38 个内置动作 UUID → 素材。顺序与 TrainingSeed 一致，便于对照。 */
    val mappings: Map<String, ExerciseMedia> = mapOf(
        // 胸 6
        "ex-chest-01" to ExerciseMedia("dumbbell-bench-press"),
        "ex-chest-02" to ExerciseMedia("bench-press"),
        "ex-chest-03" to ExerciseMedia("push-up"),
        "ex-chest-04" to ExerciseMedia("incline-dumbbell-press"),
        "ex-chest-05" to ExerciseMedia("machine-chest-press"),
        "ex-chest-06" to ExerciseMedia("dumbbell-fly"),
        // 背 6
        "ex-back-01" to ExerciseMedia("pull-up"),
        "ex-back-02" to ExerciseMedia("lat-pulldown"),
        "ex-back-03" to ExerciseMedia("seated-row"),
        "ex-back-04" to ExerciseMedia("one-arm-dumbbell-row"),
        "ex-back-05" to ExerciseMedia("barbell-row"),
        "ex-back-06" to ExerciseMedia("deadlift"),
        // 肩 5
        "ex-shoulder-01" to ExerciseMedia("seated-dumbbell-press", requiresReview = true), // 坐姿 vs 未限定姿势
        "ex-shoulder-02" to ExerciseMedia("lateral-raise"),
        "ex-shoulder-03" to ExerciseMedia("face-pull"),
        "ex-shoulder-04" to ExerciseMedia("arnold-press"),
        "ex-shoulder-05" to ExerciseMedia("bent-over-rear-delt-raise"),
        // 手臂 5
        "ex-arm-01" to ExerciseMedia("ez-bar-curl", requiresReview = true),     // EZ 杠 vs 直杆
        "ex-arm-02" to ExerciseMedia("bicep-curl", requiresReview = true),      // 普通弯举 vs 交替弯举
        "ex-arm-03" to ExerciseMedia("tricep-pushdown"),
        "ex-arm-04" to ExerciseMedia("diamond-push-up", requiresReview = true), // 钻石 vs 普通窄距
        "ex-arm-05" to ExerciseMedia("hammer-curl"),
        // 腿 6
        "ex-leg-01" to ExerciseMedia("squat"),
        "ex-leg-02" to ExerciseMedia("goblet-squat", requiresReview = true),    // 高脚杯 vs 自然持铃
        "ex-leg-03" to ExerciseMedia("dumbbell-romanian-deadlift"),
        "ex-leg-04" to ExerciseMedia("leg-press"),
        "ex-leg-05" to ExerciseMedia("forward-lunge"),
        "ex-leg-06" to ExerciseMedia("calf-raise"),
        // 核心 5
        "ex-core-01" to ExerciseMedia("plank"),
        "ex-core-02" to ExerciseMedia("crunch"),
        "ex-core-03" to ExerciseMedia("dead-bug"),
        "ex-core-04" to ExerciseMedia("russian-twist"),
        "ex-core-05" to ExerciseMedia("hanging-leg-raise"),
        // 拉伸 5
        "ex-stretch-01" to ExerciseMedia("doorway-chest-stretch"),
        "ex-stretch-02" to ExerciseMedia("childs-pose", requiresReview = true), // 儿童式 vs 背阔肌专项拉伸
        "ex-stretch-03" to ExerciseMedia("standing-quad-stretch"),
        "ex-stretch-04" to ExerciseMedia("hamstring-stretch"),
        "ex-stretch-05" to ExerciseMedia("kneeling-hip-flexor-stretch")
    )

    /** 内置动作 → 图片；自建动作 / 未映射动作返回 null。 */
    fun forUuid(uuid: String): ExerciseMedia? = mappings[uuid]

    /** Coil 用的 android_asset URI（本地离线，不联网）。 */
    fun assetPath(slug: String): String = "file:///android_asset/workout-guide/$slug.svg"

    /** 需要人工核对近似差异的 UUID 清单（6 个）。 */
    val requiresReviewUuids: Set<String> = mappings.filterValues { it.requiresReview }.keys
}
