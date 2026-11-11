package com.qingji.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * ============================================================
 * 备份解析的自动化测试（v1.0.6 新增）
 * ------------------------------------------------------------
 * 为什么先补这一块：整个 App 里「最容易把用户数据搞没」的代码就是备份恢复。
 * 以前它一个测试都没有，全靠手工点。而且旧版是「先删表再解析」，
 * 一旦解析出错，用户的记录就永久没了。
 *
 * 现在解析被拆成了纯函数 parseBackup()，只依赖 org.json，
 * 所以在电脑上就能跑，不用模拟器。
 *
 * 这里覆盖八件事：
 * 1. v2 的十五段都能读出来；
 * 2. v1 的老备份（只有八段）不会报错，新段当空；
 * 3. 别的 App 的 JSON 会被挡住；
 * 4. 文件坏了给中文提示，不是崩；
 * 5. 单条坏数据只跳过那一条，不牵连整份文件；
 * 6. 档案里的纤维/钠/钙目标能恢复（v1.0.5 漏了这三项）；
 * 7. 饮食记录的纤维/钠/钙能恢复（同上）；
 * 8. 运动的步数/公里数能恢复（v1.0.6 新增两列）。
 * ============================================================
 */
class BackupParsingTest {

    // ============================================================
    // 1. v2 备份：十五段全读出来
    // ============================================================

    @Test
    fun parseV2Backup_readsAllSections() {
        val parsed = parseBackup(V2_BACKUP).getOrThrow()

        assertEquals(2, parsed.version)
        assertEquals("male", parsed.profile?.gender)
        assertEquals(176.0, parsed.profile?.heightCm ?: 0.0, 0.001)

        assertEquals(2, parsed.weights.size)
        assertEquals(1, parsed.foods.size)
        assertEquals(listOf("米饭", "鸡蛋"), parsed.favorites)
        assertEquals(2, parsed.foodLogs.size)
        assertEquals(1, parsed.waters.size)
        assertEquals(1, parsed.diaries.size)
        assertEquals(1, parsed.usage.size)
        assertEquals("https://api.deepseek.com/v1", parsed.aiConfig?.baseUrl)

        // v2 新增的五段
        assertEquals(1, parsed.bodyMeasures.size)
        assertEquals(1, parsed.photos.size)
        assertEquals(1, parsed.exercises.size)
        assertEquals(1, parsed.fastings.size)
        assertEquals(1, parsed.customPresets.size)
    }

    // ============================================================
    // 2. v1 老备份：缺五段，不报错，新段为空
    // ============================================================

    @Test
    fun parseV1Backup_missingNewSections_areEmpty() {
        // v1.0.5 及以前导出的文件里根本没有这五段
        val v1 = """
        {
          "app": "qingji",
          "version": 1,
          "profile": null,
          "weights": [{"date":"2026-11-01","weightKg":70.0,"bodyFat":null,"note":null,"createdAt":1}],
          "foods": [],
          "foodLogs": [],
          "water": [],
          "diaries": [],
          "usage": [],
          "aiConfig": null
        }
        """.trimIndent()

        val parsed = parseBackup(v1).getOrThrow()

        assertEquals(1, parsed.version)
        assertEquals(1, parsed.weights.size)
        // 关键：不抛异常，五段都是空的
        assertTrue(parsed.bodyMeasures.isEmpty())
        assertTrue(parsed.photos.isEmpty())
        assertTrue(parsed.exercises.isEmpty())
        assertTrue(parsed.fastings.isEmpty())
        assertTrue(parsed.customPresets.isEmpty())
    }

    // ============================================================
    // 3~4. 挡掉不该读的东西
    // ============================================================

    @Test
    fun rejectForeignFile() {
        val foreign = """{"app":"other-app","version":2,"weights":[]}"""
        val result = parseBackup(foreign)

        assertTrue(result.isFailure)
        assertTrue(
            "错误提示要说清楚是什么问题",
            result.exceptionOrNull()?.message?.contains("不是轻记的备份") == true
        )
    }

