package com.qingji.app.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.screen.settings.DetailGroupTitle
import com.qingji.app.ui.screen.settings.RowDivider
import com.qingji.app.ui.screen.settings.SettingsDetailScaffold
import com.qingji.app.ui.screen.settings.SwitchRow
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.pressable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



// ============================================================
// ============================================================
// 「主题与外观」二级分类（v1.4.2 设置整合）
// ------------------------------------------------------------
// 只保留外观本身：主题色 / 深浅模式 / 应用图标 / 自定义字体 / 水杯气泡量。
// 原这里的「进度条动效 / 本周小结 / 每餐营养素比例 / 实时刷新率 / 首页模块」
// 已按工单搬到「动效与性能」和「首页布局」，本页不再重复出现（P0-03）。
// 应用图标点「进去」打开独立弹层，继续用原有 AppIconSettingsPage 实现。
// ============================================================
@Composable
internal fun ThemeSettingsPage(
    settings: QingJiSettings,
    onSetTheme: (String) -> Unit,
    onSetThemeColor: (String) -> Unit,
    onSetBubbles: (Int) -> Unit,
    onSetFont: (String) -> Unit,
    onOpenIcon: () -> Unit,
    onBack: () -> Unit,
    useSheet: Boolean = false
) {
    SettingsDetailScaffold(title = "主题与外观", onBack = onBack, useSheet = useSheet) {
        // ---------- 主题色 ----------
        DetailGroupTitle("主题色")
        SectionCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                com.qingji.app.ui.theme.AccentThemes.ALL.forEach { spec ->
                    val selected = spec.key == settings.themeColor
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(spec.lightMain))
                                .clickable { onSetThemeColor(spec.key) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) Text(text = "✓", fontSize = 16.sp, color = Color.White)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = spec.label,
                            fontSize = 10.sp,
                            color = if (selected) QingJiGreenDark else TextSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "换主色后，按钮、进度条、三大营养素的配色会一起变。",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(Modifier.height(Dimens.Gap))

        // ---------- 深浅模式 ----------
        DetailGroupTitle("深浅模式")
        SectionCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (key, label) ->
                    val selected = settings.themeMode == key
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        color = if (selected) Color.White else TextSecondary,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) QingJiGreen else PageBackground)
                            .clickable { onSetTheme(key) }
                            .padding(vertical = 9.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(Dimens.Gap))

        // ---------- 应用图标（保留原有 AppIconSettingsPage 实现） ----------
        DetailGroupTitle("应用图标")
        SectionCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenIcon() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("桌面图标样式", fontSize = 15.sp, color = TextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text("薄荷绿 / 森林绿 / 暖阳奶油，切换不影响数据", fontSize = 11.sp, color = TextSecondary)
                }
                Text("›", fontSize = 20.sp, color = TextSecondary.copy(alpha = 0.6f))
            }
        }

        Spacer(Modifier.height(Dimens.Gap))

        // ---------- 水杯气泡量 ----------
        DetailGroupTitle("水杯气泡量")
        SectionCard {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(text = "气泡越少越省电，关掉只剩水位", fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "关", 1 to "少", 2 to "标准", 3 to "多").forEach { (level, label) ->
                        val selected = settings.waterBubbles == level
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            color = if (selected) Color.White else TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) QingJiGreen else PageBackground)
                                .clickable { onSetBubbles(level) }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Dimens.Gap))

        // ---------- 自定义字体 ----------
        DetailGroupTitle("自定义字体")
        SectionCard {
            val fontCtx = androidx.compose.ui.platform.LocalContext.current
            val fontScope = rememberCoroutineScope()
            var showFontSheet by remember { mutableStateOf(false) }
            val fontPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    fontScope.launch {
                        val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching {
                                val dir = java.io.File(fontCtx.filesDir, "fonts").apply { mkdirs() }
                                val dest = java.io.File(dir, "custom_${System.currentTimeMillis()}.ttf")
                                fontCtx.contentResolver.openInputStream(uri)?.use { input ->
                                    java.io.FileOutputStream(dest).use { out -> input.copyTo(out) }
                                }
                                if (dest.length() > 0) dest.absolutePath else null
                            }.getOrNull()
                        }
                        if (saved != null) onSetFont(saved)
                        showFontSheet = false
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showFontSheet = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "自定义字体", fontSize = 15.sp, color = TextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (settings.customFontPath.isBlank()) {
                            "支持导入 ttf / otf 字体文件，整个 App 换字"
                        } else "已启用自定义字体",
                        fontSize = 11.sp, color = TextSecondary
                    )
                }
                Text(
                    text = if (settings.customFontPath.isBlank()) "去设置" else "已启用",
                    fontSize = 13.sp, color = QingJiGreenDark
                )
            }

            if (showFontSheet) {
                com.qingji.app.ui.components.AppBottomSheet(
                    title = "自定义字体",
                    subtitle = "选一个 ttf 或 otf 文件，App 所有文字会换成它",
                    onDismiss = { showFontSheet = false }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.ItemCorner))
                            .background(QingJiGreen)
                            .pressable { fontPicker.launch("font/*") }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "选择字体文件", fontSize = 15.sp, color = Color.White)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.ItemCorner))
                            .background(PageBackground)
                            .pressable { onSetFont(""); showFontSheet = false }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "恢复系统默认字体", fontSize = 14.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}


