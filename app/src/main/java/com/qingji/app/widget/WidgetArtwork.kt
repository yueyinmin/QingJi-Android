package com.qingji.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * V2.3：Glance 无 Canvas API，用 Android Canvas 在 App 进程生成 Bitmap，
 * 再经 ImageProvider(bitmap) 注入 RemoteViews。算法与 App 内 MiniBmiGauge / WaterCup 一致：
 * - BMI：四段半圆弧（15/18.5/24/28/35）+ 随 BMI 转动的指针 + 圆心点
 * - 水杯：梯形杯身 + 杯口椭圆 + 双层水位 + 气泡
 * 均为静态帧（Glance 无动画框架），但形态/配色/角度连续，不是离散资源档。
 */
internal object WidgetArtwork {

    /** BMI 半圆仪表盘。size 单位 px；颜色为各主题 palette 的 ARGB Int。 */
    fun bmiGauge(
        bmi: Double,
        widthPx: Int,
        heightPx: Int,
        green: Int,
        warn: Int,
        danger: Int,
        needle: Int,
        track: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val hasData = bmi > 0.0
        val sweep = if (hasData) ((bmi.coerceIn(15.0, 35.0) - 15.0) / 20.0).toFloat() else 0f

        val stroke = (widthPx * 0.065f).coerceAtLeast(3f)       // ≈8dp @120dp
        val inset = stroke / 2f
        val arc = RectF(inset, inset, widthPx - inset, (heightPx - inset) * 2 - stroke)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.BUTT
        }

        // 四段：15~18.5 淡绿(30%) / 18.5~24 绿 / 24~28 橙 / 28~35 红
        val thin = Color.argb((Color.alpha(green) * 0.30f).toInt(), Color.red(green), Color.green(green), Color.blue(green))
        val parts = listOf(3.5f / 20f to thin, 5.5f / 20f to green, 4f / 20f to warn, 7f / 20f to danger)
        var start = 180f
        parts.forEach { (portion, color) ->
            paint.color = color
            canvas.drawArc(arc, start, 180f * portion, false, paint)
            start += 180f * portion
        }
        if (!hasData) {
            paint.color = track
            canvas.drawArc(arc, 180f, 180f, false, paint)
        }

