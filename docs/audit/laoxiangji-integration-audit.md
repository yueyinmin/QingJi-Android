# 老乡鸡接入 · 第一阶段项目审计报告

> 工单：轻记 Android｜标准食物库升级（老乡鸡品牌接入 + 堂食菜品库 + 外卖快餐品牌菜单 + 溯源档案 + UI 改造）
> 阶段：第一阶段「项目审计」（工单第十五节）
> 审计时间：2026-11-17 · 审计分支：`feature/laoxiangji-integration`（自 `a668862` 新建，工单 A 四提交为基线）
> 结论先行：**现有代码与数据已完成本工单约 40% 的前置工作**——来源切换 UI、CookLikeHOC 全仓扫描、堂食数据清洗都已存在；真正的新增工作量在「品牌层 UI + 堂食入库 + 溯源档案 + 外卖数据结构（空态）」。

---

## 1. 现有功能与结构盘点

### 1.1 UI：来源切换已存在（工单 2.1 假设成立）

`FoodLibraryScreen.kt`（716 行）现有结构：

| 现状 | 位置 | 工单要求对照 |
| --- | --- | --- |
| 顶部「家庭厨房 / 外卖快餐」胶囊切换 | L199 `Segmented(listOf("家庭厨房","外卖快餐"), kitchenTab)` | ✅ 已有，保留 |
| 家庭厨房模式：左分类栏 + 右列表 + 收藏 + 热量 | L203-267 | ✅ 已有，不动 |
| 外卖快餐模式：目前按 `FAST_FOOD` 分类过滤普通食物 | L268-300 | ⚠️ 需改造为「品牌选择 → 品牌菜单」两级结构 |
| 外卖空数据提示「外卖快餐暂时还没有数据」 | L280 | ✅ 空态已存在，品牌化后沿用 |
| 底部标准/自定义/吃过导航 | L452+ | ✅ 不动 |

**关键结论：工单红线 2「不得破坏家庭厨房现有功能」与红线 16「不重做底部导航」的改动面只有外卖分支 + 新增品牌选择组件，家庭厨房分支零改动。**

### 1.2 数据库现状

**food_catalog.db（只读标准库，Room v3，`CATALOG_VERSION = 0.5-omnifood8k-reviewed`）**

| 表 | 行数 | 说明 |
| --- | ---: | --- |
| food_catalog 主表 | 820 | 654 老条目 + 166 OmniFood8K（audit_status=1 已复核），`source_id` 列已区分来源 |
| food_entity / food_nutrition | 166/166 | OmniFood8K 命名食物（工单所称 FoodEntity/FoodNutrition 的实际对应物） |
| nutrition_sample / food_composition | 8033/27457 | 样本与组成明细 |
| quality_issue / food_data_source | 466/1 | 异常标记与来源档案 |

⚠️ **没有 Brand / BrandFood / 任何溯源表**——工单第五、六节的表全部是新增。新表 = 标准库 Room v4 迁移，且预置库必须走 `omnifood8k_etl.py --room-schema` 机制重建（v1.3.1 踩过「手写 SQL 与 Room schema 不一致导致预置库被清空」的坑，绝不重犯）。

**qingji.db（用户库，Room v12）**：food_log / user_food / food_favorite / food_search_history 等 28 表，迁移链 1→12 完整。

⚠️ **收藏按名称绑定**：`FoodFavorite` 主键就是菜名字符串（UserFood.kt:60-63）。工单第十一节要求「收藏绑定稳定 ID，不依赖商品名称」——现有机制不满足。兼容方案见 §4 决策点 3。

### 1.3 已有数据资产（上一窗口产出，今天 2026-11-17 完成）

