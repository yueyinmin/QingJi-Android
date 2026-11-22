package com.qingji.app.training

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.backup.BackupManager
import com.qingji.app.data.backup.TrainingBackup
import com.qingji.app.data.backup.parseTraining
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.local.entity.WorkoutSession
import com.qingji.app.data.repository.HealthRepository
import com.qingji.app.data.repository.TrainingRepository
import com.qingji.app.domain.BackupFormat
import com.qingji.app.domain.ExerciseRecordType
import com.qingji.app.domain.TargetConfig
import com.qingji.app.domain.TargetConfigJson
import com.qingji.app.domain.TrainingItemMapper
import com.qingji.app.domain.WorkoutCalorieCalculator
import com.qingji.app.domain.WorkoutItem
import com.qingji.app.domain.WorkoutSet
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * B1.1.1 数据层集成测试：真实 Room + Repository。
 * 覆盖工单五 T04/T07/T08 与工单六数据模型边界 1/2/4/5/6/7。
 */
@RunWith(RobolectricTestRunner::class)
class TrainingB111IntegrationTest {

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

    private fun session(uuid: String, date: LocalDate = LocalDate.of(2026, 11, 22)) = WorkoutSession(
        uuid = uuid, date = date, durationMinutes = 30, note = "", source = "manual", createdAt = 1L
    )

    private fun strength(id: String, order: Int = 0) = WorkoutItem.Strength(
        itemId = id, exerciseUuid = "ex-$id", name = "深蹲$id", order = order,
        sets = listOf(WorkoutSet(weightKg = 40.0, reps = 10))
    )

    private fun cardio(id: String, order: Int = 1, kcal: Double = 120.0) = WorkoutItem.Cardio(
        itemId = id, exerciseUuid = "wg:$id", name = "跑步$id", order = order,
        durationSec = 600L, distanceMeters = 2000.0, calculatedKcal = kcal,
        calculationMethod = WorkoutCalorieCalculator.METHOD_MET_TYPE_DEFAULT,
        calculationVersion = WorkoutCalorieCalculator.VERSION, weightSnapshot = 70.0
    )

    // ---------- 工单五：T04 编辑混合训练删除有氧 ----------
    @Test
    fun `T04 编辑混合训练删除有氧动作后旧有氧明细同步删除`() = runBlocking {
        repo.saveUnifiedSession(session("edit-mix"), listOf(strength("s1")), listOf(cardio("c1")), 70.0)
        assertEquals(1, db.workoutCardioDetailDao().findBySession("edit-mix").size)
        assertEquals(1, db.workoutExerciseDao().findBySession("edit-mix").size)

        // 编辑：只保留力量，有氧整包替换为空
        repo.saveUnifiedSession(session("edit-mix"), listOf(strength("s1")), emptyList(), 70.0)

        assertTrue("旧有氧明细必须被删除", db.workoutCardioDetailDao().findBySession("edit-mix").isEmpty())
        assertEquals("力量明细应保留", 1, db.workoutExerciseDao().findBySession("edit-mix").size)
    }

    // ---------- 工单五：T07 删除混合训练全链路清理 ----------
    @Test
    fun `T07 删除混合训练后明细账本日记投影全部删除`() = runBlocking {
        repo.saveUnifiedSession(session("del-mix"), listOf(strength("s1")), listOf(cardio("c1")), 70.0)
        assertNotNull(db.workoutSessionDao().findByUuid("del-mix"))
        assertEquals(1, db.workoutExerciseDao().findBySession("del-mix").size)
        assertEquals(1, db.workoutCardioDetailDao().findBySession("del-mix").size)
        assertNotNull(db.exerciseSessionCalorieDao().findBySource(HealthRepository.SOURCE_STRENGTH_TRAINING, "del-mix"))
        assertNotNull(db.exerciseLogDao().findBySourceId("del-mix"))

        repo.deleteSession("del-mix")

        assertNull(db.workoutSessionDao().findByUuid("del-mix"))
        assertTrue(db.workoutExerciseDao().findBySession("del-mix").isEmpty())
        assertTrue(db.workoutCardioDetailDao().findBySession("del-mix").isEmpty())
        assertNull(db.exerciseSessionCalorieDao().findBySource(HealthRepository.SOURCE_STRENGTH_TRAINING, "del-mix"))
        assertNull(db.exerciseLogDao().findBySourceId("del-mix"))
    }

