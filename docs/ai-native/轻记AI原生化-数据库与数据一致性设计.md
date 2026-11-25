# 轻记 AI 原生化 · 数据库与数据一致性设计

> 基线：HEAD `c97ada3d98678cc7f178061a84404b64a95d6066`  
> 性质：目标设计。现状证据见《轻记AI原生化-完整代码审计报告.md》。  
> 纪律：第一轮不创建表、不改 Entity、不改迁移、不写代码。

---

## 1. 当前数据库格局

| 数据库 | 版本 | 来源 | 事务特性 | AI 相关性 |
|---|---:|---|---|---|
| `qingji.db`（主库） | 15 | App 自建，14 条迁移，无 destructive fallback | 单库 `withTransaction` 可用 | 已有 `ai_config` / `usage_stat`；未来放提案执行状态与幂等标记 |
| `food_catalog.db` | 5 | assets 只读预置，`createFromAsset`，版本变化 delete + 重建 | 只读，不可与主库跨库事务 | 作为食物召回数据源，不写 AI 数据 |
| `qingji_ai.db` | — | 尚未存在 | 独立 RoomDatabase | 会话、消息、工具历史、Token、AI 运行状态 |

**核心判断：三个库之间不存在天然原子事务。** 任何“AI 库写成功 + 业务库写成功”的假设都必须用状态机或对账机制替代。

## 2. 数据库归属设计

### 2.1 主库 `qingji.db` 新增内容（仅限阶段 E 之后）

| 表 | 用途 | 关键字段建议 |
|---|---|---|
| `ai_proposal_execution` | 提案执行状态与幂等标记，与正式写入同事务 | `idempotency_key`(UNIQUE)、`proposal_id`、`status`、`base_version`、`executed_at`、`error_code` |
| `ai_operation_log` | 正式 AI 操作审计 | `proposal_id`、`action`、`result`、`details_json`、`executed_at` |
| 业务表版本字段 | 训练计划/营养目标 CAS | `version: Long`、`updated_at: Long` |

约束：提案的“展示内容”（before/after/reason）可放 AI 库；但**“是否已执行 + 幂等键”必须与正式业务写入同库同事务**。

### 2.2 AI 库 `qingji_ai.db`

| 表 | 用途 | 关键字段建议 |
|---|---|---|
| `ai_conversation` | 会话 | `id`、`title`、`entry_page`、`created_at`、`updated_at`、`deleted_at`、`schema_version` |
| `ai_message` | 消息 | `id`、`conversation_id`、`sequence`、`role`、`content`、`message_type`、`tool_calls_json`、`token_usage`、`provider_id`、`model_id`、`status`、`created_at` |
| `ai_tool_call` | 工具调用历史 | `id`、`conversation_id`、`message_id`、`call_id`、`tool_name`、`arguments_json`、`result_json`、`status`、`duration_ms`、`error_code` |
| `ai_proposal` | 提案展示与状态 | `id`、`proposal_type`、`title`、`reason`、`before_json`、`after_json`、`target_table`、`target_record_id`、`base_version`、`status`、`expires_at` |
| `ai_token_usage` | Token 统计 | `id`、`conversation_id`、`provider_id`、`model_id`、`input_tokens`、`output_tokens`、`task_type`、`requested_at`；供应商不返回则留空，不伪造 |

索引要求：

- `ai_message(conversation_id, sequence)` 唯一索引，禁止只靠时间戳排序。
- `ai_tool_call(conversation_id, call_id)` 唯一索引，防重复回传。
- `ai_proposal(status, expires_at)` 便于清理。
- `ai_operation_log(idempotency_key)` 放主库，唯一索引。

### 2.3 食物库与别名归属（修订定稿）

`food_catalog.db` 保持只读。通用别名是**食物匹配数据**，不是 AI 对话数据：

- 通用别名作为版本化只读食物数据维护，随食物库资源版本发布（assets JSON 或独立只读表）；**不放 `qingji_ai.db`**。
- 用户个人纠正（个人偏好、识别纠错）放主库用户侧表，独立管理。
- 模型不得直接修改公共标准食物库或别名数据。
- 不满足匹配时允许“无匹配”，不得为了完成任务强行套用相似食物。

## 3. 版本控制与乐观锁

### 3.1 必须加版本的目标

- `training_plan`：AI 可提出调整；当前无 version/updatedAt。
- 营养目标：当前在 `UserProfile` 中，需评估是否拆出独立目标表或增加版本字段。
- 饮食记录、运动记录：若支持 AI 修改，也需要版本或“以用户最后编辑为准”的冲突策略。

### 3.2 定稿方案（修订收口）

**采用 `version: Long + CAS`。**

```sql
UPDATE training_plan
SET plan_data = :newData,
    version = version + 1,
    updated_at = :now
WHERE uuid = :planId
  AND version = :baseVersion
```

- 影响 1 行：成功。
- 影响 0 行：版本冲突，提案标记 `STALE`。
- 检查与更新必须在主库同一事务内。
- **训练计划父表是唯一版本源**：`training_plan_day` / `plan_exercise` 随父版本整体替换，不单独建 version。
- **营养目标暂留 `UserProfile`**，新增 `goalVersion + goalUpdatedAt`；不新建目标表。
- 所有写入入口必须统一维护版本：人工编辑、AI 提案、模板应用、`activatePlan` / `useTemplate` / `saveSessionAsTemplate` / `deactivatePlan` / `reactivatePlan` / `deletePlan`、备份恢复。
- 备份恢复整包覆盖时必须同步恢复 version，禁止把旧版本数据写回后保留新版本号。

否决方案：仅靠 `before_json` 快照 + 幂等键无法防御“提案生成后、执行前”的并发人工修改，不作为写入类 AI 的正式方案。

