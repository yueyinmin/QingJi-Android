package com.qingji.app.ui.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WbSunny
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.FoodIcon
import com.qingji.app.domain.MealRules
import com.qingji.app.ui.components.AppearItem
import com.qingji.app.ui.components.QuickActionColumn
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.components.WaterCup
import com.qingji.app.ui.components.ThinProgressBar
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.MacroCarb
import com.qingji.app.ui.theme.MacroFat
import com.qingji.app.ui.theme.MacroProtein
import com.qingji.app.ui.theme.MultiColor
import com.qingji.app.ui.theme.pressable
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.HomeUiState
import com.qingji.app.ui.viewmodel.HomeViewModel
import java.time.LocalDate

/**
 * ============================================================
 * 首页（M1 已经换成真数据）
 * ------------------------------------------------------------
 * 数据来源：HomeViewModel 订阅 Room 的四个 Flow。
 * 只要数据库里任何一条记录变了，这一屏会自动刷新，不用手动下拉。
 *
 * 能做的事：
 * 1. 点日期条切换查看历史某天（不允许翻到未来）
 * 2. 看当天剩余热量、三大营养素、饮水进度
 * 3. 按四餐看吃过的每一条，点 × 可以删
 * 4. 点饮水快捷按钮直接加 200 / 300 / 500 ml
 * ============================================================
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddRecord: () -> Unit = {},
    onOpenAnalysis: () -> Unit = {},
    onOpenWeight: () -> Unit = {},
    onOpenImage: () -> Unit = {},
    /** 点某一餐卡片 → 进用餐详情（带那一天 + 餐次）。 */
    onOpenMealDetail: (java.time.LocalDate, String) -> Unit = { _, _ -> },
    /** 点断食条 → 进轻断食页 */
    onOpenFasting: () -> Unit = {},
    /** 点「食物库」→ 进食物库页 */
    onOpenFoodLibrary: () -> Unit = {},
    modifier: Modifier = Modifier,
    /**
     * tabfix：状态栏高度，由 QingJiAppRoot 在 Scaffold 消耗 insets 之前读好传进来。
     * Scaffold 现在对所有路由统一给默认 top inset，首页内容区因此从状态栏下方开始；
     * 把顶部渐变往上挪这么多，渐变起点就还是屏幕最顶端，视觉和修复前完全一致。
     */
    statusBarTop: Dp = 0.dp,
    /** tabfix：滚动状态由 Root 持有，页面被导航销毁再重建时位置不丢。 */
    scrollState: ScrollState = rememberScrollState(),
    vm: HomeViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val weeklySummary by vm.weeklySummary.collectAsState()
    val today = AppClock.today()

    // 首页要读几项显示设置：饮水卡显隐、AI 分析按钮显隐、悬浮按钮开关
    val context = LocalContext.current
    val settings by remember { SettingsStore.observe(context) }
        .collectAsState(initial = QingJiSettings())

    // 日期下拉面板
    var showDatePicker by remember { mutableStateOf(false) }

    // 点水杯弹出的三级：先选做什么 → 首页设置 / 今日详情
    var showCupMenu by remember { mutableStateOf(false) }
    var showCupDetail by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        // 顶部一层很淡的主题色渐变：日期、周历都落在这一块上。
        // 颜色取 QingJiGreenLight（跟着主题色变），不用写死任何青色。
        // tabfix：Scaffold 统一给 top inset 后，内容区顶部 = 状态栏下沿，
        // 这里用负 offset 把渐变补回状态栏后面（高度仍是 250dp，
        // 起止位置与修复前逐像素一致）。Box 默认不裁剪子内容，能画到边界外。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = -statusBarTop)
                .height(250.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            QingJiGreenLight,
                            QingJiGreenLight.copy(alpha = 0.45f),
                            PageBackground
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                // tabfix：状态栏内边距已由 Scaffold 统一提供，这里不能再加，
                // 否则首页内容会比其它页面多沉一个状态栏高度、切页时又跳一次。
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
        Spacer(Modifier.height(12.dp))

        // ---------- 顶部：日期标题 + 翻周 + 回到今天 ----------
        // 固定高度：否则「回到今天」出现/消失时整行高度会变，下面的内容跟着跳一下
        //
        // 三颗胶囊的内边距统一用 8dp（原来是 10dp）：
        // 360dp 屏 + 历史日期 + 三颗胶囊同时在时，日期只剩 76dp 的槽位，
        // 而「11月9日」要 78dp —— 差 2dp 放不下，日期被省略成「11月…」，日期号直接看不见。
        // 三颗一起各收窄 2dp 正好让出 12dp，日期能完整显示，胶囊也没有一颗折行，
        // 而且没动字号、没给固定宽度。只在 360dp 这种窄屏上才有意义，宽屏完全看不出差别。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧「日期 + 下拉箭头」整体吃掉所有剩余宽度，内部靠左；
            // 这样右边的胶囊永远优先拿到它们需要的宽度，不会被挤。
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 标题本身可点，点开日历下拉（可以跨年选）；翻周箭头退役了。
                // maxLines + Ellipsis：屏幕窄到放不下时是日期显示「11月…」，
                // 而不是让右边的「减脂」被挤成竖排。
                Text(
                    text = "${state.date.monthValue}月${state.date.dayOfMonth}日",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable { showDatePicker = true }
                )
                Spacer(Modifier.size(4.dp))
                // 下拉箭头：点它和点标题一样弹出日历（不再用 emoji 胶囊）
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "选择日期",
                    tint = QingJiGreenDark,
                    modifier = Modifier
                        .size(24.dp)
                        .pressable { showDatePicker = true }
                )
            }

            val showBack = state.date != today
            // 「回到今天」只在真的能回的时候才占位。
            // 以前是一直渲染、只把 alpha 设为 0 —— 字看不见，但 70dp 的宽度还占着，
            // 右边两个胶囊被它挤掉位置，「减脂」就被压成「减」「脂」两行了。
            if (showBack) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = s("回到今天"),
                    fontSize = 12.sp,
                    color = QingJiGreenDark,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(QingJiGreenLight)
                        .clickable { vm.backToToday() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }

            // 连续天数：永远单行，不许折字
            Spacer(Modifier.width(6.dp))
            val joinedDays = state.profile?.let {
                val created = java.time.LocalDate.ofEpochDay(it.createdAt / 86_400_000L)
                (today.toEpochDay() - created.toEpochDay()).toInt() + 1
            } ?: 1
            Text(
                text = "🔥 $joinedDays 连续天数",
                fontSize = 11.sp,
                color = MultiColor.StreakText,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MultiColor.StreakBackground)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
            // 目标状态：宽度跟着文字走，同样锁死单行。
            // 文案统一叫「减脂」——和进展页、引导里的说法一致，不再「减肥」「减脂」混着叫。
            Spacer(Modifier.width(6.dp))
            Text(
                text = when (settings.goalMode) {
                    "gain" -> s("增肌")
                    "keep" -> s("保持")
                    else -> s("减脂")
                },
                fontSize = 11.sp,
                color = QingJiGreenDark,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(QingJiGreenLight)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---------- 一周日期条（可点） ----------
        DateStrip(state = state, onSelect = vm::selectDate)

        Spacer(Modifier.height(12.dp))

        // ---------- 今日剩余 ----------
        // 卡片依次浮上来（每张错开 45ms），比整页「啪」一下出现柔和得多
        AppearItem(index = 0) {
        TodaySummaryCard(
            state = state,
            waterGoal = settings.waterGoalMl,
            showWater = settings.showWaterCard,
            animateProgress = settings.progressAnimation,
            waterBubbles = settings.waterBubbles,
            exerciseCompensation = settings.exerciseCompensation,
            onCupClick = { showCupMenu = true },
            fastingEnabled = settings.fastingEnabled,
            fastingPlanHours = settings.fastingPlanHours,
            fastingEatStart = settings.fastingEatStartMinute,
            fastingEatEnd = settings.fastingEatEndMinute,
            fastingDays = settings.fastingDays,
            onOpenFasting = { onOpenFasting() }
        )
        }

        Spacer(Modifier.height(20.dp))

        // ---------- 今日记录 ----------
        // v3.2.4：AI 分析从右侧按钮组挪到「今日记录」文字旁边；
        // 右侧第一颗由「食物库」换成「记体重」（与悬浮栏第二颗互换位置）。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (state.date == today) s("今日记录") else s("当日记录"),
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                if (settings.showAiAnalysis) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = s("✦ AI 分析"),
                        fontSize = 11.sp,
                        color = QingJiGreenDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(QingJiGreenLight)
                            .pressable { onOpenAnalysis() }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
            // v1.0.2：「记体重」挪进右下悬浮栏，这里右侧不再放按钮
        }

        Spacer(Modifier.height(10.dp))

        // 四餐固定顺序：早 / 午 / 晚 / 加餐
        MealRules.ALL.forEachIndexed { index, type ->
            val items = state.logs.filter { it.mealType == type }
            AppearItem(index = index + 2) {
                MealCard(
                    type = type,
                    items = items,
                    onDelete = vm::deleteLog,
                    onOpenDetail = { onOpenMealDetail(state.date, type) },
                    showMacro = settings.showMealMacro
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // v3.4.0 R2：本周小结放四餐之后、底部留白之前（受「主题与显示」开关控制）
        if (settings.showWeeklySummary) {
            Spacer(Modifier.height(20.dp))
            AppearItem(index = 6) {
                com.qingji.app.ui.components.WeeklySummaryCard(summary = weeklySummary)
            }
        }

        // 底部留白：给悬浮按钮腾位置，不然会盖住最后一餐
        Spacer(Modifier.height(88.dp))
        }

        // ---------- 实时刷新率徽章（主题与显示里开关，平时关掉） ----------
        if (settings.showRefreshRate) {
            com.qingji.app.ui.components.RefreshRateBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 16.dp)
            )
        }

        // ---------- 右侧悬浮快捷按钮（M2 第 4 条，可在设置里关掉） ----------
        if (settings.showQuickButtons) {
            QuickActionColumn(
                // v3.2.4：点水滴 = 和点水杯一样弹出「快速记录喝水」菜单。
                // v1.0.2：「记体重」从今日记录行挪回悬浮栏（第二颗）。
                onWater = { showCupMenu = true },
                onWeight = { onOpenWeight() },
                onOpenFoodLibrary = onOpenFoodLibrary,
                onPhoto = onOpenImage,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    // v1.2：往下贴到底栏上方，减少遮挡「今日记录」右侧按钮的窗口
                    .padding(end = 16.dp, bottom = 10.dp)
            )
        }
    }

    // ---------- 日期下拉：月历 + 年份切换 ----------
    if (showDatePicker) {
        com.qingji.app.ui.components.MonthPickerSheet(
            selected = state.date,
            onSelect = { vm.selectDate(it) },
            onDismiss = { showDatePicker = false }
        )
    }

    // ---------- 点水杯：先选做什么 ----------
    // 用底部弹层而不是弹窗：和「+ 记一笔」的记录面板是同一套交互语言
    if (showCupMenu) {
        QuickWaterSheet(
            waterMl = state.waterMl,
            goalMl = settings.waterGoalMl,
            onAdd = { ml -> vm.addWater(ml) },
            onOpenDetail = {
                showCupMenu = false
                showCupDetail = true
            },
            onDismiss = { showCupMenu = false }
        )
    }

    // ---------- 首页显示设置（气泡量 + 餐次营养比例） ----------
    // ---------- 今日饮水详情 ----------
    if (showCupDetail) {
        val goal = settings.waterGoalMl.coerceAtLeast(1)
        val percent = (state.waterMl * 100 / goal).coerceIn(0, 100)
        com.qingji.app.ui.components.AppBottomSheet(
            title = s("今日饮水"),
            subtitle = s("水和饮食一样，记了才有趋势"),
            confirmText = s("知道了"),
            onDismiss = { showCupDetail = false }
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${state.waterMl}",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MultiColor.WaterText
                )
                Text(
                    text = " / $goal ml",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            ThinProgressBar(
                progress = state.waterMl.toFloat() / goal,
                color = MultiColor.WaterFill
            )
            Spacer(Modifier.height(10.dp))
            Text(text = "完成 $percent%", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.waterMl >= goal) {
                    "今天已经达标，保持就行"
                } else {
                    "还差 ${goal - state.waterMl} ml，大概 ${
                        ((goal - state.waterMl) / 250.0).let { if (it < 1) 1 else it.toInt() }
                    } 杯（250ml 一杯）"
                },
                fontSize = 13.sp,
                color = if (state.waterMl >= goal) QingJiGreen else TextPrimary
            )
        }
    }
}

