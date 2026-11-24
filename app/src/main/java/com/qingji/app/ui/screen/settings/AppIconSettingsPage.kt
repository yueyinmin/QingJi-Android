package com.qingji.app.ui.screen.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.R
import com.qingji.app.domain.AppIconManager
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

/**
 * v1.1.0：应用图标切换页（底部弹层）。
 * 三个预览卡：薄荷绿 / 森林绿 / 暖阳奶油，点一下立即切换。
 * 预览用同一份叶片前景矢量 + 各自的背景色合成，和桌面实际显示一致。
 */
@Composable
fun AppIconSettingsPage(
    currentVariant: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    useSheet: Boolean = true
) {
    SettingsDetailScaffold(title = "应用图标", onBack = onBack, useSheet = useSheet) {
        SectionCard(contentPadding = 0.dp) {
            AppIconManager.VARIANTS.forEachIndexed { index, variant ->
                val selected = variant == currentVariant
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(variant) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 图标预览：背景色 + 叶片前景，等比还原桌面效果
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colorResource(iconBgRes(variant))),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(iconFgRes(variant)),
                            contentDescription = AppIconManager.label(variant),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppIconManager.label(variant),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        if (selected) {
                            Text(text = "当前使用", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = QingJiGreenDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (index < AppIconManager.VARIANTS.lastIndex) RowDivider()
            }
        }

        Spacer(Modifier.height(Dimens.Gap))
        HintBox(
            text = "点一下立即切换。桌面图标可能延迟几秒刷新；" +
                "个别桌面会先把旧图标当「已失效」处理，重新放一次即可。" +
                "切换不影响任何数据。"
        )
    }
}

/** 每套图标对应的背景色资源（与 res/values/colors.xml 一致）。 */
private fun iconBgRes(variant: String): Int = when (variant) {
    "forest" -> R.color.ic_launcher_background_forest
    "cream" -> R.color.ic_launcher_background_cream
    "drop" -> R.color.ic_launcher_bg_drop
    "bolt" -> R.color.ic_launcher_bg_bolt
    "moon" -> R.color.ic_launcher_bg_moon
    "heart" -> R.color.ic_launcher_bg_heart
    "star" -> R.color.ic_launcher_bg_star
    "flame" -> R.color.ic_launcher_bg_flame
    else -> R.color.ic_launcher_background
}

/** 9 种图标各有前景：前 3 套复用叶片，后 6 套用各自图形。 */
private fun iconFgRes(variant: String): Int = when (variant) {
    "drop" -> R.drawable.ic_launcher_fg_drop
    "bolt" -> R.drawable.ic_launcher_fg_bolt
    "moon" -> R.drawable.ic_launcher_fg_moon
    "heart" -> R.drawable.ic_launcher_fg_heart
    "star" -> R.drawable.ic_launcher_fg_star
    "flame" -> R.drawable.ic_launcher_fg_flame
    else -> R.drawable.ic_launcher_foreground
}
