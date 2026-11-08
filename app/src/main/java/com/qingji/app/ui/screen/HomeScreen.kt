package com.qingji.app.ui.screen

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
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.MacroCarb
import com.qingji.app.ui.theme.MacroFat
import com.qingji.app.ui.theme.MacroProtein
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
    vm: HomeViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val today = AppClock.today()

    // 首页要读几项显示设置：饮水卡显隐、AI 分析按钮显隐、悬浮按钮开关
    val context = LocalContext.current
    val settings by remember { SettingsStore.observe(context) }
        .collectAsState(initial = QingJiSettings())

    // 点水杯弹出的三级：先选做什么 → 首页设置 / 今日详情
    var showCupMenu by remember { mutableStateOf(false) }
    var showCupSettings by remember { mutableStateOf(false) }
    var showCupDetail by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
        Spacer(Modifier.height(12.dp))

        // ---------- 顶部：日期标题 + 翻周 + 回到今天 ----------
        // 固定高度：否则「回到今天」出现/消失时整行高度会变，下面的内容跟着跳一下
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${state.date.monthValue}月${state.date.dayOfMonth}日",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.size(10.dp))
            WeekArrow(text = "‹") {
                val target = state.date.minusWeeks(1)
                vm.selectDate(target)
            }
            WeekArrow(text = "›") {
                val target = state.date.plusWeeks(1)
                if (!target.isAfter(today)) vm.selectDate(target)
            }
            Spacer(Modifier.weight(1f))
            // 这一颗始终渲染，只切透明度。
            // 之前是 if 判断，出现时会把左边挤动 → 看起来像"跳了一下"。
            val showBack = state.date != today
            Text(
                text = "回到今天",
                fontSize = 12.sp,
                color = QingJiGreenDark,
                modifier = Modifier
                    .alpha(if (showBack) 1f else 0f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (showBack) QingJiGreenLight else Color.Transparent)
                    .clickable(enabled = showBack) { vm.backToToday() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
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
            onAddWater = vm::addWater,
            waterGoal = settings.waterGoalMl,
            showWater = settings.showWaterCard,
            animateProgress = settings.progressAnimation,
            waterBubbles = settings.waterBubbles,
            onCupClick = { showCupMenu = true },
            fastingEnabled = settings.fastingEnabled,
            fastingPlanHours = settings.fastingPlanHours,
            fastingEatStart = settings.fastingEatStartMinute,
            fastingEatEnd = settings.fastingEatEndMinute,
            onOpenFasting = { onOpenFasting() }
        )
        }

        Spacer(Modifier.height(20.dp))

        // ---------- 今日记录 ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (state.date == today) "今日记录" else "当日记录",
                fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (settings.showAiAnalysis) {
                    Text(
                        text = "AI 分析",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardWhite)
                            .pressable { onOpenAnalysis() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Text(
                    text = "食物库",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardWhite)
                        .pressable { onOpenFoodLibrary() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                Text(
                    text = "+ 记一笔",
                    fontSize = 12.sp,
                    color = QingJiGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(QingJiGreenLight)
                        .pressable { onAddRecord() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
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

        // 底部留白：给悬浮按钮腾位置，不然会盖住最后一餐
        Spacer(Modifier.height(88.dp))
        }

        // ---------- 右侧悬浮快捷按钮（M2 第 4 条，可在设置里关掉） ----------
        if (settings.showQuickButtons) {
            QuickActionColumn(
                onWater = { vm.addWater(200) },
                onWeight = onOpenWeight,
                onPhoto = onOpenImage,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 20.dp)
            )
        }
    }

    // ---------- 点水杯：先选做什么 ----------
    if (showCupMenu) {
        AlertDialog(
            onDismissRequest = { showCupMenu = false },
            title = { Text("饮水", fontSize = 17.sp) },
            text = {
                Column {
                    CupMenuRow("首页显示设置") {
                        showCupMenu = false
                        showCupSettings = true
                    }
                    CupMenuRow("查看今日饮水详情") {
                        showCupMenu = false
                        showCupDetail = true
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCupMenu = false }) {
                    Text("关闭", color = TextSecondary)
                }
            }
        )
    }

    // ---------- 首页显示设置（气泡量 + 餐次营养比例） ----------
    if (showCupSettings) {
        AlertDialog(
            onDismissRequest = { showCupSettings = false },
            title = { Text("首页显示设置", fontSize = 17.sp) },
            text = {
                Column {
                    Text(text = "水杯气泡量", fontSize = 13.sp, color = TextSecondary)
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
                                    .clickable { vm.setWaterBubbles(level) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "气泡越少越省电；关掉就只剩水位",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { vm.setShowMealMacro(!settings.showMealMacro) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (settings.showMealMacro) "☑" else "☐",
                            fontSize = 15.sp,
                            color = QingJiGreen
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = "每餐卡片显示营养素比例",
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "打开后，早/午/晚/加餐卡片底部会多一条蛋白·碳水·脂肪的彩色比例条",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCupSettings = false }) {
                    Text("完成", color = QingJiGreen)
                }
            }
        )
    }

    // ---------- 今日饮水详情 ----------
    if (showCupDetail) {
        val goal = settings.waterGoalMl.coerceAtLeast(1)
        val percent = (state.waterMl * 100 / goal).coerceIn(0, 100)
        AlertDialog(
            onDismissRequest = { showCupDetail = false },
            title = { Text("今日饮水", fontSize = 17.sp) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${state.waterMl}",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3FA9C9)
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
                        color = Color(0xFF7EC8E3)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "完成 $percent%",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
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
            },
            confirmButton = {
                TextButton(onClick = { showCupDetail = false }) {
                    Text("知道了", color = QingJiGreen)
                }
            }
        )
    }
}

/** 水杯菜单里的一行。 */
@Composable
private fun CupMenuRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 15.sp,
        color = TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp)
    )
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

