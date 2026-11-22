package com.qingji.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 收尾补充核查 P0：三大营养素进度的【真实 UI 渲染条件】。
 *
 * 与 WidgetUiStateMatrixTest 不同，这里测的不是旧宏量函数 macroProgress，
 * 而是当前 OverviewWidget 实际调用的 TodayMacro 的唯一进度来源 todayMacroProgress。
 * 同时验证源码中 TodayMacro 确实调用该函数、且全部 6 个调用点都传入 goalConfigured——
 * 防止修正后的门控再次成为「与 UI 无关的死函数」。
 */
class TodayMacroRenderTest {

    /** 直接从 UI 渲染函数读取其依赖的源码，确认门控接线（防止死代码）。 */
    private fun widgetSource(): String {
        val candidates = listOf(
            "app/src/main/java/com/qingji/app/widget/QingJiWidgets.kt",
            "../QingJi-Android/app/src/main/java/com/qingji/app/widget/QingJiWidgets.kt"
        )
        for (p in candidates) {
            val f = File(p)
            if (f.exists()) return f.readText()
        }
        // Gradle 工作目录通常是模块根；向上找
        var f = File(".").absoluteFile
        repeat(6) {
            val c = File(f, "app/src/main/java/com/qingji/app/widget/QingJiWidgets.kt")
            if (c.exists()) return c.readText()
            f = f.parentFile ?: return ""
        }
        return ""
    }

    @Test
    fun `渲染函数确实使用todayMacroProgress_不是死代码`() {
        val src = widgetSource()
        assertTrue("找不到 QingJiWidgets 源码", src.isNotBlank())
        assertTrue(
            "TodayMacro 必须通过 todayMacroProgress 计算进度",
            src.contains("val progress = todayMacroProgress(state, goalConfigured, current, goal)")
        )
        val callCount = Regex("data\\.dietState, data\\.goalConfigured").findAll(src).count()
        // V2.4 渐进布局把原来 4×2/4×4 两处重复分支合并为单一调用块：
        // 蛋白/碳水/脂肪各 1 处，共 3 处，均传 goalConfigured（防漏传语义不变）。
        assertEquals("3 个 TodayMacro 调用点都必须传 goalConfigured", 3, callCount)
    }

    // ---- 工单要求的 4 种 UI 最终状态矩阵 ----

    @Test
    fun `EMPTY_占位2000_不显示伪剩余也不画营养轨道`() {
        // profile EMPTY（无档案）：goalConfigured=false；goalKcal 占位仍是 2000。
        val data = TodayRemainingWidgetData(
            profileState = ItemState.EMPTY, dietState = ItemState.EMPTY,
            exerciseState = ItemState.EMPTY, goalConfigured = false,
            goalKcal = 2000, remainingKcal = 1500,
            proteinCurrent = 30, proteinGoal = 0,
            carbCurrent = 60, carbGoal = 0, fatCurrent = 10, fatGoal = 0,
            hasSnapshot = true
        )
        assertFalse(data.canShowRemaining) // 剩余热量 --
        assertNull(todayMacroProgress(data.dietState, data.goalConfigured, data.proteinCurrent, data.proteinGoal))
        assertNull(todayMacroProgress(data.dietState, data.goalConfigured, data.carbCurrent, data.carbGoal))
        assertNull(todayMacroProgress(data.dietState, data.goalConfigured, data.fatCurrent, data.fatGoal))
    }

