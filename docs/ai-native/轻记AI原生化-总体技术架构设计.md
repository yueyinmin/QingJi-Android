# 轻记 AI 原生化 · 总体技术架构设计

> 工单：QJ-AI-NATIVE-MASTER-V1  
> 轮次：第一轮（只读审计 + 架构设计）  
> 基线仓库：`/c/qingji-git/QingJi-Android`，HEAD `c97ada3d98678cc7f178061a84404b64a95d6066`  
> 文档性质：目标设计，不是已实现事实；所有“现状”以《轻记AI原生化-完整代码审计报告.md》为准。

---

## 1. 设计原则

1. **本地优先**：业务数据和计算全部在本地完成；云端模型只做理解、归纳与建议。
2. **AI 不绕过业务层**：AI 工具只能调用现有 Repository / UseCase / 计算引擎，禁止直接访问 DAO 或拼 SQL。
3. **写操作必走提案**：任何正式数据修改必须有提案、版本校验、幂等键和用户确认。
4. **能力按模型实测**：不按供应商名字推断能力，SSE / Tool Calling / JSON Schema / 图片输入必须由连接测试 + 能力探测确认。
5. **失败降级**：AI 不可用时，原有饮食、运动、训练、身体数据功能必须完全可用。
6. **不制造重复模块**：新增类、表、Repository 前必须说明现有代码为何不能复用。
7. **阶段门禁**：每阶段有独立验收，不允许跳过。

## 2. 总体分层

```text
┌───────────────────────────────────────────────┐
│ 交互层                                        │
│ AI 管家页 / 业务页上下文入口 / 图片语音输入     │
│ 分析卡片 / 提案卡片 / 工具过程提示              │
└──────────────────────┬────────────────────────┘
                       │
┌──────────────────────▼────────────────────────┐
│ AI 应用层                                     │
│ QingJiAgent / ContextBuilder / ConversationMgr │
│ ResponseParser / ProposalManager               │
└──────────────────────┬────────────────────────┘
                       │
┌──────────────────────▼────────────────────────┐
│ AI 工具层                                     │
│ ToolRegistry / ToolExecutor / PermissionManager│
│ 只读工具 + 受控写入工具（需提案）              │
└──────────────────────┬────────────────────────┘
                       │
┌──────────────────────▼────────────────────────┐
│ 本地业务层（已存在，优先复用）                 │
│ QingJiRepository / TrainingRepository          │
│ NutritionCalculator / ExerciseCalculator       │
│ SessionCalorieCalculator / TrainingMath        │
└──────────────────────┬────────────────────────┘
                       │
┌──────────────────────▼────────────────────────┐
│ 数据层                                        │
│ qingji.db（正式业务 + 幂等标记）               │
│ qingji_ai.db（会话/消息/工具历史/Token）       │
│ food_catalog.db（只读预置食物） + ApiKeyStore  │
└──────────────────────┬────────────────────────┘
                       │
┌──────────────────────▼────────────────────────┐
│ AI 服务层                                     │
│ AiProvider 抽象 / OpenAI 兼容适配器            │
│ 普通生成 / SSE / Tool Calling / 多模态          │
└───────────────────────────────────────────────┘
```

## 3. 模块职责与边界

### 3.1 AI 服务层（阶段 B）

| 模块 | 职责 | 明确不做 |
|---|---|---|
| `AiProvider` | 统一生成、流式、连接测试、取消、能力声明 | 不绑定具体业务 |
| `OpenAiCompatibleProvider` | 实现 OpenAI 兼容协议；处理供应商差异 | 不解析业务语义 |
| `AiProviderCapabilities` | CHAT/STREAMING/TOOL_CALLING/STREAMING_TOOL_CALLING/JSON_SCHEMA/IMAGE_INPUT/AUDIO_INPUT | 不靠 provider 名称硬编码 |
| `AiStreamEvent` | TextDelta / ToolCallStarted / ToolArgumentsDelta / ToolCallCompleted / Completed / Failed | 不把普通 SSE 当成流式工具调用 |
| `AiError` | 网络、鉴权、模型、超时、流中断、工具参数、执行失败、解析失败、权限、版本冲突、取消 | 不让 UI 只看到“请求失败” |
| `AiConfigStore` | 多服务商配置、默认模型、能力测试结果 | 不明文存 API Key，不写普通备份 |

当前 `AiClient` 的普通文本、图片输入、`response_format=json_object` 降级逻辑应作为适配器内部可复用实现，而不是直接重命名成 `AiProvider` 后假装已支持工具调用。

### 3.2 AI 应用层（阶段 C 只读；阶段 E 才实施提案）

**重要边界（修订收口）**：阶段 C 的 Agent **只读、不写库、不含 ProposalManager**。下表中的 `ProposalManager` 仅为目标架构归属，**阶段 E 才正式实施**；F/G 的写入必须等 E 验收通过。

