package com.qingji.app.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingji.app.MainActivity
import com.qingji.app.data.prefs.SettingsKeys
import com.qingji.app.data.prefs.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.annotation.Config

/**
 * Compose 真交互冒烟测试。
 *
 * Robolectric android-all 已可由阿里云镜像下载，本类不再 @Ignore。
 * 外层 TestRule 在 Activity 启动前跳过引导页；waitForHome() 等开屏动画结束。
 * 先恢复最小闭环：底栏「记一笔」能打开记录面板。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class HomeInteractionTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(object : TestRule {
            override fun apply(base: Statement, description: Description): Statement = object : Statement() {
                override fun evaluate() {
                    val context = ApplicationProvider.getApplicationContext<Context>()
                    runBlocking {
                        SettingsStore.clear(context)
                        SettingsStore.set(context, SettingsKeys.ONBOARDING_DONE, true)
                        SettingsStore.set(context, SettingsKeys.UI_LANGUAGE, "zh")
                    }
                    base.evaluate()
                }
            }
        })
        .around(composeRule)

    private fun waitForHome() {
        composeRule.waitUntil(timeoutMillis = 8000) {
            composeRule.onAllNodesWithContentDescription("记一笔")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** 底栏入口能不能打开记一笔面板。 */
    @Test
    fun clickAddButton_opensRecordSheet() {
        waitForHome()
        composeRule.onNodeWithContentDescription("记一笔").performClick()
        composeRule.waitUntil(timeoutMillis = 8000) {
            composeRule.onAllNodesWithText("扫描食物").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("扫描食物").assertIsDisplayed()
    }
}
