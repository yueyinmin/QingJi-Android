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

---

## 12. Git 线收口：AI 链已移植到 main 基线（2026-11-25）

### 12.1 备份与 fetch

- 整目录备份：`C:/qingji-git/QingJi-Android-backup-20261125-1737`（753M，含 .git 与 build）。
- `git fetch origin` 后确认：`origin/main = 5caaa99`（v1.4.5 生产线），`origin/feature/health-system-v1.1 = 9eb3065`。
- 两线共同祖先 `839019b`，分别领先 253 / 134 提交；整条 feature 并入 main 预演有 81 个冲突文件，放弃该方案。

### 12.2 采用的方案

- 以 `origin/main 5caaa99` 为准，新建本地分支 `ai-native-on-main`，cherry-pick AI 链 13 个提交（跳过 `c97ada3`，其内容已被 main 的 `d5ae94e` 覆盖）。
- 冲突处理：保留 main 的 `analyzeExecutor` 注入、`getApiKeyFor(config.id)` 多配置取 Key、带日期 AI 分析、自定义训练计划编辑；AI 侧只合入 Provider/Agent/工具/会话/UI 能力。
- main 没有 `DietDetail` 页，饮食入口改挂在 `MealDetailScreen`（带「饮食页」上下文）。
- 恢复 main 版 `HomeInteractionTest`（3 个 skipped）：AI 分支版冒烟测试与 main 的 UI 不兼容，会导致 Compose 不 idle。
- 修复提交：`e8178d3 fix(ai): keep main's ignored compose smoke test after cherry-pick`。

### 12.3 验证

| 项 | 结果 |
|---|---|
| `testDebugUnitTest` | 153 suites / **1010 tests / 0 failures / 0 errors / 3 skipped** |
| `assembleDebug` / `assembleRelease` | BUILD SUCCESSFUL |
| 偶发项 | `BackupExportScopeTest` 首次全量跑出现 SQLite 指针错误，单独跑与复跑全量均通过；历史已知问题 |

### 12.4 当前分支状态

- `ai-native-on-main` 本地领先 `origin/main` 14 个提交；**未推送**。
- 原 `feature/health-system-v1.1` 保留不动（HEAD `b69f48a`），作为历史参考。
- 后续若发布，需先确认 Release 与版本号，再决定推送/合并方式。

---

## 13. E 阶段实施（2026-11-25）

> 提交：`345590f feat(ai): implement E stage migration CAS and proposal manager`
> 分支：`ai-native-on-main`（从 `origin/main 5caaa99` 移植后的线）。

### 13.1 迁移编号适配

- 收口报告写的是 v15→v16，因为当时设计基线是 v15。**当前 main 已是 v22**，v15→v16 已被其它功能占用。
- 本轮按相同 SQL 语义落为 **`MIGRATION_22_23`**：主库版本 22→23，schema 导出 `23.json`，迁移链 `ALL_MIGRATIONS` 已同步。
- 内容：`training_plan.version/updatedAt`、`user_profile.goalVersion/goalUpdatedAt`、`ai_proposal_execution`（幂等键唯一索引）、`ai_operation_log` 及索引。

### 13.2 版本控制

- 训练计划父表 `version` 为唯一 CAS 源，子表随父整包替换。
- 6 个写入入口全部维护版本：`activatePlan`、`useTemplate`（委托）、`saveSessionAsTemplate`、`deactivatePlan`、`reactivatePlan`、`deletePlan`。
- `deactivateAll` 改为 `deactivateAllWithVersion`：被停用计划全部递增版本。
- 新增 `casSetActiveForProposal` / `casDeleteForProposal` / `planVersion` 供提案执行器使用。
- 备份恢复沿用整包 `insertAll`，实体已含 version，不会被重置为 0。
- `user_profile` 新增 `casUpdateGoals()`，人工目标写入与 AI 提案共用同一路径（UI 接入留后续阶段）。

