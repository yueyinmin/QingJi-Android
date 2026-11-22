package com.qingji.app.training

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.backup.BackupManager
import com.qingji.app.data.backup.TrainingBackup
import com.qingji.app.data.backup.parseBackup
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.WorkoutSession
import com.qingji.app.data.repository.HealthRepository
import com.qingji.app.data.repository.TrainingRepository
import com.qingji.app.domain.BackupFormat
import com.qingji.app.domain.WorkoutCalorieCalculator
import com.qingji.app.domain.WorkoutItem
import com.qingji.app.domain.WorkoutSet
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * B1.1.2：真实 restoreInto() 事务中途异常回滚测试。
 * 区分于 B1.1.1 的「解析失败保护」——这里异常发生在 db.withTransaction 内、
 * 训练段已清表未灌入的中途点，验证 Room 整事务回滚，已有数据一字不动。
 */
@RunWith(RobolectricTestRunner::class)
class BackupRollbackB112Test {

    private lateinit var db: AppDatabase
    private lateinit var repo: TrainingRepository
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = TrainingRepository(ctx, refreshHook = { _, _ -> }, dbOverride = db)
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `恢复中途真实数据库异常后整事务回滚已有训练明细账本日记全部保留`() = runBlocking {
        // 1) 本机先存一场混合训练（力量 + 有氧 + 账本 + 日记投影）
        val uuid = "rollback-keep"
        repo.saveUnifiedSession(
            WorkoutSession(uuid, LocalDate.of(2026, 11, 21), 30, note = "要保住的训练",
                source = "manual", createdAt = 1L),
            listOf(WorkoutItem.Strength(
                itemId = "s1", exerciseUuid = "ex-squat", name = "深蹲", order = 0,
                sets = listOf(WorkoutSet(weightKg = 50.0, reps = 10)))),
            listOf(WorkoutItem.Cardio(
                itemId = "c1", exerciseUuid = "wg:run", name = "跑步", order = 1,
                durationSec = 1800L, distanceMeters = 5000.0,
                calculatedKcal = 150.0, calculationMethod = WorkoutCalorieCalculator.METHOD_MET_TYPE_DEFAULT,
                calculationVersion = WorkoutCalorieCalculator.VERSION, weightSnapshot = 70.0)),
            70.0
        )
        // 记录异常前快照
        assertNotNull(db.workoutSessionDao().findByUuid(uuid))
        assertEquals(1, db.workoutExerciseDao().findBySession(uuid).size)
        assertEquals(1, db.workoutCardioDetailDao().findBySession(uuid).size)
        assertNotNull(db.exerciseSessionCalorieDao()
            .findBySource(HealthRepository.SOURCE_STRENGTH_TRAINING, uuid))
        assertNotNull(db.exerciseLogDao().findBySourceId(uuid))

        // 2) 构造一份合法 v8 备份（training 段有数据，足以走到清表后灌入）
        val backup = TrainingBackup(
            exercises = db.exerciseDao().findAll(),
            sessions = db.workoutSessionDao().findAll(),
            workoutExercises = db.workoutExerciseDao().findAll(),
            cardioDetails = db.workoutCardioDetailDao().findAll()
        )
        val root = org.json.JSONObject().apply {
            put("app", "qingji")
            put("version", BackupFormat.VERSION)
            put("training", backup.toJson())
        }
        val parsed = parseBackup(root.toString()).getOrThrow()

        // 3) 真实 restoreInto()，在训练段清表后抛异常；调用方事务包住（与 importJson 一致）
        val manager = BackupManager(ctx)
        var thrown = false
        manager.restoreHook = { throw IllegalStateException("模拟灌入中途数据库异常") }
        try {
            db.withTransaction {
                manager.restoreInto(db, parsed)
            }
            fail("应抛出事务中途异常")
        } catch (e: IllegalStateException) {
            thrown = e.message?.contains("数据库异常") == true
        }
        assertTrue("restoreHook 异常应传播出来", thrown)

        // 4) 验证整事务回滚：力量/有氧明细、账本、日记投影全部原样保留
        assertNotNull("会话必须回滚保留", db.workoutSessionDao().findByUuid(uuid))
        assertEquals("力量明细必须回滚保留", 1, db.workoutExerciseDao().findBySession(uuid).size)
        assertEquals("有氧明细必须回滚保留", 1, db.workoutCardioDetailDao().findBySession(uuid).size)
        assertNotNull("账本必须回滚保留",
            db.exerciseSessionCalorieDao().findBySource(HealthRepository.SOURCE_STRENGTH_TRAINING, uuid))
        assertNotNull("日记投影必须回滚保留", db.exerciseLogDao().findBySourceId(uuid))
        assertEquals("训练备注不得被覆盖", "要保住的训练",
            db.workoutSessionDao().findByUuid(uuid)!!.note)
    }
}
