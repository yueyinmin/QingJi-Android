package com.qingji.app.data.training.media

import com.qingji.app.data.local.TrainingSeed

/**
 * Workout Guide 302 条动作的三帧本地素材映射。
 *
 * 与旧版「按名字猜候选图」不同：这里 302 条都来自上游 manifest 的显式 slug，
 * 素材路径由 TrainingSeed 写死，属于本地权威素材，不再标「候选匹配待核图」。
 * 授权逐条保留：76 条 Everkinetic 首帧来源不泛化到全部动作。
 */
object ExerciseMediaMapping {

    const val REVIEW_UPSTREAM = "upstream_source_structurally_verified"

    data class ExerciseMedia(
        val slug: String,
        /** 相对 assets 的路径，例如 exercises/chest/bench-press/frame-1.svg */
        val assetPath: String,
        val attribution: String,
        val reviewStatus: String = REVIEW_UPSTREAM
    ) {
        /** 第 index 帧的本地 asset URI（1..3），离线、不联网。 */
        fun frameUri(index: Int): String =
            "file:///android_asset/" + assetPath.replace("frame-1.svg", "frame-" + index + ".svg")
    }

    /** 1 → 2 → 3 → 2 循环，每帧 420ms。 */
    val PLAY_SEQUENCE = intArrayOf(1, 2, 3, 2)
    const val FRAME_DURATION_MS = 420L
    const val LOOP = true

    private val byUuid: Map<String, ExerciseMedia> = TrainingSeed.builtIns(0L).associate { seed ->
        seed.uuid to ExerciseMedia(
            slug = seed.slug,
            assetPath = seed.assetPath,
            attribution = seed.attribution
        )
    }

    val mappings: Map<String, ExerciseMedia> get() = byUuid

    /** 没有可靠素材的自建动作返回 null，界面走统一占位。 */
    fun forUuid(uuid: String): ExerciseMedia? = byUuid[uuid]

    /** 上游素材不是按名字猜测的候选，所以没有「近似匹配待核对」清单。 */
    val requiresReviewUuids: Set<String> = emptySet()
}
