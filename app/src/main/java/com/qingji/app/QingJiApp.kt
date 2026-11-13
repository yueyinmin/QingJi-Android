package com.qingji.app

import android.app.Application
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.diagnostics.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * App 的总管家。
 * 启动时先跑这里，适合放「全局只创建一次」的东西，比如数据库。
 */
class QingJiApp : Application() {

    /** 懒加载：第一次用到时才真正打开用户数据库，启动更快。 */
    val database: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()

        // --------------------------------------------------------
        // P1 诊断基建：统一日志 + 全局崩溃钩子（CrashHandler 只加写日志，
        // 不改变崩溃流程本身；见 diagnostics/CrashHandler.kt）。
        // 必须在其它初始化之前装好，早期崩溃才抓得到。
        // --------------------------------------------------------
        AppLogger.init(this)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = QingJiRepository(this@QingJiApp)
                // 只是确认标准库能打开、顺便把实例预热好，不再写任何标准食物
                repo.ensurePresetFoods()
                repo.ensurePresetExercises()

                // v2.8：默认档案不灌入——首次启动走引导页让用户填自己的数据。
            } catch (e: Exception) {
                // 以前这里是裸协程，一旦写库失败（磁盘满、数据库损坏）异常没人接，
                // 会直接抛到主线程把 App 干掉，表现就是「一打开就闪退」。
                // 现在吞掉异常：标准库没读到顶多搜索列表空，不该让整个 App 起不来。
                android.util.Log.e("QingJiApp", "初始化数据失败", e)
                AppLogger.e("Init", "DB_WRITE_FAILED", "启动初始化数据失败：${e.message}", e)
            }
        }
    }
}
