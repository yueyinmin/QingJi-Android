package com.qingji.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.outlined.Rowing
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 运动名 → 矢量图标。
 * 参考图里每个运动左边是一个绿色小人/器材图标，这里按名称关键词映射，
 * 颜色由调用方取主题色（QingJiGreen），换主题色时会跟着变。
 * 只做「看得懂是什么运动」这一层，不追求和参考图逐个像素一样。
 */
object ExerciseIcon {
    fun of(name: String): ImageVector = when {
        name.contains("跑") -> Icons.AutoMirrored.Outlined.DirectionsRun
        name.contains("骑") || name.contains("单车") || name.contains("车") -> Icons.AutoMirrored.Outlined.DirectionsBike
        name.contains("泳") || name.contains("水") -> Icons.Outlined.Pool
        name.contains("划船") || name.contains("赛艇") -> Icons.Outlined.Rowing
        name.contains("球") -> Icons.Outlined.SportsSoccer
        name.contains("瑜伽") || name.contains("拉伸") || name.contains("普拉提") || name.contains("柔韧") ->
            Icons.Outlined.SelfImprovement
        name.contains("力量") || name.contains("俯卧撑") || name.contains("深蹲") ||
            name.contains("平板") || name.contains("举") || name.contains("训练") ->
            Icons.Outlined.FitnessCenter
        name.contains("上坡") || name.contains("爬") || name.contains("登山") || name.contains("徒步") ->
            Icons.Outlined.Hiking
        name.contains("走") || name.contains("步") -> Icons.AutoMirrored.Outlined.DirectionsWalk
        else -> Icons.Outlined.AccessibilityNew
    }
}
