# 轻记 AI 原生化 · 完整代码审计报告

> 工单：QJ-AI-NATIVE-MASTER-V1  
> 轮次：第一轮 · 阶段 A（只读）  
> 基线仓库：`/c/qingji-git/QingJi-Android`  
> 分支：`feature/health-system-v1.1`  
> HEAD：`c97ada3d98678cc7f178061a84404b64a95d6066`  
> 审计时间：2026-11-25  
> 纪律：本轮未修改代码、未创建表、未改 Entity/Repository/UI/依赖、未提交代码。找不到的内容一律标为“未发现”，不推测。

---

## 0. 结论摘要

1. 轻记已有**可工作的 AI 调用链**：`Screen → ViewModel → QingJiRepository → AiClient → OpenAI 兼容 /chat/completions`，支持文本识别、图片识别、一次性分析、连接测试、错误分类、超时和日调用熔断。
2. 轻记**尚无 Agent 基础设施**：无 SSE、无 Tool Calling / Function Calling、无流式工具参数拼接、无多轮 messages、无请求取消、无统一 `AiProvider` 抽象。
3. 业务数据与计算引擎基础扎实：饮食、训练、有氧账本、身体数据、统计均有 Repository + DAO + 纯函数计算，可作为 AI 只读工具复用。
4. 训练计划**无 version / updatedAt**，所有写入集中在 `TrainingRepository`，但 DAO 使用 `REPLACE`，无 CAS 乐观锁；这是 AI 写入提案的硬阻塞。
5. 存在两个 Room 数据库（主库 `qingji.db` v15、只读食物库 `food_catalog.db` v5），Room 事务不能跨库；新增 `qingji_ai.db` 后必须明确事务边界。
6. API Key 已用 AndroidKeyStore + AES/GCM 加密，且明确不进备份；此项基础可用。
7. 测试以 JVM 单测为主（源码 463 个 `@Test`，历史报告 460 通过 / 3 跳过），AI 仅覆盖纯规则层；**MockWebServer / Turbine / MockK / Truth / kotlinx-coroutines-test 均未发现**。
8. 阶段编号统一以《完整实施路线图》为唯一来源：A 审计、B AI 基础设施、C Agent 与工具调用、D AI 对话页面、E 提案与权限系统、F AI 饮食助手、G AI 训练助手、H 身体数据与综合分析、I AI 长期记忆、J 主动 AI 管家、K 数据安全与隐私、L 性能与稳定性（含备份恢复、异常恢复）、M 全量测试与最终验收。

## 1. AI 基础设施审计

### 1.1 AiClient 真实能力

路径：`app/src/main/java/com/qingji/app/data/remote/AiClient.kt`（491 行）

| 项目 | 当前实现 | 能力判定 | 证据 |
|---|---|---|---|
| 公开方法 | `recognizeText` (352)、`recognizeImage` (404)、`analyze` (447)、`testConnection` (471) | 已有（一次性） | 均 `suspend` + `withContext(Dispatchers.IO)` |
| 请求构造 | `org.json.JSONObject` 手工拼装 | 已有 | `messages` 数组每次硬编码 system+user 两条 |
| 普通聊天 | 有，但非多轮 | 部分 | 无历史消息入参，无 conversation 状态 |
| SSE / stream | 未发现 | **缺失** | 全仓 main 源码无 `stream`；`send()` (193-201) 用 `client.newCall().execute()` 一次性读 body |
| Tool Calling / Function Calling | 未发现 | **缺失** | 无 `tools` / `functions` / `tool_choice`，无回调循环 |
| 流式 Tool Call 参数拼接 | 未发现 | **缺失** | 无事件模型 |
| JSON Schema | 仅 `response_format.type=json_object` (370) | 部分 | 非 `json_schema` + `strict:true`；400 时 `call()` (216-219) 移除该字段静默重试 |
| 图片输入 | 有 | 已有 | `recognizeImage` (404-428)，`image_url` + base64 data URL，长边 1024 压缩在 `ImageRecorder.kt` |
| 多轮对话 | 未发现 | 缺失 | messages 无历史入参 |
| 请求取消 | 未发现 | 缺失 | 无 `Call` 字段、无 `call.cancel()`；协程取消不中断底层 OkHttp 阻塞请求 |
| 超时 | connect/read/write 统一 5–120s | 已有 | `httpClient()` (172-178) |
| 重试 | `retryOnConnectionFailure(true)` + `response_format` 400 降级一次 | 部分 | 无 429/5xx 退避 |
| 错误分类 | 401/403 Key、429 限流、402 余额、5xx、超时、其他 | 已有 | `errorMessage()` (227-233)、超时翻译 (379/432/463/485) |
| 请求日志 | 未发现敏感字段清洗证据 | 部分/需确认 | 错误体截取前 120 字 (227-233)，需补脱敏规范 |

