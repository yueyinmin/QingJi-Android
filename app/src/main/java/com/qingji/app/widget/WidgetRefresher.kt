package com.qingji.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 小组件定向刷新器（阶段 0 从 WidgetActions.kt 原样拆出，零行为变化）。
 *
 * - 全部字面值（EXTRA_TARGET / TARGET_* / 枚举 / 250ms）与拆分前逐字一致；
 *   MainActivity 读 extra 的 key、Manifest/XML、SettingsStore 键名都没动。
 * - 协程跑在单例 scope；按目标定向刷新；250ms 后沿合并（collectLatest）。
 * - 不引入新依赖、不改调用方——QingJiRepository / SettingsViewModel / AddWaterAction
 *   仍用 `WidgetRefresher.refresh(...)` / `refreshNow(...)`，Kotlin 同包可见。
 */
/** 一个刷新目标对应一个小组件实例（阶段 0 从 WidgetActions.kt 原样搬来）。 */
enum class WidgetTarget { QUICK, OVERVIEW, WEIGHT }

object WidgetRefresher {

    /** 与 TARGET_KEY.name 保持一致，MainActivity 用这个 key 读 extra。 */
    const val EXTRA_TARGET = "qingji_target"

    /** 目标值就是 NavHost 里的路由字符串。 */
    const val TARGET_TEXT_AI = "text_ai"
    const val TARGET_WEIGHT = "weight"
    const val TARGET_IMAGE = "image_record"
    const val TARGET_HOME = "home"

    /** 全部组件（清数据 / 外观变化 / 首次启动用）。 */
    val ALL: Set<WidgetTarget> = WidgetTarget.entries.toSet()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val queued = MutableStateFlow(emptySet<WidgetTarget>())

    @Volatile
    private var appContext: Context? = null

    private val widgetOf: Map<WidgetTarget, GlanceAppWidget> = mapOf(
        WidgetTarget.QUICK to QuickRecordWidget(),
        WidgetTarget.OVERVIEW to OverviewWidget(),
        WidgetTarget.WEIGHT to WeightWidget()
    )

    /** 给 UI 层 / 仓库写入后用：定向合并刷新，不阻塞界面。 */
    fun refresh(context: Context, targets: Set<WidgetTarget> = ALL) {
        appContext = context.applicationContext
        // B2：设置/数据写入后主动使 Widget 设置快照失效，保证外观最终一致。
        WidgetSettingsCache.invalidate()
        WidgetPerf.mark("refresh_enqueue", detail = targets.joinToString(",") { it.name })
        scope.launch {
            mutex.withLock { queued.update { it + targets } }
        }
    }

    /** 真正刷新的 suspend 版本，给 ActionCallback 和需要「写完立即生效」的路径用。 */
    suspend fun refreshNow(context: Context, targets: Set<WidgetTarget> = ALL) {
        appContext = context.applicationContext
        refreshNowInternal(targets)
    }

    private suspend fun refreshNowInternal(targets: Set<WidgetTarget>) {
        val context = appContext ?: return
        val widgets: List<GlanceAppWidget> = targets.mapNotNull { widgetOf[it] }
        widgets.forEach { widget ->
            val t = WidgetPerf.begin("updateAll", "REFRESH")
            try {
                widget.updateAll(context)
            } catch (e: Exception) {
                // 某个组件没被添加到桌面时 updateAll 可能失败，不能影响其它组件
            } finally {
                WidgetPerf.end(t)
            }
        }
    }

    init {
        // 泵：把短时间内的多次请求合并成一次刷新（collectLatest 的去抖语义）
        scope.launch {
            queued.collectLatest { incoming ->
                if (incoming.isEmpty()) return@collectLatest
                delay(250)
                val targets = mutex.withLock {
                    val snapshot = queued.value
                    queued.value = emptySet()
                    snapshot
                }
                if (targets.isNotEmpty()) {
                    WidgetPerf.mark("refresh_merged", detail = targets.joinToString(",") { it.name })
                    refreshNowInternal(targets)
                }
            }
        }
    }
}
