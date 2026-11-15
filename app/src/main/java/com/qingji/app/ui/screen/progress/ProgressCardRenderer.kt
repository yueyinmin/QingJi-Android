package com.qingji.app.ui.screen.progress

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qingji.app.data.local.entity.BodyMeasure
import com.qingji.app.data.local.entity.ExerciseLog
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.ProgressPhoto
import com.qingji.app.data.local.entity.WeightLog
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.WeightForecastCalculator
import com.qingji.app.domain.progress.ProgressCardCatalog
import com.qingji.app.ui.components.WeightForecastCard
import com.qingji.app.ui.screen.BodyMeasureCard
import com.qingji.app.ui.screen.ExerciseWeekCard
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
    val photos: List<ProgressPhoto> = emptyList()
)

/** 卡片里的交互回调，同样集中管理，渲染分派本身保持纯展示。 */
data class ProgressCardActions(
    val onAddExercise: () -> Unit = {},
    val onDeleteExercise: (Long) -> Unit = {},
    val onSelectRange: (Int?) -> Unit = {},
    val onAddMeasure: () -> Unit = {},
    val onAddPhoto: () -> Unit = {},
    val onDeletePhoto: (Long) -> Unit = {},
    val onViewAllPhotos: () -> Unit = {}
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
        ProgressCardCatalog.EXERCISE -> ExerciseWeekCard(
            list = data.exerciseWeek,
            onAdd = actions.onAddExercise,
            onDelete = { id -> actions.onDeleteExercise(id) }
        )

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
    }
}