**关键结论：不能把 `json_object` 或一次性 HTTP 当成“支持结构化 Agent 调用”。阶段 B 必须真实实现 SSE、Tool Calling、流式工具参数、请求取消与能力声明。**

### 1.2 配置、密钥与调用链

| 项 | 现状 | 判定 |
|---|---|---|
| `AiConfig` | `Entities.kt:171-183`，单行 id=1；provider/baseUrl/textModel/visionModel/temperature/timeoutSec/dailyLimit/enabled | 部分：单配置，非多服务商 |
| API Key | `ApiKeyStore.kt:29`；AndroidKeyStore + AES/GCM/NoPadding，256 位密钥，密文+IV 存 SharedPreferences；损坏即清理 | 已有，安全边界可用 |
| Repository 闸门 | `QingJiRepository.requireAi()` (695)：未配置/未启用/日限熔断；`todayAiCalls()` (518) | 已有 |
| 结果纠偏 | `correctWithLocalFood()` (约 589-664)、`NutritionMatch.compute`；`AiResultRules` 清洗数值 | 已有，体现“本地计算优先” |
| 事实拼装 | `buildDailyFacts()` (769)、`buildWeeklyFacts()` (791) | 部分：ContextBuilder 雏形，需扩展 |
| ViewModel | `AiAnalysisViewModel` (daily 15/weekly 20)、`AiConfigViewModel`、`TextAiViewModel`、`ImageRecordViewModel` | 部分：薄封装，无重试/取消状态机 |
| Screen | `AiAnalysisScreen`、`AiConfigScreen`、`TextAiScreen`、`VoiceRecordScreen`、`ImageRecordScreen` | 部分：无统一会话页 |

链路：`Screen → ViewModel → QingJiRepository（熔断/规则/纠偏/计数）→ AiClient → /chat/completions`。

### 1.3 阶段 B/C/D 差距

- 阶段 B（AI 基础设施）：`AiProvider` 抽象、能力声明、SSE、工具调用、结构化 JSON、错误模型、请求取消、MockWebServer 契约测试 —— 大部分缺失。
- 阶段 C（Agent 与工具）：`QingJiAgent`、`ContextBuilder`、`ToolRegistry`、`ToolExecutor`、4 个只读工具 —— 全部未发现。
- 阶段 D（会话页）：统一 AI 管家、会话/消息流、上下文入口、流式 UI —— 未发现；现有 `AiAnalysisScreen` 只能做一次性分析。

## 2. 业务数据层审计

### 2.1 饮食

| 项 | 当前实现 | 能力 | 复用建议 |
|---|---|---|---|
| 记录实体 | `Entities.kt:122 FoodLog`（date/mealType/grams/kcal/蛋白/碳水/脂肪/纤维/钠/钙/source/aiConfidence/createdAt） | 已有 | 直接作为 AI 饮食工具输出结构 |
| 仓库入口 | `QingJiRepository.kt:94 observeFoodLogs`、`:167 observeFoodRange`、`:92/93 summary`、`:447 addFoodLog`、`:458 deleteFoodLog`、`:473 replaceMeal` | 已有 | 只读工具复用 observe*，写入必须走提案 |
| 食物库 | `CatalogFood.kt:43`、`OmniFoodEntities.kt:37`；`FoodCatalogDao` / `OmniFoodCatalogDao` 只读 | 已有 | `searchFood` / `omniFoodDetail` 作为匹配召回入口 |
| 搜索 | `QingJiRepository.kt:302 searchFood`、`:347 omniFoodDetail`；`CatalogFood.aliases`、`OmniFoodEntity.aliases` 已存在，`FoodCatalogDao.search()` 覆盖 name/aliases/pinyin/pinyin_full | 部分 | 无完整中餐别名表/FTS5/中文分词证据；现有 aliases 仅去括号同名 |
| 营养计算 | `NutritionCalculator.kt:22`：Atwater 4/4/9 (43-45)、`kcalFromMacros`(48)、`gramsFromKcal`(52)、`compute`(74)、`bmi`(141) | 已有 | AI 必须调用，不得自行计算 |
| 口径一致性 | 常量已统一；`MealDetailScreen.kt:421-426` 仍硬编码 4/4/9；`DietDetailScreen` 只做 sumOf | 部分 | 阶段 F 前收敛到 `NutritionCalculator` |

