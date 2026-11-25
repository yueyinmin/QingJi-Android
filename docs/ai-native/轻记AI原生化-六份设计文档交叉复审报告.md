# 轻记 AI 原生化 · 六份设计文档交叉复审报告

> 工单：QJ-AI-NATIVE-DOC-REVIEW-V1  
> 基线 HEAD：`c97ada3d98678cc7f178061a84404b64a95d6066`  
> 范围：六份原始设计文档 + 七份专项定稿文档交叉复核  
> 纪律：本轮只修订文档；未修改 Kotlin/Java/XML/Gradle/Entity/DAO/Repository/ViewModel/UI；未提交代码。

---

## 1. 原始问题清单

| 编号 | 原始问题 | 优先级 | 状态 |
|---|---|---|---|
| R1 | 阶段编号与职责不一致；架构文档把 ProposalManager 放入阶段 C 职责 | P0 | 已解决 |
| R2 | AI 库与主库都可能保存提案状态，业务执行权威来源不清 | P0 | 已解决 |
| R3 | 主库事务失败时事务内写 FAILED 会随回滚丢失；EXECUTING 无恢复机制 | P0 | 已解决 |
| R4 | 训练计划与营养目标版本控制未定稿 | P0 | 已解决（设计定稿，编码待 E） |
| R5 | 阶段 B 图片/语音能力范围偏大，与阶段 F 交叉 | P1 | 已解决 |
| R6 | 食物别名与匹配数据归属未定，且误判为“完全未发现别名” | P1 | 已解决 |
| R7 | 隐私与性能基础能力被后置到 K/L | P0 | 已解决 |
| R8 | Tool Calling 协议细节不足：多调用、callId、并行、错误、回传格式不清 | P0 | 已解决 |
| R9 | AI 结论未区分事实、计算、推断、数据不足、建议 | P1 | 已解决 |
| R10 | 长期记忆边界不足：事实/推断、纠正、失效、删除会话关系不清 | P1 | 已解决 |
| R11 | 既有测试报告与当前 HEAD 源码集合不一致 | P0 | 已解决 |
| R12 | 阶段 E 前必须完成版本/状态/事务/别名归属决策 | P0 | 已解决（设计层） |

## 2. 修改记录

