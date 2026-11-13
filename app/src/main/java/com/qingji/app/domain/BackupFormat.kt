package com.qingji.app.domain

/**
 * ============================================================
 * 备份文件的格式规则（M9）
 * ------------------------------------------------------------
 * 这里只做「纯文本怎么拼」这件事，不碰文件、不碰安卓，
 * 所以在电脑上用 JUnit 就能测（真的写过 3 个坑：逗号、引号、换行）。
 *
 * 两个格式：
 * 1. CSV：Excel 能直接打开。规则是「有逗号/引号/换行就整格加双引号，
 *    里面的双引号写成两个」—— 这是 RFC 4180 定的，Excel 认这个。
 * 2. JSON：全量备份。字符串里的 \ " 换行 制表符必须转义，
 *    否则备份文件自己就坏了，导入时全丢。
 * ============================================================
 */
object BackupFormat {

    /**
     * 备份文件版本号。
     *
     * 1 = v1.0.5 及以前（只有档案/体重/自建食物/饮食/饮水/日记/统计/AI配置）
     * 2 = v1.0.6 起，多五段：围度 bodyMeasures、照片 photos、运动 exercises、
     *     断食 fastings、自建运动 exercisePresets
     * 3 = 训练中心起，多一段 training（动作库/计划 3 表/训练会话 2 表，共 6 张表）
     *
     * 导入端基本不按版本号分支：新段缺失就当空列表，所以 v3 能直接读 v1/v2 的备份。
     * 唯一例外是训练段：version < 3 的老备份**不清本机训练表**（那时还没有这个模块），
     * 免得导旧备份把新记的训练弄丢；version >= 3 才按 training 段整包覆盖。
     */
    const val VERSION = 3

    /** CSV 表头，顺序不能乱，Excel 里看到的列顺序就是它。 */
    val CSV_HEADER = listOf(
        "日期", "餐次", "食物", "克数", "热量(千卡)",
        "蛋白(g)", "碳水(g)", "脂肪(g)", "来源"
    )

    /** 一条饮食记录转成 CSV 需要的纯数据（不带 Room 注解，测试好造）。 */
    data class CsvRow(
        val date: String,
        val meal: String,
        val name: String,
        val grams: String,
        val kcal: String,
        val protein: String,
        val carb: String,
        val fat: String,
        val source: String
    )

    /**
     * 单个格子转义。
     * 只要含逗号 / 双引号 / 换行 / 回车，就整体加引号并把内部引号翻倍。
     */
    fun csvEscape(raw: String): String {
        val needsQuote = raw.contains(',') || raw.contains('"') ||
            raw.contains('\n') || raw.contains('\r')
        if (!needsQuote) return raw
        return "\"" + raw.replace("\"", "\"\"") + "\""
    }

    /** 拼整份 CSV，行尾统一用 \r\n（Excel 最不容易出问题）。 */
    fun foodLogCsv(rows: List<CsvRow>): String {
        val sb = StringBuilder()
        sb.append(CSV_HEADER.joinToString(",") { csvEscape(it) }).append("\r\n")
        rows.forEach { r ->
            sb.append(
                listOf(
                    r.date, r.meal, r.name, r.grams, r.kcal,
                    r.protein, r.carb, r.fat, r.source
                ).joinToString(",") { csvEscape(it) }
            ).append("\r\n")
        }
        return sb.toString()
    }

    /** JSON 字符串体转义（不含外层引号）。 */
    fun escapeJson(raw: String): String {
        val sb = StringBuilder(raw.length + 8)
        raw.forEach { c ->
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') {
                    sb.append(String.format("\\u%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        return sb.toString()
    }

    /** 直接拼出一个带引号的 JSON 字符串值。 */
    fun jsonString(raw: String): String = "\"" + escapeJson(raw) + "\""

    /** null 安全版：null 输出 JSON 的 null 字面量。 */
    fun jsonStringOrNull(raw: String?): String =
        if (raw == null) "null" else jsonString(raw)

    /**
     * 备份文件名：带日期时间，导出多次不会互相覆盖。
     * 例：qingji-backup-20261107-2215.json
     */
    fun backupFileName(prefix: String, ext: String, stamp: String): String =
        "qingji-$prefix-$stamp.$ext"

    /** 时间戳格式：20261107-2215（本地时间，不含冒号，Windows 文件名合法）。 */
    fun stamp(now: java.time.LocalDateTime): String =
        String.format(
            "%04d%02d%02d-%02d%02d",
            now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute
        )
}
