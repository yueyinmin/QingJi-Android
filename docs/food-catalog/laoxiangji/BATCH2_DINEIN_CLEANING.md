# 老乡鸡堂食数据清洗报告（第二批）

> 工单：轻记 Android｜老乡鸡品牌食物库扩充与外卖独立模块开发工单
> 阶段：第二批「堂食数据清洗」——只做数据，不动 UI，不采集外卖，不写用户库。
> 生成时间：2026-11-17（本地时间，UTC+8）

---

## 1. 输入快照

| 项目 | 内容 |
|---|---|
| 来源仓库 | https://github.com/Gar-b-age/CookLikeHOC |
| 审计时 HEAD | `4d8e06812f501b8369bb9f00804141462f22bf7c`（main 分支） |
| 正文来源 | `docs/food-catalog/laoxiangji/cooklikehoc_md_dump.txt`（359 个 Markdown 的正文合并转储） |
| 孤儿文件 | `docs/food-catalog/laoxiangji/orphan_jitang_qingshuang_maocai.md` |
| 官方资料入口 | https://www.lxjchina.com.cn/info.php?class_id=102106 |
| 授权状态 | `unknown_no_license`（仓库无 LICENSE，GitHub `license: null`，README 自述文字摘编自第三方溯源报告） |
| 清洗状态 | `needs_verification`（全部条目，机器结构化 ≠ 人工核对，绝不写 `approved`） |

孤儿文件说明：仓库里 `煮锅/鸡汤清爽冒菜（含鸡肉牛肉）` 漏写 `.md` 扩展名，不计入 359 个 Markdown、不被索引引用，但正文完整（含营养表）。已按上述 SHA 从仓库单文件补抓，正文原样保存，本报告把它作为第 280 道菜纳入。

---

## 2. 清洗结果（脚本硬校验，跑不过直接报错）

| 类型 | 条数 | 说明 |
|---|---:|---|
| 堂食菜品 dish | 280 | 279 个 Markdown + 1 个无扩展名孤儿 |
| 配料/复合调味料 seasoning | 40 | `配料/` 目录，独立实体，不作为可点菜品 |
| 饮品 beverage | 21 | `饮品/` 目录，其中包装商品（可乐、矿泉水等）属外购零售品 |
| 含营养表 | 92 | 91 个 Markdown + 孤儿 1 条 |
| 五项营养齐全 | 86 | 热量/蛋白质/脂肪/碳水/钠 |
| 完全无营养 | 194 | 保持 null，未用 0 或别的菜填充 |
| 有配料明细的菜品 | 238 | 共拆出 2,267 条配料记录 |
| 配料里带品牌/供应商括号 | 322 | 括号内容拆到 `supplier` 字段，不留进名称 |
| 有制作步骤 | 251 | 见下方授权说明 |
| 唯一菜品组 | 256 | 12 组多版本菜品，涉及 36 条记录 |
| 有标准份量/出品克重 | **0** | 仓库无此字段，保持 null |

### 与审计报告的口径对齐
- 审计按 13 个菜品目录统计为 279 道；加孤儿文件后为 280 道。
- 审计列出 14 组名称变体：本清洗在 dish 中识别 12 组、beverage 中 1 组（原味豆浆）、seasoning 中 1 组（小炒肉调料），合计 14 组，口径一致。
- 审计统计 91 条营养来自 359 个 Markdown；加孤儿后为 92 条。

---

## 3. 字段实现（对照工单 20 个字段）

| 工单字段 | 输出字段 | 处理方式 |
|---|---|---|
| foodId | `foodId` | `lxj:dinein:<拼音slug>` / `lxj:seasoning:…` / `lxj:beverage:…`，重名追加序号，稳定不随机 |
| brandId | `brandId` | 固定 `laoxiangji` |
| foodName | `foodName` | 先还原 `[葱油](/配料/葱油.md)鸡` 这类 Markdown 链接再入库 |
| aliases | `aliases` / `baseName` | 去括号版本后缀的主名作为别名 |
| category | `category` | 采用仓库目录名（蒸菜/炒菜/早餐/主食…），不擅自重分类 |
| ingredients | `ingredients[]` | `laoxiangji_ingredient.csv`，括号品牌拆到 `supplier` |
| preparationSteps | `preparationSteps[]` | 已抽取，**但不随 APK 分发**（授权见第 5 节） |
| servingWeightG | `servingWeightG` | 全部 null；只在正文明确写「标准份量/出品重量/每份 xx g」时才取值，且过滤 <20g 的误抓 |
| energyKcalPer100g 等四项 | `energyKcalPer100g` / `proteinPer100g` / `fatPer100g` / `carbohydratePer100g` | 仅接受表头写明「每 100g」的表；热量单位必须 kcal/千卡，钠必须 mg，否则置 null |
| sodiumMgPer100g | `sodiumMgPer100g` | 同上 |
| sourceId | `sourceId` | `lxj_cooklikehoc` |
| sourceFile | `sourceFile` | 仓库内原始相对路径（含中文） |
| sourceVersion | `sourceVersion` | `4d8e06812f501b8369bb9f00804141462f22bf7c` |
| sourceUrl | `sourceUrl` | `https://github.com/Gar-b-age/CookLikeHOC/blob/<sha>/<path>` |
| licenseStatus | `licenseStatus` | 全部 `unknown_no_license` |
| reviewStatus | `reviewStatus` | 全部 `needs_verification` |

