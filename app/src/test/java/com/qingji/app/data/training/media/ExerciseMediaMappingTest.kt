package com.qingji.app.data.training.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Workout Guide 324 条本地素材映射的静态校验（302 条三帧 SVG + 22 条 AI 单帧 PNG）。 */
class ExerciseMediaMappingTest {

    @Test
    fun `324 条都有本地素材与授权`() {
        val all = ExerciseMediaMapping.mappings.values
        assertEquals(324, all.size)
        assertTrue(all.all { it.slug.isNotBlank() })
        assertTrue(all.all { it.assetPath.startsWith("exercises/") })
        assertTrue(all.all { it.assetPath.contains("/frame-1.svg") || it.assetPath.contains("/frame-1.png") })
        assertEquals(302, all.count { it.attribution.contains("CC BY-SA 4.0") })
        assertEquals(22, all.count { it.attribution.contains("AI-generated") })
    }

    @Test
    fun `键是动作 UUID 自建动作没有素材`() {
        ExerciseMediaMapping.mappings.keys.forEach { uuid ->
            assertTrue(uuid.startsWith("ex-") || uuid.startsWith("wg:"))
        }
        assertNull(ExerciseMediaMapping.forUuid("user-made-123"))
        assertNull(ExerciseMediaMapping.forUuid(""))
        assertNull(ExerciseMediaMapping.forUuid("candidate-chest-008"))
    }

    @Test
    fun `每条素材路径唯一且不含中文或空格`() {
        val paths = ExerciseMediaMapping.mappings.values.map { it.assetPath }
        assertEquals(324, paths.toSet().size)
        paths.forEach { p ->
            assertFalse(p.contains(' '))
            assertTrue(p.none { it.code > 0x7F })
            assertFalse(p.startsWith("http"))
        }
    }

    @Test
    fun `38 个老动作 UUID 一字不差`() {
        val old = ExerciseMediaMapping.mappings.filterKeys { it.startsWith("ex-") }
        assertEquals(38, old.size)
        old.forEach { (uuid, media) ->
            assertNotNull(uuid, ExerciseMediaMapping.forUuid(uuid))
            assertTrue(media.assetPath.startsWith("exercises/"))
        }
    }

    @Test
    fun `三帧 URI 带分类目录且指向本地 asset`() {
        val media = ExerciseMediaMapping.forUuid("ex-chest-02")
        assertNotNull(media)
        assertEquals(
            "file:///android_asset/exercises/chest/bench-press/frame-1.svg",
            media!!.frameUri(1)
        )
        assertEquals(
            "file:///android_asset/exercises/chest/bench-press/frame-3.svg",
            media.frameUri(3)
        )
        assertFalse(media.frameUri(2).startsWith("http"))
    }

    @Test
    fun `Everkinetic 来源没有泛化到全部动作`() {
        val count = ExerciseMediaMapping.mappings.values.count {
            it.attribution.contains("Everkinetic")
        }
        assertEquals(76, count)
        assertTrue(count < ExerciseMediaMapping.mappings.size)
    }

    @Test
    fun `素材状态不冒充已审核 也不虚报候选匹配`() {
        ExerciseMediaMapping.mappings.values.forEach {
            assertEquals(ExerciseMediaMapping.REVIEW_UPSTREAM, it.reviewStatus)
            assertFalse(it.reviewStatus.contains("approved"))
        }
        assertTrue(ExerciseMediaMapping.requiresReviewUuids.isEmpty())
    }

    @Test
    fun `播放序列 1-2-3-2 每帧420ms 循环`() {
        assertTrue(ExerciseMediaMapping.PLAY_SEQUENCE.contentEquals(intArrayOf(1, 2, 3, 2)))
        assertEquals(420L, ExerciseMediaMapping.FRAME_DURATION_MS)
        assertTrue(ExerciseMediaMapping.LOOP)
    }
}
