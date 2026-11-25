# 轻记 AI 原生化 · B01 AI 基础设施实施工单

> 工单编号：QJ-AI-B01  
> 父工单：QJ-AI-NATIVE-MASTER-V1  
> 阶段：B · AI 基础设施（第一步）  
> 优先级：P0  
> 类型：正式编码工单  
> 基线 HEAD：`c97ada3d98678cc7f178061a84404b64a95d6066`  
> 分支：`feature/health-system-v1.1`  
> 前置设计：README、完整实施路线图、ToolCalling协议与Agent执行规范、当前Git基线与测试基准。

---

## 1. 工单目标

建立可替换、可测试、可取消的 AI 服务提供商抽象，并把现有一次性文本/图片识别调用迁移到该抽象之上。

B01 只完成 **Provider 基础**，不实现 SSE，不实现 Agent，不实现 Tool Calling，不碰数据库和 UI。

完成标准：业务层不再依赖具体 `AiClient` 类型；新增 Provider 可通过 MockWebServer 完成契约测试；现有 AI 功能行为保持不变。

## 2. 当前真实状态

| 项 | 当前实现 |
|---|---|
| AI 客户端 | `app/src/main/java/com/qingji/app/data/remote/AiClient.kt`，4 个 public suspend 方法：`recognizeText`(352)、`recognizeImage`(404)、`analyze`(447)、`testConnection`(471) |
| 请求协议 | `org.json` 手工拼 JSON，OkHttp `execute()` 一次性读取响应 |
| 能力 | 普通文本、图片输入、一次性分析、连接测试 |
| 缺失 | 无 `AiProvider` 抽象、无 SSE、无 Tool Calling、无请求取消、无能力声明 |
| 配置 | `Entities.kt:171-183 AiConfig`，单行 provider/baseUrl/textModel/visionModel/temperature/timeoutSec/dailyLimit/enabled |
| Key | `ApiKeyStore.kt`，AndroidKeyStore + AES/GCM，不入库、不备份 |
| Repository 闸门 | `QingJiRepository.requireAi()`、`todayAiCalls()`、`getApiKey()` |
| 测试 | AI 仅 3 个纯规则测试；无 MockWebServer；无 Provider 测试 |

## 3. 范围

### 3.1 本工单必须做

1. 新增 `AiProvider` 抽象，支持普通文本生成、图片输入请求、连接测试、能力声明、请求取消。
2. 新增 OpenAI 兼容适配器，迁移现有 `AiClient` 协议逻辑。
3. 新增统一 `AiError` 错误模型，覆盖网络、鉴权、限流、余额、模型不可用、超时、流中断预留、解析失败、用户取消。
4. 新增 `AiProviderCapabilities`，以配置和连接测试结果表达能力；不得按 provider 名称硬编码。
5. 现有 `QingJiRepository` 的 `recognizeText` / `recognizeImage` / `analyze` / `testConnection` 改为通过 Provider 调用，保持对外签名不变。
6. 保留 `AiClient` 作为兼容门面或删除前确保无调用方；本工单优先改为 `@Deprecated` 门面，避免一次性扩大改动。
7. 增加 MockWebServer 与协程测试依赖，新增 Provider 契约测试。
8. 实现基础安全：Key 不入日志、错误体脱敏截断、上下文最小化、请求可取消、网络失败降级。

### 3.2 本工单明确不做

- 不实现 SSE 流式文本。
- 不实现 `Tool Calling` 或 `AiStreamEvent`。
- 不实现 `QingJiAgent` / `ContextBuilder` / `ToolRegistry`。
- 不实现 AI 会话页、消息流、提案系统。
- 不新增或修改 Room 表、Entity、DAO、Migration。
- 不新增 AI 库。
- 不修改业务 UI。
- 不修改饮食、训练、运动、身体数据业务流程。
- 不升级生产依赖版本，只新增测试依赖。

## 4. 目标架构

```text
Screen / ViewModel（不改调用签名）
        ↓
QingJiRepository（配置闸门、规则拼装、日限熔断、结果纠偏）
        ↓
AiProviderFactory（按 AiConfig.provider 选择适配器）
        ↓
OpenAiCompatibleProvider（普通生成、图片输入、连接测试、取消、错误映射）
        ↓
OkHttp / OpenAI 兼容 /chat/completions
```

`AiClient.kt` 的现有协议代码迁移到 Provider 后，不应再由业务层直接依赖。

## 5. 新增/修改文件清单

### 5.1 新增生产代码（建议路径）