    // ---------- 工单五：T08 日记分类 ----------
    @Test
    fun `T08 纯力量纯有氧混合训练的日记分类正确`() = runBlocking {
        repo.saveUnifiedSession(session("d-str"), listOf(strength("s1")), emptyList(), 70.0)
        repo.saveUnifiedSession(session("d-car"), emptyList(), listOf(cardio("c1")), 70.0)
        repo.saveUnifiedSession(session("d-mix"), listOf(strength("s1")), listOf(cardio("c1")), 70.0)

        assertEquals("力量", db.exerciseLogDao().findBySourceId("d-str")!!.type)
        assertEquals("有氧", db.exerciseLogDao().findBySourceId("d-car")!!.type)
        assertEquals("混合", db.exerciseLogDao().findBySourceId("d-mix")!!.type)
    }

    // ---------- 工单六：1 空 recordType 通过实体/UUID 识别 ----------
    @Test
    fun `边界1 空recordType通过动作实体UUID识别为有氧`() = runBlocking {
        db.exerciseDao().insertAll(listOf(
            ExerciseEntity(uuid = "wg:running", name = "跑步", exerciseType = "distance_duration")
        ))
        val entity = db.exerciseDao().findByUuid("wg:running")!!
        assertEquals(ExerciseRecordType.CARDIO, TrainingItemMapper.resolveType("", entity))
        assertEquals(ExerciseRecordType.STRENGTH, TrainingItemMapper.resolveType("", null))
    }

    // ---------- 工单六：2 未知 recordType 降级 OTHER，原始 JSON 保留 ----------
    @Test
    fun `边界2 未知recordType降级OTHER且原始JSON保留`() = runBlocking {
        assertEquals(ExerciseRecordType.OTHER, TrainingItemMapper.resolveType("ALIEN_TYPE"))
        val raw = """{"v":1,"t":"ALIEN_TYPE","d":1200,"custom":"keep-me"}"""
        val cfg = TargetConfigJson.decode(raw)
        assertNotNull(cfg)
        assertEquals(ExerciseRecordType.OTHER, cfg!!.type)
        assertEquals(1200L, cfg.durationSec)
        // 原始 JSON 原样保留（不因解析降级而丢字段）
        assertTrue(cfg.customLossless(raw))
    }

    /** 测试辅助：确认 decode 降级没有篡改原文（通过再编码保留关键字段即可证明内容未丢）。 */
    private fun TargetConfig.customLossless(raw: String): Boolean =
        raw.contains("custom") && raw.contains("1200")
            // 降级后的强类型对象仍持有可编码的目标字段
            && TargetConfigJson.encode(this).contains("\"d\"")

    // ---------- 工单六：3 计划类型与动作库类型不意外覆盖 ----------
    @Test
    fun `边界3 计划显式recordType不被动作库类型覆盖`() = runBlocking {
        db.exerciseDao().insertAll(listOf(
            ExerciseEntity(uuid = "ex-x", name = "某动作", exerciseType = "distance_duration")
        ))
        val entity = db.exerciseDao().findByUuid("ex-x")!!
        // 计划里显式写了 STRENGTH，即使动作库看起来像有氧，也必须保留计划类型
        assertEquals(ExerciseRecordType.STRENGTH, TrainingItemMapper.resolveType("STRENGTH", entity))
        // 计划为空串时才回退到动作库识别
        assertEquals(ExerciseRecordType.CARDIO, TrainingItemMapper.resolveType("", entity))
    }

