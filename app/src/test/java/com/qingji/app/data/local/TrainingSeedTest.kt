package com.qingji.app.data.local

import com.qingji.app.data.local.entity.ExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 300 动作库工单 · 第三阶段回归：增量导入的差集计算与稳定 UUID 规则。
 * 只测纯 Kotlin 逻辑，不依赖 Android Context / Room，跑得快也稳定。
 */
class TrainingSeedTest {

    private fun custom(name: String) = ExerciseEntity(
        uuid = "user-1",
        name = name,
        category = TrainingSeed.CORE,
        isBuiltIn = false
    )

    @Test
    fun `现有 38 个内置动作 UUID 数量与格式都不变`() {
        assertEquals(38, TrainingSeed.BUILT_IN_UUIDS.size)
        assertTrue(TrainingSeed.BUILT_IN_UUIDS.all { it.startsWith("ex-") })
        assertTrue(TrainingSeed.BUILT_IN_UUIDS.none { it.startsWith("ex2-") })
    }

    @Test
    fun `内置动作名称全局唯一`() {
        val names = TrainingSeed.builtIns().map { it.name.trim() }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `七个一级分类键与中文显示名一一对应`() {
        assertEquals(
            listOf("chest", "back", "shoulder", "arm", "leg", "core", "stretch"),
            TrainingSeed.CATEGORY_LABELS.map { it.first }
        )
        assertEquals(
            listOf("胸部", "背部", "肩部", "手臂", "腿部", "核心", "拉伸"),
            TrainingSeed.CATEGORY_LABELS.map { it.second }
        )
        val keys = TrainingSeed.CATEGORY_LABELS.map { it.first }.toSet()
        assertTrue(TrainingSeed.builtIns().all { it.category in keys })
    }

    @Test
    fun `空库时补齐全部 38 条`() {
        assertEquals(38, TrainingSeed.missingBuiltIns(emptyList(), 1_000L).size)
    }

    @Test
    fun `已经全部存在时不补任何行 不覆盖收藏状态`() {
        val existing = TrainingSeed.builtIns(1_000L).map {
            if (it.uuid == "ex-chest-01") it.copy(isFavorite = true) else it
        }
        val missing = TrainingSeed.missingBuiltIns(existing, 2_000L)
        assertTrue(missing.isEmpty())
        // 已存在的那行根本不会出现在待写列表里，收藏不会被重置
        assertTrue(existing.first { it.uuid == "ex-chest-01" }.isFavorite)
    }

    @Test
    fun `老用户只补齐缺的那些 不重复插入`() {
        // 模拟旧版装了前 10 个，且其中一个被收藏
        val old = TrainingSeed.builtIns(1_000L).take(10).map {
            if (it.uuid == "ex-chest-01") it.copy(isFavorite = true) else it
        }
        val missing = TrainingSeed.missingBuiltIns(old, 2_000L)
        assertEquals(28, missing.size)
        assertTrue(missing.none { it.uuid in old.map { o -> o.uuid } })
        assertTrue(missing.all { it.isBuiltIn })
    }

    @Test
    fun `用户自建同名动作不被覆盖 且该内置动作被跳过`() {
        val target = TrainingSeed.builtIns().first { it.uuid == "ex-chest-01" }
        val mine = custom(target.name)
        val existing = TrainingSeed.builtIns(1_000L)
            .filter { it.uuid != target.uuid } + mine
        val missing = TrainingSeed.missingBuiltIns(existing, 2_000L)
        assertTrue(missing.isEmpty())
        assertFalse(missing.any { it.name == target.name })
    }

    @Test
    fun `自建动作与历史无关的行完全不参与差集`() {
        val existing = listOf(custom("我自己的动作"), custom("另一个动作"))
        val missing = TrainingSeed.missingBuiltIns(existing, 1_000L)
        assertEquals(38, missing.size)   // 自建动作不算内置，38 个都要补
        assertTrue(missing.none { it.uuid.startsWith("user-") })
    }

    @Test
    fun `名称带空白时归一化后仍判重`() {
        val target = TrainingSeed.builtIns().first { it.uuid == "ex-back-01" }
        val mine = custom("  ${target.name}  ")
        val existing = TrainingSeed.builtIns(1_000L)
            .filter { it.uuid != target.uuid } + mine
        assertTrue(TrainingSeed.missingBuiltIns(existing, 2_000L).isEmpty())
    }

    @Test
    fun `稳定 UUID 同名恒定 大小写与空白归一`() {
        val a = TrainingSeed.stableUuid("Dumbbell Bench Press")
        val b = TrainingSeed.stableUuid("  dumbbell   bench press  ")
        assertEquals(a, b)
        assertTrue(a.startsWith("ex2-"))
        assertEquals(16, a.length)  // "ex2-" + 12 位十六进制
    }

    @Test
    fun `不同英文名生成不同 UUID 且不撞现有 38 个`() {
        val a = TrainingSeed.stableUuid("Dumbbell Bench Press")
        val b = TrainingSeed.stableUuid("Barbell Bench Press")
        assertTrue(a != b)
        val all = (1..300).map { TrainingSeed.stableUuid("Exercise $it") }
        assertEquals(all.size, all.toSet().size)
        assertTrue(all.none { it in TrainingSeed.BUILT_IN_UUIDS })
    }
}
