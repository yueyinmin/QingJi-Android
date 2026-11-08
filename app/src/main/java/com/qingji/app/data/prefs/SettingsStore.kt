package com.qingji.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * ============================================================
 * M7 的统一偏好仓库（DataStore Preferences）
 * ------------------------------------------------------------
 * 以前设置页只是一堆点了没反应的菜单；现在每个开关都存到这里，
 * 而且是一份 Flow —— 改一下，所有订阅的界面立刻跟着变。
 *
 * 注意职责边界：
 * - 这里只存「设置项」；个人资料、体重、饮食等仍归 Room 管。
 * - API Key 不在这里，单独走 ApiKeyStore 硬件加密。
 * ============================================================
 */

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "qingji_settings"
)

object SettingsKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")            // light / dark / system
    val UNIT = stringPreferencesKey("weight_unit")                 // kg / jin
    val WATER_GOAL = intPreferencesKey("water_goal_ml")
    val PROTEIN_PCT = intPreferencesKey("protein_pct")
    val CARB_PCT = intPreferencesKey("carb_pct")
    val FAT_PCT = intPreferencesKey("fat_pct")
    val EXERCISE_COMPENSATION = booleanPreferencesKey("exercise_compensation")
    val DIET_PREFERENCE = stringPreferencesKey("diet_preference")
    val AUTO_MEAL = booleanPreferencesKey("auto_meal")
    val CONSERVATIVE_ESTIMATE = booleanPreferencesKey("conservative_estimate")
    val WEIGH_REMINDER = booleanPreferencesKey("weigh_reminder")
    val FASTING_REMINDER = booleanPreferencesKey("fasting_reminder")
    /** 轻断食是否启用 */
    val FASTING_ENABLED = booleanPreferencesKey("fasting_enabled")
    /** 断食小时数：16 / 14 / 12 */
    val FASTING_PLAN_HOURS = intPreferencesKey("fasting_plan_hours")
    /** 进食窗口开始（当天第几分钟） */
    val FASTING_EAT_START = intPreferencesKey("fasting_eat_start")
    /** 进食窗口结束（当天第几分钟） */
    val FASTING_EAT_END = intPreferencesKey("fasting_eat_end")
    val KCAL_REMINDER = booleanPreferencesKey("kcal_reminder")
    val REMINDER_HOUR = intPreferencesKey("reminder_hour")
    val WEIGH_WEEKDAY = intPreferencesKey("weigh_weekday")          // 1=周一 … 7=周日
    val FAST_START_HOUR = intPreferencesKey("fast_start_hour")      // 断食开始/结束时刻
    val KCAL_REMINDER_HOUR = intPreferencesKey("kcal_reminder_hour")
    val TIME_ZONE = stringPreferencesKey("record_timezone")
    val ICON_VARIANT = stringPreferencesKey("icon_variant")
    val SHOW_AI_ANALYSIS = booleanPreferencesKey("show_ai_analysis")
    val SHOW_WATER_CARD = booleanPreferencesKey("show_water_card")
    /** 水杯气泡量：0 关 / 1 少 / 2 标准 / 3 多 */
    /** 主题色：green / blue / purple / orange / pink */
    val THEME_COLOR = stringPreferencesKey("theme_color")
    /** 过敏原，逗号分隔（花生,海鲜…） */
    val ALLERGENS = stringPreferencesKey("allergens")
    /** 忌口食物，逗号分隔 */
    val AVOID_FOODS = stringPreferencesKey("avoid_foods")
    /** 最长烹饪时长（分钟），0 = 不限 */
    val MAX_COOK_MINUTES = intPreferencesKey("max_cook_minutes")
    /** 是否在首页显示实时刷新率（调试/玩机用） */
    val SHOW_REFRESH_RATE = booleanPreferencesKey("show_refresh_rate")
    /** 首次引导是否完成 */
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    /** AI 返回语言：zh 强制简体中文 / auto 跟随用户输入 */
    val AI_LANGUAGE = stringPreferencesKey("ai_language")
    /** 偏好菜系，逗号分隔 */
    val CUISINES = stringPreferencesKey("cuisines")
    val WATER_BUBBLES = intPreferencesKey("water_bubbles")
    /** 首页每餐卡片是否显示蛋白/碳水/脂肪比例条 */
    val SHOW_MEAL_MACRO = booleanPreferencesKey("show_meal_macro")
    val SHOW_QUICK_BUTTONS = booleanPreferencesKey("show_quick_buttons")
    val SHOW_PROGRESS_TAB = booleanPreferencesKey("show_progress_tab")
    val SHOW_DIARY_TAB = booleanPreferencesKey("show_diary_tab")
    val SHOW_MINE_TAB = booleanPreferencesKey("show_mine_tab")
    val PROGRESS_ANIMATION = booleanPreferencesKey("progress_animation")
    val NICKNAME = stringPreferencesKey("nickname")
    val AVATAR_PATH = stringPreferencesKey("avatar_path")
}

