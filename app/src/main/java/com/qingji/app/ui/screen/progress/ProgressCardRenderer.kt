package com.qingji.app.ui.screen.progress

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qingji.app.data.local.entity.BodyMeasure
import com.qingji.app.data.local.entity.ExerciseLog
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.ProgressPhoto
import com.qingji.app.data.local.entity.SleepRecordEntity
import com.qingji.app.data.local.entity.WeightLog
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.SleepScoreCalculator
import com.qingji.app.domain.WeightForecastCalculator
import com.qingji.app.domain.progress.DiaryGrouping
import com.qingji.app.domain.progress.ProgressCardCatalog
import com.qingji.app.ui.components.WeightForecastCard
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.screen.BodyMeasureCard
import com.qingji.app.ui.screen.NutritionWeekCard
import com.qingji.app.ui.screen.ProgressPhotoCard
import com.qingji.app.ui.screen.TimeRangeChips
import com.qingji.app.ui.screen.WaterWeekCard
import com.qingji.app.ui.screen.WeightChart
import java.time.LocalDate

/**
 * 进展页卡片渲染需要的全部数据（TASK-07-B）。
 *
 * 抽成一个值对象的好处：渲染分派不再直接抓页面里的十几个局部变量，
 * 想换数据来源（比如以后接别的 ViewModel）只改构造这一处。
 */
data class ProgressCardData(
    val exerciseWeek: List<ExerciseLog> = emptyList(),
    val weightLogs: List<WeightLog> = emptyList(),
    val startWeight: Double = 0.0,
    val targetWeight: Double = 0.0,
    val currentWeight: Double = 0.0,
    val rangeDays: Int? = null,
    val unit: String = "kg",
    val waterWeek: List<Pair<LocalDate, Int>> = emptyList(),
    val waterGoalMl: Int = 3200,
    val foodWeek: List<FoodLog> = emptyList(),
    val latestMeasure: BodyMeasure? = null,
    val photos: List<ProgressPhoto> = emptyList(),
    /** 三种日记卡的近 30 天数据（只读展示，不改表） */
    val sleepRecords: List<SleepRecordEntity> = emptyList(),
    val exerciseLogs: List<ExerciseLog> = emptyList(),
    val foodLogs: List<FoodLog> = emptyList(),
    /** 统一运动消耗账本（力量+有氧），运动消耗卡统计口径 */
    val exerciseLedger: List<com.qingji.app.data.local.entity.ExerciseSessionCalorieEntity> = emptyList(),
    /** 日记日期查询：饮食/运动卡当前查看日期（独立于首页） */
    val diaryDate: LocalDate = AppClock.today()
)

/** 卡片里的交互回调，同样集中管理，渲染分派本身保持纯展示。 */
data class ProgressCardActions(
    val onAddExercise: () -> Unit = {},
    val onDeleteExercise: (Long) -> Unit = {},
    /** v1.3.7：修改一条有氧记录（工单 4.4「改」） */
    val onEditExercise: (ExerciseLog) -> Unit = {},
    val onSelectRange: (Int?) -> Unit = {},
    val onAddMeasure: () -> Unit = {},
    val onAddPhoto: () -> Unit = {},
    val onDeletePhoto: (Long) -> Unit = {},
    val onViewAllPhotos: () -> Unit = {},
    /** 三种日记卡空态的直达按钮 */
    val onOpenSleep: () -> Unit = {},
    val onOpenExercise: () -> Unit = {},
    val onOpenFoodLibrary: () -> Unit = {},
    /** 日记日期查询回调（饮食/运动共用一套日期） */
    val onSelectDiaryDate: (LocalDate) -> Unit = {},
    val onShiftDiaryDate: (Long) -> Unit = {},
    val onDiaryBackToToday: () -> Unit = {}
)

/**
 * 进展页卡片渲染分派（TASK-07-B）。
 *
 * 以前这段 when 长在 ProgressScreen 的滚动列里，夹在 Spacer / AppearItem 之间，
 * 想加一张卡得先读一百行上下文。现在独立成函数：给一个卡片 ID，
 * 它决定画哪张卡；顺序和显隐由 [ProgressCardCatalog] 算好再进来。
 *
 * 未知 ID 什么都不画（与改造前 when 无 else 分支的行为一致）。
 */
