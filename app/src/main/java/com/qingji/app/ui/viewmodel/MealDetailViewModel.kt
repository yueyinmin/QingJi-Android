package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.remote.AiFoodItem
import com.qingji.app.data.remote.AiRecognitionResult
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.MealRules
import com.qingji.app.domain.RefineOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 用餐详情页的一屏数据：哪一天、哪一餐、这一餐吃了什么。
 * 总热量和三大营养素在界面层直接从 logs 加出来，不额外查库。
 */
data class MealDetailState(
    val date: LocalDate = AppClock.today(),
    val mealType: String = MealRules.BREAKFAST,
    val logs: List<FoodLog> = emptyList()
) {
    val kcal: Double get() = logs.sumOf { it.kcal }
    val protein: Double get() = logs.sumOf { it.proteinG }
    val carb: Double get() = logs.sumOf { it.carbG }
    val fat: Double get() = logs.sumOf { it.fatG }
    // v3.5.0：营养素细分。来源（食物库 / AI）暂时都没有这三项数值，多为 0；
    // 界面据此显示「暂无数据」而不是误导性的 0
    val fiber: Double get() = logs.sumOf { it.fiberG }
    val sodiumMg: Double get() = logs.sumOf { it.sodiumMg }
    val calciumMg: Double get() = logs.sumOf { it.calciumMg }
    val hasMicronutrient: Boolean get() = fiber > 0.0 || sodiumMg > 0.0 || calciumMg > 0.0

    /** 这一餐里有没有 AI 识别出来的条目（决定要不要显示「AI 生成」角标）。 */
    val hasAi: Boolean
        get() = logs.any { it.source == "text" || it.source == "image" || it.source == "voice" }
}

/**
 * 用餐详情页的数据管家。
 *
 * 为什么不把日期和餐次塞进构造参数：AndroidViewModel 是系统自动创建的，
 * 拿不到导航参数。所以界面进来后调一次 bind() 把参数喂进来。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)
    private val _date = MutableStateFlow(AppClock.today())
    private val _mealType = MutableStateFlow(MealRules.BREAKFAST)

    val state: StateFlow<MealDetailState> = combine(_date, _mealType) { date, meal ->
        date to meal
    }.flatMapLatest { (date, meal) ->
        repo.observeFoodLogs(date).map { list ->
            MealDetailState(date, meal, list.filter { it.mealType == meal })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealDetailState())

    /** v3.5.0：纤维 / 钠 / 钙的目标值来自档案表。 */
    val profile: StateFlow<com.qingji.app.data.local.entity.UserProfile?> =
        repo.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun bind(date: LocalDate, mealType: String) {
        _date.value = date
        _mealType.value = mealType
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch { repo.deleteFoodLog(id) }
    }

    /** 带上「口味与做法」重新识别这一餐。description 为空时自动用现有条目拼一句。 */
    suspend fun refine(description: String, options: RefineOptions): Result<AiRecognitionResult> {
        val text = description.ifBlank { buildDescription() }
        if (text.isBlank()) {
            return Result.failure(Exception("这一餐还没有食物，先记一笔再来识别"))
        }
        return repo.refineMeal(text, options.toPrompt(), _date.value)
    }

    /** 用现有的条目名和克数拼一句描述，作为重新识别的输入。 */
    private fun buildDescription(): String {
        val logs = state.value.logs
        if (logs.isEmpty()) return ""
        val foods = logs.joinToString("、") { "${it.foodName} ${it.grams.toInt()}克" }
        return "${MealRules.label(state.value.mealType)}吃了：$foods"
    }

    /** 把重新识别的结果整体覆盖到这一餐。 */
    suspend fun applyRefined(items: List<AiFoodItem>, mealType: String) {
        val date = _date.value
        val now = System.currentTimeMillis()
        val logs = items.map { item ->
            FoodLog(
                date = date,
                mealType = mealType,
                foodName = item.name,
                grams = item.grams,
                kcal = item.kcal,
                proteinG = item.proteinG,
                carbG = item.carbG,
                fatG = item.fatG,
                source = "text",
                aiConfidence = item.confidence,
                createdAt = now
            )
        }
        repo.replaceMeal(date, mealType, logs)
        _mealType.value = mealType
    }

    /** 菜单里的「存进我的食物库」：把这一餐每条按每 100g 折算后存成自定义食物。 */
    suspend fun saveToFoodLibrary(): Int {
        val logs = state.value.logs
        if (logs.isEmpty()) return 0
        var added = 0
        logs.forEach { log ->
            if (log.grams > 0) {
                val ratio = 100.0 / log.grams
                repo.addUserFood(
                    name = log.foodName,
                    kcalPer100g = log.kcal * ratio,
                    protein = log.proteinG * ratio,
                    carb = log.carbG * ratio,
                    fat = log.fatG * ratio
                )
                added++
            }
        }
        return added
    }

    /** 菜单里的「改时间 / 改餐次」：整餐挪过去。 */
    suspend fun moveTo(date: LocalDate, mealType: String) {
        val ids = state.value.logs.map { it.id }
        if (ids.isEmpty()) return
        repo.moveMeal(ids, date, mealType)
        _date.value = date
        _mealType.value = mealType
    }

    /** 供界面显示用：当前这一餐覆盖了哪些天（改日期时给快捷选项）。 */
    fun today(): LocalDate = AppClock.today()
}
