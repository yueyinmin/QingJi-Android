package com.qingji.app.domain

import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.local.entity.ExercisePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 有氧/力训动作库拆分口径的单测。 */
class AerobicLibraryTest {

    @Test
    fun mappedCardioActionIsAerobic() {
        val running = ExerciseEntity(
            uuid = "wg:running",
            name = "跑步",
            exerciseType = "distance_duration"
        )
        assertTrue(AerobicLibrary.isAerobic(running))
    }

    @Test
    fun strengthActionIsNotAerobic() {
        val bench = ExerciseEntity(
            uuid = "ex-chest-01",
            name = "哑铃卧推",
            exerciseType = "weight_reps"
        )
        assertFalse(AerobicLibrary.isAerobic(bench))
    }

    @Test
    fun userOverrideWinsOverMapping() {
        // 卧推映射是 STRENGTH，但用户手动改成 CARDIO 后应认定有氧
        val fixed = ExerciseEntity(
            uuid = "ex-chest-01",
            name = "哑铃卧推",
            recordType = "CARDIO"
        )
        assertTrue(AerobicLibrary.isAerobic(fixed))
    }

    @Test
    fun presetConvertsToMemoryEntityWithPrefix() {
        val preset = ExercisePreset(id = 42, name = "快走 (6 km/h)", met = 5.0)
        val entity = AerobicLibrary.fromPreset(preset)
        assertEquals("preset:42", entity.uuid)
        assertEquals(42L, AerobicLibrary.presetIdOf(entity.uuid))
        assertEquals(ExerciseRecordType.CARDIO.name, entity.recordType)
        assertTrue(AerobicLibrary.isAerobic(entity))
    }

    @Test
    fun realActionUuidIsNotPreset() {
        assertNull(AerobicLibrary.presetIdOf("wg:running"))
    }

    @Test
    fun onlyAerobicPresetsEnterLibrary() {
        assertTrue(AerobicLibrary.isAerobicPreset(ExercisePreset(name = "跑步 (8 km/h)", met = 8.3)))
        assertFalse(AerobicLibrary.isAerobicPreset(ExercisePreset(name = "俯卧撑", met = 4.0, category = "力量")))
        assertFalse(AerobicLibrary.isAerobicPreset(ExercisePreset(name = "瑜伽", met = 2.5, category = "柔韧")))
    }

    @Test
    fun canonicalNameStripsParenthesesAndSpaces() {
        assertTrue(AerobicLibrary.sameCanonicalName("网球（单打）", "网球"))
        assertTrue(AerobicLibrary.sameCanonicalName("跑步 (8 km/h)", "跑步"))
        assertFalse(AerobicLibrary.sameCanonicalName("骑行", "跑步"))
    }

    @Test
    fun newAerobicActionsFallBackToTypeMet() {
        assertEquals("running", AerobicLibrary.activityTypeForSlug("treadmill-run"))
        assertEquals("hiit", AerobicLibrary.activityTypeForSlug("kickboxing-aerobic"))
        assertNull(AerobicLibrary.activityTypeForSlug("not-a-seed"))

        val mets = mapOf("running" to Triple(7.0, 9.0, 11.0))
        val entity = ExerciseEntity(uuid = "wg:treadmill-run", name = "跑步机跑步", slug = "treadmill-run")
        val item = WorkoutItem.Cardio(
            itemId = "i1", exerciseUuid = entity.uuid, name = entity.name, order = 0,
            durationSec = 1800L
        )
        val settled = TrainingItemMapper.withCardioKcal(item, entity, mets, 70.0)
        assertTrue(settled.calculatedKcal > 0.0)
    }
}
