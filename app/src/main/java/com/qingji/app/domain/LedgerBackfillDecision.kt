package com.qingji.app.domain

/**
 * 账本补算是否需要执行（QJ-GOV-2026-01 / P1-04）。
 *
 * 旧快速路径只比「训练场次数 == 账本强度行数」，数量相同但 sourceId 不同
 * （删一场 + 加一场）时会漏补。改为按 sourceId 集合比较，纯函数可单测。
 */
object LedgerBackfillDecision {
    fun needBackfill(sessionIds: Collection<String>, strengthLedgerSourceIds: Collection<String>): Boolean =
        sessionIds.toSet() != strengthLedgerSourceIds.toSet()
}