| 文件 | 职责 |
|---|---|
| `app/src/main/java/com/qingji/app/data/remote/ai/AiProvider.kt` | Provider 接口 |
| `app/src/main/java/com/qingji/app/data/remote/ai/AiModels.kt` | `AiRequest`、`AiMessage`、`AiContentPart`、`AiResponse`、`TokenUsage` |
| `app/src/main/java/com/qingji/app/data/remote/ai/AiError.kt` | 统一错误类型与文案映射 |
| `app/src/main/java/com/qingji/app/data/remote/ai/AiProviderCapabilities.kt` | 能力声明 |
| `app/src/main/java/com/qingji/app/data/remote/ai/OpenAiCompatibleProvider.kt` | OpenAI 兼容适配器，现行唯一实现 |
| `app/src/main/java/com/qingji/app/data/remote/ai/AiProviderFactory.kt` | 根据 `AiConfig.provider` 创建 Provider |

### 5.2 修改生产代码

| 文件 | 改动 |
|---|---|
| `app/src/main/java/com/qingji/app/data/repository/QingJiRepository.kt` | 注入/创建 Provider；AI 调用改走 Provider；保留闸门、规则、纠偏、计数逻辑 |
| `app/src/main/java/com/qingji/app/data/remote/AiClient.kt` | 保留为 `@Deprecated` 兼容门面，内部委托 Provider；下一阶段确认无引用后再删除 |

### 5.3 新增测试代码

| 文件 | 覆盖 |
|---|---|
| `app/src/test/java/com/qingji/app/data/remote/ai/OpenAiCompatibleProviderTest.kt` | MockWebServer 契约测试 |
| `app/src/test/java/com/qingji/app/data/remote/ai/AiErrorMappingTest.kt` | HTTP/网络/解析错误映射 |
| `app/src/test/java/com/qingji/app/data/remote/ai/AiProviderCapabilitiesTest.kt` | 能力声明与降级 |
| `app/src/test/java/com/qingji/app/data/remote/ai/AiRequestBuilderTest.kt` | 请求体、Key 不落日志、上下文最小化 |

### 5.4 依赖变更

只新增测试依赖：

- `mockwebserver = 4.12.0`（与现有 `okhttp = 4.12.0` 对齐）
- `kotlinx-coroutines-test = 1.9.0`（与现有 coroutines 版本对齐）

修改 `gradle/libs.versions.toml` 与 `app/build.gradle.kts` 的 `testImplementation`。

## 6. 接口要求

### 6.1 AiProvider（B01 形态）

```kotlin
interface AiProvider {
    suspend fun generate(request: AiRequest): AiResponse
    suspend fun testConnection(config: AiConfig, key: String): ConnectionResult
    fun capabilities(config: AiConfig): AiProviderCapabilities
}
```

注意：B01 **不加入** `stream()`。B02 再扩展流式接口，避免本阶段实现空方法。

### 6.2 请求模型

```kotlin
data class AiRequest(
    val config: AiConfig,
    val key: String,
    val messages: List<AiMessage>,
    val temperature: Double = 0.2,
    val maxTokens: Int? = null,
    val forceJsonObject: Boolean = false
)

data class AiMessage(
    val role: String,
    val parts: List<AiContentPart>
)

sealed interface AiContentPart {
    data class Text(val text: String) : AiContentPart
    data class ImageBase64(val base64: String, val mime: String = "image/jpeg") : AiContentPart
}
```

### 6.3 响应模型

```kotlin
data class AiResponse(
    val content: String,
    val model: String?,
    val usage: TokenUsage?
)

data class TokenUsage(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)

data class ConnectionResult(
    val success: Boolean,
    val message: String,
    val capabilities: AiProviderCapabilities
)
```

### 6.4 能力声明

```kotlin
data class AiProviderCapabilities(
    val chat: Boolean,
    val streaming: Boolean = false,
    val toolCalling: Boolean = false,
    val streamingToolCalling: Boolean = false,
    val jsonSchema: Boolean = false,
    val imageInput: Boolean,
    val audioInput: Boolean = false
)
```

B01 约束：

- `chat = true` 仅在实际生成成功后声明。
- `imageInput` 可基于现有图片接口声明，但不要求 B01 完成中餐识别。
- `streaming` / `toolCalling` / `streamingToolCalling` / `jsonSchema` 默认 false，等 B02/B03 实测后开启。
- 不得根据 provider 字符串直接推断全部能力。

### 6.5 错误模型

`AiError` 至少覆盖：

- `Network`
- `InvalidApiKey`
- `RateLimited`
- `InsufficientBalance`
- `ModelUnavailable`
- `Timeout`
- `StreamInterrupted`（预留，不在 B01 触发）
- `InvalidResponse`
- `ToolArgument`（预留）
- `PermissionDenied`（预留）
- `VersionConflict`（预留）
- `Cancelled`

