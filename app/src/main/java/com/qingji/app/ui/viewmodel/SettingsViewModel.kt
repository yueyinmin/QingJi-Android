package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.data.prefs.SettingsKeys
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import com.qingji.app.notify.Reminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ============================================================
 * 设置页的数据管家（M7）
 * ------------------------------------------------------------
 * 每一行设置背后都是「存进 DataStore → 界面立刻变」这一条路。
 * 这里额外做两件容易漏的事：
 * 1. 改营养素比例时，顺手把档案里的蛋白质/碳水/脂肪克数重算一遍
 *    （不然设置里改了比例，首页数字纹丝不动，用户会以为坏了）；
 * 2. 改提醒相关的任何一项，都把系统闹钟整体重排一次。
 * ============================================================
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext
    private val repo = QingJiRepository(context)

    val settings: StateFlow<QingJiSettings> = SettingsStore.observe(context)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QingJiSettings())

    val profile: StateFlow<UserProfile?> = repo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 一次性操作的结果提示（保存成功、清除完成之类）。 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    private fun toast(text: String) {
        _message.value = text
    }

    // ---------- 通用 ----------

    fun setTheme(mode: String) = put(SettingsKeys.THEME_MODE, mode)

    fun setUnit(unit: String) = put(SettingsKeys.UNIT, unit)

    fun setTimezone(zoneId: String) {
        viewModelScope.launch {
            SettingsStore.set(context, SettingsKeys.TIME_ZONE, zoneId)
            // 全局时钟立刻生效，页面里的「今天」不用等重启
            AppClock.setZone(zoneId)
        }
    }

    /** 饮水目标：设置和档案各存一份（首页读设置，小组件读档案）。 */
    fun setWaterGoal(ml: Int) {
        viewModelScope.launch {
            val safe = ml.coerceIn(500, 6000)
            SettingsStore.set(context, SettingsKeys.WATER_GOAL, safe)
            profile.value?.let { repo.saveProfile(it.copy(waterGoalMl = safe)) }
            toast("饮水目标已改为 $safe ml")
        }
    }

    /**
     * 三大营养素比例。
     * 克数按「热量 ÷ 每克热量」算：蛋白 4 千卡/g、碳水 4 千卡/g、脂肪 9 千卡/g。
     * 比例之和不等于 100 时，界面会提示，但这里照存 —— 用户可能故意这么配。
     */
    fun setMacroRatio(proteinPct: Int, carbPct: Int, fatPct: Int) {
        viewModelScope.launch {
            val p = proteinPct.coerceIn(0, 100)
            val c = carbPct.coerceIn(0, 100)
            val f = fatPct.coerceIn(0, 100)
            SettingsStore.set(context, SettingsKeys.PROTEIN_PCT, p)
            SettingsStore.set(context, SettingsKeys.CARB_PCT, c)
            SettingsStore.set(context, SettingsKeys.FAT_PCT, f)

            val current = profile.value
            if (current != null) {
                val kcal = if (current.dailyKcalGoal > 0) current.dailyKcalGoal else 2000
                repo.saveProfile(
                    current.copy(
                        proteinGoalG = (kcal * p / 100.0 / 4.0).toInt(),
                        carbGoalG = (kcal * c / 100.0 / 4.0).toInt(),
                        fatGoalG = (kcal * f / 100.0 / 9.0).toInt()
                    )
                )
            }
            toast("已按 $p% / $c% / $f% 重新计算每日目标")
        }
    }

    fun setExerciseCompensation(enabled: Boolean) =
        put(SettingsKeys.EXERCISE_COMPENSATION, enabled)

    fun setDietPreference(text: String) = put(SettingsKeys.DIET_PREFERENCE, text)

    fun setAutoMeal(enabled: Boolean) = put(SettingsKeys.AUTO_MEAL, enabled)

    fun setConservativeEstimate(enabled: Boolean) =
        put(SettingsKeys.CONSERVATIVE_ESTIMATE, enabled)

    fun setProgressAnimation(enabled: Boolean) = put(SettingsKeys.PROGRESS_ANIMATION, enabled)

    fun setShowAiAnalysis(show: Boolean) = put(SettingsKeys.SHOW_AI_ANALYSIS, show)

    fun setShowWaterCard(show: Boolean) = put(SettingsKeys.SHOW_WATER_CARD, show)

    /** 水杯气泡量：0 关 / 1 少 / 2 标准 / 3 多 */
    fun setWaterBubbles(level: Int) = put(SettingsKeys.WATER_BUBBLES, level.coerceIn(0, 3))

    /** 禁用所有通知（总开关） */
    fun setAllNotificationsDisabled(disabled: Boolean) = put(SettingsKeys.ALL_NOTIFICATIONS_DISABLED, disabled)

    /** AI 返回语言：zh 强制中文 / auto 跟随输入 */
    fun setAiLanguage(lang: String) = put(SettingsKeys.AI_LANGUAGE, lang)

    /** 首页是否显示实时刷新率 */
    fun setShowRefreshRate(show: Boolean) = put(SettingsKeys.SHOW_REFRESH_RATE, show)

    /** 过敏原（逗号分隔） */
    fun setAllergens(value: String) = put(SettingsKeys.ALLERGENS, value)

    /** 忌口食物（逗号分隔） */
    fun setAvoidFoods(value: String) = put(SettingsKeys.AVOID_FOODS, value)

    /** 最长烹饪时长（分钟），0 = 不限 */
    fun setMaxCookMinutes(minutes: Int) = put(SettingsKeys.MAX_COOK_MINUTES, minutes)

    /** 偏好菜系（逗号分隔） */
    fun setCuisines(value: String) = put(SettingsKeys.CUISINES, value)

    /** 主题色：green / blue / purple / orange / pink */
    fun setThemeColor(key: String) = put(SettingsKeys.THEME_COLOR, key)

    /** 首页每餐卡片是否显示营养素比例条 */
    fun setShowMealMacro(show: Boolean) = put(SettingsKeys.SHOW_MEAL_MACRO, show)

    fun setShowQuickButtons(show: Boolean) = put(SettingsKeys.SHOW_QUICK_BUTTONS, show)

    /** 底部导航显隐：首页不能关（关了就没地方可去了）。 */
    fun setTabVisible(tab: String, visible: Boolean) {
        when (tab) {
            "progress" -> put(SettingsKeys.SHOW_PROGRESS_TAB, visible)
            "diary" -> put(SettingsKeys.SHOW_DIARY_TAB, visible)
            "mine" -> put(SettingsKeys.SHOW_MINE_TAB, visible)
            else -> Unit
        }
    }

    fun setNickname(name: String) = put(SettingsKeys.NICKNAME, name.trim().ifBlank { "木林" })

    fun setAvatarPath(path: String) = put(SettingsKeys.AVATAR_PATH, path)

    // ---------- 提醒 ----------

    fun setWeighReminder(enabled: Boolean, hour: Int = settings.value.reminderHour) {
        viewModelScope.launch {
            SettingsStore.set(context, SettingsKeys.WEIGH_REMINDER, enabled)
            SettingsStore.set(context, SettingsKeys.REMINDER_HOUR, hour.coerceIn(0, 23))
            applyReminders()
            toast(if (enabled) "称重提醒已开启（每天 $hour 点）" else "称重提醒已关闭")
        }
    }

    fun setWeighWeekday(weekday: Int, hour: Int = settings.value.reminderHour) {
        viewModelScope.launch {
            SettingsStore.set(context, SettingsKeys.WEIGH_WEEKDAY, weekday.coerceIn(1, 7))
            SettingsStore.set(context, SettingsKeys.REMINDER_HOUR, hour.coerceIn(0, 23))
            applyReminders()
        }
    }

    fun setFastingReminder(enabled: Boolean, hour: Int = settings.value.fastStartHour) {
        viewModelScope.launch {
            SettingsStore.set(context, SettingsKeys.FASTING_REMINDER, enabled)
            SettingsStore.set(context, SettingsKeys.FAST_START_HOUR, hour.coerceIn(0, 23))
            applyReminders()
            toast(if (enabled) "断食提醒已开启（每天 $hour 点）" else "断食提醒已关闭")
        }
    }

    fun setKcalReminder(enabled: Boolean, hour: Int = settings.value.kcalReminderHour) {
        viewModelScope.launch {
            SettingsStore.set(context, SettingsKeys.KCAL_REMINDER, enabled)
            SettingsStore.set(context, SettingsKeys.KCAL_REMINDER_HOUR, hour.coerceIn(0, 23))
            applyReminders()
            toast(if (enabled) "热量提醒已开启（每天 $hour 点，当天没记录才提醒）" else "热量提醒已关闭")
        }
    }

    /** 任何提醒项一改，就把三个闹钟整体重排（开着的重设、关掉的取消）。 */
    private suspend fun applyReminders() {
        val latest = SettingsStore.observe(context).first()
        Reminders.apply(context, latest)
    }

    // ---------- 危险操作 ----------

    /** 清除全部本地数据（不动设置，也不动 API Key，Key 要单独清）。 */
    fun clearAllData(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.clearAllData()
            toast("本地记录已清空（设置与 API Key 保留）")
            onDone()
        }
    }

    private fun <T> put(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        viewModelScope.launch { SettingsStore.set(context, key, value) }
    }
}
