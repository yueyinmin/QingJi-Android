package com.qingji.app.training

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.prefs.DraftExercise
import com.qingji.app.data.prefs.WorkoutDraft
import com.qingji.app.data.repository.TrainingRepository
import com.qingji.app.domain.ExerciseRecordType
import com.qingji.app.domain.TargetConfig
import com.qingji.app.domain.TargetConfigJson
import com.qingji.app.domain.WorkoutItem
import com.qingji.app.ui.viewmodel.TrainingViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** B1.1 ViewModel 层集成测试：草稿 → save() → 真实 Room。 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TrainingViewModelB11Test {

    private lateinit var db: AppDatabase
    private lateinit var repo: TrainingRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = TrainingRepository(context, refreshHook = { _, _ -> }, dbOverride = db)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun vm() = TrainingViewModel(
        ApplicationProvider.getApplicationContext<android.app.Application>(), repo
    )

    // T07：只有计划目标、没有用户实际录入时，不得生成虚假实际成绩
    @Test
    fun `T07 仅有目标无实际录入不会写入假成绩`() = runBlocking {
        val target = TargetConfigJson.encode(
            TargetConfig(type = ExerciseRecordType.CARDIO, durationSec = 3600L, distanceMeters = 10000.0)
        )
        repo.draftStore.save(WorkoutDraft(
            sessionUuid = "vm-t07", startedAt = 1L,
            exercises = listOf(DraftExercise(
                key = "d1", exerciseUuid = "wg:running", name = "跑步",
                recordType = ExerciseRecordType.CARDIO.key, targetConfigJson = target
                // 故意不写 actualJson：用户一个实际字段都没填
            ))
        ))
        val vm = vm()
        assertTrue("草稿应已恢复", vm.draft.value != null)
        val saveDone = CompletableDeferred<Boolean>()
        vm.save { saveDone.complete(it) }
        assertTrue(withTimeout(5_000) { saveDone.await() })
        val row = db.workoutCardioDetailDao().findBySession("vm-t07").single()
        assertEquals(0L, row.durationSeconds)
        assertEquals(null, row.distanceMeters)
        assertEquals(0.0, row.calculatedKcal, 0.001)
        assertEquals("", row.calculationVersion)
        val saved = WorkoutItem.fromJson(row.rawInputJson) as WorkoutItem.Cardio
        assertEquals(3600L, TargetConfigJson.decode(saved.targetConfigJson)?.durationSec)
    }

    // T04/T05：手动添加有氧动作按动作自身类型识别，不走力量组表单
    @Test
    fun `T04 手动添加有氧动作草稿类型正确且无力量组`() = runBlocking {
        db.exerciseDao().insertAll(listOf(
            ExerciseEntity(uuid = "wg:running", name = "跑步", exerciseType = "distance_duration")
        ))
        repo.draftStore.save(WorkoutDraft(sessionUuid = "vm-t04", startedAt = 1L))
        val vm = vm()
        assertTrue("草稿应已恢复", vm.draft.value != null)
        val entity = db.exerciseDao().findByUuid("wg:running")!!
        vm.addExercise(entity)
        val draft = withTimeout(5_000) {
            vm.draft.first { it != null && it.exercises.isNotEmpty() }
        }!!
        val added = draft.exercises.single()
        assertEquals(ExerciseRecordType.CARDIO.key, added.recordType)
        assertTrue(added.sets.isEmpty())

        val saveDone = CompletableDeferred<Boolean>()
        vm.save { saveDone.complete(it) }
        assertTrue(withTimeout(5_000) { saveDone.await() })
        assertTrue(db.workoutExerciseDao().findBySession("vm-t04").isEmpty())
        assertEquals(1, db.workoutCardioDetailDao().findBySession("vm-t04").size)
    }
}
