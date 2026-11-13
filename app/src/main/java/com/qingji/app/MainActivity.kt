package com.qingji.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.data.prefs.UpdateNoticeStore
import com.qingji.app.domain.AppClock
import com.qingji.app.ui.components.UpdateNoticeDialog
import com.qingji.app.ui.navigation.QingJiAppRoot
import com.qingji.app.ui.theme.QingJiTheme
import com.qingji.app.widget.WidgetRefresher

/**
 * 全 App 唯一的 Activity。
 * 界面全部用 Compose 画，这里只做五件事：
 * 1. 铺满屏幕
 * 2. 订阅设置里的「浅色/深色/跟随系统」，把模式交给主题
 * 3. 把设置里的「记录时区」交给 AppClock（否则时区设置不生效）
 * 4. 读桌面小组件带过来的跳转目标（点小组件按钮直达对应记录页）
 * 5. 每次打开 App 主动刷新一次小组件（国产 ROM 会杀后台，兜底用）
 */
class MainActivity : ComponentActivity() {

    /**
     * 小组件要我们跳到哪一页。
     * 为什么还要一个 requestId：用户可能连着点两次同一个按钮，
     * 目标字符串一样，界面会以为「没变化」而不跳。加个自增号就能每次都跳。
     */
    private var pendingTarget by mutableStateOf<String?>(null)
    private var requestId by mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // App 一打开就刷新小组件，弥补系统周期刷新被省电策略拖慢的问题
        WidgetRefresher.refresh(applicationContext)

        readTarget(intent)

        setContent {
            val context = LocalContext.current
            val settingsFlow = remember { SettingsStore.observe(context) }
            val settings by settingsFlow.collectAsState(initial = QingJiSettings())

            // 时区设置要落到全局时钟上，页面里的「今天」才会跟着变
            SideEffect { AppClock.setZone(settings.timeZone) }

            QingJiTheme(
                themeMode = settings.themeMode,
                accentKey = settings.themeColor
            ) {
                // 正式版 v1.1：冷启动开屏动画（三叶聚拢）。
                // rememberSaveable 记住「播过了」，旋转 / 重组 / 进程内重建都不会重播；
                // 只有真正冷启动（进程被杀后重新打开）才播一次。
                var splashDone by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                if (!splashDone) {
                    com.qingji.app.ui.components.QingJiSplash(
                        onAnimationFinished = { splashDone = true }
                    )
                    return@QingJiTheme
                }
                // v3.5.0：界面语言放进 CompositionLocal，核心页的 s() 据此取文案；
                // 切语言只写 DataStore，这里跟着重组，不需要重启 Activity
                androidx.compose.runtime.CompositionLocalProvider(
                    com.qingji.app.ui.i18n.LocalUiLang provides settings.uiLanguage
                ) {
                    QingJiAppRoot(
                        startTarget = pendingTarget,
                        startTargetRequestId = requestId,
                        // v3.2.3：跳完就把目标清掉。以前它一直挂在 Activity 上，
                        // 只要界面因换主题/转屏重新组合一次，就会被重新消费一遍，
                        // 表现成「明明点了首页，最后又跑到别的页」。
                        onTargetConsumed = { pendingTarget = null }
                    )

                    // ---------- 版本更新提示（升级后第一次打开只弹一次） ----------
                    // 放在开屏动画之后、主界面之上：全新安装不弹，覆盖安装升级才弹。
                    // 只有点「我知道了」才写已读，中途被杀掉下次进来还会再弹。
                    val updateStore = remember { UpdateNoticeStore(context) }
                    var pendingUpdates by remember {
                        mutableStateOf(updateStore.pendingReleases(BuildConfig.VERSION_CODE))
                    }
                    if (pendingUpdates.isNotEmpty()) {
                        UpdateNoticeDialog(
                            releases = pendingUpdates,
                            onDismiss = {
                                updateStore.markSeen(BuildConfig.VERSION_CODE)
                                pendingUpdates = emptyList()
                            }
                        )
                    }
                }
            }
        }
    }

    /** App 已经在前台时又点了小组件：系统走这里，不会重建 Activity。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readTarget(intent)
    }

    /**
     * 断食常驻计时：只在 App 进前台时同步一次。
     * 不在 Application.onCreate 里做，是因为 Android 12+ 不允许后台启动前台服务，
     * 在这儿调用系统一定会放行。
     */
    override fun onStart() {
        super.onStart()
        try {
            com.qingji.app.notify.FastingService.sync(this)
        } catch (e: Exception) {
            // 通知挂不上不影响任何功能，静默即可
        }
    }

    private fun readTarget(intent: Intent?) {
        val target = intent?.getStringExtra(WidgetRefresher.EXTRA_TARGET)
        if (!target.isNullOrBlank()) {
            pendingTarget = target
            requestId += 1
            // 用完就清掉，免得以后转屏重建时又跳一次
            intent?.removeExtra(WidgetRefresher.EXTRA_TARGET)
        }
    }
}
