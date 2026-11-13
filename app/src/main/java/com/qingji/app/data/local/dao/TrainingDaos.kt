package com.qingji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.local.entity.PlanExercise
import com.qingji.app.data.local.entity.TrainingPlan
import com.qingji.app.data.local.entity.TrainingPlanDay
import com.qingji.app.data.local.entity.WorkoutExercise
import com.qingji.app.data.local.entity.WorkoutSession
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 训练中心六张表的读写入口。
 * 跟 Daos.kt 保持同一套规矩：界面不写 SQL；返回 Flow 的会自动通知界面。
 */

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise ORDER BY isFavorite DESC, name ASC")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise ORDER BY isFavorite DESC, name ASC")
    suspend fun findAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise WHERE uuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExerciseEntity>)

    @Update
    suspend fun update(item: ExerciseEntity)

    @Query("DELETE FROM exercise WHERE uuid = :uuid AND isBuiltIn = 0")
    suspend fun deleteCustom(uuid: String)

    @Query("DELETE FROM exercise")
    suspend fun clearAll()
}

@Dao
interface TrainingPlanDao {
    @Query("SELECT * FROM training_plan ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TrainingPlan>>

    @Query("SELECT * FROM training_plan ORDER BY createdAt DESC")
    suspend fun findAll(): List<TrainingPlan>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TrainingPlan>)

    @Query("DELETE FROM training_plan")
    suspend fun clearAll()
}

@Dao
interface TrainingPlanDayDao {
    @Query("SELECT * FROM training_plan_day ORDER BY planUuid, weekday")
    suspend fun findAll(): List<TrainingPlanDay>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TrainingPlanDay>)

    @Query("DELETE FROM training_plan_day")
    suspend fun clearAll()
}

@Dao
interface PlanExerciseDao {
    @Query("SELECT * FROM plan_exercise ORDER BY dayId, orderInDay")
    suspend fun findAll(): List<PlanExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PlanExercise>)

    @Query("DELETE FROM plan_exercise")
    suspend fun clearAll()
}

@Dao
interface WorkoutSessionDao {
    @Query("SELECT * FROM workout_session ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_session ORDER BY date DESC, createdAt DESC")
    suspend fun findAll(): List<WorkoutSession>

    @Query("SELECT * FROM workout_session WHERE date = :date ORDER BY createdAt DESC")
    fun observeByDate(date: LocalDate): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_session WHERE uuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): WorkoutSession?

    @Query("SELECT DISTINCT date FROM workout_session")
    suspend fun findAllDates(): List<LocalDate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WorkoutSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WorkoutSession>)

    @Query("DELETE FROM workout_session WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("DELETE FROM workout_session")
    suspend fun clearAll()
}

@Dao
interface WorkoutExerciseDao {
    @Query("SELECT * FROM workout_exercise WHERE sessionUuid = :sessionUuid ORDER BY orderInSession ASC, id ASC")
    fun observeBySession(sessionUuid: String): Flow<List<WorkoutExercise>>

    @Query("SELECT * FROM workout_exercise WHERE sessionUuid = :sessionUuid ORDER BY orderInSession ASC, id ASC")
    suspend fun findBySession(sessionUuid: String): List<WorkoutExercise>

    @Query("SELECT * FROM workout_exercise ORDER BY id ASC")
    suspend fun findAll(): List<WorkoutExercise>

    /** 训练日记列表要按会话聚合算「组数 / 训练量」，一次取全量比逐条查快。 */
    @Query("SELECT * FROM workout_exercise ORDER BY id ASC")
    fun observeAll(): Flow<List<WorkoutExercise>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WorkoutExercise>)

    @Query("DELETE FROM workout_exercise WHERE sessionUuid = :sessionUuid")
    suspend fun deleteBySession(sessionUuid: String)

    @Query("DELETE FROM workout_exercise")
    suspend fun clearAll()

    /**
     * 「上次成绩自动带出」：同一个动作，取最近一次训练里最后保存的那包组数据。
     * 有 uuid 优先按 uuid 找；自建动作被删过（uuid 为空串）时，退回按名字快照精确匹配。
     */
    @Query(
        """
        SELECT we.setsJson FROM workout_exercise we
        INNER JOIN workout_session ws ON ws.uuid = we.sessionUuid
        WHERE ((:exerciseUuid <> '' AND we.exerciseUuid = :exerciseUuid)
           OR (:exerciseUuid = '' AND we.exerciseNameSnapshot = :nameSnapshot))
          AND we.setsJson <> ''
        ORDER BY ws.date DESC, ws.createdAt DESC, we.id DESC
        LIMIT 1
        """
    )
    suspend fun findLatestSets(exerciseUuid: String, nameSnapshot: String): String?
}