错误对象必须带 `code`、`message`、`retryable`。UI 不得只看到“请求失败”。

## 7. 关键实现规则

### 7.1 请求取消

- Provider 必须保留当前 OkHttp `Call`。
- 协程取消时调用 `call.cancel()`。
- 建议使用 `enqueue` + `suspendCancellableCoroutine`，不要继续用不可取消的阻塞 `execute()`。
- 请求结束后清理 `Call` 引用。

### 7.2 错误处理

- 沿用现有 `errorMessage(code, body)` 的错误语义，迁入 `AiError` 映射。
- 错误体只截取有限长度，且不得打印 Authorization / Key。
- `response_format` 400 降级只允许一次，并记录降级事件；不得静默改变输出契约。
- 429/5xx 在 B01 不实现退避重试，只返回 `retryable=true`。

### 7.3 配置与 Key

- 继续读取现有 `AiConfig` 单行配置。
- 不新增服务商 CRUD UI；多服务商配置属于后续 B 阶段细化。
- Key 只从 `ApiKeyStore` / `QingJiRepository.getApiKey()` 读取。
- 日志中不得出现 Key、Authorization 头、完整请求体。

### 7.4 兼容策略

- ViewModel / Screen 不改调用方式。
- `QingJiRepository` 对外方法签名保持不变：
  - `recognizeText(...)`
  - `refineMeal(...)`
  - `recognizeImage(...)`
  - `analyze(...)`
- 现有 `AiResultRules` / `NutritionMatch` / `correctWithLocalFood()` 纠偏逻辑必须保留。

## 8. 测试要求

### 8.1 必须新增

1. 普通文本生成成功：MockWebServer 返回合法 JSON，断言请求体 model/messages/temperature。
2. Key 不落日志：请求头断言 Authorization 正确，但测试日志无 Key。
3. 图片请求：请求体包含 `image_url` 与 base64 data URL，不携带 `response_format`。
4. `response_format` 400 降级：第一次 400，去掉字段后重试成功。
5. 401/403：映射 `InvalidApiKey`。
6. 429：映射 `RateLimited` 且 `retryable=true`。
7. 5xx：映射 `ModelUnavailable` 或服务端错误。
8. 超时：映射 `Timeout`。
9. 非法 JSON：映射 `InvalidResponse`。
10. 取消：取消协程后 `Call` 被取消，无后续回调写状态。
11. 能力声明：chat/imageInput 正确，streaming/toolCalling 为 false。
12. 配置缺失/未启用/日限熔断：Repository 不发起网络请求。

### 8.2 必须保留

- 现有 463 个 JVM 单测不得新增失败。
- `HomeInteractionTest` 的 3 个 skipped 状态不变，且不得被当作已通过。

## 9. 验收命令

```bash
cd /c/qingji-git/QingJi-Android
./gradlew compileDebugKotlin --no-daemon --console=plain
./gradlew testDebugUnitTest --no-daemon --console=plain
./gradlew assembleDebug --no-daemon --console=plain
./gradlew assembleRelease --no-daemon --console=plain
```

`testDebugUnitTest` 结果要求：

- failures = 0
- errors = 0
- skipped 不得增加（仍仅 HomeInteractionTest 3 个）

## 10. 交付物

完成后必须提交：

1. 修改文件清单。
2. 代码变更说明。
3. 依赖变更说明（仅测试依赖）。
4. 数据库变更说明：写“无”。
5. 测试结果：命令、用例数、通过/失败/跳过。
6. 构建结果：compile / test / debug / release。
7. 已知问题。
8. 下一阶段依赖（B02）。
9. 最终 Git commit SHA。

## 11. 风险与约束

| 风险 | 处理 |
|---|---|
| 供应商响应格式差异 | 由 Provider 适配器吸收；测试至少覆盖 OpenAI 兼容标准格式 |
| `response_format` 降级改变输出契约 | 必须记录降级事件；禁止静默 |
| 取消实现不彻底 | 使用 Call.cancel；必须写取消测试 |
| 误把 SSE 当普通请求 | B01 不实现 stream；B02 单独验收 |
| Key 泄露 | 日志脱敏测试；错误体截断 |
| 原有 AI 功能回归 | Repository 对外签名不变；现有纠偏逻辑保留 |

## 12. 阶段门禁

只有同时满足以下条件，B01 才算完成：

- `AiProvider` 抽象与 OpenAI 兼容适配器可用。
- Repository 已迁移到 Provider。
- MockWebServer 契约测试 ≥12 个且通过。
- 现有业务调用签名未变。
- 无数据库/UI/业务模型修改。
- 四项构建命令全部成功。
- 输出完整交付报告与 commit SHA。

未通过不得进入 B02。
