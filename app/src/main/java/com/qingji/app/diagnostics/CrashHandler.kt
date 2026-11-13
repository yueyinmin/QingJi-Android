package com.qingji.app.diagnostics

import android.content.Context

/**
 * ============================================================
 * 全局崩溃钩子（任务书 4.3）
 * ------------------------------------------------------------
 * 铁律：不能吞掉原始异常。装钩子只是「先把崩溃写进日志文件（同步写，
 * 因为进程马上要死）」，然后必须原样交给系统默认处理器走正常崩溃流程。
 * 绝不自己 System.exit、绝不吞异常装没事。
 * ============================================================
 */
object CrashHandler {

    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // ① 崩溃先落盘（同步：进程马上要死了，异步队列来不及跑）
            runCatching {
                if (!AppLogger.isInitialized) {
                    // AppLogger 还没 init 就崩了（极早期崩溃）：先补 init 再写
                    AppLogger.init(appContext)
                }
                AppLogger.log(
                    level = AppLogger.Level.FATAL,
                    module = "Crash",
                    eventCode = "UNCAUGHT_CRASH",
                    message = "线程「${thread.name}」未捕获异常",
                    t = throwable,
                    sync = true
                )
            }
            // ② 原样交还系统默认处理链：该弹的崩溃框、该杀的进程照常走
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
