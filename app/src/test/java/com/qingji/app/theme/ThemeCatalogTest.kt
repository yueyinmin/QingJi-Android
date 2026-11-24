package com.qingji.app.theme

import com.qingji.app.domain.AppIconManager
import com.qingji.app.ui.theme.AccentThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** QJ-V145：主题色 20 套、图标 9 种的目录完整性。 */
class ThemeCatalogTest {

    @Test
    fun `主题色恰好二十套且 key 唯一`() {
        assertEquals(20, AccentThemes.ALL.size)
        val keys = AccentThemes.ALL.map { it.key }
        assertEquals(20, keys.toSet().size)
        assertEquals("green", keys.first())
    }

    @Test
    fun `每套主题色九档色值完整且默认回落松绿`() {
        AccentThemes.ALL.forEach { spec ->
            assertTrue(spec.label.isNotBlank())
            assertTrue(spec.lightMain != 0L && spec.lightDark != 0L && spec.lightSoft != 0L)
            assertTrue(spec.darkMain != 0L && spec.darkDark != 0L && spec.darkSoft != 0L)
            assertTrue(spec.macroProtein != 0L && spec.macroCarb != 0L && spec.macroFat != 0L)
            assertEquals(spec, AccentThemes.byKey(spec.key))
        }
        assertEquals(AccentThemes.ALL.first(), AccentThemes.byKey("unknown"))
    }

    @Test
    fun `图标恰好九种且 alias 唯一`() {
        assertEquals(9, AppIconManager.VARIANTS.size)
        val aliases = AppIconManager.VARIANTS.map { AppIconManager.aliasClass(it) }
        assertEquals(9, aliases.toSet().size)
        assertEquals("com.qingji.app.LauncherAliasDefault", AppIconManager.aliasClass("default"))
        assertEquals("com.qingji.app.LauncherAliasFlame", AppIconManager.aliasClass("flame"))
        AppIconManager.VARIANTS.forEach { assertTrue(AppIconManager.label(it).isNotBlank()) }
    }
}
