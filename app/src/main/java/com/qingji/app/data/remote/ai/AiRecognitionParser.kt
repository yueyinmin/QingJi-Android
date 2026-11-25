package com.qingji.app.data.remote.ai

import com.qingji.app.data.remote.AiException
import com.qingji.app.data.remote.AiFoodItem
import com.qingji.app.data.remote.AiRecognitionResult
import com.qingji.app.domain.AiResultRules
import com.qingji.app.domain.MealRules
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI 识别结果解析器。
 *
 * 从 AiClient 抽出，供兼容门面与 QingJiRepository 共用。
 * 这里只做结构化解析与数值清洗，不负责网络请求。
 */
object AiRecognitionParser {

    fun parse(content: String): AiRecognitionResult {
        val obj = JSONObject(extractJsonBlock(content))
        val needClarify = obj.optBoolean("need_clarify", false)
        val rawAdvice = obj.optString("advice", "")

        val items = mutableListOf<AiFoodItem>()
        val notes = mutableListOf<String>()
        val array = obj.optJSONArray("items") ?: JSONArray()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name", "").trim()
            if (name.isEmpty()) continue

            val rawGrams = item.optDouble("grams", AiResultRules.DEFAULT_GRAMS)
            val rawKcal = item.optDouble("kcal", 0.0)
            val grams = AiResultRules.saneGrams(rawGrams)
            var confidence = AiResultRules.saneConfidence(item.optDouble("confidence", 0.5))

            val overKcal = rawKcal.isFinite() && rawGrams > 0.0 &&
                rawKcal / rawGrams * 100.0 > AiResultRules.MAX_KCAL_PER_100G
            if (overKcal) {
                confidence = minOf(confidence, AiResultRules.SUSPECT_CONFIDENCE)
                notes.add("「$name」每 100 克热量超出常识，已按上限修正")
            }

            items.add(
                AiFoodItem(
                    name = name,
                    amountText = item.optString("amount_text", ""),
                    grams = grams,
                    kcal = AiResultRules.saneKcal(rawKcal, grams),
                    proteinG = AiResultRules.saneMacro(item.optDouble("protein_g", 0.0)),
                    carbG = AiResultRules.saneMacro(item.optDouble("carb_g", 0.0)),
                    fatG = AiResultRules.saneMacro(item.optDouble("fat_g", 0.0)),
                    confidence = confidence
                )
            )
        }

        val advice = withSuspectNotes(rawAdvice, notes)

        if (items.isEmpty() && AiResultRules.isLegalEmptyResult(array.length(), needClarify)) {
            return AiRecognitionResult(
                mealType = mealTypeOf(obj),
                items = emptyList(),
                totalKcal = 0.0,
                advice = advice.ifBlank { "没看清楚吃了什么，补充一句再试一次吧" },
                needClarify = true
            )
        }
        if (items.isEmpty()) {
            throw AiException("AI 没拆出食物条目，换个说法或改用手动搜索")
        }

        return AiRecognitionResult(
            mealType = mealTypeOf(obj),
            items = items,
            totalKcal = items.sumOf { it.kcal },
            advice = advice,
            needClarify = needClarify
        )
    }

    private fun extractJsonBlock(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw AiException("AI 返回的不是 JSON，无法自动拆条：${text.take(120)}")
        }
        return text.substring(start, end + 1)
    }

    private fun mealTypeOf(obj: JSONObject): String =
        obj.optString("meal_type", "").takeIf { MealRules.isValid(it) } ?: MealRules.guess()

    private fun withSuspectNotes(advice: String, notes: List<String>): String {
        if (notes.isEmpty()) return advice
        val head = notes.take(2).joinToString("；")
        val tail = if (notes.size > 2) "（共 ${notes.size} 条）" else ""
        val prefix = if (advice.isBlank()) "" else advice.trim() + "\n"
        return "$prefix⚠ $head$tail，请核对后再保存"
    }
}
