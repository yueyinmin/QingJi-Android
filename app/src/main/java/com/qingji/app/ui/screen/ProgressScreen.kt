package com.qingji.app.ui.screen

import com.qingji.app.domain.AppClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.local.entity.WeightLog
import com.qingji.app.domain.ChartMath
import com.qingji.app.domain.DiaryRules
import com.qingji.app.domain.NutritionCalculator
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.components.AppearItem
import com.qingji.app.ui.components.QuickActionColumn
import com.qingji.app.ui.components.LabeledTextField
import com.qingji.app.ui.components.ThinProgressBar
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.NormalGreen
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
import com.qingji.app.ui.theme.WarnOrange
import com.qingji.app.ui.viewmodel.ProgressViewModel
import java.time.LocalDate
import com.qingji.app.ui.i18n.s

/**
 * ============================================================
 * 进展页（M1 已换成真数据）
 * ------------------------------------------------------------
 * 数据来源：weight_log（体重流水）+ user_profile（身高、目标）。
 * 图不引第三方图表库，直接用 Compose 的 Canvas 画：
 *   - 实线 = 实际体重
 *   - 虚线 = 目标体重
 *   - 圆点 = 每次称重
 * 不引库的好处：包体积小、不会因为库升级编译不过、样式完全可控。
 * ============================================================
 */
