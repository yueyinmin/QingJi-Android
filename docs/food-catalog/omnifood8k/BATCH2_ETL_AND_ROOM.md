# OmniFood8K 食物数据库接入 — 第二阶段 ETL 与数据库设计报告

日期：2026-11-17  
范围：离线 ETL + `food_catalog.db` 接入。未改 `qingji.db`，未改 UI。

## 一、ETL 脚本

`tools/food_import/omnifood8k_etl.py`：
- 输入：清洗包 6 个 CSV、dish_catalog JSON、现有预置库。
- 输出：3.2MB `app/src/main/assets/database/food_catalog.db`，重复运行从零生成，幂等。
- 强校验：样本 8,037、组成 27,466、字典 166、菜品 141、水果 24、米饭 1、异常 470，任一不符直接失败。
- 单位：保留原始 kJ，kcal = kJ / 4.184；每 100g 值在导入前逐条核验通过。
- 审核状态：166 条新食物全部标 `needs_verification` / `provisional`，没有一条冒充 `approved`。

## 二、生成结果

| 表 | 行数 | 说明 |
|---|---:|---|
| food_catalog | 820 | 原 654 条原样保留 + 166 条新食物 |
| food_entity | 166 | 141 菜品 + 24 水果 + 1 米饭，含拼音与来源 |
| food_nutrition | 166 | kJ/kcal 双留档 + 每100g |
| nutrition_sample | 8,037 | 原始样本，不进普通搜索 |
| food_composition | 27,466 | 组成明细独立表 |
| quality_issue | 470 | 异常标记，原值保留 |
| food_data_source | 1 | OmniFood8K / CC BY-NC 4.0 / 原始文件与导入时间 |

## 三、Room 接入

- `FoodCatalogDatabase` version 2 → 3，注册 6 张新表和 `OmniFoodCatalogDao`。
- `CATALOG_VERSION` 0.3 → `0.4-omnifood8k`；换库只替换标准库文件，不碰用户库。
- 新增测试 `OmniFoodCatalogTest`：计数、中文/英文/拼音搜索、审核状态、kJ/kcal、严重异常隔离。
- 严重异常样本 346/347/348/2740 只保留在样本表，不生成正式 food_id。

## 四、风险与边界

- `ReadMe.txt` 没写 Total Energy 单位；kJ 结论来自 4/9/4 一致性推断，报告已明确标注，不宣称数据源确认。
- 8 条 provisional 菜品仍待人工审核。
- 349/102/19 三类待复核异常保留原值并打标，不删除、不猜测。
- UI 尚未接入新 DAO；现有 `food_catalog` 搜索继续可用，166 条新食物已进入旧表，显示来源 OmniFood8K、审核状态未核对。

**状态：数据层完成，等待云端编译与测试。**
