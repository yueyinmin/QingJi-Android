package com.qingji.app.domain

import kotlin.math.max

/**
 * ============================================================
 * 图片压缩的尺寸计算（M3.2）
 * ------------------------------------------------------------
 * 需求 7.3：上传前把长边缩到 1024px，能省约 60% token。
 * 这里只算「缩到多大」，不碰 Bitmap（安卓类），所以能在电脑上单测。
 * 真正的缩放和 JPEG 压缩在 ImageRecorder 里做。
 * ============================================================
 */
object ImageSizeCalculator {

    data class Size(val width: Int, val height: Int)

    /** 等比缩放到长边不超过 maxEdge；已经够小就原样返回，绝不放大。 */
    fun scaled(width: Int, height: Int, maxEdge: Int = 1024): Size {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return Size(0, 0)
        val longest = max(width, height)
        if (longest <= maxEdge) return Size(width, height)
        val ratio = maxEdge.toDouble() / longest
        val w = (width * ratio).toInt().coerceAtLeast(1)
        val h = (height * ratio).toInt().coerceAtLeast(1)
        return Size(w, h)
    }

    /**
     * 解码大图时用的采样率，取不超过目标比例的 2 的幂，省内存。
     * 例：4000px 缩到 1024px → 返回 2（缩到 2000，再交给 scaled 精确缩）。
     */
    fun sampleSize(width: Int, height: Int, maxEdge: Int = 1024): Int {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
        val longest = max(width, height)
        var sample = 1
        while (longest / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }
}