/**
 * 快速记录喝水（v3.2）：点首页那根高水杯弹出来。
 * 四档常用水量 + 自定义输入 + 记录时间 + 查看今日详情，
 * 容器色/遮罩/把手和 AppBottomSheet 完全一致，不写死任何蓝色。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun QuickWaterSheet(
    waterMl: Int,
    goalMl: Int,
    onAdd: (Int) -> Unit,
    onOpenDetail: () -> Unit,
    onDismiss: () -> Unit
) {
    var customMl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val timeText = "${AppClock.today().monthValue}月${AppClock.today().dayOfMonth}日 " +
        java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        contentColor = TextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.28f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DividerColor)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "💧", fontSize = 18.sp)
                Spacer(Modifier.size(6.dp))
                Text(
                    text = s("快速记录喝水"),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "已喝 $waterMl / 目标 ${goalMl}ml",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(150, 250, 350, 500).forEach { ml ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(QingJiGreenLight)
                            .pressable { onAdd(ml); error = "" }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${ml}ml",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = QingJiGreenDark,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedTextField(
                    value = customMl,
                    onValueChange = { customMl = it; error = "" },
                    singleLine = true,
                    placeholder = {
                        Text(text = s("输入自定义水量"), fontSize = 12.sp, color = TextSecondary)
                    },
                    trailingIcon = {
                        Text(
                            text = "ml",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(QingJiGreen)
                        .pressable {
                            val ml = customMl.toIntOrNull()
                            if (ml != null && ml in 1..3000) {
                                onAdd(ml)
                                customMl = ""
                                error = ""
                            } else {
                                error = "1 到 3000 之间都行"
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s("记录"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
            if (error.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text = error, fontSize = 11.sp, color = DangerRed)
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PageBackground)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🕐", fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Text(text = timeText, fontSize = 13.sp, color = QingJiGreenDark)
            }

            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, MultiColor.WaterText, RoundedCornerShape(10.dp))
                    .pressable { onOpenDetail() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s("查看今日饮水详情"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MultiColor.WaterText
                )
            }
        }
    }
}

/** 标题旁边翻周用的小圆按钮。 */
@Composable
private fun WeekArrow(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(CardWhite)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 18.sp, color = QingJiGreenDark)
    }
}

