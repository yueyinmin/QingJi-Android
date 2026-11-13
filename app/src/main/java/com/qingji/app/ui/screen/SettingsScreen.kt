package com.qingji.app.ui.screen

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlin.math.roundToInt
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.BuildConfig
import com.qingji.app.data.backup.BackupManager
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.ui.screen.settings.GroupTitle
import com.qingji.app.ui.screen.settings.RowDivider
import com.qingji.app.ui.screen.settings.SettingIconRow
import com.qingji.app.ui.screen.settings.SettingIconSwitchRow
import com.qingji.app.ui.screen.settings.SettingsSearchBox
import com.qingji.app.ui.screen.settings.SettingsCard
import com.qingji.app.ui.screen.settings.SwitchRow
import com.qingji.app.ui.screen.settings.ToggleItem
import com.qingji.app.ui.screen.settings.ToggleDetailPage
import com.qingji.app.ui.screen.settings.SettingsDetailScaffold
import com.qingji.app.ui.screen.settings.OptionItem
import com.qingji.app.ui.screen.settings.OptionDetailPage
import com.qingji.app.ui.screen.settings.NumberDetailPage
import com.qingji.app.ui.screen.settings.InfoDetailPage
import com.qingji.app.ui.screen.settings.DetailValueRow
import com.qingji.app.ui.screen.settings.DetailGroupTitle
import com.qingji.app.ui.screen.settings.DangerActionButton
import com.qingji.app.ui.screen.settings.hourOptions
import com.qingji.app.ui.screen.settings.weekdayOptions
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.SectionCard
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.WarnOrange
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.pressable
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.SettingsViewModel
import com.qingji.app.widget.OverviewWidgetReceiver
import com.qingji.app.widget.QuickRecordWidgetReceiver
import com.qingji.app.widget.WeightWidgetReceiver

/**
 * ============================================================
 * 设置页（M7：全量接线）
 * ------------------------------------------------------------
 * 和上一个版本最大的区别：以前 90% 的菜单点了弹「后续接入」，
 * 现在每一项都真的能改，而且改完立刻生效：
 * - 主题 / 单位 / 时区：全 App 马上跟着变
 * - 饮水目标、营养素比例：首页数字跟着重算
 * - 三个提醒：存设置 → 重排系统闹钟 → 到点发通知
 * - 底部导航、首页显隐：回首页就能看到变化
 * - 数据备份：跳到导出/导入页
 *
 * 所有二级项统一用底部弹层（ModalBottomSheet）：少按一次返回，
 * 背后的设置列表不销毁，返回后滚动位置不丢。内容多的项在弹层内部滚动。
 * 只有「个人资料 / AI 服务配置 / 数据备份 / 饮食偏好 / 桌面小组件」
 * 是真正的独立页面（内容太多或带预览，不适合塞进弹层）。
 * ============================================================
 */

/**
 * v1.0.8 极简设置页：低饱和彩色图标配色 + 浅灰紫页面底色。
 * 只在设置首页用，不改全局主题，其它页面配色不受影响。
 */
private val SettingsIconBlue = Color(0xFF5B8DEF)
private val SettingsIconBlueLight = Color(0xFF4AA8E0)
private val SettingsIconGreen = Color(0xFF4CAF88)
private val SettingsIconOrange = Color(0xFFF2A03D)
private val SettingsIconCoral = Color(0xFFF07C6E)
private val SettingsIconPurple = Color(0xFF9B7BE0)
private val SettingsIconPink = Color(0xFFE879A6)
private val SettingsIconCyan = Color(0xFF3FB6C4)
private val SettingsIconIndigo = Color(0xFF7A7FE0)
private val SettingsIconRed = Color(0xFFE06A6A)
private val SettingsIconTeal = Color(0xFF3FAE9A)
private val SettingsIconGray = Color(0xFF8A93A6)
private val SettingsPageBg = Color(0xFFF5F5F9)

/** 设置首页一行：分组 / 标题 / 图标 / 右侧状态或开关 / 点击动作。 */
private data class SettingEntry(
    val group: String,
    val title: String,
    val icon: ImageVector,
    val iconColor: Color,
    val value: String = "",
    /** 非 null 表示这一行是开关行（右侧放 Switch，不放箭头）。 */
    val switchState: Boolean? = null,
    val onSwitch: ((Boolean) -> Unit)? = null,
    val onClick: (() -> Unit)? = null
)

/** 当前打开的是哪个弹窗。 */
private enum class DialogKind {
    MACRO, WATER_GOAL, TIMEZONE, UNIT, THEME, REMINDERS,
    BOTTOM_TABS, AI_LANG, UI_LANG, PRIVACY, ICON, CACHE,
    ABOUT, PLATFORM, VERSION, EXERCISE_PCT, RECOGNITION, HOME_DISPLAY
}

