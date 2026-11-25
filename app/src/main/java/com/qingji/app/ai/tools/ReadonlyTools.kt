package com.qingji.app.ai.tools

import com.qingji.app.ai.AiTool
import com.qingji.app.ai.ToolExecutor
import com.qingji.app.ai.ToolRegistry
import com.qingji.app.data.local.dao.DailySummary
import com.qingji.app.data.local.dao.MealSummary
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.PlanExercise
import com.qingji.app.data.local.entity.TrainingPlan
import com.qingji.app.data.local.entity.TrainingPlanDay
import com.qingji.app.data.local.entity.WorkoutExercise
import com.qingji.app.data.local.entity.WorkoutSession
import com.qingji.app.data.remote.ai.AiError
import com.qingji.app.data.repository.HealthRepository
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.data.repository.TrainingRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

private object ToolArgs {
    fun parse(json: String, toolName: String): JSONObject = try {
        JSONObject(json)
    } catch (e: Exception) {
        throw AiError.ToolArgument(toolName, "参数不是合法 JSON")
    }

    fun date(args: JSONObject, key: String, toolName: String): LocalDate {
        val raw = args.optString(key)
        return try {
            LocalDate.parse(raw)
        } catch (e: DateTimeParseException) {
            throw AiError.ToolArgument(toolName, "$key 必须是 YYYY-MM-DD")
        }
    }

    fun number(value: Double?): Any = value ?: JSONObject.NULL
    fun integer(value: Int?): Any = value ?: JSONObject.NULL
    fun text(value: String?): Any = value ?: JSONObject.NULL
}

/** 今日摘要：复用首页同一套 Repository 查询，不新建业务查询。 */
class GetTodaySummaryTool(
    private val dailySummary: suspend (LocalDate) -> DailySummary?,
    private val mealSummary: suspend (LocalDate) -> List<MealSummary>,
    private val exerciseCalories: suspend (LocalDate) -> Double,
    private val waterMl: suspend (LocalDate) -> Int?
) : AiTool {
    override val name = "get_today_summary"
    override val description = "读取指定日期的饮食合计、运动消耗和饮水，用于回答今天的状态问题"
    override val parametersJson = """
        {"type":"object","properties":{"date":{"type":"string","description":"YYYY-MM-DD"}},"required":["date"]}
    """.trimIndent()
    override val readOnly = true

    override suspend fun execute(argumentsJson: String): String {
        val args = ToolArgs.parse(argumentsJson, name)
        val date = ToolArgs.date(args, "date", name)
        val summary = dailySummary(date)
        val meals = mealSummary(date)
        val exercise = exerciseCalories(date)
        val water = waterMl(date)

        val mealArray = JSONArray().apply {
            meals.forEach { meal ->
                put(
                    JSONObject()
                        .put("mealType", meal.mealType)
                        .put("kcal", ToolArgs.number(meal.kcal))
                        .put("itemCount", meal.itemCount)
                )
            }
        }

        return JSONObject()
            .put("tool", name)
            .put("source", "FACT")
            .put("dataRange", date.toString())
            .put(
                "data",
                JSONObject()
                    .put(
                        "nutrition",
                        JSONObject()
                            .put("kcal", ToolArgs.number(summary?.totalKcal))
                            .put("proteinG", ToolArgs.number(summary?.totalProtein))
                            .put("carbG", ToolArgs.number(summary?.totalCarb))
                            .put("fatG", ToolArgs.number(summary?.totalFat))
                    )
                    .put("exerciseCalories", exercise)
                    .put("waterMl", ToolArgs.integer(water))
                    .put("meals", mealArray)
            )
            .toString()
    }
}

