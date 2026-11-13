package com.qingji.app.diagnostics

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * ============================================================
 * 日志文件读写（任务书 4.3）
 * ------------------------------------------------------------
 * 存储约定：
 *   · 位置：应用私有目录 filesDir/logs/（不申请任何存储权限）；
 *   · 格式：JSON Lines——一行一条日志，坏一行不影响其它行（防「日志文件损坏」）；
 *   · 当前写 log-current.txt，超过 1MB 轮换成 log-<时间戳>.txt；
 *   · 所有内容在写入前先过 AppLogger.sanitize 脱敏（第一道防线）。
 *
 * 写入走单线程后台执行器，主线程调用只入队不阻塞；只有崩溃那条走
 * 同步写（进程马上要死了，异步来不及落盘）。
 * ============================================================
 */
class LogFileWriter(context: Context) {

    private val dir: File = File(context.filesDir, "logs").apply { mkdirs() }
    private val currentFile: File get() = File(dir, "log-current.txt")

    /** 单文件上限 1MB（任务书 4.3）。 */
    private val maxFileBytes = 1L * 1024 * 1024

    /** 同步追加一行（JSON 已由 AppLogger 组好）。崩溃落盘走这条路。 */
    fun writeLineSync(line: String) {
        rotateIfNeeded()
        currentFile.appendText(line + "\n")
    }

    /** 读取全部条目（各文件按时间从旧到新），坏行跳过不抛错。 */
    fun readEntries(): List<JSONObject> {
        val files = allFiles()
        val out = mutableListOf<JSONObject>()
        files.forEach { f ->
            f.readLines().forEach { line ->
                if (line.isBlank()) return@forEach
                try {
                    out.add(JSONObject(line))
                } catch (_: Exception) {
                    // 坏行跳过（对应任务书「日志文件损坏」场景：不影响其它行）
                }
            }
        }
        return out
    }

    /** 清空全部日志文件。 */
    fun clearAll() {
        allFiles().forEach { it.delete() }
    }

    /** 日志目录总占用字节数（给清理策略和 UI 用）。 */
    fun totalBytes(): Long = allFiles().sumOf { it.length() }

    fun allFiles(): List<File> =
        dir.listFiles { f -> f.name.startsWith("log-") && f.name.endsWith(".txt") }
            ?.sortedBy { it.name } ?: emptyList()

    /** 当前文件超过 1MB 就轮换：改名加时间戳，新日志写新文件。 */
    private fun rotateIfNeeded() {
        val cur = currentFile
        if (cur.exists() && cur.length() >= maxFileBytes) {
            val stamp = AppLogger.dateFormat().format(java.util.Date())
            cur.renameTo(File(dir, "log-$stamp-${System.currentTimeMillis() % 10000}.txt"))
        }
    }
}
