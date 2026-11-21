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
import com.qingji.app.data.repository.HealthRepository
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.data.repository.SleepRepository
import com.qingji.app.domain.AppClock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 进展页。时间范围用「往前看多少天」表示，null = 全部。 */
class ProgressViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)
    /** 睡眠表在独立仓库，日记卡只读它，不新建 ViewModel 免得日期状态打架 */
    private val sleepRepo = SleepRepository(app)
    /** 统一运动消耗账本：运动消耗卡读它（力量+有氧同一口径），只读不改 */
    private val healthRepo = HealthRepository(app)

    /** 运动库（预置 + 收藏置顶） */
    val exercisePresets: StateFlow<List<com.qingji.app.data.local.entity.ExercisePreset>> =
        repo.observeExercisePresets()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleExerciseFavorite(id: Long, favorite: Boolean) {
        viewModelScope.launch { repo.setExerciseFavorite(id, favorite) }
    }

    private val _rangeDays = MutableStateFlow<Int?>(30)
    val rangeDays: StateFlow<Int?> = _rangeDays.asStateFlow()

    // ---------- QJ 日记日期查询：独立日期状态（绝不动首页 HomeViewModel._date） ----------
    /** 饮食/运动日记当前查看的日期。独立于首页日期，避免日记切日期时改了首页。 */
    private val _diaryDate = MutableStateFlow(AppClock.today())
    val diaryDate: StateFlow<LocalDate> = _diaryDate.asStateFlow()

    fun selectDiaryDate(d: LocalDate) { _diaryDate.value = d }
    fun shiftDiaryDate(delta: Long) { _diaryDate.value = _diaryDate.value.plusDays(delta) }
    fun backToDiaryToday() { _diaryDate.value = AppClock.today() }

    /** 当天饮食记录（单日查询，按现有 Repository→DAO 口径，不重算热量）。 */
    val diaryFoods: StateFlow<List<FoodLog>> = _diaryDate.flatMapLatest { d ->
        repo.observeFoodLogs(d)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当天运动记录（单日查询）。 */
    val diaryExercises: StateFlow<List<ExerciseLog>> = _diaryDate.flatMapLatest { d ->
        repo.observeExerciseLogs(d)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /**
     * 统一运动消耗账本（运动消耗卡统计用）：力量训练结算 + 有氧镜像都在这里。
     * 卡片的「总千卡 / 次数 / 分钟 / 分类」按它聚合，力量训练消耗才不会被漏掉。
     */
    val exerciseLedger: StateFlow<List<com.qingji.app.data.local.entity.ExerciseSessionCalorieEntity>> =
        healthRepo.observeAllLedger()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
