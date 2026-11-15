package com.qingji.app.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dock
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
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
import com.qingji.app.ui.screen.settings.GroupTitle
import com.qingji.app.ui.screen.settings.RowDivider
import com.qingji.app.ui.screen.settings.SettingIconRow
import com.qingji.app.ui.screen.settings.SettingIconSwitchRow
import com.qingji.app.ui.screen.settings.SettingsSearchBox
import com.qingji.app.ui.screen.settings.SettingsCard
import com.qingji.app.ui.screen.settings.ToggleItem
import com.qingji.app.ui.screen.settings.ToggleDetailPage
import com.qingji.app.ui.screen.settings.OptionItem
import com.qingji.app.ui.screen.settings.OptionDetailPage
import com.qingji.app.ui.screen.settings.NumberDetailPage
import com.qingji.app.ui.screen.settings.InfoDetailPage
import com.qingji.app.ui.screen.settings.DetailGroupTitle
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.SettingsViewModel

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
private val SettingsIconRed = Color(0xFFE06A6A)
private val SettingsIconGray = Color(0xFF8A93A6)
// 深色模式自适应：原来写死 #F5F5F9，深色下整页还是浅灰，和深色卡片打架
private val SettingsPageBg: Color get() = com.qingji.app.ui.theme.PageBackground

/** 设置首页一行：分组 / 标题 / 图标 / 开关 / 点击动作；首页不再携带状态文字。 */
private data class SettingEntry(
    val group: String,
    val title: String,
    val icon: ImageVector,
    val iconColor: Color,
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
            // v1.0.8：7 个分组、27 个真实功能项。文档里「设备绑定 / 导出日志」
            // 这类代码里不存在的功能一律不做，避免出现点了没反应的假菜单。
            val entries: List<SettingEntry> = buildList {
                // 1 账号与安全（App 无账号，保留「隐私与安全」原名，不叫账号安全）
                add(SettingEntry("账号与安全", s("个人资料"), Icons.Filled.Person, SettingsIconBlue,
                    onClick = { onOpenProfile() }))
                add(SettingEntry("账号与安全", s("隐私与安全"), Icons.Filled.Lock, SettingsIconGreen,
                    onClick = { dialog = DialogKind.PRIVACY }))

                // 2 热量与营养
                add(SettingEntry("热量与营养", s("营养素分配方案"), Icons.Outlined.Restaurant, SettingsIconGreen,
                    onClick = { dialog = DialogKind.MACRO }))
                add(SettingEntry("热量与营养", s("运动消耗补偿"), Icons.Filled.LocalFireDepartment, SettingsIconOrange,
                    onClick = { dialog = DialogKind.EXERCISE_PCT }))
                add(SettingEntry("热量与营养", s("饮水目标"), Icons.Outlined.WaterDrop, SettingsIconBlueLight,
                    onClick = { dialog = DialogKind.WATER_GOAL }))
                add(SettingEntry("热量与营养", s("食谱偏好"), Icons.Filled.Favorite, SettingsIconCoral,
                    onClick = { onOpenFoodPreference() }))

                // 3 通用（v8：记录与识别、提醒并入通用——识别偏好、通知与提醒排最前）
                add(SettingEntry("通用", s("识别偏好"), Icons.Filled.DocumentScanner, SettingsIconPurple,
                    onClick = { dialog = DialogKind.RECOGNITION }))
                add(SettingEntry("通用", s("通知与提醒"), Icons.Filled.Notifications, SettingsIconPurple,
                    onClick = { dialog = DialogKind.REMINDERS }))

                // 界面语言文档没列，但功能在，保留入口
                add(SettingEntry("通用", s("界面语言"), Icons.Filled.Language, SettingsIconBlue,
                    onClick = { dialog = DialogKind.UI_LANG }))
                add(SettingEntry("通用", s("记录时区"), Icons.Filled.Schedule, SettingsIconCyan,
                    onClick = { dialog = DialogKind.TIMEZONE }))
                add(SettingEntry("通用", s("体重单位"), Icons.Outlined.MonitorWeight, SettingsIconGreen,
                    onClick = { dialog = DialogKind.UNIT }))

                // 6 显示与外观：主题弹层里现成的 3 组开关拆成独立行，setter 完全复用
                add(SettingEntry("显示与外观", s("主题设置"), Icons.Filled.Palette, SettingsIconPurple,
                    onClick = { dialog = DialogKind.THEME }))
                // barfx：悬浮底栏开关，紧跟在主题设置下面（主人 2026-11-14 指定）
                add(SettingEntry("显示与外观", s("悬浮底栏"), Icons.Filled.Dock, SettingsIconPurple,
                    switchState = settings.floatingBottomBar,
                    onSwitch = { vm.setFloatingBottomBar(it) }))
                add(SettingEntry("显示与外观", s("应用图标"), Icons.Filled.Apps, SettingsIconPurple,
                    onClick = { dialog = DialogKind.ICON }))
                add(SettingEntry("显示与外观", s("桌面小组件"), Icons.Filled.Widgets, SettingsIconOrange,
                    onClick = { onOpenWidget() }))
                add(SettingEntry("显示与外观", s("首页快捷入口"), Icons.Filled.Bolt, SettingsIconBlue,
                    switchState = settings.showQuickButtons,
                    onSwitch = { vm.setShowQuickButtons(it) }))
                add(SettingEntry("显示与外观", s("底部导航"), Icons.Filled.Home, SettingsIconGreen,
                    onClick = { dialog = DialogKind.BOTTOM_TABS }))
                add(SettingEntry("显示与外观", s("首页显示设置"), Icons.Filled.Dashboard, SettingsIconPink,
                    onClick = { dialog = DialogKind.HOME_DISPLAY }))
                add(SettingEntry("显示与外观", s("进度条特效"), Icons.AutoMirrored.Filled.ShowChart, SettingsIconOrange,
                    switchState = settings.progressAnimation,
                    onSwitch = { vm.setProgressAnimation(it) }))

                // 7 设备与数据
                add(SettingEntry("设备与数据", s("AI 服务配置"), Icons.Outlined.AutoAwesome, SettingsIconCyan,
                    onClick = { onOpenAiConfig() }))
                add(SettingEntry("设备与数据", s("数据导入与导出"), Icons.Filled.ImportExport, SettingsIconBlue,
                    onClick = { onOpenBackup() }))
                add(SettingEntry("设备与数据", s("缓存管理"), Icons.Filled.Storage, SettingsIconRed,
                    onClick = { dialog = DialogKind.CACHE }))

                // 9 页面交互（UI-NAV-006）
                add(SettingEntry("页面交互", s("左右滑动切换页面"), Icons.Filled.Swipe, SettingsIconBlue,
                    switchState = settings.swipeNavigate,
                    onSwitch = { vm.setSwipeNavigate(it) }))
                add(SettingEntry("页面交互", s("页面切换动画"), Icons.Filled.Animation, SettingsIconPurple,
                    switchState = settings.pageTransition,
                    onSwitch = { vm.setPageTransition(it) }))

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
                "1. 旧手机：设置 → 数据导入与导出 → 导出记录类 JSON → 用系统分享发到微信 / 网盘；\n" +
                "2. 新手机：装好轻记 → 同一页面 → 导入备份 → 选中那个 JSON 文件。\n\n" +
                "注意几点：\n" +
                "· 备份是本地 JSON 文件，目前没有云端同步；\n" +
                "· 导出的是记录类数据：68 个设置项、食物搜索历史、照片文件本身、AI Key 都不在备份里；\n" +
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
