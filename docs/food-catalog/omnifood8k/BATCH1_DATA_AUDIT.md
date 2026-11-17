# OmniFood8K 食物数据库接入 — 第一阶段数据审计报告

日期：2026-11-17  
范围：只做数据审计与设计，不改 App 代码、不改任何数据库。  
结论：**清洗包内部计数自洽，单位换算全部正确；可以按「样本与正式食物分离、异常隔离、来源可追溯」方案进入建模阶段。**

---

## 一、附件与校验

原始资料完整路径：`C://Users//22109//OneDrive//Desktop//mulin//workbuddy//体重管理App//0-OminiFood8k`

| 原始文件 | 大小 | SHA-256 |
|---|---:|---|
| `8k-result.xlsx` | 2,506,126 | `81ccd88cd355e4662ce11cbf5f84823f4ba63204c1a6fdf6f802b2ba22d20dac` |
| `ReadMe.txt` | 1,507 | `e20379d5e5705a8611310483bd7e1d8b42cc08d2339ff40a23fe0df658bdbb37` |
| `ingred-nutrition.xlsx` | 28,363 | `bd813c63beb1de78c09bf9de1de7528b486430ea7985d5279dcdd61df0029458` |
| `train_new333.txt` | 401,861 | `63c55b92c6ad1e3a55bd19eac4490bd5e50c87187c325a05def49806c90320ef` |
| `test_new333.txt` | 175,226 | `7f8b71061bdc0c85694ce9e42929c5f263cb496f8d3a22bdf12ef22eede5ac83` |

原始目录另有：`8036/` 8,041 个条目、`Ingredient-140/` 141 个条目、`Recipe/Recipe-CN.txt`、`Recipe/Recipe-EN.txt`。本次清洗包已覆盖核心计数，原始文件保留可追溯。

ReadMe 明确确认：「样本由多个菜品组成时，食材组成字段是菜品 ID；最后部分的单菜品样本，字段是具体食材成分」。这与工单规则一致。

| 文件 | 大小 | SHA-256 |
|---|---:|---|
| `omnifood8k_dish_catalog.json` | 115,951 | `ae48cc0a9b56850733a0a187f02eb1a1399ada714ce83f619f1f6ce80ba07bb2` |
| `omnifood8k_clean_package.zip` | 1,411,620 | `f311d68e7db37aaf879fa5c7362eae61e9309030125049a1f4ab6c0a93aae0a2` |
| `omnifood8k_audit.xlsx` | 4,874,628 | `abab3d723aba3c1389de312d21a1e0d270f0cd7ea7fe8fa9c5572f8d7c2166f5` |

XLSX 工作表：审计摘要 15 行、标准化样本 8,038 行、食物字典 167 行、组成明细 27,467 行、异常清单 471 行、原始 Sheet1 8,053 行、原始 Sheet3 167 行。行列数含表头。  
清洗包 6 个 CSV：food_dictionary 166 行、samples_cleaned 8,037 行、composition_long 27,466 行、quality_issues 470 行、audit_summary 14 行、dish_catalog JSON 141 条。

---

## 二、规模核对（全部对上）

| 项目 | 工单要求 | 实测 | 结论 |
|---|---:|---:|---|
| 原始营养样本 | 8,037 | 8,037 | ✅ |
| 编码组合场景 | 7,896 | 7,896 | ✅ |
| 单菜品食材拆解 | 141 | 141 | ✅ |
| 已命名菜品 | 141 | 141 | ✅ |
| 水果 | 24 | 24 | ✅ |
| 米饭 | 1 | 1（编码 0） | ✅ |
| 食物字典 | 166 | 166 | ✅ |
| 组成明细 | 27,466 | 27,466 | ✅ |

菜品代码：dish_catalog.json 的 141 个 `food_code` 与字典里的 141 个 dish 代码**完全一致**。字典名称 166/166 唯一。组成明细的 `sample_id` 全部能在 8,037 样本里找到，无孤儿行。

---

## 三、营养单位核验

