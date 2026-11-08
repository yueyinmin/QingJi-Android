package com.qingji.app.ui.screen

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import com.qingji.app.ui.screen.settings.SettingValueRow
import com.qingji.app.ui.screen.settings.SettingsCard
import com.qingji.app.ui.screen.settings.SettingsNote
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
import com.qingji.app.ui.theme.PageBackground
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
 * 用弹窗而不是二级页：这些项都只有一两个值，弹窗少按一次返回。
 * 只有「个人资料 / AI 服务配置 / 数据备份」是真正的独立页面。
 * ============================================================
 */

/** 当前打开的是哪个弹窗。 */
private enum class DialogKind {
    MACRO, WATER_GOAL, TIMEZONE, UNIT, THEME, REMINDERS,
    BOTTOM_TABS, AI_LANG, PRIVACY, ICON, CACHE,
    ABOUT, PLATFORM, VERSION
}

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

    // 安卓 13 以上要通知权限，否则提醒到点不会弹
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "没给通知权限，提醒不会弹出来", Toast.LENGTH_LONG).show()
        }
    }

    fun askNotificationThen(action: () -> Unit) {
        val needAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needAsk) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        action()
    }

    // 滚动状态放在最外层：进详情页再退回来，列表停在原来的位置，
    // 不会一退回去就跳到顶部（之前的问题就在这——列表被销毁重建了）。
    val listScroll = rememberScrollState()
    val showDetail = dialog != null
    androidx.activity.compose.BackHandler(enabled = showDetail) { dialog = null }

    if (!showDetail) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(listScroll)
                .padding(horizontal = 16.dp)
        ) {
            // ================= 账号与安全 =================
            GroupTitle("账号与安全")
            SettingsCard {
                SettingValueRow(
                    title = "个人资料",
                    subtitle = "昵称、性别、生日、身高",
                    value = settings.nickname,
                    iconColor = QingJiGreen
                ) { onOpenProfile() }
                RowDivider()
                SettingValueRow(
                    title = "隐私与安全",
                    subtitle = "数据存在哪、怎么彻底清掉",
                    value = "",
                    iconColor = QingJiGreen
                ) { dialog = DialogKind.PRIVACY }
            }

            // ================= 热量与营养 =================
            GroupTitle("热量与营养")
            SettingsCard {
                SettingValueRow(
                    title = "饮食偏好与忌口",
                    subtitle = "过敏原、不吃的东西、烹饪时长、菜系",
                    value = "去设置",
                    iconColor = QingJiGreen
                ) { onOpenFoodPreference() }
                RowDivider()
                SettingValueRow(
                    title = "营养素分配方案",
                    subtitle = "蛋白 / 碳水 / 脂肪 比例",
                    value = "${settings.proteinPct}/${settings.carbPct}/${settings.fatPct}",
                    iconColor = QingJiGreen
                ) { dialog = DialogKind.MACRO }
                RowDivider()
                SwitchRow(
                    title = "运动消耗补偿",
                    subtitle = "开启后把运动消耗加回当天预算",
                    checked = settings.exerciseCompensation
                ) { vm.setExerciseCompensation(it) }
                RowDivider()
                SettingValueRow(
                    title = "饮水目标",
                    subtitle = "首页和小组件都按它算进度",
                    value = "${settings.waterGoalMl}ml",
                    iconColor = QingJiGreenDark
                ) { dialog = DialogKind.WATER_GOAL }
            }
            SettingsNote(
                "「运动消耗补偿」这个开关会记住你的选择，但一期还没有运动记录模块，" +
                    "所以暂时没有消耗数据可以加回预算 —— 等运动模块做出来它会自动生效，" +
                    "现在打开它不会影响每天的预算数字。"
            )

            // ================= 记录与识别 =================
            GroupTitle("记录与识别")
            SettingsCard {
                SwitchRow(
                    title = "自动识别餐次",
                    subtitle = "关掉后每次都要自己选早/午/晚",
                    checked = settings.autoMeal
                ) { vm.setAutoMeal(it) }
                RowDivider()
                SwitchRow(
                    title = "估算偏保守",
                    subtitle = "AI 估算份量时宁可给少一点",
                    checked = settings.conservativeEstimate
                ) { vm.setConservativeEstimate(it) }
                SettingsNote(
                    "AI 的份量估算误差通常有 20%～40%，中餐尤其明显。" +
                        "所以每次识别完都会让你确认和微调，这是故意的，不是多此一举。"
                )
                RowDivider()
                SettingValueRow(
                    title = "AI 识别语言",
                    subtitle = "识别结果返回的语言",
                    value = if (settings.aiLanguage == "auto") "跟随输入" else "简体中文",
                    iconColor = QingJiGreen.copy(alpha = 0.72f)
                ) { dialog = DialogKind.AI_LANG }
            }

            // ================= 提醒 =================
            GroupTitle("提醒")
            SettingsCard {
                SettingValueRow(
                    title = "通知与提醒",
                    subtitle = "称重、断食、热量三类提醒的开关与时间",
                    value = "去设置",
                    iconColor = QingJiGreen
                ) { dialog = DialogKind.REMINDERS }
            }
            SettingsNote(
                "提醒靠系统闹钟实现，误差几分钟属正常（系统省电策略所致）。" +
                    "小米 / 华为 / OPPO 建议把轻记加进自启动白名单，否则重启后可能不再提醒。"
            )

            // ================= 通用 =================
            GroupTitle("通用")
            SettingsCard {
                SettingValueRow(
                    title = "记录时区",
                    subtitle = "记录按哪个时区的日期归档",
                    value = timezoneLabel(settings.timeZone),
                    iconColor = QingJiGreenDark
                ) { dialog = DialogKind.TIMEZONE }
                RowDivider()
                SettingValueRow(
                    title = "体重单位",
                    subtitle = "数据库统一存公斤，这里只改显示",
                    value = if (settings.unit == "jin") "斤" else "kg",
                    iconColor = QingJiGreen
                ) { dialog = DialogKind.UNIT }
            }

            // ================= 显示与外观 =================
            GroupTitle("显示与外观")
            SettingsCard {
                SettingValueRow(
                    title = "主题与显示",
                    subtitle = "主题色、动效、水杯气泡、首页模块开关",
                    value = com.qingji.app.ui.theme.AccentThemes.byKey(settings.themeColor).label,
                    iconColor = QingJiGreen.copy(alpha = 0.72f)
                ) { dialog = DialogKind.THEME }
                RowDivider()
                SettingValueRow(
                    title = "桌面小组件",
                    subtitle = "快捷记录 / 今日概览 / 体重闪记",
                    value = "3 个",
                    iconColor = QingJiGreen
                ) { onOpenWidget() }
                RowDivider()
                SettingValueRow(
                    title = "底部导航",
                    subtitle = "隐藏不常用的 Tab",
                    value = visibleTabsLabel(settings),
                    iconColor = QingJiGreenDark
                ) { dialog = DialogKind.BOTTOM_TABS }
            }

            // ================= 设备与数据 =================
            GroupTitle("设备与数据")
            SettingsCard {
                SettingValueRow(
                    title = "AI 服务配置",
                    subtitle = "填自己的 Base URL 与 API Key",
                    value = "",
                    iconColor = QingJiGreen.copy(alpha = 0.72f)
                ) { onOpenAiConfig() }
                RowDivider()
                SettingValueRow(
                    title = "数据备份与导出",
                    subtitle = "导出 JSON / CSV，导入恢复",
                    value = "",
                    iconColor = QingJiGreen
                ) { onOpenBackup() }
                RowDivider()
                SettingValueRow(
                    title = "缓存管理",
                    subtitle = "只清拍照临时图，不动已保存的照片",
                    value = backup.sizeText(cacheSize),
                    iconColor = TextSecondary
                ) { dialog = DialogKind.CACHE }
            }

            // ================= 关于 =================
            GroupTitle("关于")
            SettingsCard {
                SettingValueRow(
                    title = "其他平台",
                    subtitle = "换手机怎么把数据带走",
                    value = "",
                    iconColor = QingJiGreenDark
                ) { dialog = DialogKind.PLATFORM }
                RowDivider()
                SettingValueRow(
                    title = "关于我们",
                    subtitle = "这个 App 是谁做的、为什么做",
                    value = "",
                    iconColor = QingJiGreen.copy(alpha = 0.72f)
                ) { dialog = DialogKind.ABOUT }
                RowDivider()
                SettingValueRow(
                    title = "版本信息",
                    subtitle = "轻记 v${BuildConfig.VERSION_NAME}",
                    value = "",
                    iconColor = DividerColor
                ) { dialog = DialogKind.VERSION }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
    } else {
    // ============================================================
    // 二级页面集中在这里：整页替换列表，顶栏带返回，和列表同一套视觉
    //
    // 为什么不用 AnimatedContent 双页同渲染：切换瞬间新旧两页都要参与
    // 合成，设置列表又长，低端机上会掉帧（用户反馈的滑动卡顿主因）。
    // 现在改成详情页自己播一次「淡入 + 上浮」，同一时刻只有一页在渲染。
    // ============================================================
    val detailVisible = remember(dialog) {
        androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true }
    }
    androidx.compose.animation.AnimatedVisibility(
        visibleState = detailVisible,
        enter = androidx.compose.animation.fadeIn(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = com.qingji.app.ui.theme.Motion.Normal,
                easing = com.qingji.app.ui.theme.Motion.decelerate
            )
        ) + androidx.compose.animation.slideInVertically(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = com.qingji.app.ui.theme.Motion.Slow,
                easing = com.qingji.app.ui.theme.Motion.decelerate
            )
        ) { height -> height / 22 },
        exit = androidx.compose.animation.ExitTransition.None
    ) {
    val current = dialog!!
    when (current) {
        DialogKind.AI_LANG -> OptionDetailPage(
            title = "AI 识别语言",
            subtitle = "默认强制简体中文——照片里的外国菜也会翻译成中文菜名",
            options = listOf(
                OptionItem("zh", "简体中文", "推荐：所有识别结果都是中文"),
                OptionItem("auto", "跟随输入", "AI 模型自行判断，可能返回英文")
            ),
            selectedKey = settings.aiLanguage,
            onPick = { vm.setAiLanguage(it); dialog = null },
            onBack = { dialog = null }
        )

        DialogKind.REMINDERS -> ReminderPage(
            settings = settings,
            allDisabled = settings.allNotificationsDisabled,
            onSetAllDisabled = { vm.setAllNotificationsDisabled(it) },
            onSetWeighReminder = { weekday, hour -> vm.setWeighWeekday(weekday, hour) },
            onSetFastingSwitch = { on -> askNotificationThen { vm.setFastingReminder(on) } },
            onSetFastingHour = { hour -> vm.setFastingReminder(settings.fastingReminder, hour) },
            onSetKcalSwitch = { on -> askNotificationThen { vm.setKcalReminder(on) } },
            onSetKcalHour = { hour -> vm.setKcalReminder(settings.kcalReminder, hour) },
            onBack = { dialog = null }
        )

        DialogKind.MACRO -> MacroRatioPage(
            proteinInit = settings.proteinPct,
            carbInit = settings.carbPct,
            fatInit = settings.fatPct,
            onSave = { p, c, f -> vm.setMacroRatio(p, c, f) },
            onBack = { dialog = null }
        )

        DialogKind.WATER_GOAL -> NumberDetailPage(
            title = "饮水目标",
            subtitle = "首页水杯按这个目标灌满",
            label = "每天计划喝多少",
            initial = settings.waterGoalMl.toString(),
            suffix = "ml",
            hint = "常见范围 1500～3000 毫升",
            onSave = { vm.setWaterGoal(it.toIntOrNull() ?: 2000) },
            onBack = { dialog = null }
        )

        DialogKind.TIMEZONE -> OptionDetailPage(
            title = "记录时区",
            subtitle = "改这里会影响「今天」从几点开始算，跨时区出差时用得上",
            options = timezoneOptions().map { OptionItem(it.first, it.second) },
            selectedKey = settings.timeZone,
            onPick = { vm.setTimezone(it); dialog = null },
            onBack = { dialog = null }
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
            onBack = { dialog = null }
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
            onSetRefreshRate = { vm.setShowRefreshRate(it) },
            onSetFont = { vm.setCustomFontPath(it) },
            onBack = { dialog = null }
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
            onBack = { dialog = null }
        )

        DialogKind.PRIVACY -> PrivacyPage(
            onClearData = { vm.clearAllData() },
            onBack = { dialog = null }
        )

        DialogKind.ICON -> InfoDetailPage(
            title = "应用图标",
            body = "换图标需要同时打包多套图标资源（每套都要在清单里注册一个 activity-alias），" +
                "而且切换后系统有可能把原来的桌面快捷方式当成「已失效」直接删掉。" +
                "\n\n所以这一条先空着，等主体功能稳了再补 —— 现在硬做，风险是图标直接消失。",
            onBack = { dialog = null }
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
            onBack = { dialog = null }
        )

        DialogKind.PLATFORM -> InfoDetailPage(
            title = "换手机怎么带走数据",
            body = "轻记的数据全部存在本机，不上云、不需要账号。换手机两步：\n" +
                "1. 旧手机：设置 → 数据备份与导出 → 导出全量 JSON → 用系统分享发到微信 / 网盘；\n" +
                "2. 新手机：装好轻记 → 同一页面 → 导入备份 → 选中那个 JSON 文件。\n\n" +
                "注意两点：\n" +
                "· API Key 不在备份里（安全考虑），新手机要重新填一次；\n" +
                "· 饮食记录和日记里引用的照片是文件路径，不会跟着 JSON 走，" +
                "要单独把 Android/data/com.qingji.app/files/images 拷过去。",
            onBack = { dialog = null }
        )

        DialogKind.ABOUT -> InfoDetailPage(
            title = "关于轻记",
            body = "轻记是一个「不用查表、不用称重，说一句或拍一张就记完」的私人减重账本。\n\n" +
                "设计上只做三件事：记得快、看得懂趋势、数据留在自己手机里。\n" +
                "不做社交、不做商城、不做医疗建议 —— 文案里也不出现「治疗 / 诊断 / 处方」这类词。\n\n" +
                "AI 能力用你自己的 API Key，花多少自己控；我们不中转请求，也不收集任何数据。",
            onBack = { dialog = null }
        )

        DialogKind.VERSION -> InfoDetailPage(
            title = "版本信息",
            body = "轻记 v${BuildConfig.VERSION_NAME}（versionCode ${BuildConfig.VERSION_CODE}）\n" +
                "最低支持 Android 8.0，目标 Android 15\n\n" +
                "数据库：Room 11 张表，本地存储\n" +
                "AI：OpenAI 兼容协议，直连服务商\n" +
                "小组件：Jetpack Glance 1.1.1\n\n" +
                "当前用户：${settings.nickname}" +
                "（档案：${profile?.heightCm?.toInt() ?: 0}cm / 目标 ${profile?.targetWeight ?: 0.0}kg）",
            onBack = { dialog = null }
        )

        else -> Unit
    }
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
    onSave: (Int, Int, Int) -> Unit,
    onBack: () -> Unit
) {
    var protein by remember { mutableStateOf(proteinInit.toString()) }
    var carb by remember { mutableStateOf(carbInit.toString()) }
    var fat by remember { mutableStateOf(fatInit.toString()) }

    val sum = (protein.toIntOrNull() ?: 0) + (carb.toIntOrNull() ?: 0) + (fat.toIntOrNull() ?: 0)

    SettingsDetailScaffold(title = "营养素分配", onBack = onBack) {
        HintBox(text = "三项加起来要是 100%。改完会重新算首页和进展页的目标克数。")
        Spacer(Modifier.height(Dimens.Gap))

        SectionCard {
            MacroField("蛋白质", protein) { protein = it }
            Spacer(Modifier.height(10.dp))
            MacroField("碳水", carb) { carb = it }
            Spacer(Modifier.height(10.dp))
            MacroField("脂肪", fat) { fat = it }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "当前合计 $sum%" + if (sum == 100) " ✓" else "（需要调整到 100）",
                fontSize = 12.sp,
                color = if (sum == 100) QingJiGreen else Color(0xFFD64545)
            )
        }

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
private fun MacroField(label: String, value: String, onChange: (String) -> Unit) {
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
        Text(text = "%", fontSize = 14.sp, color = TextSecondary)
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

/** 隐私与安全：说明 + 清空数据（二次确认）。 */
@Composable
private fun PrivacyPage(
    onClearData: () -> Unit,
    onBack: () -> Unit
) {
    var confirmStep by remember { mutableStateOf(false) }

    SettingsDetailScaffold(title = "隐私与安全", onBack = onBack) {
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
        DangerActionButton(text = "清空本地记录") { confirmStep = true }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "会删掉饮食、体重、饮水、日记、围度、运动、照片记录和统计；不会删设置和 API Key。",
            fontSize = 11.sp,
            color = TextSecondary
        )
    }

    if (confirmStep) {
        com.qingji.app.ui.components.AppDialog(
            title = "确定清空全部记录？",
            confirmText = "确认清空",
            danger = true,
            onConfirm = {
                confirmStep = false
                onClearData()
                onBack()
            },
            dismissText = "取消",
            onDismiss = { confirmStep = false }
        ) {
            Text(
                text = "这个操作不可撤销，删了就找不回来。建议先去「数据备份与导出」导一份 JSON。",
                fontSize = 14.sp,
                color = TextPrimary
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
    onSetRefreshRate: (Boolean) -> Unit,
    onSetFont: (String) -> Unit,
    onBack: () -> Unit
) {
    SettingsDetailScaffold(title = "主题与显示", onBack = onBack) {
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

        Spacer(Modifier.height(Dimens.Gap))
        HintBox(text = "应用图标暂不支持更换：需要打包多套图标且切换可能导致桌面快捷方式失效。")
    }
}

// ============================================================
// 「通知与提醒」详情页：称重 / 断食 / 热量三类提醒集中在一页。
// 每类都是「开关 + 时间」在一起，不用再跳来跳去。
// ============================================================
@Composable
private fun ReminderPage(
    settings: QingJiSettings,
    allDisabled: Boolean,
    onSetAllDisabled: (Boolean) -> Unit,
    onSetWeighReminder: (Int, Int) -> Unit,
    onSetFastingSwitch: (Boolean) -> Unit,
    onSetFastingHour: (Int) -> Unit,
    onSetKcalSwitch: (Boolean) -> Unit,
    onSetKcalHour: (Int) -> Unit,
    onBack: () -> Unit
) {
    // 称重提醒的星期和小时在页面里先选好，点底部保存一次性写入
    var weekday by remember { mutableStateOf(settings.weighWeekday) }
    var weighHour by remember { mutableStateOf(settings.reminderHour) }
    val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    SettingsDetailScaffold(title = "通知与提醒", onBack = onBack) {
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
                Text(text = "几点提醒（点下面的时间用轮盘调）", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                com.qingji.app.ui.components.WheelTimePicker(
                    initialHour = weighHour,
                    initialMinute = 0
                ) { h, _ -> weighHour = h }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.ItemCorner))
                .background(QingJiGreen)
                .pressable {
                    onSetWeighReminder(weekday, weighHour)
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
                Text(text = "提醒时间（轮盘调整，实时保存）", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                com.qingji.app.ui.components.WheelTimePicker(
                    initialHour = settings.fastStartHour,
                    initialMinute = 0
                ) { h, _ -> onSetFastingHour(h) }
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
                Text(text = "提醒时间（轮盘调整，实时保存）", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                com.qingji.app.ui.components.WheelTimePicker(
                    initialHour = settings.kcalReminderHour,
                    initialMinute = 0
                ) { h, _ -> onSetKcalHour(h) }
            }
        }

        Spacer(Modifier.height(Dimens.Gap))
        HintBox(
            text = "提醒靠系统闹钟实现，误差几分钟属正常。" +
                "小米 / 华为 / OPPO 建议把轻记加进自启动白名单，否则重启后可能不再提醒。"
        )
    }
}

