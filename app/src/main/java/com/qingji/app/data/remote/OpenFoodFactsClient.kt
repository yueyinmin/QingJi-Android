package com.qingji.app.data.remote

import com.qingji.app.domain.BarcodeRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * ============================================================
 * 条码兜底查询：Open Food Facts（M3.4，免费开源库）
 * ------------------------------------------------------------
 * 顺序永远是「先查本地 food 表 → 查不到才联网」，断网时本地库照常用。
 * 这个 API 在境内不一定连得上，连不上就返回中文提示，
 * 界面会引导「手动录入并保存到我的食物库」，不会卡住用户。
 * ============================================================
 */
class OpenFoodFactsClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun lookup(rawCode: String): Result<BarcodeRules.Product> = withContext(Dispatchers.IO) {
        val code = BarcodeRules.normalize(rawCode)
        if (code.length !in 8..13) {
            return@withContext Result.failure(AiException("条码看起来不对，请检查是不是 8~13 位数字"))
        }
        val url = "https://world.openfoodfacts.org/api/v2/product/$code.json"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "QingJi-Android/1.0 (personal use)")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(AiException("查询失败（HTTP ${response.code}），可手动录入"))
                }
                val body = response.body?.string().orEmpty()
                val product = BarcodeRules.parseOpenFoodFacts(body)
                    ?: return@withContext Result.failure(AiException("这个条码还没被收录，可以手动录入并保存到食物库"))
                Result.success(product)
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(AiException("查询超时，检查网络或手动录入"))
        } catch (e: Exception) {
            Result.failure(AiException("连不上食品库：${e.message?.take(80) ?: "未知错误"}，可手动录入"))
        }
    }
}
