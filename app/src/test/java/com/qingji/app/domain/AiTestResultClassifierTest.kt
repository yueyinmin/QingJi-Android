package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** AI 测试连接 8 类结果映射单测（工单 §7）。 */
class AiTestResultClassifierTest {

    @Test
    fun `成功归类为连接正常`() {
        assertEquals(
            AiTestResultClassifier.Category.OK,
            AiTestResultClassifier.classify(true, "连接正常，模型可用")
        )
    }

    @Test
    fun `超时识别`() {
        assertEquals(
            AiTestResultClassifier.Category.TIMEOUT,
            AiTestResultClassifier.classify(false, "连接超时，检查网络或把超时时间调大")
        )
        assertEquals(
            AiTestResultClassifier.Category.TIMEOUT,
            AiTestResultClassifier.classify(false, "SocketTimeoutException")
        )
    }

    @Test
    fun `Key无效识别`() {
        assertEquals(
            AiTestResultClassifier.Category.INVALID_KEY,
            AiTestResultClassifier.classify(false, "Key 无效或已过期，去设置里检查（401）")
        )
    }

    @Test
    fun `模型不存在识别`() {
        assertEquals(
            AiTestResultClassifier.Category.MODEL_MISSING,
            AiTestResultClassifier.classify(false, "模型不存在或接口地址不对（404）")
        )
    }

    @Test
    fun `额度限流识别`() {
        assertEquals(
            AiTestResultClassifier.Category.QUOTA,
            AiTestResultClassifier.classify(false, "额度不足或余额已用完（429）")
        )
        assertEquals(
            AiTestResultClassifier.Category.QUOTA,
            AiTestResultClassifier.classify(false, "触发限流，稍等 10 秒（429）")
        )
    }

    @Test
    fun `网络失败识别`() {
        assertEquals(
            AiTestResultClassifier.Category.NETWORK,
            AiTestResultClassifier.classify(false, "连不上：Failed to connect")
        )
    }

    @Test
    fun `服务端异常识别`() {
        assertEquals(
            AiTestResultClassifier.Category.SERVER,
            AiTestResultClassifier.classify(false, "服务商暂时不可用（503），稍后重试")
        )
    }

    @Test
    fun `格式异常识别`() {
        assertEquals(
            AiTestResultClassifier.Category.BAD_FORMAT,
            AiTestResultClassifier.classify(false, "返回数据格式异常：JSON 解析失败")
        )
    }

    @Test
    fun `未知错误兜底`() {
        assertEquals(
            AiTestResultClassifier.Category.UNKNOWN,
            AiTestResultClassifier.classify(false, "某些没见过的错误")
        )
    }
}
