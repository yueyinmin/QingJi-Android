package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 图片压缩尺寸：长边 1024、不放大、采样率取 2 的幂。 */
class ImageSizeCalculatorTest {

    @Test
    fun `横图缩到长边1024且保持比例`() {
        val size = ImageSizeCalculator.scaled(4000, 3000)
        assertEquals(1024, size.width)
        assertEquals(768, size.height)
    }

    @Test
    fun `竖图缩到长边1024`() {
        val size = ImageSizeCalculator.scaled(3000, 4000)
        assertEquals(768, size.width)
        assertEquals(1024, size.height)
    }

    @Test
    fun `小图不放大`() {
        val size = ImageSizeCalculator.scaled(640, 480)
        assertEquals(640, size.width)
        assertEquals(480, size.height)
    }

    @Test
    fun `非法尺寸返回零而不是崩溃`() {
        assertEquals(0, ImageSizeCalculator.scaled(0, 100).width)
        assertEquals(0, ImageSizeCalculator.scaled(100, -1).height)
    }

    @Test
    fun `采样率是2的幂且不会过度缩小`() {
        val s = ImageSizeCalculator.sampleSize(4000, 3000)
        assertTrue(s and (s - 1) == 0)          // 2 的幂
        assertTrue(4000 / s >= 1024)
        assertEquals(1, ImageSizeCalculator.sampleSize(800, 600))
    }
}
