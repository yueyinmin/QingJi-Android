package com.qingji.app.data.backup

import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.data.local.entity.BodyMeasure
import com.qingji.app.data.local.entity.Diary
import com.qingji.app.data.local.entity.ExerciseLog
import com.qingji.app.data.local.entity.ExercisePreset
import com.qingji.app.data.local.entity.FastingLog
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.ProgressPhoto
import com.qingji.app.data.local.entity.UsageStat
import com.qingji.app.data.local.entity.UserFood
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.local.entity.WaterLog
import com.qingji.app.data.local.entity.WeightLog
import org.json.JSONObject
import java.time.LocalDate

/**
 * ============================================================
 * 备份文件的解析结果 + 解析器（v1.0.6）
 * ------------------------------------------------------------
 * 为什么要把「解析」和「写库」拆开：
 *
 * 旧版 importJson 是一边解析一边写库，顺序是
 *   ① 先 DELETE 掉 7 张表 → ② 再逐表解析 JSON、逐表 INSERT。
 * 中间任何一条数据坏掉（日期不是 ISO、数字写成字符串、字段超长），
 * 异常一抛，用户就停在「老数据已删、新数据只进了一半」的状态，
 * 而且原数据永远回不来了。
 *
 * 现在改成：先把整份 JSON 全部解析成下面这个 ParsedBackup 对象
 * （这一步完全不碰数据库，失败就失败，什么都不改），
 * 解析全部成功之后，才开一个事务清表 + 落库。
 *
 * 另一个好处是这一层能在电脑上直接跑 JUnit —— 它只依赖 org.json，
 * 不需要真机、不需要 Room。
 * ============================================================
 */

/** 一份备份文件里能读到的东西。哪一段缺失就是空列表（兼容 v1 的旧备份）。 */
data class ParsedBackup(
    /** 备份文件格式版本：1 = v1.0.5 及以前的备份，2 = 本次起 */
    val version: Int = 1,
    val exportedAt: String = "",
    val profile: UserProfile? = null,
    /** v1.1.0：备份 JSON 里是否真的写了 profile 段（区分「明确无档案」与「第三方工具生成的文件缺这段」）。 */
    val hasProfile: Boolean = true,
    val weights: List<WeightLog> = emptyList(),
    val foods: List<UserFood> = emptyList(),
    val favorites: List<String> = emptyList(),
    val foodLogs: List<FoodLog> = emptyList(),
    val waters: List<WaterLog> = emptyList(),
    val diaries: List<Diary> = emptyList(),
    val usage: List<UsageStat> = emptyList(),
    val aiConfig: AiConfig? = null,
    /** v1.1.0：备份 JSON 里是否真的写了 aiConfig 段。 */
    val hasAiConfig: Boolean = true,
    // ---------- v2 新增的五段 ----------
    val bodyMeasures: List<BodyMeasure> = emptyList(),
    val photos: List<ProgressPhoto> = emptyList(),
    val exercises: List<ExerciseLog> = emptyList(),
    val fastings: List<FastingLog> = emptyList(),
    /** 只装用户自己新建的运动（预置的 30 条随 APK 走，不进备份） */
    val customPresets: List<ExercisePreset> = emptyList(),
    // ---------- v3 新增：训练中心 6 张表 ----------
    /** 备份 JSON 里是否真的写了 training 段（v2 及更老的文件没有）。 */
    val hasTraining: Boolean = false,
    val training: TrainingBackup? = null
) {
    /** 有没有真的读到业务数据。全是空的时候导入没意义，界面上该给个提示。 */
    val isEmpty: Boolean
        get() = profile == null && weights.isEmpty() && foodLogs.isEmpty() &&
            waters.isEmpty() && diaries.isEmpty() && bodyMeasures.isEmpty() &&
            photos.isEmpty() && exercises.isEmpty() && fastings.isEmpty() &&
            (training == null || training.isEmpty())
}

/**
 * 解析一份备份 JSON。
 *
 * 原则：
 * 1. **不抛异常给界面**，一律用 Result 包着，失败带一句中文原因；
 * 2. 单条数据坏了只跳过那一条，不牵连整份文件（比如某条体重日期写错，
 *    不该让 3000 条饮食记录一起进不来）；
 * 3. 旧版备份缺的段直接当空列表，不报错。
 */
