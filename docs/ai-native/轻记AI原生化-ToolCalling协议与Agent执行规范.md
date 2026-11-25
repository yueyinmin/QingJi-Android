# 轻记 AI 原生化 · Tool Calling 协议与 Agent 执行规范

> 工单：QJ-AI-NATIVE-DOC-REVIEW-V1 · 任务六  
> 基线 HEAD：`c97ada3d98678cc7f178061a84404b64a95d6066`  
> 性质：阶段 B/C 前置定稿；本轮不改代码。

---

## 1. 结论

1. 阶段 B 必须实现 OpenAI 兼容 Tool Calling 与流式 Tool Calling 的协议层。
2. 阶段 C 实现只读 Agent 与只读工具；写入工具在阶段 E 提案/权限验收前不得注册为可执行工具。
3. 一个模型响应可包含多个 tool_call；允许并行执行只读工具，但结果必须按 `callId` 回传。
4. 流式参数必须按 `callId` 独立拼接，完整且通过 JSON Schema 校验后才能执行。
5. 工具失败必须返回结构化错误，不能把异常文本直接塞回模型。
6. Agent 限制由代码强制执行：单任务 ≤4 轮模型交互、单轮 ≤5 工具、单任务 ≤10 工具、单工具默认 10s（可配置）、可取消、无无限重试。
7. AI 输出必须区分 FACT / CALCULATED / INFERRED / UNKNOWN / SUGGESTION，禁止推断伪装成事实。

## 2. 统一事件模型

阶段 B 流式事件：

```kotlin
sealed interface AiStreamEvent {
    data class TextDelta(val text: String) : AiStreamEvent
    data class ToolCallStarted(val callId: String, val name: String) : AiStreamEvent
    data class ToolArgumentsDelta(val callId: String, val fragment: String) : AiStreamEvent
    data class ToolCallCompleted(val callId: String) : AiStreamEvent
    data class Completed(val usage: TokenUsage?) : AiStreamEvent
    data class Failed(val error: AiError) : AiStreamEvent
}
```

供应商差异由 AI 服务层适配；上层 Agent 只消费统一事件。

## 3. Tool Call 协议规则

| 编号 | 规则 |
|---|---|
| T1 | 单个模型响应可返回多个 tool_call；每个调用必须有唯一 `callId` |
| T2 | 流式 `arguments` 片段按 `callId` 分别累积，不能按到达顺序混拼 |
| T3 | 参数片段完整后做 JSON 解析与 Schema 校验；不完整不执行 |
| T4 | 工具执行结果必须带原 `callId` 回传模型 |
| T5 | 工具失败返回结构化错误：`code`、`message`、`toolName`、`callId`、`retryable` |
| T6 | 工具响应格式按供应商协议要求封装；不同供应商由适配器处理 |
| T7 | 工具执行必须经过 ToolExecutor 的权限校验与白名单 |
| T8 | 模型输出不能覆盖只读白名单；要求写库时只能转成提案草稿 |
| T9 | 同一 `callId` 不允许执行两次（去重表/集合） |
| T10 | 并行执行的结果按 `callId` 归集，禁止 A 结果配 B 调用 |

## 4. 工具定义结构

每个工具注册项包含：

- `name`：唯一工具名，如 `get_today_summary`。
- `description`：给模型看的用途说明。
- `parametersJsonSchema`：参数 JSON Schema。
- `permissionLevel`：`READ_ONLY` / `WRITE_PROPOSAL`（阶段 E 后）。
- `timeoutMs`：默认 10000，可配置。
- `execute`：调用本地业务 Repository/UseCase 的实现。
- `resultSerializer`：统一 DTO 序列化。

首期只读工具：

| 工具 | 参数 | 复用入口 |
|---|---|---|
| `get_today_summary` | `date` | `QingJiRepository.observeDailySummary` / `HealthRepository.observeDailyExerciseCalories` / `TrainingRepository` |
| `get_daily_nutrition` | `date`, 可选 `mealType` | `QingJiRepository.observeFoodLogs` / `observeMealSummary` |
| `get_training_history` | `dateRange`, 可选 `exercise` | `TrainingRepository.observeSessions` / `sessionDetail` / `observeAllWorkoutExercises` |
| `get_training_plan` | 可选 `planId` | `TrainingRepository.observeActivePlan` / `observePlanExercises` |

