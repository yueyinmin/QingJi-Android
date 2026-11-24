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
        assetPath = "",
        attribution = "",
        recordType = ExerciseRecordType.CARDIO.name
    )
}
