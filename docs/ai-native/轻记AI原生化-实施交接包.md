# 轻记 AI 原生化 · 实施交接包

> 记录时间：2026-11-25  
> 仓库：`C:/qingji-git/QingJi-Android`  
> 分支：`feature/health-system-v1.1`  
> HEAD：`00e2609ef849f247a32e9d79c84c2717f888f703`  
> 工作区：干净（无未提交改动）

---

## 1. 一句话结论

AI 原生化已从 B01 推进到 D 阶段：Provider 抽象、OpenAI 兼容适配器、SSE/Tool Calling 协议、四个只读工具、Agent 循环、AI 管家会话页、会话持久化均已落到代码；全量测试 520 个全部通过，Debug/Release 构建通过。

---

## 2. 当前验证状态

| 项 | 结果 |
|---|---|
| `testDebugUnitTest` | BUILD SUCCESSFUL |
| suites | 78 |
| tests | 520 |
| failures | 0 |
| errors | 0 |
| skipped | 0 |
| `assembleDebug` | 通过 |
| `assembleRelease` | 通过 |
| Compose UI 测试 | 1 个冒烟测试启用，无 skipped |

复现命令：

```bash
cd /c/qingji-git/QingJi-Android

./gradlew testDebugUnitTest --no-daemon --console=plain
./gradlew assembleDebug assembleRelease --no-daemon --console=plain
git status --short --branch
```

---

## 3. 提交链

| 提交 | 内容 |
|---|---|
| `b0f411b` | AiProvider 抽象层：接口、模型、能力、错误 |
| `0cae407` | OpenAiCompatibleProvider：普通生成、图片、错误映射、降级、取消、契约测试 |
| `c5dc50f` | Repository 迁移到 AiProvider；AiClient 降为兼容门面 |
| `2e3a94b` | B02/B03：SSE 文本流、Tool Calling 协议、流式工具参数 |
| `d81de51` | 收口：Compose UI 测试恢复、SSE 差异兼容、ToolExecutor 基础设施 |
| `6f870b4` | 清理误提交生成 zip |
| `31df3aa` | C01：四个只读工具与注册工厂 |
| `3934f34` | C02：ContextBuilder、QingJiAgent、响应可信度解析 |
| `371f7cd` | D：AI 对话页与首页统一管家入口 |
| `00e2609` | D：AI 会话列表与持久化 |

---

## 4. 已实现架构

### 4.1 Provider 层

主要文件：

- `app/src/main/java/com/qingji/app/data/remote/ai/AiProvider.kt`
- `app/src/main/java/com/qingji/app/data/remote/ai/AiModels.kt`
- `app/src/main/java/com/qingji/app/data/remote/ai/AiError.kt`
- `app/src/main/java/com/qingji/app/data/remote/ai/AiProviderCapabilities.kt`
- `app/src/main/java/com/qingji/app/data/remote/ai/OpenAiCompatibleProvider.kt`

已实现：

- `generate`：普通生成，文本/图片输入
- `stream`：SSE 文本流、流式 tool 参数、usage、错误事件
- `testConnection`：连接测试并声明能力
- 错误映射：网络、鉴权、限流、余额、模型不可用、超时、非法响应、取消
- `response_format` 400 降级重试一次
- `callId` 驱动的 tool_calls 解析与回传
- 供应商 SSE 差异兼容：
  - `data:` 无空格
  - `message` 回退
  - SSE error 事件
  - usage-only 片段
- 请求取消透传

### 4.2 Repository 兼容层

主要文件：

- `app/src/main/java/com/qingji/app/data/repository/QingJiRepository.kt`
- `app/src/main/java/com/qingji/app/data/remote/AiClient.kt`
- `app/src/main/java/com/qingji/app/data/remote/ai/AiPrompts.kt`
- `app/src/main/java/com/qingji/app/data/remote/ai/AiRecognitionParser.kt`

决策：

