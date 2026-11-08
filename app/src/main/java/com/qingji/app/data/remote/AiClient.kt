package com.qingji.app.data.remote

import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.domain.MealRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * ============================================================
 * AI 识别客户端（需求文档第 5 节）
 * ------------------------------------------------------------
 * 只支持 OpenAI 兼容协议：POST {BaseURL}/chat/completions
 * 国内主流模型（DeepSeek / 通义 / 豆包 / Kimi / 智谱 / 硅基流动）
 * 都用这一套，所以一份代码通吃。
 *
 * 已经处理的坑：
 * 1. 部分国内模型不支持 response_format → 400 时自动去掉该字段重试一次
 * 2. 模型爱在 JSON 外面套 markdown 代码块 → 从文本里抠第一个 {...} 再解析
 * 3. 401 / 429 / 超时 / 余额不足 → 全部翻成中文人话
 * 4. 请求体、返回体里的字段都做空值兜底，模型少给一个字段也不崩
 * ============================================================
 */

/** AI 返回的一个食物条目（对应需求 5.4 的 JSON 结构）。 */
data class AiFoodItem(
    val name: String,
    val amountText: String,
    val grams: Double,
    val kcal: Double,
    val proteinG: Double,
    val carbG: Double,
    val fatG: Double,
    val confidence: Double
)

/** 一次识别的完整结果。 */
data class AiRecognitionResult(
    val mealType: String,
    val items: List<AiFoodItem>,
    val totalKcal: Double,
    val advice: String,
    val needClarify: Boolean
)

/** 专门用来把错误翻成人话的异常。 */
class AiException(message: String) : Exception(message)

class AiClient {

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /**
         * 文字识别的系统提示词。
         *
         * 为什么写这么长：模型（尤其免费的小模型）份量估得准不准，
         * 几乎全靠提示词里给的参照物。不给参照物，它只能凭感觉，
         * 同一句「一碗米饭」今天估 100g、明天估 250g。
         * 所以这里把常见中餐的份量、拆解规则、算热量的公式全写死，
         * 再给一个完整示例（few-shot），让小模型照着抄结构。
         */
        private val SYSTEM_PROMPT = """
【语言（最高优先级）】
所有输出内容——食物名称 name、说明 advice、need_clarify 的追问——必须使用**简体中文**。
照片里是外国菜也要翻译成常用中文名（例：stir-fried vegetables and tofu → 「炒蔬菜豆腐」）。
除非用户消息本身是英文，才允许跟着用英文。
你是专业的中国饮食份量估算助手。用户会用口语说吃了什么，你要拆成一条条食物记录。

【份量参考】下面都是可食用部分的常见份量，用户没说清就按这个估：
- 米饭：一碗 150g（小碗 100g，大碗 200g）
- 面食：一个馒头 100g，一根油条 70g，一个包子 80g，一碗面条（熟）350g
- 蛋奶：一个鸡蛋（去壳）50g，一杯牛奶 250g，一杯豆浆 250g
- 粥汤：一碗粥 300g，一碗汤 250g
- 炒菜：一盘家常炒菜约 250g，其中主料 150g、肉 80g、食用油 8g
- 荤菜：一份红烧肉 100g，一个鸡腿（去骨）80g，一份鱼 120g
- 水果：一个苹果 200g，一根香蕉 100g，一个橘子 120g，一份水果拼盘 200g
- 零食：一包薯片 70g，一块饼干 10g，一把坚果 25g

【拆解规则（很重要）】
1. **一道菜就是一条，不要自己拆成原料。**
   - 用户说「番茄炒蛋」→ 只输出一条「番茄炒蛋」，不要拆成番茄+鸡蛋+油。
   - 用户说「盖浇饭」「牛肉面」「炒饭」→ 一条整体记录，不要把饭和菜分开。
   - 只有当用户自己分开说（「一盘番茄炒蛋，另加一碗米饭」）时，才分成两条。
   - 理由：拆开会让用户看到一堆原料，和他实际吃的那道菜对不上。
2. 用户说「两碗」「三片」这类数量要乘上去，不要只算一份。
3. amount_text 填用户的原始说法（如「一碗」「两片」）；grams 填你估算的克数，用整数。
4. 做法不明按该菜最常见的做法估算（炒菜按家常炒、盖浇饭按常规配比），不要假设额外加油。
5. 用户说「斤」按 500g 换算，说「两」按 50g 换算。

【热量规则】
6. kcal、protein_g、carb_g、fat_g 按「每 100 克营养值 × 克数 ÷ 100」算，保留 1 位小数。
7. 估不准的条目把 confidence 降到 0.5 以下，但 grams 仍要给出合理值，不要留空。

【输出要求】
8. 只输出 JSON，不要解释文字，不要 markdown 代码块。
9. 完全看不出吃了什么时，items 给空数组、need_clarify 置 true，并在 advice 里写出你想问的问题。
10. 禁止给出任何医疗、用药、疾病、减肥药相关建议。

示例输入：中午吃了一份番茄牛肉盖浇饭
示例输出：
{"meal_type":"lunch","items":[{"name":"番茄牛肉盖浇饭","amount_text":"一份","grams":450,"kcal":620.0,"protein_g":24.0,"carb_g":78.0,"fat_g":20.0,"confidence":0.7}],"total_kcal":620.0,"advice":"","need_clarify":false}

注意上面这个例子的关键点：**「盖浇饭」只输出一条**，没有拆成米饭+番茄+牛肉。

输出 JSON 结构：
{ "meal_type": "breakfast|lunch|dinner|snack", "items": [{ "name": "", "amount_text": "", "grams": 0,
  "kcal": 0, "protein_g": 0, "carb_g": 0, "fat_g": 0, "confidence": 0 }],
  "total_kcal": 0, "advice": "", "need_clarify": false }
""".trimIndent()

