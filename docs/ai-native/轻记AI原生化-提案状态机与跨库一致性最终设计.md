# 轻记 AI 原生化 · 提案状态机与跨库一致性最终设计

> 工单：QJ-AI-NATIVE-DOC-REVIEW-V1 · 任务二  
> 基线 HEAD：`c97ada3d98678cc7f178061a84404b64a95d6066`  
> 性质：阶段 E 前置定稿；本轮不改代码、不建表、不迁移。

---

## 1. 核心结论

1. `qingji.db` 是正式业务执行与状态的**唯一权威来源**。
2. `qingji_ai.db` 只保存 AI 会话、消息、工具历史、Token、提案展示状态；**不得作为“业务已执行”的依据**。
3. 提案执行成功后，主库事务内写业务数据 + 成功执行标记；执行失败时事务回滚，失败原因在事务外记录。
4. **禁止在主库事务内写 `FAILED`**：事务回滚会把该状态一起抹掉。
5. `EXECUTING` 必须有启动扫描恢复，不能永久卡住。
6. AI 库状态与主库执行记录不一致时，以主库为准，按 `idempotency_key` 对账。
7. 两个 Room Database 不能天然保证跨库原子事务；本设计采用“主库权威事务 + AI 库展示状态 + 启动对账”的 Saga/对账模式。

## 2. 数据库职责边界

### 2.1 `qingji.db`（主库，权威）

负责：

- 正式业务数据（饮食、训练、运动、身体、计划等）。
- `ai_proposal_execution`：提案执行记录、幂等键、权威状态、baseVersion、结果时间、错误码。
- `ai_operation_log`：必要的业务操作审计记录。
- 与正式写入相关的幂等标记。

### 2.2 `qingji_ai.db`（AI 库，展示）

负责：

- `ai_conversation` / `ai_message`。
- `ai_tool_call`：工具调用历史。
- `ai_token_usage`：Token 使用信息。
- `ai_proposal`：提案展示内容（before/after/reason/status 展示值）。
- AI 运行状态。

**不负责**：

- 决定正式业务是否执行成功。
- 保存唯一权威幂等键（幂等键必须在主库执行记录中唯一）。
- 在主库未提交前写 EXECUTED 展示状态。

## 3. 提案状态机

```text
                         ┌──────────────────────────┐
                         │ PENDING_CONFIRMATION     │
                         └────────────┬─────────────┘
                                      │
        ┌─────────────────┬───────────┼───────────────┬──────────────────┐
        │ 用户拒绝        │ 过期      │ 版本不匹配    │ 用户确认          │
        ▼                 ▼           ▼               ▼
    REJECTED           EXPIRED      STALE        EXECUTING
                                                     │
                              ┌──────────────────────┼─────────────────────┐
                              │ 主库事务成功         │ 版本冲突            │ 事务异常
                              ▼                      ▼                     ▼
                          EXECUTED                STALE         FAILED（事务外写）
```

### 3.1 状态说明

| 状态 | 含义 | 权威位置 |
|---|---|---|
| `PENDING_CONFIRMATION` | 已生成，等待用户确认 | AI 库展示 + 主库可无执行记录 |
| `EXECUTING` | 用户已确认，准备进入或正在执行主库事务 | 主库执行记录 |
| `EXECUTED` | 主库事务已提交，业务数据已修改 | 主库执行记录 |
| `REJECTED` | 用户明确拒绝 | AI 库展示；无需主库执行记录 |
| `STALE` | baseVersion 不匹配、目标不存在或备份恢复后失效 | 主库记录（执行冲突）或 AI 展示（确认前失效） |
| `FAILED` | 执行异常，主库已回滚 | 主库事务外执行日志 |
| `EXPIRED` | 超过有效期，未确认 | AI 库展示；不可执行 |

## 4. 关键场景规则

| 场景 | 规则 |
|---|---|
| 重复确认 | 用主库 `idempotency_key` 唯一索引；第二次确认读取已有执行记录，不重复执行 |
| 网络重试 | 同一 idempotency_key 返回原执行结果 |
| Agent 重生成同一操作 | 生成相同 idempotency_key 时复用原提案 |
| 应用崩溃后 EXECUTING | 启动扫描超时 EXECUTING；查主库业务数据是否已变更：已变更→EXECUTED；未变更→重新入队或 FAILED |
| 确认时数据被其他入口修改 | CAS baseVersion 失败 → STALE；AI 库展示同步 |
| 主库成功但 AI 库状态更新失败 | 以主库为准；启动时对账修复 AI 展示状态 |
| AI 库状态与主库不一致 | 以主库为准；禁止 AI 库反向写业务 |
| 用户恢复旧备份 | 目标不存在/版本不匹配 → STALE；禁止恢复前提案自动执行 |
| 提案过期 | EXPIRED；不可执行；用户需重新生成 |