@Composable
fun ProgressCardRenderer(
    cardId: String,
    data: ProgressCardData,
    actions: ProgressCardActions
) {
    when (cardId) {
        // v13：「本周运动消耗」卡已按工单搬到首页「今日训练」正上方，
        // 进展页不再渲染，避免出现两个相同的运动消耗入口。
        ProgressCardCatalog.FORECAST -> {
            // 近 14 天趋势；数据不足时只给说明，不硬凑日期
            val forecast = WeightForecastCalculator.forecast(
                points = data.weightLogs.map {
                    WeightForecastCalculator.Point(it.date, it.weightKg)
                },
                currentWeight = data.currentWeight,
                targetWeight = data.targetWeight,
                today = AppClock.today()
            )
            WeightForecastCard(forecast = forecast)
        }

        ProgressCardCatalog.CHART -> Column {
            TimeRangeChips(selectedDays = data.rangeDays, onSelect = actions.onSelectRange)
            Spacer(Modifier.height(14.dp))
            WeightChart(
                logs = data.weightLogs,
                startWeight = data.startWeight,
                targetWeight = data.targetWeight,
                fallbackWeight = data.startWeight,
                unit = data.unit
            )
        }

        ProgressCardCatalog.WATER -> WaterWeekCard(
            week = data.waterWeek,
            goalMl = data.waterGoalMl
        )

        ProgressCardCatalog.NUTRITION -> NutritionWeekCard(foodWeek = data.foodWeek)

        ProgressCardCatalog.MEASURE -> BodyMeasureCard(
            latest = data.latestMeasure,
            onAdd = actions.onAddMeasure
        )

        ProgressCardCatalog.PHOTOS -> ProgressPhotoCard(
            photos = data.photos,
            onAdd = actions.onAddPhoto,
            onDelete = { id -> actions.onDeletePhoto(id) },
            onViewAll = actions.onViewAllPhotos
        )

        ProgressCardCatalog.SLEEP_DIARY -> {
            val groups = DiaryGrouping.groupByDate(data.sleepRecords, { it.date })
            DiarySectionCard(
                title = s("睡眠日记"),
                groups = groups,
                emptyText = s("还没有睡眠日记，记第一晚吧"),
                emptyActionText = s("去记录"),
                onEmptyAction = actions.onOpenSleep
            ) { r ->
                val minutes = r.durationMinutes
                val duration = "%d:%02d".format(minutes / 60, minutes % 60)
                val score = SleepScoreCalculator.score(r, data.sleepRecords)
                Text("🌙 $duration · ${score.score} · ${s(score.rating)}",
                    fontSize = TextSizes.Caption, color = TextPrimary,
                    modifier = Modifier.weight(1f))
                if (r.wakeCount > 0) {
                    Text("${s("夜醒")} ${r.wakeCount} ${s("次")}",
                        fontSize = TextSizes.Tiny, color = TextSecondary)
                }
            }
        }

        ProgressCardCatalog.EXERCISE_DIARY -> {
            // QJ 日记日期查询：只显示选中日期的运动记录，当天合计放日期行
            DayQueryDiaryCard(
                title = s("运动日记"),
                selectedDate = data.diaryDate,
                items = data.exerciseLogs.filter { it.date == data.diaryDate },
                dayTotalKcal = data.exerciseLogs.filter { it.date == data.diaryDate }
                    .sumOf { it.kcal.toInt() },
                emptyText = s("这天还没有运动记录"),
                emptyActionText = s("去记录"),
                onEmptyAction = actions.onOpenExercise,
                onSelectDate = actions.onSelectDiaryDate,
                onShiftDate = actions.onShiftDiaryDate,
                onBackToToday = actions.onDiaryBackToToday
            ) { log ->
                Text("🏃 ${log.type} · ${log.minutes}${s("分钟")}",
                    fontSize = TextSizes.Caption, color = TextPrimary,
                    modifier = Modifier.weight(1f))
                Text("${log.kcal.toInt()} ${s("千卡")}",
                    fontSize = TextSizes.Caption, color = QingJiGreen,
                    fontWeight = FontWeight.SemiBold)
            }
        }

        ProgressCardCatalog.FOOD_DIARY -> {
            // QJ 日记日期查询：只显示选中日期的饮食记录，同一食物多次录入各自显示不合并
            DayQueryDiaryCard(
                title = s("饮食日记"),
                selectedDate = data.diaryDate,
                items = data.foodLogs.filter { it.date == data.diaryDate },
                dayTotalKcal = data.foodLogs.filter { it.date == data.diaryDate }
                    .sumOf { it.kcal.toInt() },
                emptyText = s("这天还没有饮食记录"),
                emptyActionText = s("去记录"),
                onEmptyAction = actions.onOpenFoodLibrary,
                onSelectDate = actions.onSelectDiaryDate,
                onShiftDate = actions.onShiftDiaryDate,
                onBackToToday = actions.onDiaryBackToToday
            ) { log ->
                Text("🍽 ${log.foodName} · ${log.grams.toInt()}g",
                    fontSize = TextSizes.Caption, color = TextPrimary,
                    modifier = Modifier.weight(1f))
                Text("${log.kcal.toInt()} ${s("千卡")}",
                    fontSize = TextSizes.Caption, color = QingJiGreen,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