/** 一次性读出的完整设置快照，给界面和 ViewModel 用。 */
data class QingJiSettings(
    val themeMode: String = "system",
    val unit: String = "kg",
    val waterGoalMl: Int = 3200,
    val proteinPct: Int = 25,
    val carbPct: Int = 50,
    val fatPct: Int = 25,
    val exerciseCompensation: Boolean = false,
    val dietPreference: String = "",
    val autoMeal: Boolean = true,
    val conservativeEstimate: Boolean = true,
    val weighReminder: Boolean = false,
    val fastingReminder: Boolean = false,
    val fastingEnabled: Boolean = false,
    val fastingPlanHours: Int = 16,
    val fastingEatStartMinute: Int = 460,
    val fastingEatEndMinute: Int = 940,
    val kcalReminder: Boolean = false,
    val reminderHour: Int = 20,
    val weighWeekday: Int = 7,
    val fastStartHour: Int = 12,
    val kcalReminderHour: Int = 20,
    val timeZone: String = "Asia/Shanghai",
    val iconVariant: String = "default",
    val showAiAnalysis: Boolean = true,
    val showWaterCard: Boolean = true,
    val themeColor: String = "green",
    val allergens: String = "",
    val avoidFoods: String = "",
    val maxCookMinutes: Int = 0,
    val showRefreshRate: Boolean = false,
    val onboardingDone: Boolean = false,
    val aiLanguage: String = "zh",
    val cuisines: String = "",
    val waterBubbles: Int = 2,
    val showMealMacro: Boolean = true,
    val showQuickButtons: Boolean = true,
    val showProgressTab: Boolean = true,
    val showDiaryTab: Boolean = true,
    val showMineTab: Boolean = true,
    val progressAnimation: Boolean = true,
    val nickname: String = "木林",
    val avatarPath: String = ""
)

object SettingsStore {

    /** 读全量设置；文件损坏时给默认值，绝不抛异常让 App 崩。 */
    fun observe(context: Context): Flow<QingJiSettings> =
        context.settingsDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { p ->
                QingJiSettings(
                    themeMode = p[SettingsKeys.THEME_MODE] ?: "system",
                    unit = p[SettingsKeys.UNIT] ?: "kg",
                    waterGoalMl = p[SettingsKeys.WATER_GOAL] ?: 3200,
                    proteinPct = p[SettingsKeys.PROTEIN_PCT] ?: 25,
                    carbPct = p[SettingsKeys.CARB_PCT] ?: 50,
                    fatPct = p[SettingsKeys.FAT_PCT] ?: 25,
                    exerciseCompensation = p[SettingsKeys.EXERCISE_COMPENSATION] ?: false,
                    dietPreference = p[SettingsKeys.DIET_PREFERENCE] ?: "",
                    autoMeal = p[SettingsKeys.AUTO_MEAL] ?: true,
                    conservativeEstimate = p[SettingsKeys.CONSERVATIVE_ESTIMATE] ?: true,
                    weighReminder = p[SettingsKeys.WEIGH_REMINDER] ?: false,
                    fastingReminder = p[SettingsKeys.FASTING_REMINDER] ?: false,
                    fastingEnabled = p[SettingsKeys.FASTING_ENABLED] ?: false,
                    fastingPlanHours = p[SettingsKeys.FASTING_PLAN_HOURS] ?: 16,
                    fastingEatStartMinute = p[SettingsKeys.FASTING_EAT_START] ?: 460,
                    fastingEatEndMinute = p[SettingsKeys.FASTING_EAT_END] ?: 940,
                    kcalReminder = p[SettingsKeys.KCAL_REMINDER] ?: false,
                    reminderHour = p[SettingsKeys.REMINDER_HOUR] ?: 20,
                    weighWeekday = p[SettingsKeys.WEIGH_WEEKDAY] ?: 7,
                    fastStartHour = p[SettingsKeys.FAST_START_HOUR] ?: 12,
                    kcalReminderHour = p[SettingsKeys.KCAL_REMINDER_HOUR] ?: 20,
                    timeZone = p[SettingsKeys.TIME_ZONE] ?: "Asia/Shanghai",
                    iconVariant = p[SettingsKeys.ICON_VARIANT] ?: "default",
                    showAiAnalysis = p[SettingsKeys.SHOW_AI_ANALYSIS] ?: true,
                    showWaterCard = p[SettingsKeys.SHOW_WATER_CARD] ?: true,
                    themeColor = p[SettingsKeys.THEME_COLOR] ?: "green",
                    allergens = p[SettingsKeys.ALLERGENS] ?: "",
                    avoidFoods = p[SettingsKeys.AVOID_FOODS] ?: "",
                    maxCookMinutes = p[SettingsKeys.MAX_COOK_MINUTES] ?: 0,
                    showRefreshRate = p[SettingsKeys.SHOW_REFRESH_RATE] ?: false,
                    onboardingDone = p[SettingsKeys.ONBOARDING_DONE] ?: false,
                    aiLanguage = p[SettingsKeys.AI_LANGUAGE] ?: "zh",
                    cuisines = p[SettingsKeys.CUISINES] ?: "",
                    waterBubbles = p[SettingsKeys.WATER_BUBBLES] ?: 2,
                    showMealMacro = p[SettingsKeys.SHOW_MEAL_MACRO] ?: true,
                    showQuickButtons = p[SettingsKeys.SHOW_QUICK_BUTTONS] ?: true,
                    showProgressTab = p[SettingsKeys.SHOW_PROGRESS_TAB] ?: true,
                    showDiaryTab = p[SettingsKeys.SHOW_DIARY_TAB] ?: true,
                    showMineTab = p[SettingsKeys.SHOW_MINE_TAB] ?: true,
                    progressAnimation = p[SettingsKeys.PROGRESS_ANIMATION] ?: true,
                    nickname = p[SettingsKeys.NICKNAME] ?: "木林",
                    avatarPath = p[SettingsKeys.AVATAR_PATH] ?: ""
                )
            }

    suspend fun <T> set(context: Context, key: Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { it[key] = value }
    }

    /** 导出备份时不含任何设置里的敏感内容；Key 本来也不在这里。 */
    suspend fun clear(context: Context) {
        context.settingsDataStore.edit { it.clear() }
    }
}
