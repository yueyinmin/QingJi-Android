package com.qingji.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P1-04：补算快速路径按 sourceId 集合判断，而非仅比数量。 */
class LedgerBackfillDecisionTest {

    @Test
    fun `两边都空不需补算`() {
        assertFalse(LedgerBackfillDecision.needBackfill(emptyList(), emptyList()))
    }

    @Test
    fun `集合相同不需补算`() {
        assertFalse(LedgerBackfillDecision.needBackfill(
            listOf("a", "b", "c"), listOf("c", "a", "b") // 顺序不同也不需补
        ))
    }

    @Test
    fun `数量相同但sourceId不同需要补算`() {
        // 这正是旧「只比数量」快速路径漏掉的场景：删 a 加 d
        assertTrue(LedgerBackfillDecision.needBackfill(
            listOf("b", "c", "d"), listOf("a", "b", "c")
        ))
    }

    @Test
    fun `数量不同需要补算`() {
        assertTrue(LedgerBackfillDecision.needBackfill(listOf("a"), emptyList()))
        assertTrue(LedgerBackfillDecision.needBackfill(emptyList(), listOf("a")))
    }
}
