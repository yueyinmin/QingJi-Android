package com.qingji.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File

/** 脱敏是纯字符串处理，不起 Android 环境。 */
class AppLoggerSanitizeTest {

    @Test
    fun `Bearer令牌打码`() {
        val out = AppLogger.sanitize("Authorization: Bearer sk-abc123def456ghi789jkl012")
        assertFalse(out.contains("sk-abc123"))
        assertTrue(out.contains("[已脱敏]"))
    }

    @Test
    fun `apiKey键值对打码`() {
        val out = AppLogger.sanitize("""{"api_key":"a1b2c3d4e5f6g7h8","city":"上海"}""")
        assertFalse(out.contains("a1b2c3d4e5f6g7h8"))
        assertTrue(out.contains("上海"))   // 无关内容不动
    }

    @Test
    fun `cookie打码`() {
        val out = AppLogger.sanitize("Cookie: sessionid=xyz789; other=1")
        assertFalse(out.contains("xyz789"))
    }

    @Test
    fun `普通消息不被误伤`() {
        val msg = "备份导出成功：qingji-backup-20261113.json（120KB）"
        assertEquals(msg, AppLogger.sanitize(msg))
    }
}

/** 文件读写、坏行容错、轮换、清理（Robolectric 起真文件系统）。 */
@RunWith(RobolectricTestRunner::class)
class LogFileWriterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `写入能读回且坏行跳过`() {
        val writer = LogFileWriter(context)
        writer.clearAll()

        val good1 = AppLogger.Entry(1L, AppLogger.Level.ERROR, "DB", "DB_WRITE_FAILED", "写入失败", "SQLException", "at ...")
        val good2 = AppLogger.Entry(2L, AppLogger.Level.WARN, "Net", "AI_REQUEST_TIMEOUT", "超时", null, null)
        writer.writeLineSync(good1.toJson().toString())
        writer.writeLineSync("这不是JSON的坏行")
        writer.writeLineSync(good2.toJson().toString())

        val entries = writer.readEntries()
        assertEquals(2, entries.size)   // 坏行被跳过
        assertEquals("DB_WRITE_FAILED", entries[0].optString("code"))
    }

    @Test
    fun `超过1MB自动轮换`() {
        val writer = LogFileWriter(context)
        writer.clearAll()
        // 写约 1.6MB 触发轮换（1MB = 1048576 字节，写 6 行 1.2MB 也够，多写两行留余量）
        val big = "x".repeat(200_000)
        repeat(8) { writer.writeLineSync("""{"ts":1,"level":"INFO","module":"T","code":"T","msg":"$big"}""") }
        val files = writer.allFiles()
        assertTrue("应产生轮换文件", files.size >= 2)
        assertTrue(files.any { it.name != "log-current.txt" })
        writer.clearAll()
    }

    @Test
    fun `清空后没有文件`() {
        val writer = LogFileWriter(context)
        writer.writeLineSync("""{"ts":1,"level":"INFO","module":"T","code":"T","msg":"m"}""")
        writer.clearAll()
        assertTrue(writer.readEntries().isEmpty())
    }
}

/** 崩溃钩子：日志要落盘、系统默认处理链必须被调用（不吞异常）。 */
@RunWith(RobolectricTestRunner::class)
class CrashHandlerTest {

    @Test
    fun `崩溃日志落盘且默认处理链被调用`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        AppLogger.init(context)

        var defaultCalled = false
        val fakeDefault = Thread.UncaughtExceptionHandler { _, _ -> defaultCalled = true }
        // 先把假默认处理器挂上，再装我们的钩子（钩子会捕获它作为「系统默认链」）
        Thread.setDefaultUncaughtExceptionHandler(fakeDefault)
        CrashHandler.install(context)

        val hook = Thread.getDefaultUncaughtExceptionHandler()!!
        val boom = RuntimeException("测试崩溃")
        hook.uncaughtException(Thread.currentThread(), boom)   // 手动触发，不是真崩

        assertTrue("默认处理链必须被调用", defaultCalled)
        val fatal = AppLogger.readAllFromDisk().filter { it.eventCode == "UNCAUGHT_CRASH" }
        assertTrue("崩溃日志必须落盘", fatal.isNotEmpty())
        assertEquals("RuntimeException", fatal.first().throwableType)
    }
}
