package com.qingji.app.domain

import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.local.entity.WorkoutCardioDetail
import com.qingji.app.data.local.entity.WorkoutExercise
import com.qingji.app.data.prefs.DraftExercise

/**
 * B1.1：草稿 ↔ 统一领域模型 ↔ Room 行的唯一映射入口。
 *
 * 设计边界：
 * - recordType 只通过强类型解析；空串按历史语义回退 STRENGTH，未知 key 降级 OTHER，绝不默认 CARDIO；
 * - 计划目标放在 targetConfigJson，实际完成值放在 WorkoutItem 的 duration/distance/work/rest/rounds 等字段；
 * - WorkoutCardioDetail 只存 CARDIO；HIIT/FLEXIBILITY/OTHER 存 workout_exercise.itemMetaJson（版本化 JSON），
 *   保留类型、目标配置和原始输入，不伪装成力量组或普通有氧；
 * - 全部使用已有 Room 列，不新增表、不升数据库版本。
 */
object TrainingItemMapper {

    /** 空串 = 历史未标记 → STRENGTH；未知/非法 key → OTHER，禁止默认 CARDIO。 */
    fun resolveType(recordTypeKey: String?, entity: ExerciseEntity? = null): ExerciseRecordType {
        ExerciseRecordType.fromKey(recordTypeKey)?.let { return it }
        if (recordTypeKey.isNullOrBlank()) {
            if (entity != null) {
                return TrainingTypeResolver.resolve(
                    persistedRecordType = entity.recordType,
                    name = entity.name,
                    englishName = entity.englishName,
                    exerciseType = entity.exerciseType,
                    category = entity.category,
                    isStretch = entity.isStretch,
                    exerciseUuid = entity.uuid
                ).type
            }
            return ExerciseRecordType.STRENGTH
        }
        return ExerciseRecordType.OTHER
    }

    /** 从草稿里读出已录入的实际数据（旧草稿没有 actualJson 时返回 null）。 */
    fun actualOf(draft: DraftExercise): WorkoutItem? = WorkoutItem.fromJson(draft.actualJson)

    /** 草稿 → 统一项目。actualJson 只提供实际值；目标只来自 targetConfigJson。 */
    fun draftToItem(
        draft: DraftExercise,
        type: ExerciseRecordType,
        sessionUuid: String,
        order: Int,
        kcal: Double = 0.0,
        method: String = "",
        version: String = "",
        weightSnapshot: Double? = null
    ): WorkoutItem {
        val parsed = actualOf(draft)
        val target = draft.targetConfigJson
        return when (type) {
            ExerciseRecordType.STRENGTH -> WorkoutItem.Strength(
                itemId = draft.key,
                exerciseUuid = draft.exerciseUuid,
                name = draft.name,
                order = order,
                note = draft.note,
                sessionUuid = sessionUuid,
                sets = draft.sets,
                targetConfigJson = target
            )
            ExerciseRecordType.CARDIO -> {
                val a = parsed as? WorkoutItem.Cardio
                WorkoutItem.Cardio(
                    itemId = draft.key,
                    exerciseUuid = draft.exerciseUuid,
                    name = draft.name,
                    order = order,
                    mode = a?.mode ?: WorkoutMode.NORMAL,
                    note = draft.note,
                    sessionUuid = sessionUuid,
                    durationSec = a?.durationSec ?: 0L,
                    distanceMeters = a?.distanceMeters,
                    intensity = a?.intensity,
                    calculatedKcal = kcal,
                    calculationMethod = method,
                    calculationVersion = version,
                    weightSnapshot = weightSnapshot,
                    targetConfigJson = target
                )
            }
            ExerciseRecordType.HIIT -> {
                val a = parsed as? WorkoutItem.Hiit
                WorkoutItem.Hiit(
                    itemId = draft.key,
                    exerciseUuid = draft.exerciseUuid,
                    name = draft.name,
                    order = order,
                    mode = a?.mode ?: WorkoutMode.INTERVAL,
                    note = draft.note,
                    sessionUuid = sessionUuid,
                    durationSec = a?.durationSec ?: 0L,
                    intensity = a?.intensity,
                    met = a?.met,
                    workSec = a?.workSec ?: 0,
                    restSec = a?.restSec ?: 0,
                    rounds = a?.rounds ?: 0,
                    calculatedKcal = kcal,
                    calculationVersion = version,
                    weightSnapshot = weightSnapshot,
                    targetConfigJson = target
                )
            }
            ExerciseRecordType.FLEXIBILITY -> {
                val a = parsed as? WorkoutItem.Flexibility
                WorkoutItem.Flexibility(
                    itemId = draft.key,
                    exerciseUuid = draft.exerciseUuid,
                    name = draft.name,
                    order = order,
                    note = draft.note,
                    sessionUuid = sessionUuid,
                    durationSec = a?.durationSec ?: 0L,
                    content = a?.content.orEmpty(),
                    targetConfigJson = target
                )
            }
            ExerciseRecordType.OTHER -> {
                val a = parsed as? WorkoutItem.Other
                WorkoutItem.Other(
                    itemId = draft.key,
                    exerciseUuid = draft.exerciseUuid,
                    name = draft.name,
                    order = order,
                    note = draft.note,
                    sessionUuid = sessionUuid,
                    durationSec = a?.durationSec ?: 0L,
                    reps = a?.reps,
                    targetConfigJson = target
                )
            }
        }
    }

