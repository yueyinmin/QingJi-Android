package com.qingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos

/**
 * 实时刷新率徽章（v2.4）。
 *
 * 用「逐帧计数」测：每一帧回调一次，数满一秒算出 FPS。
 * 不是查系统 API——各厂商 ROM 查出来的标称值（60/90/120Hz）
 * 和实际渲染帧率经常对不上，实测的才是眼睛看到的。
 *
 * 放在主题设置里开关，开了之后首页右上角会浮一个小徽章。
 */
@Composable
fun RefreshRateBadge(modifier: Modifier = Modifier) {
    var fps by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var lastNanos = withFrameNanos { it }
        var frames = 0
        while (true) {
            withFrameNanos { }
            frames++
            val now = withFrameNanos { it }
            if (now - lastNanos >= 1_000_000_000L) {
                fps = (frames * 1_000_000_000L / (now - lastNanos)).toInt()
                frames = 0
                lastNanos = now
            }
        }
    }

    Text(
        text = "$fps FPS",
        fontSize = 10.sp,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x99000000))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