### 13.3 提案状态机

- 新类：`ProposalManager`、`ProposalExecutor`、`PlanProposalExecutor`。
- 主库 `ai_proposal_execution` 是唯一权威；AI 库 `ai_proposal` 只展示状态。
- 幂等键 = 提案内容 SHA-256，唯一索引兜底重复确认。
- 成功：主库事务内 CAS → 业务写入 → EXECUTED → 审计；提交后同步 AI 展示。
- 失败：事务回滚后在事务外写 STALE/FAILED，避免 FAILED 被回滚抹掉。
- `recoverExecuting()`：扫描超时 EXECUTING，按执行器判定已落库 → EXECUTED，否则 FAILED。
- `reconcile()`：主库权威单向覆盖 AI 展示；PENDING 提案过期→EXPIRED、版本不匹配/目标缺失→STALE。
- 恢复出厂已清空 `ai_proposal_execution` / `ai_operation_log`。
- **写工具继续禁用**：`ToolRegistry` 仍只注册 4 个只读工具，本轮未向 Agent 暴露任何写入能力。

### 13.4 验证

| 项 | 结果 |
|---|---|
| 新增测试 | 迁移 1 组、CAS 2 例、提案 4 例 |
| `testDebugUnitTest` | 156 suites / **1017 tests / 0 failures / 0 errors / 3 skipped** |
| `assembleDebug` / `assembleRelease` | BUILD SUCCESSFUL |
| 偶发项 | `BackupExportScopeTest` 首次全量跑 SQLite 指针错，复跑全绿（历史已知） |

### 13.5 边界与后续

- E 阶段执行器只覆盖训练计划三类（启用/停用/删除），饮食/训练内容替换执行器留 F/G。
- 营养目标 CAS DAO 已就绪，但设置页尚未切到 CAS 写入路径；AI 目标提案未开放。
- 提案确认 UI、EXECUTING 启动扫描的 App 启动时调用点未接入；当前 Manager 可通过测试/后续阶段显式调用。
- 写工具仍禁用，满足「E 不过不开放写入」纪律。

---

## 14. F 阶段实施（2026-11-25）

> 提交：`bf9584f feat(ai): implement F stage alias patch and matching engine`

### 14.1 落地内容

- 新增版本化只读资源：`app/src/main/assets/food_alias_patch/food_alias_patch_v1.json`，`schemaVersion=1`、`appliesToCatalogVersion=0.9-lxj-kcal-match`。
- 新增 `FoodAliasPatchLoader`：从 assets 加载、校验 schema 与食物库版本；版本不匹配直接失败，调用方禁用补丁。
- 新增 `FoodMatchingEngine`：完全命中（名称/别名/补丁别名/拼音）→ LIKE 补位 → Top20 排序 → Top5；多候选全部保留，不自动收敛；无匹配返回空。
- `QingJiRepository` 的 AI 识别本地校正候选表并入补丁别名；补丁失败时跳过，保持旧行为。
- 补丁只读，**不写回 `food_catalog.db`**；用户个人纠正继续走用户侧表（本轮未新增表）。

### 14.2 验收

| 项 | 结果 |
|---|---|
| 8 个样例词 | **8/8 可达**，3 个原不可达词（木樨肉/尖椒肉丝/宫爆鸡丁）通过补丁可达 |
| 多来源 | 宫爆鸡丁保留 3 个候选，不自动选一个 |
| 版本校验 | 食物库版本不匹配时 loader 失败，禁用补丁 |
| 无匹配 | 返回空列表，不强行选相似食物 |
| 新增测试 | loader 2 例 + engine 4 例 |
| `testDebugUnitTest` | 158 suites / **1023 tests / 0 failures / 0 errors / 3 skipped** |
| `assembleDebug` / `assembleRelease` | BUILD SUCCESSFUL |
| 偶发项 | `BackupExportScopeTest` 首次全量跑 SQLite 指针错，复跑全绿 |

