# 轻记 AI 原生化 · 现有功能复用清单

> 基线 HEAD：`c97ada3d98678cc7f178061a84404b64a95d6066`  
> 原则：新增任何类、表、Repository、Service 前，必须说明现有代码为何不能复用。  
> 标记：`直接复用` / `适配后复用` / `仅作参考` / `未发现`。

---

## 1. AI 服务与配置

| 现有代码 | 当前能力 | 复用方式 | 判定 |
|---|---|---|---|
| `data/remote/AiClient.kt:352 recognizeText` | 文本识别，system+user，`json_object` | 迁入 OpenAI 兼容适配器内部，不作为 `AiProvider` 直接暴露 | 适配后复用 |
| `AiClient.kt:404 recognizeImage` | 图片识别，`image_url` + base64 | 作为 IMAGE_INPUT 适配器实现基础 | 适配后复用 |
| `AiClient.kt:447 analyze` | 一次性分析，固定 `ANALYSIS_RULE` | 作为普通生成能力参考；会话化需重写 | 仅作参考 |
| `AiClient.kt:471 testConnection` | 最小连接测试 | 扩展为能力探测，不直接改名复用 | 适配后复用 |
| `AiClient.errorMessage():227-233` | 401/403/429/402/5xx/超时错误文案 | 映射为新 `AiError`，保留文案规则 | 适配后复用 |
| `AiClient.httpClient():172-178` | 5–120s 超时、`retryOnConnectionFailure` | 保留为默认配置；需支持注入 client/Call 以测试取消 | 适配后复用 |
| `AiClient.call():206-225` | `response_format` 400 降级一次 | 保留降级但必须记录日志并向调用方暴露降级结果 | 适配后复用 |
| `data/prefs/ApiKeyStore.kt` | AndroidKeyStore + AES/GCM，Key 不入库、不备份 | 原样复用，不新建密钥存储 | **直接复用** |
| `data/local/entity/Entities.kt:171 AiConfig` | provider/baseUrl/text/vision/model/temperature/timeout/dailyLimit/enabled | 作为单默认配置模型；多服务商需扩展，不能假装已支持 | 适配后复用 |
| `QingJiRepository.getApiKey()/saveApiKey()/hasApiKey():87-89` | Key 访问闸门 | AI 服务层通过 Repository 取 Key，不直接读 Storage | **直接复用** |
| `QingJiRepository.requireAi():695`、`todayAiCalls():518` | 未配置/未启用/日限熔断 | 继续作为调用前闸门 | **直接复用** |
| `QingJiRepository.correctWithLocalFood()`、约 589-664 | 本地食物纠偏 | AI 饮食结果纠偏继续复用 | **直接复用** |
| `AiResultRules` / `AiSaveGate` / `AiNameCorrections` | 数值清洗、保存闸门、名称纠正 | 作为 AI 工具输出校验规则 | **直接复用** |

## 2. 上下文与事实拼装

| 现有代码 | 当前能力 | 复用方式 | 判定 |
|---|---|---|---|
| `QingJiRepository.buildDailyFacts():769` | 今日预算、摄入、三大营养素、饮水、各餐摘要 | 作为 `ContextBuilder` 的今日饮食数据源 | 适配后复用 |
| `QingJiRepository.buildWeeklyFacts():791` | 7 天摄入、体重变化、常吃食物 | 作为周报/主动 AI 上下文源 | 适配后复用 |
| `QingJiRepository.buildRecognitionRules():723` | 忌口、过敏、餐次、估算、语言规则 | 饮食 AI system prompt 的本地规则源 | **直接复用** |
| `AppClock` | 统一 today/now/zone | 所有 AI 工具日期参数统一走 AppClock | **直接复用** |

## 3. 饮食数据与计算

