# 轻记 AI 原生化 · 版本控制与并发修改最终设计

> 工单：QJ-AI-NATIVE-DOC-REVIEW-V1 · 任务三  
> 基线 HEAD：`c97ada3d98678cc7f178061a84404b64a95d6066`  
> 性质：阶段 E 前的定稿设计；本轮不改代码、不改表、不迁移。

---

## 1. 结论

1. **训练计划采用父计划版本控制**：`training_plan` 新增 `version: Long` 与 `updatedAt: Long`，作为唯一 CAS 版本源。
2. `training_plan_day` / `plan_exercise` 不单独建版本；它们随父计划整包替换，保证父子一致。
3. 营养目标暂留在 `user_profile`，新增 `goalVersion: Long` 与 `goalUpdatedAt: Long`；不新建营养目标表。
4. 所有写入入口——人工、AI 提案、模板应用、备份恢复——必须统一维护版本；不能只让 AI 入口维护。
5. 版本检查与写入必须在同一主库事务内；冲突返回 0 行时提案标记 `STALE`。

## 2. 真实表结构（当前 HEAD）

### 2.1 训练计划

`app/src/main/java/com/qingji/app/data/local/entity/TrainingEntities.kt:61-90`

- `training_plan`：`uuid`(PK)、`name`、`source`、`isActive`、`createdAt`。**无 version / updatedAt**。
- `training_plan_day`：`id`(PK auto)、`planUuid`、`weekday`、`title`、`isRest`。
- `plan_exercise`：`id`(PK auto)、`dayId`、`exerciseUuid`、`exerciseNameSnapshot`、`targetSets`、`targetReps`、`targetWeightKg`、`orderInDay`。

DAO：`app/src/main/java/com/qingji/app/data/local/dao/TrainingDaos.kt:76-155`

- `TrainingPlanDao.insertAll()` (`:85`) 使用 `OnConflictStrategy.REPLACE`。
- `setActive(uuid, active)` (`:98`) 是无版本条件的 `UPDATE`。
- `deactivateAll()` (`:95`) 全表置 0。
- `deleteByUuid()` / `clearAll()` 为硬删除。

### 2.2 营养目标

`app/src/main/java/com/qingji/app/data/local/entity/Entities.kt:24-49`

`user_profile` 单行（id=1，REPLACE 写入）已含：

- `dailyKcalGoal`
- `proteinGoalG` / `carbGoalG` / `fatGoalG`
- `fiberGoalG` / `sodiumGoalMg` / `calciumGoalMg`
- `targetWeight`、`waterGoalMl` 等

DAO：`Daos.kt:35-47` `UserProfileDao.save()` 为 `@Insert(REPLACE)`；`QingJiRepository.saveProfile()` (`:79`) 直接调用。

**当前无目标版本字段。**

## 3. 训练计划全部真实写入入口

| 入口 | 文件/行号 | 当前行为 | 版本责任 |
|---|---|---|---|
| `activatePlan()` | `TrainingRepository.kt:180-214` | 事务内 `deactivateAll` → 插入计划 → 7 天 day → 默认 3 组/10 次/0kg | 新增计划 version=1；其他计划 isActive 变更是否递增由阶段 E 决定（建议递增被停用计划的 version） |
| `useTemplate()` | `TrainingRepository.kt:216-217` | 调 `activatePlan()` | 继承 activatePlan |
| `saveSessionAsTemplate()` | `TrainingRepository.kt:227-268` | 只存不激活；UUID/名字快照解析 | version=1；返回缺失动作名单 |
| `deactivatePlan()` | `TrainingRepository.kt:270` | `planDao.setActive(uuid,false)`，无事务 | 必须改为 CAS 并递增 version |
| `reactivatePlan()` | `TrainingRepository.kt:273-279` | 事务 `deactivateAll` + `setActive` | 所有受影响计划均需版本递增或统一生成新版本快照 |
| `deletePlan()` | `TrainingRepository.kt:281-289` | 事务级联删 day/exercise/plan | 删除不冲突语义需定义：建议以“存在即冲突，删除成功即完成” |
| `TrainingPlanViewModel` | `:92/101/119/133/140/147` | UI 入口，调 Repository | 不直接写 DAO；保持单一入口 |
| 备份恢复 | `BackupManager.kt:537-546`、`TrainingBackup.kt` | `clearAll + insertAll` 整包覆盖 | 必须恢复 version 原值；不能把新数据写回后保留旧版本号 |

**结论：当前不存在计划日/计划动作的独立 UI 编辑入口**；它们由 `activatePlan()` / `saveSessionAsTemplate()` 整包生成。阶段 G 若新增 AI 调整，必须生成整包新版本，不直接单改子行。

## 4. 训练计划 CAS 方案

### 4.1 新增字段

```kotlin
data class TrainingPlan(
    ...
    val version: Long = 0L,
    val updatedAt: Long = 0L
)
```

