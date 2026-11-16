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
 *   · 127 条有图：123 条走三帧候选素材（其中老动作 34 条、200 条库新动作 89 条），
 *     这 123 条的 review_status = candidate_pending_visual_review（待人工核图），
 *     其中 9 条为近似/别名候选（requiresReview=true，UI 额外标注「近似匹配」）；
 *   · 另外 4 条老动作没有三帧素材，沿用它自己原来的单帧 svg（workout-guide 目录），
 *     review_status = legacy_single_frame_asset，是原图、不是候选图，UI 不标待核对；
 *   · 83 条无可靠图片（needs_new_asset_search）：不在映射里，UI 显示统一占位，
 *     绝不拿别的动作图片顶替；
 *   · 38 个老动作一条不落，升级后不会出现「以前有图、现在没图」；
 *   · 键一律用稳定 UUID，禁止运行时按中文名模糊查找；
 *   · 图片路径不写进 Room，训练历史/计划/备份结构完全不动。
 *
 * 备注：老动作和 200 条库里的同名动作会指向同一份素材（同一个 slug 被两条引用），
 * 这是同一张图，不是顶替。
 * Kotlin 的块注释可以嵌套，所以注释里不要写「斜杠加星号」那种通配写法，
 * 否则整份文件会被判成注释没闭合（Unclosed comment）。
 * ============================================================
 */
object ExerciseMediaMapping {

    /** 候选匹配、待人工核图。 */
    const val REVIEW_CANDIDATE = "candidate_pending_visual_review"

    /** 老动作沿用旧版单帧素材，本来就是原图，不算待核图。 */
    const val REVIEW_LEGACY = "legacy_single_frame_asset"

    data class ExerciseMedia(
        val slug: String,
        /** true = 近似/别名候选，UI 需标注「动作示意，姿势待核对」。 */
        val requiresReview: Boolean = false,
        /** 审核状态：候选图统一为待核图；老动作沿用原有单帧素材，状态单独标记。 */
        val reviewStatus: String = REVIEW_CANDIDATE,
        /**
         * true = 这个老动作没有三帧素材，只能沿用旧版单帧 svg。
         * 这不是「拿相似动作顶替」，而是它自己原来的图，所以界面不标「待核对」。
         */
        val legacySingleFrame: Boolean = false
    ) {
        /**
         * 第 index 帧的本地 asset URI（离线、不联网）。
         * 单帧素材忽略 index，永远返回同一张。
         */
        fun frameUri(index: Int): String =
            if (legacySingleFrame) "file:///android_asset/workout-guide/" + slug + ".svg"
            else "file:///android_asset/exercises/" + slug + "/frame-" + index + ".svg"
    }

    /** 三帧连续播放序列 1→2→3→2，每帧 420ms，循环。 */
    val PLAY_SEQUENCE = intArrayOf(1, 2, 3, 2)
    const val FRAME_DURATION_MS = 420L
    const val LOOP = true

    /** 123 条三帧候选 UUID → 素材（老动作 34 + 新动作 89）。83 条无图的动作不在表里。 */
    private val threeFrameMappings: Map<String, ExerciseMedia> = mapOf(
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
        // ex-shoulder-05（俯身后束飞鸟）自己有旧单帧原图，见下面 legacy 表，这里不挂近似素材
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
        // ex-leg-03（哑铃罗马尼亚硬拉）自己有旧单帧原图，见下面 legacy 表，这里不挂近似素材
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
        "candidate-stretch-020" to ExerciseMedia("wall-calf-stretch", requiresReview = false),

        // ---- 老动作补齐（8 条）----
        // 这 8 个老动作的三帧素材本来就在 117 目录里，但之前只挂在同名候选 UUID 上，
        // 老动作自己的 UUID 查不到图，会出现「以前有图、升级后反而没图」的倒退。
        // 这里把它们指回同一份素材：同一张图，不是拿别的动作顶替。
        "ex-shoulder-03" to ExerciseMedia("face-pull"),
        "ex-arm-01" to ExerciseMedia("ez-bar-curl", requiresReview = true),
        "ex-arm-04" to ExerciseMedia("diamond-push-up", requiresReview = true),
        "ex-leg-02" to ExerciseMedia("goblet-squat", requiresReview = true),
        "ex-stretch-01" to ExerciseMedia("doorway-chest-stretch"),
        "ex-stretch-02" to ExerciseMedia("childs-pose", requiresReview = true),
        "ex-stretch-03" to ExerciseMedia("standing-quad-stretch"),
        "ex-stretch-05" to ExerciseMedia("kneeling-hip-flexor-stretch")
    )

    /**
     * 4 个老动作没有三帧素材，只能用它们自己原来的单帧 svg（assets/workout-guide/）。
     * 这是原有素材，不是候选图，所以界面不标「待核对」。
     */
    private val legacySingleFrameMappings: Map<String, ExerciseMedia> = mapOf(
        "ex-shoulder-05" to ExerciseMedia("bent-over-rear-delt-raise", legacySingleFrame = true, reviewStatus = REVIEW_LEGACY),
        "ex-leg-03" to ExerciseMedia("dumbbell-romanian-deadlift", legacySingleFrame = true, reviewStatus = REVIEW_LEGACY),
        "ex-leg-05" to ExerciseMedia("forward-lunge", legacySingleFrame = true, reviewStatus = REVIEW_LEGACY),
        "ex-leg-06" to ExerciseMedia("calf-raise", legacySingleFrame = true, reviewStatus = REVIEW_LEGACY)
    )

    /** 全部有图的动作：三帧候选 123 条 + 老单帧 4 条 = 127 条。 */
    val mappings: Map<String, ExerciseMedia> =
        threeFrameMappings + legacySingleFrameMappings

    /** 有候选图的动作 → 图片；无图 / 自建动作返回 null，界面走统一占位。 */
    fun forUuid(uuid: String): ExerciseMedia? = mappings[uuid]

    /** 第 index 帧的本地 asset URI（index 取 1..3，离线、不联网）。 */
    fun framePath(slug: String, index: Int): String =
        "file:///android_asset/exercises/" + slug + "/frame-" + index + ".svg"

    /** 兼容旧调用：单帧 asset URI（UI 已切三帧，保留供其它模块/测试使用）。 */
    fun assetPath(slug: String): String = "file:///android_asset/workout-guide/$slug.svg"

    /** 近似匹配、需人工核对姿势的 UUID 清单（9 条：老动作 8 条 + 新动作 1 条）。 */
    val requiresReviewUuids: Set<String> = mappings.filterValues { it.requiresReview }.keys
}