| 资产 | 位置 | 状态 |
| --- | --- | --- |
| CookLikeHOC 全仓审计报告 | `docs/food-catalog/laoxiangji/BATCH1_REPO_AUDIT.md` | ✅ 针对 HEAD `4d8e068`（2026-10-03 最新提交）完成，快照 571 文件/359 MD 已校验 |
| 堂食数据清洗 | `BATCH2_DINEIN_CLEANING.md` + `laoxiangji_dinein.json` + 5 份 CSV | ✅ 280 菜品 / 40 配料 / 21 饮品 / 92 条营养 / 2,267 条配料记录；多版本菜品独立保留；出品克重 0 条（不编造）；全部 `needs_verification` + `unknown_no_license` |
| 清洗工具 | `tools/food_import/laoxiangji_dinein_clean.py` | ✅ 可重跑 |
| 原始快照 | `cooklikehoc_md_dump.txt` / `cooklikehoc_c20.json` / `cooklikehoc_pr216_*.json` 等 | ✅ 含 PR #216 patch 全文（18 道炒菜营养候选值，未合并进 main，仅作候选） |

### 1.4 搜索 / 收藏 / 记录逻辑现状

- 搜索：`QingJiRepository.searchFood()` 走 `catalogDao.search()`（name/aliases/pinyin/pinyin_full 四路）+ 用户库合并，FoodLibraryScreen 的 `matchesFood()` 再做本地过滤。品牌/菜品搜索可在此扩展，不动主链路。
- 记录：`SearchFoodViewModel.addFood()` / FoodLibraryScreen 的 `AddToMealDialog` → `FoodLog`。外卖套餐拆分记录需要新的记录路径（food_log 现有字段够存单项，套餐组合快照需要新增字段或快照表，见 §4 决策点 4）。

---

## 2. 数据源核对结论（工单第三节 → 第二阶段，本报告一次完成）

### 2.1 CookLikeHOC

- **HEAD `4d8e06812f`（2026-10-03）自今日 12:57 审计以来无新提交**（GitHub API 实时核对），已完成的 BATCH1/BATCH2 审计与清洗仍然有效，无需重扫。
- 数据规模与质量：279+1 道菜 / 40 配料 / 21 饮品；仅 92 条含营养（每 100g 口径，五项）；**出品克重字段为零**；无任何外卖商品/价格数据（关键词 0 命中）。
- PR #216（18 道炒菜营养）**未合并**，其 patch 里的数值只能当「候选待核对」，不得当权威。
- 授权：**无 LICENSE**（GitHub license: null），README 自述文字大段摘编自官方溯源报告 → 红线：步骤原文与图片不随 APK 分发，只做内部结构化参考（与 BATCH2 现有处置一致）。

### 2.2 lxj-trace（本次新扫描，上一轮未覆盖）

- HEAD `fea82e034b`（2026-05-08），20 个文件，无 LICENSE（授权状态 unknown）。
- **核心资产：`references/report-text.md`（1.12 MB，831 个标题）+ `report.pdf`**——是《老乡鸡菜品溯源报告 2.0》全文提取，含每道菜页面（加工等级/配料/营养成分/原料来源/原料加工/原料配送/餐厅操作工艺）。这是第五节「品牌溯源档案」的主要数据来源。
- `topic-map.md` / `report-index.md` 只是索引（工单红线 6：不得当原始报告）；`fact-check-rules.md` 的证据标准（精确数值必须回 report-text/pdf 核对）应作为我们导入器的核对规则。
- ⚠️ **红线 7 确认：lxj-trace 与 CookLikeHOC 用的是同一份官方报告 2.0**（CookLikeHOC README 链接官方入口，lxj-trace 直接全文提取）。两者互证不构成两个独立证据；溯源数据以 report-text.md 为准，CookLikeHOC 的菜品营养作为交叉参考。

### 2.3 老乡鸡官方资料

- 官方入口 `www.lxjchina.com.cn/info.php?class_id=102106`：报告 2.0 的权威出处。本环境直接访问待验证（上一轮通过仓库转储间接使用）。**第四阶段导入溯源数据时，加工等级/供应商/检测数值必须逐条记录报告版本与页码**（lxj-trace 的 report-text.md 按报告原文结构提取，页码信息以 report.pdf 为准）。

### 2.4 外卖商品数据（工单 3.4）——结论：无真实数据可用

- CookLikeHOC：0 条外卖数据。
- 美团/饿了么：公开页面需登录/风控，**不得绕过**（工单明令）。
- **处置：按红线 15 执行——外卖模块只建数据结构与 UI 骨架，商品列表为真实空态；老乡鸡外卖商品数据需要用户后续手动提供（商品页截图/链接/导出），或等官方渠道开放。绝不造假数据。**

