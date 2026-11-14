package com.qingji.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.data.local.entity.Diary
import com.qingji.app.data.local.entity.UserFood
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.UsageStat
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.local.entity.WaterLog
import com.qingji.app.data.local.entity.WeightLog
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.BackupFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

    /**
     * 饮食记录 CSV（Excel 能开）。
     *
     * v3.4.0 加了按月 / 按餐次筛选：
     * - month 为 null = 全部时间；
     * - mealTypes 为空 = 全部餐次。
     * 两个参数都有默认值，老调用 exportCsv() 行为完全不变。
     *
     * 这里仍然先 findAll() 再在内存里过滤，而不是改 DAO 加 SQL：
     * 饮食记录是本地单机数据，量级很小，少一条 SQL 就少一处 Room 编译期校验风险。
     */
    suspend fun exportCsv(
        month: java.time.YearMonth? = null,
        mealTypes: Set<String> = emptySet()
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val logs = db.foodLogDao().findAll().filter {
                com.qingji.app.domain.ExportFilter.matches(it.date, it.mealType, month, mealTypes)
            }
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
                com.qingji.app.domain.ExportFilter.filePrefix(month),
                "csv",
                BackupFormat.stamp(AppClock.now())
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
        // v1.0.4：只备份「用户自己的」食物。标准食物随 App 预置，不进备份文件，
        // 备份体积从几百条缩到几条，也不会因为换食物库版本把旧数据带回来。
        val foods = db.userFoodDao().findAll()
        val favorites = db.foodFavoriteDao().allNames()
        val foodLogs = db.foodLogDao().findAll()
        val waters = db.waterLogDao().findAll()
        val diaries = db.diaryDao().findAll()
        val usage = db.usageStatDao().findAll()
        val ai = db.aiConfigDao().get()
        // v1.0.6：下面这五类以前完全没进备份，换手机就静默丢了（界面上还写着「全量」）
        val bodyMeasures = db.bodyMeasureDao().findAll()
        val photos = db.progressPhotoDao().findAll()
        val exercises = db.exerciseLogDao().findAll()
        val fastings = db.fastingLogDao().findAll()
        // 只导用户自己新建的运动：预置那 30 条是 App 自带的，
        // 导回去反而会和下次启动重灌的预置打架（同名的会变成两条）
        val customPresets = db.exercisePresetDao().observeAll().first()
            .filter { it.category == com.qingji.app.domain.ExerciseCalculator.CUSTOM_CATEGORY }
        // v3：训练中心 6 张表整包带走，换手机不丢训练记录
        val training = TrainingBackup(
            exercises = db.exerciseDao().findAll(),
            plans = db.trainingPlanDao().findAll(),
            planDays = db.trainingPlanDayDao().findAll(),
            planExercises = db.planExerciseDao().findAll(),
            sessions = db.workoutSessionDao().findAll(),
            workoutExercises = db.workoutExerciseDao().findAll()
        )
        // v4：自律模块 7 张表整包带走，换手机不丢自律记录（settings 用已有的 getAll()）
        val selfDiscipline = SelfDisciplineBackup(
            goals = db.selfDisciplineGoalDao().findAll(),
            periods = db.selfDisciplinePeriodDao().findAll(),
            checkins = db.selfDisciplineCheckinDao().findAll(),
            urgeLogs = db.selfDisciplineUrgeDao().findAll(),
            settings = db.selfDisciplineSettingDao().getAll(),
            milestones = db.selfDisciplineMilestoneDao().findAll(),
            interventions = db.selfDisciplineInterventionDao().findAll()
        )

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
                // v1.0.6：这三项以前漏了，恢复完用户设过的营养目标会变回默认
                put("fiberGoalG", profile.fiberGoalG)
                put("sodiumGoalMg", profile.sodiumGoalMg)
                put("calciumGoalMg", profile.calciumGoalMg)
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
                        // v1.0.6 之前这四列在导出端整个漏了，导入端却会读，
                        // 备份往返一定丢值。现在原样导出：数值写数值，未收录写 JSON null。
                        put("fiberPer100g", f.fiberPer100g ?: JSONObject.NULL)
                        put("sodiumMgPer100g", f.sodiumMgPer100g ?: JSONObject.NULL)
                        put("sugarPer100g", f.sugarPer100g ?: JSONObject.NULL)
                        put("calciumMgPer100g", f.calciumMgPer100g ?: JSONObject.NULL)
                        put("defaultGrams", f.defaultGrams)
                        put("source", f.source)
                        put("category", f.category)
                        put("brand", f.brand)
                        put("favorite", f.favorite)
                        put("createdAt", f.createdAt)
                    })
                }
            }.toString()
        ).append(",\n")

        // 收藏按名字存（标准库只读，收藏只能记在用户库里），换设备后照样能对上
        sb.append("  \"foodFavorites\": ").append(
            JSONArray().apply { favorites.forEach { put(it) } }.toString()
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
                        // v1.0.6：这三项 + 图片路径以前漏了，恢复完纤维/钠/钙全变 0
                        put("fiberG", l.fiberG)
                        put("sodiumMg", l.sodiumMg)
                        put("calciumMg", l.calciumMg)
                        put("source", l.source)
                        put("aiConfidence", l.aiConfidence ?: JSONObject.NULL)
                        put("imagePath", l.imagePath ?: JSONObject.NULL)
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
        ).append(",\n")

        // ---------- v2 新增的五段 ----------

        sb.append("  \"bodyMeasures\": ").append(
            JSONArray().apply {
                bodyMeasures.forEach { b ->
                    put(JSONObject().apply {
                        put("date", b.date.toString())
                        put("waistCm", b.waistCm ?: JSONObject.NULL)
                        put("chestCm", b.chestCm ?: JSONObject.NULL)
                        put("hipCm", b.hipCm ?: JSONObject.NULL)
                        put("thighCm", b.thighCm ?: JSONObject.NULL)
                        put("armCm", b.armCm ?: JSONObject.NULL)
                        put("note", b.note)
                        put("createdAt", b.createdAt)
                    })
                }
            }.toString()
        ).append(",\n")

        // 照片只带路径：文件本身在 App 私有目录里，备份文件没法把 jpg 一起塞进来
        sb.append("  \"photos\": ").append(
            JSONArray().apply {
                photos.forEach { p ->
                    put(JSONObject().apply {
                        put("date", p.date.toString())
                        put("path", p.path)
                        put("note", p.note)
                        put("createdAt", p.createdAt)
                    })
                }
            }.toString()
        ).append(",\n")

        sb.append("  \"exercises\": ").append(
            JSONArray().apply {
                exercises.forEach { e ->
                    put(JSONObject().apply {
                        put("date", e.date.toString())
                        put("type", e.type)
                        put("minutes", e.minutes)
                        put("kcal", e.kcal)
                        put("steps", e.steps)
                        put("distanceKm", e.distanceKm)
                        put("createdAt", e.createdAt)
                    })
                }
            }.toString()
        ).append(",\n")

        sb.append("  \"fastings\": ").append(
            JSONArray().apply {
                fastings.forEach { f ->
                    put(JSONObject().apply {
                        put("date", f.date.toString())
                        put("planHours", f.planHours)
                        put("eatStartMinute", f.eatStartMinute)
                        put("eatEndMinute", f.eatEndMinute)
                        put("completed", f.completed)
                        put("createdAt", f.createdAt)
                    })
                }
            }.toString()
        ).append(",\n")

        sb.append("  \"exercisePresets\": ").append(
            JSONArray().apply {
                customPresets.forEach { p ->
                    put(JSONObject().apply {
                        put("name", p.name)
                        put("met", p.met)
                        put("category", p.category)
                        put("favorite", p.favorite)
                    })
                }
            }.toString()
        ).append(",\n")

        // ---------- v4 新增：自律模块 7 张表 ----------
        // 放在 training 之前：training 是最后一段、结尾不带逗号，新段必须以逗号收尾，
        // 否则会拼出段段相接的非法 JSON。
        sb.append("  \"selfDiscipline\": ").append(selfDiscipline.toJson().toString()).append(",\n")

        // ---------- v3 新增：训练中心 ----------
        sb.append("  \"training\": ").append(training.toJson().toString()).append("\n")

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

            // ---------- 第一步：整份解析（完全不碰数据库） ----------
            // 这一步失败就原样返回，用户数据库一个字节都没动。
            // 旧版是把「清表」摆在最前面，解析到一半出错就变成
            // 「老数据没了、新数据只进一半」，找都找不回来。
            val parsed = parseBackup(text).getOrElse {
                return@withContext Result.failure(it)
            }

            // ---------- 第二步：解析全部成功，才开事务落库 ----------
            // 清「记录类」+ 灌入新数据都在 restoreInto 里，任何一步失败整事务回滚。
            db.withTransaction {
                restoreInto(db, parsed)
            }

            Result.success(summaryText(parsed))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 覆盖恢复的完整落库（先清后灌），**必须**在调用方的事务里执行：
     * 中途任何一条写失败，整个事务回滚，数据库回到导入前。
     *
     * v1.1.0 覆盖恢复语义收口（任务书 P0 风险 A）：
     * - 备份 JSON 里**明确写了** profile / aiConfig 段的：有数据就覆盖，
     *   明确 null 就清空本机对应数据（导出端永远写这两个键，null = 导出设备本来就没有）；
     * - 键缺失（第三方工具生成的文件）：保留本机现状，不做猜测；
     * - API Key 依然不进备份、不恢复，保留本机安全边界。
     * 抽成 internal 并显式传库：单测可以直接灌内存 Room 验证这套语义。
     */
    internal suspend fun restoreInto(target: AppDatabase, p: ParsedBackup) {
        // 清「记录类」+ 用户自建食物/收藏。
        // 预置运动库不动 —— 它是 App 自带的，不是用户数据。
        target.foodLogDao().clearAll()
        target.weightLogDao().clearAll()
        target.waterLogDao().clearAll()
        target.diaryDao().clearAll()
        target.usageStatDao().clearAll()
        target.userFoodDao().clearAll()
        target.foodFavoriteDao().clearAll()
        target.bodyMeasureDao().clearAll()
        target.progressPhotoDao().clearAll()
        target.exerciseLogDao().clearAll()
        target.fastingLogDao().clearAll()
        // 只清用户自建的运动，预置的 30 条留着
        target.exercisePresetDao()
            .deleteByCategory(com.qingji.app.domain.ExerciseCalculator.CUSTOM_CATEGORY)

        if (p.hasProfile) {
            val dao = target.userProfileDao()
            if (p.profile != null) dao.save(p.profile!!) else dao.clear()
        }
        if (p.hasAiConfig) {
            val dao = target.aiConfigDao()
            if (p.aiConfig != null) dao.save(p.aiConfig!!) else dao.clearAll()
        }

        if (p.foods.isNotEmpty()) target.userFoodDao().insertAll(p.foods)

        // 收藏按名字存（标准库只读，收藏只能记在用户库里）
        if (p.favorites.isNotEmpty()) {
            p.favorites.forEach { name ->
                target.foodFavoriteDao().add(com.qingji.app.data.local.entity.FoodFavorite(name))
            }
        } else if (p.version < 2) {
            // v1 的老备份没有 foodFavorites 段，用「用户食物里 favorite=1」兜底
            p.foods.filter { it.favorite }.forEach {
                target.foodFavoriteDao().add(com.qingji.app.data.local.entity.FoodFavorite(it.name))
            }
        }

        if (p.foodLogs.isNotEmpty()) target.foodLogDao().insertAll(p.foodLogs)
        p.weights.forEach { target.weightLogDao().save(it) }
        p.waters.forEach { target.waterLogDao().insert(it) }
        p.diaries.forEach { target.diaryDao().save(it) }
        p.usage.forEach { target.usageStatDao().insertIfAbsent(it) }

        // v2 的五段：老备份里没有，列表是空的，这里自然什么都不做
        p.bodyMeasures.forEach { target.bodyMeasureDao().save(it) }
        p.photos.forEach { target.progressPhotoDao().insert(it) }
        p.exercises.forEach { target.exerciseLogDao().insert(it) }
        p.fastings.forEach { target.fastingLogDao().save(it) }
        p.customPresets.forEach { target.exercisePresetDao().insertOne(it) }

        // ---------- v3 训练段 ----------
        // 老备份（version < 3）里根本没有训练数据，一行都不动，避免把新记的训练清掉。
        // 段本身读坏了（hasTraining=true 但 training=null）也选择保留本机数据，宁可不覆盖。
        val t = p.training
        if (p.version >= 3 && t != null) {
            target.exerciseDao().clearAll()
            target.trainingPlanDao().clearAll()
            target.trainingPlanDayDao().clearAll()
            target.planExerciseDao().clearAll()
            target.workoutSessionDao().clearAll()
            target.workoutExerciseDao().clearAll()
            if (t.exercises.isNotEmpty()) target.exerciseDao().insertAll(t.exercises)
            if (t.plans.isNotEmpty()) target.trainingPlanDao().insertAll(t.plans)
            if (t.planDays.isNotEmpty()) target.trainingPlanDayDao().insertAll(t.planDays)
            if (t.planExercises.isNotEmpty()) target.planExerciseDao().insertAll(t.planExercises)
            if (t.sessions.isNotEmpty()) target.workoutSessionDao().insertAll(t.sessions)
            if (t.workoutExercises.isNotEmpty()) target.workoutExerciseDao().insertAll(t.workoutExercises)
        }

        // ---------- v4 自律段 ----------
        // 和 training 一样：version < 4 的老备份没有自律数据，而模块上线后本机可能已有记录，
        // 一行都不动，避免导旧备份把新记的自律数据清掉。
        // 段读坏了（hasSelfDiscipline=true 但 sd=null）也保留本机，宁可不覆盖。
        // 红线：清表绝不能放进上面「无条件清记录」那批——那批对 v1/v2/v3 老备份也执行。
        // 清灌都在调用方的 db.withTransaction 内，保持「整份解析成功才开事务」。
        val sd = p.selfDiscipline
        if (p.version >= 4 && sd != null) {
            target.selfDisciplineGoalDao().clearAll()
            target.selfDisciplinePeriodDao().clearAll()
            target.selfDisciplineCheckinDao().clearAll()
            target.selfDisciplineUrgeDao().clearAll()
            target.selfDisciplineSettingDao().clearAll()
            target.selfDisciplineMilestoneDao().clearAll()
            target.selfDisciplineInterventionDao().clearAll()
            // 恢复顺序：goals 先 → 6 张 goalId 关联 / 子表 → settings 最后
            if (sd.goals.isNotEmpty()) target.selfDisciplineGoalDao().insertAll(sd.goals)
            if (sd.periods.isNotEmpty()) target.selfDisciplinePeriodDao().insertAll(sd.periods)
            if (sd.checkins.isNotEmpty()) target.selfDisciplineCheckinDao().insertAll(sd.checkins)
            if (sd.urgeLogs.isNotEmpty()) target.selfDisciplineUrgeDao().insertAll(sd.urgeLogs)
            if (sd.milestones.isNotEmpty()) target.selfDisciplineMilestoneDao().insertAll(sd.milestones)
            if (sd.interventions.isNotEmpty()) target.selfDisciplineInterventionDao().insertAll(sd.interventions)
            if (sd.settings.isNotEmpty()) target.selfDisciplineSettingDao().insertAll(sd.settings)
        }
    }

    /** 导入完成后给用户看的一句话摘要：导进了多少、哪些东西没跟过来。 */
    private fun summaryText(p: ParsedBackup): String {
        val parts = mutableListOf<String>()
        if (p.foodLogs.isNotEmpty()) parts.add("饮食 ${p.foodLogs.size} 条")
        if (p.weights.isNotEmpty()) parts.add("体重 ${p.weights.size} 条")
        if (p.waters.isNotEmpty()) parts.add("饮水 ${p.waters.size} 条")
        if (p.diaries.isNotEmpty()) parts.add("日记 ${p.diaries.size} 篇")
        if (p.bodyMeasures.isNotEmpty()) parts.add("围度 ${p.bodyMeasures.size} 次")
        if (p.exercises.isNotEmpty()) parts.add("运动 ${p.exercises.size} 次")
        if (p.fastings.isNotEmpty()) parts.add("断食 ${p.fastings.size} 天")
        if (p.photos.isNotEmpty()) parts.add("照片条目 ${p.photos.size} 张")
        if (p.foods.isNotEmpty()) parts.add("自建食物 ${p.foods.size} 种")
        if (p.training != null && p.training.sessions.isNotEmpty()) {
            parts.add("训练 ${p.training.sessions.size} 次")
        }
        val sd = p.selfDiscipline
        if (sd != null) {
            val total = sd.goals.size + sd.periods.size + sd.checkins.size +
                sd.urgeLogs.size + sd.settings.size + sd.milestones.size + sd.interventions.size
            if (total > 0) parts.add("自律 $total 条")
        }

        val body = if (parts.isEmpty()) "这份备份里没有记录" else parts.joinToString("、")
        val photoNote = if (p.photos.isNotEmpty()) "照片文件本身不在备份里，需要从旧手机把图片拷过来。" else ""
        return "导入完成：$body。$photoNote AI Key 不随备份走，需要重新填一次。"
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
