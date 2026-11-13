package com.qingji.app.widget

import android.content.Context
import android.os.SystemClock
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
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
 * ============================================================
 * 小组件的公共动作（M8）
 * ------------------------------------------------------------
 * 1. TARGET_KEY：点小组件按钮时，把「要去哪个页面」塞进 Intent extra，
 *    MainActivity 读出来直接导航，不用先回首页再点。
 * 2. AddWaterAction：点「喝水」不打开 App，直接往 Room 写 +200ml。
 *    v1.1.0：加 600ms 防抖（合并误触，不禁止真实场景下的连续记录），
 *    并且写库成功才刷新 —— 失败绝不假装成功。
 * 3. WidgetRefresher（v1.1.0 重构）：
 *    - 协程全部跑在单例 scope 里，不再每次新建 CoroutineScope；
 *    - 按数据类型定向刷新：饮食/饮水/营养目标 → 今日概览，
 *      体重 → 体重闪记，外观/清数据 → 全部；
 *    - 短时间内的多次刷新请求经 250ms 合并成一次（collectLatest 去抖），
 *      不再出现「一次操作触发三组件重复重绘」。
 * ============================================================
 */

/** ActionParameters 的 name 会原样变成 Intent extra 的 key。 */
val TARGET_KEY = ActionParameters.Key<String>("qingji_target")

/** 一个刷新目标对应一个小组件实例。 */
enum class WidgetTarget { QUICK, OVERVIEW, WEIGHT }

/** 点「喝水」直接记账，不打开界面。 */
class AddWaterAction : ActionCallback {

    private var lastClickAt = 0L

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickAt < DEBOUNCE_MS) return
        lastClickAt = now
        try {
            QingJiRepository(context).addWater(200, AppClock.today())
        } catch (e: Exception) {
            // 写库失败就到此为止：桌面不刷新，用户看到的仍是旧数字，
            // 绝不出现「点了但没记上还以为记上了」。
            return
        }
        WidgetRefresher.refreshNow(context, setOf(WidgetTarget.OVERVIEW))
    }

    private companion object {
        /** 600ms 内的连点视为同一次误触；超过再点就是真实的新记录。 */
        const val DEBOUNCE_MS = 600L
    }
}

object WidgetRefresher {

    /** 与 TARGET_KEY.name 保持一致，MainActivity 用这个 key 读 extra。 */
    const val EXTRA_TARGET = "qingji_target"

    /** 目标值就是 NavHost 里的路由字符串。 */
    const val TARGET_SEARCH = "search_food"
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
        widgets.forEach {
            try {
                it.updateAll(context)
            } catch (e: Exception) {
                // 某个组件没被添加到桌面时 updateAll 可能失败，不能影响其它组件
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
                if (targets.isNotEmpty()) refreshNowInternal(targets)
            }
        }
    }
}
