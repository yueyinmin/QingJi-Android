package com.qingji.app.domain.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阶段 K：脱敏规则的边界。
 *
 * 这些用例不追求「把所有密钥格式都背下来」，只保证一件事：
 * 上游错误正文、异常栈、URL 里常见的那几种携带方式，进了提示和日志都必须看不见真值。
 */
class SecretRedactorTest {

    @Test
    fun `sk 开头的 Key 会被打码`() {
        val raw = "401 invalid key: sk-proj-abcdefghijklmnop1234567890"
        val safe = SecretRedactor.redact(raw)
        assertFalse(safe.contains("abcdefghijklmnop"))
        assertTrue(safe.contains("sk-***"))
    }

    @Test
    fun `Bearer 之后的 token 会被打码`() {
        val safe = SecretRedactor.redact("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig")
        assertFalse(safe.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertTrue(safe.contains("Bearer ***"))
    }

    @Test
    fun `key 等于值与 token 冒号值都会被打码`() {
        val raw = "https://api.example.com/v1/chat?api_key=abcd1234efgh&model=x token: zzzz9999"
        val safe = SecretRedactor.redact(raw)
        assertFalse(safe.contains("abcd1234efgh"))
        assertFalse(safe.contains("zzzz9999"))
        // 参数名保留，方便定位是哪个字段出的问题
        assertTrue(safe.contains("api_key="))
        assertTrue(safe.contains("model=x"))
    }

    @Test
    fun `密码类字段同样打码`() {
        val safe = SecretRedactor.redact("password=hunter2, secret=topsecret")
        assertFalse(safe.contains("hunter2"))
        assertFalse(safe.contains("topsecret"))
    }

    @Test
    fun `URL 里的账号密码会被打码`() {
        val safe = SecretRedactor.redact("proxy https://user:pa55word@127.0.0.1:7890 refused")
        assertFalse(safe.contains("pa55word"))
        assertTrue(safe.contains("127.0.0.1:7890"))
    }

    @Test
    fun `长串字母数字混合体按疑似密钥处理`() {
        val token = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"
        val safe = SecretRedactor.redact("body=$token end")
        assertFalse(safe.contains(token))
    }

    @Test
    fun `普通中文和短词不会被误伤`() {
        val raw = "服务端 500：今天饮食记录为空，请稍后再试"
        assertEquals(raw, SecretRedactor.redact(raw, maxLength = 0))
    }

    @Test
    fun `超长文本按上限截断并保留省略号`() {
        val safe = SecretRedactor.redact("a".repeat(300), maxLength = 20)
        assertEquals(21, safe.length) // 20 个字符 + 省略号
        assertTrue(safe.endsWith("…"))
    }

    @Test
    fun `截断值传 0 表示不截断`() {
        val raw = "很长的正文".repeat(50)
        assertEquals(raw, SecretRedactor.redact(raw, maxLength = 0))
    }

    @Test
    fun `containsSecret 能当自检用`() {
        assertTrue(SecretRedactor.containsSecret("key=abcdef123456"))
        assertTrue(SecretRedactor.containsSecret("sk-1234567890"))
        assertFalse(SecretRedactor.containsSecret("今天吃了 1800 千卡"))
    }

    @Test
    fun `单独的 key 参数只在值够长时才打码`() {
        // 兼容代理常见的 ?key= 写法：值够长，必须打码
        assertFalse(SecretRedactor.redact("?key=abcdef123456").contains("abcdef123456"))
        // 「key: 键盘布局」这类正常文字不该被打码
        assertEquals("key: 键盘布局", SecretRedactor.redact("key: 键盘布局", maxLength = 0))
    }

    @Test
    fun `空字符串原样返回`() {
        assertEquals("", SecretRedactor.redact(""))
    }
}