---

## 3. 工单需求 ↔ 现状的差距清单

| 工单要求 | 现状 | 差距 |
| --- | --- | --- |
| 来源两级切换 | ✅ 已有 | 外卖分支重写为品牌层 |
| 品牌动态读取（红线 5） | ❌ 无 Brand 表 | 新增 `brand` 表 + 种子（老乡鸡），UI 动态渲染 |
| 堂食菜品库 | ✅ 数据已清洗（280 条） | 缺：入 food_catalog 主表 + 分类映射 + Room v4 迁移 |
| 品牌溯源档案（第五节 10 张表） | ❌ 无 | 新增；数据来自 lxj-trace report-text（1.12MB），加工等级四枚举 |
| 外卖商品体系（第六节 8 张表） | ❌ 无 | 新增结构；数据为空态 |
| 套餐拆分记录 | ❌ 无 | 第八阶段；依赖外卖数据，先做结构与单测 |
| 搜索覆盖品牌/商品 | ⚠️ 部分复用 | catalogDao.search 扩展 brand 维度 |
| 收藏绑定稳定 ID | ❌ 按名称 | 见决策点 3 |
| 历史营养快照 | ⚠️ food_log 已存每条营养 | 套餐组合快照字段待定（决策点 4） |

## 4. 需要拍板的决策点（不阻塞，均已给默认方案，实施按默认走、用户可随时改）

1. **堂食分类映射**：CookLikeHOC 13 目录 → 工单 12 分类。默认映射：砂锅菜/烫菜/煮锅 → 其他（保留原目录名进 `subcategory`），其余一一对应；映射表写进导入脚本，可调。
2. **堂食菜品进主表**：进 `food_catalog`（820+280=1100 条），`source_id='laoxiangji'`，`audit_status=0`（needs_verification，无 LICENSE 不标已核对）；OmniFood8K 的 166 条不动。标准库 CATALOG_VERSION → `0.6-laoxiangji`，老手机自动换库。
3. **收藏兼容**：老收藏（按名）全部保留不动；品牌菜品收藏新增时同时写「名称行 + ID 行」（`food_favorite` 加可空 `foodKey` 列，Room v13 用户库迁移），查询优先 ID 回退名称。不破坏任何现有收藏。
4. **套餐快照**：food_log 已有每条营养字段；套餐拆分记录 = 一条 food_log/组成项 + 复用现有备注字段记套餐 ID？→ 默认方案：新增 `food_log` 两列（`snapshotJson` TEXT 可空 + `sourceRef` TEXT 可空），Room v13 一起迁移，历史记录不受影响。

## 5. 实施顺序与工作量（按工单第十五节对齐）

| 阶段 | 内容 | 预估 |
| --- | --- | --- |
| ~~一 审计~~ | 本报告 | ✅ 完成 |
| ~~二 数据源核对~~ | CookLikeHOC ✅ + lxj-trace ✅（本报告 §2） | ✅ 完成 |
| ~~三 堂食清洗~~ | laoxiangji_dinein.json ✅（280 条） | ✅ 完成，差分类映射 |
| 四 溯源数据 | 从 report-text.md 提取加工等级/原料/供应商/检测 → ETL | 大（解析器 + 人工抽查） |
| 五 外卖数据库 | 8 张表结构 + 空态导入 | 中 |
| 六 标准库 UI | 品牌选择组件 + 老乡鸡菜单 + 详情/溯源页 | 大 |
| 七/八 计算与记录 | 套餐拆分/快照/联动 | 中 |
| 九 回归 | 全量单测 + 迁移测试 + 真机 | 中 |

## 6. 红线自查（对应工单第十八节）

- 红线 3/4/11：配料（seasoning）不进可点菜品列表，独立表；外卖与堂食独立 ID。
- 红线 8/9：清洗数据缺失全为 null（194 道菜无营养保持 null）；出品克重 0 条不编造。
- 红线 14：无 LICENSE → 文字步骤/图片不进 APK，仅结构化字段入库。
- 红线 15：外卖无真实数据 → 空态，不造假。

---

*审计人：木木 · 下一阶段：第四阶段「溯源数据提取」（从 report-text.md 建解析器）→ 第五/六阶段并行推进。*
