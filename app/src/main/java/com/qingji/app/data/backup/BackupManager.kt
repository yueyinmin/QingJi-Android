package com.qingji.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.data.local.entity.Diary
import com.qingji.app.data.local.entity.Food
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.UsageStat
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.local.entity.WaterLog
import com.qingji.app.data.local.entity.WeightLog
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.BackupFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * ============================================================
 * 数据备份 / 导出 / 导入（M9）
 * ------------------------------------------------------------
 * 三件事：
 * 1. 导出全量 JSON（含所有表），换手机能带走；
 * 2. 导出饮食记录 CSV（Excel 能直接开）；
 * 3. 从 JSON 覆盖恢复（导入前界面会弹二次确认）。
 *
 * 两条铁律：
 * - **API Key 绝不进备份**：Key 在加密存储里，这里根本读不到，也就不可能漏。
 * - 导入是「先清空再灌」，所以界面上必须先让用户确认，代码里不做静默覆盖。
 *
 * 备份放在 App 自己的外部目录（Android/data/…/files/backup），
 * 用系统分享面板发到微信 / 网盘 / 电脑，不需要存储权限。
 * ============================================================
 */
class BackupManager(private val context: Context) {

    private val db = AppDatabase.get(context)

    /** 备份目录：App 私有外部目录，卸载 App 会被清掉，所以务必先分享出去。 */
    private fun backupDir(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "backup").apply { if (!exists()) mkdirs() }
    }

    /** 目录路径，界面上显示给用户看，方便用数据线拷。 */
    fun backupDirPath(): String = backupDir().absolutePath

    // ============================================================
    // 导出
    // ============================================================

    /** 全量 JSON 备份。返回文件，界面拿去分享。 */
    suspend fun exportJson(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val text = buildJson()
            val name = BackupFormat.backupFileName(
                "backup", "json", BackupFormat.stamp(AppClock.now())
            )
            val file = File(backupDir(), name)
            file.writeText(text, Charsets.UTF_8)
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 饮食记录 CSV（Excel 能开）。 */
    suspend fun exportCsv(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val logs = db.foodLogDao().findAll()
            val rows = logs.map {
                BackupFormat.CsvRow(
                    date = it.date.toString(),
                    meal = com.qingji.app.domain.MealRules.label(it.mealType),
                    name = it.foodName,
                    grams = trimNumber(it.grams),
                    kcal = trimNumber(it.kcal),
                    protein = trimNumber(it.proteinG),
                    carb = trimNumber(it.carbG),
                    fat = trimNumber(it.fatG),
                    source = it.source
                )
            }
            val name = BackupFormat.backupFileName(
                "food-logs", "csv", BackupFormat.stamp(AppClock.now())
            )
            val file = File(backupDir(), name)
            // 加 BOM：不加的话 Excel 打开中文会变乱码（这是国内最常见的坑）
            file.writeText("\uFEFF" + BackupFormat.foodLogCsv(rows), Charsets.UTF_8)
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun trimNumber(value: Double): String {
        return if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(java.util.Locale.US, "%.1f", value)
    }

    /** 拼 JSON。手工拼是为了让备份文件可读、字段顺序稳定。 */
    private suspend fun buildJson(): String {
        val profile = db.userProfileDao().get()
        val weights = db.weightLogDao().findAll()
        val foods = db.foodDao().findAll()
        val foodLogs = db.foodLogDao().findAll()
        val waters = db.waterLogDao().findAll()
        val diaries = db.diaryDao().findAll()
        val usage = db.usageStatDao().findAll()
        val ai = db.aiConfigDao().get()

        val sb = StringBuilder(1024)
        sb.append("{\n")
        sb.append("  \"app\": \"qingji\",\n")
        sb.append("  \"version\": ").append(BackupFormat.VERSION).append(",\n")
        sb.append("  \"exportedAt\": ")
            .append(BackupFormat.jsonString(AppClock.now().toString())).append(",\n")
        sb.append("  \"note\": ")
            .append(BackupFormat.jsonString("不含 API Key；导入会覆盖本机全部数据"))
            .append(",\n")

        // 档案
        sb.append("  \"profile\": ")
        sb.append(
            if (profile == null) "null" else JSONObject().apply {
                put("gender", profile.gender)
                put("birthday", profile.birthday?.toString() ?: JSONObject.NULL)
                put("heightCm", profile.heightCm)
                put("startWeight", profile.startWeight)
                put("targetWeight", profile.targetWeight)
                put("activityLevel", profile.activityLevel)
                put("dailyKcalGoal", profile.dailyKcalGoal)
                put("proteinGoalG", profile.proteinGoalG)
                put("carbGoalG", profile.carbGoalG)
                put("fatGoalG", profile.fatGoalG)
                put("waterGoalMl", profile.waterGoalMl)
                put("unit", profile.unit)
                put("createdAt", profile.createdAt)
            }.toString()
        ).append(",\n")

        sb.append("  \"weights\": ").append(
            JSONArray().apply {
                weights.forEach { w ->
                    put(JSONObject().apply {
                        put("date", w.date.toString())
                        put("weightKg", w.weightKg)
                        put("bodyFat", w.bodyFat ?: JSONObject.NULL)
                        put("note", w.note ?: JSONObject.NULL)
                        put("createdAt", w.createdAt)
                    })
                }
            }.toString()
        ).append(",\n")

        sb.append("  \"foods\": ").append(
            JSONArray().apply {
                foods.forEach { f ->
                    put(JSONObject().apply {
                        put("name", f.name)
                        put("pinyin", f.pinyin)
                        put("barcode", f.barcode ?: JSONObject.NULL)
                        put("kcalPer100g", f.kcalPer100g)
                        put("proteinPer100g", f.proteinPer100g)
                        put("carbPer100g", f.carbPer100g)
                        put("fatPer100g", f.fatPer100g)
                        put("defaultGrams", f.defaultGrams)
                        put("source", f.source)
                    })
                }
            }.toString()
        ).append(",\n")

        sb.append("  \"foodLogs\": ").append(
            JSONArray().apply {
                foodLogs.forEach { l ->
                    put(JSONObject().apply {
                        put("date", l.date.toString())
                        put("mealType", l.mealType)
                        put("foodName", l.foodName)
                        put("grams", l.grams)
                        put("kcal", l.kcal)
                        put("proteinG", l.proteinG)
                        put("carbG", l.carbG)
                        put("fatG", l.fatG)
                        put("source", l.source)
                        put("aiConfidence", l.aiConfidence ?: JSONObject.NULL)
                        put("createdAt", l.createdAt)
                    })
                }
            }.toString()
        ).append(",\n")

        sb.append("  \"water\": ").append(
            JSONArray().apply {
                waters.forEach { w ->
                    put(JSONObject().apply {
                        put("date", w.date.toString())
                        put("ml", w.ml)
                        put("createdAt", w.createdAt)
                    })
                }
            }.toString()
        ).append(",\n")

        sb.append("  \"diaries\": ").append(
            JSONArray().apply {
                diaries.forEach { d ->
                    put(JSONObject().apply {
                        put("date", d.date.toString())
                        put("rating", d.rating)
                        put("content", d.content)
                        put("imagePaths", d.imagePaths)
                        put("weightKg", d.weightKg ?: JSONObject.NULL)
                        put("updatedAt", d.updatedAt)
                    })
                }
            }.toString()
        ).append(",\n")

        sb.append("  \"usage\": ").append(
            JSONArray().apply {
                usage.forEach { u ->
                    put(JSONObject().apply {
                        put("date", u.date.toString())
                        put("type", u.type)
                        put("count", u.count)
                    })
                }
            }.toString()
        ).append(",\n")

        // AI 配置：只留服务商和模型名，Key 不在数据库里，天然导不出去
        sb.append("  \"aiConfig\": ").append(
            if (ai == null) "null" else JSONObject().apply {
                put("provider", ai.provider)
                put("baseUrl", ai.baseUrl)
                put("textModel", ai.textModel)
                put("visionModel", ai.visionModel)
                put("temperature", ai.temperature)
                put("timeoutSec", ai.timeoutSec)
                put("dailyLimit", ai.dailyLimit)
                put("enabled", ai.enabled)
            }.toString()
        ).append("\n")

        sb.append("}\n")
        return sb.toString()
    }

    // ============================================================
    // 导入（覆盖恢复）
    // ============================================================

    /**
     * 从用户选中的文件恢复。
     * 返回成功时的摘要文字（导入了多少条），失败抛异常由界面翻成中文提示。
     */
    suspend fun importJson(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext Result.failure(IllegalStateException("打不开这个文件"))

            val root = JSONObject(text)
            if (root.optString("app") != "qingji") {
                return@withContext Result.failure(
                    IllegalStateException("这不是轻记的备份文件（缺少 app 标记）")
                )
            }

            var foodCount = 0
            var weightCount = 0
            var waterCount = 0
            var diaryCount = 0

            // 先清空，再灌入：这样不会出现「导入两次变成两份」的脏数据
            db.foodLogDao().clearAll()
            db.weightLogDao().clearAll()
            db.waterLogDao().clearAll()
            db.diaryDao().clearAll()
            db.usageStatDao().clearAll()
            db.foodDao().clearAll()

            root.optJSONObject("profile")?.let { p ->
                db.userProfileDao().save(
                    UserProfile(
                        gender = p.optString("gender", "male"),
                        birthday = p.optString("birthday", "").let {
                            if (it.isBlank() || it == "null") null else LocalDate.parse(it)
                        },
                        heightCm = p.optDouble("heightCm", 175.0),
                        startWeight = p.optDouble("startWeight", 0.0),
                        targetWeight = p.optDouble("targetWeight", 0.0),
                        activityLevel = p.optInt("activityLevel", 2),
                        dailyKcalGoal = p.optInt("dailyKcalGoal", 0),
                        proteinGoalG = p.optInt("proteinGoalG", 0),
                        carbGoalG = p.optInt("carbGoalG", 0),
                        fatGoalG = p.optInt("fatGoalG", 0),
                        waterGoalMl = p.optInt("waterGoalMl", 2000),
                        unit = p.optString("unit", "kg"),
                        createdAt = p.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            root.optJSONArray("foods")?.let { arr ->
                val items = mutableListOf<Food>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name", "").trim()
                    if (name.isEmpty()) continue
                    items.add(
                        Food(
                            name = name,
                            pinyin = o.optString("pinyin", ""),
                            barcode = o.optString("barcode", "").takeIf { it.isNotBlank() && it != "null" },
                            kcalPer100g = o.optDouble("kcalPer100g", 0.0),
                            proteinPer100g = o.optDouble("proteinPer100g", 0.0),
                            carbPer100g = o.optDouble("carbPer100g", 0.0),
                            fatPer100g = o.optDouble("fatPer100g", 0.0),
                            defaultGrams = o.optDouble("defaultGrams", 100.0),
                            source = o.optString("source", "user")
                        )
                    )
                }
                if (items.isNotEmpty()) db.foodDao().insertAll(items)
                foodCount = items.size
            }

            root.optJSONArray("foodLogs")?.let { arr ->
                val items = mutableListOf<FoodLog>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val date = parseDate(o.optString("date", "")) ?: continue
                    val name = o.optString("foodName", "").trim()
                    if (name.isEmpty()) continue
                    items.add(
                        FoodLog(
                            date = date,
                            mealType = o.optString("mealType", "snack"),
                            foodName = name,
                            grams = o.optDouble("grams", 0.0),
                            kcal = o.optDouble("kcal", 0.0),
                            proteinG = o.optDouble("proteinG", 0.0),
                            carbG = o.optDouble("carbG", 0.0),
                            fatG = o.optDouble("fatG", 0.0),
                            source = o.optString("source", "manual"),
                            aiConfidence = o.optDouble("aiConfidence", Double.NaN)
                                .takeIf { !it.isNaN() },
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                if (items.isNotEmpty()) db.foodLogDao().insertAll(items)
            }

            root.optJSONArray("weights")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val date = parseDate(o.optString("date", "")) ?: continue
                    db.weightLogDao().save(
                        WeightLog(
                            date = date,
                            weightKg = o.optDouble("weightKg", 0.0),
                            bodyFat = o.optDouble("bodyFat", Double.NaN).takeIf { !it.isNaN() },
                            note = o.optString("note", "").takeIf { it.isNotBlank() && it != "null" },
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                    weightCount++
                }
            }

            root.optJSONArray("water")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val date = parseDate(o.optString("date", "")) ?: continue
                    db.waterLogDao().insert(
                        WaterLog(
                            date = date,
                            ml = o.optInt("ml", 0),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                    waterCount++
                }
            }

            root.optJSONArray("diaries")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val date = parseDate(o.optString("date", "")) ?: continue
                    db.diaryDao().save(
                        Diary(
                            date = date,
                            rating = o.optInt("rating", 0),
                            content = o.optString("content", ""),
                            imagePaths = o.optString("imagePaths", ""),
                            weightKg = o.optDouble("weightKg", Double.NaN).takeIf { !it.isNaN() },
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                    diaryCount++
                }
            }

            root.optJSONArray("usage")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val date = parseDate(o.optString("date", "")) ?: continue
                    db.usageStatDao().insertIfAbsent(
                        UsageStat(date = date, type = o.optString("type", "food"), count = o.optInt("count", 0))
                    )
                }
            }

            root.optJSONObject("aiConfig")?.let { o ->
                db.aiConfigDao().save(
                    AiConfig(
                        provider = o.optString("provider", "custom"),
                        baseUrl = o.optString("baseUrl", ""),
                        textModel = o.optString("textModel", ""),
                        visionModel = o.optString("visionModel", ""),
                        temperature = o.optDouble("temperature", 0.2),
                        timeoutSec = o.optInt("timeoutSec", 30),
                        dailyLimit = o.optInt("dailyLimit", 100),
                        enabled = o.optBoolean("enabled", false)
                    )
                )
            }

            // 预置食物兜底：备份里如果没有食物库（老备份），把常用项补回来
            if (db.foodDao().count() == 0) {
                db.foodDao().insertAll(com.qingji.app.data.local.SeedData.presetFoods)
            }

            Result.success(
                "导入完成：饮食 $foodCount 种食物、体重 $weightCount 条、" +
                    "饮水 $waterCount 条、日记 $diaryCount 篇。AI Key 不随备份走，需要重新填一次。"
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (e: Exception) {
        null
    }

    // ============================================================
    // 分享 / 缓存
    // ============================================================

    /** 用系统分享面板把文件发出去（微信、QQ、网盘、邮箱都能选）。 */
    fun share(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension == "csv") "text/csv" else "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "发送备份文件").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /** 临时拍照缓存有多大。 */
    fun cacheSize(): Long {
        val dir = File(context.cacheDir, "images")
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * 清缓存：只清「拍照临时文件」。
     * 已经保存进饮食记录/日记的照片在 filesDir/images 里，**不能删**，
     * 删了记录里的图就白了。
     */
    fun clearCache(): Long {
        val freed = cacheSize()
        val dir = File(context.cacheDir, "images")
        if (dir.exists()) dir.deleteRecursively()
        return freed
    }

    /** 人类看得懂的大小文案。 */
    fun sizeText(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    }
}
