package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.components.GroupCard
import com.qingji.app.ui.components.GroupDivider
import com.qingji.app.ui.components.IconTones
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SettingsRow
import com.qingji.app.ui.theme.PageBackground

/**
 * 工具页（2026-11-24）：常用小工具入口，风格沿用「我的」页的功能行。
 * 入口：我的页「工具」行 + 首页「今日记录」右侧图标。
 */
@Composable
fun ToolsScreen(
    onBack: () -> Unit,
    onOpenCalorieConverter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(title = "工具", onBack = onBack)
        GroupCard {
            SettingsRow(
                title = "卡路里换算",
                icon = Icons.Outlined.Calculate,
                tone = IconTones.Orange,
                subtitle = "按重量与千焦算出总大卡",
                onClick = onOpenCalorieConverter
            )
        }
    }
}
