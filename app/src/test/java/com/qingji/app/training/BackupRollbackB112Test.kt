package com.qingji.app.training

import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.backup.BackupManager
import com.qingji.app.data.backup.TrainingBackup
import com.qingji.app.data.backup.parseBackup
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.WorkoutSession
import com.qingji.app.data.prefs.SettingsKeys
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.data.repository.HealthRepository
import com.qingji.app.data.repository.TrainingRepository
import com.qingji.app.domain.BackupFormat
import com.qingji.app.domain.WorkoutCalorieCalculator
import com.qingji.app.domain.WorkoutItem
import com.qingji.app.domain.WorkoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowLog
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

    @Test
    fun `恢复落库只返回AI激活设置副作用不在Room事务内写DataStore`() = runBlocking {
        val newId = 42
        val aiConfig = JSONObject().apply {
            put("id", newId)
            put("displayName", "测试服务")
            put("provider", "custom")
            put("baseUrl", "https://example.com/v1")
            put("textModel", "text-model")
            put("visionModel", "vision-model")
            put("temperature", 0.2)
            put("timeoutSec", 30)
            put("dailyLimit", 100)
            put("enabled", true)
        }
        val root = JSONObject().apply {
            put("app", "qingji")
            put("version", BackupFormat.VERSION)
            put("aiConfigs", JSONArray().put(aiConfig))
        }
        val parsed = parseBackup(root.toString()).getOrThrow()
        val before = SettingsStore.observe(ctx).first().aiActiveProviderId

        val manager = BackupManager(ctx)
        val effects = db.withTransaction { manager.restoreInto(db, parsed) }

        assertEquals("应返回待提交后应用的 AI 激活 ID", newId, effects.aiActiveProviderId)
        assertEquals("Room 事务内不得写 DataStore 设置", before,
            SettingsStore.observe(ctx).first().aiActiveProviderId)

        // 显式应用副作用后，设置才更新（模拟 importJson 事务提交后的行为）
        SettingsStore.set(ctx, SettingsKeys.AI_ACTIVE_PROVIDER_ID, effects.aiActiveProviderId!!)
        assertEquals(newId, SettingsStore.observe(ctx).first().aiActiveProviderId)
    }

    // ==================== B1.1.2.2：importJson() 真实入口 ====================

    /** 构造一份最小合法 v8 备份；withTraining=true 时带上训练段，供事务中途注入异常。 */
    private fun entryBackup(aiId: Int, withTraining: Boolean): String = JSONObject().apply {
        put("app", "qingji")
        put("version", BackupFormat.VERSION)
        put("aiConfigs", JSONArray().put(JSONObject().apply {
            put("id", aiId)
            put("displayName", "导入的服务")
            put("provider", "custom")
            put("baseUrl", "https://example.com/v1")
            put("textModel", "text-model")
            put("visionModel", "vision-model")
            put("temperature", 0.2)
            put("timeoutSec", 30)
            put("dailyLimit", 100)
            put("enabled", true)
        }))
        if (withTraining) {
            put("training", TrainingBackup(
                sessions = listOf(WorkoutSession(
                    "restore-entry", LocalDate.of(2026, 11, 22), 30,
                    note = "恢复进来的训练", source = "manual", createdAt = 1L
                ))
            ).toJson())
        }
    }.toString()

    /** Robolectric 下用 content:// Uri 注册输入流，走 ContentResolver 的真实读取分支。 */
    private fun registerBackup(text: String): Uri {
        val uri = Uri.parse("content://test.backup/restore.json")
        Shadows.shadowOf(ctx.contentResolver).registerInputStream(uri, text.byteInputStream())
        return uri
    }

    @Test
    fun `importJson事务提交成功后才应用AI设置副作用`() = runBlocking {
        SettingsStore.set(ctx, SettingsKeys.AI_ACTIVE_PROVIDER_ID, 11)
        val manager = BackupManager(ctx, dbOverride = db)
        val result = manager.importJson(registerBackup(entryBackup(77, withTraining = false)))

        assertTrue("入口应返回成功", result.isSuccess)
        assertEquals("事务提交后 AI 激活设置必须更新", 77,
            SettingsStore.observe(ctx).first().aiActiveProviderId)
        assertEquals("备份中的 AI 配置必须落库", 1, db.aiConfigDao().getAll().size)
    }

    @Test
    fun `importJson事务中途失败时完全不应用AI设置副作用`() = runBlocking {
        SettingsStore.set(ctx, SettingsKeys.AI_ACTIVE_PROVIDER_ID, 11)
        val manager = BackupManager(ctx, dbOverride = db)
        manager.restoreHook = { throw IllegalStateException("模拟入口事务中途失败") }
        val result = manager.importJson(registerBackup(entryBackup(77, withTraining = true)))

        assertTrue("入口应返回失败", result.isFailure)
        assertEquals("事务回滚后设置不得被改动", 11,
            SettingsStore.observe(ctx).first().aiActiveProviderId)
        assertEquals("事务回滚后 AI 配置不得部分写入", 0, db.aiConfigDao().getAll().size)
        assertEquals("事务回滚后训练段不得部分写入", 0, db.workoutSessionDao().findAll().size)
    }

    @Test
    fun `importJson外部副作用失败分别记录日志且数据库恢复仍成功`() = runBlocking {
        val manager = BackupManager(
            ctx,
            dbOverride = db,
            aiSettingWriter = { throw IllegalStateException("模拟AI设置写入失败") },
            widgetRefresher = { throw IllegalStateException("模拟桌面组件刷新失败") }
        )
        val result = manager.importJson(registerBackup(entryBackup(88, withTraining = false)))

        assertTrue("外部副作用失败不得改写数据库恢复成功", result.isSuccess)
        assertEquals("数据库恢复必须已完成", 1, db.aiConfigDao().getAll().size)

        val messages = ShadowLog.getLogs().map { it.msg }
        assertTrue("AI 设置失败应有独立日志", messages.any { it.contains("模拟AI设置写入失败") })
        assertTrue("Widget 刷新失败应有独立日志", messages.any { it.contains("模拟桌面组件刷新失败") })
    }
}
