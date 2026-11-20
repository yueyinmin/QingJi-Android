package com.qingji.app.domain

/**
 * ============================================================
 * 条码规则 + 开放食品库返回解析（M3.4）
 * ------------------------------------------------------------
 * 只用纯 Kotlin 的字符串处理，不引 org.json（那是安卓框架类，
 * 电脑上跑不了单元测试）。OpenFoodFacts 的返回结构很固定，
 * 用正则取几个字段足够，少一个字段也能安全兜底。
 * ============================================================
 */
object BarcodeRules {

    /** 从商品条码查到的营养信息，单位是「每 100 克 / 100 毫升」。 */
    data class Product(
        val name: String,
        val kcalPer100g: Double,
        val proteinPer100g: Double,
        val carbPer100g: Double,
        val fatPer100g: Double
    )

    /** 去掉空格、横杠，只保留数字；扫出来带前缀之类也一并清干净。 */
    fun normalize(raw: String): String = raw.trim().filter { it.isDigit() }

    /** EAN-13 校验位：前 12 位按 1/3 交替加权，取补数到 10。 */
    fun checksum(code12: String): Int {
        if (code12.length != 12 || code12.any { !it.isDigit() }) return -1
        val sum = code12.mapIndexed { i, c ->
            val digit = c - '0'
            if (i % 2 == 0) digit else digit * 3
        }.sum()
        return (10 - sum % 10) % 10
    }

    /** 校验完整的 13 位条码。国内常见 EAN-13，也兼容 8 位 EAN-8。 */
    fun isValidEan13(code: String): Boolean {
        val c = normalize(code)
        if (c.length != 13) return false
        return checksum(c.take(12)) == (c.last() - '0')
    }

    /**
     * 从 OpenFoodFacts 的 JSON 文本里抠出需要的字段。
     * 返回 null 表示「没收录 / 字段残缺」，界面会提示手动录入。
     */
    fun parseOpenFoodFacts(json: String): Product? {
        if (!json.contains("\"status\"") || !json.contains("\"product\"")) return null
        // status 为 0 表示查无此商品
        val status = numberAfter(json, "\"status\"")?.toInt() ?: return null
        if (status != 1) return null

        val name = stringAfter(json, "\"product_name\"")
            ?: stringAfter(json, "\"product_name_zh\"")
            ?: return null

        // 有些商品只给 kJ，这里换算成 kcal（1 kcal ≈ 4.184 kJ）
        val kcal = numberAfter(json, "\"energy-kcal_100g\"")
            ?: numberAfter(json, "\"energy-kj_100g\"")?.let { it / 4.184 }
            ?: return null

        return Product(
            name = name,
            kcalPer100g = kcal,
            proteinPer100g = numberAfter(json, "\"proteins_100g\"") ?: 0.0,
            carbPer100g = numberAfter(json, "\"carbohydrates_100g\"") ?: 0.0,
            fatPer100g = numberAfter(json, "\"fat_100g\"") ?: 0.0
        )
    }

    /** 找到 key 后面冒号的位置，跳过空白返回第一个有效字符的下标。 */
    private fun valueStart(text: String, key: String): Int {
        val keyAt = text.indexOf(key)
        if (keyAt < 0) return -1
        val colon = text.indexOf(':', keyAt + key.length)
        if (colon < 0) return -1
        var i = colon + 1
        while (i < text.length && text[i].isWhitespace()) i++
        return if (i < text.length) i else -1
    }

    /** 取 "key": 数字 里的数字，支持负数和小数。手写扫描，不用正则转义。 */
    private fun numberAfter(text: String, key: String): Double? {
        val start = valueStart(text, key)
        if (start < 0) return null
        val sb = StringBuilder()
        var i = start
        while (i < text.length) {
            val c = text[i]
            if (c.isDigit() || c == '-' || c == '.' || c == 'e' || c == 'E' || c == '+') {
                sb.append(c)
                i++
            } else break
        }
        return sb.toString().toDoubleOrNull()
    }

    /** 取 "key": "字符串" 里的字符串，顺手处理 \" 和 \n。 */
    private fun stringAfter(text: String, key: String): String? {
        val start = valueStart(text, key)
        if (start < 0 || text[start] != '"') return null
        val sb = StringBuilder()
        var i = start + 1
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length -> {
                    val next = text[i + 1]
                    sb.append(if (next == 'n') ' ' else next)
                    i += 2
                }
                c == '"' -> break
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString().trim().takeIf { it.isNotBlank() }
    }
}
