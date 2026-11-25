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

/** 阶段 G：三类运动分开统计的只读分析工具。只算已有记录，不输出训练处方。 */
private class AnalyzeTrainingTool(
    private val snapshots: suspend (Int) -> List<com.qingji.app.domain.TrainingSessionSnapshot>,
    private val today: () -> java.time.LocalDate
) : AiTool {
    override val name = "analyze_training"
    override val description = "按力量/有氧/HIIT分开统计近N天训练量、频率、强度与数据完整度；数据不足标UNKNOWN"
    override val parametersJson = """
        {"type":"object","properties":{"days":{"type":"integer","minimum":7,"maximum":90}},"required":[]}
    """.trimIndent()
    override val readOnly = true

    override suspend fun execute(argumentsJson: String): String {
        val args = ToolArgs.parse(argumentsJson, name)
        val days = args.optInt("days", 28).coerceIn(7, 90)
        val analysis = com.qingji.app.domain.TrainingAnalysisEngine.analyze(
            snapshots(days), today(), days
        )
        return JSONObject().apply {
            put("days", analysis.windowDays)
            put("insufficientData", analysis.insufficientData)
            put("warnings", org.json.JSONArray(analysis.warnings))
            put("types", trainingTypesJson(analysis))
        }.toString()
    }
}

/** 阶段 G：只输出提案内容，不执行；确认走 ProposalManager。 */
private class SuggestPlanChangeTool(
    private val snapshots: suspend (Int) -> List<com.qingji.app.domain.TrainingSessionSnapshot>,
    private val plans: suspend () -> List<com.qingji.app.data.local.entity.TrainingPlan>,
    private val today: () -> java.time.LocalDate
) : AiTool {
    override val name = "suggest_plan_change"
    override val description = "根据训练分析生成计划启用/停用提案草稿；只返回差异，不修改计划"
    override val parametersJson = """{"type":"object","properties":{"days":{"type":"integer","minimum":7,"maximum":90}},"required":[]}""".trimIndent()
    override val readOnly = true

    override suspend fun execute(argumentsJson: String): String {
        val args = ToolArgs.parse(argumentsJson, name)
        val days = args.optInt("days", 28).coerceIn(7, 90)
        val analysis = com.qingji.app.domain.TrainingAnalysisEngine.analyze(
            snapshots(days), today(), days
        )
        val suggestion = com.qingji.app.ai.training.PlanChangeSuggester.suggest(analysis, plans())
        return suggestion?.let {
            JSONObject().apply {
                put("proposalType", it.proposalType)
                put("targetRecordId", it.targetRecordId)
                put("baseVersion", it.baseVersion)
                put("title", it.title)
                put("reason", it.reason)
                put("beforeJson", it.beforeJson)
                put("afterJson", it.afterJson)
            }.toString()
        } ?: JSONObject().apply { put("suggestion", JSONObject.NULL) }.toString()
    }
}

/** 可信度标签的口径说明：让模型照着标注，别把推断说成记录。 */
private fun trustLegend(): JSONObject = JSONObject()
    .put("RECORDED", "用户实际记录的数据")
    .put("CALCULATED", "本地公式计算的结果")
    .put("AI_INFERRED", "模型推断，不是记录")
    .put("MISSING", "数据缺失，无法计算")
    .put("SUGGESTION", "建议，不是事实")

private const val TRUST_NOTE =
    "报告只含本地记录与公式计算；你补充的推断必须标 AI_INFERRED，建议必须标 SUGGESTION，" +
        "不得声称已经修改或写入了身体数据"