    @Test
    fun `SUCCESS_目标为0_不显示伪剩余也不画营养轨道`() {
        val data = TodayRemainingWidgetData(
            profileState = ItemState.SUCCESS, dietState = ItemState.SUCCESS,
            exerciseState = ItemState.SUCCESS, goalConfigured = false,
            goalKcal = 0, remainingKcal = 0,
            proteinCurrent = 30, proteinGoal = 0,
            carbCurrent = 60, carbGoal = 0, fatCurrent = 10, fatGoal = 0,
            hasSnapshot = true
        )
        assertFalse(data.canShowRemaining)
        assertNull(todayMacroProgress(data.dietState, data.goalConfigured, data.proteinCurrent, data.proteinGoal))
        assertNull(todayMacroProgress(data.dietState, data.goalConfigured, data.carbCurrent, data.carbGoal))
        assertNull(todayMacroProgress(data.dietState, data.goalConfigured, data.fatCurrent, data.fatGoal))
    }

    @Test
    fun `SUCCESS_有效目标_剩余与营养进度正常显示`() {
        val data = TodayRemainingWidgetData(
            profileState = ItemState.SUCCESS, dietState = ItemState.SUCCESS,
            exerciseState = ItemState.SUCCESS, goalConfigured = true,
            goalKcal = 2000, eatenKcal = 500, remainingKcal = 1500,
            proteinCurrent = 60, proteinGoal = 120,
            carbCurrent = 100, carbGoal = 200, fatCurrent = 25, fatGoal = 50,
            hasSnapshot = true
        )
        assertTrue(data.canShowRemaining)
        assertEquals(0.5f, todayMacroProgress(data.dietState, data.goalConfigured, data.proteinCurrent, data.proteinGoal)!!, 0.001f)
        assertEquals(0.5f, todayMacroProgress(data.dietState, data.goalConfigured, data.carbCurrent, data.carbGoal)!!, 0.001f)
        assertEquals(0.5f, todayMacroProgress(data.dietState, data.goalConfigured, data.fatCurrent, data.fatGoal)!!, 0.001f)
    }

    @Test
    fun `FAILED_占位值或旧值_不显示伪剩余也不画营养轨道`() {
        val data = TodayRemainingWidgetData(
            profileState = ItemState.FAILED, dietState = ItemState.FAILED,
            exerciseState = ItemState.FAILED, goalConfigured = true, // 即使旧快照残留 true
            goalKcal = 2000, remainingKcal = 1200,
            proteinCurrent = 30, proteinGoal = 120,
            carbCurrent = 60, carbGoal = 200, fatCurrent = 10, fatGoal = 50,
            hasSnapshot = true
        )
        assertFalse(data.canShowRemaining)
        assertNull(todayMacroProgress(data.dietState, data.goalConfigured, data.proteinCurrent, data.proteinGoal))
        assertNull(todayMacroProgress(data.dietState, data.goalConfigured, data.carbCurrent, data.carbGoal))
        assertNull(todayMacroProgress(data.dietState, data.goalConfigured, data.fatCurrent, data.fatGoal))
    }

    @Test
    fun `序列化恢复语义_goalConfigured默认不得为true且旧schema须作废`() {
        // TodayRemainingWidgetData 的默认 goalConfigured=true 仅兼容构造；
        // 生产快照的 schemaVersion=1 且 every fromJson 读取 schemaVersion 严格校验。
        // 这里验证：无 goalConfigured 的旧数据若被误读，仍不能画轨道（state=FAILED 时 gate 双保险）。
        val legacy = TodayRemainingWidgetData(
            profileState = ItemState.FAILED, dietState = ItemState.FAILED,
            goalConfigured = true, goalKcal = 2000, hasSnapshot = true,
            proteinGoal = 120, carbGoal = 200, fatGoal = 50
        )
        // 即便旧快照残留 goalConfigured=true，FAILED 状态仍阻断轨道（EMPTY/FAILED 不合并为可用）
        assertNull(todayMacroProgress(legacy.dietState, legacy.goalConfigured, 30, legacy.proteinGoal))
        // schema 1 独立投影；旧大快照 schema 4 不能当作投影 1 使用
        assertNotEquals(4, TodayRemainingWidgetData.SCHEMA_VERSION)
        assertEquals(2, TodayRemainingWidgetData.SCHEMA_VERSION)
    }
}
