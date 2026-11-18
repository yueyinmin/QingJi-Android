package com.qingji.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.dao.DailyExerciseCalories
import com.qingji.app.data.local.entity.ExerciseCalorieConfigEntity
import com.qingji.app.data.local.entity.ExerciseLog
import com.qingji.app.data.local.entity.ExerciseSessionCalorieEntity
import com.qingji.app.data.local.entity.WorkoutExercise
import com.qingji.app.data.local.entity.WorkoutSession
import com.qingji.app.domain.SessionCalorieCalculator
import com.qingji.app.domain.TrainingMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * ============================================================
 * 健康体系 V1.0 · P0：统一运动消耗账本的读写与补算
 * ------------------------------------------------------------
 * 账本是全 App 运动消耗的唯一汇总出口：
 *   训练中心场次（力量/自重）→ 结算后写一行（STRENGTH_TRAINING）
 *   独立运动模块的 exercise_log → 镜像一行（CARDIO / WALKING / MANUAL_EXERCISE）
 *   未来的设备导入 / 手表 → DEVICE_IMPORTED（预留）
 * 首页「运动消耗」、周小结缺口、以后的每日健康汇总都从这里读，
 * 不同页面算出来的数字保证一致。
 *
 * 去重：唯一索引 (sourceType, sourceId)，同一场永远只有一行；
 * 重复保存 / 重复结束 / 重新结算都是整行替换，不会叠加。
 *
 * 账本是「可重算的缓存」：ensureLedgerBackfill() 可以随时重跑，
 * 从 workout_session + exercise_log 重新生成缺失的行（幂等），
 * 所以它不进备份文件——备份恢复后重跑补算即可，备份 schema 不用动。
 * ============================================================
 */
class HealthRepository(private val context: Context) {

    private val db = AppDatabase.get(context)
    private val configDao = db.exerciseCalorieConfigDao()
    private val ledgerDao = db.exerciseSessionCalorieDao()

    /** 训练仓库删除会话时联动清账本用（同库事务内调用）。 */
    internal fun ledgerDao(): com.qingji.app.data.local.dao.ExerciseSessionCalorieDao = ledgerDao

    /** 当日运动总消耗（毛消耗；补偿折算在额度展示层做，不动原始数据）。 */
    fun observeDailyExerciseCalories(date: LocalDate): Flow<Double> =
        ledgerDao.observeDailyTotal(date).map { it ?: 0.0 }

    /** 区间逐日合计（周小结用）；当天没记录就没有这一天的键。 */
    fun observeRangeTotals(from: LocalDate, to: LocalDate): Flow<List<DailyExerciseCalories>> =
        ledgerDao.observeRangeTotals(from, to)

    /** 当日账本明细（详情/调试用）。 */
    fun observeByDate(date: LocalDate): Flow<List<ExerciseSessionCalorieEntity>> =
        ledgerDao.observeByDate(date)

    /**
     * 类型级 MET 参数表：activityType → (low, moderate, high)。
     * 查不到时给保守兜底（中等力量 5.0），绝不因为参数缺失让结算挂掉。
     */
    suspend fun typeMetDefaults(): Map<String, Triple<Double, Double, Double>> =
        configDao.findTypeDefaults().associate {
            it.activityType to Triple(it.metLow, it.metModerate, it.metHigh)
        }.ifEmpty {
            mapOf("strength_moderate" to Triple(4.25, 5.0, 5.75))
        }

    /** 某动作的 MET 三档：先看动作级覆盖，再回退类型默认；类型认不出按中等力量。 */
    suspend fun metFor(exerciseId: String, activityType: String): Triple<Double, Double, Double> {
        configDao.findByExerciseId(exerciseId)?.let {
            return Triple(it.metLow, it.metModerate, it.metHigh)
        }
        val defaults = typeMetDefaults()
        return defaults[activityType]
            ?: defaults["strength_moderate"]
            ?: Triple(4.25, 5.0, 5.75)
    }

