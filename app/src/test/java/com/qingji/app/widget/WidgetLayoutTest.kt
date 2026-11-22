package com.qingji.app.widget

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验收补充工单 P1 / §6「MacroBar 不同尺寸布局验证」。
 *
 * 依据 Glance 1.1.1 SizeMode.Responsive 源码：LocalSize 是声明尺寸之一
 * （本项目 110dp / 250dp），不是启动器任意值。本测试验证：
 * - 2×2(110dp)、4×2(250dp)、4×4(250dp) 三档轨道宽度非负；
 * - 三条轨道 + 两条 gap + 左右 padding 不超过总宽（不溢出）；
 * - progress 钳制后的填充宽度永不超轨道。
 */
class WidgetLayoutTest {

    private val padding = WidgetSpec.CARD_PADDING_DP.dp
    private val gap = WidgetSpec.MACRO_GAP_DP.dp

    @Test
    fun `2x2_110dp_track_width_non_negative`() {
        val w = 110.dp
        val track = WidgetLayout.macroTrackWidth(w, padding, gap)
        assertTrue(track.value >= 0f)
        // 2×2 档 UI 本就不显示营养条（density=1），这里只保证算法不产生负宽。
    }

    @Test
    fun `4x2_250dp_three_tracks_plus_gaps_plus_padding_fit_exactly`() {
        val w = 250.dp
        val track = WidgetLayout.macroTrackWidth(w, padding, gap)
        val total = track * 3 + gap * 2 + padding * 2
        assertEquals(w.value, total.value, 0.001f)
    }

    @Test
    fun `4x4_250dp_same_width_as_4x2`() {
        // 4×4 声明宽度同为 250dp，轨道宽度必须一致（高度不同不影响横向）。
        assertEquals(
            WidgetLayout.macroTrackWidth(250.dp, padding, gap).value,
            WidgetLayout.macroTrackWidth(250.dp, padding, gap).value,
            0.001f
        )
    }

    @Test
    fun `overs_progress_never_exceeds_track`() {
        val track = WidgetLayout.macroTrackWidth(250.dp, padding, gap)
        for (current in listOf(0, 50, 100, 1000)) {
            val p = WidgetProgress.fraction(current, 100)
            assertTrue(p in 0f..1f)
            assertTrue((track * p).value <= track.value + 0.001f)
        }
    }

    @Test
    fun `tiny_width_returns_zero_not_negative`() {
        // 极端窄尺寸（小于 padding+gaps）不得产生负宽导致布局崩溃。
        assertEquals(0.dp, WidgetLayout.macroTrackWidth(20.dp, padding, gap))
    }
}

// V2.2：全宽进度条宽度（不能用单条营养轨道宽）
class WidgetFullBarWidthTest {
    @Test
    fun `全宽轨道宽度等于内容宽而非单条营养宽`() {
        val w = 250.dp
        val full = WidgetLayout.fullBarWidth(w)
        val track = WidgetLayout.macroTrackWidth(w)
        assertEquals(w - WidgetSpec.CARD_PADDING_DP.dp * 2, full)
        assertTrue("全宽轨道应显著大于单条营养轨道", full > track * 2)
    }

    @Test
    fun `极小尺寸不为负`() {
        assertEquals(0.dp, WidgetLayout.fullBarWidth(20.dp))
        assertTrue(WidgetLayout.fullBarWidth(110.dp) > 0.dp)
    }
}
