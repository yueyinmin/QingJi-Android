package com.qingji.app.data.backup

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.local.entity.WorkoutSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 训练数据进备份后的两条底线：
 * 1. 自家 v3 备份 → 训练记录整包覆盖，但动作库只合并自建与收藏，不清 302 条内置；
 * 2. v2 老备份（那时还没有训练模块）→ 本机训练数据一行都不能被清掉。
 */
@RunWith(RobolectricTestRunner::class)
class TrainingBackupRestoreTest {

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

    private fun session(uuid: String, date: String) = WorkoutSession(
        uuid = uuid,
        date = LocalDate.parse(date),
        startedAt = 100L,
        endedAt = 200L,
        durationMinutes = 10,
        note = "",
        createdAt = 200L
    )

    private fun manager() = BackupManager(ApplicationProvider.getApplicationContext())

    @Test
    fun `v3 备份按 training 段整包覆盖`() = runBlocking {
        db.workoutSessionDao().insert(session("old", "2026-11-01"))

        val json = """
            {
              "app":"qingji","version":3,
              "training":{
                "sessions":[
                  {"uuid":"new","date":"2026-11-13","startedAt":1,"endedAt":2,"durationMinutes":20,"createdAt":2}
                ],
                "workoutExercises":[
                  {"id":1,"sessionUuid":"new","exerciseUuid":"ex-chest-01","exerciseNameSnapshot":"哑铃卧推","setsJson":"[{\"w\":10,\"r\":12}]"}
                ],
                "exercises":[],"plans":[],"planDays":[],"planExercises":[]
              }
            }
        """.trimIndent()
        val parsed = parseBackup(json).getOrThrow()

        // 和正式导入保持一致：清表 + 灌入必须在同一个事务里
        db.withTransaction { manager().restoreInto(db, parsed) }

        val rows = db.workoutSessionDao().findAll()
        assertEquals(1, rows.size)
        assertEquals("new", rows.first().uuid)
        assertEquals(1, db.workoutExerciseDao().findAll().size)
    }

    @Test
    fun `v2 老备份不碰本机训练数据`() = runBlocking {
        db.workoutSessionDao().insert(session("keep-me", "2026-11-13"))
        val parsed = parseBackup("""{"app":"qingji","version":2,"weights":[]}""").getOrThrow()

        // 和正式导入保持一致：清表 + 灌入必须在同一个事务里
        db.withTransaction { manager().restoreInto(db, parsed) }

        assertEquals(1, db.workoutSessionDao().findAll().size)
        assertEquals("keep-me", db.workoutSessionDao().findAll().first().uuid)
    }
    @Test
    fun `恢复旧备份不清空新动作库 只合并自建与收藏`() = runBlocking {
        db.exerciseDao().insertAll(listOf(
            ExerciseEntity("ex-chest-01", "哑铃卧推", "chest", isBuiltIn = true),
            ExerciseEntity("ex-chest-02", "杠铃卧推", "chest", isBuiltIn = true),
            ExerciseEntity("wg:plank", "平板支撑", "core", isBuiltIn = true)
        ))
        val json = """
            {
              "app":"qingji","version":3,
              "training":{
                "exercises":[
                  {"uuid":"ex-chest-01","name":"哑铃卧推","category":"chest","isBuiltIn":true,"isFavorite":true},
                  {"uuid":"user-old","name":"我的老动作","category":"core","isBuiltIn":false,"isFavorite":true,"primaryMuscles":"我自己"}
                ],
                "sessions":[],"plans":[],"planDays":[],"planExercises":[],"workoutExercises":[]
              }
            }
        """.trimIndent()
        val parsed = parseBackup(json).getOrThrow()
        db.withTransaction { manager().restoreInto(db, parsed) }

        val all = db.exerciseDao().findAll()
        assertEquals(4, all.size)
        assertTrue("新动作不能被旧备份清掉", all.any { it.uuid == "wg:plank" })
        assertTrue("旧备份里的收藏要带回来", db.exerciseDao().findByUuid("ex-chest-01")!!.isFavorite)
        val custom = db.exerciseDao().findByUuid("user-old")!!
        assertFalse(custom.isBuiltIn)
        assertEquals("我自己", custom.primaryMuscles)
    }

}
