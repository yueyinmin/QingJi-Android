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
 * - 第一次装（没有记录）：直接记住当前版本，**不弹**。新用户不需要看更新说明；
 * - 升级后第一次打开（记录 < 当前版本）：把中间漏掉的几个版本一起返回，弹一次；
 * - 同版本反复打开（记录 >= 当前版本）：不弹。
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
            // 全新安装：静默记下当前版本
            markSeen(currentCode)
            return emptyList()
        }
        val last = sp.getInt(KEY_LAST_SEEN_CODE, currentCode)
        if (last >= currentCode) return emptyList()

        val pending = AppChangeLog.since(last, currentCode)
        if (pending.isEmpty()) {
            // 版本升了但没登记更新项，也别每次启动都白查一遍
            markSeen(currentCode)
        }
        return pending
    }

    fun markSeen(currentCode: Int) {
        sp.edit().putInt(KEY_LAST_SEEN_CODE, currentCode).apply()
    }
}
