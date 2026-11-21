package com.qingji.app.domain

/**
 * 统一运动入口列表项（阶段 C）。
 * 把两套数据源（有氧/快速补记的 ExercisePreset 与力量动作库 ExerciseEntity）
 * 归一为单一列表项，带解析后的 recordType，供入口页按类型进入对应表单。
 *
 * 不按名称/MET 判断类型：统一走 TrainingTypeResolver。
 */
data class UnifiedExerciseOption(
    val key: String,              // "preset:<id>" 或 "action:<uuid>"
    val name: String,
    val recordType: ExerciseRecordType,
    val met: Double? = null,      // 仅 preset 有；力量动作为 null，不显示 MET
    val presetId: Long? = null,   // 有值 → 走 ExerciseConfirm 快速补记
    val exerciseUuid: String? = null, // 有值 → 走力量动作快速补记
    val category: String = "",
    val favorite: Boolean = false,
    val recentOrder: Int = Int.MAX_VALUE
) {
    /** 是否走力量动作补记（动作库条目）。 */
    val isStrengthAction: Boolean get() = exerciseUuid != null && presetId == null
}

object UnifiedExerciseOptionFactory {

    /**
     * 从 ExercisePreset 构造。
     * preset 没有 UUID 映射，按名称/category 走 resolver（HIIT 白名单/柔韧命名优先），
     * 其余归 CARDIO（preset 本质是有氧 MET 预设）。
     */
    fun fromPreset(
        id: Long, name: String, met: Double, category: String,
        favorite: Boolean, recentOrder: Int = Int.MAX_VALUE
    ): UnifiedExerciseOption {
        val type = TrainingTypeResolver.resolve(
            persistedRecordType = null, name = name, englishName = null,
            exerciseType = null, category = category
        ).let { if (it.type == ExerciseRecordType.OTHER) ExerciseRecordType.CARDIO else it.type }
        // preset 的 category 是中文（有氧/力量/柔韧/自定义），resolver 不认中文时按上面兜底 CARDIO
        val resolved = when (category) {
            "柔韧" -> ExerciseRecordType.FLEXIBILITY
            "力量" -> ExerciseRecordType.STRENGTH
            "自定义" -> ExerciseRecordType.OTHER
            else -> type
        }
        return UnifiedExerciseOption(
            key = "preset:$id", name = name, recordType = resolved, met = met,
            presetId = id, category = category, favorite = favorite, recentOrder = recentOrder
        )
    }

    /**
     * 从 ExerciseEntity（动作库）构造：UUID 精确映射优先，回退 resolver。
     */
    fun fromAction(
        uuid: String, name: String, englishName: String = "", exerciseType: String = "",
        category: String = "", isStretch: Boolean = false, recordTypeStored: String = "",
        favorite: Boolean = false, recentOrder: Int = Int.MAX_VALUE
    ): UnifiedExerciseOption = UnifiedExerciseOption(
        key = "action:$uuid", name = name,
        recordType = TrainingTypeResolver.resolve(
            persistedRecordType = recordTypeStored, name = name, englishName = englishName,
            exerciseType = exerciseType, category = category, isStretch = isStretch,
            exerciseUuid = uuid
        ).type,
        exerciseUuid = uuid, category = category, favorite = favorite, recentOrder = recentOrder
    )

    /** 合并排序：收藏/最近优先，其余按名称；力量动作与预设不重复（同名时动作库优先）。 */
    fun merge(
        presets: List<UnifiedExerciseOption>, actions: List<UnifiedExerciseOption>
    ): List<UnifiedExerciseOption> {
        val actionNames = actions.map { it.name }.toSet()
        return (actions + presets.filterNot { it.name in actionNames })
            .sortedWith(compareBy({ it.recentOrder }, { !it.favorite }, { it.name }))
    }
}