    /**
     * 结算一场训练并写账本（调用方负责和 saveSession 在同一个事务里跑，
     * 见 TrainingRepository.saveSession 的用法）。
     * 动作 MET 按动作库的 isStretch 分流：拉伸动作走 flexibility（2.3），
     * 其余走中等力量（5.0）；以后加动作级覆盖（exerciseId 参数行）自动生效。
     * 重复保存 = 整行替换，天然幂等。
     */
    suspend fun settleStrengthSession(
        session: WorkoutSession,
        items: List<WorkoutExercise>,
        weightKg: Double,
        method: String
    ): ExerciseSessionCalorieEntity {
        val exerciseDao = db.exerciseDao()
        val entries = items.mapNotNull { item ->
            val sets = TrainingMath.decodeSets(item.setsJson).filter { it.reps > 0 || it.weightKg > 0.0 }
            if (sets.isEmpty()) return@mapNotNull null
            val entity = exerciseDao.findByUuid(item.exerciseUuid)
            val activityType = if (entity?.isStretch == true) "flexibility" else "strength_moderate"
            val met = metFor(item.exerciseUuid, activityType)
            SessionCalorieCalculator.StrengthEntry(
                exerciseUuid = item.exerciseUuid,
                name = item.exerciseNameSnapshot,
                metLow = met.first,
                metModerate = met.second,
                metHigh = met.third,
                sets = sets
            )
        }
        val result = SessionCalorieCalculator.settleStrengthSession(
            entries = entries,
            weightKg = weightKg,
            durationMinutes = session.durationMinutes,
            method = method
        )
        val row = ExerciseSessionCalorieEntity(
            sourceType = SOURCE_STRENGTH_TRAINING,
            sourceId = session.uuid,
            date = session.date,
            durationSeconds = result.estimatedSeconds.toInt(),
            caloriesGross = result.totalKcal,
            weightSnapshot = weightKg,
            calculationMethod = result.method,
            calculationVersion = SessionCalorieCalculator.VERSION,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ledgerDao.upsert(row)
        return row
    }

    /**
     * 独立运动记录镜像进账本。caloriesGross 直接用 ExerciseLog 里已存的值
     * （那是用户在确认页算好的，口径不变）；type 字符串映射来源类型。
     * REPLACE 保证同一 id 永远只有一行、且值和源记录一致。
     */
    suspend fun mirrorExerciseLog(log: ExerciseLog) {
        ledgerDao.upsert(
            ExerciseSessionCalorieEntity(
                sourceType = sourceTypeForExerciseLog(log.type),
                sourceId = "exlog:${log.id}",
                date = log.date,
                durationSeconds = log.minutes * 60,
                caloriesGross = log.kcal,
                weightSnapshot = 0.0,
                calculationMethod = if (log.kcal > 0) SessionCalorieCalculator.METHOD_USER_DIRECT
                else SessionCalorieCalculator.METHOD_EXERCISE_LOG,
                calculationVersion = SessionCalorieCalculator.VERSION,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteExerciseLogMirror(logId: Long) {
        ledgerDao.deleteExerciseLogMirrorBySourceId("exlog:$logId")
    }

    /** 运动类型字符串 → 账本来源类型。认不出的都归 MANUAL_EXERCISE。 */
    private fun sourceTypeForExerciseLog(type: String): String = when (type) {
        "走路" -> SOURCE_WALKING
        "跑步", "骑行", "游泳" -> SOURCE_CARDIO
        else -> SOURCE_MANUAL_EXERCISE
    }

    // ---------- 历史补算（决策点 4：保守估算，标记 estimation，幂等可重跑） ----------

    /**
     * 把历史数据补进账本。三个来源各自幂等：
     * 1. exercise_log 镜像：REPLACE（自愈：源记录变了/没了都能对齐）；
     * 2. 历史训练场次：有逐组数据按组结算（estimation_history），
     *    只有时长用保守公式（estimation_history_duration，MET×0.7），
     *    已有结算行的场次（IGNORE）绝不覆盖；
     * 3. 清掉来源已消失的孤儿镜像行。
     *
     * 快速路径：训练场次数 == 账本里 STRENGTH_TRAINING 行数时跳过场次补算，
     * 日常启动零成本；训练删除/新增都会让两边计数变化，自动触发重跑。
     */
    suspend fun ensureLedgerBackfill(weightKg: Double) {
        // 1) 孤儿行清理（备份恢复/清记录后对齐）：镜像行 + 强度行
        ledgerDao.deleteOrphanExerciseLogMirrors()
        ledgerDao.deleteOrphanStrengthRows()

        // 2) exercise_log 镜像（全量 REPLACE，量小：几千行以内毫秒级）
        db.exerciseLogDao().findAll().forEach { mirrorExerciseLog(it) }

        // 3) 历史训练场次补算
        val sessionDao = db.workoutSessionDao()
        val workoutExerciseDao = db.workoutExerciseDao()
        val sessions = sessionDao.findAll()
        val settledCount = ledgerDao.countByType(SOURCE_STRENGTH_TRAINING)
        if (sessions.size == settledCount) return

        val existingSourceIds = ledgerDao.findSourceIds(SOURCE_STRENGTH_TRAINING).toSet()
        val metCache = typeMetDefaults()
        sessions.forEach { session ->
            if (session.uuid in existingSourceIds) return@forEach
            val items = workoutExerciseDao.findBySession(session.uuid)
            if (items.isEmpty()) {
                // 只有时长：保守估算（中等力量 MET × 0.7）；连时长都没有就不造数
                val met = metCache["strength_moderate"]?.second ?: 5.0
                val kcal = SessionCalorieCalculator.estimateFromDurationOnly(
                    session.durationMinutes, weightKg, met
                )
                if (kcal > 0.0) {
                    ledgerDao.insertMissing(
                        ExerciseSessionCalorieEntity(
                            sourceType = SOURCE_STRENGTH_TRAINING,
                            sourceId = session.uuid,
                            date = session.date,
                            durationSeconds = session.durationMinutes * 60,
                            caloriesGross = kcal,
                            weightSnapshot = weightKg,
                            calculationMethod = SessionCalorieCalculator.METHOD_ESTIMATION_DURATION,
                            calculationVersion = SessionCalorieCalculator.VERSION,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            } else {
                settleStrengthSession(
                    session = session,
                    items = items,
                    weightKg = weightKg,
                    method = SessionCalorieCalculator.METHOD_ESTIMATION_HISTORY
                )
            }
        }
    }

    suspend fun configCount(): Int = configDao.typeDefaultCount()

    suspend fun ledgerAll(): List<ExerciseSessionCalorieEntity> = ledgerDao.findAll()

    /** 全量账本观察流（进展页运动消耗卡用，力量+有氧统一口径，记完自动刷新）。 */
    fun observeAllLedger(): Flow<List<ExerciseSessionCalorieEntity>> = ledgerDao.observeAll()

    /**
     * 从「记一次 → 选择训练」调整某场训练已计入的消耗（2026-11-18）。
     *
     * 关键：训练保存时账本已经有一行 STRENGTH_TRAINING / sourceId = 场次 uuid。
     * 这里走 upsert 覆盖同一行，只改消耗值、不改来源和日期——
     * 绝不新建 exercise_log，否则镜像又会写一行，同一场训练被记两次。
     *
     * @return 是否找到并更新了那场训练的账本行（没找到返回 false，调用方如实提示）
     */
    suspend fun adjustTrainingCalorie(sessionUuid: String, kcal: Double): Boolean {
        val row = ledgerDao.findBySource(SOURCE_STRENGTH_TRAINING, sessionUuid) ?: return false
        ledgerDao.upsert(
            row.copy(
                caloriesGross = kcal.coerceAtLeast(0.0),
                calculationMethod = "user_direct",
                updatedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    /** 取某场训练当前已计入的消耗（给选择弹层回填当前值），没记过返回 null。 */
    suspend fun trainingCalorie(sessionUuid: String): Double? =
        ledgerDao.findBySource(SOURCE_STRENGTH_TRAINING, sessionUuid)?.caloriesGross

    companion object {
        const val SOURCE_STRENGTH_TRAINING = "STRENGTH_TRAINING"
        const val SOURCE_CARDIO = "CARDIO"
        const val SOURCE_WALKING = "WALKING"
        const val SOURCE_DEVICE_IMPORTED = "DEVICE_IMPORTED"
        const val SOURCE_MANUAL_EXERCISE = "MANUAL_EXERCISE"
    }
}