### 2.2 运动与训练

- 实体：`TrainingEntities.kt:94 WorkoutSession`、`:147 WorkoutExercise`（`setsJson`）、`TrainingMath.kt:12 WorkoutSet`；独立 `ExerciseLog`（`Entities.kt:238`）。
- 保存：`TrainingRepository.kt:125 saveSession()` 同事务写会话+整包替换明细+结算账本；`TrainingViewModel.kt:341 save()` 组装。
- 结算：`SessionCalorieCalculator.kt:88 settleStrengthSession`、`:49 kcalPerMinute`、`:153 estimateFromDurationOnly`、`:176 previewSetKcalList`、`:213 previewSessionTotalKcal`。
- 有氧：`ExerciseCalculator.kt:91 estimate()`；`HealthRepository.kt:136 mirrorExerciseLog` 写统一账本。
- 统一账本：`exercise_session_calorie`，`HealthRepository.kt:47/51/236/178/247`；`ExerciseLedgerCalculator.kt:56 summarize`。
- 判定：**统一运动体系已存在且可用**。AI 不得新建“AI 专属运动模型”；力量/有氧/HIIT 必须按原语义读取。

### 2.3 训练计划写入路径

实体 `TrainingPlan(uuid,name,source,isActive,createdAt)`、`TrainingPlanDay`、`PlanExercise`（`TrainingEntities.kt:61-90`）——**无 version / updatedAt**。

全部写入路径：

1. `TrainingRepository.kt:180 activatePlan()`：事务 deactivateAll → insert 计划 → 7 天 day → 默认 3 组/10 次/0kg。
2. `:216 useTemplate()` → activatePlan。
3. `:227 saveSessionAsTemplate()`：只存不激活，解析 UUID/名字快照。
4. `:270 deactivatePlan()`：无事务。
5. `:273 reactivatePlan()`：事务 deactivateAll + setActive。
6. `:281 deletePlan()`：事务级联删 day/exercise/plan。
7. 备份恢复：`BackupManager.kt:537-546` clearAll + insertAll 整包覆盖。

DAO：`TrainingDaos.kt:84 insertAll` 为 `OnConflictStrategy.REPLACE`；`:98 setActive(uuid,active)` 无版本条件；无 `@Update` CAS。

**判定：无乐观锁、无幂等键、无统一版本维护。AI 计划调整提案必须先补版本控制或采用“快照 + 幂等键 + 单入口 CAS”方案。**

### 2.4 身体数据与统计

- `QingJiRepository.kt:78 observeProfile`、`:98 observeWeightLogs`、`:110/112` 围度、`:114/116/120` 照片、`:95/510` 饮水、`:285/288` 断食。
- `SleepRepository.kt:31 save()`、`:86 observeByDate`、`:90 observeRange`；归属日为起床日。
- `DailyHealthRepository.kt:35 summaryFor(date)`、`:82 summariesFor(from,to)`。
- 现有统计：`WeeklySummaryCalculator.summarize`、`ProgressViewModel`、`DailyHealthSummaryEntity`。
- 判定：身体与统计工具可复用现有 Repository / 计算类，不需新 SQL。

## 3. 数据库审计

### 3.1 主库 `qingji.db`

`AppDatabase.kt:107-151`：33 个 Entity，`version = 15`（149），`exportSchema = true`。迁移 `MIGRATION_1_2` 至 `MIGRATION_14_15` 共 14 条，汇总于 `ALL_MIGRATIONS` (513-521)，`get()` (776) 不使用 `fallbackToDestructiveMigration()`。

### 3.2 食物库 `food_catalog.db`