        /** 图片识别的系统提示词：在文字版基础上加「看照片估重量」的要求。 */
        private val VISION_PROMPT = """
【语言（最高优先级）】所有输出（name、advice）必须使用简体中文，外国菜翻译成常用中文名。

你是一个专业的营养记录助手，你会收到一张食物照片。先判断这是什么食物，
再参考碗、盘子、筷子、勺子的比例估算可食用部分的重量，然后输出。

【份量参考】一碗米饭 150g，一盘家常炒菜 250g，一个鸡蛋 50g，一杯牛奶 250g，
一碗粥 300g，一份红烧肉 100g，一个苹果 200g。照片里的份量明显偏离这些值时按肉眼看到的比例调整。

【规则】
1. 只输出 JSON，不要解释文字，不要 markdown 代码块。
2. 一道菜就是一条，不要拆成原料；照片里明显是分开的几盘菜才分成几条。
3. kcal 等营养值按「每 100 克营养值 × 克数 ÷ 100」算，保留 1 位小数。
4. 看不清、被遮挡、拿不准的条目把 confidence 降到 0.5 以下，但 grams 仍要给合理值。
5. 照片里没有食物时，items 输出空数组，并在 advice 里说明。
6. 禁止给出任何医疗、用药、疾病相关建议。

输出 JSON 结构：
{ "meal_type": "breakfast|lunch|dinner|snack", "items": [{ "name": "", "amount_text": "", "grams": 0,
  "kcal": 0, "protein_g": 0, "carb_g": 0, "fat_g": 0, "confidence": 0 }],
  "total_kcal": 0, "advice": "", "need_clarify": false }
""".trimIndent()