fun parseBackup(text: String): Result<ParsedBackup> {
    val root = try {
        JSONObject(text)
    } catch (e: Exception) {
        return Result.failure(IllegalStateException("这个文件不是有效的 JSON，可能下载时损坏了"))
    }

    // 只认自家文件：别的 App 导出的 JSON 结构完全不同，硬解析会读出一堆空表
    if (root.optString("app") != "qingji") {
        return Result.failure(
            IllegalStateException("这不是轻记的备份文件（缺少 app 标记）")
        )
    }

    return try {
        Result.success(
            ParsedBackup(
                version = root.optInt("version", 1),
                exportedAt = root.optString("exportedAt", ""),
                profile = parseProfile(root.optJSONObject("profile")),
                hasProfile = root.has("profile"),
                weights = parseArray(root, "weights") { o -> parseWeight(o) },
                foods = parseArray(root, "foods") { o -> parseUserFood(o) },
                favorites = parseStringArray(root, "foodFavorites"),
                foodLogs = parseArray(root, "foodLogs") { o -> parseFoodLog(o) },
                waters = parseArray(root, "water") { o -> parseWater(o) },
                diaries = parseArray(root, "diaries") { o -> parseDiary(o) },
                usage = parseArray(root, "usage") { o -> parseUsage(o) },
                aiConfig = parseAiConfig(root.optJSONObject("aiConfig")),
                hasAiConfig = root.has("aiConfig"),
                bodyMeasures = parseArray(root, "bodyMeasures") { o -> parseBodyMeasure(o) },
                photos = parseArray(root, "photos") { o -> parsePhoto(o) },
                exercises = parseArray(root, "exercises") { o -> parseExercise(o) },
                fastings = parseArray(root, "fastings") { o -> parseFasting(o) },
                customPresets = parseArray(root, "exercisePresets") { o -> parsePreset(o) },
                hasTraining = root.has("training"),
                training = parseTraining(root.optJSONObject("training"))
            )
        )
    } catch (e: Exception) {
        Result.failure(IllegalStateException("备份文件内容读不了：${e.message?.take(80) ?: "格式不对"}"))
    }
}

// ============================================================
// 下面每个 parseXxx 都遵守同一个约定：
// 返回 null = 这条数据没用，跳过它，不影响其它条目
// ============================================================

private fun <T> parseArray(
    root: JSONObject,
    key: String,
    mapper: (JSONObject) -> T?
): List<T> {
    val arr = root.optJSONArray(key) ?: return emptyList()
    val out = ArrayList<T>(arr.length())
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val item = try {
            mapper(obj)
        } catch (e: Exception) {
            null
        }
        if (item != null) out.add(item)
    }
    return out
}

private fun parseStringArray(root: JSONObject, key: String): List<String> {
    val arr = root.optJSONArray(key) ?: return emptyList()
    val out = ArrayList<String>(arr.length())
    for (i in 0 until arr.length()) {
        val s = arr.optString(i, "").trim()
        if (s.isNotEmpty()) out.add(s)
    }
    return out
}

/** 空串 / "null" / 解析不了都当没有，别让一条坏日期废掉整份档案。 */
private fun dateOrNull(text: String?): LocalDate? {
    val raw = text?.trim().orEmpty()
    if (raw.isEmpty() || raw == "null") return null
    return try {
        LocalDate.parse(raw)
    } catch (e: Exception) {
        null
    }
}

/** JSON 里的 null 会被 optString 读成 "null" 四个字符，这里统一当空。 */
private fun cleanText(raw: String?): String {
    val v = raw?.trim().orEmpty()
    return if (v == "null") "" else v
}

/** 可空的 Double：字段缺失时返回 null（比如体脂率没填）。 */
private fun nullableDouble(obj: JSONObject, key: String): Double? {
    if (!obj.has(key) || obj.isNull(key)) return null
    val v = obj.optDouble(key, Double.NaN)
    return if (v.isNaN() || v.isInfinite()) null else v
}

