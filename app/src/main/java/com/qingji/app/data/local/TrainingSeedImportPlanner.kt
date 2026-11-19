package com.qingji.app.data.local

import com.qingji.app.data.local.entity.ExerciseEntity

/**
 * 动作库导入计划器（纯 Kotlin，无 Android 依赖，便于单测）。
 *
 * 数据来源优先级（明确规则，不许隐式覆盖）：
 *   · 用户字段：uuid / isBuiltIn / isFavorite / createdAt / updatedAt / steps / commonMistakes
 *     沿用数据库里已有的值；但 steps / commonMistakes 老值为空时允许种子回填（v2 撰写了训练指导），
 *   · 动作库字段：name / englishName / slug / category / 肌群 / 器械 / exerciseType /
 *     isStretch / assetPath / attribution，以 Workout Guide 清单为准。
 *   · uuid 命中但 isBuiltIn=false（用户自建）→ 整条跳过，绝不覆盖。
 *   · 名字撞上别的动作（尤其用户自建）→ 内置动作改用「（动作库N）」变式名后插入/更新，
 *     保证 UNIQUE 索引不冲突，也保住用户原名。
 */
object TrainingSeedImportPlanner {

    data class Plan(
        val toInsert: List<ExerciseEntity>,
        val toUpdate: List<ExerciseEntity>
    )

    /** 版本一致且库完整时才跳过；版本缺失/不一致一律进导入流程做差集。 */
    fun importNeeded(installedVersion: String?, count: Int, seedCount: Int): Boolean =
        installedVersion != TrainingSeed.LIBRARY_VERSION || count < seedCount

    fun plan(
        existing: List<ExerciseEntity>,
        seeds: List<ExerciseEntity>,
        now: Long = System.currentTimeMillis()
    ): Plan {
        val byUuid = existing.associateBy { it.uuid }
        val usedNames = existing.map { it.name }.toMutableSet()
        val inserts = mutableListOf<ExerciseEntity>()
        val updates = mutableListOf<ExerciseEntity>()

        fun uniqueName(wanted: String, ownerUuid: String): String {
            val takenByOther = usedNames.contains(wanted) && byUuid[ownerUuid]?.name != wanted
            if (!takenByOther) {
                usedNames.add(wanted)
                return wanted
            }
            var n = 2
            while (true) {
                val candidate = wanted + "（动作库" + n + "）"
                if (!usedNames.contains(candidate)) {
                    usedNames.add(candidate)
                    return candidate
                }
                n++
            }
        }

        for (seed in seeds) {
            val old = byUuid[seed.uuid]
            if (old == null) {
                inserts.add(seed.copy(name = uniqueName(seed.name, seed.uuid), createdAt = now, updatedAt = now))
                continue
            }
            // 用户自建动作即使 uuid 撞上，也整条跳过
            if (!old.isBuiltIn) continue
            updates.add(
                seed.copy(
                    name = uniqueName(seed.name, old.uuid),
                    isBuiltIn = true,
                    isFavorite = old.isFavorite,
                    steps = old.steps.ifBlank { seed.steps },
                    commonMistakes = old.commonMistakes.ifBlank { seed.commonMistakes },
                    createdAt = old.createdAt,
                    updatedAt = old.updatedAt
                )
            )
        }
        return Plan(inserts, updates)
    }
}