// ============================================================
// 「通知与提醒」详情页：称重 / 断食 / 热量 / 喝水四类提醒集中在一页。
// 每类都是「开关 + 时间」在一起，不用再跳来跳去。
// ============================================================
@Composable
internal fun ReminderPage(
    settings: QingJiSettings,
    allDisabled: Boolean,
    onSetAllDisabled: (Boolean) -> Unit,
    onSetWeighReminder: (Int, Int, Int) -> Unit,   // 星期、小时、分钟
    onSetFastingSwitch: (Boolean) -> Unit,
    onSetFastingTime: (Int, Int) -> Unit,         // 小时、分钟
    onSetKcalSwitch: (Boolean) -> Unit,
    onSetKcalTime: (Int, Int) -> Unit,            // 小时、分钟
    onSetWaterSwitch: (Boolean) -> Unit,
    onSetWaterTime: (Int, Int) -> Unit,           // 小时、分钟
    onBack: () -> Unit,
    useSheet: Boolean = false
) {
    // 称重提醒的星期 / 小时 / 分钟在页面里先选好，点底部保存一次性写入
    var weekday by remember { mutableStateOf(settings.weighWeekday) }
    var weighHour by remember { mutableStateOf(settings.reminderHour) }
    var weighMinute by remember { mutableStateOf(settings.reminderMinute) }
    val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    SettingsDetailScaffold(title = "通知与提醒", onBack = onBack, useSheet = useSheet) {
        // ---------- 总开关 ----------
        SectionCard(contentPadding = 0.dp) {
            SwitchRow(
                title = "禁用所有通知",
                subtitle = "打开后下面的所有提醒都不会再响，适合出差或休假",
                checked = allDisabled
            ) { onSetAllDisabled(it) }
        }

        if (allDisabled) {
            Spacer(Modifier.height(Dimens.Gap))
            com.qingji.app.ui.components.HintBox(text = "所有提醒已禁用。关掉上面的开关即可恢复。")
            Spacer(Modifier.height(Dimens.FabSpace))
            return@SettingsDetailScaffold
        }

        // ---------- 称重提醒 ----------
        DetailGroupTitle("称重提醒")
        SectionCard {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(text = "每周几提醒上秤", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                // v12：七个圆改成「Row + weight(1f) 七等分」，每格内圆居中。
                // 之前是 7 个固定 36dp 圆直接求和（288dp），父链三层 padding 吃掉
                // 100dp 后，360dp 屏只剩 260dp，最右「日」必然被裁。
                // 现在格宽随可用宽度算，圆直径再按格宽自适应（上限 38dp），
                // 任何屏宽下七格都完整可见、圆也不会互相重叠。
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val gap = 6.dp
                    val cellWidth = (maxWidth - gap * 6) / 7
                    val dotSize = minOf(38.dp, cellWidth)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        weekdayLabels.forEachIndexed { index, label ->
                            val value = index + 1
                            val selected = weekday == value
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(dotSize)
                                        .clip(CircleShape)
                                        .background(if (selected) QingJiGreen else PageBackground)
                                        .clickable { weekday = value },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        color = if (selected) Color.White else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(text = "几点提醒（左边选小时、右边选分钟）", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                com.qingji.app.ui.components.WheelTimePicker(
                    initialHour = weighHour,
                    initialMinute = weighMinute
                ) { h, m ->
                    weighHour = h
                    weighMinute = m
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.ItemCorner))
                .background(QingJiGreen)
                .pressable {
                    onSetWeighReminder(weekday, weighHour, weighMinute)
                    onBack()
                }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "保存称重提醒", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }

        Spacer(Modifier.height(Dimens.Gap))

        // ---------- 断食提醒 ----------
        DetailGroupTitle("断食提醒")
        SectionCard(contentPadding = 0.dp) {
            SwitchRow(
                title = "轻断食提醒",
                subtitle = "每天到点提醒开始 / 结束断食",
                checked = settings.fastingReminder
            ) { onSetFastingSwitch(it) }
            RowDivider()
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(text = "提醒时间（左边小时、右边分钟，松开即保存）", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                com.qingji.app.ui.components.WheelTimePicker(
                    initialHour = settings.fastStartHour,
                    initialMinute = settings.fastStartMinute
                ) { h, m -> onSetFastingTime(h, m) }
            }
        }

        Spacer(Modifier.height(Dimens.Gap))

        // ---------- 热量提醒 ----------
        DetailGroupTitle("热量提醒")
        SectionCard(contentPadding = 0.dp) {
            SwitchRow(
                title = "热量提醒",
                subtitle = "当天还没记饮食才提醒，记了就不烦你",
                checked = settings.kcalReminder
            ) { onSetKcalSwitch(it) }
            RowDivider()
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(text = "提醒时间（左边小时、右边分钟，松开即保存）", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                com.qingji.app.ui.components.WheelTimePicker(
                    initialHour = settings.kcalReminderHour,
                    initialMinute = settings.kcalReminderMinute
                ) { h, m -> onSetKcalTime(h, m) }
            }
        }

        Spacer(Modifier.height(Dimens.Gap))

        // ---------- 喝水提醒（v3.4.0 第四类） ----------
        DetailGroupTitle("喝水提醒")
        SectionCard(contentPadding = 0.dp) {
            SwitchRow(
                title = "喝水提醒",
                subtitle = "到点提醒补一杯；当天已经喝够目标就不打扰",
                checked = settings.waterReminder
            ) { onSetWaterSwitch(it) }
            RowDivider()
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(text = "提醒时间（左边小时、右边分钟，松开即保存）", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                com.qingji.app.ui.components.WheelTimePicker(
                    initialHour = settings.waterReminderHour,
                    initialMinute = settings.waterReminderMinute
                ) { h, m -> onSetWaterTime(h, m) }
            }
        }

        Spacer(Modifier.height(Dimens.Gap))
        HintBox(
            text = "提醒靠系统闹钟实现，误差几分钟属正常。" +
                "小米 / 华为 / OPPO 建议把轻记加进自启动白名单，否则重启后可能不再提醒。"
        )
    }
}



/**
 * 运动消耗补偿二级页（2026-11-12）：五档胶囊 + 机制说明。
 * 设备普遍高估运动消耗，全额加回容易越练越多吃；0% 等于关闭。
 */
@Composable
internal fun ExercisePctDetailPage(
    pct: Int,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
    useSheet: Boolean = false
) {
    SettingsDetailScaffold(title = "运动消耗补偿", onBack = onBack, useSheet = useSheet) {
        SectionCard {
            Text(
                text = "运动消耗按比例加回当天饮食额度。运动设备和算法普遍高估消耗，" +
                    "全额加回容易越练越多吃，所以默认只补一半。",
                fontSize = 13.sp,
                color = TextPrimary,
                lineHeight = 21.sp
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 25, 50, 75, 100).forEach { option ->
                    val selected = pct == option
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) QingJiGreenLight else PageBackground)
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$option%",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) QingJiGreenDark else TextPrimary
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "0% 等于关闭；25% 和 75% 是折中档，选完立刻生效。",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(Dimens.Gap))
        SectionCard {
            Text(
                text = "举例：今天运动消耗 500 千卡、补偿 50% → 当天预算加 250 千卡，" +
                    "首页的「预计热量缺口」会同步变大。",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}
