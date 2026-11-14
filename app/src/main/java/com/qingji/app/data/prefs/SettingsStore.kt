package com.qingji.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
    /** 运动消耗补偿比例（0-100，默认 50）：设备普遍高估消耗，全额加回会让人越练越多吃 */
    val EXERCISE_COMPENSATION_PCT = intPreferencesKey("exercise_compensation_pct")
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
    /** 称重提醒分钟（v3.3.1：滚轮可选小时 + 分钟） */
    val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    val WEIGH_WEEKDAY = intPreferencesKey("weigh_weekday")          // 1=周一 … 7=周日
    val FAST_START_HOUR = intPreferencesKey("fast_start_hour")      // 断食开始/结束时刻
    /** 断食提醒分钟 */
    val FAST_START_MINUTE = intPreferencesKey("fast_start_minute")
    val KCAL_REMINDER_HOUR = intPreferencesKey("kcal_reminder_hour")
    /** 热量提醒分钟 */
    val KCAL_REMINDER_MINUTE = intPreferencesKey("kcal_reminder_minute")
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
    /** 禁用所有通知（总开关） */
    val ALL_NOTIFICATIONS_DISABLED = booleanPreferencesKey("all_notifications_disabled")
    /** 自定义字体文件路径（空 = 系统默认字体） */
    val CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")
    /** 偏好菜系，逗号分隔 */
    val CUISINES = stringPreferencesKey("cuisines")
    val WATER_BUBBLES = intPreferencesKey("water_bubbles")
    /** 首页每餐卡片是否显示蛋白/碳水/脂肪比例条 */
    val SHOW_MEAL_MACRO = booleanPreferencesKey("show_meal_macro")
    /** 首页底部是否显示「本周小结」卡片（v3.4.0） */
    val SHOW_WEEKLY_SUMMARY = booleanPreferencesKey("show_weekly_summary")
    val SHOW_QUICK_BUTTONS = booleanPreferencesKey("show_quick_buttons")
    val SHOW_PROGRESS_TAB = booleanPreferencesKey("show_progress_tab")
    val SHOW_DIARY_TAB = booleanPreferencesKey("show_diary_tab")
    val SHOW_MINE_TAB = booleanPreferencesKey("show_mine_tab")
    val PROGRESS_ANIMATION = booleanPreferencesKey("progress_animation")
    val SWIPE_NAVIGATE = booleanPreferencesKey("swipe_navigate")
    val PAGE_TRANSITION = booleanPreferencesKey("page_transition")
    /** 底栏悬浮 Dock 皮肤（设置 → 显示与外观）：关 = 经典通栏，开 = 悬浮圆角栏 */
    val FLOATING_BOTTOM_BAR = booleanPreferencesKey("floating_bottom_bar")
    val NICKNAME = stringPreferencesKey("nickname")
    val AVATAR_PATH = stringPreferencesKey("avatar_path")

    // ---------- v3.4.0 新增 ----------
    /** 喝水提醒开关（第四类提醒，默认关，老用户行为不变） */
    val WATER_REMINDER = booleanPreferencesKey("water_reminder")
    val WATER_REMINDER_HOUR = intPreferencesKey("water_reminder_hour")
    val WATER_REMINDER_MINUTE = intPreferencesKey("water_reminder_minute")
    /** 小组件卡片背景不透明度，存百分比 30..100（用 Int 避免引 float 键） */
    val WIDGET_ALPHA = intPreferencesKey("widget_alpha")
    /** 小组件展示内容：总览卡里的饮水 / 营养条，体重卡里的体重 */
    val WIDGET_SHOW_WATER = booleanPreferencesKey("widget_show_water")
    val WIDGET_SHOW_MACRO = booleanPreferencesKey("widget_show_macro")
    val WIDGET_SHOW_WEIGHT = booleanPreferencesKey("widget_show_weight")
    /** AI 识别纠错：每行「AI原名=>用户改后名」，纯文本，不建表 */
    val AI_NAME_CORRECTIONS = stringPreferencesKey("ai_name_corrections")
    /** 目标模式（引导第 1 问）：lose / gain / keep，影响热量预算与蛋白目标 */
    val GOAL_MODE = stringPreferencesKey("goal_mode")
    /** 轻断食生效周期，逗号分隔 1..7（周一=1），默认全选 */
    val FASTING_DAYS = stringPreferencesKey("fasting_days")
    /** 时段切换提醒：按进食窗口自动在开始/结束时提醒 */
    val FASTING_PHASE_ALERT = booleanPreferencesKey("fasting_phase_alert")
    /** 断食常驻通知：前台服务在通知栏显示已断食时长（只读现有方案，不改断食规则） */
    val FASTING_ONGOING_NOTIFICATION = booleanPreferencesKey("fasting_ongoing_notification")
    /** 自由断食开始时刻（毫秒）；0 = 当前没有进行中的自由断食。放 DataStore 所以进程重启也能恢复 */
    val FASTING_FREE_START = longPreferencesKey("fasting_free_start")
    /** 界面语言：zh 简体中文（默认）/ en English。只影响核心页文案，未翻译条目回落中文 */
    val UI_LANGUAGE = stringPreferencesKey("ui_language")
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
    /** 运动消耗补偿比例 0-100，默认 50（对标竞品：全额加回会放大设备高估误差） */
    val exerciseCompensationPct: Int = 50,
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
    /** 分钟默认 0：老用户没存过新键时，行为跟以前整点提醒完全一致 */
    val reminderMinute: Int = 0,
    val weighWeekday: Int = 7,
    val fastStartHour: Int = 12,
    val fastStartMinute: Int = 0,
    val kcalReminderHour: Int = 20,
    val kcalReminderMinute: Int = 0,
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
    val allNotificationsDisabled: Boolean = false,
    val customFontPath: String = "",
    val cuisines: String = "",
    val waterBubbles: Int = 2,
    val showMealMacro: Boolean = true,
    val showWeeklySummary: Boolean = true,
    val showQuickButtons: Boolean = true,
    val showProgressTab: Boolean = true,
    val showDiaryTab: Boolean = true,
    val showMineTab: Boolean = true,
    val progressAnimation: Boolean = true,
    // 一级页面左右滑动切换（默认开）
    val swipeNavigate: Boolean = true,
    // 页面切换动画（关掉只留淡入淡出，老机器更稳）
    val pageTransition: Boolean = true,
    // 悬浮底栏（Dock 皮肤），默认关
    val floatingBottomBar: Boolean = false,
    val nickname: String = "轻er~",
    val avatarPath: String = "",
    // ---------- v3.4.0 新增 ----------
    val waterReminder: Boolean = false,
    val waterReminderHour: Int = 15,
    val waterReminderMinute: Int = 0,
    val widgetAlpha: Int = 100,
    val widgetShowWater: Boolean = true,
    val widgetShowMacro: Boolean = true,
    val widgetShowWeight: Boolean = true,
    val aiNameCorrections: String = "",
    val uiLanguage: String = "zh",
    /** 目标模式：lose / gain / keep */
    val goalMode: String = "lose",
    /** 轻断食生效周期（1..7 逗号分隔）与时段切换提醒 */
    val fastingDays: String = "1,2,3,4,5,6,7",
    val fastingPhaseAlert: Boolean = false,
    /** 断食常驻通知开关（前台服务显示计时）；自由断食开始时刻（0 = 未进行） */
    val fastingOngoingNotification: Boolean = false,
    val fastingFreeStart: Long = 0L
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
                    exerciseCompensationPct = p[SettingsKeys.EXERCISE_COMPENSATION_PCT] ?: 50,
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
                    reminderMinute = p[SettingsKeys.REMINDER_MINUTE] ?: 0,
                    weighWeekday = p[SettingsKeys.WEIGH_WEEKDAY] ?: 7,
                    fastStartHour = p[SettingsKeys.FAST_START_HOUR] ?: 12,
                    fastStartMinute = p[SettingsKeys.FAST_START_MINUTE] ?: 0,
                    kcalReminderHour = p[SettingsKeys.KCAL_REMINDER_HOUR] ?: 20,
                    kcalReminderMinute = p[SettingsKeys.KCAL_REMINDER_MINUTE] ?: 0,
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
                    allNotificationsDisabled = p[SettingsKeys.ALL_NOTIFICATIONS_DISABLED] ?: false,
                    customFontPath = p[SettingsKeys.CUSTOM_FONT_PATH] ?: "",
                    cuisines = p[SettingsKeys.CUISINES] ?: "",
                    waterBubbles = p[SettingsKeys.WATER_BUBBLES] ?: 2,
                    showMealMacro = p[SettingsKeys.SHOW_MEAL_MACRO] ?: true,
                    showWeeklySummary = p[SettingsKeys.SHOW_WEEKLY_SUMMARY] ?: true,
                    showQuickButtons = p[SettingsKeys.SHOW_QUICK_BUTTONS] ?: true,
                    showProgressTab = p[SettingsKeys.SHOW_PROGRESS_TAB] ?: true,
                    showDiaryTab = p[SettingsKeys.SHOW_DIARY_TAB] ?: true,
                    showMineTab = p[SettingsKeys.SHOW_MINE_TAB] ?: true,
                    progressAnimation = p[SettingsKeys.PROGRESS_ANIMATION] ?: true,
                    swipeNavigate = p[SettingsKeys.SWIPE_NAVIGATE] ?: true,
                    pageTransition = p[SettingsKeys.PAGE_TRANSITION] ?: true,
                    floatingBottomBar = p[SettingsKeys.FLOATING_BOTTOM_BAR] ?: false,
                    nickname = p[SettingsKeys.NICKNAME] ?: "轻er~",
                    avatarPath = p[SettingsKeys.AVATAR_PATH] ?: "",
                    waterReminder = p[SettingsKeys.WATER_REMINDER] ?: false,
                    waterReminderHour = p[SettingsKeys.WATER_REMINDER_HOUR] ?: 15,
                    waterReminderMinute = p[SettingsKeys.WATER_REMINDER_MINUTE] ?: 0,
                    widgetAlpha = (p[SettingsKeys.WIDGET_ALPHA] ?: 100).coerceIn(30, 100),
                    widgetShowWater = p[SettingsKeys.WIDGET_SHOW_WATER] ?: true,
                    widgetShowMacro = p[SettingsKeys.WIDGET_SHOW_MACRO] ?: true,
                    widgetShowWeight = p[SettingsKeys.WIDGET_SHOW_WEIGHT] ?: true,
                    aiNameCorrections = p[SettingsKeys.AI_NAME_CORRECTIONS] ?: "",
                    uiLanguage = p[SettingsKeys.UI_LANGUAGE] ?: "zh",
                    goalMode = p[SettingsKeys.GOAL_MODE] ?: "lose",
                    fastingDays = p[SettingsKeys.FASTING_DAYS] ?: "1,2,3,4,5,6,7",
                    fastingPhaseAlert = p[SettingsKeys.FASTING_PHASE_ALERT] ?: false,
                    fastingOngoingNotification = p[SettingsKeys.FASTING_ONGOING_NOTIFICATION] ?: false,
                    fastingFreeStart = p[SettingsKeys.FASTING_FREE_START] ?: 0L
                )
            }

    suspend fun <T> set(context: Context, key: Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { it[key] = value }
    }

    /**
     * 引导完成时一次性写目标模式和昵称。
     *
     * 用单次 edit 而不是连续几次 set：DataStore 每次 edit 都是一次磁盘写，
     * 分开写既慢，中间断电还会出现「目标模式存了、昵称没存」的半截状态。
     *
     * 刻意**不碰**过敏原 / 忌口 / 菜系这三项——引导新版已经不问了，
     * 这里往里写空串会把老用户之前填过的偏好直接清掉。要改去
     * 「设置 → 饮食偏好与忌口」那个页面。
     */
    suspend fun saveOnboardingBasics(context: Context, goalMode: String, nickname: String) {
        context.settingsDataStore.edit { p ->
            p[SettingsKeys.GOAL_MODE] = goalMode
            if (nickname.isNotBlank()) p[SettingsKeys.NICKNAME] = nickname
        }
    }

    /** 导出备份时不含任何设置里的敏感内容；Key 本来也不在这里。 */
    suspend fun clear(context: Context) {
        context.settingsDataStore.edit { it.clear() }
    }
}
