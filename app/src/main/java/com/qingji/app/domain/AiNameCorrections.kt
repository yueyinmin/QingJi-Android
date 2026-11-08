package com.qingji.app.domain

/**
 * AI 识别「名字纠错」的轻量持久化（v3.4.0）。
 *
 * 场景：AI 老把「地锅炖」叫成别的名字，用户在确认页手动改了一次；
 * 下次再识别到同样的原名，应该直接显示用户改过的名字，也要用改后的名字
 * 去本地食物库匹配营养值。
 *
 * 为什么不建 Room 表：这只是「几个字符串映射」，建表 = version 4→5 + Migration +
 * schema 文件逐字核对，风险远大于收益。存进已有的 DataStore 一个字符串即可。
 *
 * 存储格式：每行一条 `AI原名=>用户改后名`，换行分隔。
 * 名字里理论上不会出现 `=>`，不用引 JSON，解析和写入都是纯函数，方便单测。
 */
object AiNameCorrections {

    private const val SEP = "=>"

    /** 解析；空行、没有分隔符、两侧空字符串的行一律忽略，坏数据不会让 App 崩。 */
    fun parse(raw: String): Map<String, String> =
        raw.lines()
            .map { it.trim() }
            .filter { it.contains(SEP) }
            .mapNotNull { line ->
                val parts = line.split(SEP, limit = 2)
                val from = parts[0].trim()
                val to = parts[1].trim()
                if (from.isEmpty() || to.isEmpty() || from == to) null else from to to
            }
            .toMap()

    /** 转回可存储文本；按原名排序，保证同样的映射写出来的内容一致。 */
    fun encode(map: Map<String, String>): String =
        map.toSortedMap().entries.joinToString("\n") { "${it.key}$SEP${it.value}" }

    /** 新增 / 覆盖一条映射；原名与改后名相同或为空时不记录。 */
    fun put(raw: String, from: String, to: String): String {
        val f = from.trim()
        val t = to.trim()
        if (f.isEmpty() || t.isEmpty() || f == t) return raw
        val map = parse(raw).toMutableMap()
        map[f] = t
        return encode(map)
    }

    /** 把 AI 给的名字换成用户改过的名字；没有映射就原样返回。 */
    fun apply(raw: String, name: String): String {
        val key = name.trim()
        if (raw.isBlank() || key.isEmpty()) return name
        return parse(raw)[key] ?: name
    }
}
