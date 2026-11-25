# 轻记 AI 原生化 · 迁移 SQL 与别名覆盖率收口报告

> 工单：QJ-AI-NATIVE-DOC-REVIEW-V1 后续收口  
> 基线 HEAD：`c97ada3d98678cc7f178061a84404b64a95d6066`  
> 性质：设计收口与真实数据核查。本轮未修改 Kotlin/Java/XML/Gradle/Entity/DAO/Repository/ViewModel/UI，未改数据库文件，未提交代码。

---

## 1. 收口结论

| 阻塞项 | 原状态 | 收口后状态 |
|---|---|---|
| 迁移 SQL 与 Room schema | 未解决：只有字段设计，没有可直接实施的 SQL | **已解决**：给出主库 v15→v16 与 AI 库 v1 的完整 DDL，以及 Room 校验要求 |
| 别名覆盖率 | 部分解决：字段存在但覆盖未知 | **已解决**：给出真实覆盖率、样例命中结果、版本化别名补丁方案与验收口径 |

两项均不再阻塞 B01。迁移 SQL 属于阶段 E 编码依据；别名补丁属于阶段 F 召回依据。

## 2. 迁移 SQL 收口

### 2.1 主库现状（Room schema v15）

证据：`app/schemas/com.qingji.app.data.local.AppDatabase/15.json`

真实列名（注意 Room 使用 camelCase，不是 snake_case）：

```sql
CREATE TABLE `training_plan` (
  `uuid` TEXT NOT NULL,
  `name` TEXT NOT NULL,
  `source` TEXT NOT NULL DEFAULT '',
  `isActive` INTEGER NOT NULL DEFAULT 0,
  `createdAt` INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY(`uuid`)
);

CREATE TABLE `user_profile` (
  `id` INTEGER NOT NULL,
  ...
  `createdAt` INTEGER NOT NULL,
  PRIMARY KEY(`id`)
);
```

当前 `AppDatabase.version = 15`，已有 `MIGRATION_1_2` 至 `MIGRATION_14_15`，无 `fallbackToDestructiveMigration()`。

### 2.2 主库 v15 → v16 迁移 SQL

阶段 E 实施时新增 `MIGRATION_15_16`。以下是可直接放入 `migrate(db)` 的 SQL 语义：

```sql
-- 1. 训练计划版本字段
ALTER TABLE `training_plan`
  ADD COLUMN `version` INTEGER NOT NULL DEFAULT 0;

ALTER TABLE `training_plan`
  ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0;

-- 2. 营养目标版本字段（目标仍在 user_profile 单行）
ALTER TABLE `user_profile`
  ADD COLUMN `goalVersion` INTEGER NOT NULL DEFAULT 0;

ALTER TABLE `user_profile`
  ADD COLUMN `goalUpdatedAt` INTEGER NOT NULL DEFAULT 0;

-- 3. 提案执行权威记录（主库）
CREATE TABLE IF NOT EXISTS `ai_proposal_execution` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `proposalId` TEXT NOT NULL,
  `proposalType` TEXT NOT NULL,
  `targetTable` TEXT NOT NULL DEFAULT '',
  `targetRecordId` TEXT NOT NULL DEFAULT '',
  `baseVersion` INTEGER NOT NULL DEFAULT 0,
  `status` TEXT NOT NULL,
  `idempotencyKey` TEXT NOT NULL,
  `errorCode` TEXT NOT NULL DEFAULT '',
  `errorMessage` TEXT NOT NULL DEFAULT '',
  `createdAt` INTEGER NOT NULL,
  `executedAt` INTEGER NOT NULL DEFAULT 0,
  `updatedAt` INTEGER NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_proposal_execution_idempotencyKey`
  ON `ai_proposal_execution` (`idempotencyKey`);
CREATE INDEX IF NOT EXISTS `index_ai_proposal_execution_proposalId`
  ON `ai_proposal_execution` (`proposalId`);