### 14.3 边界

- 食谱组合菜走 `food_composition` 的召回尚未接入本引擎；当前引擎覆盖标准库与补丁别名。
- 模型辅助精排、饮食确认卡片 UI、经提案正式写入饮食记录仍属 F 后续联调项。
- 用户个人别名/纠正表未新增；本轮只保证公共补丁不被运行时写入污染。
- 写工具继续保持禁用。

---

## 15. G 阶段实施（2026-11-25）

> 提交：`ad91dd6 feat(ai): implement G stage training analysis and plan suggestion`

### 15.1 落地内容

- 新增 `TrainingAnalysisEngine`：力量/有氧/HIIT 分开统计训练量、频率、RPE、时长与间隔；数据不足标 UNKNOWN，不伪造恢复指标。
- `TrainingRepository.recentTrainingSnapshots(days)`：只读快照，不改统一运动体系。
- 新增只读工具 `analyze_training` 与 `suggest_plan_change`；两者都只返回分析结果/提案草稿，不修改计划。
- 新增 `PlanChangeSuggester`：基于分析生成 before/after 差异与当前 baseVersion；训练过密→建议停用计划，无力量训练→建议启用已有计划。
- 提案必须经 `ProposalManager` 确认后 CAS 执行；本轮没有绕过提案直接改计划的路径。

### 15.2 验证

| 项 | 结果 |
|---|---|
| 三类运动分别统计 | 力量/有氧/HIIT 分开，窗口外记录不计 |
| 数据不足 | UNKNOWN + 警告，不编恢复指标 |
| 提案差异 | before/after JSON + baseVersion；确认后由 E 的 CAS 执行器版本 +1 |
| 新增测试 | 分析 2 例 + 提案 3 例 |
| `testDebugUnitTest` | 160 suites / **1028 tests / 0 failures / 0 errors / 3 skipped** |
| `assembleDebug` / `assembleRelease` | BUILD SUCCESSFUL |
| 偶发项 | `BackupExportScopeTest` 首次全量跑 SQLite 指针错，复跑全绿 |

### 15.3 边界

- 自然语言的「训练反馈→分析」对话呈现、计划优化 UI 确认卡片未接。
- 计划内容级替换（组数/次数/重量整包修改）执行器未开放；本轮提案只覆盖启用/停用。
- 写工具继续保持禁用；`suggest_plan_change` 只返回草稿，不执行。

## 16. H 阶段实施（2026-11-25）

> 提交：`0981b51 feat(ai): implement H stage body analysis and health report`

### 16.1 落地内容

- 新增 `BodyAnalysisEngine`：体重趋势（最新值、7/28 天变化、7 天与窗口平均、每周速度、方向）、BMI、体脂、围度、记录完整度、目标进度，全部本地算。
- 新增 `BodyTrust` 五级口径：`RECORDED / CALCULATED / AI_INFERRED / MISSING / SUGGESTION`；规则层只产出前三类 + MISSING，另外两类是写给模型看的标注约定。
- 新增 `HealthReportBuilder`：把身体数据、训练分析（复用 G）、饮食记录完整度拼成综合报告；饮食平均只看有记录的天，缺记录不按 0 算。
- 新增只读工具 `get_body_metrics`、`get_health_report`；两条都 `readOnly = true`，只回数字与信任标签。
- `QingJiRepository.observeFoodLogsRange(from, to)`：区间只读查询，供报告统计。
- 工具输出统一附 `trustLegend` 与 note：推断标 `AI_INFERRED`、建议标 `SUGGESTION`、不得声称改了身体数据。

### 16.2 验证

