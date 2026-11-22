package com.qingji.app.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween

import android.Manifest
import androidx.compose.foundation.background
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.qingji.app.data.backup.BackupManager
import com.qingji.app.ui.screen.settings.GroupTitle
import com.qingji.app.ui.screen.settings.RowDivider
import com.qingji.app.ui.screen.settings.SettingIconRow
import com.qingji.app.ui.screen.settings.SettingsSearchBox
import com.qingji.app.ui.screen.settings.SettingsCard
import com.qingji.app.ui.screen.settings.ToggleItem
import com.qingji.app.ui.screen.settings.ToggleDetailPage
import com.qingji.app.ui.screen.settings.OptionItem
import com.qingji.app.ui.screen.settings.OptionDetailPage
import com.qingji.app.ui.screen.settings.NumberDetailPage
import com.qingji.app.ui.screen.settings.InfoDetailPage
import com.qingji.app.ui.screen.settings.DetailGroupTitle
import com.qingji.app.ui.screen.settings.SettingIconBadge
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.clickableNoRipple
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.SettingsViewModel

/**
 * ============================================================
 * 设置中心（v1.4.2 整合重构）
 * ------------------------------------------------------------
 * 本次是「入口重组」，不是删功能：
 * - 主页由 7 个一级分类收敛为 6 个（工单 P0-01）：
 *     01 个人与安全 / 02 饮食与健康 / 03 通用设置 /
 *     04 显示与交互 / 05 AI 与数据 / 06 关于轻记
 * - 主页只放这 6 张分类卡片；点进去才是具体设置项（二级弹层）。
 * - 「显示与交互」内再分 4 个二级分类：
 *     主题与外观 / 首页布局 / 导航设置 / 动效与性能
 * - 每个设置字段只有一个正式管理入口（工单 P0-03）。原主题大杂烩
 *   （ThemeSettingsPage）里重复的「进度条动效 / 首页显示开关」已拆走。
 * - 搜索升级为统一注册表：支持中文 / 模糊 / 二级项，结果显示所属分类，
 *   点一下直接打开对应设置页（工单 P1-01）。
 *
 * 所有二级项依旧用底部弹层（AppBottomSheet）：列表不销毁、返回位置不丢。
 * 复杂功能（个人资料 / 饮食偏好 / AI 配置 / 备份 / 小组件）保留独立页面。
 * ============================================================
 */

// ---------- 设置页专用配色（只在设置首页用，不改全局主题） ----------
private val SettingsIconBlue = Color(0xFF5B8DEF)
private val SettingsIconBlueLight = Color(0xFF4AA8E0)
private val SettingsIconGreen = Color(0xFF4CAF88)
private val SettingsIconOrange = Color(0xFFF2A03D)
private val SettingsIconCoral = Color(0xFFF07C6E)
private val SettingsIconPurple = Color(0xFF9B7BE0)
private val SettingsIconPink = Color(0xFFE879A6)
private val SettingsIconRed = Color(0xFFE06A6A)
private val SettingsPageBg: Color get() = com.qingji.app.ui.theme.PageBackground

/** 6 个一级分类 + 4 个「显示与交互」二级分类 + 各类具体设置弹层。 */
private enum class DialogKind {
    // 一级分类落地列表
    CAT_PERSONAL, CAT_HEALTH, CAT_GENERAL, CAT_DISPLAY, CAT_AI_DATA, CAT_ABOUT,
    // 显示与交互下的 4 个二级分类
    CAT_THEME, CAT_HOME_LAYOUT, CAT_NAVIGATION, CAT_MOTION,
    // 具体设置弹层
    PRIVACY, MACRO, EXERCISE_PCT, WATER_GOAL, RECOGNITION, REMINDERS,
    UI_LANG, TIMEZONE, UNIT, CACHE, ICON
}

/** 独立页面目标（这些不在弹层里，走导航回调）。 */
private enum class PageRoute { PROFILE, FOOD_PREF, AI_CONFIG, BACKUP, WIDGET }