| 编号 | 原始文档 | 原始描述/问题 | 修订后方案 | 修订原因 |
|---|---|---|---|---|
| R1 | 架构设计、路线图、审计报告、验收标准 | 阶段编号来源不一；`ProposalManager` 出现在阶段 C 职责表 | 以路线图阶段表为唯一来源；C 只读，E 才实施 ProposalManager；F/G 写入依赖 E 验收 | 防止开发人员把只读 Agent 与写入提案混在同一阶段 |
| R2 | 数据库设计、架构设计、验收标准 | AI 库和主库都可能表示提案状态 | 主库 `ai_proposal_execution` 为唯一权威；AI 库只展示并按 `idempotency_key` 对账 | 两库状态冲突时必须能判定谁生效 |
| R3 | 数据库设计、提案状态机专项 | 事务内写 FAILED 会回滚；EXECUTING 可能卡死 | 事务内只写成功/STALE；失败在事务外记录；启动扫描 EXECUTING 恢复 | 保证失败状态可持久化且不误回滚 |
| R4 | 版本控制专项、数据库设计、复用清单 | `TrainingPlan` 无 version；营养目标在 UserProfile | 训练计划父表 `version+updatedAt` 为唯一版本源；子表随父整包替换；UserProfile 加 `goalVersion+goalUpdatedAt` | 覆盖全部写入入口，避免 AI 与人工并发互相覆盖 |
| R5 | 路线图、验收标准 | 阶段 B 要求图片输入可用，与 F 交叉 | B 只保留 IMAGE_INPUT 能力声明与现有接口兼容；语音不阻塞 B；中餐识别归 F | 控制阶段边界，避免底层 Provider 承担业务识别 |
| R6 | 审计报告、复用清单、食物匹配专项 | 原报告写“无别名”，但实际 `CatalogFood.aliases` 存在 | 修正为：已有 aliases 字段但覆盖有限；通用别名作为版本化只读食物数据，不放 AI 库；个人纠正放用户侧 | 以源码为准，避免重复建表与数据归属错误 |
| R7 | 路线图、验收标准、架构设计 | Key/日志/上下文最小化等放在 K/L | B 起必须具备基础安全、日志脱敏、上下文最小化、请求取消、网络降级；K/L 做专项 | 第一次云端请求就有隐私风险，不能后置 |
| R8 | ToolCalling 协议专项、架构、验收 | 多 tool_call、流式参数、并行结果、错误回传不清 | 明确 callId 映射、完整拼接后执行、结构化工具错误、白名单不可覆盖、重复调用防护 | Agent 稳定性核心规则 |
| R9 | ToolCalling 协议专项、架构、验收 | 模型推断可能被当作事实 | 强制 FACT/CALCULATED/INFERRED/UNKNOWN/SUGGESTION 五类标识 | 用户需区分记录、计算与 AI 推断 |
| R10 | 长期记忆专项、路线图、验收 | 聊天全文、事实/推断、删除会话与删除记忆边界不清 | 结构化记忆、来源/时间/状态、用户可纠正、失效过期、按任务检索、删除会话不等于删除记忆 | 长期记忆必须可控可审计 |
| R11 | Git 基线专项、审计报告 | 旧测试 XML 与 HEAD 源码集合不一致 | 在 HEAD 强制重跑 compile/test/debug/release，记录 463/0/0/3 | 建立阶段 B01 权威回归基准 |
| R12 | 全部专项与路线图 | E 前技术决策尚未集中定稿 | 七份专项文档定稿，路线图引用；B/C 不受阻，E 写入前必须完成 | 避免各阶段重新争论架构 |

## 3. 真实源码依据

| 主题 | 文件/类/方法 | 当前事实 |
|---|---|---|
| AI 能力 | `data/remote/AiClient.kt:352/404/447/471` | 只有文本/图片/分析/连接测试；无 SSE、Tool Calling、取消、多轮 |
| 请求发送 | `AiClient.send():193-201` | OkHttp `execute()` 一次性读完整 body；无流式解析 |
| 结构化输出 | `AiClient.call():216-219`、`:370` | 仅 `json_object`，400 时移除字段重试 |
| 密钥 | `data/prefs/ApiKeyStore.kt:29/65/98/134` | AndroidKeyStore + AES/GCM，Key 不入库不备份 |
| AI 配置 | `Entities.kt:171-183 AiConfig` | 单行 provider/baseUrl/text/vision/model/temperature/timeout/dailyLimit/enabled |
| 饮食入口 | `QingJiRepository.kt:94/167/92/93/447/458/473` | observe/add/delete/replace 入口齐全 |
| 营养计算 | `domain/NutritionCalculator.kt:22/48/52/74/141` | Atwater 4/4/9、目标计算、BMI 已存在 |
| 训练保存 | `TrainingRepository.kt:125 saveSession` | 同事务会话+明细+账本结算 |
| 训练账本 | `HealthRepository.kt:47/51/236/178/247`、`ExerciseLedgerCalculator.kt:56` | 统一账本与汇总已存在 |
| 计划实体 | `TrainingEntities.kt:61-90` | `TrainingPlan` 无 version/updatedAt；子表无独立版本 |
| 计划写入 | `TrainingRepository.kt:180/216/227/270/273/281` | 6 个真实写入入口；备份恢复整包覆盖 |
| 计划 DAO | `TrainingDaos.kt:85/95/98` | `insertAll` 用 REPLACE，`setActive` 无 CAS |
| 营养目标 | `Entities.kt:24-49 UserProfile`、`Daos.kt:35-47` | 目标在主档案单行；DAO 用 REPLACE；无目标版本 |
| 主库 | `AppDatabase.kt:107-151` | 33 Entity，version=15，14 条迁移，无 destructive fallback |
| 食物库 | `FoodCatalogDatabase.kt:43-69/111/123-137/159` | assets 只读库，version=5，版本变更重建 |
| 食物别名 | `CatalogFood.kt:49 aliases`、`OmniFoodEntities.kt` | 已有 aliases 字段（受限），无完整中餐别名表/FTS5 |
| 食物搜索 | `FoodCatalogDao.kt:23-55 search()` | name/aliases/pinyin/pinyin_full LIKE 召回，注释明确不引 FTS5 |
| 备份 | `BackupManager.kt:305/452`、`BackupFormat.kt:33` | API Key 不进备份；VERSION=4；无 AI 库段 |
| 测试 | `app/build/test-results/testDebugUnitTest` | HEAD 强制重跑：463 tests / 0 failures / 0 errors / 3 skipped |
| UI 跳过 | `HomeInteractionTest.kt:36` | Robolectric android-all 下载受阻，类级 @Ignore |

