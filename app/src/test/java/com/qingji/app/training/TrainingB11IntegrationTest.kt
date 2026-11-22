package com.qingji.app.training

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.backup.BackupManager
import com.qingji.app.data.backup.TrainingBackup
import com.qingji.app.data.backup.parseTraining
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.local.entity.WorkoutSession
import com.qingji.app.data.prefs.DraftExercise
import com.qingji.app.data.repository.TrainingRepository
import com.qingji.app.domain.ExerciseRecordType
import com.qingji.app.domain.TargetConfig
import com.qingji.app.domain.TargetConfigJson
import com.qingji.app.domain.TrainingItemMapper
import com.qingji.app.domain.WorkoutItem
import com.qingji.app.domain.WorkoutCalorieCalculator
import com.qingji.app.domain.WorkoutSet
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * B1.1 集成测试：使用真实 Room（内存库）+ Repository，验证数据真正写入/读回，
 * 而不是只测 enum / mapper / JSON codec。
 */
@RunWith(RobolectricTestRunner::class)
class TrainingB11IntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TrainingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = TrainingRepository(context, refreshHook = { _, _ -> }, dbOverride = db)
    }

    @After
    fun tearDown() { db.close() }

    private fun session(uuid: String) = WorkoutSession(
        uuid = uuid, date = LocalDate.of(2026, 11, 22), durationMinutes = 30,
        note = "", source = "manual", createdAt = 1L
    )

    private fun strength(id: String, name: String, reps: Int = 10) = WorkoutItem.Strength(
        itemId = id, exerciseUuid = "ex-$id", name = name, order = 0,
        sets = listOf(WorkoutSet(weightKg = 40.0, reps = reps, targetReps = 12, targetWeightKg = 45.0))
    )

    private fun cardio(id: String, durationSec: Long = 600, kcal: Double = 0.0) = WorkoutItem.Cardio(
        itemId = id, exerciseUuid = "wg:$id", name = id, order = 1,
        durationSec = durationSec, distanceMeters = 2000.0,
        calculatedKcal = kcal,
        calculationMethod = if (kcal > 0) WorkoutCalorieCalculator.METHOD_MET_TYPE_DEFAULT else "",
        calculationVersion = if (kcal > 0) WorkoutCalorieCalculator.VERSION else "",
        weightSnapshot = if (kcal > 0) 70.0 else null
    )

    // T01：力量计划动作不会被分流到有氧表
    @Test
    fun `T01 力量项目写入workout_exercise且不进有氧表`() = runBlocking {
        repo.saveUnifiedSession(session("s1"), listOf(strength("p1", "杠铃卧推")), emptyList(), 70.0)
        assertEquals(1, db.workoutExerciseDao().findBySession("s1").size)
        assertTrue(db.workoutCardioDetailDao().findBySession("s1").isEmpty())
        val sets = com.qingji.app.domain.TrainingMath.decodeSets(
            db.workoutExerciseDao().findBySession("s1").first().setsJson
        )
        assertEquals(40.0, sets.first().weightKg, 0.001)
        assertEquals(10, sets.first().reps)
        assertEquals(12, sets.first().targetReps)
    }

    // T02：目标不会伪装成实际；只有用户录入的时长/距离落库
    @Test
    fun `T02 目标时长不等于实际时长且不会生成假成绩`() = runBlocking {
        val draft = DraftExercise(
            key = "d1", exerciseUuid = "wg:running", name = "跑步",
            recordType = ExerciseRecordType.CARDIO.key,
            targetConfigJson = TargetConfigJson.encode(
                TargetConfig(type = ExerciseRecordType.CARDIO, durationSec = 3600L, distanceMeters = 10000.0)
            ),
            actualJson = WorkoutItem.toJson(WorkoutItem.Cardio(
                itemId = "d1", exerciseUuid = "wg:running", name = "跑步", order = 0,
                durationSec = 600L, distanceMeters = 2000.0
            ))
        )
        val item = TrainingItemMapper.draftToItem(
            draft, ExerciseRecordType.CARDIO, "s2", 0
        ) as WorkoutItem.Cardio
        assertEquals(600L, item.durationSec)
        assertEquals(2000.0, item.distanceMeters!!, 0.001)
        repo.saveUnifiedSession(session("s2"), emptyList(), listOf(item), 70.0)
        val row = db.workoutCardioDetailDao().findBySession("s2").single()
        assertEquals(600L, row.durationSeconds)
        assertEquals(2000.0, row.distanceMeters!!, 0.001)
        assertEquals(0.0, row.calculatedKcal, 0.001) // 无可靠 MET 时不造热量
        val persistedItem = WorkoutItem.fromJson(row.rawInputJson) as WorkoutItem.Cardio
        assertEquals(3600L, TargetConfigJson.decode(persistedItem.targetConfigJson)?.durationSec)
        assertEquals(10000.0, TargetConfigJson.decode(persistedItem.targetConfigJson)?.distanceMeters!!, 0.001)
    }

    // T03：混合训练两类明细均完整保存
    @Test
    fun `T03 混合训练力量与有氧同时落库`() = runBlocking {
        repo.saveUnifiedSession(
            session("s3"), listOf(strength("p1", "深蹲")), listOf(cardio("running", 1200)), 70.0
        )
        assertEquals(1, db.workoutExerciseDao().findBySession("s3").size)
        assertEquals(1, db.workoutCardioDetailDao().findBySession("s3").size)
        val detail = repo.sessionDetail("s3")
        assertEquals(2, detail.items.size)
        assertTrue(detail.items.any { it is WorkoutItem.Strength })
        assertTrue(detail.items.any { it is WorkoutItem.Cardio })
    }

    // T04/T05：类型强解析，空串按动作库语义解析，未知 key 不降级为有氧
    @Test
    fun `T04 空recordType按动作映射解析有氧未知key降级OTHER`() {
        val cardioEntity = ExerciseEntity(
            uuid = "wg:running", name = "跑步", exerciseType = "distance_duration"
        )
        assertEquals(ExerciseRecordType.CARDIO, TrainingItemMapper.resolveType("", cardioEntity))
        assertEquals(ExerciseRecordType.STRENGTH, TrainingItemMapper.resolveType("", null))
        assertEquals(ExerciseRecordType.STRENGTH, TrainingItemMapper.resolveType("STRENGTH"))
        assertEquals(ExerciseRecordType.OTHER, TrainingItemMapper.resolveType("NOT_A_TYPE"))
        assertEquals(ExerciseRecordType.HIIT, TrainingItemMapper.resolveType("HIIT"))
    }

    // T06：HIIT 的 work/rest/rounds 不丢失，且不被当作有氧
    @Test
    fun `T06 HIIT字段完整保留在itemMetaJson并能读回`() = runBlocking {
        val hiit = WorkoutItem.Hiit(
            itemId = "h1", exerciseUuid = "seed-hiit", name = "间歇跑", order = 0,
            durationSec = 1200, workSec = 30, restSec = 30, rounds = 8
        )
        repo.saveUnifiedSession(session("s6"), listOf(hiit), emptyList(), 70.0)
        assertTrue(db.workoutCardioDetailDao().findBySession("s6").isEmpty())
        val row = db.workoutExerciseDao().findBySession("s6").single()
        assertTrue(row.setsJson.isEmpty())
        val back = TrainingItemMapper.workoutExerciseToItem(row) as WorkoutItem.Hiit
        assertEquals(30, back.workSec)
        assertEquals(30, back.restSec)
        assertEquals(8, back.rounds)
        assertEquals(ExerciseRecordType.HIIT, back.recordType)
    }

    // T08：编辑混合训练，只改力量，有氧明细不被清空
    @Test
    fun `T08 编辑混合训练保留未修改的有氧明细`() = runBlocking {
        repo.saveUnifiedSession(
            session("s8"), listOf(strength("p1", "卧推")), listOf(cardio("running", 900)), 70.0
        )
        val before = repo.sessionDetail("s8")
        val rebuilt = before.items.map { item ->
            when (item) {
                is WorkoutItem.Strength -> item.copy(sets = listOf(WorkoutSet(50.0, 8)))
                else -> item
            }
        }
        repo.saveUnifiedSession(
            session("s8"),
            rebuilt.filter { it !is WorkoutItem.Cardio },
            rebuilt.filterIsInstance<WorkoutItem.Cardio>(),
            70.0
        )
        val after = repo.sessionDetail("s8")
        assertEquals(2, after.items.size)
        assertEquals(900L, (after.items.first { it is WorkoutItem.Cardio } as WorkoutItem.Cardio).durationSec)
        assertEquals(8, (after.items.first { it is WorkoutItem.Strength } as WorkoutItem.Strength).sets.first().reps)
    }

    // T11：日记类型按会话构成计算
    @Test
    fun `T11 纯力量纯有氧混合训练分类正确`() {
        assertEquals("力量", TrainingItemMapper.diaryType(listOf(strength("a", "卧推"))))
        assertEquals("有氧", TrainingItemMapper.diaryType(listOf(cardio("running"))))
        assertEquals("混合", TrainingItemMapper.diaryType(listOf(strength("a", "卧推"), cardio("running"))))
        assertEquals("其他", TrainingItemMapper.diaryType(listOf(
            WorkoutItem.Other("o", "custom", "自定义", 0, durationSec = 60)
        )))
    }

    // T12：历史混合训练复制为计划，类型与目标配置完整保留
    @Test
    fun `T12 混合训练复制为计划保留类型与顺序`() = runBlocking {
        db.exerciseDao().insertAll(listOf(
            ExerciseEntity(uuid = "ex-p1", name = "卧推"),
            ExerciseEntity(uuid = "wg:running", name = "跑步")
        ))
        repo.saveUnifiedSession(
            session("s12"), listOf(strength("p1", "卧推")), listOf(cardio("running", 900)), 70.0
        )
        val (planUuid, missing) = repo.saveSessionAsTemplate("s12", "复制计划")
        assertTrue(missing.isEmpty())
        val plans = db.planExerciseDao().findAll().filter { planUuid.isNotBlank() }
        assertEquals(2, plans.size)
        assertTrue(plans.any { it.recordType == ExerciseRecordType.CARDIO.key })
        assertTrue(plans.any { it.targetConfigJson.contains("CARDIO") })
        assertEquals(listOf(0, 1), plans.sortedBy { it.orderInDay }.map { it.orderInDay })
    }

    // T13：备份往返包含有氧明细与新增字段
    @Test
    fun `T13 备份导出解析包含有氧明细与新字段`() = runBlocking {
        repo.saveUnifiedSession(
            session("s13"), listOf(strength("p1", "卧推")), listOf(cardio("running", 900)), 70.0
        )
        val backup = TrainingBackup(
            exercises = db.exerciseDao().findAll(),
            plans = db.trainingPlanDao().findAll(),
            planDays = db.trainingPlanDayDao().findAll(),
            planExercises = db.planExerciseDao().findAll(),
            sessions = db.workoutSessionDao().findAll(),
            workoutExercises = db.workoutExerciseDao().findAll(),
            cardioDetails = db.workoutCardioDetailDao().findAll()
        )
        val parsed = parseTraining(backup.toJson())!!
        assertEquals(1, parsed.cardioDetails.size)
        assertEquals(900L, parsed.cardioDetails.first().durationSeconds)
        assertTrue(parsed.workoutExercises.first().itemMetaJson.contains("STRENGTH"))

        // 恢复：清空后灌回，事务由调用方负责（与 BackupManager 生产路径一致）
        db.withTransaction {
            db.workoutSessionDao().clearAll()
            db.workoutExerciseDao().clearAll()
            db.workoutCardioDetailDao().clearAll()
            parsed.sessions.forEach { db.workoutSessionDao().insert(it) }
            db.workoutExerciseDao().insertAll(parsed.workoutExercises)
            db.workoutCardioDetailDao().insertAll(parsed.cardioDetails)
        }
        assertEquals(1, db.workoutExerciseDao().findAll().size)
        assertEquals(1, db.workoutCardioDetailDao().findAll().size)
    }

    // T15：旧备份缺少新字段时安全恢复
    @Test
    fun `T15 旧备份缺少新字段时用安全默认值`() {
        val old = """{"exercises":[],"plans":[],"planDays":[],"planExercises":[],
            "sessions":[{"uuid":"old","date":"2026-11-01"}],
            "workoutExercises":[{"sessionUuid":"old","exerciseNameSnapshot":"卧推","setsJson":"[]"}]}"""
        val parsed = parseTraining(org.json.JSONObject(old))!!
        assertEquals("", parsed.workoutExercises.first().itemMetaJson)
        assertTrue(parsed.cardioDetails.isEmpty())
        assertNotNull(parsed.sessions.first())
        assertFalse(parsed.isEmpty())
    }

    // T16：有可靠类型 MET 时算热量；无 MET 时保留原始数据但不造数
    @Test
    fun `T16 可靠MET才计算热量且重新读回一致`() = runBlocking {
        val entity = ExerciseEntity(uuid = "wg:running", name = "跑步", slug = "running")
        val mets = mapOf("running" to Triple(7.0, 8.3, 9.5))
        val computable = TrainingItemMapper.withCardioKcal(cardio("running", 3600), entity, mets, 70.0)
        assertEquals(581.0, computable.calculatedKcal, 1.0)
        assertEquals(WorkoutCalorieCalculator.METHOD_MET_TYPE_DEFAULT, computable.calculationMethod)
        assertEquals(WorkoutCalorieCalculator.VERSION, computable.calculationVersion)

        val unknown = TrainingItemMapper.withCardioKcal(cardio("unknown", 3600), null, mets, 70.0)
        assertEquals(0.0, unknown.calculatedKcal, 0.001)
        assertEquals("", unknown.calculationVersion)

        repo.saveUnifiedSession(session("s16"), emptyList(), listOf(computable), 70.0)
        val row = db.workoutCardioDetailDao().findBySession("s16").single()
        assertEquals(581.0, row.calculatedKcal, 1.0)
    }
}