## 4. 提案状态机

```text
PENDING_CONFIRMATION
   ├── 用户拒绝 → REJECTED
   ├── 过期 → EXPIRED
   ├── 基础版本不一致 → STALE
   └── 用户确认 → EXECUTING（主库执行记录）
          ├── 主库事务成功 → EXECUTED（事务内写成功标记）
          ├── 版本冲突 → STALE（事务内写冲突标记）
          └── 事务异常 → 事务回滚；事务外写 FAILED + 失败原因
```

**权威来源（修订收口）**：

- 主库 `ai_proposal_execution` 是业务执行状态的唯一权威依据。
- AI 库 `ai_proposal.status` 只是展示状态，启动时按 `idempotency_key` 从主库对账。
- **禁止在主库事务内写 `FAILED`**：事务失败会回滚，失败原因必须在事务外单独写入执行日志。
- `EXECUTING` 必须有恢复机制：启动时扫描超过阈值的 EXECUTING 记录，按幂等键判定已执行（转 EXECUTED）或重新入队/转 FAILED；禁止永久卡住。
- 网络重试、重复点击、Agent 重生成必须用 `idempotency_key` 去重。
- `FAILED` 可重试时生成新 execution 记录，不覆盖原记录。
- 恢复后若目标记录不存在或版本不匹配，统一标记 `STALE`，禁止自动执行。

## 5. 跨库一致性策略

### 5.1 不采用跨库事务

Room 的 `withTransaction` 绑定单个 Database；当前代码已存在双库并用“最终一致 + 自愈”处理账本镜像（`HealthDaos.kt:103-132`）。AI 库沿用同样纪律。

### 5.2 推荐执行协议

```text
1. 解析提案，完成参数/权限/版本全部前置校验（不写库）
2. 主库事务：
   - CAS 校验版本
   - 写正式业务数据
   - 写 ai_proposal_execution（成功标记 EXECUTED / 冲突标记 STALE）
3. 主库提交成功 → AI 库事务更新提案展示状态
4. 主库事务抛异常 → 整体回滚；在事务外写 FAILED + 错误码/原因
5. 第 3 步失败或应用崩溃 → 启动时按 idempotency_key 从主库对账修复
```

**注意**：第 2 步事务内不写 `FAILED`。事务回滚会抹掉事务内写入，失败记录必须在事务外生成。

### 5.3 失败场景

| 场景 | 处理 |
|---|---|
| 主库事务失败 | 事务整体回滚；事务外写 FAILED + 原因，不写 AI 展示状态 |
| 主库成功、AI 状态写失败 | 以主库为准，启动时对账 |
| 应用崩溃在主库提交后 | 按幂等键识别已执行，禁止重复 |
| 恢复备份后提案版本不匹配 | 标记 STALE |
| 食物库重建导致匹配食物丢失 | 提案保留快照，执行时校验；失效则 STALE |

## 6. 备份与恢复

### 6.1 现状

- `BackupFormat.VERSION = 4`；备份不含 API Key、设置、搜索历史、照片文件。
- 备份包含主库 `aiConfig` 段，但没有 AI 会话/消息/提案/记忆段。
- 主库恢复使用导入事务；恢复流程已有 `hasTraining` / `hasSelfDiscipline` 标志兼容老备份。

### 6.2 目标

- `BackupFormat.VERSION` 升到 5（具体版本号在进入阶段 L 时确定）。
- 备份增加 `hasAi` 标志和 AI 段；老备份无该键时保留本机 AI 数据，不误清。
- AI 段包含会话、消息、工具历史、提案展示、Token 统计；**不包含 API Key**。
- 恢复顺序：
  1. 解析完整备份并校验版本。
  2. 主库事务恢复正式业务 + 提案执行状态。
  3. AI 库事务恢复会话/消息/提案展示。
  4. 对账：标记所有版本不匹配的 PENDING 提案为 STALE。
- 恢复后禁止直接执行恢复前遗留的提案。

### 6.3 恢复出厂

`factoryReset()` 当前主库事务外还有 SettingsStore / WorkoutDraftStore / ApiKeyStore / 文件清理。AI 库清理应作为独立事务加入序列，位置在主库事务之后、密钥与文件清理之前；需记录现有“非原子复位”风险，不在本轮修复。

## 7. 数据最小化与隐私

- 每次 AI 请求只发送任务所需摘要，不发送完整数据库。
- Chat 请求默认不带 API Key；Key 只由 Repository 在构造请求时读取。
- Token usage 可记录数量；不记录用户输入全文到 usage 表。
- 图片采用现有压缩链路（长边 1024、JPEG），缓存路径不写备份。
- 日志不得打印完整请求体、响应体或 Key；错误体截取前 120 字需加脱敏规则。

## 8. 迁移纪律

1. 用户数据库：version+1、显式 Migration、`defaultValue` 与 Entity 一致、禁止 destructive fallback。
2. 只读资源库：可照 `food_catalog.db` 的 assets 版本 + 重建策略。
3. 迁移测试：主库迁移已有 JVM + androidTest 覆盖；AI 库迁移必须补同类测试。
4. 备份恢复测试必须覆盖：旧备份无 AI 段、新备份带 AI 段、版本冲突提案、API Key 不恢复。

## 9. 第一轮决策项（未决）

- 训练计划采用加 version 还是快照过渡方案。
- `ai_proposal_execution` 是否放主库新表，还是复用现有通用日志表（当前未发现合适表）。
- 别名表放 AI 库还是独立 assets 包。
- AI 库备份是否与主库备份同文件（推荐同文件、分段校验）。
- 恢复后 AI 会话是否保留业务记录快照（推荐保留，避免悬空引用）。
