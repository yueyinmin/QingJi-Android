package com.qingji.app.domain.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QJ-RECORD-INPUT-UNIFIED-V3 / B1：统一数字状态机纯逻辑单测。
 * 这些用例全部不依赖 Compose，JVM 直接跑。
 */
class NumericInputStateTest {

    @Test
    fun firstDigitReplacesWholeValue() {
        val editing = NumericInputState.of(100.0, 1.0, 3000.0).beginEdit()
        assertTrue("进入编辑必须请求全选", editing.pendingSelectAll)
        assertEquals("1", editing.onDigit('1').text)
        assertFalse(editing.onDigit('1').pendingSelectAll)
    }

    @Test
    fun pressingZeroOnSelectedValueGivesZero() {
        val editing = NumericInputState.of(100.0, 1.0, 3000.0).beginEdit()
        assertEquals("0", editing.onDigit('0').text)
    }

    @Test
    fun backspaceOnSelectedValueClearsInsteadOfDeletingOneChar() {
        val editing = NumericInputState.of(200.0, 1.0, 3000.0).beginEdit()
        assertEquals("", editing.onBackspace().text)
    }

    @Test
    fun blankValueStaysBlankWhileEditing() {
        val editing = NumericInputState.of(200.0, 1.0, 3000.0).beginEdit().onBackspace()
        assertEquals("", editing.text)
        assertNull("编辑中空串不能被当成 0", editing.value)
        assertFalse(editing.canSubmit)
    }

    @Test
    fun blankValueRestoresOriginalWhenPolicyIsRestore() {
        val state = NumericInputState.of(
            value = 200.0, min = 1.0, max = 3000.0,
            emptyPolicy = NumericEmptyPolicy.RESTORE_ORIGINAL
        ).beginEdit().onBackspace().endEdit()

        assertEquals("200", state.text)
        assertEquals(200.0, state.committedValue, 0.001)
        assertFalse(state.isEditing)
    }

    @Test
    fun blankValueNormalizesToFallbackWhenPolicyIsNormalize() {
        val state = NumericInputState.of(
            value = 30.0, min = 0.0, max = 600.0,
            emptyPolicy = NumericEmptyPolicy.NORMALIZE,
            emptyFallback = 0.0
        ).beginEdit().onBackspace().endEdit()

        assertEquals("0", state.text)
        assertEquals(0.0, state.committedValue, 0.001)
    }

    @Test
    fun cancelAlwaysRestoresValueBeforeEditing() {
        val state = NumericInputState.of(180.0, 1.0, 3000.0)
        val changed = state.beginEdit().onTextChange("999")
        assertEquals("999", changed.text)
        assertEquals("180", changed.cancelEdit().text)
        assertEquals(180.0, changed.cancelEdit().committedValue, 0.001)
    }

    @Test
    fun sanitizeDropsJunkAndKeepsIntermediateDecimal() {
        assertEquals("30", NumericInputState.sanitize("030", 0))
        assertEquals("0.5", NumericInputState.sanitize("0.5", 1))
        assertEquals("12.3", NumericInputState.sanitize("12.3.4", 1))
        assertEquals("1.5", NumericInputState.sanitize("1。5", 1))
        assertEquals("12.34", NumericInputState.sanitize("12.345", 2))
        assertEquals("3000000", NumericInputState.sanitize("30000000", 0))
    }

    @Test
    fun applyDeltaUsesCurrentTextAndStaysInRange() {
        val state = NumericInputState.of(200.0, 1.0, 3000.0)
        assertEquals(250.0, state.applyDelta(50.0).committedValue, 0.001)
        assertEquals(3000.0, state.applyDelta(99999.0).committedValue, 0.001)
        assertEquals(1.0, state.applyDelta(-99999.0).committedValue, 0.001)
    }

    @Test
    fun commitRejectsOutOfRangeValue() {
        val state = NumericInputState.of(200.0, 1.0, 3000.0).onTextChange("3001")
        assertNull(state.commit())
    }

    @Test
    fun resetToSynchronizesOriginalAndCommitted() {
        val state = NumericInputState.of(100.0, 1.0, 3000.0).resetTo(350.0)
        assertEquals("350", state.text)
        assertEquals(350.0, state.originalValue, 0.001)
        assertEquals(350.0, state.committedValue, 0.001)
    }
}