/**
 * 一周日期条：参考吃有数的样式 —— 没选中的是虚线圆圈，选中那天
 * 变成主题浅色的圆角方块，下面带一颗主题色小点。
 * 颜色全部取主题色，切换主题时一起变。
 */
@Composable
private fun DateStrip(
    state: HomeUiState,
    onSelect: (LocalDate) -> Unit
) {
    val weekLabels = listOf(s("周一"), s("周二"), s("周三"), s("周四"), s("周五"), s("周六"), s("周日"))
    val monday = state.date.minusDays((state.date.dayOfWeek.value - 1).toLong())
    val today = AppClock.today()

    // drawBehind 的 lambda 不是 @Composable，颜色要在这里先取出来。
    // 虚线圈不能用 DividerColor：它太淡，压在顶部浅色渐变上等于没画；
    // 选中块也不能用 QingJiGreenLight：和渐变背景同色，看不出方块。
    val dashColor = QingJiGreen.copy(alpha = 0.42f)
    val selectedBg = QingJiGreen.copy(alpha = 0.16f)
    val selectedText = QingJiGreenDark
    val dotColor = QingJiGreen

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weekLabels.forEachIndexed { index, label ->
            val date = monday.plusDays(index.toLong())
            val selected = date == state.date
            val isFuture = date.isAfter(today)

            Column(
                modifier = Modifier
                    // 固定宽度：个位数（8）和两位数（18）原本宽度不同，
                    // 翻周时会左右抽动，固定住就不抖了
                    .width(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !isFuture) { onSelect(date) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = when {
                        isFuture -> DividerColor
                        selected -> selectedText
                        else -> TextSecondary
                    }
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selected) selectedBg else Color.Transparent)
                        .then(
                            if (selected) {
                                Modifier
                            } else {
                                Modifier.drawBehind {
                                    drawCircle(
                                        color = dashColor,
                                        radius = (size.minDimension - 2.dp.toPx()) / 2,
                                        style = Stroke(
                                            width = 1.2.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(
                                                floatArrayOf(6f, 5f)
                                            )
                                        )
                                    )
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${date.dayOfMonth}",
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isFuture -> DividerColor
                            selected -> selectedText
                            else -> TextPrimary
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (selected) dotColor else Color.Transparent)
                )
            }
        }
    }
}

