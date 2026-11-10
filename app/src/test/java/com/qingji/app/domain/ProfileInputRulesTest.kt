package com.qingji.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 引导页 / 个人资料页共用的输入校验（v1.5 引导精简时抽出来的）。
 *
 * 为什么要测这么细：这两个页面以前各写一套校验，标准和文案都不一样。
 * 现在合并成一个对象，任何一个边界写错都会同时影响两处，
 * 所以范围、空值、NaN、模式规则、生日往返全部钉死。
 */
class ProfileInputRulesTest {

    // ---------- 范围 ----------

    @Test
    fun `年龄只接受范围内的整数`() {
        assertEquals(25, ProfileInputRules.parseAge("25"))
        assertEquals(10, ProfileInputRules.parseAge("10"))
        assertEquals(100, ProfileInputRules.parseAge("100"))
        assertNull("9 岁太小", ProfileInputRules.parseAge("9"))
        assertNull("101 岁超出范围", ProfileInputRules.parseAge("101"))
        assertNull("小数不是年龄", ProfileInputRules.parseAge("25.5"))
        assertNull("空串不是数字", ProfileInputRules.parseAge(""))
    }

    @Test
    fun `身高体重拒绝空串与异常数值`() {
        assertEquals(175.0, ProfileInputRules.parseHeight("175")!!, 0.001)
        assertEquals(175.5, ProfileInputRules.parseHeight(" 175.5 ")!!, 0.001)

        assertNull("空串", ProfileInputRules.parseHeight(""))
        assertNull("纯空格", ProfileInputRules.parseHeight("   "))
        assertNull("NaN", ProfileInputRules.parseHeight("NaN"))
        assertNull("Infinity", ProfileInputRules.parseHeight("Infinity"))
        assertNull("负数", ProfileInputRules.parseHeight("-175"))
        assertNull("太矮", ProfileInputRules.parseHeight("79"))
        assertNull("太高", ProfileInputRules.parseHeight("251"))

        assertNull("体重为空", ProfileInputRules.parseWeight(""))
        assertNull("体重为 0", ProfileInputRules.parseWeight("0"))
        assertNull("体重太轻", ProfileInputRules.parseWeight("19"))
        assertNull("体重太重", ProfileInputRules.parseWeight("351"))
        assertEquals(350.0, ProfileInputRules.parseWeight("350")!!, 0.001)
    }

    @Test
    fun `负数和小数点号都能被正确拒绝或接受`() {
        // 体重可以带小数（电子秤一般一位小数）
        assertEquals(70.5, ProfileInputRules.parseWeight("70.5")!!, 0.001)
        // 但负数永远不行
        assertNull(ProfileInputRules.parseWeight("-70.5"))
    }

    // ---------- 目标体重：按模式区分 ----------

    @Test
    fun `减重模式目标体重必须比现在轻`() {
        val mode = NutritionCalculator.MODE_LOSE
        assertNull(ProfileInputRules.targetWeightError(mode, 70.0, "65"))
        assertNotNull("一样重不算减重", ProfileInputRules.targetWeightError(mode, 70.0, "70"))
        assertNotNull("更重更不行", ProfileInputRules.targetWeightError(mode, 70.0, "75"))
    }

    @Test
    fun `增肌模式目标体重必须比现在重`() {
        val mode = NutritionCalculator.MODE_GAIN
        assertNull(ProfileInputRules.targetWeightError(mode, 70.0, "75"))
        assertNotNull("一样重不算增肌", ProfileInputRules.targetWeightError(mode, 70.0, "70"))
        assertNotNull("更轻不行", ProfileInputRules.targetWeightError(mode, 70.0, "65"))
    }

    @Test
    fun `保持模式可以不填目标体重`() {
        val mode = NutritionCalculator.MODE_KEEP
        assertNull("留空是允许的", ProfileInputRules.targetWeightError(mode, 70.0, ""))
        // 留空时目标体重直接等于当前体重，不能是 null
        assertEquals(70.0, ProfileInputRules.parseTargetWeight(mode, 70.0, "")!!, 0.001)
        // 填了也不限制轻重
        assertNull(ProfileInputRules.targetWeightError(mode, 70.0, "60"))
        assertNull(ProfileInputRules.targetWeightError(mode, 70.0, "80"))
    }

    @Test
    fun `非保持模式留空目标体重会被拦下`() {
        assertNotNull(ProfileInputRules.targetWeightError(NutritionCalculator.MODE_LOSE, 70.0, ""))
        assertNotNull(ProfileInputRules.targetWeightError(NutritionCalculator.MODE_GAIN, 70.0, ""))
        // 没填当前体重时，保持模式也不能凭空顶上一个数
        assertNotNull(ProfileInputRules.targetWeightError(NutritionCalculator.MODE_KEEP, null, ""))
    }

    @Test
    fun `非法模式按减重处理`() {
        assertEquals(NutritionCalculator.MODE_LOSE, ProfileInputRules.normalizeMode(""))
        assertEquals(NutritionCalculator.MODE_LOSE, ProfileInputRules.normalizeMode("whatever"))
        assertEquals(NutritionCalculator.MODE_GAIN, ProfileInputRules.normalizeMode("gain"))
        assertEquals(NutritionCalculator.MODE_KEEP, ProfileInputRules.normalizeMode("keep"))
    }

    // ---------- 四个字段一起校验 ----------

