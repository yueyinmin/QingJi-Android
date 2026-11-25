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
    const val SearchFood = "search_food"
    const val TextAi = "text_ai"
    const val Weight = "weight"
    const val AiConfig = "ai_config"
    const val Profile = "profile"
    const val ImageRecord = "image_record"
    const val VoiceRecord = "voice_record"
    const val Barcode = "barcode"
    const val AiAnalysis = "ai_analysis"
    /**
     * QJ-AI-HISTORY-DATE-V1：带目标分析日期进入 AI 分析页。
     * 无参的 AiAnalysis 保留，兼容直达入口（默认今天）。
     * 日期用 ISO yyyy-MM-dd（LocalDate.toString()），只含数字与连字符，URL 安全。
     */
    const val AiAnalysisWithDate = "ai_analysis/{date}"
    fun aiAnalysis(date: java.time.LocalDate) =
        "ai_analysis/${java.net.URLEncoder.encode(date.toString(), "UTF-8")}"
    /** 路由参数合法性校验：非法日期一律回退今天。 */
    fun aiAnalysisDate(raw: String?): java.time.LocalDate =
        if (raw.isNullOrBlank()) com.qingji.app.domain.AppClock.today()
        else runCatching { java.time.LocalDate.parse(raw) }
            .getOrDefault(com.qingji.app.domain.AppClock.today())
    /** 统一 AI 管家入口。context 表示来源业务页，所有入口进入同一会话系统。 */
    const val AiChat = "ai_chat/{context}"
    fun aiChat(context: String = "首页") = "ai_chat/$context"
    /** 阶段 I：AI 长期记忆管理页（查看/纠正/失效/删除/关类别）。 */
    const val AiMemory = "ai_memory"
    /** M9 数据备份 / 导出 / 导入。 */
    const val Backup = "backup"
    /** 0 = 新写一篇；大于 0 = 编辑已有那篇的 id。 */
    const val DiaryEdit = "diary_edit/{diaryId}"
    fun diaryEdit(diaryId: Long = 0L) = "diary_edit/$diaryId"

    /** 桌面小组件设置 */
    const val WidgetSettings = "widget_settings"

    /** 食物库 */
    const val FoodLibrary = "food_library"

    /** v1.5 新增/编辑自定义食物：editFoodId=0 新建，>0 编辑 */
    const val CustomFoodEdit = "custom_food_edit/{editFoodId}"
    fun customFoodEdit(editFoodId: Long = 0L) = "custom_food_edit/$editFoodId"

    /** v1.5 阶段四：用户自建外卖品牌管理 */
    const val UserBrandManage = "user_brand_manage"

    /** v1.5 阶段四：食物库独立全局搜索 */
    const val FoodLibrarySearch = "food_library_search"

    /** 饮食偏好与忌口 */
    const val FoodPreference = "food_preference"

    /** 轻断食页 */
    const val Fasting = "fasting"

    /** 2026-11-24：工具页 / 卡路里换算二级页 */
    const val Tools = "tools"
    const val CalorieConverter = "calorie_converter"

    /** 用餐详情：某一天某一餐。日期用 2026-11-08 这种格式传。 */
    const val MealDetail = "meal_detail/{date}/{mealType}"
    fun mealDetail(date: java.time.LocalDate, mealType: String) = "meal_detail/$date/$mealType"

    /** v3.3.0 运动模块：二级「记录运动」 → 三级「确认记录」。 */
    const val ExercisePicker = "exercise_picker"
    const val ExerciseConfirm = "exercise_confirm/{presetId}?editLogId={editLogId}"
    /** editLogId > 0 表示修改已有记录，默认 -1 = 新建。可选参数，老调用不用改。 */
    fun exerciseConfirm(presetId: Long, editLogId: Long = -1L) =
        "exercise_confirm/$presetId?editLogId=$editLogId"
    /** 阶段 C：力量动作快速补记（uuid 可能含冒号，传参时 encode）。 */
    const val StrengthQuickRecord = "strength_quick/{uuid}"
    fun strengthQuick(uuid: String) = "strength_quick/${java.net.URLEncoder.encode(uuid, "UTF-8")}"

    /** 训练中心 P0：进行中训练 / 训练日记。 */
    const val TrainingActive = "training_active"
    const val TrainingHistory = "training_history"

    /** 训练中心 P1：动作库 / 收藏 / 动作详情 / 我的计划 / 个人最佳。 */
    const val ExerciseLibrary = "exercise_library"
    /** 2026-11-24：有氧动作库（302 条 CARDIO + 记录有氧预设） */
    const val AerobicLibrary = "aerobic_library"
    const val ExerciseFavorites = "exercise_favorites"
    const val ExerciseDetail = "exercise_detail/{uuid}"
    fun exerciseDetail(uuid: String) = "exercise_detail/$uuid"
    const val TrainingPlan = "training_plan"
    const val CustomPlanEdit = "custom_plan_edit"   // QJ 训练联动：自定义计划全屏编辑页
    const val PersonalBest = "personal_best"

    // ---------- 健康体系 V1.1：睡眠（工单 9，记一笔直达） ----------
    const val Sleep = "sleep"

    // ---------- 自律管理模块（P0，SD-001） ----------
    /** 模块首页 Dashboard。 */
    const val SelfDiscipline = "self_discipline"
    /** 目标管理列表。 */
    const val SelfDisciplineGoals = "self_discipline_goals"
    /** 新建（goalId=0）/ 编辑目标。 */
    const val SelfDisciplineGoalEdit = "self_discipline_goal_edit/{goalId}"
    fun selfDisciplineGoalEdit(goalId: Long = 0L) = "self_discipline_goal_edit/$goalId"
    /** 每日打卡。 */
    const val SelfDisciplineCheckin = "self_discipline_checkin/{goalId}"
    fun selfDisciplineCheckin(goalId: Long) = "self_discipline_checkin/$goalId"
    /** 冲动记录。 */
    const val SelfDisciplineUrge = "self_discipline_urge/{goalId}"
    fun selfDisciplineUrge(goalId: Long) = "self_discipline_urge/$goalId"
    /** 目标偏离。 */
    const val SelfDisciplineDeviation = "self_discipline_deviation/{goalId}"
    fun selfDisciplineDeviation(goalId: Long) = "self_discipline_deviation/$goalId"
    /** 历史日历。 */
    const val SelfDisciplineHistory = "self_discipline_history/{goalId}"
    fun selfDisciplineHistory(goalId: Long) = "self_discipline_history/$goalId"
    /** 模块设置。 */
    const val SelfDisciplineSettings = "self_discipline_settings"

    /** 这些是全屏子页面：打开时不显示底部导航栏。 */
    val fullScreenRoutes = setOf(
        Settings, SearchFood, TextAi, Weight, AiConfig, Profile,
        ImageRecord, VoiceRecord, Barcode, AiAnalysis, AiAnalysisWithDate, AiChat, AiMemory, Backup, DiaryEdit, MealDetail,
        Fasting, FoodPreference, FoodLibrary, CustomFoodEdit, UserBrandManage, FoodLibrarySearch,
        Tools, CalorieConverter,
        WidgetSettings, ExercisePicker, ExerciseConfirm, StrengthQuickRecord, // 补记页也是全屏页：隐藏底栏避免遮挡保存按钮
        TrainingActive, TrainingHistory,
        ExerciseLibrary, ExerciseFavorites, ExerciseDetail, TrainingPlan, CustomPlanEdit, PersonalBest,
        Sleep,
        SelfDiscipline, SelfDisciplineGoals, SelfDisciplineGoalEdit, SelfDisciplineCheckin,
        SelfDisciplineUrge, SelfDisciplineDeviation, SelfDisciplineHistory, SelfDisciplineSettings
    )

    /**
     * 日记隐私锁覆盖的「日记区域」路由（导航层与单测共用）。
     * 必须与所有挂 DiaryPrivacyGate 的页面一致，否则会出现「页面显示 Gate，
     * 但 _inDiaryArea=false → 认证成功仍被 onAuthenticated 拒绝 → 一直锁着」。
     * 覆盖：日记 Tab、进展 Tab（三张日记卡）、日记编辑页、睡眠独立页。
     */
    val privacyAreaRoutes: Set<String> = setOf(
        QingJiTab.Diary.route,
        QingJiTab.Progress.route,
        DiaryEdit,
        Sleep
    )
}