/** 身体数据各分区（体重/BMI/体脂/围度/完整度/目标），每块都带 trust。 */
private fun bodySectionsJson(a: com.qingji.app.domain.BodyAnalysis): JSONObject = JSONObject().apply {
    put("weight", JSONObject().apply {
        put("trust", a.weight.trust.name)
        put("latestKg", ToolArgs.number(a.weight.latestKg))
        put("latestDate", ToolArgs.text(a.weight.latestDate?.toString()))
        put("change7dKg", ToolArgs.number(a.weight.change7dKg))
        put("change28dKg", ToolArgs.number(a.weight.change28dKg))
        put("average7dKg", ToolArgs.number(a.weight.average7dKg))
        put("averageWindowKg", ToolArgs.number(a.weight.averageWindowKg))
        put("weeklyRateKg", ToolArgs.number(a.weight.weeklyRateKg))
        put("direction", a.weight.direction.name)
    })
    put("bmi", JSONObject().apply {
        put("trust", a.bmi.trust.name)
        put("value", ToolArgs.number(a.bmi.value))
        put("category", ToolArgs.text(a.bmi.category))
        put("heightCm", ToolArgs.number(a.bmi.heightCm))
        put("weightKg", ToolArgs.number(a.bmi.weightKg))
    })
    put("bodyFat", JSONObject().apply {
        put("trust", a.bodyFat.trust.name)
        put("latestPct", ToolArgs.number(a.bodyFat.latestPct))
        put("latestDate", ToolArgs.text(a.bodyFat.latestDate?.toString()))
        put("change14dPct", ToolArgs.number(a.bodyFat.change14dPct))
    })
    put("measures", JSONObject().apply {
        put("trust", a.measures.trust.name)
        a.measures.latest?.let { m ->
            put("latestDate", m.date.toString())
            put("waistCm", ToolArgs.number(m.waistCm))
            put("chestCm", ToolArgs.number(m.chestCm))
            put("hipCm", ToolArgs.number(m.hipCm))
            put("thighCm", ToolArgs.number(m.thighCm))
            put("armCm", ToolArgs.number(m.armCm))
        }
        put("waistSpanDays", ToolArgs.integer(a.measures.waistSpanDays))
        put("waistChangeCm", ToolArgs.number(a.measures.waistChangeCm))
    })
    put("completeness", JSONObject().apply {
        put("trust", a.completeness.trust.name)
        put("weightDays", a.completeness.weightDays)
        put("requiredWeightDays", a.completeness.requiredWeightDays)
        put("measureDays", a.completeness.measureDays)
        put("averageGapDays", ToolArgs.integer(a.completeness.averageGapDays))
        put("level", a.completeness.level)
    })
    put("goal", JSONObject().apply {
        put("trust", a.goal.trust.name)
        put("startWeightKg", ToolArgs.number(a.goal.startWeightKg))
        put("targetWeightKg", ToolArgs.number(a.goal.targetWeightKg))
        put("currentWeightKg", ToolArgs.number(a.goal.currentWeightKg))
        put("remainingKg", ToolArgs.number(a.goal.remainingKg))
        put("progressPct", ToolArgs.number(a.goal.progressPct))
        put("estimatedWeeks", ToolArgs.number(a.goal.estimatedWeeks))
    })
}

/** 训练三类汇总（综合报告复用 G 阶段的统计口径）。 */
private fun trainingTypesJson(t: com.qingji.app.domain.TrainingAnalysis): JSONObject = JSONObject().apply {
    t.totals.forEach { (type, summary) ->
        put(type.key, JSONObject().apply {
            put("sessions", summary.sessions)
            put("totalSets", summary.totalSets)
            put("volumeKg", summary.volumeKg)
            put("durationSec", summary.durationSec)
            put("averageRpe", ToolArgs.number(summary.averageRpe))
            put("sessionsPerWeek", summary.sessionsPerWeek)
            put("minGapDays", ToolArgs.integer(summary.minGapDays))
            put("trust", summary.trust.name)
        })
    }
}

/**
 * 阶段 H：只读身体数据。
 *
 * 所有数值都在 BodyAnalysisEngine 里算好（BMI 不允许模型自己算），
 * 这里只负责把结果和信任标签摊平成 JSON。
 */
private class GetBodyMetricsTool(
    private val weights: suspend () -> List<com.qingji.app.domain.WeightPoint>,
    private val measures: suspend () -> List<com.qingji.app.domain.MeasurePoint>,
    private val profile: suspend () -> com.qingji.app.domain.BodyProfile?,
    private val today: () -> LocalDate
) : AiTool {
    override val name = "get_body_metrics"
    override val description = "读取体重趋势、BMI、体脂率、围度、记录完整度与目标进度；数值全部本地计算，只读"
    override val parametersJson = """
        {"type":"object","properties":{"days":{"type":"integer","minimum":7,"maximum":180}},"required":[]}
    """.trimIndent()
    override val readOnly = true

    override suspend fun execute(argumentsJson: String): String {
        val args = ToolArgs.parse(argumentsJson, name)
        val days = args.optInt("days", 28).coerceIn(7, 180)
        val analysis = com.qingji.app.domain.BodyAnalysisEngine.analyze(
            weights = weights(),
            measures = measures(),
            profile = profile(),
            today = today(),
            windowDays = days
        )
        return JSONObject().apply {
            put("tool", name)
            put("generatedFor", analysis.generatedFor.toString())
            put("windowDays", analysis.windowDays)
            put("insufficientData", analysis.insufficientData)
            put("sections", bodySectionsJson(analysis))
            put("facts", JSONArray(analysis.facts))
            put("warnings", JSONArray(analysis.warnings))
            put("trustLegend", trustLegend())
            put("note", TRUST_NOTE)
        }.toString()
    }
}

/**
 * 阶段 H：综合健康报告 = 身体数据 + 训练 + 饮食记录完整度。
 * 三块都在本地拼好，模型只做解释，不给医学结论。
 */