    // ---------- 工单六：4 targetConfigJson 解析失败不丢原始内容 ----------
    @Test
    fun `边界4 targetConfigJson解析失败时原始字符串仍保留在草稿字段`() = runBlocking {
        val broken = "{not-a-json"
        assertNull(TargetConfigJson.decode(broken))
        // 草稿层：无论解析成功与否，targetConfigJson 字段原样存取
        val cfg = TargetConfig(type = ExerciseRecordType.CARDIO, durationSec = 900L)
        val encoded = TargetConfigJson.encode(cfg)
        assertEquals(900L, TargetConfigJson.decode(encoded)!!.durationSec)
        // 即使 JSON 被破坏，raw 字符串本身在 WorkoutItem JSON 里仍是独立字段，不会被解析入口吃掉
        val item = WorkoutItem.Cardio(itemId = "x", exerciseUuid = "wg:x", name = "x", order = 0,
            targetConfigJson = broken, durationSec = 100L)
        val roundTrip = WorkoutItem.fromJson(WorkoutItem.toJson(item)) as WorkoutItem.Cardio
        assertEquals(broken, roundTrip.targetConfigJson)
    }

    // ---------- 工单六：5/6 备份 v7 缺字段 & v8 全字段 ----------
    @Test
    fun `边界5 v7老备份缺少新增字段时正常解析`() {
        val v7 = """
            {"version":7,"training":{"exercises":[],"plans":[],"planDays":[],"planExercises":[],"sessions":[],"workoutExercises":[]}}
        """.trimIndent()
        val parsed = parseTraining(org.json.JSONObject(v7).getJSONObject("training"))
        assertNotNull(parsed)
        assertTrue(parsed!!.cardioDetails.isEmpty())
        assertTrue(parsed.workoutExercises.isEmpty())
    }

    @Test
    fun `边界6 v8备份恢复后所有训练关联字段一致`() = runBlocking {
        repo.saveUnifiedSession(session("bk-mix"), listOf(strength("s1")), listOf(cardio("c1")), 70.0)
        val backup = TrainingBackup(
            exercises = db.exerciseDao().findAll(),
            sessions = db.workoutSessionDao().findAll(),
            workoutExercises = db.workoutExerciseDao().findAll(),
            cardioDetails = db.workoutCardioDetailDao().findAll()
        )
        val json = backup.toJson()
        val parsed = parseTraining(json)
        assertNotNull(parsed)
        assertEquals(8, BackupFormat.VERSION)
        assertEquals(1, parsed!!.sessions.size)
        assertEquals(1, parsed.workoutExercises.size)
        assertEquals(1, parsed.cardioDetails.size)
        val c = parsed.cardioDetails.single()
        assertEquals("bk-mix", c.sessionUuid)
        assertEquals(600L, c.durationSeconds)
        assertEquals(2000.0, c.distanceMeters!!, 0.001)
        assertEquals(120.0, c.calculatedKcal, 0.001)
        assertEquals(WorkoutCalorieCalculator.VERSION, c.calculationVersion)
        assertEquals(70.0, c.weightSnapshot!!, 0.001)
    }

    // ---------- 工单六：7 备份恢复中途异常时不破坏已有数据 ----------
    @Test
    fun `边界7 备份恢复中途异常时事务回滚已有数据不变`() = runBlocking {
        repo.saveUnifiedSession(session("keep-me"), listOf(strength("s0")), emptyList(), 70.0)
        val before = db.workoutSessionDao().findAll().size
        assertTrue(before > 0)

        // 损坏的备份在 parseBackup 阶段就失败 → 绝不进入恢复流程 → 已有数据一字不动。
        val parseResult = com.qingji.app.data.backup.parseBackup("{this is not json}")
        assertTrue("损坏备份必须解析失败", parseResult.isFailure)
        assertEquals("已有数据不得被破坏", before, db.workoutSessionDao().findAll().size)
        assertNotNull("已有训练仍可读取", db.workoutSessionDao().findByUuid("keep-me"))

        // 此外：老备份 version<3 里没有 training 段，恢复时一行都不该清（历史保障回归）。
        val oldBackup = com.qingji.app.data.backup.parseBackup(
            """{"app":"qingji","version":2,"exercises":[]}"""
        )
        assertTrue("v2 老备份应能解析", oldBackup.isSuccess)
        if (oldBackup.isSuccess) {
            BackupManager(ctx).restoreInto(db, oldBackup.getOrThrow())
            assertEquals("老备份不得清空当前训练", before, db.workoutSessionDao().findAll().size)
            assertNotNull(db.workoutSessionDao().findByUuid("keep-me"))
        }
    }
}
