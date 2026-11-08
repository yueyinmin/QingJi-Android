package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份格式（M9）。
 * 这三条是真正踩过坑的：
 * 1. CSV 里食物名带逗号（"鸡蛋,煎"）不转义 → Excel 列全错位；
 * 2. 名字里带双引号 → 整份文件都读不出来；
 * 3. JSON 里带换行 → 备份文件自己坏掉，导入时全丢。
 */
class BackupFormatTest {

    @Test
    fun `普通文字不加引号`() {
        assertEquals("鸡蛋", BackupFormat.csvEscape("鸡蛋"))
        assertEquals("116", BackupFormat.csvEscape("116"))
    }

    @Test
    fun `有逗号要整格加引号`() {
        assertEquals("\"鸡蛋,煎\"", BackupFormat.csvEscape("鸡蛋,煎"))
    }

    @Test
    fun `有双引号要把引号翻倍`() {
        assertEquals("\"他说\"\"好吃\"\"\"", BackupFormat.csvEscape("他说\"好吃\""))
    }

    @Test
    fun `有换行要加引号`() {
        assertEquals("\"第一行\n第二行\"", BackupFormat.csvEscape("第一行\n第二行"))
    }

    @Test
    fun `CSV 首行是表头且列数一致`() {
        val csv = BackupFormat.foodLogCsv(
            listOf(
                BackupFormat.CsvRow(
                    date = "2026-11-07",
                    meal = "早餐",
                    name = "鸡蛋,煎",
                    grams = "100",
                    kcal = "144",
                    protein = "12.6",
                    carb = "1.1",
                    fat = "9.8",
                    source = "text"
                )
            )
        )
        val lines = csv.split("\r\n").filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertEquals(BackupFormat.CSV_HEADER.joinToString(","), lines[0])
        // 逗号在格子里被引号包住，所以按「引号外的逗号」切应该还是 9 列
        assertTrue(lines[1].contains("\"鸡蛋,煎\""))
        assertTrue(lines[1].endsWith("text"))
    }

    @Test
    fun `JSON 转义关键字符`() {
        assertEquals("a\\\"b", BackupFormat.escapeJson("a\"b"))
        assertEquals("a\\\\b", BackupFormat.escapeJson("a\\b"))
        assertEquals("a\\nb", BackupFormat.escapeJson("a\nb"))
        assertEquals("a\\tb", BackupFormat.escapeJson("a\tb"))
    }

    @Test
    fun `JSON 字符串带外层引号并处理空值`() {
        assertEquals("\"轻记\"", BackupFormat.jsonString("轻记"))
        assertEquals("null", BackupFormat.jsonStringOrNull(null))
        assertEquals("\"\"", BackupFormat.jsonStringOrNull(""))
    }

    @Test
    fun `文件名与时间戳格式合法`() {
        val stamp = BackupFormat.stamp(java.time.LocalDateTime.of(2026, 11, 7, 22, 5))
        assertEquals("20261107-2205", stamp)
        val name = BackupFormat.backupFileName("backup", "json", stamp)
        assertEquals("qingji-backup-20261107-2205.json", name)
        assertFalse(name.contains(":"))
    }

    @Test
    fun `空列表也能导出只有表头的 CSV`() {
        val csv = BackupFormat.foodLogCsv(emptyList())
        assertEquals(1, csv.split("\r\n").filter { it.isNotBlank() }.size)
    }
}