@Composable
fun ProgressScreen(
    onOpenWeight: () -> Unit = {},
    onOpenImage: () -> Unit = {},
    /** v3.2.4：悬浮栏第二颗换成食物库 */
    onOpenFoodLibrary: () -> Unit = {},
    /**
     * v1.0.6：「本周运动消耗」右上角「+ 记一次」改为跳运动二级页。
     * 旧的做法是在这里弹只填分钟的小面板，那样记不了步数 / 公里，
     * 两条路记出来的数据不一样，用户会以为步数白填了。
     */
    onOpenExercise: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** tabfix：滚动状态由 Root 持有，切 Tab 回来不丢位置。 */
    scrollState: ScrollState = rememberScrollState(),
    /** v8：本周小结（数据源在 HomeViewModel，Root 传入）。 */
    weeklySummary: com.qingji.app.domain.WeeklySummaryCalculator.Summary,
    vm: ProgressViewModel = viewModel()
) {
    val profile by vm.profile.collectAsState()
    val allLogs by vm.logs.collectAsState()
    val rangeDays by vm.rangeDays.collectAsState()

    // 本轮新增的五个模块的数据
    val waterWeek by vm.waterWeek.collectAsState()
    val foodWeek by vm.foodWeek.collectAsState()
    val foodMonth by vm.foodMonth.collectAsState()
    val exerciseWeek by vm.exerciseWeek.collectAsState()
    val bodyMeasures by vm.bodyMeasures.collectAsState()
    val photos by vm.photos.collectAsState()

    var showBodyMeasure by remember { mutableStateOf(false) }
    // v3.4.0：进度照片「查看全部 / 前后对比」全屏图库
    var showPhotoGallery by remember { mutableStateOf(false) }

    // 选进度照片：选完立刻拷进 App 私有目录，原图删了也不影响
    val photoContext = androidx.compose.ui.platform.LocalContext.current
    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val path = savePhotoToPrivate(photoContext, uri)
            if (path != null) {
                vm.addPhoto(
                    com.qingji.app.data.local.entity.ProgressPhoto(
                        date = AppClock.today(),
                        path = path
                    )
                )
            }
        }
    }

    // M7：体重单位（kg / 斤）只影响显示，库里永远是公斤
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by androidx.compose.runtime.remember {
        com.qingji.app.data.prefs.SettingsStore.observe(context)
    }.collectAsState(initial = com.qingji.app.data.prefs.QingJiSettings())
    val unit = settings.unit

    // 按时间范围过滤，再按日期升序（画线要按时间先后）。
    // AI 专项修 off-by-one：以前 today.minusDays(days) 会把「近一周」算成 8 个自然日
    // （today-7..today），天数 chips 与真正纳入的记录对不上。改成 days-1 才是 N 天。
    val logs = allLogs
        .filter { log ->
            val days = rangeDays
            days == null || !log.date.isBefore(AppClock.today().minusDays((days - 1).toLong()))
        }
        .sortedBy { it.date }

    val startWeight = profile?.startWeight?.takeIf { it > 0 } ?: 0.0
    val targetWeight = profile?.targetWeight?.takeIf { it > 0 } ?: 0.0
    // 范围内一条记录都没有时，用引导里填的「当前体重」兜底。
    // 之前硬写 0，用户明明在引导填了身高体重，进来身体数据卡和「最新」全是灰的 --。
    // 现在引导也会写第一条体重记录（OnboardingViewModel），这里是「清除记录后」的保险，
    // 两处用同一个值，不会再出现「卡片 80kg、最新 --」的自相矛盾。
    val currentWeight = logs.lastOrNull()?.weightKg ?: startWeight
    val bmi = if (profile != null && currentWeight > 0) {
        NutritionCalculator.bmi(currentWeight, profile!!.heightCm)
    } else 0.0
    val category = NutritionCalculator.bmiCategory(bmi)
    val categoryColor = when (category) {
        "正常" -> NormalGreen
        "超重" -> WarnOrange
        "肥胖" -> DangerRed
        else -> QingJiGreen.copy(alpha = 0.6f)
    }

    // 减重进度 = 已经减掉的 / 一共要减的
    val totalToLose = startWeight - targetWeight
    val progress = if (totalToLose > 0 && currentWeight > 0) {
        ((startWeight - currentWeight) / totalToLose).toFloat().coerceIn(0f, 1f)
    } else 0f
    val remain = (currentWeight - targetWeight).coerceAtLeast(0.0)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text = s("进展"),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(Modifier.height(14.dp))

        BodyDataCard(
            profile = profile,
            currentWeight = currentWeight,
            targetWeight = targetWeight,
            bmi = bmi,
            category = category,
            categoryColor = categoryColor,
            progress = progress,
            remain = remain,
            recordCount = logs.size,
            unit = unit,
            onClick = onOpenWeight
        )

        // v1.5：两卡之间补一格间距，之前直接贴在一起
        Spacer(Modifier.height(Dimens.Gap))

        // v3.4.0 R3：目标达成预估（近 14 天趋势；数据不足时只给说明，不硬凑日期）
        val forecast = com.qingji.app.domain.WeightForecastCalculator.forecast(
            points = logs.map {
                com.qingji.app.domain.WeightForecastCalculator.Point(it.date, it.weightKg)
            },
            currentWeight = currentWeight,
            targetWeight = targetWeight,
            today = AppClock.today()
        )
        com.qingji.app.ui.components.WeightForecastCard(forecast = forecast)

        // v3.2.4：整块「BMI 分级（中国标准）」刻度条 + 四档图例已去掉，
        // BMI 改成小仪表盘收进上面的「身体数据」卡片左下角。
        Spacer(Modifier.height(14.dp))

        TimeRangeChips(selectedDays = rangeDays, onSelect = vm::setRange)

        Spacer(Modifier.height(14.dp))

        WeightChart(
            logs = logs,
            startWeight = startWeight,
            targetWeight = targetWeight,
            fallbackWeight = startWeight,
            unit = unit
        )

        Spacer(Modifier.height(Dimens.Gap))

        // ---------- 近一周饮水 ----------
        // 下面的模块依次错开入场：滚下来时一块一块浮上来，不会整屏齐刷刷出现
        AppearItem(index = 0) {
            WaterWeekCard(week = waterWeek, goalMl = settings.waterGoalMl)
        }

        Spacer(Modifier.height(Dimens.Gap))

        // ---------- 近一周营养摄入 ----------
        AppearItem(index = 1) {
            NutritionWeekCard(foodWeek = foodWeek)
        }

        Spacer(Modifier.height(Dimens.Gap))

        // 饮食 30 天已搬到「日记 → 训练日记」下方，这里不再重复展示

        // ---------- v8：本周运动消耗 + 本周小结（从首页搬回进展页，主人指定） ----------
        AppearItem(index = 2) {
            ExerciseWeekCard(
                list = exerciseWeek,
                onAdd = onOpenExercise,
                onDelete = { id -> vm.deleteExercise(id) }
            )
        }

        Spacer(Modifier.height(Dimens.Gap))

        if (settings.showWeeklySummary) {
            AppearItem(index = 2) {
                com.qingji.app.ui.components.WeeklySummaryCard(summary = weeklySummary)
            }
            Spacer(Modifier.height(Dimens.Gap))
        }

        // ---------- 身体维度 ----------
        AppearItem(index = 3) {
            BodyMeasureCard(
                latest = bodyMeasures.firstOrNull(),
                onAdd = { showBodyMeasure = true }
            )
        }

        Spacer(Modifier.height(Dimens.Gap))

        // ---------- 进度照片 ----------
        AppearItem(index = 4) {
            ProgressPhotoCard(
                photos = photos,
                onAdd = { pickPhoto.launch("image/*") },
                onDelete = vm::deletePhoto,
                onViewAll = { showPhotoGallery = true }
            )
        }

        // v9：悬浮底栏模式滚动尾部让出 Dock 高度，最后一块卡不被盖住
        Spacer(Modifier.height(if (settings.floatingBottomBar) Dimens.FabSpace + 82.dp else Dimens.FabSpace))
    }

    // 和首页一样的三个快捷按钮：在进展页也能顺手记水、记体重、拍照
    QuickActionColumn(
        onWater = { vm.addWater(200) },
        onWeight = onOpenWeight,
        onOpenFoodLibrary = onOpenFoodLibrary,
        onPhoto = onOpenImage,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 20.dp)
    )
    }

    // ---------- 录入弹窗 ----------
    if (showBodyMeasure) {
        BodyMeasureDialog(
            initial = bodyMeasures.firstOrNull(),
            onDismiss = { showBodyMeasure = false },
            onConfirm = {
                showBodyMeasure = false
                vm.saveBodyMeasure(it)
            }
        )
    }

    // v3.4.0：全部进度照片 + 选两张做前后对比
    if (showPhotoGallery) {
        com.qingji.app.ui.components.ProgressPhotoGallery(
            photos = photos,
            onDismiss = { showPhotoGallery = false }
        )
    }
}

