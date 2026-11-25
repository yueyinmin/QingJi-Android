# 轻记 AI 原生化 · 食物匹配数据与别名最终设计

> 工单：QJ-AI-NATIVE-DOC-REVIEW-V1 · 任务五  
> 基线 HEAD：`c97ada3d98678cc7f178061a84404b64a95d6066`  
> 性质：阶段 F 前置定稿；本轮不改代码、不改表。

---

## 1. 结论

1. 现有 `food_catalog.db` **已具备别名字段**：`CatalogFood.aliases`（`|` 分隔）与 `OmniFoodEntity.aliases`，但注释明确当前只放“去掉括号后的名字”，覆盖有限。
2. 不新建 AI 库别名表。通用别名属于**版本化只读食物基础数据**：优先扩充现有只读库中的别名数据；如需规范化别名表，也必须随 `food_catalog.db` assets 版本发布，不在运行时写入。
3. 用户个人纠正（个人别名/常吃偏好）放主库用户侧数据，不污染公共食物库。
4. 匹配采用“AI 结构化识别 → 本地混合召回 → 本地规则排序 → Top5 → 模型辅助精排 → 本地营养计算 → 用户确认 → 正式写入”。
5. FTS5 不是唯一召回方式；当前 DAO 明确不引 FTS5（老安卓 createFromAsset 兼容 + 654 条数据规模），继续以 `LIKE` 多字段召回为主，FTS 只能作为后续可选增强。
6. 必须允许无匹配结果，模型不得为了完成任务强行选相似食物。

## 2. 现有真实结构

### 2.1 标准食物库

`app/src/main/java/com/qingji/app/data/local/entity/CatalogFood.kt:36-83`

| 字段 | 说明 |
|---|---|
| `food_id` | 100001 起；与用户库 id 不冲突 |
| `name` | 标准食物名 |
| `aliases` | `|` 分隔；当前仅“去掉括号后的名字”，不是完整中文别名表 |
| `pinyin` / `pinyin_full` | 拼音首字母/全拼，机器生成，仅检索 |
| `category` / `subcategory` | 分类 |
| `energy_kcal` / `protein` / `fat` / `carbs` | 每 100g 营养 |
| `fiber` / `sodium` / `sugar` / `calcium` | 可空；NULL=未收录，0=实测为 0 |
| `basis_grams` | 恒为 100 |
| `default_grams` / `portion_note` | 默认份量 |
| `source_id` / `source_note` / `audit_status` / `catalog_version` | 来源与审核状态 |

数据库：`FoodCatalogDatabase.kt:43-69`，version=5；assets `database/food_catalog.db`；`createFromAsset` (159)；版本变化 delete + 重建 (123-137)；`fallbackToDestructiveMigration()` 仅用于只读公共库 (161-164)。

DAO：`FoodCatalogDao.kt:11-12` 明确无写方法；`search()` 覆盖 name / aliases / pinyin / pinyin_full 四种 `LIKE` 召回，注释说明不引 FTS5。

### 2.2 OmniFood8K 扩展

`app/src/main/java/com/qingji/app/data/local/entity/OmniFoodEntities.kt`

- `food_entity`：`name_cn`、`name_en`、`aliases`、`pinyin`、`pinyin_full`、`food_type`、`category`、`dataset_version`。
- `food_nutrition`：`energy_kcal_per_100g`、`protein_per_100g`、`fat_per_100g`、`carbohydrates_per_100g`、`nutrition_basis`、`audit_status`。
- `nutrition_sample` / `food_composition`：样本与食材组成关系，可作为组合菜召回与解释的数据源。
- DAO `OmniFoodCatalogDao` 已提供 `search/findByNameCn/nutritionFor/compositionForSample`。

### 2.3 真实覆盖率（已核查）

`food_catalog.db` 实测：

| 表 | 总行数 | aliases 非空 | 含 `|` 多别名 | 中文别名 | 拉丁别名 |
|---|---:|---:|---:|---:|---:|
| `food_catalog` | 1281 | 420（32.8%） | 17 | 250 | 176 |
| `food_entity` | 166 | 166（100%） | 0 | 0 | 166 |

`nutrition_sample` 8033 行、`food_composition` 27457 行、287 个 distinct 食材名；无 FTS 表、无独立 alias 表。

工单 8 个样例词当前 5 个可达、3 个不可达：木樨肉、尖椒肉丝、宫爆鸡丁。完整命中表、别名补丁 v1 JSON 与验收口径见《迁移SQL与别名覆盖率收口报告.md》。结论是：现有字段可用但覆盖不足；阶段 F 以版本化只读别名补丁补齐，补丁后样例词要求 8/8 可达。

## 3. 数据归属定稿