/**
 * v1.0.7（14:50 后）：设置里**所有**二级项都走「从底部浮起的操作面板」
 *（AppBottomSheet，和轻断食设置同款），不再整屏翻页。
 *
 * 早期按「少于 4 条才用弹层」分流，是因为当时弹层高度写死 420dp，
 * 条目一多就得在里面滚；后来弹层改成「随内容高、最多 420dp、超了内部滚」，
 * 长内容（关于 / 主题 / 提醒）实测可滚且不撑满屏，这条分界线就撤掉了。
 * 交互口径不变：点选即生效；只有要输入的（营养素 / 饮水目标）
 * 才保留「保存」按钮。
 *
 * 所以这里不再维护白名单：dialog 非空 = 弹层，列表始终留在底下。
 */

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAiConfig: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenBackup: () -> Unit,
    /** 饮食偏好与忌口：选项太多，改成独立页面 */
    onOpenFoodPreference: () -> Unit = {},
    /** 桌面小组件：改成带预览的独立页面 */
    onOpenWidget: () -> Unit = {},
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    val message by vm.message.collectAsState()

    var dialog by remember { mutableStateOf<DialogKind?>(null) }
    var cacheSize by remember { mutableStateOf(0L) }
    val backup = remember { BackupManager(context) }

    // 一次性提示：存完设置给个反馈
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    LaunchedEffect(dialog) {
        if (dialog == DialogKind.CACHE) cacheSize = backup.cacheSize()
    }

    // v1.1.0 修复：安卓 13 以上开启提醒要先拿到通知权限——
    // 授权成功才执行开启动作，拒绝则保持关闭（之前是弹窗和开启同时进行，
    // 用户拒绝了开关也会变成开）。
    var pendingNotifAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingNotifAction
        pendingNotifAction = null
        if (granted) {
            action?.invoke()
        } else {
            Toast.makeText(context, "没给通知权限，提醒保持关闭", Toast.LENGTH_LONG).show()
        }
    }

    /** 开启提醒前检查权限：没有就先弹系统授权框，授权成功才执行 action。 */
    fun enableReminderWithPermission(action: () -> Unit) {
        val needAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needAsk) {
            pendingNotifAction = action
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    // 滚动状态放在最外层：进详情页再退回来，列表停在原来的位置，
    // 不会一退回去就跳到顶部（之前的问题就在这——列表被销毁重建了）。
    val listScroll = rememberScrollState()
    // v1.0.8：搜索关键字放最外层，列表重组不会丢输入
    var query by remember { mutableStateOf("") }
    // 全部二级项都是弹层：设置列表始终留在底下，弹层叠在上面
    androidx.activity.compose.BackHandler(enabled = dialog != null) { dialog = null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SettingsPageBg)
    ) {
        // ---------- 标题栏 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = "设置",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }

        // v1.0.8：搜索框常驻，不跟着列表滚走
        SettingsSearchBox(query = query, onQueryChange = { query = it })

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(listScroll)
                .padding(horizontal = 12.dp)
        ) {
            // v1.0.8：8 个分组、24 个真实功能项。文档里「设备绑定 / 导出日志」
            // 这类代码里不存在的功能一律不做，避免出现点了没反应的假菜单。
            val entries: List<SettingEntry> = buildList {
                // 1 账号与安全（App 无账号，保留「隐私与安全」原名，不叫账号安全）
                add(SettingEntry("账号与安全", s("个人资料"), Icons.Filled.Person, SettingsIconBlue,
                    value = settings.nickname, onClick = { onOpenProfile() }))
                add(SettingEntry("账号与安全", s("隐私与安全"), Icons.Filled.Lock, SettingsIconGreen,
                    onClick = { dialog = DialogKind.PRIVACY }))

                // 2 热量与营养
                add(SettingEntry("热量与营养", s("营养素分配方案"), Icons.Outlined.Restaurant, SettingsIconGreen,
                    value = "${settings.proteinPct}/${settings.carbPct}/${settings.fatPct}",
                    onClick = { dialog = DialogKind.MACRO }))
                add(SettingEntry("热量与营养", s("运动消耗补偿"), Icons.AutoMirrored.Outlined.DirectionsRun, SettingsIconOrange,
                    value = "${settings.exerciseCompensationPct}%",
                    onClick = { dialog = DialogKind.EXERCISE_PCT }))
                add(SettingEntry("热量与营养", s("饮水目标"), Icons.Outlined.WaterDrop, SettingsIconBlueLight,
                    value = "${settings.waterGoalMl}ml",
                    onClick = { dialog = DialogKind.WATER_GOAL }))
                add(SettingEntry("热量与营养", s("食谱偏好"), Icons.Filled.Favorite, SettingsIconCoral,
                    onClick = { onOpenFoodPreference() }))

                // 3 记录与识别：3 个旧开关/选项收进一个「识别偏好」
                add(SettingEntry("记录与识别", s("识别偏好"), Icons.Outlined.PhotoCamera, SettingsIconPurple,
                    onClick = { dialog = DialogKind.RECOGNITION }))

                // 4 提醒：保持单入口，4 类提醒 + 总开关都在原弹层里
                add(SettingEntry("提醒", s("通知与提醒"), Icons.Filled.Notifications, SettingsIconPurple,
                    onClick = { dialog = DialogKind.REMINDERS }))

                // 5 通用（界面语言文档没列，但功能在，保留入口）
                add(SettingEntry("通用", s("界面语言"), Icons.Filled.Language, SettingsIconBlue,
                    value = if (settings.uiLanguage == "en") "English" else "简体中文",
                    onClick = { dialog = DialogKind.UI_LANG }))
                add(SettingEntry("通用", s("记录时区"), Icons.Filled.Schedule, SettingsIconCyan,
                    value = timezoneLabel(settings.timeZone),
                    onClick = { dialog = DialogKind.TIMEZONE }))
                add(SettingEntry("通用", s("体重单位"), Icons.Outlined.MonitorWeight, SettingsIconTeal,
                    value = if (settings.unit == "jin") s("斤") else "kg",
                    onClick = { dialog = DialogKind.UNIT }))

                // 6 显示与外观：主题弹层里现成的 3 组开关拆成独立行，setter 完全复用
                add(SettingEntry("显示与外观", s("主题设置"), Icons.Filled.Palette, SettingsIconIndigo,
                    value = com.qingji.app.ui.theme.AccentThemes.byKey(settings.themeColor).label,
                    onClick = { dialog = DialogKind.THEME }))
                add(SettingEntry("显示与外观", s("应用图标"), Icons.Filled.Apps, SettingsIconPurple,
                    value = com.qingji.app.domain.AppIconManager.label(settings.iconVariant),
                    onClick = { dialog = DialogKind.ICON }))
                add(SettingEntry("显示与外观", s("桌面小组件"), Icons.Filled.Widgets, SettingsIconOrange,
                    value = s("3 个"), onClick = { onOpenWidget() }))
                add(SettingEntry("显示与外观", s("首页快捷入口"), Icons.Filled.Bolt, SettingsIconBlue,
                    switchState = settings.showQuickButtons,
                    onSwitch = { vm.setShowQuickButtons(it) }))
                add(SettingEntry("显示与外观", s("底部导航"), Icons.Filled.Home, SettingsIconGreen,
                    value = visibleTabsLabel(settings),
                    onClick = { dialog = DialogKind.BOTTOM_TABS }))
                add(SettingEntry("显示与外观", s("首页显示设置"), Icons.Filled.Dashboard, SettingsIconPink,
                    onClick = { dialog = DialogKind.HOME_DISPLAY }))
                add(SettingEntry("显示与外观", s("进度条特效"), Icons.AutoMirrored.Filled.ShowChart, SettingsIconCoral,
                    switchState = settings.progressAnimation,
                    onSwitch = { vm.setProgressAnimation(it) }))

                // 7 设备与数据
                add(SettingEntry("设备与数据", s("AI 服务配置"), Icons.Outlined.AutoAwesome, SettingsIconCyan,
                    onClick = { onOpenAiConfig() }))
                add(SettingEntry("设备与数据", s("数据导入与导出"), Icons.Filled.ImportExport, SettingsIconBlue,
                    onClick = { onOpenBackup() }))
                add(SettingEntry("设备与数据", s("缓存管理"), Icons.Filled.CleaningServices, SettingsIconRed,
                    value = backup.sizeText(cacheSize),
                    onClick = { dialog = DialogKind.CACHE }))

                // 8 关于
                add(SettingEntry("关于", s("换机与数据迁移"), Icons.Filled.SwapHoriz, SettingsIconBlueLight,
                    onClick = { dialog = DialogKind.PLATFORM }))
                add(SettingEntry("关于", s("关于我们"), Icons.Filled.Info, SettingsIconPink,
                    onClick = { dialog = DialogKind.ABOUT }))
                add(SettingEntry("关于", s("版本信息"), Icons.Outlined.Code, SettingsIconGray,
                    onClick = { dialog = DialogKind.VERSION }))
            }

            // 搜索：只按标题过滤；分组顺序由 groupBy 的插入序保证
            val visibleEntries = if (query.isBlank()) entries else entries.filter {
                it.title.contains(query.trim(), ignoreCase = true)
            }

            if (visibleEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "没有找到相关设置", fontSize = 13.sp, color = TextSecondary)
                }
            } else {
                visibleEntries.groupBy { it.group }.forEach { (group, rows) ->
                    GroupTitle(group)
                    SettingsCard {
                        rows.forEachIndexed { index, row ->
                            if (row.switchState != null) {
                                SettingIconSwitchRow(
                                    title = row.title,
                                    icon = row.icon,
                                    iconColor = row.iconColor,
                                    checked = row.switchState,
                                    onCheckedChange = { row.onSwitch?.invoke(it) }
                                )
                            } else {
                                SettingIconRow(
                                    title = row.title,
                                    icon = row.icon,
                                    iconColor = row.iconColor,
                                    value = row.value,
                                    onClick = { row.onClick?.invoke() }
                                )
                            }
                            if (index < rows.lastIndex) RowDivider()
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    // ============================================================
    // 全部设置二级项：底部浮起的操作面板（和轻断食设置同款）
    // 页面内部用 SettingsDetailSheet 渲染 ModalBottomSheet，
    // 是窗口级弹层，叠在设置列表之上；列表不销毁，返回后滚动位置不丢。
    // 内容多的项（主题 / 提醒 / 营养素）在弹层内部滚动，不撑满屏。
    // ============================================================
    if (dialog != null) {
        when (dialog!!) {
        DialogKind.RECOGNITION -> ToggleDetailPage(
            title = "识别偏好",
            subtitle = "",
            items = listOf(
                ToggleItem("自动识别餐次", "", settings.autoMeal) { vm.setAutoMeal(it) },
                ToggleItem("估算偏保守", "", settings.conservativeEstimate) {
                    vm.setConservativeEstimate(it)
                }
            ),
            extra = {
                DetailGroupTitle("AI 识别语言")
                listOf(
                    OptionItem("zh", "简体中文"),
                    OptionItem("auto", "跟随输入")
                ).forEach { option ->
                    val selected = option.key == settings.aiLanguage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.setAiLanguage(option.key) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option.label,
                            fontSize = 14.sp,
                            color = if (selected) QingJiGreenDark else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) Text(text = "✓", fontSize = 16.sp, color = QingJiGreen)
                    }
                }
            },
            onBack = { dialog = null },
            useSheet = true
        )

        DialogKind.HOME_DISPLAY -> ToggleDetailPage(
            title = "首页显示设置",
            subtitle = "",
            items = listOf(
                ToggleItem("AI 分析按钮", "", settings.showAiAnalysis) { vm.setShowAiAnalysis(it) },
                ToggleItem("饮水卡", "", settings.showWaterCard) { vm.setShowWaterCard(it) },
                ToggleItem("每周小结", "", settings.showWeeklySummary) { vm.setShowWeeklySummary(it) }
            ),
            onBack = { dialog = null },
            useSheet = true
        )

        DialogKind.MACRO -> MacroRatioPage(
            proteinInit = settings.proteinPct,
            carbInit = settings.carbPct,
            fatInit = settings.fatPct,
            fiberInit = vm.profile.collectAsState().value?.fiberGoalG ?: 25,
            sodiumInit = vm.profile.collectAsState().value?.sodiumGoalMg ?: 2000,
            calciumInit = vm.profile.collectAsState().value?.calciumGoalMg ?: 800,
            onSave = { p, c, f -> vm.setMacroRatio(p, c, f) },
            onSaveGoals = { fiber, sodium, calcium -> vm.setNutrientGoals(fiber, sodium, calcium) },
            onBack = { dialog = null },
            useSheet = true
        )

        DialogKind.EXERCISE_PCT -> ExercisePctDetailPage(
            pct = settings.exerciseCompensationPct,
            onSelect = { vm.setExerciseCompensationPct(it) },
            onBack = { dialog = null },
            useSheet = true
        )

        DialogKind.TIMEZONE -> OptionDetailPage(
            title = "记录时区",
            subtitle = "改这里会影响「今天」从几点开始算，跨时区出差时用得上",
            options = timezoneOptions().map { OptionItem(it.first, it.second) },
            selectedKey = settings.timeZone,
            onPick = { vm.setTimezone(it); dialog = null },
            onBack = { dialog = null },
            useSheet = true
        )

        DialogKind.REMINDERS -> ReminderPage(
            settings = settings,
            allDisabled = settings.allNotificationsDisabled,
            onSetAllDisabled = { vm.setAllNotificationsDisabled(it) },
            onSetWeighReminder = { weekday, hour, minute -> vm.setWeighWeekday(weekday, hour, minute) },
            onSetFastingSwitch = { on ->
                // 只在开启时需要通知权限；关闭提醒不弹系统授权框
                if (on) enableReminderWithPermission { vm.setFastingReminder(true) }
                else vm.setFastingReminder(false)
            },
            onSetFastingTime = { hour, minute ->
                vm.setFastingReminder(settings.fastingReminder, hour, minute, showToast = false)
            },
            onSetKcalSwitch = { on ->
                if (on) enableReminderWithPermission { vm.setKcalReminder(true) }
                else vm.setKcalReminder(false)
            },
            onSetKcalTime = { hour, minute ->
                vm.setKcalReminder(settings.kcalReminder, hour, minute, showToast = false)
            },
            onSetWaterSwitch = { on ->
                if (on) enableReminderWithPermission { vm.setWaterReminder(true) }
                else vm.setWaterReminder(false)
            },
            onSetWaterTime = { hour, minute ->
                vm.setWaterReminder(settings.waterReminder, hour, minute, showToast = false)
            },
            onBack = { dialog = null },
            useSheet = true
        )

        DialogKind.THEME -> ThemeSettingsPage(
            settings = settings,
            onSetTheme = { vm.setTheme(it) },
            onSetThemeColor = { vm.setThemeColor(it) },
            onSetBubbles = { vm.setWaterBubbles(it) },
            onSetMealMacro = { vm.setShowMealMacro(it) },
            onSetAiAnalysis = { vm.setShowAiAnalysis(it) },
            onSetWaterCard = { vm.setShowWaterCard(it) },
            onSetQuickButtons = { vm.setShowQuickButtons(it) },
            onSetProgressAnimation = { vm.setProgressAnimation(it) },
            onSetWeeklySummary = { vm.setShowWeeklySummary(it) },
            onSetRefreshRate = { vm.setShowRefreshRate(it) },
            onSetFont = { vm.setCustomFontPath(it) },
            onBack = { dialog = null },
            useSheet = true
        )

        DialogKind.AI_LANG -> OptionDetailPage(
            title = s("AI 识别语言"),
            subtitle = s("默认强制简体中文——照片里的外国菜也会翻译成中文菜名"),
            options = listOf(
                OptionItem("zh", s("简体中文"), s("推荐：所有识别结果都是中文")),
                OptionItem("auto", s("跟随输入"), s("AI 模型自行判断，可能返回英文"))
            ),
            selectedKey = settings.aiLanguage,
            onPick = { vm.setAiLanguage(it); dialog = null },
            onBack = { dialog = null },
            useSheet = true
        )

        DialogKind.UI_LANG -> OptionDetailPage(
            title = s("界面语言"),
            subtitle = s("切换后立即生效，未翻译的条目仍显示中文"),
            options = listOf(
                OptionItem("zh", "简体中文", s("界面默认中文，核心页可切英文")),
                OptionItem("en", "English", s("Core screens in English; the rest stays Chinese"))
            ),
            selectedKey = settings.uiLanguage,
            onPick = { vm.setUiLanguage(it); dialog = null },
            onBack = { dialog = null },
            useSheet = true
        )

        DialogKind.UNIT -> OptionDetailPage(
            title = "体重单位",
            subtitle = "只改显示，数据库里永远存公斤",
            options = listOf(
                OptionItem("kg", "公斤（kg）"),
                OptionItem("jin", "斤（500g）")
            ),
            selectedKey = settings.unit,
            onPick = { vm.setUnit(it); dialog = null },
            onBack = { dialog = null },
            useSheet = true
        )

        DialogKind.WATER_GOAL -> NumberDetailPage(
            title = "饮水目标",
            subtitle = "首页水杯按这个目标灌满",
            label = "每天计划喝多少",
            initial = settings.waterGoalMl.toString(),
            suffix = "ml",
            hint = "常见范围 1500～3000 毫升",
            onSave = { vm.setWaterGoal(it.toIntOrNull() ?: 2000) },
            onBack = { dialog = null },
            useSheet = true
        )

        DialogKind.BOTTOM_TABS -> ToggleDetailPage(
            title = "底部导航",
            subtitle = "关掉的 Tab 不会显示在底部，首页永远保留",
            items = listOf(
                ToggleItem("进展", "体重曲线、围度、饮水、运动", settings.showProgressTab) {
                    vm.setTabVisible("progress", it)
                },
                ToggleItem("日记", "写日记与打卡记录", settings.showDiaryTab) {
                    vm.setTabVisible("diary", it)
                },
                ToggleItem("我的", "个人资料、成就、设置入口", settings.showMineTab) {
                    vm.setTabVisible("mine", it)
                }
            ),
            onBack = { dialog = null },
            useSheet = true
        )

        DialogKind.PRIVACY -> PrivacyPage(
            onClearRecords = { vm.clearRecords() },
            onFactoryReset = { vm.factoryReset() },
            onBack = { dialog = null },
            useSheet = true
        )

        DialogKind.ICON -> com.qingji.app.ui.screen.settings.AppIconSettingsPage(
            currentVariant = settings.iconVariant,
            onSelect = { vm.setIconVariant(it) },
            onBack = { dialog = null },
            useSheet = true
        )

        DialogKind.CACHE -> InfoDetailPage(
            title = "清空缓存",
            body = "会删掉拍照时的临时图片，当前占用 ${backup.sizeText(cacheSize)}。" +
                "已经保存到饮食记录和日记里的照片不受影响。",
            actionText = "清理缓存",
            onAction = {
                backup.clearCache()
                cacheSize = backup.cacheSize()
                Toast.makeText(context, "已清理", Toast.LENGTH_SHORT).show()
            },
            onBack = { dialog = null },
            useSheet = true
        )

        DialogKind.PLATFORM -> InfoDetailPage(
            title = "换机与数据迁移",
            body = "轻记的数据全部存在本机，不上云、不需要账号。换手机两步：\n" +
                "1. 旧手机：设置 → 数据导入与导出 → 导出全量 JSON → 用系统分享发到微信 / 网盘；\n" +
                "2. 新手机：装好轻记 → 同一页面 → 导入备份 → 选中那个 JSON 文件。\n\n" +
                "注意三点：\n" +
                "· 备份是本地 JSON 文件，目前没有云端同步；\n" +
                "· API Key 不进备份（安全考虑），换机后要重新配置 AI 服务；\n" +
                "· 饮食记录和日记里引用的照片文件不在 JSON 里，换机后这些位置会显示占位图。",
            onBack = { dialog = null },
            useSheet = true
        )

        DialogKind.VERSION -> InfoDetailPage(
            title = "版本信息",
            body = "轻记 v${BuildConfig.VERSION_NAME}（versionCode ${BuildConfig.VERSION_CODE}）\n" +
                "最低支持 Android 8.0，目标 Android 15\n\n" +
                "数据库：Room 本地存储\n" +
                "AI：OpenAI 兼容协议，直连服务商\n" +
                "小组件：Jetpack Glance 1.1.1\n\n" +
                "当前用户：${settings.nickname}" +
                "（档案：${profile?.heightCm?.toInt() ?: 0}cm / 目标 ${profile?.targetWeight ?: 0.0}kg）" +
                // 更新提示里弹过的内容，在这里长期留档，随时能回看
                "\n\n" + com.qingji.app.domain.AppChangeLog.versionInfoText(),
            onBack = { dialog = null },
            useSheet = true
        )

        DialogKind.ABOUT -> AboutScreen(onBack = { dialog = null }, useSheet = true)


            else -> Unit
        }
    }
}

// ============================================================
// 几个组合弹窗
// ============================================================


private fun requestPinWidget(context: Context, receiver: Class<*>) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        Toast.makeText(context, "系统版本太低，请长按桌面手动添加", Toast.LENGTH_SHORT).show()
        return
    }
    val manager = context.getSystemService(AppWidgetManager::class.java)
    if (manager == null || !manager.isRequestPinAppWidgetSupported) {
        Toast.makeText(context, "这台手机不支持一键添加，请长按桌面手动添加", Toast.LENGTH_LONG).show()
        return
    }
    val provider = ComponentName(context, receiver)
    manager.requestPinAppWidget(provider, null, null)
}