- 8,037 条样本全部满足 `energy_kcal = energy_kj_raw / 4.184`（误差 < 0.01）。原始 kJ 与换算 kcal 可同时保留。
- 8,037 条样本的 `energy/protein/fat/carbs per_100g` 全部满足 `总量 / weight_g * 100`（误差 < 0.01）。
- 注意：`ReadMe.txt` **没有明确写 Total Energy 的单位**。kJ 判断来自 4/9/4 公式高度吻合，是推断而非原文确认。因此必须同时保留原始数值与换算值，不能声称「已由数据源确认单位为 kJ」。
- 结论：**不能把原始 Total Energy 当 kcal**；应用展示与计算统一用 kcal，原始数值留档。

---

## 四、异常数据

| 类型 | 数量 | 处理 |
|---|---:|---|
| 组成重量与总重量差 > 10g | 349 | `pending_verification`，保留原值 |
| 4/9/4 能量差异 > 10% | 102 | `pending_verification`，公式只作近似提示 |
| 未映射编码 177 | 19 条明细 / 19 条样本 | 明确标记，不猜测食材 |
| 能量密度 > 900 kcal/100g | 3（样本 346/347/348） | 严重异常，隔离 |
| 宏量营养素 > 总重量 | 2（样本 346/347） | 严重异常，隔离 |
| 脂肪字段缺失 | 1（样本 2740） | 严重异常，隔离 |
| 重量 ≤ 0 | 0 | ✅ |

异常清单共 470 行。严重异常样本合计 4 条（346/347/348/2740），不得进入正式可搜索食物库；349 + 102 + 19 为待复核，保留但与正式推荐数据分区。

另外，dish_catalog 里 141 道菜中 **133 条 `needs_quality_review`、8 条 `provisional`**。8 条 provisional：凉拌黄瓜、肉沫玉米粒、杏鲍菇炒肉、清蒸基围虾、皮蛋、凉拌海带结、清炒豆泡、凉拌海白菜。这 8 条也不能默认为「已审核可推荐」。

---

## 五、数据模型结论

必须沿用双库：

- `food_catalog.db`（只读标准库）：新增 `FoodEntity` / `FoodNutrition` / `FoodComposition` / `NutritionSample` / `FoodDataSource`，按工单字段建表。`NutritionSample` 不进普通食物搜索，`FoodComposition` 独立存 27,466 行。
- `qingji.db`：用户饮食记录、收藏、自定义食物、别名、历史营养快照一行不动。标准库更新不能 clear/REPLACE 用户库。

导入原则：
1. 以 `food_code` + `source_id` 建唯一性，不用 sample_id 当 food_id。
2. 严重异常进隔离区，待复核异常带 `pending_verification`，正常数据才 `approved`。
3. 组合场景只保留组成关系与营养总量，不生成“麻婆豆腐 + 米饭”这种假菜品。
4. 重复导入按版本 + 唯一键幂等；失败整体回滚。
5. 来源与 CC BY-NC 4.0 写入 `FoodDataSource`，应用内保留署名。

---

## 六、数据来源与许可

- 数据集：OmniFood8K；原始文件：`8k-result.xlsx`；项目地址：https://github.com/yudongjian/OmniFood8K-food
- 许可：CC BY-NC 4.0（非商业）。清洗包内**没有独立 LICENSE 文件**，许可信息来自工单；导入时必须在 `FoodDataSource` 和 App 来源页保留。
- 不得把第三方数据标为轻记原创；不得把这些标准菜品混入 MET 运动消耗库。

---

## 七、下一阶段

1. 第二阶段：按上述结构完成 Room Entity / DAO / Migration 与离线 ETL 脚本，生成可校验的 `food_catalog.db`。
2. 第三阶段：接入现有搜索/详情/饮食记录，不改用户库。
3. 尚未做：UI、迁移测试、真机验收。不得提前宣称完成 12,000 条扩充。

**状态：第一阶段完成，等待确认后进入数据库建模与 ETL。**