/** 一级分类卡片定义——集中管理，避免多处维护重复列表。 */
private data class CategoryDef(
    val kind: DialogKind,
    val title: String,
    val icon: ImageVector,
    val color: Color,
    /** V2.8：非空时点击卡片直达该目标，不再展开分类（用于「个人资料」这类只有一个入口的分类）。 */
    val direct: EntryDest? = null
)

/** 分类落地页里的一行入口。 */
private data class EntryRow(
    val title: String,
    val subtitle: String = "",
    val icon: ImageVector? = null,
    val iconColor: Color = SettingsIconGreen,
    val dest: EntryDest
)

/** 一行入口的目标：打开弹层 / 打开独立页。 */
private sealed interface EntryDest {
    data class Dlg(val kind: DialogKind) : EntryDest
    data class Page(val route: PageRoute) : EntryDest
}

/** 搜索注册表条目——只负责入口索引，不保存任何设置状态。 */
private data class SettingIndexItem(
    val id: String,
    val title: String,
    val keywords: List<String>,
    val category: String,
    val subCategory: String = "",
    val dest: EntryDest,
    val searchable: Boolean = true
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAiConfig: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenFoodPreference: () -> Unit = {},
    onOpenWidget: () -> Unit = {},
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel(),
    /** V1：日记隐私锁设置（Activity 作用域统一状态）。 */
    privacyVm: com.qingji.app.ui.viewmodel.DiaryPrivacyViewModel? = null
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    val message by vm.message.collectAsState()

    var dialog by remember { mutableStateOf<DialogKind?>(null) }
    // 一级分类页面内就地展开（用户反馈：点分类弹 bottom sheet 很繁琐）。
    // 用户反馈：点开一个分类后点别的，原来那个会自动收起不合理——改为多个可同时展开。
    var expandedCategories by remember { mutableStateOf(setOf<DialogKind>()) }
    var cacheSize by remember { mutableStateOf(0L) }
    val backup = remember { BackupManager(context) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }
    LaunchedEffect(dialog) {
        if (dialog == DialogKind.CACHE) cacheSize = backup.cacheSize()
    }

    // 安卓 13+ 开提醒先要通知权限，授权成功才执行开启动作
    var pendingNotifAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingNotifAction
        pendingNotifAction = null
        if (granted) action?.invoke()
        else Toast.makeText(context, "没给通知权限，提醒保持关闭", Toast.LENGTH_LONG).show()
    }
    fun enableReminderWithPermission(action: () -> Unit) {
        val needAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needAsk) {
            pendingNotifAction = action
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else action()
    }

    val listScroll = rememberScrollState()
    var query by remember { mutableStateOf("") }
    BackHandler(enabled = dialog != null || expandedCategories.isNotEmpty()) {
        if (dialog != null) dialog = null else expandedCategories = emptySet()
    }

    /** 打开某目标：弹层直接切 dialog，独立页走对应回调。 */
    fun go(dest: EntryDest) {
        when (dest) {
            is EntryDest.Dlg -> dialog = dest.kind
            is EntryDest.Page -> when (dest.route) {
                PageRoute.PROFILE -> onOpenProfile()
                PageRoute.FOOD_PREF -> onOpenFoodPreference()
                PageRoute.AI_CONFIG -> onOpenAiConfig()
                PageRoute.BACKUP -> onOpenBackup()
                PageRoute.WIDGET -> onOpenWidget()
            }
        }
    }

    // ============================================================
    // 入口注册中心：主页 6 张分类卡片 + 全量搜索索引
    // ============================================================
    val categories = listOf(
        // V2.8：原「个人与安全」点进去只有一项，改为直接展示「个人资料」入口。
        CategoryDef(DialogKind.CAT_PERSONAL, s("个人资料"), Icons.Filled.Person, SettingsIconBlue,
            direct = EntryDest.Page(PageRoute.PROFILE)),
        CategoryDef(DialogKind.CAT_HEALTH, s("饮食与健康"), Icons.Filled.Favorite, SettingsIconCoral),
        CategoryDef(DialogKind.CAT_GENERAL, s("通用设置"), Icons.Filled.Tune, SettingsIconPurple),
        CategoryDef(DialogKind.CAT_DISPLAY, s("显示与交互"), Icons.Filled.Dashboard, SettingsIconCyanSafe),
        CategoryDef(DialogKind.CAT_AI_DATA, s("AI 与数据"), Icons.Filled.Storage, SettingsIconBlueLight),
        // V2.8：关于轻记直达关于页（版本/更新/隐私都在里面），不再展开空壳分类。
        CategoryDef(DialogKind.CAT_ABOUT, s("关于轻记"), Icons.Filled.Info, SettingsIconPink,
            direct = EntryDest.Dlg(DialogKind.CAT_ABOUT))
    )

    // 每个分类落地页里的入口行（统一命名参数，避免位置错位）
    val categoryRows: Map<DialogKind, List<EntryRow>> = mapOf(
        DialogKind.CAT_PERSONAL to listOf(
            EntryRow(s("个人资料"), "头像、昵称、身高体重目标", Icons.Filled.Person, SettingsIconBlue,
                EntryDest.Page(PageRoute.PROFILE))
        ),
        // 「隐私与安全」从个人与安全移入关于轻记：隐私说明、清数据属于 App 信息，不属于个人资料。
        // V2.8：删掉「关于轻记」指向自身的重复行；分类卡点开即关于内容，
        // 里面只保留「隐私与安全」作为子菜单。
        DialogKind.CAT_ABOUT to listOf(
            EntryRow(s("隐私与安全"), "隐私说明、清除记录、恢复出厂", Icons.Filled.Info, SettingsIconGreen,
                EntryDest.Dlg(DialogKind.PRIVACY))
        ),
        DialogKind.CAT_HEALTH to listOf(
            EntryRow(s("营养素分配"), "蛋白 / 碳水 / 脂肪比例与目标克数", Icons.Filled.Favorite, SettingsIconGreen,
                EntryDest.Dlg(DialogKind.MACRO)),
            EntryRow(s("运动消耗补偿"), "运动消耗按多少比例加回额度", Icons.Filled.Dashboard, SettingsIconOrange,
                EntryDest.Dlg(DialogKind.EXERCISE_PCT)),
            EntryRow(s("饮水目标"), "首页水杯按这个目标灌满", Icons.Filled.Info, SettingsIconBlueLight,
                EntryDest.Dlg(DialogKind.WATER_GOAL)),
            EntryRow(s("饮食偏好与忌口"), "过敏原、忌口、菜系、烹饪时长", Icons.Filled.Favorite, SettingsIconCoral,
                EntryDest.Page(PageRoute.FOOD_PREF))
        ),
        DialogKind.CAT_GENERAL to listOf(
            EntryRow(s("识别偏好"), "自动识别餐次、估算口径、AI 语言", Icons.Filled.Info, SettingsIconPurple,
                EntryDest.Dlg(DialogKind.RECOGNITION)),
            EntryRow(s("通知与提醒"), "称重 / 断食 / 热量 / 饮水提醒", Icons.Filled.Info, SettingsIconPurple,
                EntryDest.Dlg(DialogKind.REMINDERS)),
            EntryRow(s("界面语言"), "简体中文 / English", Icons.Filled.Info, SettingsIconBlue,
                EntryDest.Dlg(DialogKind.UI_LANG)),
            EntryRow(s("记录时区"), "决定「今天」从几点开始算", Icons.Filled.Info, SettingsIconCyanSafe,
                EntryDest.Dlg(DialogKind.TIMEZONE)),
            EntryRow(s("体重单位"), "公斤 / 斤，只改显示", Icons.Filled.Info, SettingsIconGreen,
                EntryDest.Dlg(DialogKind.UNIT))
        ),
        DialogKind.CAT_DISPLAY to listOf(
            EntryRow(s("桌面小组件"), "今日剩余 / 体重 / 记一笔的外观与预览", Icons.Filled.Dashboard, SettingsIconGreen,
                EntryDest.Page(PageRoute.WIDGET)),
            EntryRow(s("主题与外观"), "主题色、深浅模式、图标、字体、气泡", Icons.Filled.Dashboard, SettingsIconPurple,
                EntryDest.Dlg(DialogKind.CAT_THEME)),
            EntryRow(s("首页布局"), "AI 分析、饮水卡、快捷按钮、小结、营养素比例", Icons.Filled.Dashboard, SettingsIconBlue,
                EntryDest.Dlg(DialogKind.CAT_HOME_LAYOUT)),
            EntryRow(s("导航设置"), "悬浮底栏、底部 Tab、左右滑动", Icons.Filled.Dashboard, SettingsIconGreen,
                EntryDest.Dlg(DialogKind.CAT_NAVIGATION)),
            EntryRow(s("动效与性能"), "页面切换、进度条动效、刷新率", Icons.Filled.Dashboard, SettingsIconOrange,
                EntryDest.Dlg(DialogKind.CAT_MOTION))
        ),
        DialogKind.CAT_AI_DATA to listOf(
            EntryRow(s("AI 服务配置"), "服务商、模型、API 地址与 Key 管理", Icons.Filled.Storage, SettingsIconCyanSafe,
                EntryDest.Page(PageRoute.AI_CONFIG)),
            EntryRow(s("数据导入与导出"), "备份 / 恢复 / 换机迁移 / 缓存", Icons.Filled.Storage, SettingsIconBlue,
                EntryDest.Page(PageRoute.BACKUP)),
            EntryRow(s("缓存管理"), "清理临时图片，不影响记录照片", Icons.Filled.Storage, SettingsIconRed,
                EntryDest.Dlg(DialogKind.CACHE))
        )
    )

    // 搜索索引：包含一级分类、二级入口、以及二级页里的具体设置项
    val searchIndex = buildList {
        categories.forEach { c ->
            add(SettingIndexItem(c.kind.name, c.title, listOf(c.title), c.title, "",
                EntryDest.Dlg(c.kind)))
        }
        fun addRows(kind: DialogKind, category: String, sub: String = "") {
            categoryRows[kind].orEmpty().forEach { row ->
                add(SettingIndexItem(row.title + kind.name, row.title, listOf(row.title, row.subtitle),
                    category, sub, row.dest))
            }
        }
        addRows(DialogKind.CAT_PERSONAL, s("个人与安全"))
        addRows(DialogKind.CAT_HEALTH, s("饮食与健康"))
        addRows(DialogKind.CAT_GENERAL, s("通用设置"))
        addRows(DialogKind.CAT_DISPLAY, s("显示与交互"))
        addRows(DialogKind.CAT_AI_DATA, s("AI 与数据"))
        // 「关于轻记」与「隐私与安全」都从 categoryRows 取，搜索入口与分类落地页保持一致。
        // 分类卡本身已在上方 categories.forEach 注册，这里只补行级入口。
        addRows(DialogKind.CAT_ABOUT, s("关于轻记"))
        // 二级页里的具体设置项，直接定位到所在二级分类弹层
        val themeItems = listOf("主题色" to listOf("主色", "颜色", "配色"), "深浅模式" to listOf("夜间", "深色", "浅色", "跟随系统"),
            "应用图标" to listOf("桌面图标", "icon"), "自定义字体" to listOf("字体", "ttf", "otf"), "水杯气泡量" to listOf("气泡", "水杯"))
        themeItems.forEach { (t, kw) -> add(SettingIndexItem("theme_$t", t, kw + t, s("显示与交互"), s("主题与外观"),
            EntryDest.Dlg(DialogKind.CAT_THEME))) }
        val homeItems = listOf("AI 分析按钮" to listOf("ai", "分析"), "饮水卡" to listOf("喝水", "水杯"),
            "悬浮快捷按钮" to listOf("悬浮", "快捷"), "本周小结" to listOf("每周小结", "小结"),
            "每餐营养素比例" to listOf("营养素", "蛋白", "碳水", "脂肪"))
        homeItems.forEach { (t, kw) -> add(SettingIndexItem("home_$t", t, kw + t, s("显示与交互"), s("首页布局"),
            EntryDest.Dlg(DialogKind.CAT_HOME_LAYOUT))) }
        val navItems = listOf("悬浮底栏" to listOf("dock", "底栏"), "底部导航" to listOf("tab", "底部"),
            "左右滑动切换页面" to listOf("滑动", "手势", "翻页"))
        navItems.forEach { (t, kw) -> add(SettingIndexItem("nav_$t", t, kw + t, s("显示与交互"), s("导航设置"),
            EntryDest.Dlg(DialogKind.CAT_NAVIGATION))) }
        val motionItems = listOf("页面切换动画" to listOf("转场", "动画"), "进度条动效" to listOf("进度条", "动效"),
            "实时刷新率" to listOf("fps", "帧率", "调试"))
        motionItems.forEach { (t, kw) -> add(SettingIndexItem("motion_$t", t, kw + t, s("显示与交互"), s("动效与性能"),
            EntryDest.Dlg(DialogKind.CAT_MOTION))) }
    }

    val q = query.trim()
    val searchResults = if (q.isBlank()) emptyList() else searchIndex.filter { item ->
        item.searchable && (listOf(item.title) + item.keywords + listOf(item.category, item.subCategory))
            .any { it.contains(q, ignoreCase = true) }
    }

    // ============================================================
    // 页面主体
    // ============================================================
    // 二级设置打开时不组合主页，避免底层仍参与渲染/交互
    if (dialog == null) {
    Column(modifier = modifier.fillMaxSize().background(SettingsPageBg)) {
        // 标题栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickableNoRipple { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    tint = TextPrimary, modifier = Modifier.size(22.dp))
            }
            Text(s("设置"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }

        // 搜索框常驻
        SettingsSearchBox(query = query, onQueryChange = { query = it })

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(listScroll).padding(horizontal = 12.dp)
        ) {
            if (q.isNotBlank()) {
                // ---- 搜索结果 ----
                GroupTitle("${s("搜索结果")}（${searchResults.size}）")
                if (searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center) {
                        Text(s("没有找到相关设置"), fontSize = 13.sp, color = TextSecondary)
                    }
                } else {
                    SettingsCard {
                        searchResults.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)
                                    .clickableNoRipple { go(item.dest) }.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    val where = if (item.subCategory.isNotBlank())
                                        "${item.category} · ${item.subCategory}" else item.category
                                    Text(where, fontSize = 11.sp, color = TextSecondary)
                                }
                                Text("›", fontSize = 20.sp, color = TextSecondary.copy(alpha = 0.6f))
                            }
                            if (index < searchResults.lastIndex) RowDivider()
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            } else {
                // ---- 6 个一级分类卡片：点击就地展开，不再弹 bottom sheet ----
                GroupTitle(s("设置分类"))
                categories.forEach { cat ->
                    Spacer(Modifier.height(10.dp))
                    val expanded = cat.kind in expandedCategories
                    SettingsCard {
                        Column(
                            modifier = Modifier.animateContentSize(tween(200))
                        ) {
                            SettingIconRow(
                                title = cat.title, icon = cat.icon, iconColor = cat.color,
                                onClick = {
                                    if (cat.direct != null) go(cat.direct)
                                    else expandedCategories = if (expanded) expandedCategories - cat.kind
                                    else expandedCategories + cat.kind
                                }
                            )
                            // 直达卡不显示展开箭头下的子项
                            if (expanded && cat.direct == null) {
                                val rows = categoryRows[cat.kind].orEmpty()
                                com.qingji.app.ui.components.SectionCard(contentPadding = 0.dp) {
                                    rows.forEachIndexed { index, row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                                .clickableNoRipple { go(row.dest) }
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (row.icon != null) SettingIconBadge(row.icon, row.iconColor)
                                            Column(modifier = Modifier.weight(1f).padding(start = if (row.icon != null) 12.dp else 0.dp)) {
                                                Text(row.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                                if (row.subtitle.isNotBlank()) {
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(row.subtitle, fontSize = 11.sp, color = TextSecondary)
                                                }
                                            }
                                            Text("›", fontSize = 18.sp, color = TextSecondary.copy(alpha = 0.6f))
                                        }
                                        if (index < rows.lastIndex) RowDivider()
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
    }

    // ============================================================
    // 弹层：分类落地页 + 具体设置页
    // ============================================================
    if (dialog != null) {
        when (val d = dialog!!) {
            // 一级分类已改为页面内就地展开（见上方分类卡），不再走 SettingsDetailSheet。
            DialogKind.CAT_PERSONAL, DialogKind.CAT_HEALTH, DialogKind.CAT_GENERAL,
            DialogKind.CAT_DISPLAY, DialogKind.CAT_AI_DATA -> {
                expandedCategories = expandedCategories + d
                dialog = null
            }

            DialogKind.CAT_ABOUT -> AboutScreen(onBack = { dialog = null }, useSheet = false,
                onOpenPrivacy = { dialog = DialogKind.PRIVACY })

            // ---------- 显示与交互 · 4 个二级分类 ----------
            DialogKind.CAT_THEME -> ThemeSettingsPage(
                settings = settings,
                onSetTheme = { vm.setTheme(it) },
                onSetThemeColor = { vm.setThemeColor(it) },
                onSetBubbles = { vm.setWaterBubbles(it) },
                onSetFont = { vm.setCustomFontPath(it) },
                onOpenIcon = { dialog = DialogKind.ICON },
                onBack = { dialog = null },
                useSheet = false // 改为页面内全屏，不再底部弹窗
            )

            DialogKind.CAT_HOME_LAYOUT -> ToggleDetailPage(
                title = s("首页布局"),
                subtitle = "控制首页各模块是否显示，不影响记录数据",
                items = listOf(
                    ToggleItem(s("AI 分析按钮"), "不配 AI 可以关掉", settings.showAiAnalysis) { vm.setShowAiAnalysis(it) },
                    ToggleItem(s("饮水卡"), "不记喝水可以关掉", settings.showWaterCard) { vm.setShowWaterCard(it) },
                    ToggleItem(s("悬浮快捷按钮"), "首页右下角三个圆按钮（喝水/体重/拍照）", settings.showQuickButtons) { vm.setShowQuickButtons(it) },
                    ToggleItem(s("本周小结"), "进展页显示近 7 天一句话小结（本地计算）", settings.showWeeklySummary) { vm.setShowWeeklySummary(it) },
                    ToggleItem(s("每餐营养素比例"), "餐次卡片底部显示蛋白·碳水·脂肪比例条", settings.showMealMacro) { vm.setShowMealMacro(it) }
                ),
                onBack = { dialog = null }, useSheet = false // 改为页面内全屏，不再底部弹窗
            )

            DialogKind.CAT_NAVIGATION -> ToggleDetailPage(
                title = s("导航设置"),
                subtitle = "调整底栏与页面切换方式，不影响手势与点击",
                items = listOf(
                    ToggleItem(s("悬浮底栏"), "关掉切回经典通栏底栏", settings.floatingBottomBar) { vm.setFloatingBottomBar(it) },
                    ToggleItem("显示进展 Tab", "体重曲线、围度、饮水、运动", settings.showProgressTab) { vm.setTabVisible("progress", it) },
                    ToggleItem("显示日记 Tab", "写日记与打卡记录", settings.showDiaryTab) { vm.setTabVisible("diary", it) },
                    ToggleItem("显示我的 Tab", "个人资料、成就、设置入口", settings.showMineTab) { vm.setTabVisible("mine", it) },
                    ToggleItem(s("左右滑动切换页面"), "在首页等主页面左右滑动切换", settings.swipeNavigate) { vm.setSwipeNavigate(it) }
                ),
                onBack = { dialog = null }, useSheet = false // 改为页面内全屏，不再底部弹窗
            )

            DialogKind.CAT_MOTION -> ToggleDetailPage(
                title = s("动效与性能"),
                subtitle = "页面切换与进度条动效分开控制；刷新率为调试辅助",
                items = listOf(
                    ToggleItem(s("页面切换动画"), "关掉只留淡入淡出，老机器更稳", settings.pageTransition) { vm.setPageTransition(it) },
                    ToggleItem(s("进度条动效"), "关掉可以省一点点电", settings.progressAnimation) { vm.setProgressAnimation(it) }
                ),
                extra = {
                    DetailGroupTitle("开发调试")
                    com.qingji.app.ui.components.SectionCard(contentPadding = 0.dp) {
                        com.qingji.app.ui.screen.settings.SwitchRow(
                            title = s("实时刷新率"),
                            subtitle = "首页右上角浮一个徽章显示实测 FPS，平时关掉",
                            checked = settings.showRefreshRate
                        ) { vm.setShowRefreshRate(it) }
                    }
                },
                onBack = { dialog = null }, useSheet = false // 改为页面内全屏，不再底部弹窗
            )

            // ---------- 具体设置弹层 ----------
            DialogKind.RECOGNITION -> ToggleDetailPage(
                title = s("识别偏好"), subtitle = "",
                items = listOf(
                    ToggleItem(s("自动识别餐次"), "", settings.autoMeal) { vm.setAutoMeal(it) },
                    ToggleItem(s("估算偏保守"), "", settings.conservativeEstimate) { vm.setConservativeEstimate(it) }
                ),
                extra = {
                    DetailGroupTitle(s("AI 识别语言"))
                    listOf(OptionItem("zh", s("简体中文")), OptionItem("auto", s("跟随输入"))).forEach { option ->
                        val selected = option.key == settings.aiLanguage
                        Row(modifier = Modifier.fillMaxWidth().clickableNoRipple { vm.setAiLanguage(option.key) }
                            .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(option.label, fontSize = 14.sp,
                                color = if (selected) QingJiGreenDark else TextPrimary, modifier = Modifier.weight(1f))
                            if (selected) Text("✓", fontSize = 16.sp, color = com.qingji.app.ui.theme.QingJiGreen)
                        }
                    }
                },
                onBack = { dialog = null }, useSheet = false // 改为页面内全屏，不再底部弹窗
            )

            DialogKind.MACRO -> MacroRatioPage(
                proteinInit = settings.proteinPct, carbInit = settings.carbPct, fatInit = settings.fatPct,
                fiberInit = profile?.fiberGoalG ?: 25, sodiumInit = profile?.sodiumGoalMg ?: 2000,
                calciumInit = profile?.calciumGoalMg ?: 800,
                onSave = { p, c, f -> vm.setMacroRatio(p, c, f) },
                onSaveGoals = { fiber, sodium, calcium -> vm.setNutrientGoals(fiber, sodium, calcium) },
                onBack = { dialog = null }, useSheet = false // 改为页面内全屏，不再底部弹窗
            )

            DialogKind.EXERCISE_PCT -> ExercisePctDetailPage(
                pct = settings.exerciseCompensationPct, onSelect = { vm.setExerciseCompensationPct(it) },
                onBack = { dialog = null }, useSheet = false // 改为页面内全屏，不再底部弹窗
            )

            DialogKind.TIMEZONE -> OptionDetailPage(
                title = s("记录时区"), subtitle = s("改这里会影响「今天」从几点开始算，跨时区出差时用得上"),
                options = timezoneOptions().map { OptionItem(it.first, it.second) },
                selectedKey = settings.timeZone, onPick = { vm.setTimezone(it); dialog = null },
                onBack = { dialog = null }, useSheet = false // 改为页面内全屏，不再底部弹窗
            )

            DialogKind.REMINDERS -> ReminderPage(
                settings = settings, allDisabled = settings.allNotificationsDisabled,
                onSetAllDisabled = { vm.setAllNotificationsDisabled(it) },
                onSetWeighReminder = { weekday, hour, minute -> vm.setWeighWeekday(weekday, hour, minute) },
                onSetFastingSwitch = { on -> if (on) enableReminderWithPermission { vm.setFastingReminder(true) } else vm.setFastingReminder(false) },
                onSetFastingTime = { hour, minute -> vm.setFastingReminder(settings.fastingReminder, hour, minute, showToast = false) },
                onSetKcalSwitch = { on -> if (on) enableReminderWithPermission { vm.setKcalReminder(true) } else vm.setKcalReminder(false) },
                onSetKcalTime = { hour, minute -> vm.setKcalReminder(settings.kcalReminder, hour, minute, showToast = false) },
                onSetWaterSwitch = { on -> if (on) enableReminderWithPermission { vm.setWaterReminder(true) } else vm.setWaterReminder(false) },
                onSetWaterTime = { hour, minute -> vm.setWaterReminder(settings.waterReminder, hour, minute, showToast = false) },
                onBack = { dialog = null }, useSheet = false // 改为页面内全屏，不再底部弹窗
            )

            DialogKind.UI_LANG -> OptionDetailPage(
                title = s("界面语言"), subtitle = s("切换后立即生效，未翻译的条目仍显示中文"),
                options = listOf(OptionItem("zh", "简体中文", s("界面默认中文，核心页可切英文")),
                    OptionItem("en", "English", s("Core screens in English; the rest stays Chinese"))),
                selectedKey = settings.uiLanguage, onPick = { vm.setUiLanguage(it); dialog = null },
                onBack = { dialog = null }, useSheet = false // 改为页面内全屏，不再底部弹窗
            )

            DialogKind.UNIT -> OptionDetailPage(
                title = s("体重单位"), subtitle = s("只改显示，数据库里永远存公斤"),
                options = listOf(OptionItem("kg", "公斤（kg）"), OptionItem("jin", "斤（500g）")),
                selectedKey = settings.unit, onPick = { vm.setUnit(it); dialog = null },
                onBack = { dialog = null }, useSheet = false // 改为页面内全屏，不再底部弹窗
            )

            DialogKind.WATER_GOAL -> NumberDetailPage(
                title = s("饮水目标"), subtitle = s("首页水杯按这个目标灌满"), label = s("每天计划喝多少"),
                initial = settings.waterGoalMl.toString(), suffix = "ml", hint = s("常见范围 1500～3000 毫升"),
                onSave = { vm.setWaterGoal(it.toIntOrNull() ?: 2000) },
                onBack = { dialog = null }, useSheet = false // 改为页面内全屏，不再底部弹窗
            )

            DialogKind.PRIVACY -> PrivacyPage(
                onClearRecords = { vm.clearRecords() }, onFactoryReset = { vm.factoryReset() },
                onBack = { dialog = null }, useSheet = false,
                privacyVm = privacyVm
            )

            DialogKind.ICON -> com.qingji.app.ui.screen.settings.AppIconSettingsPage(
                currentVariant = settings.iconVariant, onSelect = { vm.setIconVariant(it) },
                onBack = { dialog = null }, useSheet = false // 改为页面内全屏，不再底部弹窗
            )

            DialogKind.CACHE -> InfoDetailPage(
                title = s("清空缓存"),
                body = s("会删掉拍照时的临时图片，当前占用") + " ${backup.sizeText(cacheSize)}。" +
                    s("已经保存到饮食记录和日记里的照片不受影响。"),
                actionText = s("清理缓存"),
                onAction = { backup.clearCache(); cacheSize = backup.cacheSize()
                    Toast.makeText(context, "已清理", Toast.LENGTH_SHORT).show() },
                onBack = { dialog = null }, useSheet = false // 改为页面内全屏，不再底部弹窗
            )

            else -> Unit
        }
    }
}

/** 显示与交互分类图标色（青色，深浅自适应由背景透明度处理）。 */
private val SettingsIconCyanSafe = Color(0xFF3FB6C4)

/** epoch 毫秒 → HH:mm（保留给可能的二级页复用）。 */
private fun formatMillis(millis: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))
