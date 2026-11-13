package com.qingji.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.TrainingSeed
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.local.entity.WorkoutExercise
import com.qingji.app.data.local.entity.WorkoutSession
import com.qingji.app.data.prefs.WorkoutDraftStore
import com.qingji.app.domain.TrainingMath
import com.qingji.app.domain.WorkoutSet
import kotlinx.coroutines.flow.Flow

/**
 * ============================================================
 * 训练中心的读写仓库（P0）
 * ------------------------------------------------------------
 * 三条铁律：
 * 1. 会话和它下面的动作/组必须一次事务写完，不许出现「有会话没明细」；
 * 2. 历史只读 workout_exercise.exerciseNameSnapshot，绝不 JOIN 动作库现名；
 * 3. 力量训练 P0 不写 exercise_log、不产生热量消耗，
 *    现有「运动补偿 50%」通道完全不碰（有氧记录仍走原来的记运动流程）。
 * ============================================================
 */
class TrainingRepository(context: Context) {

    private val db = AppDatabase.get(context)
    private val exerciseDao = db.exerciseDao()
    private val planDao = db.trainingPlanDao()
    private val planDayDao = db.trainingPlanDayDao()
    private val planExerciseDao = db.planExerciseDao()
    private val sessionDao = db.workoutSessionDao()
    private val workoutExerciseDao = db.workoutExerciseDao()

    /** 进行中草稿与 kg/lb 偏好（SharedPreferences，不进数据库）。 */
    val draftStore = WorkoutDraftStore(context)

    fun observeExercises(): Flow<List<ExerciseEntity>> = exerciseDao.observeAll()
    fun observeSessions(): Flow<List<WorkoutSession>> = sessionDao.observeAll()
    fun observeSessionExercises(sessionUuid: String): Flow<List<WorkoutExercise>> =
        workoutExerciseDao.observeBySession(sessionUuid)

    /** 训练日记算每次的总组数 / 训练量用，跟着库变化自动刷新。 */
    fun observeAllWorkoutExercises(): Flow<List<WorkoutExercise>> =
        workoutExerciseDao.observeAll()

    /** 首次进训练中心时灌最小动作库；灌过就不再动，避免覆盖用户自建/收藏状态。 */
    suspend fun ensureSeed() {
        if (exerciseDao.count() == 0) {
            exerciseDao.insertAll(TrainingSeed.builtIns())
        }
    }

    suspend fun addCustomExercise(name: String, category: String): ExerciseEntity? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        // 名字唯一索引：同名就改成直接复用已有的那条，别让界面崩在 SQLiteConstraintException
        val existing = exerciseDao.findAll().firstOrNull { it.name == trimmed }
        if (existing != null) return existing
        val now = System.currentTimeMillis()
        val item = ExerciseEntity(
            uuid = "user-${java.util.UUID.randomUUID()}",
            name = trimmed,
            category = category.ifBlank { TrainingSeed.CORE },
            isBuiltIn = false,
            createdAt = now,
            updatedAt = now
        )
        exerciseDao.insertAll(listOf(item))
        return item
    }

    suspend fun toggleFavorite(uuid: String, favorite: Boolean) {
        val item = exerciseDao.findByUuid(uuid) ?: return
        exerciseDao.update(item.copy(isFavorite = favorite, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteCustomExercise(uuid: String) = exerciseDao.deleteCustom(uuid)

    /** 「上次成绩自动带出」。动作被改名/删除时由 DAO 的名字快照回退匹配兜底。 */
    suspend fun lastSets(exerciseUuid: String, nameSnapshot: String): List<WorkoutSet> =
        TrainingMath.decodeSets(workoutExerciseDao.findLatestSets(exerciseUuid, nameSnapshot))

    /** 保存一次训练（新建与「当天编辑」共用），整包写、整包替换明细。 */
    suspend fun saveSession(session: WorkoutSession, items: List<WorkoutExercise>) {
        db.withTransaction {
            sessionDao.insert(session)
            workoutExerciseDao.deleteBySession(session.uuid)
            // id 一律重新生成，避免编辑时把老明细的 id 带进来撞主键
            workoutExerciseDao.insertAll(items.map { it.copy(id = 0L) })
        }
    }

    /** 删除会话要连明细一起删，不留孤儿行。 */
    suspend fun deleteSession(uuid: String) {
        db.withTransaction {
            workoutExerciseDao.deleteBySession(uuid)
            sessionDao.deleteByUuid(uuid)
        }
    }

    suspend fun sessionDetail(uuid: String): Pair<WorkoutSession?, List<WorkoutExercise>> =
        sessionDao.findByUuid(uuid) to workoutExerciseDao.findBySession(uuid)

    suspend fun allTrainingDates() = sessionDao.findAllDates()

    // 备份的整表导出/覆盖恢复统一放在 data/backup/TrainingBackup.kt + BackupManager，
    // 这里不再做第二套，免得以后两边字段不一致。
}