    @Test
    fun rejectBrokenJson() {
        val result = parseBackup("这不是 JSON，只是随便一段文字")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("不是有效的 JSON") == true)
    }

    // ============================================================
    // 5. 单条坏数据只跳过那一条
    // ============================================================

    @Test
    fun badRowSkipped_othersKept() {
        val json = """
        {
          "app": "qingji",
          "version": 2,
          "weights": [
            {"date":"2026-11-01","weightKg":70.0},
            {"date":"不是日期","weightKg":71.0},
            {"date":"2026-11-03","weightKg":0.0},
            {"date":"2026-11-04","weightKg":72.0}
          ],
          "foodLogs": [
            {"date":"2026-11-01","mealType":"lunch","foodName":"米饭","grams":150,"kcal":174},
            {"date":"2026-11-01","mealType":"lunch","foodName":"","grams":150,"kcal":174}
          ]
        }
        """.trimIndent()

        val parsed = parseBackup(json).getOrThrow()

        // 日期坏的、体重为 0 的两条被跳过，剩下 2 条
        assertEquals(2, parsed.weights.size)
        assertEquals(70.0, parsed.weights[0].weightKg, 0.001)
        assertEquals(72.0, parsed.weights[1].weightKg, 0.001)
        // 名字空的那条饮食被跳过
        assertEquals(1, parsed.foodLogs.size)
        assertEquals("米饭", parsed.foodLogs[0].foodName)
    }

    // ============================================================
    // 6. 档案里的营养目标（v1.0.5 导出导入都漏了这三项）
    // ============================================================

    @Test
    fun profileNutritionGoals_areRestored() {
        val parsed = parseBackup(V2_BACKUP).getOrThrow()
        val profile = parsed.profile!!

        assertEquals(30, profile.fiberGoalG)
        assertEquals(1800, profile.sodiumGoalMg)
        assertEquals(1000, profile.calciumGoalMg)
        assertEquals(1900, profile.waterGoalMl)
    }

    @Test
    fun profileWithoutNutritionGoals_fallsBackToDefaults() {
        // v1 的备份里没有这三个字段，要用默认值，不能读成 0
        val json = """
        {"app":"qingji","version":1,"profile":{"gender":"female","heightCm":160}}
        """.trimIndent()

        val profile = parseBackup(json).getOrThrow().profile!!
        assertEquals(25, profile.fiberGoalG)
        assertEquals(2000, profile.sodiumGoalMg)
        assertEquals(800, profile.calciumGoalMg)
    }

    // ============================================================
    // 7. 饮食记录的三项微量营养素
    // ============================================================

    @Test
    fun foodLogMicroNutrients_areRestored() {
        val log = parseBackup(V2_BACKUP).getOrThrow().foodLogs.first()
        assertEquals(2.5, log.fiberG, 0.001)
        assertEquals(320.0, log.sodiumMg, 0.001)
        assertEquals(45.0, log.calciumMg, 0.001)
        assertEquals("/data/user/0/com.qingji.app/files/images/a.jpg", log.imagePath)
    }

    // ============================================================
    // 8. 运动的步数与公里（v1.0.6 新加的两列）
    // ============================================================

    @Test
    fun exerciseStepsAndDistance_areRestored() {
        val exercise = parseBackup(V2_BACKUP).getOrThrow().exercises.first()

        assertEquals("走路", exercise.type)
        assertEquals(40, exercise.minutes)
        assertEquals(8000, exercise.steps)
        assertEquals(0.0, exercise.distanceKm, 0.001)   // 按步数记的，公里是 0
        assertEquals(LocalDate.of(2026, 11, 9), exercise.date)
    }

    @Test
    fun oldExerciseWithoutNewColumns_getsZero() {
        // 旧备份里的运动没有 steps / distanceKm 两个字段
        val json = """
        {"app":"qingji","version":1,
         "exercises":[{"date":"2026-11-01","type":"跑步","minutes":30,"kcal":300}]}
        """.trimIndent()

        val exercise = parseBackup(json).getOrThrow().exercises.first()
        assertEquals(30, exercise.minutes)
        assertEquals(0, exercise.steps)          // 0 = 当时没存，不是「走了 0 步」
        assertEquals(0.0, exercise.distanceKm, 0.001)
    }

    // ============================================================
    // 补充：空备份要能识别出来、照片路径要留着
    // ============================================================

    @Test
    fun emptyBackup_isDetected() {
        val json = """{"app":"qingji","version":2,"profile":null}"""
        val parsed = parseBackup(json).getOrThrow()
        assertTrue("只有壳没有数据，界面该提示一句", parsed.isEmpty)
        assertNull(parsed.profile)
    }

    @Test
    fun photoPath_keptEvenThoughFileIsNotInBackup() {
        val photo = parseBackup(V2_BACKUP).getOrThrow().photos.first()
        assertEquals("/data/user/0/com.qingji.app/files/images/p.jpg", photo.path)
        assertEquals("正面", photo.note)
    }

    /**
     * 迁移 8：自建食物的四列微量营养素要能按「NULL = 未收录」往返。
     * 导出端以前整个漏写这四键，导入端却有 optDouble(..., 0.0)，
     * 备份恢复一次就把「不知道」变成 0；现在两端都按可空处理。
     */
    @Test
    fun parseUserFood_keepsNullableMicroNutrients() {
        val json = """
        {
          "app": "qingji", "version": 2,
          "profile": null, "weights": [],
          "foods": [
            {"name":"A","kcalPer100g":100.0,"fiberPer100g":null,
             "sodiumMgPer100g":0.0,"sugarPer100g":1.5,"calciumMgPer100g":null},
            {"name":"B","kcalPer100g":200.0}
          ],
          "foodLogs": [], "water": [], "diaries": [], "usage": [], "aiConfig": null
        }
        """.trimIndent()

        val foods = parseBackup(json).getOrThrow().foods
        assertEquals(2, foods.size)

        assertNull(foods[0].fiberPer100g)
        assertEquals(0.0, foods[0].sodiumMgPer100g!!, 0.0001)   // 真 0 不能被当成未收录
        assertEquals(1.5, foods[0].sugarPer100g!!, 0.0001)
        assertNull(foods[0].calciumMgPer100g)

        // 老备份没有这些键 → 全部「未收录」，不能回落成 0.0
        assertNull(foods[1].fiberPer100g)
        assertNull(foods[1].sodiumMgPer100g)
        assertNull(foods[1].sugarPer100g)
        assertNull(foods[1].calciumMgPer100g)
    }

    private companion object {
        /** 一份「和 buildJson 输出结构一致」的 v2 备份，覆盖全部十五段。 */
        val V2_BACKUP = """
        {
          "app": "qingji",
          "version": 2,
          "exportedAt": "2026-11-10T17:00",
          "note": "不含 API Key；导入会覆盖本机全部数据",
          "profile": {
            "gender": "male", "birthday": "1998-03-05", "heightCm": 176.0,
            "startWeight": 80.0, "targetWeight": 70.0, "activityLevel": 3,
            "dailyKcalGoal": 2200, "proteinGoalG": 120, "carbGoalG": 260, "fatGoalG": 60,
            "waterGoalMl": 1900, "fiberGoalG": 30, "sodiumGoalMg": 1800, "calciumGoalMg": 1000,
            "unit": "kg", "createdAt": 1700000000000
          },
          "weights": [
            {"date":"2026-11-08","weightKg":75.5,"bodyFat":22.1,"note":"早上","createdAt":1},
            {"date":"2026-11-09","weightKg":75.2,"bodyFat":null,"note":null,"createdAt":2}
          ],
          "foods": [
            {"name":"自制沙拉","pinyin":"zzsl","barcode":null,"kcalPer100g":80.0,
             "proteinPer100g":3.0,"carbPer100g":10.0,"fatPer100g":3.0,
             "defaultGrams":200.0,"source":"user","category":"家常菜","brand":"",
             "favorite":true,"createdAt":3}
          ],
          "foodFavorites": ["米饭","鸡蛋"],
          "foodLogs": [
            {"date":"2026-11-09","mealType":"lunch","foodName":"米饭","grams":150.0,
             "kcal":174.0,"proteinG":3.9,"carbG":38.4,"fatG":0.5,
             "fiberG":2.5,"sodiumMg":320.0,"calciumMg":45.0,
             "source":"manual","aiConfidence":null,
             "imagePath":"/data/user/0/com.qingji.app/files/images/a.jpg","createdAt":4},
            {"date":"2026-11-09","mealType":"dinner","foodName":"番茄炒蛋","grams":250.0,
             "kcal":320.0,"proteinG":15.0,"carbG":12.0,"fatG":22.0,
             "fiberG":1.2,"sodiumMg":600.0,"calciumMg":80.0,
             "source":"ai","aiConfidence":0.8,"imagePath":null,"createdAt":5}
          ],
          "water": [{"date":"2026-11-09","ml":250,"createdAt":6}],
          "diaries": [
            {"date":"2026-11-09","rating":4,"content":"今天吃得不错",
             "imagePaths":"","weightKg":75.2,"updatedAt":7}
          ],
          "usage": [{"date":"2026-11-09","type":"ai","count":3}],
          "aiConfig": {
            "provider":"deepseek","baseUrl":"https://api.deepseek.com/v1",
            "textModel":"deepseek-chat","visionModel":"deepseek-vl",
            "temperature":0.2,"timeoutSec":30,"dailyLimit":100,"enabled":true
          },
          "bodyMeasures": [
            {"date":"2026-11-01","waistCm":88.0,"chestCm":100.0,"hipCm":95.0,
             "thighCm":55.0,"armCm":32.0,"note":"空腹","createdAt":8}
          ],
          "photos": [
            {"date":"2026-11-01","path":"/data/user/0/com.qingji.app/files/images/p.jpg",
             "note":"正面","createdAt":9}
          ],
          "exercises": [
            {"date":"2026-11-09","type":"走路","minutes":40,"kcal":160.0,
             "steps":8000,"distanceKm":0.0,"createdAt":10}
          ],
          "fastings": [
            {"date":"2026-11-09","planHours":16,"eatStartMinute":460,
             "eatEndMinute":940,"completed":true,"createdAt":11}
          ],
          "exercisePresets": [
            {"name":"爬楼梯","met":8.0,"category":"自定义","favorite":false}
          ]
        }
        """.trimIndent()
    }
}
