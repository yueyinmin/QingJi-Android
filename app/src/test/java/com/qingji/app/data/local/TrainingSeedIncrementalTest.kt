package com.qingji.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.local.entity.LibraryMeta
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

/**
 * Workout Guide 302 条动作库导入底线：
 *  1. 种子完整：324 条（302 + 22 有氧扩充），UUID / 名称唯一，38 个老 UUID 保留，七分类计数吻合；
 *  2. 升级只增不覆盖：收藏、自建、训练历史、计划不碰；名字撞车时内置动作改用变式名。
 *
 * 数据来源优先级（明确规则）：
 *   · 用户字段：uuid / isBuiltIn / isFavorite / createdAt / updatedAt / steps / commonMistakes
 *   · 动作库字段：name / englishName / slug / category / 肌群 / 器械 / 类型 / 素材 / 授权
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
        val seeds = TrainingSeed.builtIns()
        val existing = dao.findAll()
        val installed = db.libraryMetaDao().get(TrainingSeed.LIBRARY_VERSION_KEY)
        if (!TrainingSeedImportPlanner.importNeeded(installed, existing.size, seeds.size)) return@runBlocking
        val plan = TrainingSeedImportPlanner.plan(existing, seeds)
        if (plan.toInsert.isNotEmpty()) dao.insertMissing(plan.toInsert)
        plan.toUpdate.forEach { dao.update(it) }
        db.libraryMetaDao().put(LibraryMeta(TrainingSeed.LIBRARY_VERSION_KEY, TrainingSeed.LIBRARY_VERSION))
    }

    @Test
    fun `种子 324 条 UUID 与中文名都唯一`() {
        val all = TrainingSeed.builtIns(1000L)
        assertEquals(324, all.size)
        assertEquals(324, all.map { it.uuid }.toSet().size)
        assertEquals(324, all.map { it.name }.toSet().size)
        assertTrue(all.all { it.englishName.isNotBlank() })
        assertTrue(all.all { it.slug.isNotBlank() })
        assertTrue(all.all { it.assetPath.startsWith("exercises/") })
        assertEquals(302, all.count { it.attribution.contains("CC BY-SA 4.0") })
        assertEquals(22, all.count { it.attribution.contains("AI-generated") })
        assertEquals(22, all.count { it.assetPath.endsWith("frame-1.png") })
        assertEquals("302 条上游无步骤留空，22 条有氧扩充自带步骤",
            22, all.count { it.steps.isNotBlank() })
    }

    @Test
    fun `38 个老 UUID 保留 286 个新 UUID 稳定 七分类齐全`() {
        val all = TrainingSeed.builtIns(1000L)
        assertEquals(38, all.count { it.uuid.startsWith("ex-") })
        assertEquals(286, all.count { it.uuid.startsWith("wg:") })
        val counts = all.groupingBy { it.category }.eachCount()
        assertEquals(26, counts["chest"])
        assertEquals(46, counts["back"])
        assertEquals(26, counts["shoulders"])
        assertEquals(37, counts["arms"])
        assertEquals(130, counts["legs"])
        assertEquals(55, counts["core"])
        assertEquals(4, counts["mobility"])
    }

    @Test
    fun `空库首次灌入 324 条`() = runBlocking {
        assertEquals(0, db.exerciseDao().count())
        ensureSeedLikeRepo()
        assertEquals(324, db.exerciseDao().count())
    }

    @Test
    fun `老用户升级只补缺失项且收藏自建内容原样不动`() = runBlocking {
        val dao = db.exerciseDao()
        val now = 500L
        dao.insertAll(listOf(
            ExerciseEntity("ex-chest-01", "哑铃卧推", TrainingSeed.CHEST,
                isBuiltIn = true, isFavorite = true, steps = "我写过的要点",
                createdAt = now, updatedAt = now),
            ExerciseEntity("ex-chest-02", "杠铃卧推", TrainingSeed.CHEST,
                isBuiltIn = true, createdAt = now, updatedAt = now),
            ExerciseEntity("ex-back-01", "引体向上", TrainingSeed.BACK,
                isBuiltIn = true, createdAt = now, updatedAt = now)
        ))
        dao.insertAll(listOf(
            ExerciseEntity("user-abc", "我的独门动作", TrainingSeed.CORE,
                isBuiltIn = false, isFavorite = true, createdAt = now, updatedAt = now)
        ))

        ensureSeedLikeRepo()

        val fav = dao.findByUuid("ex-chest-01")!!
        assertTrue("原收藏状态必须保留", fav.isFavorite)
        assertEquals("用户写过的要点不能被覆盖", "我写过的要点", fav.steps)
        val custom = dao.findByUuid("user-abc")!!
        assertFalse("自建动作必须还在", custom.isBuiltIn)
        assertTrue("自建动作的收藏必须保留", custom.isFavorite)
        assertEquals(325, dao.count())
        assertNotNull(dao.findByUuid("wg:incline-bench-press"))
    }

    @Test
    fun `重复调用幂等 版本一致时零写入`() = runBlocking {
        ensureSeedLikeRepo()
        val before = db.exerciseDao().findAll()
        ensureSeedLikeRepo()
        assertEquals(324, db.exerciseDao().count())
        assertEquals(before.map { it.uuid }.toSet(), db.exerciseDao().findAll().map { it.uuid }.toSet())
    }

    @Test
    fun `自建动作与内置同名 内置改用变式名 自建保持原样`() = runBlocking {
        val dao = db.exerciseDao()
        dao.insertAll(listOf(
            ExerciseEntity("user-dup", "上斜杠铃卧推", TrainingSeed.CHEST,
                primaryMuscles = "我的定义", isBuiltIn = false, createdAt = 1L, updatedAt = 1L)
        ))
        ensureSeedLikeRepo()
        val custom = dao.findByUuid("user-dup")!!
        assertEquals("我的定义", custom.primaryMuscles)
        assertFalse(custom.isBuiltIn)
        assertEquals(325, dao.count())
        val builtIn = db.exerciseDao().findAll().first { it.slug == "incline-bench-press" }
        assertTrue("内置动作应换成变式名", builtIn.name != "上斜杠铃卧推")
        assertTrue(builtIn.isBuiltIn)
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