/** 每日营养明细：按现有 FoodLog 做本地聚合，模型不参与营养计算。 */
class GetDailyNutritionTool(
    private val foodLogs: suspend (LocalDate) -> List<FoodLog>
) : AiTool {
    override val name = "get_daily_nutrition"
    override val description = "读取指定日期的饮食明细；可指定餐次，营养合计由本地计算"
    override val parametersJson = """
        {"type":"object","properties":{"date":{"type":"string","description":"YYYY-MM-DD"},"mealType":{"type":"string","enum":["breakfast","lunch","dinner","snack"]}},"required":["date"]}
    """.trimIndent()
    override val readOnly = true

    override suspend fun execute(argumentsJson: String): String {
        val args = ToolArgs.parse(argumentsJson, name)
        val date = ToolArgs.date(args, "date", name)
        val mealType = args.optString("mealType").takeIf { it.isNotBlank() }
        val logs = foodLogs(date).filter { mealType == null || it.mealType == mealType }

        val items = JSONArray().apply {
            logs.forEach { log ->
                put(
                    JSONObject()
                        .put("mealType", log.mealType)
                        .put("foodName", log.foodName)
                        .put("grams", log.grams)
                        .put("kcal", log.kcal)
                        .put("proteinG", log.proteinG)
                        .put("carbG", log.carbG)
                        .put("fatG", log.fatG)
                        .put("source", log.source)
                )
            }
        }

        return JSONObject()
            .put("tool", name)
            .put("source", "FACT")
            .put("calculation", "CALCULATED")
            .put("dataRange", date.toString())
            .put(
                "data",
                JSONObject()
                    .put("date", date.toString())
                    .put("mealType", ToolArgs.text(mealType))
                    .put(
                        "totals",
                        JSONObject()
                            .put("kcal", logs.sumOf { it.kcal })
                            .put("proteinG", logs.sumOf { it.proteinG })
                            .put("carbG", logs.sumOf { it.carbG })
                            .put("fatG", logs.sumOf { it.fatG })
                    )
                    .put("items", items)
            )
            .toString()
    }
}

/** 训练历史：复用 TrainingRepository 的会话与动作快照，不 JOIN 动作库现名。 */
class GetTrainingHistoryTool(
    private val sessions: suspend () -> List<WorkoutSession>,
    private val exercises: suspend () -> List<WorkoutExercise>
) : AiTool {
    override val name = "get_training_history"
    override val description = "按日期范围读取训练历史，可按动作名筛选；数据来自正式训练记录"
    override val parametersJson = """
        {"type":"object","properties":{"from":{"type":"string","description":"YYYY-MM-DD"},"to":{"type":"string","description":"YYYY-MM-DD"},"exerciseName":{"type":"string"},"limit":{"type":"integer","minimum":1,"maximum":50}},"required":["from","to"]}
    """.trimIndent()
    override val readOnly = true

    override suspend fun execute(argumentsJson: String): String {
        val args = ToolArgs.parse(argumentsJson, name)
        val from = ToolArgs.date(args, "from", name)
        val to = ToolArgs.date(args, "to", name)
        if (from > to) throw AiError.ToolArgument(name, "from 不能晚于 to")

        val exerciseName = args.optString("exerciseName").takeIf { it.isNotBlank() }
        val limit = args.optInt("limit", 20).coerceIn(1, 50)
        val allSessions = sessions()
        val allExercises = exercises()
        val exercisesBySession = allExercises.groupBy { it.sessionUuid }

        val filtered = allSessions
            .asSequence()
            .filter { it.date in from..to }
            .filter { session ->
                exerciseName == null || exercisesBySession[session.uuid].orEmpty()
                    .any { it.exerciseNameSnapshot.contains(exerciseName, ignoreCase = true) }
            }
            .sortedByDescending { it.date }
            .take(limit)
            .toList()

        val sessionArray = JSONArray().apply {
            filtered.forEach { session ->
                val exerciseArray = JSONArray().apply {
                    exercisesBySession[session.uuid].orEmpty()
                        .sortedBy { it.orderInSession }
                        .forEach { exercise ->
                            put(
                                JSONObject()
                                    .put("name", exercise.exerciseNameSnapshot)
                                    .put("setsJson", exercise.setsJson)
                                    .put("note", exercise.note)
                            )
                        }
                }
                put(
                    JSONObject()
                        .put("uuid", session.uuid)
                        .put("date", session.date.toString())
                        .put("durationMinutes", session.durationMinutes)
                        .put("note", session.note)
                        .put("source", session.source)
                        .put("exercises", exerciseArray)
                )
            }
        }

        return JSONObject()
            .put("tool", name)
            .put("source", "FACT")
            .put("dataRange", "$from..$to")
            .put(
                "data",
                JSONObject()
                    .put("from", from.toString())
                    .put("to", to.toString())
                    .put("exerciseName", ToolArgs.text(exerciseName))
                    .put("limit", limit)
                    .put("sessions", sessionArray)
            )
            .toString()
    }
}

