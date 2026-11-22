package com.qingji.app.domain

/**
 * 统一运动记录类型（统一运动体系 V2.1 / B1）。
 *
 * 三概念严格区分：
 * - category：动作库浏览/筛选分类（chest/back/legs...），不参与记录类型判断。
 * - exerciseType：动作的数据输入形式（weight_reps/bodyweight_reps/assisted_bodyweight/
 *   duration/distance_duration），参与默认表单映射但不等于记录类型。
 * - recordType（本枚举）：决定默认记录表单与记录生命周期；用户可手动修正。
 *
 * workoutMode（WorkoutItem.mode）决定同一动作在当前训练中的实际组织方式，
 * 与本类型解耦：同一动作可在不同 mode 下用不同表单。
 *
 * 【事实基线】302 条种子真实取值：weight_reps 136 / bodyweight_reps 114 /
 * assisted_bodyweight 3 / duration 39 / distance_duration 10；无 MET 字段。
 */
enum class ExerciseRecordType(val key: String) {
    STRENGTH("STRENGTH"),
    CARDIO("CARDIO"),
    HIIT("HIIT"),
    FLEXIBILITY("FLEXIBILITY"),
    OTHER("OTHER");

    companion object {
        /** 安全解析：未知/空值返回 null，由调用方降级为 OTHER 并保留诊断。 */
        fun fromKey(key: String?): ExerciseRecordType? =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) }
    }
}

/**
 * 训练组织方式：决定同一 recordType 动作的实际表单分支。
 * 旧历史/草稿没有 mode 时缺省 NORMAL，不猜测、不回填。
 */
enum class WorkoutMode(val key: String) {
    NORMAL("NORMAL"),
    CONTINUOUS("CONTINUOUS"),
    INTERVAL("INTERVAL"),
    CIRCUIT("CIRCUIT");

    companion object {
        fun fromKey(key: String?): WorkoutMode =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: NORMAL
    }
}

/**
 * 统一类型解析（集中服务）：所有页面/Repository/统计共用，禁止各页面自行按名称/MET 判断。
 *
 * 优先级（不使用 MET>0 判型）：
 * 1. 已持久化且合法的 recordType（用户手动修正优先）；
 * 2. HIIT 明确白名单；
 * 3. 明确拉伸标记 / mobility / 拉伸命名；
 * 4. 已核实的动作类型映射（exerciseType）；
 * 5. 无法识别 → OTHER（降级，保留诊断信息）。
 */
object TrainingTypeResolver {

    /** 解析诊断：便于日志追踪未知值（工单 §1.3 第 10 条）。 */
    data class Resolution(
        val type: ExerciseRecordType,
        val reason: String,
        val diagnostic: String? = null
    )

    /** 明确 HIIT 关键词（仅名称白名单，不用 MET）。 */
    private val HIIT_WORDS = listOf("hiit", "高强度间歇", "波比跳", "战绳", "battle rope", "burpee")

    /** 拉伸关键词（isStretch/category 之外的命名兜底）。 */
    private val FLEX_WORDS = listOf(
        "拉伸", "stretch", "婴儿式", "蝴蝶式", "坐姿前屈", "child's pose", "butterfly",
        // 运动预设（SeedData.kt）里 category="柔韧" 的中文项目，无 isStretch 标记，靠名称兜底
        "瑜伽", "yoga", "普拉提", "pilates"
    )

    /**
     * @param persistedRecordType 已持久化的类型（可空，用户手动修正优先）
     * @param name 动作名
     * @param englishName 英文名（可选）
     * @param exerciseType 输入形式（weight_reps 等）
     * @param category 浏览分类
     * @param isStretch 拉伸标记
     */
    fun resolve(
        persistedRecordType: String?,
        name: String?,
        englishName: String? = null,
        exerciseType: String? = null,
        category: String? = null,
        isStretch: Boolean = false,
        exerciseUuid: String? = null
    ): Resolution {
        // 1. 持久化合法值（用户手动修正优先，不被动作库分类变化覆盖）
        ExerciseRecordType.fromKey(persistedRecordType)?.let {
            return Resolution(it, "persisted")
        }
        // 2. 302 条正式映射：按 UUID 精确查询（禁止名称模糊匹配；已映射动作不被重新推断）
        if (!exerciseUuid.isNullOrBlank()) {
            ExerciseRecordTypeMap.find(exerciseUuid)?.let {
                return Resolution(it, "uuid-map")
            }
        }
        val n = (name.orEmpty() + " " + englishName.orEmpty()).lowercase()
        // 2. HIIT 白名单
        if (HIIT_WORDS.any { n.contains(it) }) return Resolution(ExerciseRecordType.HIIT, "hiit-whitelist")
        // 3. 拉伸
        if (isStretch || category.equals("mobility", true) || FLEX_WORDS.any { n.contains(it) }) {
            return Resolution(ExerciseRecordType.FLEXIBILITY, "stretch-flag-or-name")
        }
        // 4. 动作输入形式映射
        when (exerciseType?.lowercase()) {
            "weight_reps", "bodyweight_reps", "assisted_bodyweight" ->
                return Resolution(ExerciseRecordType.STRENGTH, "exerciseType-reps")
            "distance_duration" ->
                return Resolution(ExerciseRecordType.CARDIO, "exerciseType-distance")
        }
        // duration 等无法仅凭输入形式确定的 → OTHER（保留诊断，不强行归 STRENGTH）
        return Resolution(
            ExerciseRecordType.OTHER,
            "fallback",
            diagnostic = "name=$name exerciseType=$exerciseType category=$category isStretch=$isStretch"
        )
    }
}
