package com.qingji.app.domain

/**
 * ============================================================
 * 计划里的动作解析 —— 纯函数，不碰数据库，方便单测
 * ------------------------------------------------------------
 * 工单 4.5 的 🟡 偏差：从历史训练复制成计划时按「名字」找动作，
 * 动作库一改名就断链。这里改成 UUID 优先、名字兜底：
 *
 * 1. 历史明细里存了 exerciseUuid → 先按 UUID 找，改名也认得；
 * 2. 老数据 UUID 是空串（历史遗留）→ 退回名字快照匹配；
 * 3. UUID 失效（自建动作被删）但名字还在 → 也算找到，用名字那条；
 * 4. 两者都找不到 → 返回 missingName，UI 做兼容提示，计划本身照存。
 *
 * 名字快照永远原样保留，历史记录显示不受影响。
 * ============================================================
 */
object PlanExerciseResolver {

    data class Ref(
        val exerciseUuid: String,
        val nameSnapshot: String
    )

    data class Resolved(
        val exerciseUuid: String,
        val missingName: String?
    ) {
        val isMissing: Boolean get() = missingName != null
    }

    /**
     * @param ref 历史动作引用（UUID + 当时的名字快照）
     * @param uuids 当前动作库里全部有效 UUID
     * @param names 当前动作库里「名字 → UUID」的映射
     */
    fun resolve(
        ref: Ref,
        uuids: Set<String>,
        names: Map<String, String>
    ): Resolved {
        if (ref.exerciseUuid.isNotBlank() && ref.exerciseUuid in uuids) {
            return Resolved(ref.exerciseUuid, null)
        }
        val byName = if (ref.nameSnapshot.isNotBlank()) names[ref.nameSnapshot] else null
        if (!byName.isNullOrBlank()) return Resolved(byName, null)
        return Resolved("", ref.nameSnapshot.takeIf { it.isNotBlank() })
    }
}