## 4. 最终架构结论

### 4.1 模块职责

- AI 服务层：Provider 抽象、SSE、Tool Calling、流式事件、错误模型、多模态能力声明；阶段 B。
- AI 应用层：Agent、ContextBuilder、ConversationManager、ResponseParser；阶段 C/D。
- 工具层：只读 ToolRegistry/ToolExecutor；阶段 C。写入工具必须等阶段 E。
- 提案层：ProposalManager、权限、版本、幂等、状态机；阶段 E。
- 业务层：继续复用现有 Repository 与 domain 计算引擎，不新建 AI 专属业务模型。
- 数据层：主库权威业务与执行记录；AI 库会话/消息/工具历史/Token/提案展示；食物库只读。

### 4.2 数据库边界

- `qingji.db`：正式业务、`ai_proposal_execution`、`ai_operation_log`、幂等标记。
- `qingji_ai.db`：会话、消息、工具调用、Token、提案展示、AI 状态。
- `food_catalog.db`：只读食物与版本化别名资源。
- 跨库一致性：主库权威 + AI 库对账，不做跨库原子事务假设。

### 4.3 执行链路

- 只读问答：用户 → ContextBuilder → Agent → ToolExecutor → 现有 Repository → 本地计算 → 模型 → UI。
- 写入提案：模型写入意图 → ProposalDraft → 用户确认 → 主库 CAS 事务 → AI 展示对账。
- 图片饮食：AI 结构化识别 → 本地混合召回 → 规则排序 → Top5 → 本地计算 → 用户确认 → 正式写入。

### 4.4 阶段归属

严格以《完整实施路线图》A–M 表为准。B 只读基础设施，C 只读 Agent，D 会话，E 提案，F/G/H 业务接入，I/J 长期智能，K/L 安全与性能专项，M 全量验收。基础安全从 B 起生效。

## 5. 尚未解决的问题

| 状态 | 问题 | 说明 |
|---|---|---|
| 已解决 | 阶段编号/ProposalManager 归属 | 全部文档已统一 |
| 已解决 | 主库权威与跨库对账 | 专项文档已定稿 |
| 已解决 | 训练计划/营养目标版本方案 | 设计已定稿，编码在 E |
| 已解决 | B 阶段图片/语音范围 | 已收敛 |
| 已解决 | 别名归属 | 已定稿为版本化只读食物数据 |
| 已解决 | Tool Calling 协议与可信度标识 | 已定稿 |
| 已解决 | 长期记忆边界 | 已定稿 |
| 已解决 | Git/测试基线 | 已强制重跑 |
| 已解决 | 现有 aliases/FTS 覆盖率 | 已实测：food_catalog 420/1281 非空，food_entity 166/166 但全拉丁；样例词 5/8 可达；别名补丁 v1 后要求 8/8 |
| 部分解决 | `deactivateAll()` 多行版本递增策略 | 设计按“全部递增”，阶段 E 编码验证 |
| 已解决 | 具体迁移 SQL 与 Room schema | 已给出主库 v15→v16 与 AI 库 v1 完整 DDL；见《迁移SQL与别名覆盖率收口报告.md》 |
| 已解决 | Compose UI 测试跳过 | `@Ignore` 已移除，恢复 1 个 Compose 冒烟测试；全量测试 skipped=0 |
| 已解决 | AI 供应商 SSE/tool_call 具体事件差异 | Provider 已兼容 data 无空格、`message` 回退、SSE error 事件、usage-only 片段；MockWebServer 已验证 |
| 部分解决 | 工具并发数与重试退避 | 已实现 ToolExecutor 并发上限、callId 映射、超时与只读校验；实际工具的指数退避留到接入真实工具后调 |