`FoodCatalogDatabase.kt:43-69`：18 个 Entity，`version = 5`。来源为 assets 只读预置库：`ASSET_PATH` (111)、`createFromAsset` (159)；版本变化即 `deleteDatabase` + 清 WAL (123-137)；保留 `fallbackToDestructiveMigration()` (161-164)。`FoodCatalogDao.kt:11-12` 注释明确无 `@Insert/@Update/@Delete`。

**不能跨库事务**：`QingJiRepository.kt:55-64` 分别持有两个 Database；Room `withTransaction` 只绑定单个库。

### 3.3 事务与一致性风险

- `@Transaction` 主源码仅一处：`Daos.kt:286-290 UsageStatDao.increment()`。
- `withTransaction` 使用点：`BackupManager.kt:434`、`QingJiRepository.kt:474/933/976`、`SelfDisciplineRepository.kt:56/70/123/162/194/236`、`TrainingRepository.kt:79/131/152/184/248/274/282`。
- `HealthRepository.kt` 仅 import `withTransaction`，无实际调用；`ensureLedgerBackfill` (178-220) 多步删写无事务。
- `factoryReset()` (976-1022) 只事务包住主库表，SettingsStore / WorkoutDraftStore / ApiKeyStore / 文件清理在事务外。
- `clearRecords()` (924-931) 先删照片文件再开库事务，回滚会留下“记录还在、图已删”。
- 账簿镜像走最终一致 + 自愈（`HealthDaos.kt:103-132`），已承认跨层不一致可能。

### 3.4 新增 `qingji_ai.db` 的建议边界（设计建议，非现状）

- 不尝试跨库事务；每库各自 `withTransaction`，先解析校验再逐库提交。
- AI 表引用主库记录时用业务键（uuid）+ 快照字段，不用跨库外键。
- 提案执行状态与幂等标记放主库，与正式写入同事务；AI 库只保存展示状态。
- 写入顺序：主库先提交，AI 派生数据后写，使用 IGNORE/REPLACE 幂等 upsert。
- 用户 AI 数据走严格迁移（version+1 + Migration + default 一致，无 destructive fallback）；只读词表可照食物库 assets 方案。

## 4. 食物数据库审计

| 检查项 | 现状 |
|---|---|
| 标准食物 | `CatalogFood`：name/energyKcal/protein/fat/carbs（58-61），`basisGrams=100` |
| 扩展食物 | `OmniFoodNutrition` per100g 字段 (46-49)，`FoodComposition`、`NutritionSample` 等 |
| 分类 | 有 category / 品牌 / 外卖表 |
| 中文别名表 | **未发现** |
| FTS5 搜索 | **未发现**；`FoodCatalogDao.kt` 注释明确未引 FTS5 |
| 食材组成 | 有 `FoodComposition` 表，覆盖度未审计，不能假设足以支持组合菜计算 |
| 烹饪方式 | 未发现结构化字段 |
| 中餐成品菜匹配 | **不能支持完整链路**；需 FoodMatchingEngine + 别名表 + 无匹配兜底 |

结论：现有库可作为召回数据源之一，但不能直接宣称支持“中餐菜品识别与匹配”。阶段 F 必须允许无匹配，并补齐别名/规则/估算标记。

## 5. 备份恢复审计

- `BackupFormat.kt:33 VERSION = 4`；明确不含设置项、搜索历史、照片文件、API Key (`:13`)。
- `BackupManager.buildJson()` (119-408) 覆盖：profile、weights、user_food、收藏、foodLogs、water、diaries、usage、**aiConfig**、bodyMeasures、照片路径、exercise_log、fastings、自建预设、训练 6 表、自律 7 表。
- 不覆盖：food_catalog、sleep_record、exercise_session_calorie、exercise_calorie_config、daily_health_summary、搜索历史、DataStore 设置、照片文件。
- API Key 不进备份：`BackupManager.kt:28/305/452/602`；Key 在 `ApiKeyStore`。
- AI 相关占位：只有 `aiConfig` 段与 `FoodLog.aiConfidence`；会话/消息/提案/记忆/向量段**未发现**。

风险：新增 AI 库后，若备份不含会话/提案，恢复会出现“业务记录存在、AI 上下文缺失”；旧提案必须标记 STALE，不得在恢复后执行。

## 6. 测试基础设施审计