## 5. 并发执行规范

- 只读工具在确认无数据依赖时可并行。
- 并行结果按 `callId` 收集，顺序不影响映射。
- 某一个工具失败不能丢失其他工具结果；失败工具返回结构化错误，其他工具正常回传。
- 写入工具（阶段 E 后）默认串行，受权限、幂等和提案状态约束。
- 工具执行超时只终止该工具，不自动终止整个任务；是否继续由 Agent 策略决定。

## 6. Agent 执行限制（代码强制）

| 限制 | 值 | 说明 |
|---|---|---|
| 单任务模型交互轮数 | ≤4 | 防止无限循环 |
| 单轮工具调用数 | ≤5 | 防止 prompt 过大 |
| 单任务工具调用总数 | ≤10 | 防止费用失控 |
| 单工具超时 | 10000ms | 可配置，不无限等待 |
| 重复调用 | 同一 callId/参数指纹只执行一次 | 防重复副作用 |
| 用户取消 | 必须支持 | 取消后停止模型流和工具执行 |
| 无限重试 | 禁止 | 失败需退回错误或用户决策 |

## 7. Agent 流程

```text
用户输入
 → ContextBuilder 构建上下文（带数据范围/来源/缺失标识）
 → 模型请求（携带只读工具 schema）
 → 流式事件消费
 → 多 tool_call 按 callId 校验与执行
 → 工具结果按 callId 回传模型
 → 模型生成最终答案或提案草稿
 → ResponseParser 结构化解析
 → UI 展示（带数据来源标识）
```

阶段 C 不生成可执行写工具；模型返回的写入意图只能转成 `ProposalDraft`，阶段 E 才接入 ProposalManager。

## 8. 数据来源与结论可信度

### 8.1 五类标识

| 类型 | 含义 | 示例 |
|---|---|---|
| `FACT` | 正式业务记录 | 今天记录了 1800 kcal |
| `CALCULATED` | 本地业务引擎计算结果 | 蛋白质完成度 75% |
| `INFERRED` | AI 推断 | 近期训练量可能偏低 |
| `UNKNOWN` | 数据不足或无法确定 | 缺少最近三天体重记录 |
| `SUGGESTION` | AI 建议 | 下次训练增加一组 |

### 8.2 强制规则

- `FACT` 必须可追溯到正式业务记录或工具结果。
- `CALCULATED` 必须来自本地计算引擎，不能由模型算。
- `INFERRED` / `SUGGESTION` 必须明确标注，不得写成事实。
- `UNKNOWN` 必须说明缺什么数据、时间范围是什么。
- 每个重要结论需带数据时间范围（如“近 7 天”“今天”）。
- `ResponseParser` 拒绝把 `INFERRED` 包装成 `FACT`。

## 9. 测试要求

阶段 B：

- SSE 文本流分片。
- 流式 tool_call 参数按 callId 拼接。
- 多 tool_call 同响应。
- 工具结果按 callId 回传。
- 供应商错误码、超时、取消。
- JSON Schema 严格输出与降级。

阶段 C：

- 单工具/多工具/多轮。
- 参数错误不执行。
- 工具超时/失败结构化。
- 重复调用防护。
- 并行结果不串。
- 循环次数与总工具数上限。
- 取消后停止执行。
- 工具结果与业务页面一致。
- 只读白名单不可被模型覆盖。
- FACT/CALCULATED/INFERRED/UNKNOWN/SUGGESTION 标识正确。

## 10. 未解决 / 需编码验证

- 具体供应商 SSE 事件格式需在阶段 B 用 MockWebServer 验证。
- 工具参数指纹算法（用于重复防护）需在阶段 C 实现时确定。
- 并行工具的最大并发数需结合设备性能在阶段 L 调优。