| 现有代码 | 当前能力 | 复用方式 | 判定 |
|---|---|---|---|
| `Entities.kt:122 FoodLog` | 饮食记录实体 | AI 工具输出 DTO 的来源；确认写入仍用该实体 | **直接复用** |
| `QingJiRepository.observeFoodLogs():94` / `observeFoodRange():167` | 指定日期/区间记录 | `get_daily_nutrition` 数据源 | **直接复用** |
| `QingJiRepository.observeDailySummary():92` / `observeMealSummary():93` | 汇总查询 | `get_today_summary` 数据源 | **直接复用** |
| `QingJiRepository.addFoodLog():447` / `addFoodLogs():454` | 写入 + usageStat + Widget 刷新 | 只在用户确认提案后调用，不暴露给工具层 | 适配后复用 |
| `QingJiRepository.deleteFoodLog():458`、`replaceMeal():473` | 删除/整餐替换 | 提案写入入口，需幂等包装 | 适配后复用 |
| `FoodCatalogDao.kt` | 预置食物只读查询 | FoodMatchingEngine 召回源之一 | **直接复用** |
| `OmniFoodCatalogDao.kt:15` | `search/findByNameCn/nutritionFor/compositionForSample` | 别名/食材匹配的召回源 | **直接复用** |
| `QingJiRepository.searchFood():302` / `omniFoodDetail():347` | 食物搜索与细账 | 工具只读白名单 | **直接复用** |
| `domain/NutritionCalculator.kt:22` | Atwater 4/4/9、`kcalFromMacros`、`gramsFromKcal`、`compute`、`bmi` | AI 营养与 BMI 计算的唯一来源 | **直接复用** |
| `domain/NutritionMatch.kt` | `compute(hit.per100g, grams)` / `pick` | 本地食物命中后的营养计算 | **直接复用** |
| `RecordConfirm.kt:117 toFoodLogs()` | 每 100g × 克数换算 | 饮食确认卡片写入前的统一换算参考 | 适配后复用 |

现状修正：`CatalogFood.aliases` 与 `OmniFoodEntity.aliases` 已存在（`|` 分隔），`FoodCatalogDao.search()` 已覆盖 name/aliases/pinyin/pinyin_full；当前别名仅“去括号同名”，覆盖有限。缺口：完整中餐别名数据、FTS5、烹饪方式结构化数据、组合菜计算规则 —— 阶段 F 以版本化只读食物数据补齐，不放 AI 库。

## 4. 运动、训练与账本

| 现有代码 | 当前能力 | 复用方式 | 判定 |
|---|---|---|---|
| `TrainingEntities.kt:94 WorkoutSession` | 会话实体 | 训练工具输出 DTO | **直接复用** |
| `TrainingEntities.kt:147 WorkoutExercise` | 动作明细 + `setsJson` | 训练历史工具 | **直接复用** |
| `TrainingMath.kt:12 WorkoutSet` | 重量/次数/RPE/休息/完成 | 训练量分析输入 | **直接复用** |
| `TrainingMath.encodeSets()/decodeSets()` | 组数据序列化 | 工具结果解析 | **直接复用** |
| `TrainingRepository.observeSessions()` / `observeAllWorkoutExercises()` / `sessionDetail()` | 历史查询 | `get_training_history` 数据源 | **直接复用** |
| `TrainingRepository.saveSession():125` | 同事务会话+明细+账本结算 | 仅在提案确认后调用 | 适配后复用 |
| `SessionCalorieCalculator.kt` | MET×kg/60、分段结算、预览 | 训练热量计算唯一来源 | **直接复用** |
| `ExerciseCalculator.kt:91 estimate()` | 时长/千卡/步数/公里换算 | 有氧工具数据源 | **直接复用** |
| `HealthRepository.observeDailyExerciseCalories():47` / `observeRangeTotals():51` / `observeAllLedger():236` | 统一运动账本 | 今日/周期运动摘要工具 | **直接复用** |
| `ExerciseLedgerCalculator.kt:56 summarize()` | 账本汇总 | 统计与主动 AI 规则 | **直接复用** |
| `PersonalBestCalculator` / `FoodRanking` / `WeeklySummaryCalculator` | 纯函数分析 | AI 解释层直接消费 | **直接复用** |

不得新增“AI 专属运动模型”；力量/有氧/HIIT 语义保持不变。

## 5. 训练计划与版本控制

| 现有代码 | 当前能力 | 复用方式 | 判定 |
|---|---|---|---|
| `TrainingRepository.activatePlan():180` | 启用计划，事务写计划/日/动作 | 提案执行入口 | 适配后复用 |
| `useTemplate():216` | 模板启用 | 提案执行入口 | 适配后复用 |
| `saveSessionAsTemplate():227` | 历史转计划，解析 UUID/名字快照 | 提案执行入口 | 适配后复用 |
| `deactivatePlan():270` / `reactivatePlan():273` / `deletePlan():281` | 计划状态与删除 | 提案执行入口 | 适配后复用 |
| `TrainingPlanViewModel.kt:92/101/119/133/140/147` | UI 入口 | 保持为人工入口；AI 走提案，不绕过 | 仅作参考 |
| `TrainingPlanDao.insertAll():84` / `setActive():98` | REPLACE / 无版本条件 | 必须新增 CAS 方法后再复用 | **需改造** |
| `TrainingPlan` Entity | 无 version / updatedAt | AI 写入前必须补 | **缺能力** |

## 6. 身体数据与统计