/** 身体数据卡：当前体重、目标、BMI 分级、减重进度。 */
@Composable
private fun BodyDataCard(
    profile: UserProfile?,
    currentWeight: Double,
    targetWeight: Double,
    bmi: Double,
    category: String,
    categoryColor: Color,
    progress: Float,
    remain: Double,
    recordCount: Int,
    unit: String,
    /** 点卡片进记体重页 */
    onClick: () -> Unit = {}
) {
    // 进场动画：进度条从 0 涨到真实百分比，右上角的数字跟着一起跑。
    // 为什么要有：减肥进度是这个页面最该「有感觉」的地方，
    // 静态一条进度条看不出「我又往前走了一点」。
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val animProgress by animateFloatAsState(
        targetValue = if (entered) progress else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "progressGrow"
    )

    SectionCard(modifier = Modifier.clickable { onClick() }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = s("身体数据"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(text = s("记体重 ›"), fontSize = 11.sp, color = QingJiGreenDark)
        }

        Spacer(Modifier.height(12.dp))

        // 体重区：只放大字 + 目标，右上角不再放 BMI 数字
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (currentWeight > 0) {
                    com.qingji.app.domain.WeightUnit.format(currentWeight, unit)
                } else {
                    "--"
                },
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = " ${com.qingji.app.domain.WeightUnit.symbol(unit)}",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Text(
            text = "目标: ${
                if (targetWeight > 0) {
                    com.qingji.app.domain.WeightUnit.format(targetWeight, unit)
                } else {
                    "--"
                }
            } ${com.qingji.app.domain.WeightUnit.symbol(unit)}",
            fontSize = 12.sp,
            color = TextSecondary
        )

        // v3.2.4：BMI 小仪表盘放这块左下角，灰色细竖线隔开，右下角显示分级
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                MiniBmiGauge(bmi = bmi, categoryColor = categoryColor)
            }
            // 灰色细线（竖）：把仪表盘和分级结论分开
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(64.dp)
                    .background(DividerColor)
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = s("BMI 分级"), fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (bmi > 0) when (category) {
                        "正常" -> "健康"
                        "偏瘦" -> "偏瘦"
                        "超重" -> "超重"
                        "肥胖" -> "肥胖"
                        else -> category
                    } else "待记录",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = categoryColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(categoryColor.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(text = s("中国标准"), fontSize = 10.sp, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = s("减脂进展"), fontSize = 14.sp, color = TextPrimary)
            Text(
                text = "${(animProgress * 100).toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = QingJiGreen
            )
        }
        Spacer(Modifier.height(8.dp))
        ThinProgressBar(progress = animProgress, barHeight = 8.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (recordCount == 0) {
                "还没有体重记录"
            } else {
                "还差 ${
                    com.qingji.app.domain.WeightUnit.format(remain, unit)
                } ${com.qingji.app.domain.WeightUnit.symbol(unit)} · 已记录 $recordCount 次"
            },
            fontSize = 12.sp,
            color = TextSecondary
        )
        if (profile == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = s("个人资料还没填完整，进度可能显示为 0"),
                fontSize = 11.sp,
                color = WarnOrange
            )
        }
    }
}

/**
 * v3.2.4：缩小版 BMI 仪表盘，收进「身体数据」卡片左下角。
 * 半圆弧按中国标准分四段（偏瘦 / 健康 / 超重 / 肥胖），
 * 一根短指针指到当前 BMI，底下写数值和「BMI」标签。
 *
 * 注意：Canvas 的绘制 lambda 不是 @Composable，
 * 所有会随主题变化的颜色必须先在 Composable 作用域取成局部变量再传进去。
 */