| 数据 | 归属 | 写入方 | 说明 |
|---|---|---|---|
| 标准食物名/营养/分类 | `food_catalog.db` 只读公共库 | 资源构建流程 | 与 App 用户数据分离 |
| 通用别名（番茄炒蛋/西红柿炒鸡蛋等） | 版本化只读食物数据；优先扩充 `CatalogFood.aliases` / `OmniFoodEntity.aliases`，必要时在 food_catalog assets 中增加规范化只读 alias 表 | 资源构建流程 | **不放 `qingji_ai.db`**，不在运行时写公共库 |
| 拼音/简称 | 现有 `pinyin` / `pinyin_full` | 资源构建流程 | 已存在，直接复用 |
| 食材组成/做法 | `nutrition_sample` / `food_composition` 等只读表 | 资源构建流程 | 覆盖度有限，不能假设完整 |
| 用户个人别名/常吃偏好/识别纠正 | 主库用户侧表（阶段 F 新增） | 用户操作 | 与公共库隔离，用户可删改 |
| AI 识别 JSON/置信度 | AI 库消息/工具历史或饮食记录快照字段 | AI 系统 | 不写公共食物库 |

**禁止**：模型直接修改公共标准食物库、公共别名或审核状态。

## 4. 匹配流程定稿

```text
图片/自然语言
      ↓
AI 结构化识别（菜名、食材、做法、份量、重量区间、置信度、不确定因素）
      ↓
本地混合召回 Top20：
  标准名称 / 现有 aliases / 拼音 / 分类 / 食材 / 组成关系
      ↓
本地规则过滤与排序：
  别名规范化 → 完全命中优先 → 分类一致 → 食材重合 → 份量/做法一致
      ↓
Top5 候选
      ↓
模型辅助精排（仅排序，不改营养数值）
      ↓
确定匹配 / 明确无匹配
      ↓
本地 NutritionCalculator / per100g 规则计算营养
      ↓
用户确认卡片（可替换食物、改重量、改餐次）
      ↓
经提案/确认后写入正式饮食记录
```

### 4.1 召回规则

- 第一优先：`name` 完全命中、`aliases` 完全命中。
- 第二优先：`pinyin_full` / `pinyin` 命中。
- 第三优先：`LIKE` 包含匹配 + 分类一致。
- 第四优先：`food_composition` / `ingredient_name` 食材匹配。
- FTS5 若未来引入，只能作为额外召回通道，不能成为唯一通道。

### 4.2 规范化建议（阶段 F 实现）

- 统一同义字：番茄/西红柿、青椒/尖椒、宫保/宫爆、木须/木樨。
- 去除口语修饰：不清、微辣、少油、家常等作为烹饪/做法线索，不直接当菜名。
- 保留原名与规范化名两个字段，避免丢失用户表达。
- 规范化规则作为版本化资源，与别名数据同版本发布。

### 4.3 无匹配策略

必须允许无匹配，且：

- 返回“未找到可靠匹配”，列出 AI 提取到的食材/做法供用户手动选择。
- 不自动选最相似食物写入。
- 若按食材估算，必须标记 `is_estimated=true` 与估算依据，且仍需要用户确认。

## 5. 营养计算边界

- 标准成品菜：`per100g × grams / 100`，使用 `CatalogFood` 或 `OmniFoodNutrition` 的已审核数据。
- 组合菜：优先用 `nutrition_sample` / `food_composition`；缺数据时按食材估算并明确标记。
- 模型只负责识别与排序建议，不输出最终 kcal/蛋白/碳水/脂肪数值。
- `AuditStatus` 为 UNCHECKED 的食物应在 UI 提示“估算值/未核对”。

## 6. 个人纠正规则

- 用户改匹配结果：保存个人纠正记录（原识别文本、用户选择 food id、时间、来源）。
- 用户常用别名：可加入个人偏好，不写公共别名。
- 个人纠正只影响该用户后续匹配排序，不修改公共库。
- 用户可查看、删除个人纠正。

## 7. 测试方案

阶段 F 必须覆盖：

1. 标准名称匹配。
2. 现有 aliases 匹配。
3. 工单举例别名组：番茄炒蛋/西红柿炒鸡蛋、木须肉/木樨肉、青椒肉丝/尖椒肉丝、宫保鸡丁/宫爆鸡丁。
4. 拼音/全拼匹配。
5. 食材/组成召回。
6. 无匹配结果。
7. 估算数据标记。
8. 重量修改后营养重算。
9. 用户个人纠正只影响个人，不写公共库。
10. 与首页今日记录的营养口径一致。

## 8. 未解决 / 需编码验证

- 现有 aliases 的实际覆盖率需在阶段 F 用真实数据抽样统计；本轮只确认字段存在，不宣称覆盖完整中餐。
- `food_composition` / `nutrition_sample` 的实际数据覆盖度需抽样；不足时必须走无匹配/估算路径。
- 若新增规范化 alias 表，必须走 `food_catalog.db` assets 版本发布，禁止运行时写公共库。
