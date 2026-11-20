package com.qingji.app.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v3.5.0：双语回落逻辑。
 * 设计承诺：英文表没收录的条目回落中文，绝不显示 key 或空白；
 * 中文模式零成本直出。
 */
class L10nTest {

    @Test
    fun chineseModeReturnsSourceTextDirectly() {
        assertEquals("首页", sStatic("首页", "zh"))
        // 表里根本没有这条也照样返回
        assertEquals("随便什么文案", sStatic("随便什么文案", "zh"))
    }

    @Test
    fun englishModeFallsBackToChineseWhenMissing() {
        assertEquals("这个没翻译", sStatic("这个没翻译", "en"))
        assertEquals("", sStatic("", "en"))
    }

    @Test
    fun englishModeTranslatesKnownKeys() {
        assertEquals("Home", sStatic("首页", "en"))
        assertEquals("Settings", sStatic("设置", "en"))
        assertEquals("Back", sStatic("返回", "en"))
    }

    @Test
    fun unknownLanguageBehavesLikeChinese() {
        assertEquals("首页", sStatic("首页", "jp"))
        assertEquals("首页", sStatic("首页", ""))
    }

    @Test
    fun coreEntriesAreAllPresent() {
        // 底部导航四个 tab + 通用按钮必须双语齐全，漏了会导致英文模式下导航退回中文
        val mustHave = listOf(
            "首页", "进展", "日记", "我的", "记一笔",
            "返回", "保存", "取消", "确定", "知道了", "删除", "搜索",
            "设置", "去设置", "界面语言",
            "今日记录", "当日记录", "今日剩余", "暂无记录",
            "标准食物库", "自定义食物", "吃过的", "我的收藏",
            "家庭厨房", "外卖快餐"
        )
        val missing = mustHave.filter { !L10nEnKeys.has(it) }
        assertTrue("英文表缺条目: $missing", missing.isEmpty())
    }
}

/** 测试专用小门面：不动 L10n.kt 的可见性。 */
private object L10nEnKeys {
    fun has(zh: String): Boolean {
        // 通过 sStatic 不够（回落无法区分「翻译了」和「原本就是英文」），
        // 直接反射拿 EN 表？不行——private。这里换个思路：英文模式返回值 != 中文原值即视为已翻译。
        return sStatic(zh, "en") != zh || zh == "English"
    }
}
