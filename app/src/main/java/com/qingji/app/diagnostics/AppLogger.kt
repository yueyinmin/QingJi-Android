package com.qingji.app.diagnostics

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * ============================================================
 * 统一日志入口（任务书 4.1 / 4.2）
 * ------------------------------------------------------------
 * 全 App 只用这一个 logger。特点：
 *   · 五个等级 DEBUG / INFO / WARN / ERROR / FATAL；
 *   · 每条带：时间戳、等级、模块、事件代码、简短说明、异常类型、脱敏堆栈；
 *   · 写入前先脱敏（第一道防线；导出时还会再脱一遍，双保险）；
 *   · 内存里留最近 300 条给「错误日志」页秒开，文件是完整档案；
 *   · 后台单线程异步写，主线程不阻塞。
 *
 * 事件代码约定（任务书 4.2 建议命名）：
 *   DB_WRITE_FAILED / AI_REQUEST_TIMEOUT / BACKUP_PARSE_FAILED /
 *   WIDGET_LOAD_FAILED / UPDATE_DOWNLOAD_FAILED / UNCAUGHT_CRASH ...
 * ============================================================
 */
object AppLogger {

    enum class Level { DEBUG, INFO, WARN, ERROR, FATAL }

    /** 一条日志的完整结构（存文件、进 UI、进诊断包都是它）。 */
    data class Entry(
        val timestamp: Long,
        val level: Level,
        val module: String,
        val eventCode: String,
        val message: String,
        val throwableType: String?,
        val stackTrace: String?
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("ts", timestamp)
            put("level", level.name)
            put("module", module)
            put("code", eventCode)
            put("msg", message)
            throwableType?.let { put("type", it) }
            stackTrace?.let { put("stack", it) }
        }

        companion object {
            fun fromJson(obj: JSONObject): Entry = Entry(
                timestamp = obj.optLong("ts"),
                level = runCatching { Level.valueOf(obj.optString("level")) }.getOrDefault(Level.INFO),
                module = obj.optString("module"),
                eventCode = obj.optString("code"),
                message = obj.optString("msg"),
                throwableType = obj.optString("type").ifBlank { null },
                stackTrace = obj.optString("stack").ifBlank { null }
            )
        }
    }

    private const val MEMORY_CAP = 300
    private const val STACK_MAX_CHARS = 1500   // 堆栈只留前 1500 字，防日志爆炸

    private lateinit var writer: LogFileWriter
    private val executor = Executors.newSingleThreadExecutor()
    private val memory = ArrayDeque<Entry>()
    @Volatile private var initialized = false

    /** CrashHandler 等外部模块判断「init 跑过没有」。 */
    val isInitialized: Boolean get() = initialized

    /** 在 QingJiApp.onCreate 里调用：装好崩溃钩子 + 清理过期日志。 */
    fun init(context: Context) {
        if (initialized) return
        writer = LogFileWriter(context.applicationContext)
        initialized = true
        CrashHandler.install(context.applicationContext)
        executor.execute { LogRotationManager.cleanup(writer) }
    }

    // ---------- 便捷入口 ----------

    fun i(module: String, eventCode: String, message: String) =
        log(Level.INFO, module, eventCode, message)

    fun w(module: String, eventCode: String, message: String) =
        log(Level.WARN, module, eventCode, message)

    fun e(module: String, eventCode: String, message: String, t: Throwable? = null) =
        log(Level.ERROR, module, eventCode, message, t)

    /** 主入口：所有日志都从这走（先脱敏，再进内存 + 异步落盘）。 */
    fun log(
        level: Level,
        module: String,
        eventCode: String,
        message: String,
        t: Throwable? = null,
        /** 崩溃场景要同步写（进程马上死，异步来不及）；平时异步。 */
        sync: Boolean = false
    ) {
        if (!initialized) return   // init 之前的日志丢弃（比如单元测试里），不崩
        val entry = Entry(
            timestamp = System.currentTimeMillis(),
            level = level,
            module = module.take(24),
            eventCode = eventCode.take(48),
            message = sanitize(message).take(500),
            throwableType = t?.javaClass?.simpleName,
            stackTrace = t?.let { sanitize(it.stackTraceToString()).take(STACK_MAX_CHARS) }
        )
        synchronized(memory) {
            memory.addLast(entry)
            while (memory.size > MEMORY_CAP) memory.removeFirst()
        }
        val line = entry.toJson().toString()
        if (sync) writer.writeLineSync(line) else executor.execute { writer.writeLineSync(line) }
    }

    /** 给「错误日志」页用的内存快照（新的在前）。 */
    fun recent(): List<Entry> = synchronized(memory) { memory.toList().asReversed() }

    /** 从日志文件读全量（新的在前），坏行已被 LogFileWriter 跳过。 */
    fun readAllFromDisk(): List<Entry> =
        writer.readEntries().map { Entry.fromJson(it) }.asReversed()

    /** 清空全部（错误日志页「清空」按钮，UI 层负责二次确认）。 */
    fun clearAll() {
        synchronized(memory) { memory.clear() }
        executor.execute { writer.clearAll() }
    }

    fun totalBytesOnDisk(): Long = if (initialized) writer.totalBytes() else 0L

    // ---------- 脱敏（任务书 5.2：写入前过滤，不是只靠导出时替换） ----------

    /**
     * 把疑似敏感信息替换成 [已脱敏]。宁可错杀（假阳性只是日志少点信息），
     * 不可放过（API Key / Cookie 进日志包是事故）。
     */
    fun sanitize(text: String): String = text
        // Bearer / Authorization 头整体打码
        .replace(Regex("(?i)(authorization\\s*[:=]\\s*|bearer\\s+)[^\\s\"',}]+"), "$1[已脱敏]")
        // JSON 引号风格："api_key":"xxxx" —— 值被引号包住，普通规则吃不到，单独一条
        .replace(Regex("(?i)(\"(?:api[_-]?key|token|secret|password|cookie)\"\\s*:\\s*\")[^\"]*"), "$1[已脱敏]")
        // key=value / key: value 无引号风格
        .replace(Regex("(?i)((api[_-]?key|token|secret|password|cookie)\\s*[\"'=:]\\s*)[^\\s\"',}&]+"), "$1[已脱敏]")
        // OpenAI 风格 sk- 开头的长串（前面没打到的兜一层）
        .replace(Regex("\\bsk-[A-Za-z0-9_-]{16,}\\b"), "[已脱敏]")

    /** 文件名 / UI 里用的时间戳格式。 */
    fun dateFormat(): SimpleDateFormat =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
}
