package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.BodyMeasure
import com.qingji.app.data.local.entity.ExerciseLog
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.ProgressPhoto
import com.qingji.app.data.local.entity.SleepRecordEntity
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.local.entity.WeightLog
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.data.repository.SleepRepository
import com.qingji.app.data.repository.TrainingRepository
import com.qingji.app.domain.AppClock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 进展页。时间范围用「往前看多少天」表示，null = 全部。 */
class ProgressViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)
    /** 睡眠表在独立仓库，日记卡只读它，不新建 ViewModel 免得日期状态打架 */
    private val sleepRepo = SleepRepository(app)
    /** v1.3.7：肌群分布只读动作库分类 + 训练明细，只读不改，不新建 ViewModel */
    private val trainingRepo = TrainingRepository(app)

    /** 运动库（预置 + 收藏置顶） */
    val exercisePresets: StateFlow<List<com.qingji.app.data.local.entity.ExercisePreset>> =
        repo.observeExercisePresets()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleExerciseFavorite(id: Long, favorite: Boolean) {
        viewModelScope.launch { repo.setExerciseFavorite(id, favorite) }
    }

    private val _rangeDays = MutableStateFlow<Int?>(30)
    val rangeDays: StateFlow<Int?> = _rangeDays.asStateFlow()

    val profile: StateFlow<UserProfile?> = repo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val logs: StateFlow<List<WeightLog>> = repo.observeWeightLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- 近一周的区间数据（进展页新增模块用） ----------
    // 统一以「今天往前 6 天」为窗口，共 7 天。
    private val weekStart: LocalDate get() = AppClock.today().minusDays(6)

    /** 近一周每天喝了多少水（7 条，缺的那天是 0）。 */
    val waterWeek: StateFlow<List<Pair<LocalDate, Int>>> = repo
        .observeWaterRange(weekStart, AppClock.today())
        .map { list ->
            val byDate = list.groupBy { it.date }.mapValues { (_, items) -> items.sumOf { it.ml } }
            (0..6).map { offset ->
                val date = weekStart.plusDays(offset.toLong())
                date to (byDate[date] ?: 0)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 近一周每天的饮食记录（用于算热量和三大营养素）。 */
    val foodWeek: StateFlow<List<FoodLog>> = repo
        .observeFoodRange(weekStart, AppClock.today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 近 30 天饮食记录：饮食热力图用（按天聚合热量，对照目标着色）。 */
    val foodMonth: StateFlow<List<FoodLog>> = repo
        .observeFoodRange(AppClock.today().minusDays(29), AppClock.today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 本周运动记录。 */
    val exerciseWeek: StateFlow<List<ExerciseLog>> = repo
        .observeExerciseRange(weekStart, AppClock.today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 全部围度记录（最新在前）。 */
    val bodyMeasures: StateFlow<List<BodyMeasure>> = repo.observeBodyMeasures()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 进度照片（最新在前）。 */
    val photos: StateFlow<List<ProgressPhoto>> = repo.observePhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- 三种日记卡的近 30 天数据（只读展示，不落库、不改表） ----------
    private val diaryMonthStart: LocalDate get() = AppClock.today().minusDays(29)

    /** 近 30 晚睡眠（睡眠日记卡）。 */
    val sleepMonth: StateFlow<List<SleepRecordEntity>> = sleepRepo
        .observeRange(diaryMonthStart, AppClock.today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 近 30 天运动（运动日记卡）。 */
    val exerciseMonth: StateFlow<List<ExerciseLog>> = repo
        .observeExerciseRange(diaryMonthStart, AppClock.today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- v1.3.7：肌群分布（工单 4.2 第 6 项 P2，只读不改表） ----------
    /** 动作库 UUID → 七分类 key，给训练明细归类用。 */
    private val exerciseCategories: StateFlow<List<Pair<String, String>>> =
        trainingRepo.observeExercises()
            .map { list -> list.map { it.uuid to it.category } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 近 30 天力量训练明细。肌群分布只按组数与训练量聚合，不写回任何表。 */
    private val workoutMonth: StateFlow<List<com.qingji.app.data.local.entity.WorkoutExercise>> =
        trainingRepo.observeAllWorkoutExercises()
            .map { rows ->
                val from = AppClock.today().minusDays(29)
                // WorkoutExercise 没有日期列，靠会话表过滤稍重；近 30 天量小，
                // 这里先全量取，UI 只展示，不参与结算，避免过度查询
                rows.takeLast(2000)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 七类 + 未分类的组数 / 训练量，按固定顺序。 */
    val muscleDistribution: StateFlow<List<com.qingji.app.domain.MuscleDistributionCalculator.Bucket>> =
        kotlinx.coroutines.flow.combine(exerciseCategories, workoutMonth) { cats, rows ->
            com.qingji.app.domain.MuscleDistributionCalculator.compute(cats, rows)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setRange(days: Int?) {
        _rangeDays.value = days
    }

    /** 快捷按钮「水」：直接加 200ml。 */
    fun addWater(ml: Int) {
        viewModelScope.launch { repo.addWater(ml, AppClock.today()) }
    }

    fun saveBodyMeasure(measure: BodyMeasure) {
        viewModelScope.launch { repo.saveBodyMeasure(measure) }
    }

    fun addPhoto(photo: ProgressPhoto) {
        viewModelScope.launch { repo.addPhoto(photo) }
    }

    fun deletePhoto(id: Long) {
        viewModelScope.launch { repo.deletePhoto(id) }
    }

    fun addExercise(log: ExerciseLog) {
        viewModelScope.launch { repo.addExercise(log) }
    }

    fun deleteExercise(id: Long) {
        viewModelScope.launch { repo.deleteExercise(id) }
    }
}
