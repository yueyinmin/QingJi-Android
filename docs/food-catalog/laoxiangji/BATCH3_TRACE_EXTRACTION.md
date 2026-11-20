# 老乡鸡溯源数据提取报告（第三批）

> 工单：标准食物库升级 · 第四阶段「溯源数据库」数据提取
> 生成时间：2026-11-17 · 提取器：`tools/food_import/laoxiangji_trace_extract.py`（可重跑）
> 输入：`lxjtrace_report-text_fea82e034b.md`（《老乡鸡菜品溯源报告 2.0》PDF 全文提取，
> 来自 lxj-trace @ `fea82e034b`，1.12MB / 830 页）
> 输出：`laoxiangji_trace.json`

---

## 1. 页型分类（830 个 PDF 页全部归类）

| 页型 | 页数 | 内容 | 对应工单表 |
| --- | ---: | --- | --- |
| 菜品溯源卡 | **177** | 加工等级/配料/每100g营养/原料来源/原料加工/配送/门店工艺 | BrandDishTrace / BrandNutritionEvidence / BrandDishIngredient |
| 原料物料溯源卡 | **2** | 大葱、熟猪油：类别/规格/产地/物料编码/检测项目/检测指标 | BrandIngredient / BrandInspectionReport |
| 原料供应商资料卡 | **84** | 产品分类/产地/感官指标/检验指标/资质证件/储运/供应商名单 | BrandIngredient / BrandSupplier |
| 非溯源页（封面/目录/综述/汇总表等） | 567 | 含分章节营养汇总表（数值与菜品卡一致，不重复采） | — |

## 2. 菜品卡解析结果（177 条，全部 needs_verification）

**加工等级分布（工单 5.6 四枚举，不做布尔简化）**：

| 枚举 | 条数 | 说明 |
| --- | ---: | --- |
| RESTAURANT_FRESH 餐厅现做 | 87 | 报告原文「餐厅现做」 |
| SEMI_PREPARED 半预制 | 28 | 报告原文「半预制」 |
| REHEAT_PREPARED 复热预制 | 1 | 报告原文「复热预制」 |
| UNKNOWN 未知 | 61 | 其中 **59 条报告原文标「/」**（该章节品类报告未逐菜标注，如实记 UNKNOWN 不推测）；2 条版面异常待人工核对 |

**其他字段覆盖率**：每100g 营养五项齐全 **144 条**（比 CookLikeHOC 侧的 92 条多 52 条——溯源报告营养覆盖更全，入库时以溯源卡为准、CookLikeHOC 做交叉核对）；原料来源 145 条；原料加工 149 条；门店制作工艺 125 条；配送周期普遍为「24h 一配 46% 门店 / 48h 一配 54% 门店」。

**页码留痕**：每条记录带 `pdfPage`（PDF 页）+ `reportPage`（报告印刷页码，页脚 `- N -`），满足工单「记录报告版本和页码」。

## 3. 与堂食清洗数据的衔接

- 与 `laoxiangji_dinein.json`（280 条 baseName）按名精确匹配：**143/177**；未匹配 34 条多为版本后缀/空格差异（如「农家蒸蛋（常规版）」），入库阶段按规范化名合并（12 组多版本菜品），合并规则进导入脚本，机器合并不了的人工核对。
- 营养交叉：CookLikeHOC 92 条 vs 溯源卡 144 条，同名菜两源数值以溯源卡为准，差异>2% 的进人工核对清单。

## 4. 授权与红线执行

- 上游无 LICENSE（lxj-trace / CookLikeHOC 均 unknown_no_license）；本提取只产出**结构化字段**（枚举、供应商名、数值、工艺要点），报告原文段落不打包进 APK。
- 全部条目 `reviewStatus = needs_verification`、`licenseStatus = unknown_no_license`。
- 「/」加工等级如实记 UNKNOWN——不做「炸品章节说了现场炸制所以推断全部 RESTAURANT_FRESH」之类的推测（红线 8）。

## 5. 已知解析局限（人工核对重点）

1. 折行导致的字段粘连：PDF 双栏/表格文字层可能错位，抽 10% 逐条对 report.pdf 复核。
2. 原料来源的「原料名/供应商」判别用启发式（含「公司/厂/中心/基地/合作社/集团/有限」视为供应商），个别厂名不含这些词的会误判——已标记 needs_verification。
3. 检测指标表格为扁平文本（inspectionText/sensoryText），结构化入库前需人工按表拆行。