        val cx = widthPx / 2f
        val cy = heightPx - inset
        val radius = arc.width() / 2 - stroke / 2
        if (hasData) {
            val angle = Math.toRadians((180f + 180f * sweep).toDouble())
            paint.color = needle
            paint.strokeWidth = (widthPx * 0.017f).coerceAtLeast(1.5f) // ≈2dp
            canvas.drawLine(cx, cy, (cx + radius * cos(angle)).toFloat(), (cy + radius * sin(angle)).toFloat(), paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = needle
        canvas.drawCircle(cx, cy, (widthPx * 0.025f).coerceAtLeast(2f), paint)
        return bmp
    }

    /**
     * 梯形水杯（静态帧）。outline/fill/surface 为颜色 Int（App MultiColor 固定语义色，不随主题）。
     * level 0..1；杯内波浪用双层正弦近似，气泡数量 = (9*level).toInt()。
     */
    fun waterCup(
        widthPx: Int,
        heightPx: Int,
        level: Float,
        outline: Int,
        fill: Int,
        surface: Int,
        bubbleTint: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val safe = level.coerceIn(0f, 1f)
        val stroke = (widthPx * 0.019f).coerceAtLeast(1f)          // ≈1.5dp @78dp
        val mouth = (widthPx * 0.20f).coerceAtMost((widthPx * 0.128f)) // ≈10dp @78dp
        val top = mouth / 2 + stroke
        val bottom = heightPx - stroke * 2
        val cx = widthPx / 2f
        val topHalf = widthPx / 2f - stroke
        val bottomHalf = topHalf * 0.72f

        val cup = Path().apply {
            moveTo(cx - topHalf, top)
            lineTo(cx + topHalf, top)
            lineTo(cx + bottomHalf, bottom)
            lineTo(cx - bottomHalf, bottom)
            close()
        }
        canvas.save()
        canvas.clipPath(cup)

        // QJ-WIDGET-GLASS：杯内淡玻璃底色 + 左右竖向反光（与首页 WaterCup 玻璃质感一致）
        val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(26, 255, 255, 255) // 白色 10%
        }
        canvas.drawPath(cup, glassPaint)
        val wallW = (widthPx * 0.09f).coerceAtMost(widthPx * 0.13f)
        val leftGloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx - topHalf, 0f, cx - topHalf + wallW, 0f,
                Color.argb(86, 255, 255, 255), Color.argb(5, 255, 255, 255), Shader.TileMode.CLAMP)
        }
        val rightGloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx + topHalf - wallW, 0f, cx + topHalf, 0f,
                Color.argb(5, 255, 255, 255), Color.argb(46, 255, 255, 255), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(cx - topHalf, top, cx - topHalf + wallW, bottom, leftGloss)
        canvas.drawRect(cx + topHalf - wallW, top, cx + topHalf, bottom, rightGloss)

        if (safe > 0.02f) {
            val waterTop = bottom - (bottom - top) * safe
            val amp = heightPx * 0.016f
            val waveLen = widthPx * 0.7f
            fun wave(yTop: Float, amplitude: Float, shift: Float, fillPath: Boolean): Path {
                val p = Path()
                val n = 24
                p.moveTo(cx - topHalf, yTop + amplitude * sin(((cx - topHalf + shift) / waveLen) * 2.0 * PI).toFloat())
                for (i in 1..n) {
                    val x = cx - topHalf + (topHalf * 2) * i / n
                    p.lineTo(x, yTop + amplitude * sin(((x + shift) / waveLen) * 2.0 * PI).toFloat())
                }
                if (fillPath) {
                    p.lineTo(cx + bottomHalf, bottom)
                    p.lineTo(cx - bottomHalf, bottom)
                    p.close()
                }
                return p
            }
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            fillPaint.color = Color.argb((Color.alpha(fill) * 0.45f).toInt(), Color.red(fill), Color.green(fill), Color.blue(fill))
            canvas.drawPath(wave(waterTop - heightPx * 0.013f, amp * 1.15f, waveLen * 0.35f, true), fillPaint)
            val bodyGrad = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = LinearGradient(0f, waterTop, 0f, bottom,
                    Color.argb((Color.alpha(fill) * 0.82f).toInt(), Color.red(fill), Color.green(fill), Color.blue(fill)),
                    fill, Shader.TileMode.CLAMP)
            }
            canvas.drawPath(wave(waterTop, amp, 0f, true), bodyGrad)
            // 水面折射亮带：紧贴水面下方
            val refract = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.argb(96, 255, 255, 255)
                strokeWidth = (stroke * 1.6f).coerceAtMost(heightPx * 0.008f)
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawPath(wave(waterTop + heightPx * 0.01f, amp, 0f, false), refract)
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; color = surface; strokeWidth = stroke; this.strokeCap = Paint.Cap.ROUND
            }
            canvas.drawPath(wave(waterTop, amp, 0f, false), linePaint)

            // 固定种子气泡（与 App Random(20261108) 同思路）
            val random = java.util.Random(20261108)
            val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = bubbleTint }
            repeat((9 * safe).toInt()) {
                val bx = widthPx * (0.32f + 0.4f * random.nextFloat())
                val by = waterTop + (bottom - waterTop) * (0.1f + 0.8f * random.nextFloat())
                val r = widthPx * (0.018f + random.nextFloat() * 0.02f)
                canvas.drawCircle(bx, by, r, bubblePaint)
            }
        }
        // 厚玻璃底：底部渐变亮带 + 高光线
        val footH = (heightPx * 0.085f).coerceAtLeast(widthPx * 0.05f)
        val footGrad = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(0f, bottom - footH, 0f, bottom,
                Color.argb(12, 255, 255, 255), Color.argb(107, 255, 255, 255), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(cx - bottomHalf, bottom - footH, cx + bottomHalf, bottom, footGrad)
        val footLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(128, 255, 255, 255)
            strokeWidth = (stroke * 1.4f).coerceAtMost(widthPx * 0.03f)
            strokeCap = Paint.Cap.ROUND
        }
        val footY = bottom - stroke * 3f
        val halfFoot = bottomHalf * 0.62f
        canvas.drawLine(cx - halfFoot, footY, cx + halfFoot, footY, footLine)
        canvas.restore()

        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = outline; strokeWidth = stroke
        }
        canvas.drawOval(RectF(cx - topHalf, top - mouth / 2, cx + topHalf, top + mouth / 2), outlinePaint)
        val innerRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(140, 255, 255, 255)
            strokeWidth = (stroke * 0.8f).coerceAtLeast(1f)
        }
        canvas.drawOval(RectF(cx - topHalf + stroke, top - mouth / 2 + stroke,
            cx + topHalf - stroke, top + mouth / 2 - stroke), innerRim)
        canvas.drawLine(cx - topHalf, top, cx - bottomHalf, bottom, outlinePaint)
        canvas.drawLine(cx + topHalf, top, cx + bottomHalf, bottom, outlinePaint)
        canvas.drawLine(cx - bottomHalf, bottom, cx + bottomHalf, bottom, outlinePaint)
        return bmp
    }
}
