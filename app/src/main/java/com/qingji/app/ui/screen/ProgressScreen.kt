package com.qingji.app.ui.screen

import com.qingji.app.data.photo.ProgressPhotoStore
import com.qingji.app.domain.AppClock
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.domain.NutritionCalculator
import com.qingji.app.ui.screen.progress.ProgressCardActions
import com.qingji.app.ui.screen.progress.ProgressCardData
import com.qingji.app.ui.screen.progress.ProgressCardManagerSheet
import com.qingji.app.ui.screen.progress.ProgressCardRenderer
import com.qingji.app.ui.components.AppearItem
import com.qingji.app.ui.components.QuickActionColumn
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.NormalGreen
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.WarnOrange
import com.qingji.app.ui.viewmodel.ProgressViewModel
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
     * V2 工单：点水滴只打开「记录饮水」弹层，不再直接 addWater(200)。
     * 弹层由 Root 统一提供，确认后才写库。
     */
    onOpenWaterSheet: () -> Unit = {},
    /**
     * v1.0.6：「本周运动消耗」右上角「+ 记一次」改为跳运动二级页。
     * 旧的做法是在这里弹只填分钟的小面板，那样记不了步数 / 公里，
     * 两条路记出来的数据不一样，用户会以为步数白填了。
     */
    onOpenExercise: () -> Unit = {},
    /** v1.3.7：修改一条有氧记录，回调把原记录交出去（Root 找预设 id 后跳确认页） */
    onEditExercise: (com.qingji.app.data.local.entity.ExerciseLog) -> Unit = {},
    /** P1：睡眠日记卡空态的「去记录」直达睡眠页 */
    onOpenSleepDiary: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** tabfix：滚动状态由 Root 持有，切 Tab 回来不丢位置。 */
    scrollState: ScrollState = rememberScrollState(),
    /** v8：本周小结（数据源在 HomeViewModel，Root 传入）。 */
    weeklySummary: com.qingji.app.domain.WeeklySummaryCalculator.Summary,
    /** v12：卡片管理写设置用（独立实例，仅作为 DataStore 写入口）。 */
    settingsVm: com.qingji.app.ui.viewmodel.SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
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
    // P1：三种日记卡的近 30 天数据（只读展示）
    val sleepMonth by vm.sleepMonth.collectAsState()
    // QJ 日记日期查询：饮食/运动卡改用独立的单日数据（睡眠卡仍用 sleepMonth 30天方案，不动）
    val diaryDate by vm.diaryDate.collectAsState()
    val diaryFoods by vm.diaryFoods.collectAsState()
    val diaryExercises by vm.diaryExercises.collectAsState()
    // 统一运动消耗账本（力量+有氧），运动消耗卡统计口径
    val exerciseLedger by vm.exerciseLedger.collectAsState()

    var showBodyMeasure by remember { mutableStateOf(false) }
    // v3.4.0：进度照片「查看全部 / 前后对比」全屏图库
    var showPhotoGallery by remember { mutableStateOf(false) }

    // 选进度照片：选完立刻拷进 App 私有目录，原图删了也不影响
    // TASK-06-B：照片存取统一走 ProgressPhotoStore，UI 不再直接做文件 IO。
    val photoContext = androidx.compose.ui.platform.LocalContext.current
    val photoStore = remember(photoContext) { ProgressPhotoStore(photoContext) }
    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val path = photoStore.saveFromUri(uri)
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
    // TASK-05：统一从 LocalAppSettings 取，不再单独订阅 SettingsStore。
    val settings = com.qingji.app.ui.LocalAppSettings.current
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

    // ===== 身体数据口径（仪表盘工单 P0）=====
    // 关键修复：身体数据取【完整记录 allLogs】，不受曲线 rangeDays 过滤影响。
    // allLogs 来自 observeAll()=date DESC，故 BodyMetricsCalculator 取 firstOrNull（最新）。
    // 原来从过滤后的 logs 取，选近7天且该范围无记录时会错误回退到起始体重。
    val bodyMetrics = com.qingji.app.domain.BodyMetricsCalculator
    val currentWeight = bodyMetrics.latestWeightKg(allLogs, startWeight)
    val latestBodyFat = bodyMetrics.latestBodyFat(allLogs)
    val goalReached = bodyMetrics.isGoalReached(currentWeight, targetWeight)
    val bmi = if (profile != null && currentWeight > 0) {
        NutritionCalculator.bmi(currentWeight, profile!!.heightCm)
    } else 0.0
    // 年龄按生日现算周岁；缺失就是缺失，不回退默认值（工单禁止猜测）。
    val ageYears = profile?.birthday?.let {
        java.time.Period.between(it, AppClock.today()).years.takeIf { y -> y > 0 }
    }
    // 体脂展示：实测优先，无实测时用未舍入 BMI + 年龄 + 性别估算（标"估算"，不入库）。
    val bodyFatDisplay = bodyMetrics.bodyFatDisplay(latestBodyFat, bmi, ageYears, profile?.gender)
    val category = NutritionCalculator.bmiCategory(bmi)
    val categoryColor = when (category) {
        "正常" -> NormalGreen
        "超重" -> WarnOrange
        "肥胖" -> DangerRed
        else -> QingJiGreen.copy(alpha = 0.6f)
    }

    // 减重进度统一走 domain 纯函数（覆盖除零/达成/负值边界）
    val progress = if (targetWeight > 0 && startWeight > 0) {
        (bodyMetrics.lossProgressPercent(startWeight, currentWeight, targetWeight) / 100.0)
            .toFloat().coerceIn(0f, 1f)
    } else 0f
    val remain = bodyMetrics.remainingToTarget(currentWeight, targetWeight)

    // v11：卡片管理——右上角按钮弹层里调顺序/显隐（参考吃有数）。
    // TASK-07-A：顺序 / 显隐的解析逻辑搬到 domain/ProgressCardCatalog，这里只拿结果。
    // 语义不变：空串 = 默认顺序 / 全显示；未知 ID 丢弃；新卡自动补到末尾。
    // 「身体数据」卡固定最上，不在目录里，也不进管理列表。
    val cardOrder = com.qingji.app.domain.progress.ProgressCardCatalog.parseOrder(settings.progressCardOrder)
    val cardHidden = com.qingji.app.domain.progress.ProgressCardCatalog.parseHidden(settings.progressCardHidden)
    var showCardManager by remember { mutableStateOf(false) }

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

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = s("进展"),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            // v11：管理卡片入口——弹层里调进展页各卡片的顺序与显隐（参考吃有数）
            IconButton(onClick = { showCardManager = true }) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = s("管理卡片"),
                    tint = TextPrimary
                )
            }
        }

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
            // 身体数据卡的记录次数也取全量，不随曲线范围变化
            recordCount = allLogs.size,
            unit = unit,
            bodyFatDisplay = bodyFatDisplay,
            goalReached = goalReached,
            onClick = onOpenWeight
        )

        // v12：身体数据卡固定最上、不进管理列表。之前它和第一张可管理卡
        // （默认「本周运动消耗」）之间没有任何间距，两张白卡看着连成一块。
        // 补上和其它卡片一致的 12dp；forEach 里仍只从第二张起加间距。
        Spacer(Modifier.height(Dimens.Gap))

        // v11：卡片按「管理卡片」里调好的顺序渲染，关掉开关的卡不显示。
        var appearIndex = 0
        cardOrder.filter { it !in cardHidden }.forEach { cardId ->
            if (appearIndex > 0) Spacer(Modifier.height(Dimens.Gap))
            AppearItem(index = appearIndex) {
                // TASK-07-B：分派搬到 ui/screen/progress/ProgressCardRenderer.kt
                ProgressCardRenderer(
                    cardId = cardId,
                    data = ProgressCardData(
                        exerciseWeek = exerciseWeek,
                        weightLogs = logs,
                        startWeight = startWeight,
                        targetWeight = targetWeight,
                        currentWeight = currentWeight,
                        rangeDays = rangeDays,
                        unit = unit,
                        waterWeek = waterWeek,
                        waterGoalMl = settings.waterGoalMl,
                        foodWeek = foodWeek,
                        latestMeasure = bodyMeasures.firstOrNull(),
                        photos = photos,
                        sleepRecords = sleepMonth,
                        // 饮食/运动日记：只传选中日期的单日记录（不再平铺 30 天）
                        exerciseLogs = diaryExercises,
                        foodLogs = diaryFoods,
                        exerciseLedger = exerciseLedger,
                        diaryDate = diaryDate
                    ),
                    actions = ProgressCardActions(
                        onAddExercise = onOpenExercise,
                        onEditExercise = onEditExercise,
                        onDeleteExercise = { id -> vm.deleteExercise(id) },
                        onSelectRange = { days -> vm.setRange(days) },
                        onAddMeasure = { showBodyMeasure = true },
                        onAddPhoto = { pickPhoto.launch("image/*") },
                        onDeletePhoto = { id -> vm.deletePhoto(id) },
                        onViewAllPhotos = { showPhotoGallery = true },
                        onOpenSleep = onOpenSleepDiary,
                        onOpenExercise = onOpenExercise,
                        onOpenFoodLibrary = onOpenFoodLibrary,
                        onSelectDiaryDate = { vm.selectDiaryDate(it) },
                        onShiftDiaryDate = { vm.shiftDiaryDate(it) },
                        onDiaryBackToToday = { vm.backToDiaryToday() }
                    )
                )
            }
            appearIndex++
        }
        // v8：本周小结（从首页搬回进展页）——由「主题与显示」开关独立控制，
        // 不进卡片管理列表，避免两个开关打架。
        if (settings.showWeeklySummary) {
            Spacer(Modifier.height(Dimens.Gap))
            AppearItem(index = appearIndex) {
                com.qingji.app.ui.components.WeeklySummaryCard(summary = weeklySummary)
            }
        }

        // v9：悬浮底栏模式滚动尾部让出 Dock 高度，最后一块卡不被盖住
        Spacer(Modifier.height(if (settings.floatingBottomBar) 116.dp else Dimens.FabSpace))
    }

    // v11：管理卡片弹层
    if (showCardManager) {
        ProgressCardManagerSheet(
            order = cardOrder,
            hidden = cardHidden,
            defaultOrder = com.qingji.app.domain.progress.ProgressCardCatalog.DEFAULT_ORDER,
            onCommit = { order, hidden ->
                settingsVm.setProgressCardOrder(order.joinToString(","))
                settingsVm.setProgressCardHidden(hidden.joinToString(","))
            },
            onDismiss = { showCardManager = false }
        )
    }
    // 和首页一样的三个快捷按钮：在进展页也能顺手记水、记体重、拍照
    // V2：喝水统一走确认式弹层，点击本身不写任何数据
    QuickActionColumn(
        onWater = onOpenWaterSheet,
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