| 现有代码 | 当前能力 | 复用方式 | 判定 |
|---|---|---|---|
| `QingJiRepository.observeProfile():78` / `saveProfile():79` | 用户档案/目标 | 身体工具与上下文 | **直接复用** |
| `observeWeightLogs():98` / `latestWeight():99` / `saveWeight():100` | 体重历史 | 趋势工具 | **直接复用** |
| `BodyMeasure` / `ProgressPhoto` DAO | 围度/照片 | 身体数据工具（只读） | **直接复用** |
| `SleepRepository.kt:31/86/90` | 睡眠写入/按日/区间 | 睡眠相关上下文 | **直接复用** |
| `DailyHealthRepository.kt:35/82` | 每日健康汇总缓存 | 局部摘要数据源，不直写 | **直接复用** |
| `ProgressViewModel` / `WeeklySummaryCalculator` | 统计口径 | 统计工具直接消费 | **直接复用** |

## 7. 数据库、事务与备份

| 现有代码 | 当前能力 | 复用方式 | 判定 |
|---|---|---|---|
| `AppDatabase.kt:107-151` | 33 Entity，v15，14 条迁移，无 destructive fallback | 主库迁移纪律作为 AI 库模板 | 仅作参考 |
| `FoodCatalogDatabase.kt:43-69` | assets 只读库，版本变化重建 | 只读词表/别名资源可参考 | 仅作参考 |
| `TrainingRepository` 多处 `withTransaction` | 单库事务实例 | 提案执行事务模板 | 适配后复用 |
| `BackupManager.kt:434-436` | 先解析后事务导入 | AI 备份恢复采用相同模式 | 适配后复用 |
| `BackupManager` 的 `hasTraining/hasSelfDiscipline` 标志 | 老备份兼容 | AI 段兼容模式照此设计 | 适配后复用 |
| `BackupManager.kt:305/452` | API Key 不进备份 | 保持，不新增 Key 字段 | **直接复用** |
| `exercise_session_calorie` 的 `(sourceType, sourceId)` 唯一索引 | 跨表业务键模式 | AI 引用主库记录时照此用 uuid + 快照 | 仅作参考 |
| `HealthDaos.kt:103-132` 孤儿自愈 | 最终一致对账模式 | AI 库状态对账参考 | 仅作参考 |

## 8. UI 组件与设计语言

| 现有代码 | 用途 | 判定 |
|---|---|---|
| `PageTopBar` / `SectionCard` / `AppDialogs.AppBottomSheet` | 页面骨架与弹层 | **直接复用** |
| `NumericInput.kt`（本会话新增） | 数字输入全选/空串/恢复 | AI 确认卡片改重量时复用 | **直接复用** |
| `MealCard`（`HomeCards.kt:488`） | 首页餐食条目 | 饮食详情/确认卡片样式参考 | 适配后复用 |
| `MealDetailScreen.FoodLogRow()` | 单条食物展示 | 分析卡片参考 | 适配后复用 |
| `pressable` / `Dimens` / 主题色 | 交互与视觉规范 | **直接复用** |

## 9. 测试设施

| 现有代码 | 用途 | 判定 |
|---|---|---|
| 463 个 JVM `@Test`（源码口径） | 业务回归基线 | **直接复用** |
| `AppDatabaseMigrationTest` / `AppDatabaseMigration*Test` | Room 迁移测试模板 | AI 库迁移照此补 | 适配后复用 |
| `AiResultRulesTest` / `AiSaveGateTest` / `AiNameCorrectionsTest` | AI 纯规则测试 | **直接复用** |
| `BackupParsingTest` / `BackupRestoreTest` | 备份格式测试模板 | AI 段备份测试照此补 | 适配后复用 |
| Robolectric 4.14.1 | 数据类测试可用 | 保持；Compose UI 测试因网络 @Ignore | 适配后复用 |

需新增：MockWebServer、`kotlinx-coroutines-test`、SSE 事件解析测试、工具调用测试、提案测试基础设施。

## 10. 明确不得复用/不得新建

- 不得把 `AiClient` 直接改名为 `AiProvider` 后宣称支持 Agent；这是错误抽象。
- 不得复用饮水 ViewModel / 饮水实体承载食物记录（V3 工单已明确）。
- 不得复用普通聊天页面承载各业务页 AI 对话；必须统一会话系统。
- 不得把 `food_catalog.db` 当可写库；模型不得修改公共标准食物。
- 不得复用 AI 猜测覆盖正式身体数据、营养数据或训练记录。
- 不得新增 AI 专属 Repository 直接操作 DAO；必须走现有 Repository/UseCase。