@Composable
private fun MiniBmiGauge(bmi: Double, categoryColor: Color) {
    val hasData = bmi > 0
    val sweep = if (hasData) {
        ((bmi.coerceIn(15.0, 35.0) - 15.0) / 20.0).toFloat()
    } else 0f

    val thin = QingJiGreen.copy(alpha = 0.30f)
    val normal = QingJiGreen
    val warn = WarnOrange
    val danger = DangerRed
    val needle = TextPrimary
    val track = DividerColor

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(width = 88.dp, height = 48.dp)) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(
                width = size.width - stroke,
                height = (size.height - inset) * 2 - stroke
            )
            val topLeft = Offset(inset, inset)
            // 四段：15~18.5 / 18.5~24 / 24~28 / 28~35
            val parts = listOf(
                3.5f / 20f to thin,
                5.5f / 20f to normal,
                4f / 20f to warn,
                7f / 20f to danger
            )
            var start = 180f
            parts.forEach { (portion, color) ->
                drawArc(
                    color = color,
                    startAngle = start,
                    sweepAngle = 180f * portion,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke)
                )
                start += 180f * portion
            }
            // 没数据时盖一层灰：空表盘不至于看起来像画坏了
            if (!hasData) {
                drawArc(
                    color = track,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke)
                )
            }
            val cx = size.width / 2
            val cy = size.height - inset
            val radius = arcSize.width / 2 - stroke / 2
            if (hasData) {
                val angle = Math.toRadians((180f + 180f * sweep).toDouble())
                drawLine(
                    color = needle,
                    start = Offset(cx, cy),
                    end = Offset(
                        cx + (radius * kotlin.math.cos(angle)).toFloat(),
                        cy + (radius * kotlin.math.sin(angle)).toFloat()
                    ),
                    strokeWidth = 2.dp.toPx()
                )
            }
            drawCircle(color = needle, radius = 3.dp.toPx(), center = Offset(cx, cy))
        }
        Text(
            text = if (hasData) String.format("%.1f", bmi) else "--",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (hasData) categoryColor else TextSecondary
        )
        Text(text = "BMI", fontSize = 10.sp, color = TextSecondary)
    }
}