    /** 统一项目 → workout_exercise 行。仅 STRENGTH 写 setsJson；其余写 itemMetaJson。 */
    fun itemToWorkoutExercise(item: WorkoutItem, sessionUuid: String, order: Int): WorkoutExercise =
        WorkoutExercise(
            sessionUuid = sessionUuid,
            exerciseUuid = item.exerciseUuid,
            exerciseNameSnapshot = item.name,
            orderInSession = if (item.order >= 0) item.order else order,
            setsJson = if (item is WorkoutItem.Strength) TrainingMath.encodeSets(item.sets) else "",
            note = item.note,
            itemMetaJson = WorkoutItem.toJson(item.copyOrder(order).copySession(sessionUuid))
        )

    /** 只有 CARDIO 进入有氧明细表；rawInputJson 保存完整 WorkoutItem JSON（含目标与实际）。 */
    fun itemToCardio(item: WorkoutItem.Cardio, sessionUuid: String, order: Int): WorkoutCardioDetail =
        WorkoutCardioDetail(
            sessionUuid = sessionUuid,
            exerciseUuid = item.exerciseUuid,
            exerciseNameSnapshot = item.name,
            orderInSession = if (item.order >= 0) item.order else order,
            durationSeconds = item.durationSec.coerceAtLeast(0L),
            distanceMeters = item.distanceMeters?.takeIf { it.isFinite() && it > 0.0 },
            calculatedKcal = item.calculatedKcal.takeIf { it.isFinite() && it > 0.0 } ?: 0.0,
            calculationMethod = item.calculationMethod,
            calculationVersion = item.calculationVersion,
            weightSnapshot = item.weightSnapshot,
            rawInputJson = WorkoutItem.toJson(item.copyOrder(order).copySession(sessionUuid)),
            createdAt = System.currentTimeMillis()
        )

    /** workout_exercise 行 → 统一项目；旧行（itemMetaJson 空）按力量兼容。 */
    fun workoutExerciseToItem(row: WorkoutExercise): WorkoutItem {
        val parsed = WorkoutItem.fromJson(row.itemMetaJson)
        if (parsed != null && parsed.recordType != ExerciseRecordType.STRENGTH) return parsed
        return WorkoutItem.Strength(
            itemId = parsed?.itemId ?: "legacy-${row.id}",
            exerciseUuid = row.exerciseUuid,
            name = row.exerciseNameSnapshot,
            order = row.orderInSession,
            note = row.note,
            sessionUuid = row.sessionUuid,
            sets = TrainingMath.decodeSets(row.setsJson),
            targetConfigJson = parsed?.targetConfigJson.orEmpty()
        )
    }

