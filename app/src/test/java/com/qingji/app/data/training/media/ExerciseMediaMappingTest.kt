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
 */
class ExerciseMediaMappingTest {

    @Test
    fun `117 个动作有候选图 83 个无图`() {
        assertEquals(117, ExerciseMediaMapping.mappings.size)
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
    fun `每个 slug 唯一且不含中文或空格`() {
        val slugs = ExerciseMediaMapping.mappings.values.map { it.slug }
        assertEquals(slugs.size, slugs.toSet().size)
        slugs.forEach { slug ->
            assertTrue(slug.isNotBlank())
            assertTrue(slug.none { it == ' ' || it.code > 0x7F })
        }
    }

    @Test
    fun `6 条别名候选标待核对 111 条精确匹配不标`() {
        val review = ExerciseMediaMapping.requiresReviewUuids
        assertEquals(6, review.size)
        assertEquals(
            setOf(
                "ex-back-03", "ex-back-05", "ex-back-06",
                "ex-shoulder-05", "candidate-shoulder-024", "ex-arm-02"
            ),
            review
        )
        assertEquals(
            111,
            ExerciseMediaMapping.mappings.values.count { !it.requiresReview }
        )
    }

    @Test
    fun `所有候选图状态都是待人工核图 不虚报已审核`() {
        ExerciseMediaMapping.mappings.values.forEach {
            assertEquals("candidate_pending_visual_review", it.reviewStatus)
        }
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
    fun `播放序列 1-2-3-2 每帧420ms 循环`() {
        assertTrue(ExerciseMediaMapping.PLAY_SEQUENCE.contentEquals(intArrayOf(1, 2, 3, 2)))
        assertEquals(420L, ExerciseMediaMapping.FRAME_DURATION_MS)
        assertTrue(ExerciseMediaMapping.LOOP)
    }

    @Test
    fun `38 个老动作仍有候选图且映射不丢`() {
        val old = ExerciseMediaMapping.mappings.keys.filter { it.startsWith("ex-") }
        assertEquals(38, old.size)
        assertNotNull(ExerciseMediaMapping.forUuid("ex-chest-01"))
    }
}