/** 时间范围切换：点了真的会过滤曲线数据。 */
@Composable
private fun TimeRangeChips(
    selectedDays: Int?,
    onSelect: (Int?) -> Unit
) {
    val options = listOf(
        "近一周" to 7,
        "近一个月" to 30,
        "近90天" to 90,
        "全部" to null
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(CardWhite)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        options.forEach { (label, days) ->
            val selected = selectedDays == days
            Text(
                text = label,
                fontSize = 13.sp,
                color = if (selected) QingJiGreenDark else TextSecondary,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) QingJiGreenLight else Color.Transparent)
                    .clickable { onSelect(days) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}

/**
 * 体重曲线（M4 补齐）：
 * 实线 = 实测体重；深绿斜虚线 = 目标轨迹（起始体重 → 目标体重线性插值）；
 * 浅绿横线 = 目标体重基准；手指点按能看某一天的具体数值。
 *
 * 所有坐标换算走 ChartMath（纯 Kotlin、有单元测试），包括「只有 1 条记录」
 * 这种容易崩的边界，界面这里不自己算数。
 */
@Composable
private fun WeightChart(
    logs: List<WeightLog>,
    startWeight: Double,
    targetWeight: Double,
    /** 一条记录都没有时（例如刚引导完 / 清除过记录），用档案里的当前体重当起点 */
    fallbackWeight: Double = 0.0,
    unit: String = "kg"
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    // 换了时间范围导致条数变化，清掉旧选中，避免指向不存在的下标
    LaunchedEffect(logs.size) { selectedIndex = null }

    // 进场时曲线从左往右「画」出来（0 → 1），跟手写一遍曲线一样
    var chartEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { chartEntered = true }
    val reveal by animateFloatAsState(
        targetValue = if (chartEntered) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "chartReveal"
    )

    val values = logs.map { it.weightKg.toFloat() }.ifEmpty {
        if (fallbackWeight > 0.0) listOf(fallbackWeight.toFloat()) else emptyList()
    }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = s("体重走势"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(text = "共 ${logs.size} 条记录", fontSize = 12.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PageBackground),
            contentAlignment = Alignment.Center
        ) {
            if (logs.isEmpty()) {
                Text(
                    text = "这个时间范围还没有体重记录",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(16.dp)
                        .pointerInput(logs.size) {
                            detectTapGestures { offset ->
                                selectedIndex = ChartMath.nearestIndex(
                                    offset.x, logs.size, size.width.toFloat()
                                )
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val bounds = ChartMath.paddedBounds(
                        values,
                        if (targetWeight > 0) targetWeight.toFloat() else null
                    )
                    val points = ChartMath.points(values, bounds, w, h)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

                    // ① 目标体重横向基准线
                    if (targetWeight > 0) {
                        val y = ChartMath.yOf(targetWeight.toFloat(), bounds, h)
                        drawLine(
                            color = QingJiGreenLight,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dash
                        )
                    }

                    // ② 目标轨迹：从起始体重到目标体重线性下降
                    if (startWeight > 0 && targetWeight > 0 && points.size >= 2) {
                        val trajectory = ChartMath.targetTrajectory(
                            startWeight.toFloat(), targetWeight.toFloat(), points.size
                        ).mapIndexed { index, value ->
                            Offset(points[index].x, ChartMath.yOf(value, bounds, h))
                        }
                        for (i in 1 until trajectory.size) {
                            drawLine(
                                color = QingJiGreenDark,
                                start = trajectory[i - 1],
                                end = trajectory[i],
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = dash
                            )
                        }
                    }

                    // ③ 实际体重实线 + ④ 记录点
                    // 用 clipRect 限制只画到 w * reveal 的位置，
                    // reveal 从 0 涨到 1 的过程看起来就是「线正在被画出来」。
                    clipRect(right = w * reveal) {
                        if (points.size >= 2) {
                            val path = Path().apply {
                                points.forEachIndexed { index, point ->
                                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                                }
                            }
                            drawPath(path, color = QingJiGreen, style = Stroke(width = 3.dp.toPx()))
                        }

                        // ④ 每个记录点 + 选中点高亮
                        points.forEachIndexed { index, point ->
                            val active = selectedIndex == index
                            drawCircle(
                                color = if (active) Color.White else QingJiGreen,
                                radius = if (active) 7.dp.toPx() else 4.dp.toPx(),
                                center = Offset(point.x, point.y)
                            )
                            if (active) {
                                drawCircle(
                                    color = QingJiGreen,
                                    radius = 4.dp.toPx(),
                                    center = Offset(point.x, point.y)
                                )
                            }
                        }
                    }
                }

                // 点按后显示那一天的具体数值
                selectedIndex?.let { index ->
                    logs.getOrNull(index)?.let { log ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${log.date} · ${
                                    com.qingji.app.domain.WeightUnit.formatWithSymbol(log.weightKg, unit)
                                }",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = QingJiGreenDark
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = logs.firstOrNull()?.date?.toString().orEmpty(),
                fontSize = 11.sp,
                color = TextSecondary
            )
            Row {
                LegendDot(color = QingJiGreen, label = "实际")
                Spacer(Modifier.padding(6.dp))
                if (targetWeight > 0) LegendDot(color = QingJiGreenDark, label = "目标轨迹")
            }
            Text(
                text = logs.lastOrNull()?.date?.toString().orEmpty(),
                fontSize = 11.sp,
                color = TextSecondary
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
        Spacer(Modifier.height(12.dp))

        // 最新体重 + 区间变化量（需求 M4）
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = s("最新"), fontSize = 12.sp, color = TextSecondary)
                Text(
                    text = values.lastOrNull()?.let {
                        com.qingji.app.domain.WeightUnit.formatWithSymbol(it.toDouble(), unit)
                    } ?: "--",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(text = s("本区间变化"), fontSize = 12.sp, color = TextSecondary)
                Text(
                    text = run {
                        val first = values.firstOrNull()
                        val last = values.lastOrNull()
                        when {
                            first == null || last == null -> "--"
                            kotlin.math.abs(last - first) < 0.05f -> "持平"
                            else -> com.qingji.app.domain.WeightUnit
                                .changeText((last - first).toDouble(), unit) +
                                if (last < first) " ↓" else " ↑"
                        }
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = QingJiGreen
                )
            }
        }
        if (logs.size == 1) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = s("只有 1 条记录，再记几次就能连成曲线"),
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextSecondary,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

// ============================================================
// 以下是本轮新增的五个模块（近一周饮水 / 营养摄入 / 运动消耗 /
// 身体维度 / 进度照片）。都放在进展页，共用 SectionCard 的样式。
// ============================================================

/** 七根柱子：近一周每天的饮水量。 */
/**
 * 饮食 30 天热力图（2026-11-12，对标竞品「吃有数」）。
 *
 * 网格排法复用日记模块的 DiaryRules.cells（近 30 天滚动、周一开头、前后补位），
 * 着色按当天摄入 / 目标热量：
 *   没记录 = 灰；0-80% = 绿（吃得偏少）；80-120% = 黄（贴着预算）；120% 以上 = 红（超了）。
 * 点一天，下方显示那天的摄入和目标。
 */
@Composable
fun DietHeatmapCard(
    logs: List<com.qingji.app.data.local.entity.FoodLog>,
    dailyGoal: Double
) {
    val today = AppClock.today()
    val kcalByDate = remember(logs) {
        logs.groupBy { it.date }.mapValues { entry -> entry.value.sumOf { it.kcal } }
    }
    var selected by remember { mutableStateOf<LocalDate?>(null) }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "饮食 30 天",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }

        Spacer(Modifier.height(10.dp))

        val cells = DiaryRules.cells(today, 30)
        val leading = cells.first().dayOfWeek.value - 1
        val grid: List<LocalDate?> = List(leading) { null } + cells

        // 搬到训练日记后，右侧有 4 颗悬浮按钮（50dp + 右留 16dp）。
        // 给网格整体留 44dp 安全边距，最后一列才不会被按钮挡住点不到。
        Column(
            modifier = Modifier.padding(end = 44.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            grid.chunked(7).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    row.forEach { date ->
                        if (date == null) {
                            Spacer(Modifier.weight(1f))
                            return@forEach
                        }
                        val eaten = kcalByDate[date] ?: 0.0
                        val hasRecord = eaten > 0.0
                        val isSelected = date == selected
                        val bg = when {
                            isSelected -> QingJiGreen
                            !hasRecord -> DividerColor.copy(alpha = 0.5f)
                            eaten < dailyGoal * 0.8 -> QingJiGreen
                            eaten <= dailyGoal * 1.2 -> WarnOrange
                            else -> DangerRed
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(bg)
                                .clickable { selected = if (isSelected) null else date },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Text(
                                    text = "${date.dayOfMonth}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // 图例：四种颜色（含灰=未记录），wrapContent 居中，不随宽度拉伸
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(color = QingJiGreen, label = "0-80%")
            Spacer(Modifier.width(10.dp))
            LegendDot(color = WarnOrange, label = "80-120%")
            Spacer(Modifier.width(10.dp))
            LegendDot(color = DangerRed, label = "120%+")
            Spacer(Modifier.width(10.dp))
            LegendDot(color = DividerColor.copy(alpha = 0.5f), label = "未记录")
        }

        selected?.let { day ->
            Spacer(Modifier.height(8.dp))
            val eaten = kcalByDate[day] ?: 0.0
            Text(
                text = if (eaten > 0.0) {
                    "${day.monthValue}月${day.dayOfMonth}日 · 摄入 ${eaten.toInt()} 千卡 / 目标 ${dailyGoal.toInt()} 千卡"
                } else {
                    "${day.monthValue}月${day.dayOfMonth}日 · 当天没有饮食记录"
                },
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun WaterWeekCard(week: List<Pair<LocalDate, Int>>, goalMl: Int) {
    val total = week.sumOf { it.second }
    val peak = maxOf(goalMl, week.maxOfOrNull { it.second } ?: 0, 1)
    val barColor = QingJiGreen.copy(alpha = 0.65f)

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s("近一周饮水"),
                fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(text = "共 $total ml", fontSize = TextSizes.Caption, color = TextSecondary)
        }
        Spacer(Modifier.height(12.dp))

        if (week.isEmpty()) {
            Text(text = s("还没有饮水记录"), fontSize = 13.sp, color = TextSecondary)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                week.forEach { (date, ml) ->
                    val ratio = (ml.toFloat() / peak).coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(64.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(PageBackground),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (ratio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(ratio)
                                        .background(barColor)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${date.dayOfMonth}",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "目标 $goalMl ml/天 · 柱子满格代表达标",
                fontSize = TextSizes.Tiny,
                color = TextSecondary
            )
        }
    }
}

/** 近一周营养：每天热量 + 平均三大营养素。 */
@Composable
private fun NutritionWeekCard(foodWeek: List<com.qingji.app.data.local.entity.FoodLog>) {
    val byDate = foodWeek.groupBy { it.date }
    val days = (0..6).map { offset -> AppClock.today().minusDays((6 - offset).toLong()) }
    val kcalByDay = days.map { d -> d to (byDate[d]?.sumOf { it.kcal } ?: 0.0) }
    val peak = maxOf(kcalByDay.maxOfOrNull { it.second } ?: 0.0, 1.0)
    val avgProtein = foodWeek.sumOf { it.proteinG } / 7.0
    val avgCarb = foodWeek.sumOf { it.carbG } / 7.0
    val avgFat = foodWeek.sumOf { it.fatG } / 7.0
    val totalKcal = foodWeek.sumOf { it.kcal }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = s("近一周营养摄入"),
                fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = "共 ${totalKcal.toInt()} 千卡",
                fontSize = TextSizes.Caption,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            kcalByDay.forEach { (date, kcal) ->
                val ratio = (kcal / peak).coerceIn(0.0, 1.0).toFloat()
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(64.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(PageBackground),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (ratio > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(ratio)
                                    .background(QingJiGreen)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(text = "${date.dayOfMonth}", fontSize = 10.sp, color = TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MacroCell("日均蛋白", avgProtein, MacroProtein)
            MacroCell("日均碳水", avgCarb, MacroCarb)
            MacroCell("日均脂肪", avgFat, MacroFat)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = s("按最近 7 天平均算；没记的日子也计入分母"),
            fontSize = TextSizes.Tiny,
            color = TextSecondary
        )
    }
}

@Composable
private fun MacroCell(label: String, grams: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${grams.toInt()} g",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Spacer(Modifier.height(2.dp))
        Text(text = label, fontSize = TextSizes.Tiny, color = TextSecondary)
    }
}

/** 本周运动消耗。 */
@Composable
fun ExerciseWeekCard(
    list: List<com.qingji.app.data.local.entity.ExerciseLog>,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit
) {
    val total = list.sumOf { it.kcal }

    // 360dp 窄屏走查：右侧 QuickActionColumn 是 4 颗 52dp 圆按钮、右留 16dp，
    // 浮在内容之上，占住屏幕右侧 (W-68dp) ~ (W-16dp) 这一条。
    // 而卡片内容区右边界是 (W-16dp 外层 - 16dp SectionCard) = W-32dp，
    // 比悬浮栏左边界还靠右 36dp —— 行尾的「×」和「+ 记一次」正好落进按钮热区，
    // 点下去全被悬浮按钮吃掉（第一行 360dp 下完全点不到）。
    // 给卡内两处行尾统一留 44dp 安全边距（36dp 理论值 + 8dp 余量）：
    // 卡片本身宽度、标题位置、与其它卡片的对齐都不变，只是内容避开悬浮区。
    val fabSafeEnd = 44.dp

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = fabSafeEnd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = s("本周运动消耗"),
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "共 ${total.toInt()} 千卡 · ${list.size} 次",
                    fontSize = TextSizes.Caption,
                    color = TextSecondary
                )
            }
            Text(
                text = s("+ 记一次"),
                fontSize = 12.sp,
                color = QingJiGreenDark,
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.TinyCorner))
                    .background(QingJiGreenLight)
                    .pressable { onAdd() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        if (list.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            list.takeLast(3).reversed().forEach { log ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .padding(end = fabSafeEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🏃", fontSize = 14.sp)
                    Spacer(Modifier.size(8.dp))
                    // v1.0.6：原来一行塞「日期 类型 N 分钟」，再想显示步数就放不下了。
                    // 改成两行：第一行日期 + 类型，第二行明细（分钟一定有，步数/公里
                    // 只在用户确实按那个方式记过时才出现，都记为 0 时不显示假数据）。
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${log.date.monthValue}/${log.date.dayOfMonth} ${log.type}",
                            fontSize = 12.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = exerciseDetail(log),
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(text = "${log.kcal.toInt()} 千卡", fontSize = 12.sp, color = QingJiGreenDark)
                    Spacer(Modifier.size(6.dp))
                    // 原来只是 Text 加 4dp 内边距，点击热区不到 24dp，
                    // 就算没被悬浮栏挡住也很难点中；改成 36dp 透明方形热区，
                    // 视觉上还是同一个「×」，只是更容易点。
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onDelete(log.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "×", fontSize = 14.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

/**
 * 运动记录的第二行明细（v1.0.6）。
 *
 * 分钟一定有（任何方式记录都会换算出分钟）；步数和公里只在用户
 * 确实按那个方式填过时才显示 —— 存的是 0 表示「当时没按这个方式记」，
 * 不是「走了 0 步」，所以不能显示成「0 步」。
 */
private fun exerciseDetail(log: com.qingji.app.data.local.entity.ExerciseLog): String {
    val parts = mutableListOf("${log.minutes} 分钟")
    if (log.steps > 0) parts.add("${log.steps} 步")
    if (log.distanceKm > 0) parts.add(kmText(log.distanceKm))
    return parts.joinToString(" · ")
}

/** 公里数去掉多余的 .0：1.0 → 「1 公里」，5.5 → 「5.5 公里」。 */
private fun kmText(km: Double): String {
    val raw = String.format(java.util.Locale.US, "%.1f", km)
    val trimmed = if (raw.endsWith(".0")) raw.dropLast(2) else raw
    return "$trimmed 公里"
}

/** 身体维度：最新一次量的五个围度。 */
@Composable
private fun BodyMeasureCard(
    latest: com.qingji.app.data.local.entity.BodyMeasure?,
    onAdd: () -> Unit
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = s("身体维度"),
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (latest == null) "还没量过围度" else "最近一次：${latest.date}",
                    fontSize = TextSizes.Caption,
                    color = TextSecondary
                )
            }
            Text(
                text = if (latest == null) "+ 记录" else "更新",
                fontSize = 12.sp,
                color = QingJiGreenDark,
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.TinyCorner))
                    .background(QingJiGreenLight)
                    .pressable { onAdd() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        if (latest == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = s("量一次腰围、胸围、臀围，变化比体重更早看得出来"),
                fontSize = 12.sp,
                color = TextSecondary
            )
        } else {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MeasureCell("腰围", latest.waistCm)
                MeasureCell("胸围", latest.chestCm)
                MeasureCell("臀围", latest.hipCm)
                MeasureCell("大腿", latest.thighCm)
                MeasureCell("上臂", latest.armCm)
            }
        }
    }
}

@Composable
private fun MeasureCell(label: String, value: Double?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (value == null) "—" else "${value.toInt()}",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(2.dp))
        Text(text = "$label cm", fontSize = 10.sp, color = TextSecondary)
    }
}

/** 进度照片：横向缩略图。 */
@Composable
private fun ProgressPhotoCard(
    photos: List<com.qingji.app.data.local.entity.ProgressPhoto>,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
    onViewAll: () -> Unit = {}
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = s("进度照片"),
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (photos.isEmpty()) "拍一张留个底，过段时间对比最有感觉" else "${photos.size} 张",
                    fontSize = TextSizes.Caption,
                    color = TextSecondary
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (photos.isNotEmpty()) {
                    Text(
                        text = s("查看全部"),
                        fontSize = 12.sp,
                        color = QingJiGreenDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.TinyCorner))
                            .background(QingJiGreenLight)
                            .pressable { onViewAll() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Text(
                    text = s("+ 添加"),
                    fontSize = 12.sp,
                    color = QingJiGreenDark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.TinyCorner))
                        .background(QingJiGreenLight)
                        .pressable { onAdd() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(Dimens.ItemCorner))
                    .background(PageBackground)
                    .pressable { onAdd() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = s("点这里从相册选一张"), fontSize = 12.sp, color = TextSecondary)
            }
        } else {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(photos.size, key = { photos[it].id }) { index ->
                    val photo = photos[index]
                    Box {
                        coil.compose.AsyncImage(
                            model = java.io.File(photo.path),
                            contentDescription = "进度照片",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(Dimens.ItemCorner))
                                .background(PageBackground)
                                .clickable { onViewAll() }
                        )
                        Text(
                            text = "×",
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(Color(0x99000000))
                                .clickable { onDelete(photo.id) }
                                .padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 围度录入弹窗。留空的项不会存。 */
@Composable
private fun BodyMeasureDialog(
    initial: com.qingji.app.data.local.entity.BodyMeasure?,
    onDismiss: () -> Unit,
    onConfirm: (com.qingji.app.data.local.entity.BodyMeasure) -> Unit
) {
    var waist by remember { mutableStateOf(initial?.waistCm?.toInt()?.toString().orEmpty()) }
    var chest by remember { mutableStateOf(initial?.chestCm?.toInt()?.toString().orEmpty()) }
    var hip by remember { mutableStateOf(initial?.hipCm?.toInt()?.toString().orEmpty()) }
    var thigh by remember { mutableStateOf(initial?.thighCm?.toInt()?.toString().orEmpty()) }
    var arm by remember { mutableStateOf(initial?.armCm?.toInt()?.toString().orEmpty()) }

    com.qingji.app.ui.components.AppBottomSheet(
        title = "记一组围度",
        subtitle = "只填量过的就行，单位厘米",
        confirmText = "保存",
        onConfirm = {
            onConfirm(
                com.qingji.app.data.local.entity.BodyMeasure(
                    date = AppClock.today(),
                    waistCm = waist.toDoubleOrNull(),
                    chestCm = chest.toDoubleOrNull(),
                    hipCm = hip.toDoubleOrNull(),
                    thighCm = thigh.toDoubleOrNull(),
                    armCm = arm.toDoubleOrNull()
                )
            )
        },
        onDismiss = onDismiss
    ) {
        Column {
            MeasureField("腰围", waist) { waist = it }
            MeasureField("胸围", chest) { chest = it }
            MeasureField("臀围", hip) { hip = it }
            MeasureField("大腿围", thigh) { thigh = it }
            MeasureField("上臂围", arm) { arm = it }
        }
    }
}

@Composable
private fun MeasureField(label: String, value: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
            ),
            modifier = Modifier.width(110.dp)
        )
    }
}

/** 把相册选的图拷进 App 私有目录，返回本地路径。 */
private fun savePhotoToPrivate(context: android.content.Context, uri: android.net.Uri): String? = try {
    val dir = java.io.File(context.filesDir, "progress").apply { mkdirs() }
    val target = java.io.File(dir, "photo_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    }
    target.absolutePath
} catch (e: Exception) {
    null
}