private fun parseProfile(p: JSONObject?): UserProfile? {
    if (p == null) return null
    return UserProfile(
        gender = p.optString("gender", "male").let { if (it == "female") "female" else "male" },
        birthday = dateOrNull(p.optString("birthday", "")),
        heightCm = p.optDouble("heightCm", 175.0),
        startWeight = p.optDouble("startWeight", 0.0),
        targetWeight = p.optDouble("targetWeight", 0.0),
        activityLevel = p.optInt("activityLevel", 2),
        dailyKcalGoal = p.optInt("dailyKcalGoal", 0),
        proteinGoalG = p.optInt("proteinGoalG", 0),
        carbGoalG = p.optInt("carbGoalG", 0),
        fatGoalG = p.optInt("fatGoalG", 0),
        waterGoalMl = p.optInt("waterGoalMl", 2000),
        // v1.0.6：这三项以前导出和导入都漏了，恢复完用户设过的营养目标会变回默认
        fiberGoalG = p.optInt("fiberGoalG", 25),
        sodiumGoalMg = p.optInt("sodiumGoalMg", 2000),
        calciumGoalMg = p.optInt("calciumGoalMg", 800),
        unit = p.optString("unit", "kg"),
        createdAt = p.optLong("createdAt", System.currentTimeMillis())
    )
}

private fun parseWeight(o: JSONObject): WeightLog? {
    val date = dateOrNull(o.optString("date", "")) ?: return null
    val kg = o.optDouble("weightKg", 0.0)
    // 0 或负数的体重是坏数据，宁可不导，也别让它进趋势图把曲线拽到底
    if (kg <= 0.0 || kg > 500.0) return null
    return WeightLog(
        date = date,
        weightKg = kg,
        bodyFat = nullableDouble(o, "bodyFat"),
        note = cleanText(o.optString("note", "")).takeIf { it.isNotEmpty() },
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )
}

private fun parseUserFood(o: JSONObject): UserFood? {
    val name = o.optString("name", "").trim()
    if (name.isEmpty()) return null
    return UserFood(
        name = name,
        pinyin = o.optString("pinyin", ""),
        barcode = cleanText(o.optString("barcode", "")).takeIf { it.isNotEmpty() },
        kcalPer100g = o.optDouble("kcalPer100g", 0.0),
        proteinPer100g = o.optDouble("proteinPer100g", 0.0),
        carbPer100g = o.optDouble("carbPer100g", 0.0),
        fatPer100g = o.optDouble("fatPer100g", 0.0),
        // 四列微量营养素：键缺失 / JSON null 都读成 null（未收录），
        // 不再回落 0.0 —— 0.0 在新语义里是「确实测得为 0」，不能冒充。
        // 顺带补上以前导出端漏写、这里也漏读的 sugarPer100g。
        fiberPer100g = nullableDouble(o, "fiberPer100g"),
        sodiumMgPer100g = nullableDouble(o, "sodiumMgPer100g"),
        sugarPer100g = nullableDouble(o, "sugarPer100g"),
        calciumMgPer100g = nullableDouble(o, "calciumMgPer100g"),
        defaultGrams = o.optDouble("defaultGrams", 100.0),
        // 老备份里可能混着预置食物；拆库后一律归为用户数据，不冒充标准库
        source = o.optString("source", "user").let { if (it == "preset") "user" else it },
        category = o.optString("category", "其他"),
        brand = o.optString("brand", ""),
        favorite = o.optBoolean("favorite", false),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )
}

private fun parseFoodLog(o: JSONObject): FoodLog? {
    val date = dateOrNull(o.optString("date", "")) ?: return null
    val name = o.optString("foodName", "").trim()
    if (name.isEmpty()) return null
    return FoodLog(
        date = date,
        mealType = o.optString("mealType", "snack"),
        foodName = name,
        grams = o.optDouble("grams", 0.0),
        kcal = o.optDouble("kcal", 0.0),
        proteinG = o.optDouble("proteinG", 0.0),
        carbG = o.optDouble("carbG", 0.0),
        fatG = o.optDouble("fatG", 0.0),
        // v1.0.6：这三项以前漏了，恢复完纤维/钠/钙全变 0
        fiberG = o.optDouble("fiberG", 0.0),
        sodiumMg = o.optDouble("sodiumMg", 0.0),
        calciumMg = o.optDouble("calciumMg", 0.0),
        source = o.optString("source", "manual"),
        aiConfidence = nullableDouble(o, "aiConfidence"),
        imagePath = cleanText(o.optString("imagePath", "")).takeIf { it.isNotEmpty() },
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )
}

