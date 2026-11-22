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
import kotlinx.coroutines.CancellationException
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * B1.1.2 ViewModel 层测试：T15/T16 两次保存语义、T17/T18 混合草稿保存期间修改、
 * T19 取消后锁释放、T20 落盘失败内存/磁盘一致。
 * 全部走真实 Room + Repository + ViewModel（saveHook 仅注入延迟/异常，不替换 DB 行为）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TrainingViewModelB112Test {

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

    /** 仅力量的草稿：一组 40kg。 */
    private fun strengthDraft(uuid: String, revision: Long = 1L, editing: Boolean = false) = WorkoutDraft(
        sessionUuid = uuid, startedAt = 1L, revision = revision,
        editingUuid = if (editing) uuid else "",
        originalDate = "2026-11-22", fixedDurationMinutes = 30,
        exercises = listOf(
            DraftExercise(
                key = "d1", exerciseUuid = "ex-squat", name = "深蹲",
                sets = listOf(WorkoutSet(weightKg = 40.0, reps = 10))
            )
        )
    )

    private suspend fun sessionCount(uuid: String) =
        db.workoutSessionDao().findAll().count { it.uuid == uuid }
    private suspend fun ledgerCount(uuid: String) =
        db.exerciseSessionCalorieDao()
            .findSourceIds(HealthRepository.SOURCE_STRENGTH_TRAINING).count { it == uuid }
    private suspend fun diaryOf(uuid: String) = db.exerciseLogDao().findBySourceId(uuid)

    // ==================== T15：新建训练，保存期间修改草稿后再次保存 ====================
    @Test
    fun `T15 新建训练两次保存只一场会话且最终内容与新草稿一致`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val call = AtomicInteger(0)
        // 第一次保存卡在 gate，第二次直接放行
        val repo = repoWith(saveHook = {
            if (call.incrementAndGet() == 1) gate.await()
        })
        repo.draftStore.save(strengthDraft("vm112-t15"))
        val vm = vm(repo)

        val first = CompletableDeferred<Boolean>()
        vm.save { first.complete(it) }   // 第一次：卡在事务内
        // 保存期间把重量从 40 改成 50（revision 递增，草稿保留）
        vm.updateSet("d1", 0, WorkoutSet(weightKg = 50.0, reps = 8))
        gate.complete(Unit)
        assertTrue(withTimeout(5_000) { first.await() })

        // 第一次保存后：草稿应仍在（新 revision），旧快照 40kg 已落库
        assertNotNull("保存期间的新草稿必须保留", repo.draftStore.load())
        assertEquals(1, sessionCount("vm112-t15"))

        // 第二次保存：用新草稿覆盖同一场
        val second = CompletableDeferred<Boolean>()
        vm.save { second.complete(it) }
        assertTrue(withTimeout(5_000) { second.await() })

        // 验证：仍是一场会话/一条账本/一条日记，sessionUuid 不变，明细为最新 50kg
        assertEquals("不得产生重复会话", 1, sessionCount("vm112-t15"))
        assertEquals("账本不得重复", 1, ledgerCount("vm112-t15"))
        assertNotNull(diaryOf("vm112-t15"))
        val rows = db.workoutExerciseDao().findBySession("vm112-t15")
        assertEquals(1, rows.size)
        val sets = com.qingji.app.domain.TrainingMath.decodeSets(rows[0].setsJson)
        assertEquals("最终内容必须是新草稿 50kg", 50.0, sets[0].weightKg, 0.001)
        assertEquals("最终次数必须是新草稿 8 次", 8, sets[0].reps)
        assertNull("成功后草稿应清理", repo.draftStore.load())
    }

    // ==================== T16：编辑历史训练，两次保存仍更新原场、不波及其他场 ====================
    @Test
    fun `T16 编辑历史训练两次保存只更新原历史场不覆盖其他场`() = runBlocking {
        // 预置两场历史训练：目标场 + 另一个场
        val plainRepo = repoWith()
        plainRepo.saveUnifiedSession(
            com.qingji.app.data.local.entity.WorkoutSession(
                uuid = "vm112-hist", date = java.time.LocalDate.of(2026, 11, 20),
                durationMinutes = 30, note = "原历史", source = "manual", createdAt = 1L
            ),
            listOf(WorkoutItem.Strength(
                itemId = "hs", exerciseUuid = "ex-squat", name = "深蹲", order = 0,
                sets = listOf(WorkoutSet(weightKg = 30.0, reps = 10))
            )), emptyList(), 70.0
        )
        plainRepo.saveUnifiedSession(
            com.qingji.app.data.local.entity.WorkoutSession(
                uuid = "vm112-other", date = java.time.LocalDate.of(2026, 11, 20),
                durationMinutes = 20, note = "其他历史", source = "manual", createdAt = 2L
            ),
            listOf(WorkoutItem.Strength(
                itemId = "os", exerciseUuid = "ex-bench", name = "卧推", order = 0,
                sets = listOf(WorkoutSet(weightKg = 60.0, reps = 5))
            )), emptyList(), 70.0
        )

        val gate = CompletableDeferred<Unit>()
        val call = AtomicInteger(0)
        val repo = repoWith(saveHook = { if (call.incrementAndGet() == 1) gate.await() })
        val vm = vm(repo)
        // 走真实 editSession 载入历史场草稿
        val loaded = CompletableDeferred<Unit>()
        vm.editSession("vm112-hist") { loaded.complete(Unit) }
        withTimeout(5_000) { loaded.await() }
        assertEquals("editingUuid 必须指向原历史场", "vm112-hist", vm.draft.value!!.editingUuid)

        val first = CompletableDeferred<Boolean>()
        vm.save { first.complete(it) }
        val before = vm.draft.value!!
        vm.updateSet(before.exercises[0].key, 0, WorkoutSet(weightKg = 45.0, reps = 6))
        gate.complete(Unit)
        assertTrue(withTimeout(5_000) { first.await() })
        assertNotNull(repo.draftStore.load())

        val second = CompletableDeferred<Boolean>()
        vm.save { second.complete(it) }
        assertTrue(withTimeout(5_000) { second.await() })

        // 目标历史场仍是原 uuid、一场、明细更新为 45kg
        assertEquals("原历史场不得重复", 1, sessionCount("vm112-hist"))
        val histRows = db.workoutExerciseDao().findBySession("vm112-hist")
        assertEquals(45.0, com.qingji.app.domain.TrainingMath.decodeSets(histRows[0].setsJson)[0].weightKg, 0.001)
        assertEquals("原历史日期不得被改成今天",
            java.time.LocalDate.of(2026, 11, 20), db.workoutSessionDao().findByUuid("vm112-hist")!!.date)
        assertEquals(1, ledgerCount("vm112-hist"))
        assertNotNull(diaryOf("vm112-hist"))
        // 其他历史场一字未动
        assertEquals(1, sessionCount("vm112-other"))
        assertEquals("其他历史场重量不得被覆盖", 60.0,
            com.qingji.app.domain.TrainingMath.decodeSets(
                db.workoutExerciseDao().findBySession("vm112-other")[0].setsJson)[0].weightKg, 0.001)
    }

    // ==================== T17：混合训练保存期间新增有氧动作 ====================
    @Test
    fun `T17 混合训练保存期间新增有氧草稿再次保存后力量有氧都在`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val call = AtomicInteger(0)
        val repo = repoWith(saveHook = { if (call.incrementAndGet() == 1) gate.await() })
        val running = WorkoutItem.Cardio(
            itemId = "c-run", exerciseUuid = "wg:running", name = "跑步", order = 1,
            durationSec = 1200L, distanceMeters = 4000.0,
            calculatedKcal = 100.0, calculationMethod = "met_type_default",
            calculationVersion = "v1", weightSnapshot = 70.0
        )
        val draft = WorkoutDraft(
            sessionUuid = "vm112-t17", startedAt = 1L, revision = 3L,
            exercises = listOf(
                DraftExercise(key = "d1", exerciseUuid = "ex-squat", name = "深蹲",
                    sets = listOf(WorkoutSet(weightKg = 40.0, reps = 10))),
                DraftExercise(key = "d2", exerciseUuid = "wg:running", name = "跑步",
                    recordType = ExerciseRecordType.CARDIO.key,
                    actualJson = WorkoutItem.toJson(running))
            )
        )
        repo.draftStore.save(draft)
        val vm = vm(repo)

        val first = CompletableDeferred<Boolean>()
        vm.save { first.complete(it) }
        // 保存期间把有氧实际时长改成 1800s
        vm.updateActual("d2", running.copy(durationSec = 1800L))
        gate.complete(Unit)
        assertTrue(withTimeout(5_000) { first.await() })

        val second = CompletableDeferred<Boolean>()
        vm.save { second.complete(it) }
        assertTrue(withTimeout(5_000) { second.await() })

        assertEquals(1, sessionCount("vm112-t17"))
        assertEquals("力量明细应在", 1, db.workoutExerciseDao().findBySession("vm112-t17").size)
        val cardio = db.workoutCardioDetailDao().findBySession("vm112-t17")
        assertEquals("有氧明细应在", 1, cardio.size)
        assertEquals("有氧实际时长应为新值 1800s", 1800L, cardio[0].durationSeconds)
        assertEquals("混合日记分类", "混合", diaryOf("vm112-t17")!!.type)
    }

    // ==================== T18：混合训练保存期间删除动作 ====================
    @Test
    fun `T18 混合训练保存期间删除有氧动作再次保存后有氧明细被清除`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val call = AtomicInteger(0)
        val repo = repoWith(saveHook = { if (call.incrementAndGet() == 1) gate.await() })
        val running = WorkoutItem.Cardio(
            itemId = "c-run", exerciseUuid = "wg:running", name = "跑步", order = 1,
            durationSec = 1200L, distanceMeters = 4000.0,
            calculatedKcal = 100.0, calculationMethod = "met_type_default",
            calculationVersion = "v1", weightSnapshot = 70.0
        )
        repo.draftStore.save(WorkoutDraft(
            sessionUuid = "vm112-t18", startedAt = 1L, revision = 2L,
            exercises = listOf(
                DraftExercise(key = "d1", exerciseUuid = "ex-squat", name = "深蹲",
                    sets = listOf(WorkoutSet(weightKg = 40.0, reps = 10))),
                DraftExercise(key = "d2", exerciseUuid = "wg:running", name = "跑步",
                    recordType = ExerciseRecordType.CARDIO.key,
                    actualJson = WorkoutItem.toJson(running))
            )
        ))
        val vm = vm(repo)

        val first = CompletableDeferred<Boolean>()
        vm.save { first.complete(it) }
        // 保存期间删掉有氧动作
        vm.removeExercise("d2")
        gate.complete(Unit)
        assertTrue(withTimeout(5_000) { first.await() })

        val second = CompletableDeferred<Boolean>()
        vm.save { second.complete(it) }
        assertTrue(withTimeout(5_000) { second.await() })

        assertEquals(1, sessionCount("vm112-t18"))
        assertEquals(1, db.workoutExerciseDao().findBySession("vm112-t18").size)
        assertTrue("被删除的有氧明细必须清除",
            db.workoutCardioDetailDao().findBySession("vm112-t18").isEmpty())
        assertEquals("删掉有氧后应归为纯力量", "力量", diaryOf("vm112-t18")!!.type)
    }

    // ==================== T19：保存期间协程取消后锁正确释放 ====================
    @Test
    fun `T19 保存被取消后保存锁正确释放且草稿保留`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val repo = repoWith(saveHook = {
            gate.await()
            throw CancellationException("模拟协程取消")
        })
        repo.draftStore.save(strengthDraft("vm112-t19", revision = 9L))
        val vm = vm(repo)

        val done = CompletableDeferred<Boolean>()
        vm.save { done.complete(it) }
        gate.complete(Unit)
        // 取消不回调 onDone(false)（不是普通失败），用 isSaving 轮询锁释放
        withTimeout(5_000) {
            while (vm.isSaving.value) kotlinx.coroutines.delay(10)
        }
        assertFalse("取消后保存锁必须释放", vm.isSaving.value)
        assertTrue("取消不得被当成普通保存失败回调 false", !done.isCompleted)
        assertNotNull("取消不应清草稿", repo.draftStore.load())
        // 锁释放后仍可再次正常保存
        val repo2 = repoWith()
        repo2.draftStore.save(strengthDraft("vm112-t19b"))
        val vm2 = vm(repo2)
        val ok = CompletableDeferred<Boolean>()
        vm2.save { ok.complete(it) }
        assertTrue(withTimeout(5_000) { ok.await() })
    }

    // ==================== T21：不同训练相同 revision 不得互相清理 ====================
    @Test
    fun `T21 不同训练相同revision不得互相清理草稿`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val call = AtomicInteger(0)
        val repo = repoWith(saveHook = { if (call.incrementAndGet() == 1) gate.await() })
        repo.draftStore.save(strengthDraft("vm112-t21-a", revision = 1L))
        val vm = vm(repo)

        val first = CompletableDeferred<Boolean>()
        vm.save { first.complete(it) }   // A 保存卡在事务内

        // 保存期间创建训练 B：同为 revision=1，但 sessionUuid 不同
        repo.draftStore.save(strengthDraft("vm112-t21-b", revision = 1L))
        gate.complete(Unit)
        assertTrue(withTimeout(5_000) { first.await() })

        val stored = repo.draftStore.load()
        assertNotNull("A 的保存不得清理掉 B 的草稿", stored)
        assertEquals("B 的 sessionUuid 必须保持", "vm112-t21-b", stored!!.sessionUuid)
        assertEquals("B 的 revision 必须保持", 1L, stored.revision)
        assertEquals("A 的数据库保存结果必须正确", 1, sessionCount("vm112-t21-a"))
        assertNotNull("A 的日记投影必须存在", diaryOf("vm112-t21-a"))
    }

    // ==================== T22：内存草稿为空时不得无条件清理新草稿 ====================
    @Test
    fun `T22 内存草稿为空时不得无条件清理新草稿`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val call = AtomicInteger(0)
        val repo = repoWith(saveHook = { if (call.incrementAndGet() == 1) gate.await() })
        repo.draftStore.save(strengthDraft("vm112-t22-a", revision = 1L))
        val vm = vm(repo)

        val first = CompletableDeferred<Boolean>()
        vm.save { first.complete(it) }   // A 保存卡在事务内

        // 保存期间：内存清空，并在 SharedPreferences 写入训练 B 的新草稿
        vm.discard()
        repo.draftStore.save(strengthDraft("vm112-t22-b", revision = 3L))
        assertNull("前置条件：内存草稿已空", vm.draft.value)

        gate.complete(Unit)
        assertTrue(withTimeout(5_000) { first.await() })

        val stored = repo.draftStore.load()
        assertNotNull("current==null 不得无条件 clear，B 的草稿必须保留", stored)
        assertEquals("B 的 sessionUuid 必须保持", "vm112-t22-b", stored!!.sessionUuid)
        assertEquals("B 的 revision 必须保持", 3L, stored.revision)
        assertEquals("A 的数据库保存结果必须正确", 1, sessionCount("vm112-t22-a"))
    }

    // ==================== T24：多 Store 实例共享锁 ====================
    @Test
    fun `T24 不同Store实例共享同一把草稿锁`() = runBlocking {
        val store1 = WorkoutDraftStore(ctx)
        val store2 = WorkoutDraftStore(ctx)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        store1.saveHook = {
            entered.countDown()
            release.await()   // 持有锁时阻塞，验证另一实例能否穿越
        }

        val t1 = Thread { store1.save(strengthDraft("vm112-t24-a", revision = 1L)) }
        t1.start()
        assertTrue("store1 应进入写入钩子", entered.await(5, TimeUnit.SECONDS))

        val t2Done = CompletableDeferred<Unit>()
        val t2 = Thread {
            store2.save(strengthDraft("vm112-t24-b", revision = 2L))
            t2Done.complete(Unit)
        }
        t2.start()
        val finishedEarly = runCatching {
            withTimeout(300) { t2Done.await() }
            true
        }.getOrDefault(false)
        assertFalse("共享锁应阻塞另一实例的写入", finishedEarly)

        release.countDown()
        withTimeout(5_000) { t2Done.await() }
        t1.join(5_000)
        t2.join(5_000)
        assertFalse("两个写入线程都应结束", t1.isAlive || t2.isAlive)

        val finalDraft = store2.load()
        assertNotNull(finalDraft)
        assertEquals("后完成的写入应最终生效", "vm112-t24-b", finalDraft!!.sessionUuid)
        assertEquals(2L, finalDraft.revision)
    }

    // ==================== T23：相同 sessionUuid 不同 revision 的条件清理 ====================
    @Test
    fun `T23 相同sessionUuid不同revision的条件清理`() = runBlocking {
        val store = repoWith().draftStore
        store.save(strengthDraft("vm112-t23", revision = 2L))

        assertFalse("按 revision=1 清理必须失败",
            store.clearIfMatches("vm112-t23", 1L))
        assertNotNull("清理失败时草稿必须还在", store.load())
        assertEquals(2L, store.load()!!.revision)

        assertFalse("不同 sessionUuid 同 revision 也必须失败",
            store.clearIfMatches("vm112-t23-other", 2L))
        assertNotNull("身份不匹配时草稿必须还在", store.load())

        assertTrue("同身份同 revision 才允许清理",
            store.clearIfMatches("vm112-t23", 2L))
        assertNull("条件清理成功后草稿应清空", store.load())
    }

    // ==================== P0-1：数据库成功但 UI 回调异常不得误判为保存失败 ====================
    @Test
    fun `回调异常不得把已成功的数据库保存改成失败`() = runBlocking {
        val repo = repoWith()
        repo.draftStore.save(strengthDraft("vm112-cb"))
        val vm = vm(repo)

        var successCalled = 0
        var failCalled = 0
        val done = CompletableDeferred<Unit>()
        // onDone(true) 内部抛异常，模拟 UI 关闭页面/导航回调失败
        vm.save { ok ->
            if (ok) {
                successCalled++
                done.complete(Unit)
                throw RuntimeException("模拟 UI 回调异常")
            } else failCalled++
        }
        withTimeout(5_000) { done.await() }
        kotlinx.coroutines.delay(100) // 让 runCatching 吞掉回调异常后的 finally 执行完

        // 数据库确实保存成功；回调异常不能被 catch 转成 onDone(false)
        assertEquals(1, sessionCount("vm112-cb"))
        assertEquals("成功回调只应调用一次", 1, successCalled)
        assertEquals("UI 回调异常绝不能触发失败回调", 0, failCalled)
        assertFalse("锁必须释放", vm.isSaving.value)
        assertNull("保存成功草稿应清理", repo.draftStore.load())
    }

    // ==================== T20：草稿落盘失败时内存与磁盘 revision 一致 ====================
    @Test
    fun `T20 草稿落盘失败时内存与磁盘revision保持一致`() = runBlocking {
        val repo = repoWith()
        repo.draftStore.save(strengthDraft("vm112-t20", revision = 5L))
        val vm = vm(repo)
        val before = vm.draft.value!!.revision  // 5

        // 注入落盘失败
        var fail = true
        repo.draftStore.saveHook = {
            if (fail) throw IllegalStateException("模拟存储写入失败")
        }
        // 通过公开修改入口触发 persist：落盘失败
        vm.updateSet("d1", 0, WorkoutSet(weightKg = 99.0, reps = 3))
        val afterMem = vm.draft.value!!
        val afterDisk = repo.draftStore.load()!!
        assertEquals("落盘失败时内存 revision 不得递增", before, afterMem.revision)
        assertEquals("内存与磁盘 revision 必须一致", afterDisk.revision, afterMem.revision)
        assertEquals("内存与磁盘重量必须一致", afterMem.exercises[0].sets[0].weightKg,
            afterDisk.exercises[0].sets[0].weightKg, 0.001)

        // 恢复落盘后重试：revision 正常递增且内存磁盘一致
        fail = false
        vm.updateSet("d1", 0, WorkoutSet(weightKg = 55.0, reps = 7))
        val retried = vm.draft.value!!
        assertEquals(before + 1, retried.revision)
        assertEquals(retried.revision, repo.draftStore.load()!!.revision)
        assertEquals(55.0, repo.draftStore.load()!!.exercises[0].sets[0].weightKg, 0.001)
    }
}
