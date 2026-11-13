package com.qingji.app.diagnostics

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ============================================================
 * 诊断日志导出（任务书第五节）
 * ------------------------------------------------------------
 * 白名单制：ZIP 里只可能出现下面 4 个文件，别的一律不打包——
 *   · diagnostic_summary.json ：错误统计概要
 *   · app_info.json           ：应用版本信息
 *   · device_info.json        ：设备信息（仅制造商/型号/系统版本等 6 项）
 *   · error_logs.txt          ：错误日志（人类可读排版）
 *
 * 数据库、用户备份、照片、饮食/体重记录、API Key、完整 AI 请求正文
 * 严禁进包——白名单机制天然保证（任务书 5.2），不是靠导出时挑文件。
 * 日志在写入时已脱敏过一次，导出前对全部文本再脱敏一遍（双保险）。
 *
 * 导出后不自动上传任何地方，走系统分享面板让用户自己选发给谁（5.3）。
 * ============================================================
 */
object DiagnosticExporter {

    /** 导出成功的结果：文件 + 展示用信息。 */
    data class Output(val file: File, val entryCount: Int, val errorCount: Int, val fatalCount: Int, val warnCount: Int)

    /**
     * 生成诊断 ZIP。放 filesDir/diagnostics/（FileProvider 白名单里），
     * 每次导出先生成新文件，导出前把目录里的旧文件清掉，不堆积。
     */
    fun build(context: Context, entries: List<AppLogger.Entry>): Result<Output> = try {
        val exportTime = System.currentTimeMillis()
        val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        // 清旧文件：诊断包是一次性的，留最新的就够
        dir.listFiles()?.forEach { it.delete() }

        val appInfo = DiagnosticInfoProvider.appInfo(context, exportTime)
        val versionName = appInfo.optString("version_name")

        // ---------- 统计 ----------
        val errorCount = entries.count { it.level == AppLogger.Level.ERROR }
        val fatalCount = entries.count { it.level == AppLogger.Level.FATAL }
        val warnCount = entries.count { it.level == AppLogger.Level.WARN }

        val summary = JSONObject().apply {
            put("generated_at", AppLogger.dateFormat().format(java.util.Date(exportTime)))
            put("total_entries", entries.size)
            put("fatal_count", fatalCount)
            put("error_count", errorCount)
            put("warn_count", warnCount)
            put("whitelist", listOf("diagnostic_summary.json", "app_info.json", "device_info.json", "error_logs.txt"))
        }

        // ---------- error_logs.txt（人类可读，导出前再脱敏一遍） ----------
        val logText = buildString {
            if (entries.isEmpty()) {
                append("（最近没有错误日志）\n")
            } else {
                entries.forEach { e ->
                    append(AppLogger.dateFormat().format(java.util.Date(e.timestamp)))
                    append("  [").append(e.level.name).append("] ")
                    append(e.module).append(" · ").append(e.eventCode).append('\n')
                    append("  ").append(AppLogger.sanitize(e.message)).append('\n')
                    e.throwableType?.let { append("  异常类型：").append(it).append('\n') }
                    e.stackTrace?.let { append(AppLogger.sanitize(it)).append('\n') }
                    append('\n')
                }
            }
        }

        // ---------- 打 ZIP（只装上面四个，白名单硬编码） ----------
        val outFile = File(dir, "QingJi_Diagnostic_${versionName}_${AppLogger.dateFormat().format(java.util.Date(exportTime))}.zip")
        ZipOutputStream(outFile.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("diagnostic_summary.json"))
            zip.write(summary.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("app_info.json"))
            zip.write(appInfo.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("device_info.json"))
            zip.write(DiagnosticInfoProvider.deviceInfo().toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("error_logs.txt"))
            zip.write(logText.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        Result.success(Output(outFile, entries.size, errorCount, fatalCount, warnCount))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
