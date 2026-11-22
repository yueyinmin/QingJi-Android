package com.qingji.app.training

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.prefs.DraftExercise
import com.qingji.app.data.prefs.WorkoutDraft
import com.qingji.app.data.prefs.WorkoutDraftStore
import com.qingji.app.data.repository.HealthRepository
import com.qingji.app.data.repository.TrainingRepository
import com.qingji.app.domain.ExerciseRecordType
import com.qingji.app.domain.TargetConfig
import com.qingji.app.domain.TargetConfigJson
import com.qingji.app.domain.WorkoutItem
import com.qingji.app.domain.WorkoutSet
import com.qingji.app.ui.viewmodel.TrainingViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
 * B1.1.1 ViewModel 层测试：并发互斥 / 草稿快照 / 异常保留 / 真实磁盘进程恢复。
 * 覆盖工单三 P0、工单四 P0、工单五 T11/T12/T14 与进程恢复。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TrainingViewModelB111Test {

    private lateinit var db: AppDatabase
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun repoWith(saveHook: suspend () -> Unit = {}) =
        TrainingRepository(ctx, refreshHook = { _, _ -> }, dbOverride = db, saveHook = saveHook)

    private fun vm(repo: TrainingRepository) = TrainingViewModel(app, repo)

    private fun draft(uuid: String, revision: Long = 1L) = WorkoutDraft(
        sessionUuid = uuid, startedAt = 1L, revision = revision,
        exercises = listOf(
            DraftExercise(
                key = "d1", exerciseUuid = "ex-squat", name = "深蹲",
                sets = listOf(WorkoutSet(weightKg = 40.0, reps = 10))
            )
        )
    )

    // ---------- 工单三 / T12：连续点击只产生一次有效提交 ----------
    @Test
    fun `T12 连续触发保存只有一次有效提交不产生重复会话账本`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var entered = 0
        val repo = repoWith(saveHook = { entered++; gate.await() })
        repo.draftStore.save(draft("vm111-t12"))
        val vm = vm(repo)

        val first = CompletableDeferred<Boolean>()
        val second = CompletableDeferred<Boolean>()
        vm.save { first.complete(it) }   // 第一次抢到锁，卡在 gate
        vm.save { second.complete(it) }   // 第二次应立即被拒

        assertFalse("第二次保存必须被互斥拒绝", withTimeout(2_000) { second.await() })
        assertTrue("保存中标志应为 true", vm.isSaving.value)
        gate.complete(Unit)
        assertTrue("第一次保存应成功", withTimeout(5_000) { first.await() })
        assertFalse("保存结束后标志应释放", vm.isSaving.value)

        assertEquals(1, entered)
        assertEquals(1, db.workoutSessionDao().findAll().count { it.uuid == "vm111-t12" })
        assertEquals(1, db.workoutExerciseDao().findBySession("vm111-t12").size)
        assertEquals(
            1, db.exerciseSessionCalorieDao()
                .findSourceIds(HealthRepository.SOURCE_STRENGTH_TRAINING)
                .count { it == "vm111-t12" }
        )
    }

    // ---------- 工单四 / T14：保存期间修改草稿不清新修改 ----------
    @Test
    fun `T14 保存期间修改草稿新修改不被清理`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val repo = repoWith(saveHook = { gate.await() })
        repo.draftStore.save(draft("vm111-t14", revision = 5L))
        val vm = vm(repo)

        val done = CompletableDeferred<Boolean>()
        vm.save { done.complete(it) }   // 卡在 gate

        // 保存期间用户又改了一组 → 通过公开 updateSet 触发 persist（版本递增）
        val before = vm.draft.value!!
        vm.updateSet(before.exercises[0].key, 0, WorkoutSet(weightKg = 50.0, reps = 8))
        val newRevision = vm.draft.value!!.revision
        assertTrue("revision 必须递增", newRevision > before.revision)

        gate.complete(Unit)
        assertTrue(withTimeout(5_000) { done.await() })

        val survived = repo.draftStore.load()
        assertNotNull("保存期间的新修改草稿必须保留", survived)
        assertEquals("应保留新 revision", newRevision, survived!!.revision)
        assertEquals(50.0, survived.exercises[0].sets[0].weightKg, 0.001)
        // 旧的那次保存仍然成功落库
        assertEquals(1, db.workoutExerciseDao().findBySession("vm111-t14").size)
    }

    // ---------- 工单四 / T11：数据库异常保留草稿且可重试 ----------
    @Test
    fun `T11 数据库保存异常时草稿完整保留并可重试`() = runBlocking {
        var failOnce = true
        val repo = repoWith(saveHook = {
            if (failOnce) { failOnce = false; throw IllegalStateException("模拟数据库异常") }
        })
        val original = draft("vm111-t11", revision = 7L)
        repo.draftStore.save(original)
        val vm = vm(repo)

        val failed = CompletableDeferred<Boolean>()
        vm.save { failed.complete(it) }
        assertFalse("第一次保存应失败", withTimeout(5_000) { failed.await() })

        val kept = repo.draftStore.load()
        assertNotNull("失败后草稿必须保留", kept)
        assertEquals(7L, kept!!.revision)
        assertEquals("ex-squat", kept.exercises[0].exerciseUuid)
        assertEquals(40.0, kept.exercises[0].sets[0].weightKg, 0.001)
        assertTrue("保存标志必须释放", !vm.isSaving.value)

        // 重试：钩子不再抛错，草稿原样保存成功
        val retried = CompletableDeferred<Boolean>()
        vm.save { retried.complete(it) }
        assertTrue("重试应成功", withTimeout(5_000) { retried.await() })
        assertEquals(1, db.workoutSessionDao().findAll().count { it.uuid == "vm111-t11" })
        org.junit.Assert.assertNull("重试成功后草稿应被清理", repo.draftStore.load())
    }

    // ---------- 工单五：真实磁盘进程恢复（新建 Store 读同一 SharedPreferences 文件）----------
    @Test
    fun `进程恢复 真实磁盘SharedPreferences恢复混合草稿全字段`() = runBlocking {
        val store = WorkoutDraftStore(ctx)
        val target = TargetConfigJson.encode(
            TargetConfig(type = ExerciseRecordType.CARDIO, durationSec = 1800L, distanceMeters = 5000.0)
        )
        val actual = WorkoutItem.toJson(
            WorkoutItem.Cardio(itemId = "d1", exerciseUuid = "wg:running", name = "跑步", order = 0,
                durationSec = 1200L, distanceMeters = 4000.0)
        )
        val saved = WorkoutDraft(
            sessionUuid = "vm111-proc", startedAt = 1000L, revision = 11L,
            sourcePlan = "晨跑计划",
            exercises = listOf(
                DraftExercise(
                    key = "d1", exerciseUuid = "ex-squat", name = "深蹲",
                    sets = listOf(WorkoutSet(weightKg = 42.5, reps = 12))
                ),
                DraftExercise(
                    key = "d2", exerciseUuid = "wg:running", name = "跑步",
                    recordType = ExerciseRecordType.CARDIO.key,
                    targetConfigJson = target, actualJson = actual
                )
            )
        )
        store.save(saved)

        // 模拟进程重建：丢弃原 Store/ViewModel，用全新 Store 读同一份磁盘持久化数据
        val afterRestart = WorkoutDraftStore(ctx).load()
        assertNotNull("进程重建后草稿必须恢复", afterRestart)
        assertEquals("vm111-proc", afterRestart!!.sessionUuid)
        assertEquals(11L, afterRestart.revision)
        assertEquals("晨跑计划", afterRestart.sourcePlan)
        assertEquals(2, afterRestart.exercises.size)
        assertEquals(42.5, afterRestart.exercises[0].sets[0].weightKg, 0.001)
        val cardio = afterRestart.exercises[1]
        assertEquals(ExerciseRecordType.CARDIO.key, cardio.recordType)
        assertEquals(1800L, TargetConfigJson.decode(cardio.targetConfigJson)!!.durationSec)
        val restoredActual = WorkoutItem.fromJson(cardio.actualJson) as WorkoutItem.Cardio
        assertEquals(1200L, restoredActual.durationSec)
        assertEquals(4000.0, restoredActual.distanceMeters!!, 0.001)
    }
}