        /** 复盘/周报的通用约束：只说人话，不碰医疗建议（合规红线）。 */
        private val ANALYSIS_RULE =
            "你是饮食记录助手。只根据下面给出的数字做描述和建议，用中文口语，控制在 150 字以内。" +
                "禁止出现诊断、治疗、用药、疾病、处方一类表述，也不要编造没给出的数字。"
    }

    private fun endpoint(config: AiConfig): String {
        val raw = config.baseUrl.trim().trimEnd('/')
        // 有人习惯填到 /v1，也有人把完整地址粘进来，两种都兼容
        return if (raw.endsWith("/chat/completions")) raw else "$raw/chat/completions"
    }

    private fun httpClient(config: AiConfig): OkHttpClient {
        val seconds = config.timeoutSec.coerceIn(5, 120).toLong()
        return OkHttpClient.Builder()
            .connectTimeout(seconds, TimeUnit.SECONDS)
            .readTimeout(seconds, TimeUnit.SECONDS)
            .writeTimeout(seconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** 发请求前先检查必填项，别把空 Key 发出去浪费一次调用。 */
    private fun validate(config: AiConfig, key: String) {
        if (config.baseUrl.isBlank()) throw AiException("还没填 Base URL，去设置 → AI 服务配置")
        if (key.isBlank()) throw AiException("还没填 API Key，去设置 → AI 服务配置")
        if (config.textModel.isBlank()) throw AiException("还没填文本模型名，去设置 → AI 服务配置")
    }

    /**
     * 真正发一次请求。
     * 返回 Pair(HTTP 状态码, 返回文本)，错误不在这里抛，交给外层统一翻译。
     */
    private fun send(client: OkHttpClient, config: AiConfig, key: String, bodyJson: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(endpoint(config))
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            return response.code to (response.body?.string().orEmpty())
        }
    }

    /** 带「response_format 降级」的请求。 */
    private fun call(
        config: AiConfig,
        key: String,
        bodyJson: String,
        allowFallback: Boolean
    ): String {
        val client = httpClient(config)
        var result = send(client, config, key, bodyJson)

        // 国内有些模型不认 response_format，返回 400。去掉这个字段再试一次。
        if (result.first == 400 && allowFallback && JSONObject(bodyJson).has("response_format")) {
            val withoutFormat = JSONObject(bodyJson).apply { remove("response_format") }.toString()
            result = send(client, config, key, withoutFormat)
        }

        if (result.first !in 200..299) {
            throw AiException(errorMessage(result.first, result.second))
        }
        return result.second
    }

    private fun errorMessage(code: Int, body: String): String = when (code) {
        401, 403 -> "Key 无效或已过期，去设置里检查（$code）"
        429 -> "触发限流，稍等 10 秒再试一次（429）"
        402 -> "账户余额不足，去服务商充值页看看（402）"
        in 500..599 -> "服务商暂时不可用（$code），稍后重试"
        else -> "请求失败（$code）：${body.take(120)}"
    }

    /** 从模型返回里取出正文；有些服务商直接返回 JSON，没有 choices 包装。 */
    private fun extractContent(raw: String): String = try {
        JSONObject(raw)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: raw
    } catch (e: Exception) {
        raw
    }

    /** 模型爱把 JSON 包在 ```json ... ``` 里，这里抠出第一个完整的 {...}。 */
    private fun extractJsonBlock(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw AiException("AI 返回的不是 JSON，无法自动拆条：${text.take(120)}")
        }
        return text.substring(start, end + 1)
    }

    private fun parseResult(raw: String): AiRecognitionResult {
        val content = extractContent(raw)
        val obj = JSONObject(extractJsonBlock(content))

        val items = mutableListOf<AiFoodItem>()
        val array = obj.optJSONArray("items") ?: JSONArray()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name", "").trim()
            if (name.isEmpty()) continue
            items.add(
                AiFoodItem(
                    name = name,
                    amountText = item.optString("amount_text", ""),
                    grams = item.optDouble("grams", 100.0),
                    kcal = item.optDouble("kcal", 0.0),
                    proteinG = item.optDouble("protein_g", 0.0),
                    carbG = item.optDouble("carb_g", 0.0),
                    fatG = item.optDouble("fat_g", 0.0),
                    confidence = item.optDouble("confidence", 0.5)
                )
            )
        }
        if (items.isEmpty()) throw AiException("AI 没拆出食物条目，换个说法或改用手动搜索")

        val mealType = obj.optString("meal_type", "").takeIf { MealRules.isValid(it) }
            ?: MealRules.guess()
        val total = if (obj.has("total_kcal")) {
            obj.optDouble("total_kcal", items.sumOf { it.kcal })
        } else {
            items.sumOf { it.kcal }
        }

        return AiRecognitionResult(
            mealType = mealType,
            items = items,
            totalKcal = total,
            advice = obj.optString("advice", ""),
            needClarify = obj.optBoolean("need_clarify", false)
        )
    }

    /**
     * 文字识别：输入一句人话，返回可编辑的食物条目列表。
     *
     * @param extraRule 设置页里的个性化规则（忌口、估算保守/激进、是否自动判断餐次），
     *                  由仓库层拼好传进来；空字符串表示不加。
     */
    suspend fun recognizeText(
        config: AiConfig,
        key: String,
        input: String,
        extraRule: String = ""
    ): Result<AiRecognitionResult> = withContext(Dispatchers.IO) {
        try {
            validate(config, key)
            val systemContent = if (extraRule.isBlank()) {
                SYSTEM_PROMPT
            } else {
                SYSTEM_PROMPT + "\n\n用户偏好（优先遵守）：\n" + extraRule
            }
            val body = JSONObject().apply {
                put("model", config.textModel.trim())
                // 识别是「照着规则算数」的活儿，不需要创造性。
                // 用小模型时温度调低能明显减少「同一句话每次克数都不一样」的情况。
                put("temperature", 0.1)
                put("response_format", JSONObject().put("type", "json_object"))
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemContent))
                    put(JSONObject().put("role", "user").put("content", input))
                })
            }.toString()
            Result.success(parseResult(call(config, key, body, allowFallback = true)))
        } catch (e: AiException) {
            Result.failure<AiRecognitionResult>(e)
        } catch (e: SocketTimeoutException) {
            Result.failure<AiRecognitionResult>(
                AiException("网络较慢，超过 ${config.timeoutSec} 秒没回应；输入内容已保留，可重试")
            )
        } catch (e: Exception) {
            Result.failure<AiRecognitionResult>(
                AiException("连不上 AI 服务：${e.message?.take(100) ?: "未知错误"}")
            )
        }
    }

    /** 图片识别前检查：视觉模型是单独一个字段，没填就别浪费一次请求。 */
    private fun validateVision(config: AiConfig, key: String) {
        if (config.baseUrl.isBlank()) throw AiException("还没填 Base URL，去设置 → AI 服务配置")
        if (key.isBlank()) throw AiException("还没填 API Key，去设置 → AI 服务配置")
        if (config.visionModel.isBlank()) {
            throw AiException("还没填视觉模型名（要支持 image_url），去设置 → AI 服务配置")
        }
    }

    /**
     * 图片识别（M3.2）。
     * 照片必须先压缩到长边 1024 再转 Base64，这里只管发请求。
     * 不传 response_format：多数视觉模型不认这个字段，直接省掉 400 重试。
     */
    suspend fun recognizeImage(
        config: AiConfig,
        key: String,
        base64Jpeg: String,
        extraRule: String = ""
    ): Result<AiRecognitionResult> = withContext(Dispatchers.IO) {
        try {
            validateVision(config, key)
            val dataUrl = "data:image/jpeg;base64,$base64Jpeg"
            val systemContent = if (extraRule.isBlank()) {
                VISION_PROMPT
            } else {
                VISION_PROMPT + "\n\n用户偏好（优先遵守）：\n" + extraRule
            }
            val body = JSONObject().apply {
                put("model", config.visionModel.trim())
                put("temperature", 0.1)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemContent))
                    put(JSONObject().put("role", "user").put("content", JSONArray().apply {
                        put(JSONObject().put("type", "text").put("text", "识别这份食物并估算份量"))
                        put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUrl)))
                    }))
                })
            }.toString()
            Result.success(parseResult(call(config, key, body, allowFallback = false)))
        } catch (e: AiException) {
            Result.failure<AiRecognitionResult>(e)
        } catch (e: SocketTimeoutException) {
            Result.failure<AiRecognitionResult>(
                AiException("图片识别超时（${config.timeoutSec} 秒），照片已保留，可重试或改用手动搜索")
            )
        } catch (e: Exception) {
            Result.failure<AiRecognitionResult>(
                AiException("连不上 AI 服务：${e.message?.take(100) ?: "未知错误"}")
            )
        }
    }

    /**
     * AI 分析（M3.8）：今日复盘 / 周报共用。
     * 数据由调用方在仓库层聚合好拼进 prompt，这里不查数据库，保证「只说给它的数字」。
     */
    suspend fun analyze(config: AiConfig, key: String, facts: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            validate(config, key)
            val body = JSONObject().apply {
                put("model", config.textModel.trim())
                put("temperature", config.temperature)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", ANALYSIS_RULE))
                    put(JSONObject().put("role", "user").put("content", facts))
                })
            }.toString()
            val text = extractContent(call(config, key, body, allowFallback = false))
                .replace("```json", "").replace("```", "").trim()
            Result.success(text.ifBlank { "AI 没有给出内容，换个时间范围再试" })
        } catch (e: AiException) {
            Result.failure<String>(e)
        } catch (e: SocketTimeoutException) {
            Result.failure<String>(AiException("分析超时，稍后再试"))
        } catch (e: Exception) {
            Result.failure<String>(AiException("连不上 AI 服务：${e.message?.take(100) ?: "未知错误"}"))
        }
    }

    /** 测试连接：只发一条 10 token 的探测，成功就说明 Key 和模型都可用。 */
    suspend fun testConnection(config: AiConfig, key: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            validate(config, key)
            val body = JSONObject().apply {
                put("model", config.textModel.trim())
                put("max_tokens", 10)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "user").put("content", "hi"))
                })
            }.toString()
            call(config, key, body, allowFallback = false)
            Result.success("连接正常，模型可用")
        } catch (e: AiException) {
            Result.failure<String>(e)
        } catch (e: SocketTimeoutException) {
            Result.failure<String>(AiException("连接超时，检查网络或把超时时间调大"))
        } catch (e: Exception) {
            Result.failure<String>(AiException("连不上：${e.message?.take(100) ?: "未知错误"}"))
        }
    }
}
