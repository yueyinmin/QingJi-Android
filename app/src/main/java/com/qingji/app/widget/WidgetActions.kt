package com.qingji.app.widget

import com.qingji.app.domain.AppClock
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.qingji.app.data.repository.QingJiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * ============================================================
 * 小组件的公共动作（M8）
 * ------------------------------------------------------------
 * 三件事：
 * 1. TARGET_KEY：点小组件按钮时，把「要去哪个页面」塞进 Intent extra，
 *    MainActivity 读出来直接导航，不用先回首页再点。
 * 2. AddWaterAction：点「喝水」不打开 App，直接往 Room 写 +200ml 并刷新，
 *    这是 PRD 验收清单里明确要求的一条。
 * 3. WidgetRefresher：App 内数据一变就主动刷新全部小组件。
 *    国产 ROM 会杀后台，光靠系统的 30 分钟周期刷新不够。
 * ============================================================
 */

/** ActionParameters 的 name 会原样变成 Intent extra 的 key。 */
val TARGET_KEY = ActionParameters.Key<String>("qingji_target")

/** 点「喝水」直接记账，不打开界面。 */
class AddWaterAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        QingJiRepository(context).addWater(200, AppClock.today())
        WidgetRefresher.refreshNow(context)
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

    /** 给 UI 层用：起个后台协程刷新，不阻塞界面。 */
    fun refresh(context: Context) {
        CoroutineScope(Dispatchers.Default).launch { refreshNow(context) }
    }

    /** 真正刷新的 suspend 版本，给 ActionCallback 和仓库写入后调用。 */
    suspend fun refreshNow(context: Context) {
        val widgets: List<GlanceAppWidget> = listOf(
            QuickRecordWidget(),
            OverviewWidget(),
            WeightWidget()
        )
        widgets.forEach {
            try {
                it.updateAll(context)
            } catch (e: Exception) {
                // 某个组件没被添加到桌面时 updateAll 可能失败，不能影响其它组件
            }
        }
    }
}
