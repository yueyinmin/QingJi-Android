package com.qingji.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** V2.1 阶段B4：深浅色主题选择与对比度测试。 */
@RunWith(RobolectricTestRunner::class)
class WidgetThemeTest {

    @Test
    fun `主题选择按uiMode返回独立调色板`() {
        val light = WidgetSpec.theme(false)
        val dark = WidgetSpec.theme(true)
        assertEquals(WidgetTheme.LIGHT, light)
        assertEquals(WidgetTheme.DARK, dark)
        assertTrue(light != dark)
        assertEquals(WidgetTheme.LIGHT, WidgetSpec.light())
        assertEquals(WidgetTheme.DARK, WidgetSpec.dark())
    }

    @Test
    fun `两个主题都提供全量颜色且互不相同`() {
        val l = WidgetTheme.LIGHT
        val d = WidgetTheme.DARK
        assertTrue(l.card != d.card)
        assertTrue(l.text != d.text)
        assertTrue(l.sub != d.sub)
        assertTrue(l.green != d.green)
        assertTrue(l.greenLight != d.greenLight)
        assertTrue(l.overLimit != d.overLimit)
        assertTrue(l.warnOrange != d.warnOrange)
        assertTrue(l.divider != d.divider)
        assertTrue(l.macroProtein != d.macroProtein)
        assertTrue(l.macroCarb != d.macroCarb)
        assertTrue(l.macroFat != d.macroFat)
    }

    @Test
    fun `深色模式文字与背景有亮度差`() {
        val d = WidgetTheme.DARK
        // 深色底 + 浅色字：R+G+B 之和差异显著，保证对比度
        val bg = d.card.red + d.card.green + d.card.blue
        val fg = d.text.red + d.text.green + d.text.blue
        assertTrue("深色模式文字与背景对比不足", fg - bg > 1.0f)
        val l = WidgetTheme.LIGHT
        val lbg = l.card.red + l.card.green + l.card.blue
        val lfg = l.text.red + l.text.green + l.text.blue
        assertTrue("浅色模式文字与背景对比不足", lbg - lfg > 1.0f)
    }

    @Test
    fun `系统深色配置被正确识别`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(context.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            Configuration.UI_MODE_NIGHT_YES
        val themed = context.createConfigurationContext(config)
        assertTrue(WidgetTheme.isDark(themed))
        assertEquals(WidgetTheme.DARK, WidgetTheme.of(themed))
    }

    @Test
    fun `stale与错误状态在深色模式有独立醒目色`() {
        val d = WidgetTheme.DARK
        assertTrue(d.overLimit != d.text) // 超标/错误不是普通文字色
        assertTrue(d.warnOrange != d.text) // 估算标识不是普通文字色
        assertTrue(d.sub != d.text)        // stale 辅助色与正文区分
    }
}