额外补充：`variantGroup` / `variantName`（版本归组）、`rawNutrition`（原始文本 + 数值 + 单位）、`nutritionBasis`、`pinyin` / `pinyinFull`、`is2026New`、`importedAt`。

---

## 4. 红线执行情况

1. **不按菜名/价格猜营养**：脚本没有名称→热量的映射，无任何推测分支；194 条缺营养保持 null。
2. **不把每份当每 100g**：逐条检查表头，只接受「每 100g 含量 / 每100g含量」两种写法；本仓库 92 张表全部符合，无 kJ 单位。
3. **不使用 PR #216 未合并的 18 组数值**：本批完全不引用 PR #216；那 18 组数值仅留在第一批审计报告的「候选值」章节，入库前须人工核对报告 2.0 原文。
4. **不同版本不合并**：12 组多版本菜品全部保留独立记录，只用 `variantGroup` 归组，不覆盖、不去重。
5. **配料与商品分开**：40 项配料进 `lxj:seasoning:*`，不进堂食菜品集；不进入后续普通食物搜索。
6. **堂食与外卖分离**：本批产物中**没有任何外卖商品、价格、规格、套餐字段或记录**。
7. **不动用户库**：本批没有写 `qingji.db`，没有改任何用户表。

### 一个已纠正的坑
第一版脚本用「`出品` 后 8 个字符内出现数字+g」来抓份量，结果抓到「出品点缀 **1g** 葱花」这类摆盘用量，得出 35 条假份量。已改成只认「标准份量 / 出品重量 / 单份重量 / 净重 / 每份重量 / 每份」六类明确写法，并过滤 <20g 的值。修正后出品克重为 **0 条**，与审计报告「标准份量字段 0/341」一致。

---

## 5. 授权处置

- 仓库**无 LICENSE**；README 自述「文字超大段 copy 自《老乡鸡菜品溯源报告》，有编辑与整理」。
- 处置：结构化**事实**（菜名、分类、配料名称、每 100g 营养数值、来源元数据）可作为内部参考数据继续清洗；**制作步骤原文、菜品图片、README/报告原文不随 APK 离线分发**。
- `preparationSteps` 已抽取到工作文件供人工核对，但入库/随包时保持不分发；后续如需展示，须先取得授权或自行改写。
- 所有条目 `licenseStatus = unknown_no_license`，`reviewStatus = needs_verification`。

---

## 6. 交付物

| 文件 | 内容 |
|---|---|
| `laoxiangji_dinein.json` | 280 道堂食菜品完整字段（含 meta 统计与授权声明） |
| `laoxiangji_dinein.csv` | 同上，表格版 |
| `laoxiangji_seasoning.csv` | 40 项配料 |
| `laoxiangji_beverage.csv` | 21 项饮品 |
| `laoxiangji_nutrition_review.csv` | 92 条营养逐条核对（原始文本 + 解析值 + 状态） |
| `laoxiangji_ingredient.csv` | 2,267 条菜品→配料明细（含供应商） |
| `laoxiangji_dinein_clean.py` | 清洗脚本，内置数量与规则硬校验 |

复现命令：

```bash
python tools/food_import/laoxiangji_dinein_clean.py \
  --dump docs/food-catalog/laoxiangji/cooklikehoc_md_dump.txt \
  --orphan docs/food-catalog/laoxiangji/orphan_jitang_qingshuang_maocai.md \
  --out-dir docs/food-catalog/laoxiangji
```

---

## 7. 未做/待做（不属于本批）

- 数据尚未写入 `food_catalog.db`（Room Entity / DAO / Migration 属后续批次）。
- 外卖商品、套餐、规格、价格、平台、门店范围：仓库内检索命中 0，**没有采集到任何真实外卖数据**，后续不得臆造。
- UI、饮食记录来源类型、搜索接入：未做。
- 人工逐条核对营养数值：未做；当前全部 `needs_verification`。