- JVM 单测源码 67 个文件；`@Test` 463 个，`androidTest` 1 文件 6 个 `@Test`。
- 已有报告（`app/build/test-results/testDebugUnitTest/`，70 XML）：tests=463 / failures=0 / errors=0 / skipped=3；3 skipped 来自 `HomeInteractionTest`。注意报告与 HEAD 源码集合不完全一致，验收前必须重跑取证。
- `HomeInteractionTest.kt:36` 类级 `@Ignore`：Robolectric android-all 下载受阻；Robolectric 本身 4.14.1 已接入，15 个数据类测试可用。
- AI 测试仅 3 个纯规则类：`AiResultRulesTest`（16）、`AiSaveGateTest`（8）、`AiNameCorrectionsTest`（7）；不触网、不测 `AiClient`。
- MockWebServer / Turbine / MockK / Truth / kotlinx-coroutines-test：**未发现**。

阶段 B 前必须补齐：网络层可注入（client/dispatcher）、MockWebServer、协程测试栈、SSE 事件解析纯函数测试、工具调用契约测试。

## 7. 阶段 B–M 逐项现状判定

| 阶段 | 目标 | 现状 | 判定 |
|---|---|---|---|
| B AI 基础设施 | Provider / 能力 / SSE / 工具 / JSON Schema / 错误 / 配置 | 有一次性文本+图片、错误分类、超时、Key 安全；无 Provider/SSE/Tool/Cancel | 部分 |
| C Agent 与只读工具 | Agent / ContextBuilder / ToolRegistry / 4 工具 | 无 Agent；有事实拼装与业务 Repository 可复用 | 完全缺失（可复用底层） |
| D AI 会话页 | 统一管家、消息流、上下文入口 | 无会话页；有 AiAnalysisScreen 一次性分析 | 完全缺失 |
| E 提案与权限 | Proposal / 版本 / 幂等 / 跨库一致 | 无提案、无版本、无幂等键 | 完全缺失，**阻塞写入** |
| F AI 饮食助手 | 识别/匹配/计算/确认卡片 | 有图片识别与本地纠偏；无 FoodMatchingEngine/别名/确认提案 | 部分 |
| G AI 训练助手 | 历史分析/反馈/计划提案 | 有训练数据与计算；无分析 Agent 与计划版本 | 部分（阻塞于 E） |
| H 身体数据综合 | 趋势/BMI/报告 | 有身体数据与 BMI/统计；无 AI 解释层 | 部分 |
| I 长期记忆 | 记忆分类/管理/检索 | 未发现 | 完全缺失 |
| J 主动 AI | 规则触发/频率控制 | 未发现 | 完全缺失 |
| K 数据安全与隐私 / L 性能与稳定性（备份恢复） | AI 库备份/提案恢复/稳定性 | 有 Key 安全与主库备份；无 AI 库段 | 部分 |
| L 性能稳定 | Token/上下文/降级 | 有日调用熔断；无 Token 记录/取消/流控 | 部分 |
| M 全量测试 | AI/Agent/工具/提案/回归 | 有 463 单测与迁移测试；网络/Agent/提案测试缺失 | 部分 |

### 7.1 版本控制补充（交叉复审后定稿）

- 训练计划：`training_plan` 新增 `version + updatedAt`，父计划为唯一版本源；`training_plan_day` / `plan_exercise` 随父版本整包替换。
- 营养目标：暂留 `user_profile`，新增 `goalVersion + goalUpdatedAt`，不新建目标表。
- 所有人工/AI/模板/备份恢复入口统一维护版本；详见《版本控制与并发修改最终设计.md》。
- 测试基线已在当前 HEAD 重新建立；详见《当前Git基线与测试基准.md》。

## 8. 必须先解决的架构阻塞

1. AI 服务层能力真实实现（SSE、Tool Calling、取消、能力探测）——阶段 B。
2. AI 数据库归属与会话模型——阶段 B/D。
3. 训练计划版本控制与提案幂等——阶段 E 前置。
4. 跨库事务边界（主库业务写入 vs AI 库状态）——阶段 E/K。
5. 中餐匹配数据与无匹配策略——阶段 F。
6. 测试基础设施（MockWebServer、协程测试、流解析）——阶段 B 前置。

## 9. 审计纪律说明

- 本轮所有结论来自当前 HEAD 源码与已有测试报告；未把工单目标当作现状。
- 未发现项已明确标注；未提供截图或真机行为的部分未做推断。
- 本轮未修改代码、未提交代码。
