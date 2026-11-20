package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 条码校验位 + 开放食品库返回解析。 */
class BarcodeRulesTest {

    @Test
    fun `去掉非数字字符`() {
        assertEquals("6901234567892", BarcodeRules.normalize(" 690-1234 567892 "))
    }

    @Test
    fun `EAN13 校验位算得对`() {
        // 6+27+0+3+2+9+4+15+6+21+8+27 = 128 → (10-8)%10 = 2
        assertEquals(2, BarcodeRules.checksum("690123456789"))
        assertEquals("6901234567892", "690123456789" + BarcodeRules.checksum("690123456789"))
    }

    @Test
    fun `校验完整条码`() {
        assertTrue(BarcodeRules.isValidEan13("6901234567892"))
        assertFalse(BarcodeRules.isValidEan13("6901234567893"))
        assertFalse(BarcodeRules.isValidEan13("123"))
        assertFalse(BarcodeRules.isValidEan13("abcdefghijklm"))
    }

    @Test
    fun `非法前12位返回负一`() {
        assertEquals(-1, BarcodeRules.checksum("123"))
        assertEquals(-1, BarcodeRules.checksum("69012345678X"))
    }

    @Test
    fun `解析正常返回`() {
        val json = """
            {"status":1,"product":{"product_name":"全麦面包",
             "nutriments":{"energy-kcal_100g":250.5,"proteins_100g":9.0,
                           "carbohydrates_100g":45.0,"fat_100g":3.2}}}
        """.trimIndent()
        val p = BarcodeRules.parseOpenFoodFacts(json)
        assertNotNull(p)
        assertEquals("全麦面包", p!!.name)
        assertEquals(250.5, p.kcalPer100g, 0.001)
        assertEquals(9.0, p.proteinPer100g, 0.001)
        assertEquals(45.0, p.carbPer100g, 0.001)
        assertEquals(3.2, p.fatPer100g, 0.001)
    }

    @Test
    fun `只给千焦时自动换算成千卡`() {
        val json = """{"status":1,"product":{"product_name":"饼干","nutriments":{"energy-kj_100g":418.4}}}"""
        val p = BarcodeRules.parseOpenFoodFacts(json)
        assertNotNull(p)
        assertEquals(100.0, p!!.kcalPer100g, 0.5)
        assertEquals(0.0, p.proteinPer100g, 0.001)
    }

    @Test
    fun `查无此商品或字段残缺返回null`() {
        assertNull(BarcodeRules.parseOpenFoodFacts("""{"status":0,"product":null}"""))
        assertNull(BarcodeRules.parseOpenFoodFacts("""{"status":1,"product":{"product_name":"??"}}"""))
        assertNull(BarcodeRules.parseOpenFoodFacts("not json at all"))
    }

    @Test
    fun `中文名字段兜底与转义引号`() {
        val json = """{"status":1,"product":{"product_name_zh":"酸奶\"原味\"","nutriments":{"energy-kcal_100g":72}}}"""
        val p = BarcodeRules.parseOpenFoodFacts(json)
        assertNotNull(p)
        assertEquals("酸奶\"原味\"", p!!.name)
    }
}