| 项 | 结果 |
|---|---|
| 体重趋势 | 记录跨度 <14 天不给速度；未来日期与 20~400kg 外的异常值排除 |
| BMI | 一律走 `NutritionCalculator.bmi`，身高缺失返回 null + MISSING，不让模型算 |
| 体脂 / 围度 | 没有记录就 MISSING，绝不拿体重反推体脂；腰围间隔 <7 天不比较 |
| 目标进度 | 需起始/目标体重齐全；方向相反时不给预计周数并给警告 |
| 饮食完整度 | 平均热量按有记录天数算；<3 天标数据不足 |
| 新增测试 | 身体分析 13 例 + 综合报告 5 例 |
| `testDebugUnitTest` | 162 suites / **1046 tests / 0 failures / 0 errors / 3 skipped** |
| `assembleDebug` / `assembleRelease` | BUILD SUCCESSFUL |
| 偶发项 | `BackupExportScopeTest` 首次全量跑 SQLite 指针错，复跑全绿 |

### 16.3 边界

- 报告只在对话里解释，没有独立的「健康报告」页面；数据仍由用户在体重/围度页录入。
- 没有体脂/围度设备接入，也没有基础代谢二次校准。
- 未做医学风险提示分级（如 BMI 过低/过高时的就医建议），只给数值分类。
- 写工具继续保持禁用；本轮 8 个工具全部只读。

## 17. I 阶段实施（2026-11-25）

> 提交：`e7e4c9d feat(ai): implement I stage long-term memory and management`

### 17.1 落地内容

- AI 库 v1 → v2：新增 `ai_memory` 表（分类/内容/可信度/来源/来源 id/置信度/状态/过期时间/去重键），三个索引（类别、状态、去重键唯一）；`MIGRATION_1_2` 只加表不动老表，schema `2.json` 已导出。
- 新增 `domain/memory/MemoryModels.kt`：`MemoryCategory`（偏好/训练/饮食/目标/事件/推断）、`MemoryTruth`（USER_FACT / CONFIRMED / INFERRED）、`MemorySource`、`MemoryStatus`、`MemoryRecord`、`MemoryNote`、`MemoryInjection`。
- 新增 `MemoryStore` + `RoomMemoryStore`：只做实体↔领域转换；`insert` 唯一键冲突返回 false（幂等）。
- 新增 `MemoryManager`：
  - 只有句首「记住 / 帮我记住 / 记一下 / 以后都…」才触发写入，标 USER_FACT；正文里的「记住」不算；聊天正文不会自动收集。
  - AI 推断只能以 INFERRED 落库，置信度上限 0.8，已有同内容用户事实时拒绝覆盖；**没有任何自动升级路径**，必须 `confirm()` 显式确认（INFERRED → CONFIRMED）。
  - 按任务检索：饮食页只读饮食/偏好；训练页读训练/偏好/目标；身体/统计读目标/偏好；推断类最多注入 1 条。
  - 条数（6）与字数（500）双预算，超了标 `truncated`。
  - 纠正、失效、删除、过期扫描（`expiresAt` 到期标 EXPIRED，不再注入）。
  - 去重键 = 类别 + 去空白标点小写内容的 SHA-256。
- 新增 `AiMemoryPrefs`（独立 DataStore）：每个话题类的「不再记住这类内容」开关，只影响以后的新记忆。
- `ContextBuilder` 注入长期记忆：逐条带 `[可信度][类别]`，并新增 3 条硬规则（记忆不是本轮发言、推断不能单独下结论、不要复述全部记忆）。
- `AiChatViewModel`：发送时若用户说「记住…」则写入并提示「已记住」；构造请求时按页面检索记忆注入。
- 删除会话不删记忆（`deleteConversation` 只软删会话）；恢复出厂清 `ai_memory` + 记忆开关。
- 新增记忆管理页 `MemoryScreen` + `MemoryViewModel`：类别筛选、纠正、失效、删除（二次确认）、推断确认、类别开关；聊天页常驻「记忆」入口。

### 17.2 验证