    /** workout_cardio_detail 行 → 统一项目；列优先，rawInputJson 仅补目标与低频字段。 */
    fun cardioToItem(row: WorkoutCardioDetail): WorkoutItem {
        val parsed = WorkoutItem.fromJson(row.rawInputJson) as? WorkoutItem.Cardio
        return WorkoutItem.Cardio(
            itemId = parsed?.itemId ?: "cardio-${row.id}",
            exerciseUuid = row.exerciseUuid,
            name = row.exerciseNameSnapshot,
            order = row.orderInSession,
            mode = parsed?.mode ?: WorkoutMode.NORMAL,
            note = parsed?.note.orEmpty(),
            sessionUuid = row.sessionUuid,
            durationSec = row.durationSeconds,
            distanceMeters = row.distanceMeters,
            intensity = parsed?.intensity,
            calculatedKcal = row.calculatedKcal,
            calculationMethod = row.calculationMethod,
            calculationVersion = row.calculationVersion,
            weightSnapshot = row.weightSnapshot,
            targetConfigJson = parsed?.targetConfigJson.orEmpty()
        )
    }

    /** 有氧热量：只使用参数库里真实存在的类型 MET；无匹配 MET 时不造数。 */
    fun withCardioKcal(
        item: WorkoutItem.Cardio,
        entity: ExerciseEntity?,
        metDefaults: Map<String, Triple<Double, Double, Double>>,
        weightKg: Double
    ): WorkoutItem.Cardio {
        val activityType = entity?.slug?.takeIf { it.isNotBlank() && metDefaults.containsKey(it) }
            ?: return item.copy(calculatedKcal = 0.0, calculationMethod = "", calculationVersion = "", weightSnapshot = null)
        if (item.durationSec <= 0L || weightKg <= 0.0) {
            return item.copy(calculatedKcal = 0.0, calculationMethod = "", calculationVersion = "", weightSnapshot = null)
        }
        val met = metDefaults.getValue(activityType).second
        val estimate = ExerciseCalculator.estimate(
            met = met,
            weightKg = weightKg,
            mode = ExerciseCalculator.MODE_MINUTES,
            amount = item.durationSec / 60.0,
            intensity = item.intensity
        )
        return item.copy(
            calculatedKcal = estimate.kcal,
            calculationMethod = WorkoutCalorieCalculator.METHOD_MET_TYPE_DEFAULT,
            calculationVersion = WorkoutCalorieCalculator.VERSION,
            weightSnapshot = weightKg
        )
    }

    /** 运动日记分类：按会话实际构成判定，不再固定“力量”。 */
    fun diaryType(items: List<WorkoutItem>): String {
        if (items.isEmpty()) return "力量"
        val hasStrength = items.any { it is WorkoutItem.Strength }
        val hasNonStrength = items.any { it !is WorkoutItem.Strength }
        if (hasStrength && hasNonStrength) return "混合"
        if (hasStrength) return "力量"
        if (items.all { it is WorkoutItem.Cardio }) return "有氧"
        if (items.all { it is WorkoutItem.Flexibility }) return "柔韧"
        return "其他"
    }

    private fun WorkoutItem.copyOrder(order: Int): WorkoutItem = when (this) {
        is WorkoutItem.Strength -> copy(order = order)
        is WorkoutItem.Cardio -> copy(order = order)
        is WorkoutItem.Hiit -> copy(order = order)
        is WorkoutItem.Flexibility -> copy(order = order)
        is WorkoutItem.Other -> copy(order = order)
    }

    private fun WorkoutItem.copySession(sessionUuid: String): WorkoutItem = when (this) {
        is WorkoutItem.Strength -> copy(sessionUuid = sessionUuid)
        is WorkoutItem.Cardio -> copy(sessionUuid = sessionUuid)
        is WorkoutItem.Hiit -> copy(sessionUuid = sessionUuid)
        is WorkoutItem.Flexibility -> copy(sessionUuid = sessionUuid)
        is WorkoutItem.Other -> copy(sessionUuid = sessionUuid)
    }
}
