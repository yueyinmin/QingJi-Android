package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.data.prefs.SettingsKeys
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.NutritionCalculator
import com.qingji.app.domain.ProfileInputRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ============================================================
 * 首次引导的收尾流程（v1.5 重做）
 * ------------------------------------------------------------
 * 以前引导完成时是三个互不相干的协程各跑各的：
 *
 *   onSave(profile)            → scope.launch { 存档案 }
 *   onSavePreferences(...)     → scope.launch { 存偏好 }
 *   onDone()                   → scope.launch { 写 ONBOARDING_DONE = true }
 *
 * 三个 launch 谁先跑完完全看调度，`ONBOARDING_DONE` 经常最先落库 ——
 * 界面立刻切到首页，可档案还没写完。用户看到的就是「进来了，但目标是 0」，
 * 严重时首页读不到档案，热量环一直是空的。
 *
 * 现在只有一条路，顺序写死：
 *   ① 再校验一遍数据（防止绕过界面直接调）
 *   ② 算营养目标（调 NutritionCalculator，和「我的 → 个人资料」同一个函数）
 *   ③ 档案写进 Room            —— 失败就停在这儿
 *   ④ 目标模式 / 昵称写进 DataStore —— 失败也停在这儿
 *   ⑤ 最后才写 ONBOARDING_DONE = true
 *
 * ③④ 任何一步没成，⑤ 就不会执行，用户还留在引导页、填的内容都还在，
 * 再点一次「完成」即可（档案是 id=1 的 REPLACE，重试不会建出第二条）。
 *
 * Room 和 DataStore 是两个存储，没有跨库事务，所以顺序 + 「最后写标记」
 * 就是这里唯一能保证一致性的办法。
 * ============================================================
 */

/** 引导页收集到的答案（已经是校验过的干净数值）。 */
data class OnboardingAnswers(
    val mode: String,
    val gender: String,
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val targetWeightKg: Double,
    val activityLevel: Int,
    /** 首次进入引导的时间，用于「加入天数」；重试时沿用，不会归 1 */
    val createdAt: Long = System.currentTimeMillis()
)

class OnboardingViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)
    private val context: android.content.Context get() = getApplication()

    private val _saving = MutableStateFlow(false)

    /** 正在保存：界面据此显示「保存中…」并禁用按钮，防止连点重复提交。 */
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _error = MutableStateFlow("")

    /** 保存失败的提示；空 = 没有错误。 */
    val error: StateFlow<String> = _error.asStateFlow()

    fun clearError() {
        _error.value = ""
    }

    /**
     * 给界面用的入口：自己起协程，用 viewModelScope。
     *
     * 为什么不交给界面的 rememberCoroutineScope：用户点完「完成」正好转屏，
     * 界面那个 scope 会被取消，一次落库就断在半截。viewModelScope 活得比页面长，
     * 保存一旦开始就一定会跑完（档案 + 设置 + 完成标记）。
     */
    fun submit(answers: OnboardingAnswers) {
        if (_saving.value) return
        viewModelScope.launch { complete(answers) }
    }

    /**
     * 一次性完成引导。返回 true 只代表「档案 + 设置 + 完成标记」全都落库了。
     * 调用方不需要自己再写 ONBOARDING_DONE，settings 流会自动把界面切到首页。
     */
    suspend fun complete(answers: OnboardingAnswers): Boolean {
        // 连点保护：上一次还没跑完就直接忽略这一次
        if (_saving.value) return false
        _saving.value = true
        _error.value = ""
        try {
            val mode = ProfileInputRules.normalizeMode(answers.mode)

            // ① 兜底校验：数值必须在同一套范围里（界面已经拦过一次，这里防绕过）
            if (!inRange(answers)) {
                _error.value = "有些数字还不对，回上一步检查一下"
                return false
            }

            // ② 营养目标：走和「我的 → 个人资料」完全相同的入口，
            //    参数一致 → 结果必然一致，不会再出现「引导算一套、资料页算另一套」
            val goal = NutritionCalculator.compute(
                gender = answers.gender,
                age = answers.age,
                heightCm = answers.heightCm,
                weightKg = answers.weightKg,
                activityLevel = answers.activityLevel,
                mode = mode
            )

            val profile = UserProfile(
                id = 1,
                gender = answers.gender,
                // 用「今天减 age 年」表示年龄：资料页再用 Period 反算回来正好还是 age
                birthday = ProfileInputRules.birthdayForAge(answers.age),
                heightCm = answers.heightCm,
                startWeight = answers.weightKg,
                targetWeight = answers.targetWeightKg,
                activityLevel = answers.activityLevel,
                dailyKcalGoal = goal.kcal,
                proteinGoalG = goal.proteinG,
                carbGoalG = goal.carbG,
                fatGoalG = goal.fatG,
                // 引导不再问饮水，先给默认 2000，之后在资料页或饮水卡片里改
                waterGoalMl = ProfileInputRules.DEFAULT_WATER_ML,
                createdAt = answers.createdAt
            )

            // ③ 档案：失败就停，绝不往下写完成标记
            try {
                repo.saveProfile(profile)
            } catch (e: Exception) {
                _error.value = "个人档案没存上，你填的内容都还在，再点一次「完成」试试"
                return false
            }

            // ④ 设置：目标模式和昵称一起写（DataStore 单次 edit，不会只成一半）
            val current: QingJiSettings = try {
                SettingsStore.observe(context).first()
            } catch (e: Exception) {
                QingJiSettings()
            }
            val nickname = current.nickname
                .takeIf { it.isNotBlank() && it != DEFAULT_NICKNAME }
                ?: randomNickname()
            try {
                SettingsStore.saveOnboardingBasics(context, mode, nickname)
            } catch (e: Exception) {
                _error.value = "设置没存上，你填的内容都还在，再点一次「完成」试试"
                return false
            }

            // ⑤ 全部成功，最后才开闸
            SettingsStore.set(context, SettingsKeys.ONBOARDING_DONE, true)
            return true
        } catch (e: Exception) {
            _error.value = "保存没成功，再点一次「完成」试试"
            return false
        } finally {
            _saving.value = false
        }
    }

    private fun inRange(a: OnboardingAnswers): Boolean =
        // 性别必须明确是 male / female：空串会被公式当成男性算，等于替用户做了决定
        (a.gender == "male" || a.gender == "female") &&
            a.age in ProfileInputRules.AGE_RANGE &&
            a.heightCm in ProfileInputRules.HEIGHT_RANGE_CM &&
            a.weightKg in ProfileInputRules.WEIGHT_RANGE_KG &&
            a.targetWeightKg in ProfileInputRules.WEIGHT_RANGE_KG &&
            a.activityLevel in 1..4

    /** 没填昵称时给一个「轻er~888」式的随手名字，和原来的机制一致。 */
    private fun randomNickname(): String = DEFAULT_NICKNAME + (100..999).random()

    companion object {
        /** 未设置昵称时的前缀（原来引导里就是 "轻er~" + 随机三位数） */
        const val DEFAULT_NICKNAME = "轻er~"
    }
}
