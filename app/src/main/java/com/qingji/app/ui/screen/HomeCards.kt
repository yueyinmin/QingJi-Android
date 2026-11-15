package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.domain.FoodIcon
import com.qingji.app.domain.MealRules
import com.qingji.app.domain.NutritionCalculator
import com.qingji.app.domain.TimeFormat
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.components.ThinProgressBar
import com.qingji.app.ui.components.WaterCup
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.MacroCarb
import com.qingji.app.ui.theme.MacroFat
import com.qingji.app.ui.theme.MacroProtein
import com.qingji.app.ui.theme.MultiColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.theme.WarnOrange
import com.qingji.app.ui.viewmodel.HomeUiState
import java.time.LocalDate


/**
 * 今日剩余卡。
 * 全部数字来自数据库：profile 给目标，food_log 汇总给已吃，water_log 给饮水。
 * 档案还没算过目标（=0）时兜底 2000 千卡，避免除零和显示 0/0。
 */
@Composable
internal fun TodaySummaryCard(
    state: HomeUiState,
    waterGoal: Int = 3200,
    showWater: Boolean = true,
    animateProgress: Boolean = true,
    /** 水杯气泡量（设置里可调）：0 关 / 1 少 / 2 标准 / 3 多 */
    waterBubbles: Int = 2,
    /** v3.4.0 R5：关掉后运动消耗不计入预计热量缺口 */
    exerciseCompensation: Boolean = false,
    /** v1.0.7：补偿比例 0-100（默认 50），设备普遍高估消耗，全额加回会放大误差 */
    exerciseCompensationPct: Int = 50,
    /** 点水杯时通知外面弹「首页设置 / 查看详情」的选择 */
    onCupClick: () -> Unit = {},
    /** 轻断食：开了才在卡片底部显示断食按钮 */
    fastingEnabled: Boolean = false,
    fastingPlanHours: Int = 16,
    fastingEatStart: Int = 460,
    fastingEatEnd: Int = 940,
    /** v1.0.3：生效周期（周几），今天不在周期内按钮显示「今日休息」 */
    fastingDays: String = "1,2,3,4,5,6,7",
    /** 自由断食开始时刻（0 = 没有）；首页状态条三态要用 */
    fastingFreeStart: Long = 0L,
    /** 目标状态（减脂/增肌/保持）：显示在「今日剩余」标题右侧 */
    goalMode: String = "lose",
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
    // v7：切 Tab 回首页时 Composable 重建，animateFloatAsState 会从 0 重新
    // 补间一遍——主人看到的「闪一下」主要就是它和水杯涨水。首帧用 snap
    // 直接落在当前值，之后数据变化才走 400ms 补间。
    var kcalAnimReady by remember { mutableStateOf(false) }
    val animatedProgress = if (animateProgress) {
        androidx.compose.animation.core.animateFloatAsState(
            targetValue = rawProgress,
            animationSpec = if (kcalAnimReady) {
                androidx.compose.animation.core.tween(400)
            } else {
                androidx.compose.animation.core.snap()
            },
            label = "kcal-progress"
        ).value
    } else {
        rawProgress
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { kcalAnimReady = true }

    // 右侧高水杯上挂的完成度百分比
    val waterPercent = (state.waterMl * 100 / goalWater.coerceAtLeast(1)).coerceIn(0, 100)

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            // 右侧水杯跟卡片上下居中，不要贴着顶边或底边
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左栏：热量 / 营养素 / 断食条；右栏：一根贯穿卡片的高水杯
            Column(modifier = Modifier.weight(1f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = s("今日剩余"), fontSize = 14.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            // v1.0.7：目标状态从顶栏挪进来，和「今日剩余」同一行、水杯上方
            Text(
                text = when (goalMode) {
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
                    .padding(horizontal = 8.dp, vertical = 4.dp)
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
        // 预计热量缺口 = 每日预算 + 运动消耗×补偿比例 − 已吃
        // v1.0.7：全额加回会放大设备高估误差，默认只补 50%（对标竞品策略）
        val exerciseBonus = if (exerciseCompensationPct > 0) {
            (state.exerciseKcal * exerciseCompensationPct / 100).toInt()
        } else {
            0
        }
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
                        contentAlignment = Alignment.Center
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
                            // 气泡用浅水蓝，不要纯白 —— 白色小圆点看着像杯身上的脏点
                            bubbleTint = MultiColor.WaterSurface.copy(alpha = 0.9f),
                            // v1.5：杯内容量数字整个去掉（主人不要杯内计量数字）
                        )
                        // v1.0.10：水杯上叠的那个白色圆按钮去掉了。
                        // 它只是个可拖拽的完成度胶囊（纯视觉、不改水量），
                        // 在杯身上凭空多一个白圆，主人反馈「不正常」。
                        // 完成度改到杯子下方的文字里，一眼能看懂，杯身保持干净。
                    }
                    Spacer(Modifier.height(6.dp))
                    // 紧凑写法，窄屏也不会被截成「3200 /」；顺带带上完成度
                    Text(
                        text = "${state.waterMl}/${goalWater}ml · $waterPercent%",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }
        } // 外层 Row 结束

        // ---------- 轻断食状态条（2026-11-12 三态，对标竞品） ----------
        // 进食中 = 浅绿底绿字、断食中 = 浅橙底橙字、未开始 = 灰底灰字；
        // 自由断食优先于方案显示（它有自己的秒表）。浅色底不抢卡片主信息，
        // 点一下进断食页。倒计时保留：方案显示「剩 xx:xx」，自由断食直接走秒表。
        Spacer(Modifier.height(10.dp))

        // 自由断食进行中每秒走表；其余状态每分钟刷一次就够
        var statusNow by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(fastingEnabled, fastingFreeStart) {
            while (true) {
                statusNow = System.currentTimeMillis()
                kotlinx.coroutines.delay(if (fastingFreeStart > 0L) 1_000L else 60_000L)
            }
        }
        val freeStart = fastingFreeStart
        val freeRunning = freeStart > 0L && freeStart <= statusNow
        val freeScheduled = freeStart > 0L && freeStart > statusNow
        val fasting = com.qingji.app.domain.FastingRules.status(
            now = java.time.LocalTime.now(),
            planHours = fastingPlanHours,
            eatStartMinute = fastingEatStart,
            eatEndMinute = fastingEatEnd,
            enabled = fastingEnabled && freeStart <= 0L   // 自由断食优先，方案状态让位
        )
        val fastingDaySet = fastingDays.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        val isFastingDay = java.time.LocalDate.now().dayOfWeek.value in fastingDaySet

        val freeClock = TimeFormat.clock((statusNow - freeStart) / 1000L)
        val streakState = when {
            freeRunning -> Triple(
                MultiColor.FastingBackground,
                WarnOrange,
                "自由断食 · 断食中 · $freeClock"
            )
            freeScheduled -> Triple(
                MultiColor.FastingBackground,
                WarnOrange,
                "自由断食 · " + com.qingji.app.domain.FastingRules.freeScheduleLabel(freeStart, statusNow)
            )
            fastingEnabled && isFastingDay && fasting.active -> Triple(
                MultiColor.FastingBackground,
                WarnOrange,
                com.qingji.app.domain.FastingRules.planLabel(fastingPlanHours) +
                    " 轻断食 · 断食中 · 剩 " +
                    com.qingji.app.domain.FastingRules.durationLabel(fasting.remainMinutes)
            )
            fastingEnabled && isFastingDay -> Triple(
                QingJiGreenLight,
                QingJiGreenDark,
                com.qingji.app.domain.FastingRules.planLabel(fastingPlanHours) + " 轻断食 · 进食中"
            )
            fastingEnabled && !isFastingDay -> Triple(
                QingJiGreenLight,
                QingJiGreenDark,
                "轻断食 · 今日休息"
            )
            else -> Triple(
                DividerColor.copy(alpha = 0.35f),
                TextSecondary,
                "轻断食 · 未开始"
            )
        }
        val (streakBg, streakFg, streakText) = streakState

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(streakBg)
                    .clickable { onOpenFasting() }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⏱ " + streakText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = streakFg
                )
            }
        }

    } // SectionCard 结束
}