// ============================================================
// 文案小工具
// ============================================================

private fun weekdayLabel(day: Int): String = when (day) {
    1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
    5 -> "周五"; 6 -> "周六"; else -> "周日"
}

private fun themeLabel(mode: String): String = when (mode) {
    "light" -> "浅色"
    "dark" -> "深色"
    else -> "跟随系统"
}

private fun timezoneLabel(zone: String): String = when (zone) {
    "Asia/Shanghai" -> "北京时间"
    "Asia/Tokyo" -> "东京"
    "Asia/Singapore" -> "新加坡"
    "Europe/London" -> "伦敦"
    "America/New_York" -> "纽约"
    "UTC" -> "UTC"
    else -> zone
}

private fun timezoneOptions(): List<Pair<String, String>> = listOf(
    "Asia/Shanghai" to "北京时间（UTC+8）",
    "Asia/Tokyo" to "东京（UTC+9）",
    "Asia/Singapore" to "新加坡（UTC+8）",
    "Europe/London" to "伦敦（UTC+0/+1）",
    "America/New_York" to "纽约（UTC-5/-4）",
    "UTC" to "协调世界时 UTC"
)

private fun visibleTabsLabel(settings: QingJiSettings): String {
    val list = buildList {
        add("首页")
        if (settings.showProgressTab) add("进展")
        if (settings.showDiaryTab) add("日记")
        if (settings.showMineTab) add("我的")
    }
    return list.joinToString("/")
}


