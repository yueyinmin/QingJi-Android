package com.qingji.app.domain

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoodAliasPatchLoaderTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `加载资产中的v1补丁并校验版本`() {
        val patch = FoodAliasPatchLoader.load(context).getOrThrow()

        assertEquals(1, patch.schemaVersion)
        assertEquals("0.9-lxj-kcal-match", patch.appliesToCatalogVersion)
        assertTrue(patch.aliases.any { it.alias == "木樨肉" && it.preferredFoodId == 100185L })
        assertTrue(patch.aliases.any { it.alias == "宫爆鸡丁" && it.preferredFoodId == null })
    }

    @Test
    fun `食物库版本不匹配时加载失败`() {
        val result = FoodAliasPatchLoader.load(context, expectedCatalogVersion = "9.9-wrong")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("版本不匹配"))
    }
}