/**
 * 今日剩余卡。
 * 全部数字来自数据库：profile 给目标，food_log 汇总给已吃，water_log 给饮水。
 * 档案还没算过目标（=0）时兜底 2000 千卡，避免除零和显示 0/0。
 */
@Composable
private fun TodaySummaryCard(
    state: HomeUiState,
    waterGoal: Int = 3200,
    showWater: Boolean = true,
    animateProgress: Boolean = true,
    /** 水杯气泡量（设置里可调）：0 关 / 1 少 / 2 标准 / 3 多 */
    waterBubbles: Int = 2,
    /** v3.4.0 R5：关掉后运动消耗不计入预计热量缺口 */
    exerciseCompensation: Boolean = false,
    /** 点水杯时通知外面弹「首页设置 / 查看详情」的选择 */
    onCupClick: () -> Unit = {},
    /** 轻断食：开了才在卡片底部显示断食按钮 */
    fastingEnabled: Boolean = false,
    fastingPlanHours: Int = 16,
    fastingEatStart: Int = 460,
    fastingEatEnd: Int = 940,
    /** v1.0.3：生效周期（周几），今天不在周期内按钮显示「今日休息」 */
    fastingDays: String = "1,2,3,4,5,6,7",
    onOpenFasting: () -> Unit = {}
) {
    val profile = state.profile
    val goalKcal = profile?.dailyKcalGoal?.takeIf { it > 0 } ?: 2000
    // 饮水目标以设置页为准（设置里改完首页立刻变），没设置才回落到档案
    val goalWater = waterGoal.takeIf { it > 0 }
        ?: profile?.waterGoalMl?.takeIf { it > 0 }
        ?: 2000
    val eatenKcal = state.totalKcal.toInt()
    val remainingKcal = goalKcal - eatenKcal

    // 「进度条动效」设置：开了就 400ms 补间，关了直接跳到位
    val rawProgress = eatenKcal.toFloat() / goalKcal
    val animatedProgress = if (animateProgress) {
        androidx.compose.animation.core.animateFloatAsState(
            targetValue = rawProgress,
            animationSpec = androidx.compose.animation.core.tween(400),
            label = "kcal-progress"
        ).value
    } else {
        rawProgress
    }

    // 右侧高水杯上挂的完成度百分比
    val waterPercent = (state.waterMl * 100 / goalWater.coerceAtLeast(1)).coerceIn(0, 100)

    SectionCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左栏：热量 / 营养素 / 断食条；右栏：一根贯穿卡片的高水杯
            Column(modifier = Modifier.weight(1f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // v1.0.2：目标状态（减脂/增肌/保持）挪到顶栏连续天数右边了
            Text(text = s("今日剩余"), fontSize = 14.sp, color = TextSecondary)
        }

        Spacer(Modifier.height(6.dp))

        // 数字变化时上下滚动一下：吃完记一笔，能明显看到剩余额度往下掉
        com.qingji.app.ui.components.AnimatedNumber(
            value = remainingKcal,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            ),
            color = if (remainingKcal < 0) DangerRed else TextPrimary,
            suffix = " 千卡"
        )

        Spacer(Modifier.height(10.dp))

        // 进度条上叠一颗主题色小圆点，标出「吃到哪儿了」
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            ThinProgressBar(progress = animatedProgress)
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier.fillMaxWidth()
            ) {
                val dotX = (maxWidth - 8.dp) * animatedProgress.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .padding(start = dotX)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(QingJiGreen)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // 预计热量缺口 = 每日预算 + 运动消耗 − 已吃（动得多缺口大）
        // v3.4.0 R5：由「运动消耗补偿」开关决定运动要不要计入
        val exerciseBonus = if (exerciseCompensation) state.exerciseKcal.toInt() else 0
        val gap = remainingKcal + exerciseBonus
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 参考图用「已吃/预算」的分数形式，比「已吃 xx 千卡」更省地方
            Text(text = "$eatenKcal/$goalKcal", fontSize = 12.sp, color = TextSecondary)
            Text(
                text = if (gap >= 0) "预计缺口 $gap 千卡" else "反超 ${-gap} 千卡",
                fontSize = 12.sp,
                color = if (gap >= 0) TextSecondary else DangerRed
            )
        }

        Spacer(Modifier.height(14.dp))
        // 灰线隔开「预计热量缺口」和「营养素」，但不分成两块卡片
        androidx.compose.material3.HorizontalDivider(color = DividerColor)
        Spacer(Modifier.height(14.dp))

        Text(text = s("营养素 (g)"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NutrientColumn(
                name = s("蛋白质"),
                current = state.totalProtein.toInt(),
                goal = profile?.proteinGoalG ?: 0,
                dotColor = MacroProtein,
                modifier = Modifier.weight(1f)
            )
            NutrientColumn(
                name = s("碳水"),
                current = state.totalCarb.toInt(),
                goal = profile?.carbGoalG ?: 0,
                dotColor = MacroCarb,
                modifier = Modifier.weight(1f)
            )
            NutrientColumn(
                name = s("脂肪"),
                current = state.totalFat.toInt(),
                goal = profile?.fatGoalG ?: 0,
                dotColor = MacroFat,
                modifier = Modifier.weight(1f)
            )
        }

        // 饮水不再单独占一块 + 四颗快捷按钮：进度收进右侧的高水杯，
        // 加水操作收进「快速记录喝水」弹层（点杯子打开），卡片更接近参考样式。

        // ---------- 轻断食条（放在「今日剩余」这张卡的最底下） ----------
        Spacer(Modifier.height(14.dp))
        if (!fastingEnabled) {
            // 2026-11-11 合并验证修：入口以前只在「已开启」后才显示，
            // 而开启开关本身在断食页里 —— 新装用户根本没有路径进断食页。
            // 未开启时也给一条浅色入口，点进去选方案。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(QingJiGreenLight)
                    .clickable { onOpenFasting() }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🕐", fontSize = 13.sp)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "轻断食 · 未开启，点这里选方案",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = QingJiGreenDark
                )
            }
        } else {

            // 每分钟刷一次，「剩余 xx:xx」才会自己走
            var fastingNow by remember { mutableStateOf(java.time.LocalTime.now()) }
            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(60_000L)
                    fastingNow = java.time.LocalTime.now()
                }
            }
            val fasting = com.qingji.app.domain.FastingRules.status(
                now = fastingNow,
                planHours = fastingPlanHours,
                eatStartMinute = fastingEatStart,
                eatEndMinute = fastingEatEnd,
                enabled = true
            )

            // v1.0.3：改成明显的实底绿按钮（之前浅色条看着不像能点）
            val fastingDaySet = fastingDays.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
            val isFastingDay = java.time.LocalDate.now().dayOfWeek.value in fastingDaySet

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(QingJiGreen)
                    .clickable { onOpenFasting() }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🕐", fontSize = 13.sp)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (!isFastingDay) {
                        "轻断食 · 今日休息"
                    } else {
                        "轻断食 · " + com.qingji.app.domain.FastingRules.planLabel(fastingPlanHours)
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = if (!isFastingDay) {
                        ""
                    } else if (fasting.active) {
                        " · 断食中 · 剩余 ${com.qingji.app.domain.FastingRules.durationLabel(fasting.remainMinutes)}"
                    } else {
                        " · 进食中"
                    },
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
            } // 左栏 Column 结束

            // 右栏：一根贯穿整张卡的高水杯，中间挂完成度胶囊，下面写「已喝/目标」
            if (showWater) {
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.width(82.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(width = 78.dp, height = 190.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        WaterCup(
                            ratio = state.waterMl.toFloat() / goalWater.coerceAtLeast(1),
                            bubbleLevel = waterBubbles,
                            modifier = Modifier.fillMaxSize(),
                            onClick = onCupClick,
                            // 参考图里水是蓝色的：固定语义色，不跟主题变
                            fillColor = MultiColor.WaterFill,
                            surfaceColor = MultiColor.WaterSurface,
                            outlineColor = MultiColor.WaterOutline,
                            bubbleTint = Color.White.copy(alpha = 0.75f),
                            // v1.5：杯内容量数字整个去掉（主人不要杯内计量数字）
                        )
                        Text(
                            text = "$waterPercent%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MultiColor.WaterText,
                            modifier = Modifier
                                .padding(bottom = 46.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MultiColor.WaterFill.copy(alpha = 0.24f))
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    // 紧凑写法，窄屏也不会被截成「3200 /」
                    Text(
                        text = "${state.waterMl}/${goalWater}ml",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }
        } // 外层 Row 结束
    } // SectionCard 结束
}

/** 营养素一列：名称、进度、目标。 */
@Composable
private fun NutrientColumn(
    name: String,
    current: Int,
    goal: Int,
    /** 进度条下面那颗小圆点，用跟主题走的三大营养素色 */
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    val percent = if (goal == 0) 0 else current * 100 / goal
    Column(modifier = modifier) {
        Text(text = name, fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$current/$goal",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        ThinProgressBar(
            progress = if (goal == 0) 0f else current.toFloat() / goal,
            barHeight = 4.dp
        )
        // 参考图里每列下面还有百分比 + 一颗彩色小点
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(4.dp))
            Text(text = "$percent%", fontSize = 10.sp, color = TextSecondary)
        }
    }
}