| 项 | 结果 |
|---|---|
| 迁移 1→2 | 老会话数据保留、记忆表可写、三个索引齐全 |
| 写入边界 | 只有句首触发词写 USER_FACT；正文「记住」不触发；重复内容不写第二遍 |
| 推断边界 | 推断只能 INFERRED，置信度 >0.8 拒绝；用户事实不可被推断覆盖；确认后才 CONFIRMED |
| 检索边界 | 饮食问题读不到训练记忆；失效/过期不注入；推断最多 1 条；条数/字数截断 |
| 会话与记忆 | 删除会话后记忆仍在；恢复出厂才清空 |
| 新增测试 | 规则 17 例 + 存储 4 例 + 迁移 2 例 + 注入 3 例 |
| `testDebugUnitTest` | 166 suites / **1074 tests / 0 failures / 0 errors / 3 skipped** |
| `assembleDebug` / `assembleRelease` | BUILD SUCCESSFUL |
| 偶发项 | `BackupExportScopeTest`、`TrainingViewModelB112Test` 首次全量跑偶发失败，复跑全绿 |

### 17.3 边界

- 没有自动观察入口：本阶段不会「偷偷」从对话里总结记忆，写工具仍禁用；`captureInferred` 只留给后续阶段调用。
- 备份/恢复时 sourceRef 失效的校验归 K 阶段；记忆随 AI 库整体备份。
- 记忆条数上限与默认过期时间留到 L 阶段按性能定；当前默认不过期，靠用户手动失效/删除。
- 没有做「删除会话时同时删除相关记忆」的二次确认流程，只保证两者互不影响。


## 18. J 阶段实施（2026-11-25）

> 提交：`feat(ai): implement J stage proactive nudge`（见 `git log`）

### 18.1 落地内容

- AI 库 v2 → v3：新增 `ai_nudge` 表（类别/触发原因/日期键/标题/正文/关键数字/是否需分析/反馈状态/发送时间），三个索引；其中 `(kind, dateKey)` 是**唯一索引**，一天同一类提醒只可能落一条。`MIGRATION_2_3` 只加表不动老表，schema `3.json` 已导出。
- 新增 `domain/nudge/NudgeModels.kt`：`NudgeKind`（四类 + 优先级）、`NudgeReason`（六种触发细节）、`NudgeFacts`（本地事实快照）、`NudgePolicy`（开关/额度/静默时段/冷却）、`NudgeSkipReason`、`NudgeDecision`、`NudgeRecord`。
- 新增 `domain/nudge/NudgeRuleEngine.kt`（纯函数，无 IO）：
  - 记录不完整：20:00 后今天饮食记录 ≤1 条；
  - 蛋白不足：19:00 后、蛋白目标已设、今天 ≥2 餐、蛋白 < 目标 60%；
  - 计划未完成：20:00 后、有启用计划、今天有安排（休息日不算）、还没有训练记录；
  - 体重偏离：目标体重已设、最新记录不超过 3 天、离目标 >0.5kg；近 7 天均值比前 3 周均值反向漂移 ≥0.4kg 判「方向相反」，14 天内峰谷差 ≤0.3kg 且离目标 >1kg 判「停滞」。
  - 一天只发一条：四类按优先级排序取最高；被关掉或在冷却的自动顺延到下一条，全都不可用就沉默。
  - 三种「不说话」的理由分开记：`NO_TRIGGER`（判断过且正常）/ `INSUFFICIENT_DATA`（一条都没法判断）/ `COOLDOWN`。
