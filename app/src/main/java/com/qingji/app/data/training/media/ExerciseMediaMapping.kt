package com.qingji.app.data.training.media

/**
 * ============================================================
 * 动作库插画映射（200 条动作库接入）
 * ------------------------------------------------------------
 * 素材来源：Workout Guide（github.com/bryllim/workout-guide）
 *   · 作者 Bryl Lim，视觉素材 CC BY-SA 4.0；76 个动作首帧源自 Everkinetic，
 *     逐帧署名见 docs/exercise-library/metadata/asset_licenses.csv 与 THIRD_PARTY_NOTICES.md。
 * 资产位置：app/src/main/assets/exercises/<slug>/frame-{1,2,3}.svg（三帧连续播放）
 *
 * 状态与边界（绝不虚报审核）：
 *   · 117 条有候选图，全部 review_status = candidate_pending_visual_review（待人工核图）；
 *     其中 111 条中文名直接匹配、6 条为别名候选（requiresReview=true，UI 标注待核对）；
 *   · 83 条无可靠图片（needs_new_asset_search）：不在映射里，UI 显示统一占位，
 *     绝不拿别的动作图片顶替；
 *   · 键一律用稳定 UUID，禁止运行时按中文名模糊查找；
 *   · 图片路径不写进 Room，训练历史/计划/备份结构完全不动。
 *
 * 新版 38 个老动作也走三帧体系（旧的单帧 workout-guide 同名 svg 保留仅作兼容，UI 不再读）。
 * 注意：上面这句原来写成「workout-guide/星号.svg」带通配符，
 * 而 Kotlin 注释可以嵌套，星号紧跟斜杠会被当成新注释开头，整个文件就报 Unclosed comment。
 * ============================================================
 */
object ExerciseMediaMapping {

    data class ExerciseMedia(
        val slug: String,
        /** true = 近似/别名候选，UI 需标注「动作示意，姿势待核对」。 */
        val requiresReview: Boolean = false,
        /** 审核状态：全部为候选匹配待核图，人工通过后在项目清单里改，不改这里的代码语义。 */
        val reviewStatus: String = "candidate_pending_visual_review"
    )

    /** 三帧连续播放序列 1→2→3→2，每帧 420ms，循环。 */
    val PLAY_SEQUENCE = intArrayOf(1, 2, 3, 2)
    const val FRAME_DURATION_MS = 420L
    const val LOOP = true