/** 一餐的卡片：列出这一餐吃过的每一条，右侧 × 可以删；整张卡片点进去看用餐详情。 */
@Composable
private fun MealCard(
    type: String,
    items: List<FoodLog>,
    onDelete: (Long) -> Unit,
    onOpenDetail: () -> Unit,
    /** 是否在这一餐底部显示蛋白/碳水/脂肪的比例条（首页显示设置里可关） */
    showMacro: Boolean = true
) {
    // 四餐恢复参考图的固定语义色：早餐橙 / 午餐绿 / 晚餐蓝 / 加餐紫
    val badgeColor = when (type) {
        MealRules.BREAKFAST -> MultiColor.Breakfast
        MealRules.LUNCH -> MultiColor.Lunch
        MealRules.DINNER -> MultiColor.Dinner
        else -> MultiColor.Snack
    }
    // 参考图每餐都是一枚图形图标，以前这里只放「早/午/晚」一个字
    val badgeIcon = when (type) {
        MealRules.BREAKFAST -> Icons.Outlined.WbSunny
        MealRules.LUNCH -> Icons.Outlined.Restaurant
        MealRules.DINNER -> Icons.Outlined.NightsStay
        else -> Icons.Outlined.Cake
    }
    val subtitle = items.sumOf { it.kcal }.toInt()

    SectionCard(
        contentPadding = 14.dp,
        modifier = Modifier.clickable { onOpenDetail() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "${MealRules.label(type)} (${items.size})",
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "小计 $subtitle 千卡", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.size(4.dp))
                Text(text = "›", fontSize = 18.sp, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (items.isEmpty()) {
            Text(text = s("暂无记录"), fontSize = 13.sp, color = TextSecondary)
        } else {
            items.forEachIndexed { index, log ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(DividerColor)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                com.qingji.app.ui.components.SwipeToRevealDelete(
                    onDelete = { onDelete(log.id) }
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 食物图标：按名字归类，认不出来给个通用餐盘
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PageBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = FoodIcon.emoji(log.foodName), fontSize = 15.sp)
                    }
                    Spacer(Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = log.foodName, fontSize = 14.sp, color = TextPrimary)
                            if (isAiGenerated(log)) {
                                Spacer(Modifier.size(5.dp))
                                Text(
                                    text = "AI",
                                    fontSize = 9.sp,
                                    color = QingJiGreenDark,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(QingJiGreenLight)
                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "${log.grams.toInt()} g · 蛋白 ${log.proteinG.toInt()}g",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = "${log.kcal.toInt()} 千卡",
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                }
                }   // SwipeToRevealDelete content
                Spacer(Modifier.height(8.dp))
            }
        }

        // 营养素比例条：一眼看出这一餐是碳水多、还是蛋白多
        if (showMacro && items.isNotEmpty()) {
            val proteinKcal = items.sumOf { it.proteinG } * 4.0
            val carbKcal = items.sumOf { it.carbG } * 4.0
            val fatKcal = items.sumOf { it.fatG } * 9.0
            val total = proteinKcal + carbKcal + fatKcal
            if (total > 0) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                ) {
                    Box(
                        Modifier
                            .weight((proteinKcal / total).toFloat().coerceAtLeast(0.001f))
                            .fillMaxHeight()
                            .background(MacroProtein)
                    )
                    Box(
                        Modifier
                            .weight((carbKcal / total).toFloat().coerceAtLeast(0.001f))
                            .fillMaxHeight()
                            .background(MacroCarb)
                    )
                    Box(
                        Modifier
                            .weight((fatKcal / total).toFloat().coerceAtLeast(0.001f))
                            .fillMaxHeight()
                            .background(MacroFat)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "蛋白 ${items.sumOf { it.proteinG }.toInt()}g · " +
                        "碳水 ${items.sumOf { it.carbG }.toInt()}g · " +
                        "脂肪 ${items.sumOf { it.fatG }.toInt()}g",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

/** 这条记录是不是 AI 识别出来的（手动搜索/条码不算）。 */
internal fun isAiGenerated(log: FoodLog): Boolean =
    log.source in setOf("text", "image", "voice")