- 新增 `data/prefs/NudgePrefs.kt`（独立 DataStore `ai_nudge_prefs`）：总开关、单类开关、每天 1～2 条、静默时段（默认 22:00～08:00，支持跨午夜，起止相同视为关闭）。
- 新增 `data/ai/NudgeStore.kt` + `RoomNudgeStore.kt`：只做存取与实体转换；`insert` 返回 false 即「今天这条已经发过」。
- 新增 `ai/nudge/NudgeFactsSource.kt`（读事实的接口 + 策略来源 + 静默期计算）、`RepositoryNudgeFactsSource.kt`（复用现有 Repository 查询：今日 `food_log`、`weight_log` 近 28 天、`workout_session` 今日、`todayPlanExercises()`、`user_profile` 目标）、`NudgeManager.kt`。
- 关键边界：**NudgeManager 不调用任何模型**。进页面只跑本地规则，文案由规则层用本地数字拼出；只有用户主动点「让管家看看」，才把该条提醒里的几个数字变成问题发进对话（走已有只读工具）。
- 接入点：
  - `AiChatViewModel`：`loadNudge()`（每次进页面算一次）/`dismissNudge()`/`askAboutNudge()`；`AiChatUiState.nudge` 驱动卡片；测试注入 store 时不启用（不碰真库）。
  - 聊天页新增「管家提醒」卡片（`ui/components/NudgeCard.kt`）与常驻「提醒」入口。
  - 新增 `ui/screen/NudgeScreen.kt` + `ui/viewmodel/NudgeViewModel.kt`：开关、每日条数、静默时段三档、四类单独开关、最近 30 条历史（可单条删除 / 整体清空）。
  - 路由 `QingJiRoutes.AiNudge = "ai_nudge"`，已加入全屏路由集合。
  - 恢复出厂：`AiChatDao.clearAll()` 增加清 `ai_nudge`，`NudgePrefs.clear()` 与其他独立 DataStore 一起清。

### 18.2 验证

| 项 | 结果 |
|---|---|
| 迁移 2→3 | 会话与记忆老数据保留、提醒表可写、三个索引齐全、同类同日第二次写入被唯一索引拦下 |
| 闭嘴上限 | 总开关关闭 / 静默时段（含跨午夜 22:00–08:00 与 21:00–07:00）/ 当天额度用完 / 同类冷却（含体重 72 小时冷却）全部不落库 |
| 四类触发 | 记录不完整、蛋白不足、计划未完成、体重方向相反、体重停滞各有正反用例；休息日、已训练、只吃一餐、没设目标、记录超过 3 天均不误报 |
| 优先级与顺延 | 体重 > 计划 > 蛋白 > 记录；最高优先级被关掉或在冷却时顺延到下一条；全不可用则沉默 |
| 去重 | 同一 manager 连续两次 `check()` 只落一条（第二次额度/冷却拦下）；`checkRecord()` 第二次返回 null |
| 降级 | 事实来源抛异常 → `INSUFFICIENT_DATA`，不崩、不落库 |
| 隐私边界 | `analysisPrompt` 只带本地算好的键值对与「先核对再说建议」，不带原始记录明细 |
| 新增测试 | 规则 26 例 + 管家流程 10 例 + 迁移 2 例 |
| `testDebugUnitTest` | 169 suites / **1110 tests / 0 errors / 3 skipped**；唯一失败是既有偶发（见下） |
| `assembleDebug` / `assembleRelease` | BUILD SUCCESSFUL |
| 既有偶发（非本阶段引入） | `BackupExportScopeTest` 在全量跑时抛 Robolectric `Illegal connection pointer`；用基线提交 `d4c1e55` 的 worktree 全量复现同一条失败（1074 tests 同一条），单跑与「AI 迁移 + 备份」组合跑均通过 → 既有测试隔离问题（主库单例跨类残留），留给 L 阶段处理 |

### 18.3 边界

- 不做后台定时检查、不发系统通知、不弹窗：只有用户打开管家页那一刻才判断一次，避免「每次开 App 都打扰」。
- 不做「记录不完整」以外的数据补全建议：卡只说事实与差额，不替用户排菜单。
- 提醒只落在管家页顶部，首页/其他业务页不插入卡片（K 阶段要统一入口时再评估）。
- 静默时段只提供三档预设，不开放任意时刻编辑；体重偏离的冷却固定 72 小时，不可单独调。
- 写工具继续保持禁用；本轮仍未新增任何写工具（8 个工具全部只读）。

