package com.qingji.app.data.training.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2 工单 A 组验收：动作插画映射的静态正确性。
 * 只测纯 Kotlin 映射对象，不依赖 Android Context / assets 文件系统。
 * 图片能否真正渲染由模拟器截图验收。
 */
class ExerciseMediaMappingTest {

    @Test
    fun `38 个内置动作全部有映射`() {
        assertEquals(38, ExerciseMediaMapping.mappings.size)
    }

    @Test
    fun `键一律是动作 UUID 不吃中文动作名`() {
        ExerciseMediaMapping.mappings.keys.forEach { uuid ->
            assertTrue(uuid.startsWith("ex-"))
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
    fun `正好 6 个近似匹配且都是工单点名的 UUID`() {
        val review = ExerciseMediaMapping.requiresReviewUuids
        assertEquals(6, review.size)
        assertEquals(
            setOf(
                "ex-shoulder-01", "ex-arm-01", "ex-arm-02",
                "ex-arm-04", "ex-leg-02", "ex-stretch-02"
            ),
            review
        )
    }

    @Test
    fun `32 个直接匹配不带审核标记`() {
        assertEquals(
            32,
            ExerciseMediaMapping.mappings.values.count { !it.requiresReview }
        )
    }

    @Test
    fun `自建动作没有映射 界面走默认占位`() {
        assertNull(ExerciseMediaMapping.forUuid("user-made-123"))
        assertNull(ExerciseMediaMapping.forUuid(""))
    }

    @Test
    fun `资产路径指向本地 android_asset 不联网`() {
        val path = ExerciseMediaMapping.assetPath("bench-press")
        assertEquals("file:///android_asset/workout-guide/bench-press.svg", path)
        assertFalse(path.startsWith("http"))
    }
}