迁移要求（阶段 E 实施，不在本轮执行）：

- `ALTER TABLE training_plan ADD COLUMN version INTEGER NOT NULL DEFAULT 0`
- `ALTER TABLE training_plan ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0`
- Entity `@ColumnInfo(defaultValue = "0")` 与 SQL 逐字一致。
- 迁移测试覆盖旧库升级后版本默认 0、现有计划可继续读取。
- **可直接实施的 v15→v16 SQL 与 AI 库 v1 DDL 见《迁移SQL与别名覆盖率收口报告.md》；本设计不再保留“具体 SQL 待定”状态。**

### 4.2 CAS 更新

推荐使用整包替换 + 父版本 CAS：

```sql
UPDATE training_plan
SET name = :name,
    source = :source,
    version = version + 1,
    updated_at = :now
WHERE uuid = :uuid
  AND version = :baseVersion
```

子表写入在同一事务内：

```text
BEGIN TRANSACTION
  CAS UPDATE training_plan
  if rows == 0: abort → STALE
  DELETE training_plan_day WHERE planUuid = :uuid
  DELETE plan_exercise WHERE dayId IN (旧 day ids)
  INSERT 新 days / exercises
COMMIT
```

- 影响 1 行：提交，提案 EXECUTED。
- 影响 0 行：回滚并标 STALE。
- 禁止只改 `plan_exercise` 某几行而不递增父版本。

### 4.3 isActive 变更

`isActive` 是计划状态，不是计划内容，但会影响“当前计划”。定稿：

- `activatePlan()` / `reactivatePlan()` 仍在一个事务里保证只有一个 `isActive=1`。
- 被停用计划和被启用计划的 `version` 均 +1，`updatedAt` 同步更新。
- AI 若只启用已有计划，也必须生成提案，baseVersion 取当前值。

## 5. 营养目标 CAS 方案

### 5.1 新增字段

在 `user_profile` 增加：

- `goalVersion: Long = 0`
- `goalUpdatedAt: Long = 0`

不新建表。理由：目标字段已在同一单行实体中，新增表会造成重复数据与口径风险。

### 5.2 更新方式

由于 `UserProfileDao.save()` 是 REPLACE，阶段 E 必须新增 CAS 更新路径：

```sql
UPDATE user_profile
SET daily_kcal_goal = :kcal,
    protein_goal_g = :protein,
    carb_goal_g = :carb,
    fat_goal_g = :fat,
    goalVersion = goalVersion + 1,
    goalUpdatedAt = :now
WHERE id = 1
  AND goalVersion = :baseVersion
```

- 人工设置页、AI 提案、备份恢复都走同一路径。
- 备份恢复必须保留原 `goalVersion`，不得用 REPLACE 覆盖后版本回退。
- 若恢复备份的目标版本低于本机，选择“以备份为准并保留备份版本号”，不做自动递增。

## 6. 并发冲突处理

| 场景 | 处理 |
|---|---|
| 提案生成后、用户确认前人工改了计划 | 确认时 CAS 失败 → STALE；提示用户查看最新计划 |
| 用户连续点击确认 | `idempotency_key` 去重；第二次不执行 |
| 两个 AI 提案基于同一 baseVersion | 第一个成功，第二个 CAS 失败 → STALE |
| 备份恢复后旧提案 | 版本不匹配或目标不存在 → STALE，禁止执行 |
| 人工入口未维护版本 | 视为阶段 E 验收失败；必须修复后才能上线 AI 写入 |

## 7. 事务边界

- 版本读取、CAS、计划内容替换、执行状态写入必须在**主库同一事务**。
- AI 库提案展示状态在事务提交后更新；失败时对账修复。
- 禁止在主库事务内写 `FAILED`（会随回滚丢失）；失败在事务外记录。

## 8. 测试方案

阶段 E 必须新增/更新：

1. Room 迁移测试：`training_plan` 与 `user_profile` 新字段默认值、旧数据可读。
2. CAS 单测/仪器测试：baseVersion 匹配成功；不匹配失败。
3. 写入入口回归：`activatePlan` / `useTemplate` / `saveSessionAsTemplate` / `deactivatePlan` / `reactivatePlan` / `deletePlan` 全部维护版本。
4. 备份恢复测试：恢复后版本不回退；旧提案 STALE。
5. 并发测试：两个提案同 baseVersion，只有一个 EXECUTED。
6. 重复确认测试：同一 idempotency_key 不重复写。

## 9. 未解决 / 需编码验证

- `deactivateAll()` 影响多行时，是否对**所有**被停用计划递增版本，需在阶段 E 实现时最终确认；当前设计按“全部递增”执行。
- `deletePlan()` 是否需要“软删除 + 版本”取决于产品是否要恢复已删计划；当前按硬删除、存在即冲突处理。
- `UserProfile` 单行 REPLACE 的具体升级 SQL 需以 Room 生成的 schema 校验为准。