## 6. 实施阻塞项

### 6.1 不阻塞 B01

- 设计文档已完成统一；当前 HEAD 测试与构建基线已重跑；B01 范围（Provider 基础、MockWebServer、能力探测、错误模型）已明确。
- B01 不依赖训练计划 version、AI 库、提案状态机或食物别名数据。

### 6.2 阶段 E 前必须解决（编码层）

- 训练计划 `version/updatedAt` 迁移与所有写入入口改造（SQL 已定稿，编码待执行）。
- `ai_proposal_execution` 表与主库事务（DDL 已定稿，编码待执行）。
- `EXECUTING` 恢复与跨库对账实现（状态机已定稿，编码待执行）。
- 营养目标 `goalVersion/goalUpdatedAt` 迁移（SQL 已定稿，编码待执行）。

### 6.3 阶段 F 前必须解决

- 别名补丁 v1 资源落地与 8/8 样例回归（覆盖率已实测、补丁方案已定稿，编码待执行）。
- `food_composition` / `nutrition_sample` 覆盖度已抽样；同名多来源 canonical 审校待阶段 F 数据确认。
- 个人纠正数据模型细化。

### 6.4 阶段 D 前必须解决

- Robolectric/Compose UI 测试环境或等效验证方案。

## 7. 最终开发顺序

```text
B01 Provider 基础（Provider 抽象、配置复用、能力声明、错误模型、MockWebServer）
  ↓
B02 SSE 流式文本 + 请求取消 + 错误恢复
  ↓
B03 Tool Calling + 流式参数拼接 + JSON Schema 降级
  ↓
C01 四个只读工具（今日饮食、营养、训练历史、训练计划）
  ↓
C02 Agent + ContextBuilder + 调用限制 + 可信度标识
  ↓
D01 AI 管家页 + 业务上下文入口 + 会话/消息流
  ↓
E01 提案系统 + 权限 + 版本/CAS + 幂等 + 状态机
  ↓
E02 故障恢复 + 跨库对账 + 备份恢复契约
  ↓
F 饮食助手（识别/匹配/本地计算/确认录入）
  ↓
G 训练助手（训练分析/自然语言反馈/计划提案）
  ↓
H 身体数据与综合分析
  ↓
I 长期记忆
  ↓
J 主动 AI
  ↓
K 数据安全与隐私专项
  ↓
L 性能与稳定性专项（含备份恢复、异常恢复）
  ↓
M 全量测试与最终验收
```

段落目标：

1. AI 读懂轻记数据：B01–C02。
2. AI 能交互但不擅自写入：D01–E02。
3. AI 参与饮食/训练/身体管理：F–H。
4. 长期智能与专项完善：I–L。
5. 正式交付：M。

## 8. 最终判定

- **六份原始文档已完成交叉核对并修订。**
- **阶段编号、模块职责、数据库归属、提案状态机、版本控制、Tool Calling、别名归属已统一。**
- **当前 HEAD 基线与测试基准已重新建立。**
- **未修改任何正式业务代码；未提交代码。**
- **具备进入 B01 的条件。**

按工单要求，本轮完成后暂停，不直接进入 B01。B01 需另开实施工单。
