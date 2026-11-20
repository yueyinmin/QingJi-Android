package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.SleepRecordEntity
import com.qingji.app.data.repository.SleepRepository
import com.qingji.app.domain.AppClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * ============================================================
 * 健康体系 V1.1 · 睡眠模块 ViewModel（工单 9）
 * ------------------------------------------------------------
 * 「昨晚」的定义：归属日期 = 今天 的那条主睡眠（今天早上起床的那觉）。
 * 无记录就是 null → 界面显示「尚未记录」，绝不显示成 0 小时（工单 9.5）。
 * 编辑时把原记录塞进 editing，保存走同一入口（工单：支持修改、删除、补录）。
 * ============================================================
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SleepRepository(app)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }
    private fun say(text: String) { _message.value = text }

    /** 正在编辑的记录（null = 新增）。 */
    private val _editing = MutableStateFlow<SleepRecordEntity?>(null)
    val editing: StateFlow<SleepRecordEntity?> = _editing.asStateFlow()

    /** 昨晚（归属今天）的睡眠记录。首页摘要卡仍用它，口径不变。 */
    val lastNight: StateFlow<SleepRecordEntity?> =
        repo.observeByDate(AppClock.today())
            .map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 睡眠页顶部日期导航选中的某一天（默认今天）。
     * 重设计后页面「先给结论再看细节」，日期决定看哪一晚的结果。
     * 不允许选未来；最早 30 天前（和统计窗口一致）。
     */
    private val _selectedDate = MutableStateFlow(AppClock.today())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /** 选中那晚的记录（无记录 = null，UI 显示空态，不显示 0 分）。 */
    val selectedNight: StateFlow<SleepRecordEntity?> =
        _selectedDate.flatMapLatest { repo.observeByDate(it) }
            .map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 日期翻页：delta = -1 昨天 / +1 明天。到边界就停住。 */
    fun shiftSelectedDate(delta: Long) {
        val next = _selectedDate.value.plusDays(delta)
        val today = AppClock.today()
        if (next > today || next.isBefore(today.minusDays(29))) return
        _selectedDate.value = next
        _editing.value = null
    }

    /** 回到今天（顶部「今天」按钮）。 */
    fun backToToday() {
        _selectedDate.value = AppClock.today()
        _editing.value = null
    }

    /**
     * 直接选中某一天（七天趋势卡点击某天调用，2026-11-19）。
     * 边界口径和 [shiftSelectedDate] 完全一致：不允许未来、不早于 30 天前；
     * 越界请求直接忽略（不改状态 → 页面不闪），并清掉编辑态。
     */
    fun selectDate(date: LocalDate) {
        val today = AppClock.today()
        if (date > today || date.isBefore(today.minusDays(29))) return
        if (date == _selectedDate.value) return
        _selectedDate.value = date
        _editing.value = null
    }

    /** 最近 30 天（趋势 + 历史列表），新到旧。 */
    val recent30: StateFlow<List<SleepRecordEntity>> =
        repo.observeRange(AppClock.today().minusDays(29), AppClock.today())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 进编辑模式：把一条历史记录载入表单。 */
    fun startEdit(record: SleepRecordEntity) { _editing.value = record }

    /** 退出编辑模式。 */
    fun cancelEdit() { _editing.value = null }

    /**
     * 保存（新增 / 编辑同一入口）。
     * 入参是「日期 + 时:分」的本地时间，转成 epoch 毫秒交给仓库统一算归属与时长。
     */
    fun save(
        sleepDate: LocalDate,
        sleepHour: Int, sleepMinute: Int,
        wakeDate: LocalDate,
        wakeHour: Int, wakeMinute: Int,
        quality: Int, morningFeeling: Int,
        wakeCount: Int, napMinutes: Int, note: String
    ) {
        viewModelScope.launch {
            val zone = AppClock.zone
            val start = sleepDate.atTime(sleepHour, sleepMinute).atZone(zone).toInstant().toEpochMilli()
            val end = wakeDate.atTime(wakeHour, wakeMinute).atZone(zone).toInstant().toEpochMilli()
            val editingRecord = _editing.value
            val result = repo.save(
                id = editingRecord?.id ?: 0L,
                startTime = start,
                endTime = end,
                quality = quality,
                morningFeeling = morningFeeling,
                wakeCount = wakeCount,
                napMinutes = napMinutes,
                note = note,
                externalId = editingRecord?.externalId.orEmpty()
            )
            result.onSuccess {
                _editing.value = null
                say("睡眠已记录")
            }.onFailure { say(it.message ?: "保存失败") }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repo.deleteById(id)
            if (_editing.value?.id == id) _editing.value = null
            say("已删除这条睡眠记录")
        }
    }
}

/** 7 天趋势的聚合结果（纯数据，UI 只管画）。 */
data class SleepTrendStats(
    val days: List<Pair<LocalDate, Double?>>,   // 每天的睡眠时长（小时）；null = 没记录
    val avgHours: Double?,                      // 平均时长；null = 期间没记录
    val recordedCount: Int
) {
    companion object {
        /** 从记录列表算最近 n 天趋势。放 companion 方便单测。 */
        fun of(records: List<SleepRecordEntity>, today: LocalDate, n: Int): SleepTrendStats {
            val byDate = records.groupBy { it.date }
            val days = (n - 1 downTo 0).map { offset ->
                val d = today.minusDays(offset.toLong())
                val hours = byDate[d]?.sumOf { it.durationMinutes }?.div(60.0)
                d to hours
            }
            val hoursList = days.mapNotNull { it.second }
            return SleepTrendStats(
                days = days,
                avgHours = hoursList.takeIf { it.isNotEmpty() }?.sum()?.div(hoursList.size),
                recordedCount = hoursList.size
            )
        }
    }
}
