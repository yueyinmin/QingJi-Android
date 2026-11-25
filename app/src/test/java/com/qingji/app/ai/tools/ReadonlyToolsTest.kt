package com.qingji.app.ai.tools

import com.qingji.app.ai.ToolExecutor
import com.qingji.app.ai.ToolRequest
import com.qingji.app.data.local.dao.DailySummary
import com.qingji.app.data.local.dao.MealSummary
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.PlanExercise
import com.qingji.app.data.local.entity.TrainingPlan
import com.qingji.app.data.local.entity.TrainingPlanDay
import com.qingji.app.data.local.entity.WorkoutExercise
import com.qingji.app.data.local.entity.WorkoutSession
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReadonlyToolsTest {

    private val date = LocalDate.parse("2026-11-25")

    private fun foodLogs() = listOf(
        FoodLog(date = date, mealType = "breakfast", foodName = "米饭", grams = 150.0, kcal = 174.0, proteinG = 4.0, carbG = 38.0, fatG = 0.6),
        FoodLog(date = date, mealType = "lunch", foodName = "番茄炒蛋", grams = 250.0, kcal = 275.0, proteinG = 15.0, carbG = 10.0, fatG = 18.0),
        FoodLog(date = date.minusDays(1), mealType = "dinner", foodName = "昨天的饭", grams = 100.0, kcal = 100.0)
    )

    private fun sessions() = listOf(
        WorkoutSession(uuid = "s1", date = date, durationMinutes = 45, note = "推"),
        WorkoutSession(uuid = "s2", date = date.minusDays(10), durationMinutes = 30, note = "旧"),
        WorkoutSession(uuid = "s3", date = date, durationMinutes = 20, note = "跑")
    )

    private fun exercises() = listOf(
        WorkoutExercise(id = 1, sessionUuid = "s1", exerciseNameSnapshot = "卧推", orderInSession = 0, setsJson = "[{\"w\":60,\"r\":8}]"),
        WorkoutExercise(id = 2, sessionUuid = "s2", exerciseNameSnapshot = "深蹲", orderInSession = 0),
        WorkoutExercise(id = 3, sessionUuid = "s3", exerciseNameSnapshot = "慢跑", orderInSession = 0)
    )

    private fun summaryTool() = GetTodaySummaryTool(
        dailySummary = { DailySummary(449.0, 19.0, 48.0, 18.6) },
        mealSummary = { listOf(MealSummary("breakfast", 174.0, 1), MealSummary("lunch", 275.0, 1)) },
        exerciseCalories = { 210.0 },
        waterMl = { 1200 }
    )

    private fun nutritionTool() = GetDailyNutritionTool { date -> foodLogs().filter { it.date == date } }

    private fun historyTool() = GetTrainingHistoryTool(
        sessions = { sessions() },
        exercises = { exercises() }
    )

    private fun planTool() = GetTrainingPlanTool(
        activePlan = { TrainingPlan(uuid = "p1", name = "推拉腿", source = "template:push_pull_legs", isActive = true) },
        plans = { emptyList() },
        planDays = {
            listOf(
                TrainingPlanDay(id = 1, planUuid = "p1", weekday = 1, title = "推", isRest = false),
                TrainingPlanDay(id = 2, planUuid = "other", weekday = 2, title = "别的计划", isRest = false)
            )
        },
        planExercises = {
            listOf(PlanExercise(id = 1, dayId = 1, exerciseUuid = "ex1", exerciseNameSnapshot = "卧推", targetSets = 3, targetReps = 8, targetWeightKg = 60.0, orderInDay = 0))
        }
    )

    @Test
    fun readonlyRegistry_containsFourToolsWithSchemas() {
        val registry = readonlyTools(summaryTool(), nutritionTool(), historyTool(), planTool())

        val defs = registry.definitions()
        assertEquals(4, defs.size)
        assertEquals(
            listOf("get_today_summary", "get_daily_nutrition", "get_training_history", "get_training_plan"),
            defs.map { it.name }
        )
        assertTrue(defs.all { it.parametersJson.contains("\"type\":\"object\"") })
        assertTrue(registry.all().all { it.readOnly })
    }

    @Test
    fun getTodaySummary_readsRepoDataWithoutWrites() = runTest {
        val result = JSONObject(summaryTool().execute("""{"date":"2026-11-25"}"""))

        assertEquals("FACT", result.getString("source"))
        assertEquals("2026-11-25", result.getString("dataRange"))
        assertEquals(449.0, result.getJSONObject("data").getJSONObject("nutrition").getDouble("kcal"), 0.0)
        assertEquals(210.0, result.getJSONObject("data").getDouble("exerciseCalories"), 0.0)
        assertEquals(1200, result.getJSONObject("data").getInt("waterMl"))
        assertEquals(2, result.getJSONObject("data").getJSONArray("meals").length())
    }

    @Test
    fun getDailyNutrition_filtersMealAndComputesTotalsLocally() = runTest {
        val result = JSONObject(
            nutritionTool().execute("""{"date":"2026-11-25","mealType":"breakfast"}""")
        )
        val data = result.getJSONObject("data")

        assertEquals("CALCULATED", result.getString("calculation"))
        assertEquals(174.0, data.getJSONObject("totals").getDouble("kcal"), 0.0)
        assertEquals(1, data.getJSONArray("items").length())
        assertEquals("米饭", data.getJSONArray("items").getJSONObject(0).getString("foodName"))
    }

    @Test
    fun getTrainingHistory_filtersByDateExerciseAndLimit() = runTest {
        val result = JSONObject(
            historyTool().execute(
                """{"from":"2026-11-20","to":"2026-11-25","exerciseName":"卧推","limit":10}"""
            )
        )
        val sessions = result.getJSONObject("data").getJSONArray("sessions")

        assertEquals(1, sessions.length())
        assertEquals("s1", sessions.getJSONObject(0).getString("uuid"))
        assertEquals(1, sessions.getJSONObject(0).getJSONArray("exercises").length())
    }

    @Test
    fun getTrainingPlan_readsActivePlanAndOnlyItsDays() = runTest {
        val result = JSONObject(planTool().execute("{}"))
        val data = result.getJSONObject("data")

        assertTrue(result.getBoolean("found"))
        assertEquals("p1", data.getString("planId"))
        assertEquals(1, data.getJSONArray("days").length())
        assertEquals("推", data.getJSONArray("days").getJSONObject(0).getString("title"))
        assertEquals(
            "卧推",
            data.getJSONArray("days").getJSONObject(0).getJSONArray("exercises").getJSONObject(0).getString("nameSnapshot")
        )
    }

    @Test
    fun getTrainingPlan_missingPlanReturnsUnknown() = runTest {
        val tool = GetTrainingPlanTool(
            activePlan = { null },
            plans = { emptyList() },
            planDays = { emptyList() },
            planExercises = { emptyList() }
        )

        val result = JSONObject(tool.execute("""{"planId":"missing"}"""))

        assertFalse(result.getBoolean("found"))
        assertEquals("UNKNOWN", result.getString("source"))
    }

    @Test
    fun toolExecutor_runsRealReadonlyToolWithCallId() = runTest {
        val executor = ToolExecutor(
            readonlyTools(summaryTool(), nutritionTool(), historyTool(), planTool()),
            maxConcurrency = 2
        )

        val results = executor.execute(
            listOf(
                ToolRequest("call_1", "get_daily_nutrition", """{"date":"2026-11-25","mealType":"lunch"}"""),
                ToolRequest("call_2", "get_today_summary", """{"date":"2026-11-25"}""")
            )
        )

        assertEquals(listOf("call_1", "call_2"), results.map { it.callId })
        assertTrue(results.all { it.ok })
        assertTrue(JSONObject(results[0].resultJson).getJSONObject("data").getJSONArray("items").length() == 1)
    }
}