@Composable
private fun MacroRatioPage(
    proteinInit: Int,
    carbInit: Int,
    fatInit: Int,
    /** v3.5.0：膳食纤维 / 钠 / 钙三个每日目标，存档案表 */
    fiberInit: Int,
    sodiumInit: Int,
    calciumInit: Int,
    onSave: (Int, Int, Int) -> Unit,
    onSaveGoals: (fiberGoalG: Int, sodiumGoalMg: Int, calciumGoalMg: Int) -> Unit,
    onBack: () -> Unit,
    useSheet: Boolean = false
) {
    var protein by remember { mutableStateOf(proteinInit.toString()) }
    var carb by remember { mutableStateOf(carbInit.toString()) }
    var fat by remember { mutableStateOf(fatInit.toString()) }
    var fiber by remember { mutableStateOf(fiberInit.toString()) }
    var sodium by remember { mutableStateOf(sodiumInit.toString()) }
    var calcium by remember { mutableStateOf(calciumInit.toString()) }

    val sum = (protein.toIntOrNull() ?: 0) + (carb.toIntOrNull() ?: 0) + (fat.toIntOrNull() ?: 0)

    SettingsDetailScaffold(title = "营养素分配", onBack = onBack, useSheet = useSheet) {
        HintBox(text = "三项加起来要是 100%。改完会重新算首页和进展页的目标克数。")
        Spacer(Modifier.height(Dimens.Gap))

        SectionCard {
            MacroField("蛋白质", protein, "%") { protein = it }
            Spacer(Modifier.height(10.dp))
            MacroField("碳水", carb, "%") { carb = it }
            Spacer(Modifier.height(10.dp))
            MacroField("脂肪", fat, "%") { fat = it }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "当前合计 $sum%" + if (sum == 100) " ✓" else "（需要调整到 100）",
                fontSize = 12.sp,
                color = if (sum == 100) QingJiGreen else Color(0xFFD64545)
            )
        }

        Spacer(Modifier.height(Dimens.Gap))

        // AI 专项：核心营养口径统一为热量 + 三大营养素，
        // 移除膳食纤维 / 钠 / 钙的每日目标录入入口。
        // 三个值仍随 onSaveGoals 按默认值写回档案，备份 JSON 也照旧导出，
        // 数据库字段与旧数据一行都不动（Room 的 defaultValue 与迁移 SQL
        // 必须逐字一致，动注解会触发清库兜底）。

        Spacer(Modifier.height(Dimens.Gap))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.ItemCorner))
                .background(if (sum == 100) QingJiGreen else DividerColor)
                .clickable(enabled = sum == 100) {
                    onSave(
                        protein.toIntOrNull() ?: 0,
                        carb.toIntOrNull() ?: 0,
                        fat.toIntOrNull() ?: 0
                    )
                    onSaveGoals(
                        fiber.toIntOrNull() ?: 25,
                        sodium.toIntOrNull() ?: 2000,
                        calcium.toIntOrNull() ?: 800
                    )
                    onBack()
                }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "保存", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}

