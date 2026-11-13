package com.qingji.app.data.prefs

import android.content.Context
import com.qingji.app.domain.AppChangeLog

/**
 * ============================================================
 * 「更新后弹一次」的记忆
 * ------------------------------------------------------------
 * 记的是「用户上一次点过『我知道了』时的 versionCode」，
 * 不是「App 装过哪些版本」——用系统里已安装的版本号判断反而会被
 * 覆盖安装、备份恢复、多渠道包这些情况绕晕。
 *
 * 三种情况：
 * - **没有记录**：可能是全新安装，也可能是从 1.0.9 及以前升级上来
 *   （老版本里根本没有这个键）。两种都按「装上了一个新版本」处理，
 *   只展示当前版本的更新内容 —— 之前把它当成全新安装静默记下，
 *   导致覆盖安装升级后用户看不到任何提示（2026-11-13 修）。
 * - 升级后第一次打开（记录 < 当前版本）：把中间漏掉的几个版本一起返回，弹一次；
 * - 同版本反复打开（记录 == 当前版本）：不弹。
 *
 * 只有用户点了弹窗上的「我知道了」才写 markSeen；
 * 中途杀进程、点返回键回来还会再弹一次，不会漏看。
 * ============================================================
 */
class UpdateNoticeStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("app_version_notice", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_SEEN_CODE = "last_seen_version_code"
    }

    /** 返回本次要展示的版本记录；空列表 = 不用弹。 */
    fun pendingReleases(currentCode: Int): List<AppChangeLog.Release> {
        if (!sp.contains(KEY_LAST_SEEN_CODE)) {
            // 全新安装 / 从没弹过提示的老版本升级：都展示当前版本的更新内容。
            // 这里绝对不能 markSeen，否则用户一次都没看到就被记成已读。
            return listOfNotNull(AppChangeLog.current(currentCode))
        }
        val last = sp.getInt(KEY_LAST_SEEN_CODE, currentCode)
        if (last >= currentCode) return emptyList()

        val pending = AppChangeLog.since(last, currentCode)
        if (pending.isEmpty()) {
            // 版本升了但没登记更新项，只展示当前版本，别让用户什么都看不到
            return listOfNotNull(AppChangeLog.current(currentCode))
        }
        return pending
    }

    fun markSeen(currentCode: Int) {
        sp.edit().putInt(KEY_LAST_SEEN_CODE, currentCode).apply()
    }
}
