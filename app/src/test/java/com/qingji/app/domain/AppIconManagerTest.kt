package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.1.0：图标切换的纯逻辑测试。
 * 别名类名是切换功能的根基 —— 类名一旦和 Manifest 不一致，
 * setComponentEnabledSetting 会抛 IllegalArgumentException，
 * 所以这里把「三套类名互不相同、与包名前缀一致」锁死。
 */
class AppIconManagerTest {

    @Test
    fun `三套图标各对应不同的别名类`() {
        val classes = AppIconManager.VARIANTS.map { AppIconManager.aliasClass(it) }
        assertEquals(3, classes.size)
        assertEquals(3, classes.toSet().size)
    }

    @Test
    fun `未知图标值回落到默认别名`() {
        assertEquals(
            AppIconManager.aliasClass("default"),
            AppIconManager.aliasClass("不存在的值")
        )
    }

    @Test
    fun `别名类名与包名前缀一致`() {
        AppIconManager.VARIANTS.forEach {
            assertTrue(AppIconManager.aliasClass(it).startsWith("com.qingji.app.LauncherAlias"))
        }
    }

    @Test
    fun `每个图标都有中文名`() {
        AppIconManager.VARIANTS.forEach {
            assertTrue(AppIconManager.label(it).isNotBlank())
        }
    }
}
