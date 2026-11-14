package com.qingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.QingJiThemeState
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

/**
 * ============================================================
 * 「我的 / 设置」统一视觉组件（UI 统一工单）
 * ------------------------------------------------------------
 * 目标只有一个：让两个页面长成同一套设计系统，而不是两套拼在一起。
 *
 * 三个统一：
 * 1. 形状统一 —— 38dp 圆角方形底板（12dp 圆角）；
 * 2. 线条统一 —— 全部 Material 线性图标，20dp；
 * 3. 配色统一 —— 按功能语义分色，浅色底 + 同色系深色图标。
 *
 * 深色模式下自动把浅色底换成同色 18% 透明，不再额外维护一套色表。
 * ============================================================
 */

/** 一个语义色阶：图标色 + 浅色底板（深色模式自动换算）。 */
data class IconTone(val icon: Color, val containerLight: Color)

/** 底板色：浅色模式用固定的低饱和色，深色模式用图标色加透明度。 */
val IconTone.container: Color
    get() = if (QingJiThemeState.darkMode) icon.copy(alpha = 0.18f) else containerLight

/**
 * 深色底板上的前景色。
 * 深色下底板是「图标色 18% 叠深底」，直接用原色画数字会糊成一团，
 * 往白色混 32% 提亮，保证图标和统计数字都看得清。
 */
val IconTone.onContainer: Color
    get() = if (QingJiThemeState.darkMode) lerp(icon, Color.White, 0.32f) else icon

/**
 * 八种语义色（颜色值按工单给的建议值，已与现有主题校准）。
 * 蓝色=数据/资料，绿色=健康/隐私，橙色=热量/组件，紫色=智能/外观，
 * 粉色=偏好/关于，红色=缓存/清理，青色=时区/AI，灰色=版本。
 */
object IconTones {
    val Green = IconTone(Color(0xFF299879), Color(0xFFE7F4EE))
    val Blue = IconTone(Color(0xFF528FE0), Color(0xFFE9F1FC))
    val Orange = IconTone(Color(0xFFE5A044), Color(0xFFFBF1E2))
    val Purple = IconTone(Color(0xFF9276D8), Color(0xFFF0ECFA))
    val Pink = IconTone(Color(0xFFDB83A5), Color(0xFFF9EBF1))
    val Red = IconTone(Color(0xFFDB7777), Color(0xFFF9ECEB))
    val Teal = IconTone(Color(0xFF4BAEB0), Color(0xFFE7F4F3))
    val Gray = IconTone(Color(0xFF87949A), Color(0xFFEDF0F1))
}

/** 统一功能图标：圆角方形底板 + 线性图标。 */
@Composable
fun FeatureIcon(
    icon: ImageVector,
    tone: IconTone,
    modifier: Modifier = Modifier,
    containerSize: Dp = 38.dp,
    iconSize: Dp = 20.dp,
    corner: Dp = 12.dp
) {
    Box(
        modifier = modifier
            .size(containerSize)
            .clip(RoundedCornerShape(corner))
            .background(tone.container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tone.icon,
            modifier = Modifier.size(iconSize)
        )
    }
}

/** 统一的白色分组卡片（20dp 圆角、无阴影）。 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            content = content
        )
    }
}

/** 分组标题：13sp 次文字，和列表左边缘对齐一点留白。 */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = TextSecondary,
        modifier = modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp)
    )
}

/**
 * 统一列表行。
 * 图标 38dp + 标题 + 可选右侧状态 + 箭头，最小高度 60dp。
 * 普通行默认不显示副标题（工单要求），确需说明时才传 subtitle。
 */
@Composable
fun SettingsRow(
    title: String,
    icon: ImageVector,
    tone: IconTone,
    modifier: Modifier = Modifier,
    trailing: String = "",
    subtitle: String? = null,
    showArrow: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FeatureIcon(icon = icon, tone = tone)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) TextPrimary else TextSecondary
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 11.sp, color = TextSecondary)
            }
        }
        if (trailing.isNotBlank()) {
            Text(
                text = trailing,
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (showArrow) {
            Spacer(Modifier.size(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 行间分割线：左侧留白对齐标题（14 + 38 + 12 = 64dp）。 */
@Composable
fun GroupDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 64.dp)
            .height(1.dp)
            .background(DividerColor.copy(alpha = 0.7f))
    )
}

/** 统计单元数据（使用情况宫格用）。 */
data class StatCell(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val tone: IconTone
)

/**
 * 三列自适应统计宫格。
 * 每格：浅色底 + 小图标 + 名称 + 次数，比原来「两列白条」更紧凑。
 */
@Composable
fun StatisticGrid(
    items: List<StatCell>,
    modifier: Modifier = Modifier,
    columns: Int = 3
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { cell ->
                    StatisticCell(cell = cell, modifier = Modifier.weight(1f))
                }
                // 最后一行不满时补空位，保证格子宽度一致
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatisticCell(cell: StatCell, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cell.tone.container)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = cell.icon,
            contentDescription = null,
            tint = cell.tone.icon,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.size(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cell.label,
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1
            )
            Text(
                text = cell.value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}


/**
 * 使用情况统计卡（2 列 × 4 排用）。
 * 横向结构：左侧功能图标（26dp）+ 右侧「名称 / 次数」两行，次数是视觉重点。
 * 底板沿用语义色，数字跟图标同色，8 张卡只写这一个 Composable。
 *
 * 高度用 heightIn(min) 而不是固定 height：系统字体放大时卡片能自己长高，
 * 不会把数字裁掉。
 */
@Composable
fun UsageStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    tone: IconTone,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            // 尺寸对齐「我的」页普通菜单行：图标 22、名称 13、次数 20，
            // 整卡 68dp 高，比第一版 88dp 收了一圈，不再占掉半个页面
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(tone.container)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tone.onContainer,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = tone.onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
