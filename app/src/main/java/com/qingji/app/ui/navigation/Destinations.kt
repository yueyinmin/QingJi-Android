package com.qingji.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部导航的四个 Tab（中间那个加号不算 Tab，它是"记录"入口）。
 * 想加 Tab 就在这里加一行，底部栏和路由会自动跟着变。
 */
enum class QingJiTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Home("home", "首页", Icons.Filled.Home),
    Progress("progress", "进展", Icons.AutoMirrored.Filled.ShowChart),
    Diary("diary", "日记", Icons.AutoMirrored.Filled.MenuBook),
    Mine("mine", "我的", Icons.Filled.Person)
}

/** 不在底部 Tab 里、但需要跳转的页面（M1 新增记录页与配置页）。 */
object QingJiRoutes {
    const val Settings = "settings"
    const val AddRecord = "add_record"
    const val SearchFood = "search_food"
    const val TextAi = "text_ai"
    const val Weight = "weight"
    const val AiConfig = "ai_config"
    const val Profile = "profile"
    const val ImageRecord = "image_record"
    const val VoiceRecord = "voice_record"
    const val Barcode = "barcode"
    const val AiAnalysis = "ai_analysis"
    /** M9 数据备份 / 导出 / 导入。 */
    const val Backup = "backup"
    /** 0 = 新写一篇；大于 0 = 编辑已有那篇的 id。 */
    const val DiaryEdit = "diary_edit/{diaryId}"
    fun diaryEdit(diaryId: Long = 0L) = "diary_edit/$diaryId"

    /** 任务书 P1：版本更新检查页 */
    const val Update = "update"
    /** 任务书 P1：错误日志页 */
    const val ErrorLog = "error_log"
    /** 任务书 P1：诊断日志导出页 */
    const val DiagnosticExport = "diagnostic_export"

    /** 桌面小组件设置 */
    const val WidgetSettings = "widget_settings"

    /** 食物库 */
    const val FoodLibrary = "food_library"

    /** 饮食偏好与忌口 */
    const val FoodPreference = "food_preference"

    /** 轻断食页 */
    const val Fasting = "fasting"

    /** 用餐详情：某一天某一餐。日期用 2026-11-08 这种格式传。 */
    const val MealDetail = "meal_detail/{date}/{mealType}"
    fun mealDetail(date: java.time.LocalDate, mealType: String) = "meal_detail/$date/$mealType"

    /** v3.3.0 运动模块：二级「记录运动」 → 三级「确认记录」。 */
    const val ExercisePicker = "exercise_picker"
    const val ExerciseConfirm = "exercise_confirm/{presetId}"
    fun exerciseConfirm(presetId: Long) = "exercise_confirm/$presetId"

    /** 训练中心 P0：进行中训练 / 训练日记。 */
    const val TrainingActive = "training_active"
    const val TrainingHistory = "training_history"

    /** 训练中心 P1：动作库 / 收藏 / 动作详情 / 我的计划 / 个人最佳。 */
    const val ExerciseLibrary = "exercise_library"
    const val ExerciseFavorites = "exercise_favorites"
    const val ExerciseDetail = "exercise_detail/{uuid}"
    fun exerciseDetail(uuid: String) = "exercise_detail/$uuid"
    const val TrainingPlan = "training_plan"
    const val PersonalBest = "personal_best"

    /** 这些是全屏子页面：打开时不显示底部导航栏。 */
    val fullScreenRoutes = setOf(
        Settings, SearchFood, TextAi, Weight, AiConfig, Profile,
        ImageRecord, VoiceRecord, Barcode, AiAnalysis, Backup, DiaryEdit, MealDetail,
        Fasting, FoodPreference, FoodLibrary, WidgetSettings, ExercisePicker, ExerciseConfirm,
        TrainingActive, TrainingHistory,
        ExerciseLibrary, ExerciseFavorites, ExerciseDetail, TrainingPlan, PersonalBest,
        Update, ErrorLog, DiagnosticExport
    )
}