private fun parseWater(o: JSONObject): WaterLog? {
    val date = dateOrNull(o.optString("date", "")) ?: return null
    val ml = o.optInt("ml", 0)
    if (ml <= 0) return null
    return WaterLog(date = date, ml = ml, createdAt = o.optLong("createdAt", System.currentTimeMillis()))
}

private fun parseDiary(o: JSONObject): Diary? {
    val date = dateOrNull(o.optString("date", "")) ?: return null
    return Diary(
        date = date,
        rating = o.optInt("rating", 0),
        content = o.optString("content", ""),
        imagePaths = o.optString("imagePaths", ""),
        weightKg = nullableDouble(o, "weightKg"),
        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
    )
}

private fun parseUsage(o: JSONObject): UsageStat? {
    val date = dateOrNull(o.optString("date", "")) ?: return null
    val type = o.optString("type", "").trim()
    if (type.isEmpty()) return null
    return UsageStat(date = date, type = type, count = o.optInt("count", 0))
}

private fun parseAiConfig(o: JSONObject?): AiConfig? {
    if (o == null) return null
    return AiConfig(
        provider = o.optString("provider", "custom"),
        baseUrl = o.optString("baseUrl", ""),
        textModel = o.optString("textModel", ""),
        visionModel = o.optString("visionModel", ""),
        temperature = o.optDouble("temperature", 0.2),
        timeoutSec = o.optInt("timeoutSec", 30),
        dailyLimit = o.optInt("dailyLimit", 100),
        enabled = o.optBoolean("enabled", false)
    )
}

private fun parseBodyMeasure(o: JSONObject): BodyMeasure? {
    val date = dateOrNull(o.optString("date", "")) ?: return null
    return BodyMeasure(
        date = date,
        waistCm = nullableDouble(o, "waistCm"),
        chestCm = nullableDouble(o, "chestCm"),
        hipCm = nullableDouble(o, "hipCm"),
        thighCm = nullableDouble(o, "thighCm"),
        armCm = nullableDouble(o, "armCm"),
        note = o.optString("note", ""),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )
}

private fun parsePhoto(o: JSONObject): ProgressPhoto? {
    val date = dateOrNull(o.optString("date", "")) ?: return null
    val path = o.optString("path", "").trim()
    if (path.isEmpty()) return null
    return ProgressPhoto(
        date = date,
        path = path,
        note = o.optString("note", ""),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )
}

private fun parseExercise(o: JSONObject): ExerciseLog? {
    val date = dateOrNull(o.optString("date", "")) ?: return null
    val type = o.optString("type", "").trim()
    if (type.isEmpty()) return null
    return ExerciseLog(
        date = date,
        type = type,
        minutes = o.optInt("minutes", 0),
        kcal = o.optDouble("kcal", 0.0),
        // 旧备份没有这两列，读出来是 0，表示「当时确实没存」
        steps = o.optInt("steps", 0),
        distanceKm = o.optDouble("distanceKm", 0.0),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )
}

private fun parseFasting(o: JSONObject): FastingLog? {
    val date = dateOrNull(o.optString("date", "")) ?: return null
    return FastingLog(
        date = date,
        planHours = o.optInt("planHours", 16),
        eatStartMinute = o.optInt("eatStartMinute", 7 * 60 + 40),
        eatEndMinute = o.optInt("eatEndMinute", 15 * 60 + 40),
        completed = o.optBoolean("completed", false),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )
}

private fun parsePreset(o: JSONObject): ExercisePreset? {
    val name = o.optString("name", "").trim()
    if (name.isEmpty()) return null
    val met = o.optDouble("met", 0.0)
    if (met <= 0.0) return null
    return ExercisePreset(
        name = name,
        met = met,
        category = o.optString("category", "有氧"),
        favorite = o.optBoolean("favorite", false)
    )
}
