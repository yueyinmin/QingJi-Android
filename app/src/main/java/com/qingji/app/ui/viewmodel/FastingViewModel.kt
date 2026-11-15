package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.prefs.SettingsKeys
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.FastingRules
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 轻断食页的数据管家。
 *
 * 断食状态不需要后台服务：每次打开页面、或者每 30 秒 tick 一下，
 * 用「当前时间 + 设置里的进食窗口」重新算一遍就行，省电也不出错。
 */
class FastingViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    val settings: StateFlow<com.qingji.app.data.prefs.QingJiSettings> =
        com.qingji.app.data.prefs.SettingsStore.observe(app)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                com.qingji.app.data.prefs.QingJiSettings()
            )

    /** 近 7 天断食记录，用来画柱状图 */
    val weekLogs: StateFlow<List<com.qingji.app.data.local.entity.FastingLog>> = repo
        .observeFastingRange(AppClock.today().minusDays(6), AppClock.today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** v1.0.3：近 30 天记录倒序，历史记录卡用 */
    val historyLogs: StateFlow<List<com.qingji.app.data.local.entity.FastingLog>> = repo
        .observeFastingRange(AppClock.today().minusDays(29), AppClock.today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 近一年断食记录：算「连续断食天数」用。
     * 历史卡只看 30 天，但连续天数要一路往前数，30 天不够。
     */
    val yearLogs: StateFlow<List<com.qingji.app.data.local.entity.FastingLog>> = repo
        .observeFastingRange(AppClock.today().minusDays(365), AppClock.today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前时刻，每 30 秒刷一次，倒计时才会走 */
    var now by androidx.compose.runtime.mutableStateOf(java.time.LocalTime.now())
        private set

    fun tick() {
        now = java.time.LocalTime.now()
    }

    fun saveSettings(
        enabled: Boolean, planHours: Int, startMinute: Int, endMinute: Int,
        days: String, phaseAlert: Boolean
    ) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            com.qingji.app.data.prefs.SettingsStore.set(ctx, com.qingji.app.data.prefs.SettingsKeys.FASTING_ENABLED, enabled)
            com.qingji.app.data.prefs.SettingsStore.set(ctx, com.qingji.app.data.prefs.SettingsKeys.FASTING_PLAN_HOURS, planHours)
            com.qingji.app.data.prefs.SettingsStore.set(ctx, com.qingji.app.data.prefs.SettingsKeys.FASTING_EAT_START, startMinute)
            com.qingji.app.data.prefs.SettingsStore.set(ctx, com.qingji.app.data.prefs.SettingsKeys.FASTING_EAT_END, endMinute)
            // v1.0.3：生效周期 + 时段切换提醒；提醒排布走 Reminders.apply（settings 变化自动重算）
            com.qingji.app.data.prefs.SettingsStore.set(ctx, com.qingji.app.data.prefs.SettingsKeys.FASTING_DAYS, days)
            com.qingji.app.data.prefs.SettingsStore.set(ctx, com.qingji.app.data.prefs.SettingsKeys.FASTING_PHASE_ALERT, phaseAlert)
            // 用刚保存的值重排提醒（不等 Flow 回流）
            com.qingji.app.notify.Reminders.apply(
                ctx,
                settings.value.copy(
                    fastingEnabled = enabled,
                    fastingPlanHours = planHours,
                    fastingEatStartMinute = startMinute,
                    fastingEatEndMinute = endMinute,
                    fastingDays = days,
                    fastingPhaseAlert = phaseAlert
                )
            )
            // 顺手把今天这条记录定下来，近 7 日柱状图才有数据
            repo.saveFasting(
                com.qingji.app.data.local.entity.FastingLog(
                    date = AppClock.today(),
                    planHours = planHours,
                    eatStartMinute = startMinute,
                    eatEndMinute = endMinute
                )
            )
        }
    }

    /** 只改方案小时数；进食窗口、生效星期、提醒开关保持不变。方案卡只在已启用时显示。 */
    fun updatePlanHours(hours: Int) {
        val s = settings.value
        // v1.0.7：可进食时间自动对标方案（断食开始 + 方案小时数，跨零点取模），不再单独存
        val end = s.fastingEatEndMinute
        saveSettings(
            enabled = true,
            planHours = hours,
            startMinute = (end + hours * 60) % 1440,
            endMinute = end,
            days = s.fastingDays,
            phaseAlert = s.fastingPhaseAlert
        )
    }

    /**
     * 一键开启轻断食（2026-11-11 改版）。
     *
     * 沿用当前方案和进食窗口，顺手把「时段切换提醒」打开 ——
     * 用户要的就是「选了就自动提醒」，不该让他再去设置里找开关。
     */
    fun enableFasting() {
        val s = settings.value
        // 可进食时间按方案自动对标（断食开始 + 方案小时数）
        val end = s.fastingEatEndMinute
        saveSettings(
            enabled = true,
            planHours = s.fastingPlanHours,
            startMinute = (end + s.fastingPlanHours * 60) % 1440,
            endMinute = end,
            days = s.fastingDays,
            phaseAlert = true
        )
    }

    /** 断食常驻通知开关（前台服务只负责把现有断食状态挂到通知栏）。 */
    fun setOngoingNotification(enabled: Boolean) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            com.qingji.app.data.prefs.SettingsStore.set(
                ctx,
                com.qingji.app.data.prefs.SettingsKeys.FASTING_ONGOING_NOTIFICATION,
                enabled
            )
            com.qingji.app.notify.FastingService.sync(ctx)
        }
    }

    // ---------- 自由断食（2026-11-11 专项） ----------

    /** 给界面的一句话反馈：重复开始被拒、时长太短等。空串 = 没消息。 */
    var freeMessage by androidx.compose.runtime.mutableStateOf("")
        private set

    fun clearFreeMessage() {
        freeMessage = ""
    }

    /**
     * 开始一段自由断食。
     *
     * [startAtMillis] 允许是过去的时间（比如「昨晚 8 点开始的」），
     * 但不能是未来、也不能早于 72 小时前，否则拒绝并给一句提示。
     * 已经有一段在进行时直接忽略 —— 重复点「开始」不会把计时清零。
     */
    fun startFreeFasting(startAtMillis: Long = System.currentTimeMillis()) {
        if (settings.value.fastingFreeStart > 0L) {
            freeMessage = "已经有一段自由断食在进行中，先结束它再开始新的"
            return
        }
        val now = System.currentTimeMillis()
        val issue = FastingRules.freeStartIssue(startAtMillis, now)
        if (issue != null) {
            freeMessage = issue
            return
        }
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            com.qingji.app.data.prefs.SettingsStore.set(
                ctx,
                com.qingji.app.data.prefs.SettingsKeys.FASTING_FREE_START,
                startAtMillis
            )
            freeMessage = ""
            com.qingji.app.notify.FastingService.sync(ctx)
        }
    }

    /** 取消一段还没开始的预约（只有预约状态才有意义，进行中的请走「结束并保存」）。 */
    fun cancelFreeFasting() {
        if (settings.value.fastingFreeStart <= 0L) {
            freeMessage = "当前没有进行中或预约中的自由断食"
            return
        }
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            com.qingji.app.data.prefs.SettingsStore.set(
                ctx,
                com.qingji.app.data.prefs.SettingsKeys.FASTING_FREE_START,
                0L
            )
            com.qingji.app.notify.FastingService.sync(ctx)
            freeMessage = "已取消预约"
        }
    }

    /**
     * 结束自由断食：按真实起止算分钟数，写进当天记录。
     *
     * 注意 `fasting_log` 主键是日期、一天一条，所以当天已有方案记录时会被这条覆盖 ——
     * 这是既定表结构决定的，不额外改表；覆盖后历史卡按「自由断食」样式显示。
     */
    fun endFreeFasting() {
        val start = settings.value.fastingFreeStart
        if (start <= 0L) {
            freeMessage = "当前没有进行中的自由断食"
            return
        }
        val now = System.currentTimeMillis()
        val minutes = FastingRules.freeElapsedMinutes(start, now)
        val issue = FastingRules.freeEndIssue(minutes)
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            if (issue != null) {
                // 太短：清掉状态，不写脏记录
                com.qingji.app.data.prefs.SettingsStore.set(
                    ctx,
                    com.qingji.app.data.prefs.SettingsKeys.FASTING_FREE_START,
                    0L
                )
                com.qingji.app.notify.FastingService.sync(ctx)
                freeMessage = issue
                return@launch
            }
            val zone = java.time.ZoneId.systemDefault()
            val startTime = java.time.Instant.ofEpochMilli(start).atZone(zone).toLocalTime()
            val endTime = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalTime()
            repo.saveFasting(
                com.qingji.app.data.local.entity.FastingLog(
                    date = AppClock.today(),
                    planHours = FastingRules.freePlanHours(minutes),
                    eatStartMinute = startTime.hour * 60 + startTime.minute,
                    eatEndMinute = endTime.hour * 60 + endTime.minute,
                    completed = true,
                    freeMode = true,
                    actualMinutes = minutes
                )
            )
            com.qingji.app.data.prefs.SettingsStore.set(
                ctx,
                com.qingji.app.data.prefs.SettingsKeys.FASTING_FREE_START,
                0L
            )
            com.qingji.app.notify.FastingService.sync(ctx)
            freeMessage = "已记录：${FastingRules.freeDurationLabel(minutes)}"
        }
    }
}
