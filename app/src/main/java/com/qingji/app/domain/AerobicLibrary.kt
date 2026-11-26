package com.qingji.app.domain

import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.local.entity.ExercisePreset

/**
 * 有氧动作库 / 力训动作库的划分口径（2026-11-24）。
 *
 * 数据来源有两类，都进「有氧动作库」：
 *  1. 动作库 302 条里被 ExerciseRecordTypeMap 正式标为 CARDIO 的动作（22 条）；
 *  2. 「记录有氧」原有的运动预设（跑步/快走/骑行等，带 MET，存在另一张表）。
 * 力训动作库 = 302 条里除 CARDIO 之外的动作。
 *
 * 判定一律以正式映射表为准，禁止按名称/MET 猜测（与 B1.1 映射表规则一致）。
 */
object AerobicLibrary {

    /** 预设项转成内存版 ExerciseEntity 时用的 uuid 前缀，避免和 302 条真 uuid 撞车。 */
    const val PRESET_UUID_PREFIX = "preset:"

    /**
     * 预设 → 本地图示（复用 302 条现有 SVG，按最接近的动作匹配）。
     * 羽毛球/乒乓球/篮球/足球无可靠对应素材，保持占位不硬凑。
     */
    val PRESET_IMAGE: Map<String, String> = mapOf(
        "快走 (5 km/h)" to "exercises/legs/walking/frame-1.svg",
        "快走 (6 km/h)" to "exercises/legs/walking/frame-1.svg",
        "上坡快走" to "exercises/legs/treadmill-incline-walk/frame-1.svg",
        "跑步 (8 km/h)" to "exercises/legs/running/frame-1.svg",
        "跑步 (11 km/h)" to "exercises/legs/running/frame-1.svg",
        "跳绳" to "exercises/legs/jump-rope/frame-1.svg",
        "骑行 (19 km/h)" to "exercises/legs/cycling/frame-1.svg",
        "骑行 (23 km/h)" to "exercises/legs/cycling/frame-1.svg",
        "骑行 (26 km/h)" to "exercises/legs/cycling/frame-1.svg",
        "动感单车" to "exercises/legs/assault-bike/frame-1.svg",
        "划船机" to "exercises/back/rowing/frame-1.svg",
        "仰泳" to "exercises/back/swimming/frame-1.svg",
        "自由泳" to "exercises/back/swimming/frame-1.svg",
        "蛙泳" to "exercises/back/swimming/frame-1.svg",
        "爬楼梯" to "exercises/legs/stair-climber/frame-1.svg",
        "椭圆机" to "exercises/legs/elliptical/frame-1.svg",
        "跑步机 (中速)" to "exercises/legs/running/frame-1.svg",
        "HIIT 高强度间歇" to "exercises/shoulders/battle-ropes/frame-1.svg",
        "力量训练（轻）" to "exercises/legs/bodyweight-squat/frame-1.svg",
        "力量训练（重）" to "exercises/legs/squat/frame-1.svg",
        "俯卧撑" to "exercises/chest/push-up/frame-1.svg",
        "深蹲" to "exercises/legs/squat/frame-1.svg",
        "平板支撑" to "exercises/core/plank/frame-1.svg",
        "瑜伽" to "exercises/core/butterfly-stretch/frame-1.svg",
        "拉伸" to "exercises/legs/hamstring-stretch/frame-1.svg",
        "普拉提" to "exercises/core/bird-dog/frame-1.svg",
    )

    /**
     * 302 条动作是否为有氧。
     * 复用既有判定口径 TrainingItemMapper.resolveType：用户手动修正优先，
     * 其次 B1.1 正式映射表；禁止另起一套按名称猜测的逻辑。
     */
    fun isAerobic(entity: ExerciseEntity): Boolean =
        TrainingItemMapper.resolveType(entity.recordType, entity) == ExerciseRecordType.CARDIO

    /** 预设 id 从内存实体 uuid 反解；非预设项返回 null。 */
    fun presetIdOf(uuid: String): Long? =
        uuid.takeIf { it.startsWith(PRESET_UUID_PREFIX) }
            ?.removePrefix(PRESET_UUID_PREFIX)?.toLongOrNull()

    /**
     * 硬伤 1：只有「有氧」类预设能进有氧库。
     * 力量/柔韧预设（俯卧撑、瑜伽等 8 条）必须挡在库外。
     */
    fun isAerobicPreset(preset: ExercisePreset): Boolean =
        preset.category.trim() == "有氧"

    private val parenthetical = Regex("[（(][^）)]*[）)]")

    /**
     * 硬伤 3：去重命名比较。去括号内容、去空格、忽略大小写。
     * 「网球（单打）」→「网球」，「跑步 (8 km/h)」→「跑步」。
     */
    fun canonicalName(name: String): String =
        parenthetical.replace(name, "").replace(Regex("\\s+"), "").lowercase()

    fun sameCanonicalName(a: String, b: String): Boolean =
        canonicalName(a) == canonicalName(b)

    /**
     * 硬伤 4：11-24 新增 22 条动作没有动作级 MET，按 slug 映射到既有
     * 健康参数库 activityType，再用参数库 MET 计算；不新造 MET 数值，
     * 也不让 kcal 静默变成 0。
     */
    val SLUG_ACTIVITY_TYPE: Map<String, String> = mapOf(
        "kickboxing-aerobic" to "hiit",
        "aerobic-dance" to "bodyweight_circuit",
        "step-aerobics" to "bodyweight_circuit",
        "trampoline-fitness" to "bodyweight_circuit",
        "versa-climber" to "hiit",
        "roller-skating" to "cycling",
        "tennis" to "bodyweight_circuit",
        "volleyball" to "bodyweight_circuit",
        "basketball" to "bodyweight_circuit",
        "badminton" to "bodyweight_circuit",
        "soccer" to "running",
        "table-tennis" to "bodyweight_circuit",
        "fencing" to "bodyweight_circuit",
        "heavy-bag-boxing" to "hiit",
        "rock-climbing" to "strength_moderate",
        "alpine-skiing" to "cycling",
        "kayaking" to "cycling",
        "stepper" to "walking_brisk",
        "recumbent-bike" to "cycling",
        "arm-bike" to "bodyweight_circuit",
        "arc-trainer" to "bodyweight_circuit",
        "treadmill-run" to "running"
    )

    /** 参数库没有同名 slug 时，用新增动作的兜底类型；其余返回 null。 */
    fun activityTypeForSlug(slug: String?): String? =
        slug?.takeIf { it.isNotBlank() }?.let { SLUG_ACTIVITY_TYPE[it] }

    /**
     * 把「记录有氧」的预设转成内存版 ExerciseEntity，好让有氧库统一走动作详情流程。
     * 只用于展示与跳转，不写数据库；uuid 带前缀与真动作区分。
     */
    fun fromPreset(preset: ExercisePreset): ExerciseEntity = ExerciseEntity(
        uuid = PRESET_UUID_PREFIX + preset.id,
        name = preset.name,
        category = "cardio",
        primaryMuscles = "有氧",
        secondaryMuscles = "",
        equipment = "",
        steps = "",
        commonMistakes = "",
        isBuiltIn = false,
        isFavorite = preset.favorite,
        createdAt = 0L,
        updatedAt = 0L,
        englishName = "",
        slug = "",
        exerciseType = "有氧",
        isStretch = false,
        assetPath = PRESET_IMAGE[preset.name] ?: "",
        attribution = "",
        recordType = ExerciseRecordType.CARDIO.name
    )
}
