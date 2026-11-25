package com.qingji.app.ui.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.ui.viewmodel.ProgressViewModel
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.MealRules
import com.qingji.app.ui.components.AppearItem
import com.qingji.app.ui.components.QuickActionColumn
import com.qingji.app.ui.components.ThinProgressBar
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.screen.training.TodayTrainingCard
import com.qingji.app.ui.viewmodel.TrainingPlanViewModel
import com.qingji.app.ui.viewmodel.TrainingViewModel
import com.qingji.app.ui.viewmodel.SelfDisciplineViewModel
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.MultiColor
import com.qingji.app.ui.theme.pressable
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
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
    /** QJ-AI-HISTORY-DATE-V1：带首页当前选中日期进入 AI 分析（历史日期分析用）。 */
    onOpenAnalysis: (LocalDate) -> Unit = {},
    /** 统一 AI 管家入口：所有业务页上下文都进入同一会话系统。 */
    onOpenAiChat: () -> Unit = {},
    onOpenWeight: () -> Unit = {},
    onOpenImage: () -> Unit = {},
    /** 点某一餐卡片 → 进用餐详情（带那一天 + 餐次）。 */
    onOpenMealDetail: (java.time.LocalDate, String) -> Unit = { _, _ -> },
    /** 点断食条 → 进轻断食页 */
    onOpenFasting: () -> Unit = {},
    /** 点「食物库」→ 进食物库页 */
    onOpenFoodLibrary: () -> Unit = {},
    /** 2026-11-24：今日记录右侧工具图标 → 进工具页 */
    onOpenTools: () -> Unit = {},
    /** 点「今日训练」卡 → 进训练日记 */
    onOpenTraining: () -> Unit = {},
    /** 点「自律管理」卡 → 进自律管理首页 */
    onOpenSelfDiscipline: () -> Unit = {},
    /** 自律模块 VM：和模块内各页共用，首页只读主要目标和统计 */
    selfDisciplineVm: SelfDisciplineViewModel = viewModel(),
    /** 首页「本周运动消耗」卡复用进展页数据源（统一账本 + 近 30 天有氧明细） */
    progressVm: ProgressViewModel = viewModel(),
    /** 今日自律菜单的三个快捷动作（Root 解析当前主要目标） */
    onSelfDisciplineCheckin: () -> Unit = {},
    onSelfDisciplineUrge: () -> Unit = {},
    onSelfDisciplineHistory: () -> Unit = {},
    /** 记一次运动（走现有的运动选择页） */
    onOpenExercise: () -> Unit = {},
    /** 「本周运动消耗」卡改一条有氧记录（与进展页同一跳转逻辑，Root 传入） */
    onEditExercise: (com.qingji.app.data.local.entity.ExerciseLog) -> Unit = {},
    /** 健康体系 V1.1（工单 10.3）：点睡眠摘要卡 → 进睡眠记录页 */
    onOpenSleep: () -> Unit = {},
    /** 健康体系 V1.1：昨晚睡眠数据（null = 尚未记录）。Root 层 collect，保持和其它卡同一数据流模式 */
    lastNightSleep: com.qingji.app.data.local.entity.SleepRecordEntity? = null,
    /** 近 30 晚记录：首页睡眠评分的规律性项用（Root 层 collect，和睡眠页同源） */
    recentSleepNights: List<com.qingji.app.data.local.entity.SleepRecordEntity> = emptyList(),
    /** 与日记页、训练页共用同一个 ViewModel，进行中状态才对得上 */
    trainingVm: TrainingViewModel = viewModel(),
    /** P1：读当前启用的计划，给「今日训练」卡显示今日安排 */
    planVm: TrainingPlanViewModel = viewModel(),
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
    // 本周运动消耗卡（从进展页搬回首页）：复用进展页同一份账本 + 近 30 天有氧明细，
    // 同一个 progressVm 实例，显示口径与原来进展页的 ExerciseWeekCard 完全一致。
    val exerciseMonth by progressVm.exerciseMonth.collectAsState()
    val exerciseLedger by progressVm.exerciseLedger.collectAsState()
    val trainingSessions by trainingVm.sessions.collectAsState()
    val trainingDraft by trainingVm.draft.collectAsState()
    // 订阅计划相关的三份流，计划一变首页卡就跟着重组
    val activePlan by planVm.activePlan.collectAsState()
    val planDays by planVm.activePlanDays.collectAsState()
    val planExercises by planVm.planExercises.collectAsState()
    val todayArrangement = planVm.todayArrangement()
    val today = AppClock.today()

    // 首页要读几项显示设置：饮水卡显隐、AI 分析按钮显隐、悬浮按钮开关
    // TASK-05：从统一出口取值，不再自己订阅 SettingsStore。
    val context = LocalContext.current
    val settings = com.qingji.app.ui.LocalAppSettings.current

    // 日期下拉面板
    // 四餐折叠状态：key = 日期|餐次，切换日期不串用；默认「有记录的餐次展开」
    val mealExpanded = remember { mutableStateMapOf<String, Boolean>() }
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
                        .pressable { showDatePicker = true }
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
            // 目标状态（减脂/增肌/保持）：v1.0.7 挪进「今日剩余」卡标题右侧，
            // 顶部只留连续天数，两个胶囊不再挤一行。
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
            progressBarStyle = com.qingji.app.ui.components.ProgressBarStyle.fromKey(settings.progressBarStyle),
            waterCupAnimation = settings.waterCupAnimation,
            waterBubbles = settings.waterBubbles,
            exerciseCompensation = settings.exerciseCompensation,
            exerciseCompensationPct = settings.exerciseCompensationPct,
            goalMode = settings.goalMode,
            onCupClick = { showCupMenu = true },
            fastingEnabled = settings.fastingEnabled,
            fastingPlanHours = settings.fastingPlanHours,
            fastingEatStart = settings.fastingEatStartMinute,
            fastingEatEnd = settings.fastingEatEndMinute,
            fastingDays = settings.fastingDays,
            fastingFreeStart = settings.fastingFreeStart,
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
                            .pressable { onOpenAnalysis(state.date) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = s("✦ AI 管家"),
                    fontSize = 11.sp,
                    color = QingJiGreenDark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(QingJiGreenLight)
                        .pressable { onOpenAiChat() }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
            // 2026-11-24：右侧工具图标 → 工具页（卡路里换算等）
            androidx.compose.material3.IconButton(onClick = onOpenTools) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Outlined.Build,
                    contentDescription = "工具",
                    tint = QingJiGreenDark,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // 四餐固定顺序：早 / 午 / 晚 / 加餐
        MealRules.ALL.forEachIndexed { index, type ->
            val items = state.logs.filter { it.mealType == type }
            val expandKey = "${state.date}|$type"
            val expanded = mealExpanded[expandKey] ?: items.isNotEmpty()
            androidx.compose.runtime.key(expandKey) {
                AppearItem(index = index + 2) {
                    MealCard(
                        type = type,
                        items = items,
                        onDelete = vm::deleteLog,
                        onOpenDetail = { onOpenMealDetail(state.date, type) },
                        showMacro = settings.showMealMacro,
                        expanded = expanded,
                        onToggleExpand = { mealExpanded[expandKey] = !expanded }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ---------- 本周运动消耗（工单：从进展页搬回首页，紧贴「今日训练」正上方） ----------
        // 卡片本体、账本口径与显示规则都和原来进展页的 ExerciseWeekCard 完全一致，
        // 只是渲染位置换到首页；进展页入口已同步移除，不会出现两张相同的卡。
        Spacer(Modifier.height(12.dp))
        ExerciseWeekCard(
            list = exerciseMonth,
            ledger = exerciseLedger,
            onAdd = onOpenExercise,
            onEdit = onEditExercise,
            onDelete = { id -> progressVm.deleteExercise(id) }
        )

        // ---------- 今日训练（训练中心 P0，放在今日记录下面） ----------
        Spacer(Modifier.height(12.dp))
        TodayTrainingCard(
            todaySessions = trainingSessions.filter { it.date == today },
            draft = trainingDraft,
            onClick = onOpenTraining,
            arrangement = todayArrangement
        )

        // ---------- 昨晚睡眠（健康体系 V1.1，工单 10.3：首页三大模块摘要之一） ----------
        Spacer(Modifier.height(12.dp))
        SleepSummaryCard(
            lastNight = lastNightSleep,
            recentNights = recentSleepNights,
            onClick = onOpenSleep
        )

        // ---------- 自律管理快捷卡片（SD-001） ----------
        // 只在已经建过目标时出现，没建过就不占首页位置
        Spacer(Modifier.height(12.dp))
        SelfDisciplineQuickCard(
            vm = selfDisciplineVm,
            onOpen = onOpenSelfDiscipline,
            onCheckin = onSelfDisciplineCheckin,
            onUrge = onSelfDisciplineUrge,
            onHistory = onSelfDisciplineHistory
        )

        // v8：本周小结仍在进展页；本周运动消耗卡已按工单搬回首页「今日训练」正上方

        // 底部留白：给悬浮按钮腾位置，不然会盖住最后一餐。
        // v9：悬浮底栏模式再让出 Dock 高度，最后的餐卡能完整滚出来。
        Spacer(Modifier.height(if (settings.floatingBottomBar) 116.dp else 88.dp))
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
                    // 2026-11-13：和进展 / 日记两页统一成 end=16 / bottom=20
                    .padding(end = 16.dp, bottom = 20.dp)
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
            // QJ-HEALTH-DATE-WATER-004：首页喝水带日期时间（默认首页选中日期 + 当前时刻，可改）
            onAdd = { ml, date, time -> vm.addWater(ml, date, time) },
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

            // ---------- QJ-HEALTH-DATE-WATER-004：逐笔饮水明细（当前查看日期，时间升序） ----------
            Spacer(Modifier.height(14.dp))
            Text(text = s("饮水记录"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            if (state.waterLogs.isEmpty()) {
                Text(text = s("这天还没有饮水记录"), fontSize = 12.sp, color = TextSecondary)
            } else {
                state.waterLogs.forEach { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(PageBackground)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = java.time.Instant.ofEpochMilli(log.createdAt)
                                .atZone(AppClock.zone)
                                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                            fontSize = 13.sp, color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(text = "${log.ml} ml", fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, color = MultiColor.WaterText)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
