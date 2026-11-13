package com.qingji.app.diagnostics

import java.io.File

/**
 * ============================================================
 * 日志轮换与过期清理（任务书 4.3：防日志无限增长）
 * ------------------------------------------------------------
 * 策略：
 *   · 总空间约 5MB：超了就从最老的轮换文件开始删；
 *   · 保留最近 14 天：更老的文件直接删；
 *   · 只清理「轮换文件」，不动 log-current.txt（今天正在写的）。
 * App 每次启动跑一次（AppLogger.init 里调用），不需要常驻。
 * ============================================================
 */
object LogRotationManager {

    private const val TOTAL_CAP_BYTES = 5L * 1024 * 1024   // 总量约 5MB
    private const val KEEP_DAYS = 14L                       // 保留最近 14 天

    fun cleanup(writer: LogFileWriter) {
        val files = writer.allFiles().filter { it.name != "log-current.txt" }
        val now = System.currentTimeMillis()
        val keepMs = KEEP_DAYS * 24 * 60 * 60 * 1000

        // ① 先删过期文件（按文件最后修改时间）
        val fresh = files.filter { f ->
            if (now - f.lastModified() > keepMs) {
                f.delete()
                false
            } else {
                true
            }
        }

        // ② 还超总量：从最老的开始删，删到不超为止
        var total = fresh.sumOf { it.length() } + currentSize(writer)
        val byOldest = fresh.sortedBy { it.lastModified() }
        for (f in byOldest) {
            if (total <= TOTAL_CAP_BYTES) break
            val size = f.length()
            if (f.delete()) total -= size
        }
    }

    private fun currentSize(writer: LogFileWriter): Long =
        writer.allFiles().firstOrNull { it.name == "log-current.txt" }?.length() ?: 0L
}
