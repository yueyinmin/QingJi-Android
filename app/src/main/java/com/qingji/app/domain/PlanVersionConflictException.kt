package com.qingji.app.domain

/** 训练计划 CAS 失败：确认前已被人工或其他提案改过。提案侧据此标记 STALE。 */
class PlanVersionConflictException(
    message: String = "训练计划已变化，请刷新后重试"
) : IllegalStateException(message)