- `QingJiRepository` 已直接注入 `AiProvider`。
- `recognizeText` / `refineMeal` / `recognizeImage` / `analyze` / `testConnection` 全部改走 Provider。
- `AiClient` 保留旧签名，标注 `@Deprecated`，仅作为兼容门面。
- 提示词与识别解析从 `AiClient` 抽出。
- 新增 `ensureAiAvailable(date)` / `recordAiCall(date)` 给聊天入口复用 AI 配置闸门。

### 4.3 工具层

主要文件：

- `app/src/main/java/com/qingji/app/ai/ToolModels.kt`
- `app/src/main/java/com/qingji/app/ai/ToolRegistry.kt`
- `app/src/main/java/com/qingji/app/ai/ToolExecutor.kt`
- `app/src/main/java/com/qingji/app/ai/tools/ReadonlyTools.kt`

四个只读工具：

| 工具 | 作用 |
|---|---|
| `get_today_summary` | 今日饮食合计、运动消耗、饮水、餐次摘要 |
| `get_daily_nutrition` | 饮食明细，可选餐次，本地计算合计 |
| `get_training_history` | 训练历史，按日期/动作筛选 |
| `get_training_plan` | 当前启用计划或指定计划 |

`ToolExecutor` 规则：

- 只执行注册过的工具。
- 写入工具拒绝。
- JSON Schema required 最小校验。
- 并发上限默认 2。
- 结果按 `callId` 映射，顺序与请求一致。
- 单工具超时只影响该工具。
- 重复 `callId` 返回结构化错误。

### 4.4 Agent 层

主要文件：

- `app/src/main/java/com/qingji/app/ai/agent/AgentModels.kt`
- `app/src/main/java/com/qingji/app/ai/agent/ContextBuilder.kt`
- `app/src/main/java/com/qingji/app/ai/agent/QingJiAgent.kt`
- `app/src/main/java/com/qingji/app/ai/agent/AgentResponseParser.kt`

实现：

- `ContextBuilder` 最小化上下文：日期、任务类型、页面、少量摘要、只读工具清单、可信度规则。
- `QingJiAgent`：
  - 单工具 / 多工具 / 多轮循环
  - `callId` 回传
  - 最多 4 轮、单轮 5 工具、单任务 10 工具
  - 重复 `callId` / 参数指纹防护
  - 只读白名单
  - 工具错误结构化
  - 取消透传
- `AgentResponseParser`：
  - `FACT` / `CALCULATED` / `INFERRED` / `UNKNOWN` / `SUGGESTION`
  - 无工具证据时 `FACT` / `CALCULATED` 降级为 `UNKNOWN`

### 4.5 交互层

主要文件：

- `app/src/main/java/com/qingji/app/ui/screen/AiChatScreen.kt`
- `app/src/main/java/com/qingji/app/ui/viewmodel/AiChatViewModel.kt`
- `app/src/main/java/com/qingji/app/ui/navigation/Destinations.kt`
- `app/src/main/java/com/qingji/app/ui/navigation/QingJiAppRoot.kt`
- `app/src/main/java/com/qingji/app/ui/screen/HomeScreen.kt`

实现：

- 统一路由 `ai_chat/{context}`。
- 首页「✦ AI 管家」入口。
- 对话页：
  - 输入、发送、停止、重试、新会话
  - 会话列表 chips
  - 当前会话高亮
  - 工具执行过程提示
  - 可信度标签
  - 快捷问题
- `AiChatViewModel`：
  - 加载/切换会话
  - 保存消息
  - 删除失败消息用于重试
  - 页面关闭清理任务
  - 按上下文复用或创建会话

### 4.6 持久化层

主要文件：

- `app/src/main/java/com/qingji/app/data/local/ai/AiDatabase.kt`
- `app/src/main/java/com/qingji/app/data/local/ai/AiEntities.kt`
- `app/src/main/java/com/qingji/app/data/local/ai/AiChatDao.kt`
- `app/src/main/java/com/qingji/app/data/ai/AiChatStore.kt`
- `app/src/main/java/com/qingji/app/data/ai/RoomAiChatStore.kt`
- `app/schemas/com.qingji.app.data.local.ai.AiDatabase/1.json`

实现：