/** 训练计划：默认读当前启用计划，也可按 planId 读取指定计划。 */
class GetTrainingPlanTool(
    private val activePlan: suspend () -> TrainingPlan?,
    private val plans: suspend () -> List<TrainingPlan>,
    private val planDays: suspend () -> List<TrainingPlanDay>,
    private val planExercises: suspend () -> List<PlanExercise>
) : AiTool {
    override val name = "get_training_plan"
    override val description = "读取训练计划及计划日/动作明细；不修改计划"
    override val parametersJson = """
        {"type":"object","properties":{"planId":{"type":"string","description":"留空则读取当前启用计划"}},"required":[]}
    """.trimIndent()
    override val readOnly = true

    override suspend fun execute(argumentsJson: String): String {
        val args = ToolArgs.parse(argumentsJson, name)
        val planId = args.optString("planId").takeIf { it.isNotBlank() }
        val plan = if (planId == null) activePlan() else plans().firstOrNull { it.uuid == planId }

        if (plan == null) {
            return JSONObject()
                .put("tool", name)
                .put("source", "UNKNOWN")
                .put("found", false)
                .put("data", JSONObject().put("message", if (planId == null) "当前没有启用训练计划" else "找不到训练计划：$planId"))
                .toString()
        }

        val days = planDays().filter { it.planUuid == plan.uuid }.sortedBy { it.weekday }
        val exercises = planExercises().filter { ex -> days.any { it.id == ex.dayId } }
        val dayArray = JSONArray().apply {
            days.forEach { day ->
                val exerciseArray = JSONArray().apply {
                    exercises.filter { it.dayId == day.id }
                        .sortedBy { it.orderInDay }
                        .forEach { ex ->
                            put(
                                JSONObject()
                                    .put("nameSnapshot", ex.exerciseNameSnapshot)
                                    .put("targetSets", ex.targetSets)
                                    .put("targetReps", ex.targetReps)
                                    .put("targetWeightKg", ex.targetWeightKg)
                            )
                        }
                }
                put(
                    JSONObject()
                        .put("weekday", day.weekday)
                        .put("title", day.title)
                        .put("isRest", day.isRest)
                        .put("exercises", exerciseArray)
                )
            }
        }

        return JSONObject()
            .put("tool", name)
            .put("source", "FACT")
            .put("found", true)
            .put(
                "data",
                JSONObject()
                    .put("planId", plan.uuid)
                    .put("name", plan.name)
                    .put("source", plan.source)
                    .put("isActive", plan.isActive)
                    .put("days", dayArray)
            )
            .toString()
    }
}

/** 把现有 Repository 装配成只读工具注册表。 */
class ReadonlyToolFactory(
    private val qingJiRepository: QingJiRepository,
    private val healthRepository: HealthRepository,
    private val trainingRepository: TrainingRepository
) {
    fun createRegistry(): ToolRegistry = ToolRegistry().apply {
        register(
            GetTodaySummaryTool(
                dailySummary = { qingJiRepository.observeDailySummary(it).first() },
                mealSummary = { qingJiRepository.observeMealSummary(it).first() },
                exerciseCalories = { healthRepository.observeDailyExerciseCalories(it).first() },
                waterMl = { qingJiRepository.observeWaterMl(it).first() }
            )
        )
        register(
            GetDailyNutritionTool(
                foodLogs = { qingJiRepository.observeFoodLogs(it).first() }
            )
        )
        register(
            GetTrainingHistoryTool(
                sessions = { trainingRepository.observeSessions().first() },
                exercises = { trainingRepository.observeAllWorkoutExercises().first() }
            )
        )
        register(
            GetTrainingPlanTool(
                activePlan = { trainingRepository.observeActivePlan().first() },
                plans = { trainingRepository.observePlans().first() },
                planDays = { trainingRepository.observeAllPlanDays().first() },
                planExercises = { trainingRepository.observePlanExercises().first() }
            )
        )
    }

    fun createExecutor(maxConcurrency: Int = 2): ToolExecutor =
        ToolExecutor(createRegistry(), maxConcurrency = maxConcurrency)
}

/** 仅供无 Repository 环境（测试/预览）装配。 */
fun readonlyTools(
    todaySummary: GetTodaySummaryTool,
    dailyNutrition: GetDailyNutritionTool,
    trainingHistory: GetTrainingHistoryTool,
    trainingPlan: GetTrainingPlanTool
): ToolRegistry = ToolRegistry().apply {
    register(todaySummary)
    register(dailyNutrition)
    register(trainingHistory)
    register(trainingPlan)
}