## 19. K 阶段实施（2026-11-25）

> 提交：`feat(ai): implement K stage data privacy hardening`（见 `git log`）

### 19.1 落地内容

**一、API Key 隔离专项（复核 + 补硬保证）**

| 原来就有 | 本轮补的 |
|---|---|
| Keystore AES/GCM 256，alias `qingji_ai_api_key`，密文+IV 存 `qingji_secure_prefs`，按 `configId` 分组互不覆盖 | 系统备份规则改为「只 include database」，并显式排除 AI 库；sharedpref 因为不在 include 范围内天然不备份 |
| Key 不进 Room、不进 App 导出 JSON、不回声（已保存时留空=不改）、界面掩码输入 | 导出 JSON 增加整份文本扫描断言：不得出现 `sk-`、`cipher_text`、`"iv"`、`Bearer `、Keystore 别名 |
| 解不开就清残片并返回 null（要求重填，不静默显示已保存） | 保留不变；本轮把这条写进验收表，避免以后被人「优化」成静默兜底 |

**二、日志脱敏专项**

- 新增 `domain/privacy/SecretRedactor`（纯函数、无 IO、不依赖 Android）：`Bearer xxx`、`sk-xxx`、`api_key/token/secret/password=...`、单独的 `key=`（值 ≥8 位才打码，避免误伤「key: 键盘布局」）、URL 里的 `user:pass@`、≥32 位字母数字混合长串，统一换成 `***`；先脱敏后截断，防止半截 token 绕过识别。
- 接入点：
  - `AiError.InvalidResponse` / `AiError.Unknown`：在构造时脱敏（message 会进 UI、进聊天记录）；
  - `OpenAiCompatibleProvider`：HTTP 错误正文、非 JSON 返回、流式 `error` 事件、连接测试结果，4 处都先过脱敏；
  - `AiChatViewModel.readableError()`：所有异常统一再过一遍再落库，防止新异常类型漏网。
- release 包在 `proguard-rules.pro` 里去掉 `Log.v/d/i`（保留 `w/e`）：少一类泄漏面，也少刷 logcat。**现有 21 处 `Log.*` 已逐条复核**，都是表名/组件名 + 通用描述，不含 Key、Token、请求体。

**三、数据删除覆盖 AI 数据**

- 会话删除从「打 `deletedAt` 标记」改为**事务内真删**：`ai_message`、`ai_tool_call`、`ai_token_usage`、`ai_conversation` 一起清；其它会话与 `ai_proposal`（无会话外键的展示镜像）不动。
- 记忆按用户选择处理：不勾选 → 该会话产生的记忆标 `INVALIDATED`（保留可查看，不再注入）；勾选 → 直接删除。删除弹窗会先查出条数（`countMemoriesFrom`），并列出将删除项，符合《长期记忆数据边界设计》第 76–82 行。
- 新增 `ai/privacy/AiPrivacyCleaner`：一键清 AI 库 7 张表 + `ai_memory_prefs` + `ai_nudge_prefs`，并回报「删了多少个会话/消息/记忆/提醒」。**保留 AI 服务配置与 Key**（那是配置，不是内容；要连 Key 一起清走恢复出厂）。
- 隐私页新增第三档「AI 数据」：位于「清除记录」与「恢复出厂」之间，文案写清删什么、留什么；确认弹窗先显示真实规模。
- 记忆自检：打开记忆页时跑一条 SQL，把 `sourceRef` 是 UUID 但已找不到对应消息的记忆标 `INVALIDATED`（只处理 36 位 UUID，避免误伤未来可能写入的「业务记录 id」）。失败静默，不影响浏览。

**四、用户数据与 AI 数据边界**