- 独立库 `qingji_ai.db` version 1。
- v1 一次建齐五张表：
  - `ai_conversation`
  - `ai_message`
  - `ai_tool_call`
  - `ai_proposal`
  - `ai_token_usage`
- `ai_message(conversationId, sequence)` 唯一索引。
- 会话软删除。
- 消息持久化包含正文、工具执行 JSON、可信度结论 JSON、warnings。
- 当前 `ai_tool_call` / `ai_proposal` / `ai_token_usage` 表已建但未完全业务化。

---

## 5. 测试覆盖

新增/覆盖测试：

- Provider 契约测试
- SSE 文本流与工具流
- 供应商 SSE 差异
- ToolExecutor 并发/callId/校验/超时
- 四个只读工具
- Agent 单工具/多工具/多轮/上限/去重/取消/可信度
- AiClient 兼容门面
- Compose UI 冒烟
- AiChatViewModel 会话/发送/失败重试/切换
- RoomAiChatStore 持久化读回

当前基线：

```text
suites=78
tests=520
failures=0
errors=0
skipped=0
```

---

## 6. 关键设计决策

1. **不把 AI 库塞进主库**：独立 `qingji_ai.db`，避免 AI 功能影响主库。
2. **AiClient 不删除**：保留为兼容门面，新代码禁止继续使用。
3. **AI DB v1 一次建齐**：后续新增字段必须走 v2 migration，不改 v1 schema。
4. **Agent 当前使用 generate 循环**：Provider 已有流式能力，但 UI 尚未接流式渲染。
5. **只读工具先行**：写入工具与提案系统必须等 E 阶段。
6. **结果可信度强制五类标识**：模型不能把推断写成事实。
7. **API Key 不进数据库、不进日志**。
8. **AI 调用按任务计一次**：Agent 内部多轮不重复扣额度。

---

## 7. 未完成事项

### D 阶段剩余

- 流式文本渲染（Provider 已有 `stream()`，UI 未接）。
- 图片 / 语音输入入口。
- 更多业务页上下文入口：
  - 饮食
  - 训练历史
  - 训练计划
  - 身体数据
  - 统计页
- 会话重命名 UI。
- 会话删除按钮（Store 已支持软删除，UI 未放按钮）。
- AI 库备份/恢复与 factory reset 清理接入。
- 长会话性能优化（分页/窗口化）。

### E 阶段

- 主库 v15→v16 迁移实际编码：
  - 版本字段
  - `ai_proposal_execution`
  - 训练计划/营养目标版本
- 提案系统与 ProposalManager。
- 写入工具与白名单执行。
- 提案状态对账、超时恢复、幂等键。
- 工具失败后的重试与退避策略。

### F 阶段

- 食物别名补丁 v1 落地，验收样例词 8/8。
- 版本化只读食物数据资源包。
- 用户个人纠正与公共别名分层。

---

## 8. 接续开发建议

### 优先顺序

1. **D 收口**：流式渲染 + 更多入口 + 图片/语音。
2. **E 前置**：主库迁移 SQL 实际落地与迁移测试。
3. **E 核心**：提案状态机 + 写入工具禁用/提案转换。
4. **F**：别名补丁与覆盖率验收。

### 从这些文件开始读

```text
app/src/main/java/com/qingji/app/data/remote/ai/AiProvider.kt
app/src/main/java/com/qingji/app/data/remote/ai/OpenAiCompatibleProvider.kt
app/src/main/java/com/qingji/app/ai/agent/QingJiAgent.kt
app/src/main/java/com/qingji/app/ai/tools/ReadonlyTools.kt
app/src/main/java/com/qingji/app/data/ai/RoomAiChatStore.kt
app/src/main/java/com/qingji/app/ui/viewmodel/AiChatViewModel.kt
app/src/main/java/com/qingji/app/ui/screen/AiChatScreen.kt
```

---

## 9. 风险与注意事项

