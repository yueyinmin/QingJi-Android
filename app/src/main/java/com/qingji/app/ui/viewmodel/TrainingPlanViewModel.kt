package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.TrainingTemplateData
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.local.entity.PlanExercise
import com.qingji.app.data.local.entity.TrainingPlan
import com.qingji.app.data.local.entity.TrainingPlanDay
import com.qingji.app.data.repository.TrainingRepository
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.PersonalBestCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 首页「今日训练」卡要显示的当日安排。 */
data class TodayArrangement(
    val planName: String,
    val title: String,
    val isRest: Boolean,
    val exercises: List<PlanExercise>,
    val notice: String = TrainingTemplateData.NOTICE
)

/**
 * ============================================================
 * P1 专用 ViewModel：计划 / 动作库 / 个人最佳
 * ------------------------------------------------------------
 * 和 P0 的 TrainingViewModel 分开，是因为草稿状态（进行中训练）
 * 和计划配置是两套完全不同的生命周期；但两者共用同一个 Repository 和数据库。
 * ============================================================
 */
class TrainingPlanViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TrainingRepository(app)

    val exercises: StateFlow<List<ExerciseEntity>> = repo.observeExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<ExerciseEntity>> = repo.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val plans: StateFlow<List<TrainingPlan>> = repo.observePlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activePlan: StateFlow<TrainingPlan?> = repo.observeActivePlan()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 当前启用计划的 7 天安排（换计划会自动跟着变）。 */
    val activePlanDays: StateFlow<List<TrainingPlanDay>> =
        combine(repo.observeActivePlan(), repo.observeAllPlanDays()) { plan, all ->
            if (plan == null) emptyList() else all.filter { it.planUuid == plan.uuid }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val planExercises: StateFlow<List<PlanExercise>> = repo.observePlanExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 按历史明细实时算个人最佳，1RM 是估算值，界面要带角标。 */
    val personalBests: StateFlow<List<PersonalBestCalculator.Best>> =
        combine(repo.observeSessions(), repo.observeAllWorkoutExercises()) { sessions, items ->
            PersonalBestCalculator.compute(sessions, items)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }
    private fun say(text: String) { _message.value = text }

    /** 今天该练什么。没启用计划就返回 null，由界面显示「创建引导」。 */
    fun todayArrangement(): TodayArrangement? {
        val plan = activePlan.value ?: return null
        val weekday = AppClock.today().dayOfWeek.value   // 周一=1 … 周日=7
        val day = activePlanDays.value.firstOrNull { it.weekday == weekday }
        if (day == null) return TodayArrangement(plan.name, "休息", true, emptyList())
        return TodayArrangement(
            planName = plan.name,
            title = day.title,
            isRest = day.isRest,
            exercises = planExercises.value
                .filter { it.dayId == day.id }
                .sortedBy { it.orderInDay }
        )
    }

    fun useTemplate(templateId: String) {
        val template = TrainingTemplateData.byId(templateId) ?: return
        viewModelScope.launch {
            repo.useTemplate(template)
            say("已启用「${template.name}」")
        }
    }

    /** 自定义计划：只给名字和每天的动作名，组数/次数沿用模板的保守默认值。 */
    fun useCustomPlan(name: String, days: List<TrainingTemplateData.TemplateDay>) {
        val trimmed = name.trim().ifBlank { "我的训练计划" }
        viewModelScope.launch {
            repo.activatePlan(trimmed, "custom", days)
            say("已启用「$trimmed」")
        }
    }

    fun deactivate(uuid: String) {
        viewModelScope.launch {
            repo.deactivatePlan(uuid)
            say("已停用，计划留在历史里")
        }
    }

    fun reactivate(uuid: String) {
        viewModelScope.launch {
            repo.reactivatePlan(uuid)
            say("已重新启用该计划")
        }
    }

    fun delete(uuid: String) {
        viewModelScope.launch {
            repo.deletePlan(uuid)
            say("已删除该计划")
        }
    }

    fun toggleFavorite(uuid: String, favorite: Boolean) {
        viewModelScope.launch { repo.toggleFavorite(uuid, favorite) }
    }
}