CREATE INDEX IF NOT EXISTS `index_ai_proposal_execution_status`
  ON `ai_proposal_execution` (`status`);

-- 4. 正式 AI 操作审计
CREATE TABLE IF NOT EXISTS `ai_operation_log` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `proposalId` TEXT NOT NULL DEFAULT '',
  `action` TEXT NOT NULL,
  `result` TEXT NOT NULL,
  `errorCode` TEXT NOT NULL DEFAULT '',
  `detailsJson` TEXT NOT NULL DEFAULT '',
  `idempotencyKey` TEXT NOT NULL DEFAULT '',
  `executedAt` INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS `index_ai_operation_log_proposalId`
  ON `ai_operation_log` (`proposalId`);
CREATE INDEX IF NOT EXISTS `index_ai_operation_log_idempotencyKey`
  ON `ai_operation_log` (`idempotencyKey`);
```

Room Entity 对应字段必须使用相同列名：

- `TrainingPlan.version` / `updatedAt`，`@ColumnInfo(defaultValue = "0")`。
- `UserProfile.goalVersion` / `goalUpdatedAt`，`@ColumnInfo(defaultValue = "0")`。
- 新 Entity 字段类型与默认值逐字一致。
- 迁移后导出 `16.json`，用 Room schema 校验比对。

### 2.3 AI 库 v1 DDL（新建库，不是主库迁移）

`qingji_ai.db` 首次创建时 version=1。建议建表：

```sql
CREATE TABLE IF NOT EXISTS `ai_conversation` (
  `id` TEXT PRIMARY KEY NOT NULL,
  `title` TEXT NOT NULL DEFAULT '',
  `entryPage` TEXT NOT NULL DEFAULT '',
  `createdAt` INTEGER NOT NULL,
  `updatedAt` INTEGER NOT NULL,
  `deletedAt` INTEGER NOT NULL DEFAULT 0,
  `schemaVersion` INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS `ai_message` (
  `id` TEXT PRIMARY KEY NOT NULL,
  `conversationId` TEXT NOT NULL,
  `sequence` INTEGER NOT NULL,
  `role` TEXT NOT NULL,
  `content` TEXT NOT NULL DEFAULT '',
  `messageType` TEXT NOT NULL,
  `toolCallsJson` TEXT NOT NULL DEFAULT '',
  `tokenUsageJson` TEXT NOT NULL DEFAULT '',
  `providerId` TEXT NOT NULL DEFAULT '',
  `modelId` TEXT NOT NULL DEFAULT '',
  `status` TEXT NOT NULL,
  `createdAt` INTEGER NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_message_conversationId_sequence`
  ON `ai_message` (`conversationId`, `sequence`);

CREATE TABLE IF NOT EXISTS `ai_tool_call` (
  `id` TEXT PRIMARY KEY NOT NULL,
  `conversationId` TEXT NOT NULL,
  `messageId` TEXT NOT NULL DEFAULT '',
  `callId` TEXT NOT NULL,
  `toolName` TEXT NOT NULL,
  `argumentsJson` TEXT NOT NULL DEFAULT '',
  `resultJson` TEXT NOT NULL DEFAULT '',
  `status` TEXT NOT NULL,
  `durationMs` INTEGER NOT NULL DEFAULT 0,
  `errorCode` TEXT NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_tool_call_conversationId_callId`
  ON `ai_tool_call` (`conversationId`, `callId`);

CREATE TABLE IF NOT EXISTS `ai_proposal` (
  `id` TEXT PRIMARY KEY NOT NULL,
  `proposalType` TEXT NOT NULL,
  `title` TEXT NOT NULL DEFAULT '',
  `reason` TEXT NOT NULL DEFAULT '',
  `beforeJson` TEXT NOT NULL DEFAULT '',
  `afterJson` TEXT NOT NULL DEFAULT '',
  `targetTable` TEXT NOT NULL DEFAULT '',
  `targetRecordId` TEXT NOT NULL DEFAULT '',
  `baseVersion` INTEGER NOT NULL DEFAULT 0,
  `status` TEXT NOT NULL,
  `expiresAt` INTEGER NOT NULL DEFAULT 0,
  `createdAt` INTEGER NOT NULL,
  `updatedAt` INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS `index_ai_proposal_status_expiresAt`
  ON `ai_proposal` (`status`, `expiresAt`);

CREATE TABLE IF NOT EXISTS `ai_token_usage` (
  `id` TEXT PRIMARY KEY NOT NULL,
  `conversationId` TEXT NOT NULL DEFAULT '',
  `providerId` TEXT NOT NULL DEFAULT '',
  `modelId` TEXT NOT NULL DEFAULT '',
  `inputTokens` INTEGER,
  `outputTokens` INTEGER,
  `taskType` TEXT NOT NULL DEFAULT '',
  `requestedAt` INTEGER NOT NULL
);
```

说明：

- Token 字段可空；供应商不返回时留 NULL，不伪造 0。
- AI 库不保存幂等权威键；幂等键在主库 `ai_proposal_execution` 唯一索引中。
- 长期记忆表留到阶段 I 以 v2 迁移新增，本轮不提前建表。

### 2.4 迁移验收要求

- 从 v15 迁移到 v16 后，旧 `training_plan` 可读，version=0、updatedAt=0。
- 旧 `user_profile` 可读，goalVersion=0、goalUpdatedAt=0。
- `ai_proposal_execution.idempotencyKey` 唯一索引生效，重复键插入失败。
- `ai_message(conversationId, sequence)` 唯一索引生效。
- Room schema 导出 `16.json` 与 Entity 一致。
- `connectedAndroidTest` 迁移测试覆盖 v15→v16；本轮未执行，阶段 E 必须执行。

## 3. 别名覆盖率收口

### 3.1 真实数据核查

数据库文件：`app/src/main/assets/database/food_catalog.db`（4,059,136 bytes）

| 表 | 总行数 | aliases 非空 | 含 `|` 多别名 | 中文别名 | 拉丁别名 |
|---|---:|---:|---:|---:|---:|
| `food_catalog` | 1281 | 420（32.8%） | 17 | 250 | 176 |
| `food_entity` | 166 | 166（100%） | 0 | 0 | 166 |

其他结构：

- `nutrition_sample`：8033 行。
- `food_composition`：27457 行，287 个 distinct `ingredient_name`。
- `sqlite_master` 中无 FTS 表、无独立 alias 表。
- `food_catalog` 索引：name / pinyin / pinyin_full / category。

结论：已有别名数据，但 `food_catalog.aliases` 主要是“去括号同名”，`food_entity.aliases` 全部是英文名；不足以覆盖中餐俗称。

### 3.2 工单样例命中现状

| 输入词 | `food_catalog` | `food_entity` | 结论 |
|---|---|---|---|
| 番茄炒蛋 | 100096 命中 | 无 | 可达 |
| 西红柿炒鸡蛋 | 100659 / 200143 命中 | omni:5 命中 | 可达，但与番茄炒蛋未建立别名关系 |
| 木须肉 | 100185 命中 | 无 | 可达 |
| 木樨肉 | 无 | 无 | **不可达** |
| 青椒肉丝 | 100183 命中 | 无 | 可达 |
| 尖椒肉丝 | 无 | 无 | **不可达** |
| 宫保鸡丁 | 100190 / 100715 / 200097 命中 | omni:61 命中 | 可达 |
| 宫爆鸡丁 | 无 | 无 | **不可达** |

当前 8 个样例词中 5 个可达、3 个不可达。不可达项为：木樨肉、尖椒肉丝、宫爆鸡丁。

### 3.3 别名补丁方案（不写公共库）

新增版本化只读资源文件，建议路径（阶段 F 实施）：

```text
app/src/main/assets/food_alias_patch/food_alias_patch_v1.json
```

内容示例：

```json
{
  "schemaVersion": 1,
  "appliesToCatalogVersion": "0.9-lxj-kcal-match",
  "aliases": [
    {
      "alias": "西红柿炒鸡蛋",
      "normalizedAlias": "番茄炒蛋",
      "candidateFoodIds": [100096, 100659, 200143],
      "preferredFoodId": null,
      "source": "curated",
      "note": "同菜异名；营养值冲突需用户确认或后续审校"
    },
    {
      "alias": "番茄炒蛋",
      "normalizedAlias": "番茄炒蛋",
      "candidateFoodIds": [100096, 100659, 200143],
      "preferredFoodId": null,
      "source": "curated"
    },
    {
      "alias": "木樨肉",
      "normalizedAlias": "木须肉",
      "candidateFoodIds": [100185],
      "preferredFoodId": 100185,
      "source": "curated"
    },
    {
      "alias": "尖椒肉丝",
      "normalizedAlias": "青椒肉丝",
      "candidateFoodIds": [100183],
      "preferredFoodId": 100183,
      "source": "curated"
    },
    {
      "alias": "宫爆鸡丁",
      "normalizedAlias": "宫保鸡丁",
      "candidateFoodIds": [100190, 100715, 200097],
      "preferredFoodId": null,
      "source": "curated",
      "note": "同名多来源营养值差异大，保留候选"
    }
  ]
}
```

规则：

- 补丁只提供召回与排序，不写回 `food_catalog.db`。
- `preferredFoodId=null` 时返回多个候选，由用户确认，不强行选一个。
- 用户个人纠正存主库用户侧表，不进入公共补丁。
- 补丁版本与 `catalogVersion` 绑定；食物库升级时重新审校。
- 示例补丁后，8 个样例词全部可达（8/8），其中 3 个原本不可达词通过别名补丁可达。

### 3.4 匹配策略定稿

1. 启动时加载别名补丁到内存索引，与 `CatalogFood.aliases`、`OmniFoodEntity.aliases` 合并。
2. 先做完全命中：name / alias / normalizedAlias / pinyinFull。
3. 再做 `LIKE` 包含匹配，分类一致优先。
4. 再做 `food_composition.ingredient_name` 食材召回。
5. 返回 Top20 → 本地规则排序 → Top5。
6. 同名多来源或营养值差异大时保留候选，不自动收敛。
7. 无候选时明确无匹配，允许按食材估算但必须标记。

FTS5 仍不引入。理由：现有库无 FTS 表，DAO 注释明确为老安卓兼容与 1281 条规模不引入；别名补丁 + LIKE 已能覆盖样例。

### 3.5 别名覆盖率验收口径

阶段 F 必须通过：

- 8 个样例词 8/8 可召回。
- 3 个原本不可达词通过补丁可达。
- 同名多来源不强行选一个，候选进入用户确认。
- 用户纠正不写公共库。
- 食物库版本变化时补丁 `appliesToCatalogVersion` 校验失败需提示并禁用旧补丁。
- 无匹配路径可工作。

## 4. 对既有文档的影响

- 《版本控制与并发修改最终设计.md》：补充主库 v16 与 AI 库 v1 的具体 DDL。
- 《食物匹配数据与别名最终设计.md》：补充真实覆盖率与别名补丁 v1。
- 《六份设计文档交叉复审报告.md》：两项状态从未解决/部分解决改为已解决。
- 《README.md》：加入本报告索引。

## 5. 仍未解决但不阻塞 B01

- 别名补丁的长期维护流程与人工审校责任人。
- 同名多来源营养值差异的最终 canonical 选择。
- AI 库长期记忆表 v2 迁移（阶段 I）。
- Compose UI 测试环境（阶段 D）。

本轮未改数据库文件、未改实体、未执行迁移、未写业务代码。