- `QingJiRepository` 中旧的 AI 分析方法仍保留结果契约；新聊天链路不要绕回 `AiClient`。
- `AiClient` 已 `@Deprecated`，如果将来有人重新调用，会绕过 Agent 可信度与工具链。
- `HomeInteractionTest` 是单冒烟测试，不能替代完整业务导航回归。
- `RoomAiChatStoreTest` 使用内存库，必须保持当前 `runBlocking` + `close()` 写法，避免 JVM 测试 worker 残留连接。
- `AiDatabase` v1 包含未使用表；后续加字段必须新增 migration 和 schema 文件。
- 聊天页尚未接 `AiProvider.stream()`，所以当前显示的是一次性结果，不是逐字输出。
- AI 库尚未接入备份恢复；用户清除数据时也不会自动清 AI 库。
- SSE 供应商差异仅覆盖测试样本，不保证所有厂商实现一致。

---

## 10. 交接验收清单

接手人可逐项确认：

- [ ] `git status --short --branch` 干净
- [ ] `./gradlew testDebugUnitTest --no-daemon --console=plain` 全绿
- [ ] `./gradlew assembleDebug assembleRelease --no-daemon --console=plain` 全绿
- [ ] 首页能看到「✦ AI 管家」入口
- [ ] 进入后能发送问题并得到本地工具查询结果
- [ ] 新会话 / 会话切换正常
- [ ] 重启 App 后会话与消息仍存在
- [ ] 失败回答可重试
- [ ] 设置中关闭 AI 后，聊天入口请求被闸门拦截

---

## 11. D 阶段收口补记（2026-11-25）

> 提交：`c695923 feat(ai): complete D stage AI chat handover items`
> 版本不变；本轮只做 D 阶段剩余项，未进入 E（提案/写入）。未推送远端。

### 11.1 已完成

| 交接包原列项 | 本轮落地 |
|---|---|
| 流式文本渲染 | `QingJiAgent.runStreaming()` + `AnswerStreamFilter`（只吐 JSON 里的 answer，不漏 JSON 片段）；`AiChatViewModel` 三参 runner 接片段；`AiChatScreen` 随文本自动滚动 |
| 会话重命名 UI | `AiChatStore.renameConversation()` + ViewModel + 会话条旁「改名」弹窗 |
| 会话删除按钮 | ViewModel 原已支持；聊天页补「删除」按钮与确认弹窗，提示删除会话不等于删长期记忆 |
| 更多业务页上下文入口 | 饮食详情、训练日记、训练计划、体重、进展 5 个页面接入「AI 管家」，分别带页面上下文 |
| 图片 / 语音输入 | 聊天页加「图片」（系统相册 → base64 内存直传，2MB 上限，不入库不进日志）与「语音」（系统识别 → 填入输入框，可改再发） |
| 长会话性能 | `AiChatDao.observeRecentMessages` + `countMessages`；默认只载最近 100 条，顶部「加载更早消息」每次 +100 |
| AI 库备份 / 恢复 | **未做**：路线图归 L 阶段，备份格式升级需单独工单。本轮只做「恢复出厂」时清空 AI 库 5 张表 |

### 11.2 验证

| 项 | 结果 |
|---|---|
| `testDebugUnitTest` | 79 suites / 529 tests / 0 failures / 0 errors / 0 skipped |
| `assembleDebug` | BUILD SUCCESSFUL |
| `assembleRelease` | BUILD SUCCESSFUL |
| 新增测试 | 流式 answer 过滤 4 例、Agent 流式 1 例、ViewModel 流式/改名/图片语音 3 例、Room 改名与清空 1 例 |

### 11.3 风险与边界

- 工具调用轮次仍按 generate 语义执行，流式只用于最终 answer；Agent 轮次/工具上限未变。
- 供应商 SSE 差异仍只覆盖测试样本；流式渲染未做真机逐字视觉验收。
- 图片只走多模态 provider 能力；模型不支持图片时会返回结构化错误，不会静默丢图。
- 语音依赖设备系统识别能力，缺失时提示手动输入。
- AI 库尚未进入备份导出/恢复；只保证恢复出厂会清干净。
- 本提交仍在本地 `feature/health-system-v1.1`，未推送；远端 main 仍是 v1.4.5 生产线，合并前需先处理两线分叉。
