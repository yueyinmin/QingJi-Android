package com.qingji.app.data.training.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 200 条动作库接入：动作插画映射的静态正确性。
 * 只测纯 Kotlin 映射对象，不依赖 Android Context / assets 文件系统。
 * 图片能否真正渲染由模拟器截图验收。
 *
 * 数字口径（2026-11-16）：
 *   · 三帧候选素材 123 条（其中老动作 34 条 + 200 条库里的新动作 89 条）；
 *   · 老动作里另有 4 条没有三帧素材，沿用旧版单帧 svg（legacy）；
 *   · 合计 127 条有图，38 个老动作一个不少。
 */
class ExerciseMediaMappingTest {

    @Test
    fun `共 127 条有图 其中三帧123 单帧4`() {
        val all = ExerciseMediaMapping.mappings.values
        assertEquals(127, all.size)
        assertEquals(4, all.count { it.legacySingleFrame })
        assertEquals(123, all.count { !it.legacySingleFrame })
        assertNull(ExerciseMediaMapping.forUuid("candidate-chest-008"))
        assertNull(ExerciseMediaMapping.forUuid("user-made-123"))
        assertNull(ExerciseMediaMapping.forUuid(""))
    }

    @Test
    fun `键一律是动作 UUID 不吃中文动作名`() {
        ExerciseMediaMapping.mappings.keys.forEach { uuid ->
            assertTrue(uuid.startsWith("ex-") || uuid.startsWith("candidate-"))
            assertFalse(uuid.any { it.code > 0x7F })
        }
    }

    @Test
    fun `slug 只用英文小写连字符 同一个 slug 最多被两条引用`() {
        val slugs = ExerciseMediaMapping.mappings.values.map { it.slug }
        slugs.forEach { slug ->
            assertTrue(slug.isNotBlank())
            assertTrue(slug.none { it == ' ' || it.code > 0x7F })
        }
        // 老动作与 200 条库里的同名动作会指向同一份素材，所以允许重复，
        // 但绝不允许 3 条以上共用（那说明映射写错了）。
        slugs.groupingBy { it }.eachCount().forEach { (slug, count) ->
            assertTrue("slug=$slug 被 $count 条引用", count <= 2)
        }
        assertEquals(119, slugs.toSet().size)
    }

    @Test
    fun `9 条近似候选标待核对 其余 118 条精确匹配不标`() {
        val review = ExerciseMediaMapping.requiresReviewUuids
        assertEquals(9, review.size)
        assertEquals(
            setOf(
                "ex-back-03", "ex-back-05", "ex-back-06",
                "ex-arm-01", "ex-arm-02", "ex-arm-04",
                "ex-leg-02", "ex-stretch-02", "candidate-shoulder-024"
            ),
            review
        )
        assertEquals(
            118,
            ExerciseMediaMapping.mappings.values.count { !it.requiresReview }
        )
    }

    @Test
    fun `候选图状态待核图 老单帧状态单独标记 不虚报已审核`() {
        ExerciseMediaMapping.mappings.values.forEach {
            if (it.legacySingleFrame) {
                assertEquals(ExerciseMediaMapping.REVIEW_LEGACY, it.reviewStatus)
            } else {
                assertEquals(ExerciseMediaMapping.REVIEW_CANDIDATE, it.reviewStatus)
            }
        }
        // 没有任何一条冒充「已审核」
        assertFalse(
            ExerciseMediaMapping.mappings.values.any { it.reviewStatus.contains("approved") }
        )
    }

    @Test
    fun `三帧路径指向本地 android_asset 不联网`() {
        val p1 = ExerciseMediaMapping.framePath("bench-press", 1)
        val p3 = ExerciseMediaMapping.framePath("bench-press", 3)
        assertEquals("file:///android_asset/exercises/bench-press/frame-1.svg", p1)
        assertEquals("file:///android_asset/exercises/bench-press/frame-3.svg", p3)
        assertFalse(p1.startsWith("http"))
    }

    @Test
    fun `单帧老动作走旧目录 三帧动作走 exercises 目录`() {
        val legacy = ExerciseMediaMapping.forUuid("ex-leg-06")
        assertNotNull(legacy)
        // 单帧素材忽略帧号，永远同一张
        assertEquals(
            "file:///android_asset/workout-guide/calf-raise.svg",
            legacy!!.frameUri(1)
        )
        assertEquals(legacy.frameUri(1), legacy.frameUri(3))

        val animated = ExerciseMediaMapping.forUuid("ex-chest-02")
        assertNotNull(animated)
        assertEquals(
            "file:///android_asset/exercises/bench-press/frame-2.svg",
            animated!!.frameUri(2)
        )
    }

    @Test
    fun `播放序列 1-2-3-2 每帧420ms 循环`() {
        assertTrue(ExerciseMediaMapping.PLAY_SEQUENCE.contentEquals(intArrayOf(1, 2, 3, 2)))
        assertEquals(420L, ExerciseMediaMapping.FRAME_DURATION_MS)
        assertTrue(ExerciseMediaMapping.LOOP)
    }

    @Test
    fun `38 个老动作一个不少 每条都有图`() {
        val old = ExerciseMediaMapping.mappings.filterKeys { it.startsWith("ex-") }
        assertEquals(38, old.size)
        old.forEach { (uuid, media) ->
            assertNotNull(uuid, ExerciseMediaMapping.forUuid(uuid))
            assertTrue(uuid, media.slug.isNotBlank())
        }
        assertEquals(4, old.values.count { it.legacySingleFrame })
        assertEquals(34, old.values.count { !it.legacySingleFrame })
    }
}