| 模块 | 职责 | 实施阶段 |
|---|---|---|
| `QingJiAgent` | 多轮循环、最大 4 轮模型交互、单轮最多 5 工具、总最多 10 工具、取消、重复调用防护 | C |
| `ContextBuilder` | 按任务注入日期、目标、今日摘要、当前计划、近期训练/身体数据、当前页面上下文；带 Token 预算与数据缺失标记 | C |
| `ConversationManager` | 会话创建、消息序号、历史裁剪、页面退出后的任务状态 | C/D |
| `ResponseParser` | 文本/工具调用/结构化 JSON 解析，参数校验 | C |
| `ProposalManager` | 生成提案、冲突检测、幂等执行、状态回写 | **E（阶段 C 不实施）** |

`QingJiRepository.buildDailyFacts()` / `buildWeeklyFacts()` 已具备事实材料拼装雏形，应扩展为 `ContextBuilder` 的数据源之一，不另起一套统计 SQL。

### 3.3 AI 工具层（阶段 C）

首期只读工具：

| 工具 | 数据来源 | 复用现有入口 |
|---|---|---|
| `get_today_summary` | 今日饮食、运动、训练、目标 | `QingJiRepository.observeFoodLogs` / `observeDailySummary` / `HealthRepository` / `UserProfile` |
| `get_daily_nutrition` | 指定日期/餐次饮食 | `FoodLogDao.observeMealSummary` / `observeRange` |
| `get_training_history` | 训练会话与组数据 | `TrainingRepository.observeSessions` / `observeAllWorkoutExercises` / `sessionDetail` |
| `get_training_plan` | 当前计划与安排 | `TrainingRepository.observeActivePlan` / `observePlanExercises` |

每个工具声明：唯一名称、描述、JSON Schema 参数、权限等级、超时、执行方法、返回结构。工具结果必须能追溯到实际 Repository 数据。

**Tool Calling 协议细则（修订收口）**：

1. 单个模型响应可包含多个 `tool_call`；每个调用有唯一 `callId`。
2. 多个工具允许并行执行，但结果必须按各自 `callId` 回传，禁止按完成顺序或数组下标错配。
3. 流式参数片段必须完整拼接、通过 JSON Schema 校验后才能执行；不完整片段不触发业务调用。
4. 工具失败返回结构化错误（`code`、`message`、`toolName`、`callId`），不能把异常文本直接塞给模型。
5. 工具响应必须按供应商协议格式回传模型；不同供应商差异由 AI 服务层吸收。
6. 只读白名单不可被模型输出覆盖；模型要求写库时只生成提案草稿，不执行写操作。

写入工具（阶段 F/G，且必须在 E 验收后）只能返回提案，不能直接落库。

### 3.4 业务层（复用，不重建）

- 饮食：`QingJiRepository` + `FoodLogDao` + `NutritionCalculator`。
- 训练：`TrainingRepository` + `TrainingViewModel` + `TrainingMath` + `SessionCalorieCalculator`。
- 有氧：`ExerciseLog` / `ExerciseCalculator` / `HealthRepository` 统一账本。
- 身体：`UserProfile` / `WeightLog` / `BodyMeasure` 及对应 DAO。
- 统计：`DailyHealthSummaryEntity` / `ProgressViewModel` / `WeeklySummaryCalculator`。

AI 分析层不重新计算卡路里、BMI、训练量或营养值；只消费本地计算结果并解释。

### 3.5 数据层

- `qingji.db`：正式业务数据 + 提案执行状态 + 幂等标记（与业务写入同事务）。
- `qingji_ai.db`：会话、消息、工具调用历史、Token 使用、AI 运行状态。
- `food_catalog.db`：预置食物库（assets 只读拷贝），不写 AI 猜测数据。
- `ApiKeyStore`：密钥加密存储；备份中只导出配置元数据，不含 Key。

跨库边界：会话删除不影响正式业务；正式业务记录删除后，历史消息中的引用只展示快照，不尝试回写。

### 3.6 交互层（阶段 D）

- 统一 AI 管家页：会话列表、消息流、流式输出、图片/语音入口、快捷问题、工具过程提示、分析卡片、提案卡片。
- 业务页上下文入口：首页/饮食/训练/训练历史/训练计划/身体数据/统计页携带上下文跳转同一会话系统。
- 不允许各业务页各自实现 AI 对话。
- 网络失败时保留输入和非 AI 功能可用。

## 4. 关键数据流

### 4.1 只读问答

```text
用户提问
 → AiAnalysis/AI 管家页收集上下文参数
 → ContextBuilder 构造摘要与数据范围
 → QingJiAgent 调用模型
 → 模型返回 tool_call
 → ToolExecutor 校验参数并调用 Repository
 → 结果回模型
 → 流式文本返回 UI
 → 消息落 qingji_ai.db
```

### 4.2 写入提案