- 主库（业务记录，用户自己录的）是源；AI 库（会话/消息/工具/记忆/提醒）是派生。删 AI 数据一条业务记录都不动。
- 系统云备份与换机直传：只带主库。**AI 库整体排除**——聊天原文与长期记忆不随「用户无感上传」进厂商云；要带走请走 App 内主动导出（L 阶段做 AI 库备份段）。
- 长期记忆仍只存结构化短句（最长 120 字）、带来源与可信度，不存聊天全文。

**五、权限与组件复核（逐个核对 `AndroidManifest.xml`）**

| 权限 | 用途 | 触发时机 | 拒绝后 |
|---|---|---|---|
| `INTERNET` | 调用户自填的 AI 服务 | 只在你点「识别 / 问管家」时；不配 Key 完全不联网 | 本地功能照常 |
| `RECORD_AUDIO` | 语音记账 | 按下麦克风才申请 | 可继续手动输入 |
| `POST_NOTIFICATIONS` | 提醒 / 断食通知 | 设置页里开启提醒时引导 | 提醒不弹，记录照常 |
| `RECEIVE_BOOT_COMPLETED` | 重启后重排闹钟 | 系统广播，仅重排 | 重启后提醒失效 |
| `FOREGROUND_SERVICE`(+`SPECIAL_USE`) | 断食常驻计时 | 开始断食时 | 不计时，历史记录不受影响 |

组件导出面：`MainActivity` 与 9 个 `activity-alias` 为桌面入口（必要，MainActivity 本身无 intent-filter，外部需精确类名）；`FileProvider` `exported=false` + `grantUriPermissions`，路径白名单只有 `images/`、`images/`（缓存）、`backup/` 三个；所有 receiver / service 均 `exported=false`。

### 19.2 验证

| 项 | 结果 |
|---|---|
| 脱敏规则 | 13 例：`sk-`、`Bearer`、`api_key=`、`token:`、`?key=`、`password=`、URL 账号密码、长串、误伤保护（中文/短词/「key: 键盘布局」）、截断、`containsSecret` 自检 |
| 会话深删 | 6 例：消息/工具/用量真删、其它会话不受影响、记忆默认失效、勾选直删、不影响别的会话的记忆、删除前计数、来源自检只动 UUID 形式 |
| 一键清 AI 数据 | 1 例：统计 → 清空 → 整库复核为空、摘要文案正确 |
| 备份不含密钥 | 追加断言：整份导出 JSON 无 `sk-` / `cipher_text` / `"iv"` / `Bearer ` / Keystore 别名 |
| 权限与组件 | 5 项权限 + 全部组件逐个核对，结论见上表 |
| `testDebugUnitTest` | 172 suites / **1129 tests**（+19）/ 0 errors / 3 skipped；唯一失败仍是既有偶发 `BackupExportScopeTest`（单跑通过，含本轮新增断言） |
| `assembleDebug` / `assembleRelease` | BUILD SUCCESSFUL（release 的 lintVital 已通过，备份规则 xml 的 include/exclude 组合无告警） |

### 19.3 边界

- 不自动识别聊天里的身份证/手机号等个人敏感信息：那是用户主动输入的内容，App 只保证「不额外扩散」（不传云端除用户自己配的服务商）。
- 会话删除**不删** `ai_proposal`：提案是跨会话的执行展示镜像，没有会话外键；它的清理归「一键清 AI 数据 / 恢复出厂」。
- 一键清 AI 数据**不删** `ai_proposal_execution`（主库执行台账）：避免出现「台账没了但计划已改过」的对不上；该表随恢复出厂清。
- 系统备份排除 AI 库是「保守选择」，不是 L 阶段的最终形态：L 做 AI 库备份段时，用户主动导出的那份仍然可以包含 AI 数据。
- 日志只去掉 `v/d/i`，保留 `w/e`：线上排障需要；已复核不含敏感串。
- 写工具继续保持禁用；本轮仍未新增任何写工具（8 个工具全部只读）。