private class GetHealthReportTool(
    private val weights: suspend () -> List<com.qingji.app.domain.WeightPoint>,
    private val measures: suspend () -> List<com.qingji.app.domain.MeasurePoint>,
    private val profile: suspend () -> com.qingji.app.domain.BodyProfile?,
    private val snapshots: suspend (Int) -> List<com.qingji.app.domain.TrainingSessionSnapshot>,
    private val foodLogs: suspend (LocalDate, LocalDate) -> List<com.qingji.app.domain.FoodLogPoint>,
    private val today: () -> LocalDate
) : AiTool {
    override val name = "get_health_report"
    override val description = "综合身体数据、训练与饮食记录生成健康报告；只读，区分记录/计算/缺失"
    override val parametersJson = """
        {"type":"object","properties":{"days":{"type":"integer","minimum":7,"maximum":90}},"required":[]}
    """.trimIndent()
    override val readOnly = true

    override suspend fun execute(argumentsJson: String): String {
        val args = ToolArgs.parse(argumentsJson, name)
        val days = args.optInt("days", 28).coerceIn(7, 90)
        val end = today()
        val from = end.minusDays(days - 1L)
        val bodyProfile = profile()
        val body = com.qingji.app.domain.BodyAnalysisEngine.analyze(
            weights = weights(),
            measures = measures(),
            profile = bodyProfile,
            today = end,
            windowDays = days
        )
        val training = com.qingji.app.domain.TrainingAnalysisEngine.analyze(
            snapshots = snapshots(days),
            today = end,
            windowDays = days
        )
        val report = com.qingji.app.domain.HealthReportBuilder.build(
            body = body,
            training = training,
            foodLogs = foodLogs(from, end),
            proteinGoalG = bodyProfile?.proteinGoalG ?: 0,
            today = end,
            windowDays = days
        )
        return JSONObject().apply {
            put("tool", name)
            put("generatedFor", report.generatedFor.toString())
            put("windowDays", report.windowDays)
            put("insufficientData", report.insufficientData)
            put("summary", JSONArray(report.summary))
            put("body", bodySectionsJson(report.body))
            put("training", JSONObject().apply {
                put("insufficientData", report.training.insufficientData)
                put("types", trainingTypesJson(report.training))
            })
            put("nutrition", JSONObject().apply {
                put("trust", report.nutrition.trust.name)
                put("loggedDays", report.nutrition.loggedDays)
                put("averageKcal", ToolArgs.integer(report.nutrition.averageKcal))
                put("averageProteinG", ToolArgs.integer(report.nutrition.averageProteinG))
                put("proteinGoalG", report.nutrition.proteinGoalG)
                put("daysMeetingProtein", report.nutrition.daysMeetingProtein)
            })
            put("warnings", JSONArray(report.warnings))
            put("trustLegend", trustLegend())
            put("note", TRUST_NOTE)
        }.toString()
    }
}

/** 把现有 Repository 装配成只读工具注册表。 */
class ReadonlyToolFactory(
    private val qingJiRepository: QingJiRepository,
    private val healthRepository: HealthRepository,
    private val trainingRepository: TrainingRepository
) {
    fun createRegistry(): ToolRegistry {
        // 阶段 H：身体数据只在这里做一次实体→规则层的映射，分析与报告都不直接碰 Room 实体
        val weightPoints: suspend () -> List<com.qingji.app.domain.WeightPoint> = {
            qingJiRepository.observeWeightLogs().first().map {
                com.qingji.app.domain.WeightPoint(date = it.date, weightKg = it.weightKg, bodyFatPct = it.bodyFat)
            }
        }
        val measurePoints: suspend () -> List<com.qingji.app.domain.MeasurePoint> = {
            qingJiRepository.observeBodyMeasures().first().map {
                com.qingji.app.domain.MeasurePoint(
                    date = it.date,
                    waistCm = it.waistCm,
                    chestCm = it.chestCm,
                    hipCm = it.hipCm,
                    thighCm = it.thighCm,
                    armCm = it.armCm
                )
            }
        }
        val bodyProfile: suspend () -> com.qingji.app.domain.BodyProfile? = {
            qingJiRepository.observeProfile().first()?.let {
                com.qingji.app.domain.BodyProfile(
                    heightCm = it.heightCm,
                    startWeightKg = it.startWeight,
                    targetWeightKg = it.targetWeight,
                    gender = it.gender,
                    birthday = it.birthday,
                    proteinGoalG = it.proteinGoalG
                )
            }
        }

        return ToolRegistry().apply {
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
            AnalyzeTrainingTool(
                snapshots = { trainingRepository.recentTrainingSnapshots(it) },
                today = { com.qingji.app.domain.AppClock.today() }
            )
        )
        register(
            SuggestPlanChangeTool(
                snapshots = { trainingRepository.recentTrainingSnapshots(it) },
                plans = { trainingRepository.observePlans().first() },
                today = { com.qingji.app.domain.AppClock.today() }
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
        register(
            GetBodyMetricsTool(
                weights = weightPoints,
                measures = measurePoints,
                profile = bodyProfile,
                today = { com.qingji.app.domain.AppClock.today() }
            )
        )
        register(
            GetHealthReportTool(
                weights = weightPoints,
                measures = measurePoints,
                profile = bodyProfile,
                snapshots = { trainingRepository.recentTrainingSnapshots(it) },
                foodLogs = { from, to ->
                    qingJiRepository.observeFoodLogsRange(from, to).first().map {
                        com.qingji.app.domain.FoodLogPoint(date = it.date, kcal = it.kcal, proteinG = it.proteinG)
                    }
                },
                today = { com.qingji.app.domain.AppClock.today() }
            )
        )
        }
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