```text
模型返回写入意图
 → ToolExecutor 只生成 ProposalDraft（不写库）
 → ProposalManager 记录 before/after/baseVersion/idempotencyKey
 → 用户确认页展示差异
 → 用户确认
 → 主库事务内校验版本 + 写正式数据 + 写执行状态/幂等键
 → 成功：标记 EXECUTED；冲突：STALE；失败：FAILED
 → AI 库仅更新提案展示状态
```

关键点：主库事务是最终一致性的来源；AI 库状态不能作为“业务已写入”的依据。

### 4.3 图片饮食识别

```text
图片/文本
 → AI 提取菜品、食材、做法、份量、置信度
 → FoodMatchingEngine 本地召回 Top20
 → 规则过滤/排序 Top5
 → 模型精排或标记无匹配
 → NutritionCalculator 本地计算
 → 用户确认卡片
 → 提案/正式写入
```

模型不得直接输出最终营养数值；无匹配必须明确返回。

### 4.4 数据来源与结论可信度标识（修订收口）

所有 AI 分析结果必须在消息/卡片中区分以下五类，用户可据此判断是事实还是推断：

| 类型 | 例子 | 来源 |
|---|---|---|
| 实际记录 | 今天记录了 1800 kcal | 本地正式业务记录 |
| 本地计算 | 蛋白质完成度 75% | `NutritionCalculator` 等本地引擎 |
| AI 推断 | 近期训练量可能偏低 | 模型解释，不写正式数据 |
| 数据不足 | 缺少最近三天体重记录 | ContextBuilder 缺失标识 |
| 建议 | 下次训练增加一组 | AI 建议，需用户决策 |

`ContextBuilder` 必须为每段上下文标注来源、时间范围和缺失标识；`ResponseParser` 拒绝把 AI 推断伪装成实际记录。

### 4.5 长期记忆治理（修订收口）

- 记忆必须标记「用户事实」或「AI 推断」，AI 推断不得自动升级为用户事实。
- 用户可查看、纠正、失效、删除长期记忆；删除会话不等于删除长期记忆。
- 记忆有 `createdAt`、`expiresAt`、`source`；过期自动失效。
- 按任务只检索相关类别记忆（训练问题只读训练记忆等）。
- 用户个人食物纠正写主库用户侧表，不污染公共食物词库。

## 5. 架构阻塞与已定稿决策（修订收口）

1. **Provider/SSE/Tool Calling 实现**：阶段 B 必须真实实现并测试；阶段 B 图片仅做能力声明与现有接口兼容，中餐识别放 F，语音不阻塞 B。
2. **AI 数据存储位置**：`qingji_ai.db` 存会话/消息/工具历史/Token/提案展示；主库存执行状态与幂等标记。
3. **提案与版本控制（定稿）**：训练计划父表 `version: Long + updatedAt` 为唯一版本源，计划日/动作明细随父版本整体替换；营养目标暂留 `UserProfile`，新增 `goalVersion + goalUpdatedAt`；所有人工/AI/模板/备份写入统一维护版本。
4. **跨库一致性（定稿）**：主库 `ai_proposal_execution` 是业务执行唯一权威；AI 库只展示，按 `idempotency_key` 对账；事务失败不在事务内写 FAILED，`EXECUTING` 超时由启动扫描恢复。
5. **食物匹配数据（定稿）**：通用别名作为版本化只读食物数据随资源版本发布；用户个人纠正存主库用户侧表；不把公共词库放 AI 库，模型不得修改。
6. **隐私与性能基线前置**：阶段 B 起强制 API Key 安全、日志脱敏、上下文最小化、请求取消、网络降级；K/L 做完整专项验收。
7. **测试基础设施**：阶段 B 必须补 MockWebServer / 流事件测试 / 工具调用测试；Robolectric UI 测试当前因网络问题跳过，不得虚报。

## 6. 阶段门禁映射

阶段编号以《完整实施路线图》为唯一来源；本设计不另设阶段口径：

| 阶段 | 名称 | 交付 |
|---|---|---|
| B | AI 基础设施 | AiProvider + 适配器 + 能力声明 + 错误模型 |
| C | Agent 与只读工具 | QingJiAgent + ToolRegistry + 4 只读工具 |
| D | AI 会话页面 | 统一 AI 管家 + 上下文入口 |
| E | 提案与安全执行 | ProposalManager + 版本/幂等 + 确认页 |
| F | AI 饮食助手 | FoodMatchingEngine + 识别确认链路 |
| G | AI 训练助手 | 训练分析 + 计划优化提案 |
| H | 身体数据综合分析 | 趋势/目标/报告 |
| I | 长期记忆 | 可管理记忆 |
| J | 主动 AI | 本地规则触发 + 频率控制 |
| K | 数据安全与隐私 | 数据最小化、密钥、日志、隐私控制专项 |
| L | 性能与稳定性 | 性能优化、备份恢复、异常恢复、Token/上下文 |
| M | 全量测试验收 | 正式交付报告 |

未通过上一阶段验收，不得进入下一阶段写入类功能。
