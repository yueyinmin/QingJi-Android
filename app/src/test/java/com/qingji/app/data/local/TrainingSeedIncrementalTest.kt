package com.qingji.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.entity.ExerciseEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 动作库 200 条接入的两条底线（增量灌入工单）：
 *  1. 种子本身完整正确：200 条、UUID/名称唯一、38 个老 UUID 原样、分类计数吻合；
 *  2. 老用户升级补齐时，只增不覆盖：新动作进得来，收藏/自建/训练历史一行不丢。
 *
 * 用 Robolectric 内存库直接跑 DAO，差集逻辑与 TrainingRepository.ensureSeed() 一致。
 */
@RunWith(RobolectricTestRunner::class)
class TrainingSeedIncrementalTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() { db.close() }

    private fun ensureSeedLikeRepo() = runBlocking {
        val dao = db.exerciseDao()
        val all = TrainingSeed.builtIns()
        val existing = dao.findAll().map { it.uuid }.toSet()
        val missing = all.filterNot { it.uuid in existing }
        if (missing.isNotEmpty()) dao.insertMissing(missing)
    }

    @Test
    fun `种子 200 条 UUID 与名称都唯一`() {
        val all = TrainingSeed.builtIns(1000L)
        assertEquals(200, all.size)
        assertEquals(200, all.map { it.uuid }.toSet().size)
        assertEquals(200, all.map { it.name }.toSet().size)
    }

    @Test
    fun `38 个老动作 UUID 全部保留且分类计数吻合`() {
        val all = TrainingSeed.builtIns(1000L)
        val old = all.filter { it.uuid.startsWith("ex-") }
        assertEquals(38, old.size)
        val new = all.filter { it.uuid.startsWith("candidate-") }
        assertEquals(162, new.size)

        val counts = all.groupingBy { it.category }.eachCount()
        assertEquals(30, counts["chest"])
        assertEquals(35, counts["back"])
        assertEquals(25, counts["shoulder"])
        assertEquals(25, counts["arm"])
        assertEquals(40, counts["leg"])
        assertEquals(25, counts["core"])
        assertEquals(20, counts["stretch"])
    }

    @Test
    fun `空库首次灌入 200 条`() = runBlocking {
        assertEquals(0, db.exerciseDao().count())
        ensureSeedLikeRepo()
        assertEquals(200, db.exerciseDao().count())
    }

    @Test
    fun `老用户升级只补缺失项且收藏自建内容原样不动`() = runBlocking {
        val dao = db.exerciseDao()
        // 模拟老用户库：只有旧 38 条里的 3 条，其中一条被收藏、一条被改过要点
        val now = 500L
        dao.insertAll(
            listOf(
                ExerciseEntity("ex-chest-01", "哑铃卧推", TrainingSeed.CHEST,
                    isBuiltIn = true, isFavorite = true, createdAt = now, updatedAt = now),
                ExerciseEntity("ex-chest-02", "杠铃卧推", TrainingSeed.CHEST,
                    primaryMuscles = "我改过的肌群", isBuiltIn = true, createdAt = now, updatedAt = now),
                ExerciseEntity("ex-back-01", "引体向上", TrainingSeed.BACK,
                    isBuiltIn = true, createdAt = now, updatedAt = now)
            )
        )
        // 一个自建动作，绝不能被灌库影响
        dao.insertAll(
            listOf(
                ExerciseEntity("user-abc", "我的独门动作", TrainingSeed.CORE,
                    isBuiltIn = false, isFavorite = true, createdAt = now, updatedAt = now)
            )
        )

        ensureSeedLikeRepo()

        val fav = dao.findByUuid("ex-chest-01")!!
        assertTrue("原收藏状态必须保留", fav.isFavorite)
        val edited = dao.findByUuid("ex-chest-02")!!
        assertEquals("用户改过的肌群不能被种子覆盖", "我改过的肌群", edited.primaryMuscles)
        val custom = dao.findByUuid("user-abc")!!
        assertTrue("自建动作必须还在", custom.isBuiltIn.not())
        assertTrue("自建动作的收藏必须保留", custom.isFavorite)
        // 补齐到 200 + 1 个自建 = 201
        assertEquals(201, dao.count())
        // 新增候选确实进来了
        assertTrue(dao.findByUuid("candidate-chest-007") != null)
    }

    @Test
    fun `重复调用幂等 不会产生重复行也不报错`() = runBlocking {
        ensureSeedLikeRepo()
        ensureSeedLikeRepo()
        assertEquals(200, db.exerciseDao().count())
    }

    @Test
    fun `自建动作与新种子同名时 IGNORE 不覆盖自建 不崩`() = runBlocking {
        val dao = db.exerciseDao()
        // 用户自建了一个和新增候选同名的动作（name 有唯一索引）
        dao.insertAll(
            listOf(
                ExerciseEntity("user-dup", "上斜杠铃卧推", TrainingSeed.CHEST,
                    primaryMuscles = "我的定义", isBuiltIn = false, createdAt = 1L, updatedAt = 1L)
            )
        )
        ensureSeedLikeRepo() // 不应抛 SQLiteConstraintException
        val custom = dao.findByUuid("user-dup")!!
        assertEquals("我的定义", custom.primaryMuscles)
        assertTrue("自建动作还在", custom.isBuiltIn.not())
        // 同名的那条候选因 name 冲突被 IGNORE 跳过，其余照常补齐
        assertTrue(dao.count() >= 200)
    }

    @Test
    fun `两字分类标签齐全`() {
        val labels = TrainingSeed.CATEGORY_LABELS.toMap()
        assertEquals("胸部", labels[TrainingSeed.CHEST])
        assertEquals("背部", labels[TrainingSeed.BACK])
        assertEquals("肩部", labels[TrainingSeed.SHOULDER])
        assertEquals("手臂", labels[TrainingSeed.ARM])
        assertEquals("腿部", labels[TrainingSeed.LEG])
        assertEquals("核心", labels[TrainingSeed.CORE])
        assertEquals("拉伸", labels[TrainingSeed.STRETCH])
        assertFalse(labels.containsKey(""))
    }
}