    @Test
    fun `全部填对才返回数值`() {
        val input = ProfileInputRules.BodyInput(
            ageText = "28",
            heightText = "175",
            weightText = "72.5",
            targetWeightText = "68"
        )
        val values = ProfileInputRules.parseBody(NutritionCalculator.MODE_LOSE, input)
        assertNotNull(values)
        assertEquals(28, values!!.age)
        assertEquals(175.0, values.heightCm, 0.001)
        // 关键：数字必须原样带出去，不能被四舍五入或回落成默认值，
        // 否则引导算出的目标和资料页算的会对不上。
        assertEquals(72.5, values.weightKg, 0.001)
        assertEquals(68.0, values.targetWeightKg, 0.001)
    }

    @Test
    fun `按顺序返回第一条错误且不跳来跳去`() {
        val input = ProfileInputRules.BodyInput(
            ageText = "",
            heightText = "999",
            weightText = "",
            targetWeightText = ""
        )
        val error = ProfileInputRules.bodyError(NutritionCalculator.MODE_LOSE, input)
        assertNotNull(error)
        assertTrue("先报年龄，不要把后面的问题抢先抛出来", error!!.contains("年龄"))
        assertNull(ProfileInputRules.parseBody(NutritionCalculator.MODE_LOSE, input))
    }

    @Test
    fun `保持模式下目标体重留空也能走完校验`() {
        val input = ProfileInputRules.BodyInput(
            ageText = "30",
            heightText = "165",
            weightText = "58",
            targetWeightText = ""
        )
        assertNull(ProfileInputRules.bodyError(NutritionCalculator.MODE_KEEP, input))
        val values = ProfileInputRules.parseBody(NutritionCalculator.MODE_KEEP, input)
        assertEquals(58.0, values!!.targetWeightKg, 0.001)
    }

    // ---------- 生日往返：引导写进去的年龄，资料页要读回同一个数 ----------

    @Test
    fun `年龄换算成生日再读回来完全一致`() {
        val today = LocalDate.of(2026, 11, 10)
        listOf(10, 18, 25, 30, 45, 60, 100).forEach { age ->
            val birthday = ProfileInputRules.birthdayForAge(age, today)
            assertEquals(
                "引导填 $age 岁，资料页读回来必须还是 $age 岁",
                age,
                ProfileInputRules.ageFromBirthday(birthday, today)
            )
        }
    }

    @Test
    fun `不同日期换算年龄都不会差一岁`() {
        // 挑几个边界日子：年初、年末、闰年 2 月 29
        listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2026, 6, 15)
        ).forEach { today ->
            listOf(20, 33, 57).forEach { age ->
                val birthday = ProfileInputRules.birthdayForAge(age, today)
                assertEquals(age, ProfileInputRules.ageFromBirthday(birthday, today))
            }
        }
    }

    // ---------- 饮水默认值 ----------

    @Test
    fun `饮水目标没填就回落2000越界也回落`() {
        assertEquals(2000, ProfileInputRules.waterGoal(null))
        assertEquals(2000, ProfileInputRules.waterGoal(""))
        assertEquals(2000, ProfileInputRules.waterGoal("abc"))
        assertEquals(2000, ProfileInputRules.waterGoal("0"))
        assertEquals(2000, ProfileInputRules.waterGoal("99999"))
        assertEquals(2500, ProfileInputRules.waterGoal("2500"))
        assertEquals(200, ProfileInputRules.waterGoal("200"))
        assertEquals(8000, ProfileInputRules.waterGoal("8000"))
    }

    // ---------- 引导与资料页算出来的目标必须一致 ----------

    @Test
    fun `同一组数据引导和目标页算出的目标完全相同`() {
        // 引导页：从文本框解析出数值 → 算目标
        val input = ProfileInputRules.BodyInput("32", "172", "78.4", "72")
        val values = ProfileInputRules.parseBody(NutritionCalculator.MODE_LOSE, input)!!
        val fromOnboarding = NutritionCalculator.compute(
            gender = "female",
            age = values.age,
            heightCm = values.heightCm,
            weightKg = values.weightKg,
            activityLevel = 3,
            mode = NutritionCalculator.MODE_LOSE
        )

        // 资料页：存进档案的生日 → 反算年龄 → 同一套参数算目标
        val birthday = ProfileInputRules.birthdayForAge(values.age)
        val ageFromProfile = ProfileInputRules.ageFromBirthday(birthday)
        val fromProfile = NutritionCalculator.compute(
            gender = "female",
            age = ageFromProfile,
            heightCm = values.heightCm,
            weightKg = values.weightKg,
            activityLevel = 3,
            mode = NutritionCalculator.MODE_LOSE
        )

        assertEquals(fromOnboarding, fromProfile)
        // 顺带确认目标本身是合理的，不是 0
        assertTrue(fromOnboarding.kcal > 0)
        assertTrue(fromOnboarding.proteinG > 0)
    }

    @Test
    fun `三种模式算出来的热量各不相同`() {
        fun goalFor(mode: String) = NutritionCalculator.compute(
            gender = "male", age = 30, heightCm = 178.0,
            weightKg = 80.0, activityLevel = 2, mode = mode
        ).kcal

        val lose = goalFor(NutritionCalculator.MODE_LOSE)
        val keep = goalFor(NutritionCalculator.MODE_KEEP)
        val gain = goalFor(NutritionCalculator.MODE_GAIN)
        assertTrue("减重必须低于维持", lose < keep)
        assertTrue("增肌必须高于维持", gain > keep)
        assertFalse("不能被下限拉到同一个值", lose == gain)
    }
}