    /** 117 条 UUID → 候选素材。83 条无图的动作不在表里。 */
    val mappings: Map<String, ExerciseMedia> = mapOf(
        "ex-chest-01" to ExerciseMedia("dumbbell-bench-press", requiresReview = false),
        "ex-chest-02" to ExerciseMedia("bench-press", requiresReview = false),
        "ex-chest-03" to ExerciseMedia("push-up", requiresReview = false),
        "ex-chest-04" to ExerciseMedia("incline-dumbbell-press", requiresReview = false),
        "ex-chest-05" to ExerciseMedia("machine-chest-press", requiresReview = false),
        "ex-chest-06" to ExerciseMedia("dumbbell-fly", requiresReview = false),
        "candidate-chest-007" to ExerciseMedia("incline-bench-press", requiresReview = false),
        "candidate-chest-009" to ExerciseMedia("decline-bench-press", requiresReview = false),
        "candidate-chest-010" to ExerciseMedia("decline-dumbbell-press", requiresReview = false),
        "candidate-chest-011" to ExerciseMedia("smith-machine-bench-press", requiresReview = false),
        "candidate-chest-018" to ExerciseMedia("pec-deck", requiresReview = false),
        "candidate-chest-019" to ExerciseMedia("cable-fly", requiresReview = false),
        "candidate-chest-024" to ExerciseMedia("incline-push-up", requiresReview = false),
        "candidate-chest-025" to ExerciseMedia("decline-push-up", requiresReview = false),
        "candidate-chest-027" to ExerciseMedia("diamond-push-up", requiresReview = false),
        "candidate-chest-028" to ExerciseMedia("chest-dip", requiresReview = false),
        "ex-back-01" to ExerciseMedia("pull-up", requiresReview = false),
        "ex-back-02" to ExerciseMedia("lat-pulldown", requiresReview = false),
        "ex-back-03" to ExerciseMedia("seated-row", requiresReview = true),
        "ex-back-04" to ExerciseMedia("one-arm-dumbbell-row", requiresReview = false),
        "ex-back-05" to ExerciseMedia("barbell-row", requiresReview = true),
        "ex-back-06" to ExerciseMedia("deadlift", requiresReview = true),
        "candidate-back-007" to ExerciseMedia("chin-up", requiresReview = false),
        "candidate-back-008" to ExerciseMedia("assisted-pull-up", requiresReview = false),
        "candidate-back-009" to ExerciseMedia("wide-grip-lat-pulldown", requiresReview = false),
        "candidate-back-010" to ExerciseMedia("close-grip-lat-pulldown", requiresReview = false),
        "candidate-back-013" to ExerciseMedia("straight-arm-pulldown", requiresReview = false),
        "candidate-back-015" to ExerciseMedia("pendlay-row", requiresReview = false),
        "candidate-back-016" to ExerciseMedia("dumbbell-bent-over-row", requiresReview = false),
        "candidate-back-017" to ExerciseMedia("chest-supported-row", requiresReview = false),
        "candidate-back-018" to ExerciseMedia("machine-row", requiresReview = false),
        "candidate-back-021" to ExerciseMedia("t-bar-row", requiresReview = false),
        "candidate-back-025" to ExerciseMedia("single-arm-cable-row", requiresReview = false),
        "candidate-back-026" to ExerciseMedia("inverted-row", requiresReview = false),
        "candidate-back-027" to ExerciseMedia("sumo-deadlift", requiresReview = false),
        "candidate-back-028" to ExerciseMedia("rack-pull", requiresReview = false),
        "candidate-back-030" to ExerciseMedia("good-morning", requiresReview = false),
        "candidate-back-031" to ExerciseMedia("back-extension", requiresReview = false),
        "ex-shoulder-01" to ExerciseMedia("seated-dumbbell-press", requiresReview = false),
        "ex-shoulder-02" to ExerciseMedia("lateral-raise", requiresReview = false),
        "ex-shoulder-04" to ExerciseMedia("arnold-press", requiresReview = false),
        "ex-shoulder-05" to ExerciseMedia("rear-delt-fly", requiresReview = true),
        "candidate-shoulder-006" to ExerciseMedia("overhead-press", requiresReview = false),
        "candidate-shoulder-010" to ExerciseMedia("machine-shoulder-press", requiresReview = false),
        "candidate-shoulder-011" to ExerciseMedia("landmine-press", requiresReview = false),
        "candidate-shoulder-013" to ExerciseMedia("front-raise", requiresReview = false),
        "candidate-shoulder-017" to ExerciseMedia("cable-lateral-raise", requiresReview = false),
        "candidate-shoulder-020" to ExerciseMedia("reverse-pec-deck", requiresReview = false),
        "candidate-shoulder-021" to ExerciseMedia("face-pull", requiresReview = false),
        "candidate-shoulder-024" to ExerciseMedia("shrug", requiresReview = true),
        "ex-arm-02" to ExerciseMedia("bicep-curl", requiresReview = true),
        "ex-arm-03" to ExerciseMedia("tricep-pushdown", requiresReview = false),
        "ex-arm-05" to ExerciseMedia("hammer-curl", requiresReview = false),
        "candidate-arm-006" to ExerciseMedia("ez-bar-curl", requiresReview = false),
        "candidate-arm-008" to ExerciseMedia("incline-dumbbell-curl", requiresReview = false),
        "candidate-arm-009" to ExerciseMedia("concentration-curl", requiresReview = false),
        "candidate-arm-010" to ExerciseMedia("preacher-curl", requiresReview = false),
        "candidate-arm-011" to ExerciseMedia("cable-curl", requiresReview = false),
        "candidate-arm-013" to ExerciseMedia("reverse-curl", requiresReview = false),
        "candidate-arm-016" to ExerciseMedia("close-grip-bench-press", requiresReview = false),
        "candidate-arm-017" to ExerciseMedia("skull-crusher", requiresReview = false),
        "candidate-arm-019" to ExerciseMedia("dumbbell-overhead-tricep-extension", requiresReview = false),
        "candidate-arm-020" to ExerciseMedia("single-arm-dumbbell-tricep-extension", requiresReview = false),
        "candidate-arm-022" to ExerciseMedia("overhead-tricep-extension", requiresReview = false),
        "candidate-arm-024" to ExerciseMedia("dip", requiresReview = false),
        "candidate-arm-025" to ExerciseMedia("wrist-curl", requiresReview = false),
        "ex-leg-01" to ExerciseMedia("squat", requiresReview = false),
        "ex-leg-03" to ExerciseMedia("romanian-deadlift", requiresReview = false),
        "ex-leg-04" to ExerciseMedia("leg-press", requiresReview = false),
        "candidate-leg-009" to ExerciseMedia("front-squat", requiresReview = false),
        "candidate-leg-011" to ExerciseMedia("goblet-squat", requiresReview = false),
        "candidate-leg-015" to ExerciseMedia("hack-squat", requiresReview = false),
        "candidate-leg-016" to ExerciseMedia("leg-extension", requiresReview = false),
        "candidate-leg-017" to ExerciseMedia("bulgarian-split-squat", requiresReview = false),
        "candidate-leg-019" to ExerciseMedia("reverse-lunge", requiresReview = false),
        "candidate-leg-020" to ExerciseMedia("walking-lunge", requiresReview = false),
        "candidate-leg-021" to ExerciseMedia("lateral-lunge", requiresReview = false),
        "candidate-leg-023" to ExerciseMedia("step-up", requiresReview = false),
        "candidate-leg-025" to ExerciseMedia("glute-bridge", requiresReview = false),
        "candidate-leg-026" to ExerciseMedia("hip-thrust", requiresReview = false),
        "candidate-leg-027" to ExerciseMedia("single-leg-glute-bridge", requiresReview = false),
        "candidate-leg-028" to ExerciseMedia("cable-kickback", requiresReview = false),
        "candidate-leg-029" to ExerciseMedia("hip-abduction-machine", requiresReview = false),
        "candidate-leg-032" to ExerciseMedia("single-leg-romanian-deadlift", requiresReview = false),
        "candidate-leg-033" to ExerciseMedia("leg-curl", requiresReview = false),
        "candidate-leg-034" to ExerciseMedia("lying-leg-curl", requiresReview = false),
        "candidate-leg-035" to ExerciseMedia("seated-leg-curl", requiresReview = false),
        "candidate-leg-037" to ExerciseMedia("standing-calf-raise", requiresReview = false),
        "candidate-leg-038" to ExerciseMedia("seated-calf-raise", requiresReview = false),
        "candidate-leg-040" to ExerciseMedia("kettlebell-swing", requiresReview = false),
        "ex-core-01" to ExerciseMedia("plank", requiresReview = false),
        "ex-core-02" to ExerciseMedia("crunch", requiresReview = false),
        "ex-core-03" to ExerciseMedia("dead-bug", requiresReview = false),
        "ex-core-04" to ExerciseMedia("russian-twist", requiresReview = false),
        "ex-core-05" to ExerciseMedia("hanging-leg-raise", requiresReview = false),
        "candidate-core-006" to ExerciseMedia("reverse-crunch", requiresReview = false),
        "candidate-core-008" to ExerciseMedia("v-up", requiresReview = false),
        "candidate-core-009" to ExerciseMedia("lying-leg-raise", requiresReview = false),
        "candidate-core-010" to ExerciseMedia("hanging-knee-raise", requiresReview = false),
        "candidate-core-012" to ExerciseMedia("ab-wheel", requiresReview = false),
        "candidate-core-013" to ExerciseMedia("bicycle-crunch", requiresReview = false),
        "candidate-core-015" to ExerciseMedia("cable-woodchop", requiresReview = false),
        "candidate-core-017" to ExerciseMedia("side-plank", requiresReview = false),
        "candidate-core-020" to ExerciseMedia("bird-dog", requiresReview = false),
        "candidate-core-021" to ExerciseMedia("hollow-body-hold", requiresReview = false),
        "candidate-core-022" to ExerciseMedia("cable-crunch", requiresReview = false),
        "candidate-core-023" to ExerciseMedia("pallof-press", requiresReview = false),
        "candidate-core-024" to ExerciseMedia("mountain-climber", requiresReview = false),
        "candidate-core-025" to ExerciseMedia("farmer-carry", requiresReview = false),
        "ex-stretch-04" to ExerciseMedia("hamstring-stretch", requiresReview = false),
        "candidate-stretch-006" to ExerciseMedia("doorway-chest-stretch", requiresReview = false),
        "candidate-stretch-008" to ExerciseMedia("cross-body-shoulder-stretch", requiresReview = false),
        "candidate-stretch-013" to ExerciseMedia("childs-pose", requiresReview = false),
        "candidate-stretch-014" to ExerciseMedia("seated-forward-fold-stretch", requiresReview = false),
        "candidate-stretch-017" to ExerciseMedia("standing-quad-stretch", requiresReview = false),
        "candidate-stretch-019" to ExerciseMedia("kneeling-hip-flexor-stretch", requiresReview = false),
        "candidate-stretch-020" to ExerciseMedia("wall-calf-stretch", requiresReview = false),    )

    /** 有候选图的动作 → 图片；无图 / 自建动作返回 null，界面走统一占位。 */
    fun forUuid(uuid: String): ExerciseMedia? = mappings[uuid]

    /** 第 index 帧的本地 asset URI（index 取 1..3，离线、不联网）。 */
    fun framePath(slug: String, index: Int): String =
        "file:///android_asset/exercises/" + slug + "/frame-" + index + ".svg"

    /** 兼容旧调用：单帧 asset URI（UI 已切三帧，保留供其它模块/测试使用）。 */
    fun assetPath(slug: String): String = "file:///android_asset/workout-guide/$slug.svg"

    /** 近似匹配、需人工核对姿势的 UUID 清单（6 条别名候选）。 */
    val requiresReviewUuids: Set<String> = mappings.filterValues { it.requiresReview }.keys
}