@Composable
private fun MacroField(label: String, value: String, unit: String = "%", onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            ),
            modifier = Modifier.width(96.dp)
        )
        Spacer(Modifier.size(6.dp))
        Text(text = unit, fontSize = 14.sp, color = TextSecondary)
    }
}

/** 主题色：五个色块，点一下全 App 变色。 */
@Composable
private fun ThemeColorPage(
    current: String,
    onPick: (String) -> Unit,
    onBack: () -> Unit
) {
    SettingsDetailScaffold(title = "主题色", onBack = onBack) {
        HintBox(text = "换主色后，按钮、进度条、三大营养素的配色会一起变。")
        Spacer(Modifier.height(Dimens.Gap))

        SectionCard {
            com.qingji.app.ui.theme.AccentThemes.ALL.forEachIndexed { index, spec ->
                val selected = spec.key == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(spec.key) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(spec.lightMain))
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = spec.label,
                        fontSize = 15.sp,
                        color = if (selected) QingJiGreenDark else TextPrimary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) Text(text = "✓", fontSize = 17.sp, color = QingJiGreen)
                }
                if (index < com.qingji.app.ui.theme.AccentThemes.ALL.lastIndex) RowDivider()
            }
        }
    }
}

/** 体重提醒：周几 + 几点。 */
@Composable
private fun WeighReminderPage(
    weekdayInit: Int,
    hourInit: Int,
    onSave: (Int, Int) -> Unit,
    onBack: () -> Unit
) {
    var weekday by remember { mutableStateOf(weekdayInit) }
    var hour by remember { mutableStateOf(hourInit) }
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")

    SettingsDetailScaffold(title = "称重提醒", onBack = onBack) {
        HintBox(text = "到点会发一条通知，提醒你上秤记一笔。")
        Spacer(Modifier.height(Dimens.Gap))

        SectionCard {
            Text(text = "每周哪天", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEachIndexed { index, label ->
                    val value = index + 1
                    val selected = weekday == value
                    Box(
                        modifier = Modifier
                            .size(36.dp)
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

            Spacer(Modifier.height(16.dp))
            Text(text = "几点提醒", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..23).chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { h ->
                            val selected = hour == h
                            Text(
                                text = "%02d".format(h),
                                fontSize = 12.sp,
                                color = if (selected) Color.White else TextSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) QingJiGreen else PageBackground)
                                    .clickable { hour = h }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Dimens.Gap))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.ItemCorner))
                .background(QingJiGreen)
                .pressable {
                    onSave(weekday, hour)
                    onBack()
                }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "保存", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}

/**
 * 隐私与安全：说明 + 两档清除（v1.0.6 拆开）。
 *
 * 以前这里只有一个「清空本地记录」，文案写着会删围度/运动/照片，
 * 实际代码只清了 6 张表，那三样根本没动。现在按「清多少」分成两档，
 * 每一档下面直接写清「删哪些、留哪些」，不让用户靠猜。
 */
@Composable
private fun PrivacyPage(
    onClearRecords: () -> Unit,
    onFactoryReset: () -> Unit,
    onBack: () -> Unit,
    useSheet: Boolean = false
) {
    var confirmRecords by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    SettingsDetailScaffold(title = "隐私与安全", onBack = onBack, useSheet = useSheet) {
        SectionCard {
            Text(
                text = "轻记没有账号、没有服务器、没有任何埋点统计：\n" +
                    "· 所有记录只存本机数据库，卸载 App 就一起没了；\n" +
                    "· API Key 用系统 Keystore 加密存，不进数据库、不进备份、不进日志；\n" +
                    "· 照片存在 App 私有目录，其它 App 读不到；\n" +
                    "· 只有你点「识别」时才联网，请求直接发给你填的服务商。",
                fontSize = 13.sp,
                color = TextPrimary,
                lineHeight = 21.sp
            )
        }

        Spacer(Modifier.height(Dimens.Gap))

        // ---------- 第一档：只清记录 ----------
        Text(
            text = "清除记录",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "删掉饮食、体重、饮水、日记、围度、进度照片、运动、断食记录和使用统计。\n" +
                "个人档案（身高体重目标）、自己录的食物、收藏、AI 配置和所有设置都保留 —— " +
                "适合「想重新开始记，但不想重新配置一遍」。",
            fontSize = 11.sp,
            color = TextSecondary,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(8.dp))
        DangerActionButton(text = "清除全部记录") { confirmRecords = true }

        Spacer(Modifier.height(20.dp))

        // ---------- 第二档：恢复出厂 ----------
        Text(
            text = "恢复出厂设置",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "在上面那一档的基础上，再删掉个人档案、目标、自己录的食物、收藏、" +
                "AI 配置、所有设置和 API Key。删完回到刚装好的状态，要重新填一次资料。\n" +
                "备份文件不会删（那是你导出来准备带走的东西）。",
            fontSize = 11.sp,
            color = TextSecondary,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(8.dp))
        DangerActionButton(text = "恢复出厂设置") { confirmReset = true }
    }

    if (confirmRecords) {
        com.qingji.app.ui.components.AppDialog(
            title = "确定清除全部记录？",
            confirmText = "确认清除",
            danger = true,
            onConfirm = {
                confirmRecords = false
                onClearRecords()
                onBack()
            },
            dismissText = "取消",
            onDismiss = { confirmRecords = false }
        ) {
            Text(
                text = "饮食、体重、饮水、日记、围度、照片、运动、断食、训练记录都会删掉，" +
                    "删了就找不回来。档案、目标、动作库和训练计划会留着。\n\n" +
                    "建议先去「数据备份与导出」导一份 JSON。",
                fontSize = 14.sp,
                color = TextPrimary,
                lineHeight = 21.sp
            )
        }
    }

    if (confirmReset) {
        com.qingji.app.ui.components.AppDialog(
            title = "确定恢复出厂设置？",
            confirmText = "确认恢复出厂",
            danger = true,
            onConfirm = {
                confirmReset = false
                onFactoryReset()
            },
            dismissText = "取消",
            onDismiss = { confirmReset = false }
        ) {
            Text(
                text = "这次是全部删掉：记录、档案、目标、自己录的食物、收藏、" +
                    "AI 配置、所有设置、API Key 都不留。\n\n" +
                    "删完会回到引导页，要重新填一次资料。这个操作不可撤销，" +
                    "现在先去导一份 JSON 备份还来得及。",
                fontSize = 14.sp,
                color = TextPrimary,
                lineHeight = 21.sp
            )
        }
    }
}


/** 一段文本偏好（食谱偏好这种）。 */
@Composable
private fun TextPreferencePage(
    title: String,
    subtitle: String,
    label: String,
    initial: String,
    onSave: (String) -> Unit,
    onBack: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    SettingsDetailScaffold(title = title, onBack = onBack) {
        if (subtitle.isNotBlank()) {
            HintBox(text = subtitle)
            Spacer(Modifier.height(Dimens.Gap))
        }

        SectionCard {
            Text(text = label, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(Dimens.Gap))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.ItemCorner))
                .background(QingJiGreen)
                .pressable {
                    onSave(text)
                    onBack()
                }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "保存", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}

// ============================================================
// 「主题与显示」详情页：原来散在四个弹窗里的外观设置全部归到这里。
// 顺序按「改得最频繁的放最上面」：主题色 > 模式 > 动效 > 首页模块。
// ============================================================
@Composable
private fun ThemeSettingsPage(
    settings: QingJiSettings,
    onSetTheme: (String) -> Unit,
    onSetThemeColor: (String) -> Unit,
    onSetBubbles: (Int) -> Unit,
    onSetMealMacro: (Boolean) -> Unit,
    onSetAiAnalysis: (Boolean) -> Unit,
    onSetWaterCard: (Boolean) -> Unit,
    onSetQuickButtons: (Boolean) -> Unit,
    onSetProgressAnimation: (Boolean) -> Unit,
    onSetWeeklySummary: (Boolean) -> Unit,
    onSetRefreshRate: (Boolean) -> Unit,
    onSetFont: (String) -> Unit,
    onBack: () -> Unit,
    useSheet: Boolean = false
) {
    SettingsDetailScaffold(title = "主题与显示", onBack = onBack, useSheet = useSheet) {
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

        // ---------- 主题模式 ----------
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

        // ---------- 动效与细节 ----------
        DetailGroupTitle("动效与细节")
        SectionCard(contentPadding = 0.dp) {
            SwitchRow(
                title = "进度条动效",
                subtitle = "关掉可以省一点点电",
                checked = settings.progressAnimation
            ) { onSetProgressAnimation(it) }
            RowDivider()
            SwitchRow(
                title = "本周小结",
                subtitle = "首页底部显示近 7 天的一句话小结（纯本地计算，不调 AI）",
                checked = settings.showWeeklySummary
            ) { onSetWeeklySummary(it) }
            RowDivider()
            SwitchRow(
                title = "每餐营养素比例",
                subtitle = "餐次卡片底部显示蛋白·碳水·脂肪比例条",
                checked = settings.showMealMacro
            ) { onSetMealMacro(it) }
            RowDivider()
            // 水杯气泡量
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = "水杯气泡量",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
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
            RowDivider()
            SwitchRow(
                title = "显示实时刷新率",
                subtitle = "首页右上角浮一个小徽章显示实测 FPS，平时关掉",
                checked = settings.showRefreshRate
            ) { onSetRefreshRate(it) }
        }

        Spacer(Modifier.height(Dimens.Gap))

        // ---------- 字体 ----------
        DetailGroupTitle("字体")
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
                        } else {
                            "已启用自定义字体"
                        },
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Text(
                    text = if (settings.customFontPath.isBlank()) "去设置" else "已启用",
                    fontSize = 13.sp,
                    color = QingJiGreenDark
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
                            .pressable {
                                onSetFont("")
                                showFontSheet = false
                            }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "恢复系统默认字体", fontSize = 14.sp, color = TextSecondary)
                    }
                }
            }
        }

        Spacer(Modifier.height(Dimens.Gap))

        // ---------- 首页模块 ----------
        DetailGroupTitle("首页模块")
        SectionCard(contentPadding = 0.dp) {
            SwitchRow(
                title = "AI 分析按钮",
                subtitle = "不配 AI 可以关掉",
                checked = settings.showAiAnalysis
            ) { onSetAiAnalysis(it) }
            RowDivider()
            SwitchRow(
                title = "饮水卡",
                subtitle = "不记喝水可以关掉",
                checked = settings.showWaterCard
            ) { onSetWaterCard(it) }
            RowDivider()
            SwitchRow(
                title = "悬浮快捷按钮",
                subtitle = "首页右下角三个圆按钮（喝水/体重/拍照）",
                checked = settings.showQuickButtons
            ) { onSetQuickButtons(it) }
        }

    }
}

// ============================================================
// 「通知与提醒」详情页：称重 / 断食 / 热量 / 喝水四类提醒集中在一页。
// 每类都是「开关 + 时间」在一起，不用再跳来跳去。
// ============================================================
@Composable
private fun ReminderPage(
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekdayLabels.forEachIndexed { index, label ->
                        val value = index + 1
                        val selected = weekday == value
                        Box(
                            modifier = Modifier
                                .size(36.dp)
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
private fun ExercisePctDetailPage(
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