/** 一周日期条：基于「当前选中的那天」所在的周，点哪天看哪天。 */
@Composable
private fun DateStrip(
    state: HomeUiState,
    onSelect: (LocalDate) -> Unit
) {
    val weekLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val monday = state.date.minusDays((state.date.dayOfWeek.value - 1).toLong())
    val today = AppClock.today()

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
                    .background(if (selected) QingJiGreenLight else Color.Transparent)
                    .clickable(enabled = !isFuture) { onSelect(date) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = when {
                        isFuture -> DividerColor
                        selected -> QingJiGreenDark
                        else -> TextSecondary
                    }
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.dp,
                            color = if (selected) QingJiGreen else DividerColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${date.dayOfMonth}",
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isFuture -> DividerColor
                            selected -> QingJiGreenDark
                            else -> TextPrimary
                        }
                    )
                }
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
    onAddWater: (Int) -> Unit,
    waterGoal: Int = 3200,
    showWater: Boolean = true,
    animateProgress: Boolean = true,
    /** 水杯气泡量（设置里可调）：0 关 / 1 少 / 2 标准 / 3 多 */
    waterBubbles: Int = 2,
    /** 点水杯时通知外面弹「首页设置 / 查看详情」的选择 */
    onCupClick: () -> Unit = {},
    /** 轻断食：开了才在卡片底部显示那条断食条 */
    fastingEnabled: Boolean = false,
    fastingPlanHours: Int = 16,
    fastingEatStart: Int = 460,
    fastingEatEnd: Int = 940,
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

    // 自定义杯子容量（M3.7）：除了三个快捷按钮，也能自己填毫升数
    var showCustomWater by remember { mutableStateOf(false) }
    var customMl by remember { mutableStateOf("") }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "今日剩余", fontSize = 14.sp, color = TextSecondary)
            Text(
                text = "减肥",
                fontSize = 12.sp,
                color = QingJiGreenDark,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(QingJiGreenLight)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
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

        ThinProgressBar(progress = animatedProgress)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "已吃 $eatenKcal / $goalKcal", fontSize = 12.sp, color = TextSecondary)
            Text(
                text = if (remainingKcal >= 0) "预算还剩 $remainingKcal" else "超出 ${-remainingKcal}",
                fontSize = 12.sp,
                color = if (remainingKcal >= 0) TextSecondary else DangerRed
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(text = "营养素 (g)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NutrientColumn(
                "蛋白质", state.totalProtein.toInt(), profile?.proteinGoalG ?: 0, Modifier.weight(1f)
            )
            NutrientColumn(
                "碳水", state.totalCarb.toInt(), profile?.carbGoalG ?: 0, Modifier.weight(1f)
            )
            NutrientColumn(
                "脂肪", state.totalFat.toInt(), profile?.fatGoalG ?: 0, Modifier.weight(1f)
            )
        }

        // 饮水模块可以在设置里关掉（不喝水的人看到的全是 0，反而碍眼）
        if (showWater) {
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "饮水", fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${state.waterMl}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3FA9C9)
                        )
                        Text(
                            text = " / $goalWater ml",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (state.waterMl >= goalWater) {
                            "今天喝够了，杯子灌满啦"
                        } else {
                            "还差 ${goalWater - state.waterMl} ml"
                        },
                        fontSize = 11.sp,
                        color = if (state.waterMl >= goalWater) QingJiGreen else TextSecondary
                    )
                }

                // 计量杯：水位跟着今天的饮水涨，气泡越多说明喝得越多
                WaterCup(
                    ratio = state.waterMl.toFloat() / goalWater.coerceAtLeast(1),
                    bubbleLevel = waterBubbles,
                    modifier = Modifier.size(width = 58.dp, height = 78.dp),
                    onClick = onCupClick
                )
            }

            Spacer(Modifier.height(10.dp))

            // 四颗按钮等宽铺开。
            // 以前是自然宽度平铺，窄屏上「自定义」会被挤到第二行、甚至变成竖排字。
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(200, 300, 500).forEach { ml ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(QingJiGreenLight)
                            .pressable { onAddWater(ml) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+$ml",
                            fontSize = 12.sp,
                            color = QingJiGreenDark,
                            maxLines = 1
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardWhite)
                        .pressable { customMl = ""; showCustomWater = true }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "自定义",
                        fontSize = 11.sp,
                        color = QingJiGreenDark,
                        maxLines = 1
                    )
                }
            }
        }

        // ---------- 轻断食条（放在「今日剩余」这张卡的最底下） ----------
        if (fastingEnabled) {
            Spacer(Modifier.height(14.dp))

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(QingJiGreenLight)
                    .clickable { onOpenFasting() }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🕐", fontSize = 12.sp)
                Spacer(Modifier.size(6.dp))
                Text(
                    text = com.qingji.app.domain.FastingRules.planLabel(fastingPlanHours),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = QingJiGreenDark
                )
                Text(
                    text = if (fasting.active) {
                        " · 断食中 · 剩余 ${com.qingji.app.domain.FastingRules.durationLabel(fasting.remainMinutes)}"
                    } else {
                        " · 可以进食"
                    },
                    fontSize = 12.sp,
                    color = QingJiGreenDark
                )
            }
        }
    }

    if (showCustomWater) {
        AlertDialog(
            onDismissRequest = { showCustomWater = false },
            title = { Text("自定义这一杯", fontSize = 17.sp) },
            text = {
                Column {
                    Text(
                        text = "杯子容量按毫升填，例如 350",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = customMl,
                        onValueChange = { customMl = it },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ml = customMl.toIntOrNull()
                    if (ml != null && ml in 1..3000) {
                        onAddWater(ml)
                        showCustomWater = false
                    } else {
                        customMl = ""
                    }
                }) { Text("记下", color = QingJiGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomWater = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }
}

/** 营养素一列：名称、进度、目标。 */
@Composable
private fun NutrientColumn(
    name: String,
    current: Int,
    goal: Int,
    modifier: Modifier = Modifier
) {
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
    val badgeColor = when (type) {
        MealRules.BREAKFAST -> Color(0xFFF5A623)
        MealRules.LUNCH -> QingJiGreen
        MealRules.DINNER -> Color(0xFF5B8DEF)
        else -> Color(0xFF9B8AFB)
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
                    Text(
                        text = MealRules.label(type).take(1),
                        fontSize = 13.sp,
                        color = badgeColor,
                        fontWeight = FontWeight.Medium
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
            Text(text = "暂无记录", fontSize = 13.sp, color = TextSecondary)
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
                    Spacer(Modifier.size(12.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(PageBackground)
                            .clickable { onDelete(log.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "×", fontSize = 16.sp, color = TextSecondary)
                    }
                }
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