/** 营养素一列：名称、进度、目标。 */
@Composable
internal fun NutrientColumn(
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
        // v6：进度条按本列标注色（小圆点同款）做「浅 → 深」横向渐变，
        // 不再是统一的主题绿；轨道照旧 DividerColor，超 100% 夹紧到满条。
        val fraction = if (goal == 0) 0f else (current.toFloat() / goal).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(DividerColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(dotColor.copy(alpha = 0.45f), dotColor)
                        )
                    )
            )
        }
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
internal fun MealCard(
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
        // v10：右侧 44dp 让位取消——快捷按钮已默认收起，点开展开时浮到内容
        // 上层即可，主人明确「不要怕遮挡」；餐卡内容恢复满宽。
        Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        // 图标底板改成小圆角方形，和设置/我的页的图标语言统一
                        .clip(RoundedCornerShape(9.dp))
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
            val proteinKcal = items.sumOf { it.proteinG } * NutritionCalculator.KCAL_PER_G_PROTEIN
            val carbKcal = items.sumOf { it.carbG } * NutritionCalculator.KCAL_PER_G_CARB
            val fatKcal = items.sumOf { it.fatG } * NutritionCalculator.KCAL_PER_G_FAT
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
        }   // Column：悬浮按钮安全区结束
    }
}


/** 这条记录是不是 AI 识别出来的（手动搜索/条码不算）。 */
internal fun isAiGenerated(log: FoodLog): Boolean =
    log.source in setOf("text", "image", "voice")