## 5. 事务执行顺序（成功）

```text
1. 用户点击确认
2. 校验提案状态 = PENDING_CONFIRMATION
3. 校验权限与工具白名单
4. 读取或生成幂等键（同一提案固定）
5. 读取正式业务数据当前版本
6. 若当前版本 != baseVersion → STALE，终止
7. 主库事务：
   - CAS 版本检查（UPDATE ... WHERE version = :baseVersion）
   - 修改正式业务数据
   - 写 ai_proposal_execution(status = EXECUTING/EXECUTED, idempotency_key)
   - 写必要操作审计记录
8. 主库事务提交
9. AI 库事务：更新提案展示状态 = EXECUTED
10. UI 展示执行结果
```

注意：第 7 步中 `EXECUTING` 到 `EXECUTED` 在同一事务内完成；不要求外部看到中间态。若需要“执行中”UI，可在发起事务前展示本地乐观状态，但权威状态仍以主库为准。

## 6. 事务执行顺序（失败）

```text
1-6 同上
7. 主库事务执行中抛异常
8. 事务整体回滚（正式数据未修改，事务内状态也未保留）
9. 事务外写 FAILED 执行日志：proposalId、idempotencyKey、errorCode、reason、time
10. AI 库展示状态同步为 FAILED（失败也要对账）
11. UI 展示失败原因，可重试则生成新的 execution 记录
```

**禁止**：在事务内写 FAILED 后期望回滚仍保留；这是错误的。

## 7. Saga / 对账机制

### 7.1 主库幂等记录

`ai_proposal_execution` 至少包含：

- `idempotency_key`（唯一）
- `proposal_id`
- `proposal_type`
- `target_table` / `target_record_id`
- `base_version`
- `status`
- `error_code`
- `created_at` / `executed_at`

### 7.2 启动对账

应用启动时：

1. 扫描主库中 `EXECUTING` 超时记录。
2. 对每条记录按 `idempotency_key` 检查业务数据是否已变更。
3. 已变更 → 标记 EXECUTED，同步 AI 展示。
4. 未变更 → 重新入队执行或标记 FAILED（需重试策略）。
5. 扫描 AI 库展示状态与主库不一致的提案，以主库为准修复。
6. 扫描 PENDING 提案：baseVersion 不匹配或目标不存在 → STALE；过期 → EXPIRED。

### 7.3 备份恢复对账

1. 先恢复主库正式数据 + 执行记录。
2. 再恢复 AI 库展示数据。
3. 按 `idempotency_key` 对账。
4. 所有指向不存在记录或版本不匹配的 PENDING/EXECUTING 提案标记 STALE。
5. 禁止执行恢复前遗留的旧提案。

## 8. 完整成功流程示例（饮食记录新增）

```text
AI 生成饮食提案（foodName/grams/mealType/date/before=null/after=FoodLog）
→ 用户确认
→ 校验权限（food_write）
→ 主库事务：
   CAS 校验（新增类目标版本可为空）
   插入 FoodLog
   写 ai_proposal_execution(EXECUTED)
→ 提交
→ AI 库展示 EXECUTED
→ 首页刷新
```

## 9. 完整失败恢复示例（训练计划调整）

```text
AI 生成计划调整提案（baseVersion=3）
→ 用户确认
→ 人工入口先把计划改到 version=4
→ 主库事务 CAS UPDATE version=3 → 影响 0 行
→ 事务回滚，正式计划保持 version=4
→ 主库事务外记录 FAILED/STALE 原因
→ AI 库展示 STALE
→ 用户看到“计划已变化，请重新生成建议”
```

## 10. 测试要求

- 正常确认 EXECUTED。
- 用户拒绝 REJECTED。
- 重复确认幂等。
- 网络重试幂等。
- EXECUTING 崩溃恢复。
- 版本冲突 STALE。
- 主库成功 AI 展示失败对账。
- AI 展示错误不反向改业务。
- 备份恢复后旧提案 STALE。
- 过期 EXPIRED。
- 食物/训练两类提案各至少一个完整成功与失败流程。

## 11. 未解决 / 需编码验证

- `ai_proposal_execution` 的具体表名与字段需阶段 E 迁移设计时确定；本轮只定语义。
- 执行中 UI 的乐观展示方式需阶段 D/E 联调；权威状态不变。
- 重试次数上限与退避策略需在阶段 E/L 定稿；本轮不设具体数值。
