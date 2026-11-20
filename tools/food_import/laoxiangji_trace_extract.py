#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""《老乡鸡菜品溯源报告 2.0》→ 溯源数据 JSON 提取器（健康工单第四阶段前置）。

输入：lxj-trace 仓库 `references/report-text.md`（report.pdf 全文提取，
      HEAD fea82e034b，1.12MB，830 个 PDF 页）。
输出：docs/food-catalog/laoxiangji/laoxiangji_trace.json
      —— 每道菜一条溯源档案（加工等级四枚举 / 原料来源 / 原料加工 /
         配送周期 / 烹饪方式 / 门店制作工艺 / 每100g营养 / 报告页码）。

结构事实（解析依据，人工核对过多个菜品页）：
- 每个菜品独占一个 PDF 页，以「基本信息」行开头，随后一行是菜名；
- 字段平铺为「标签行 → 值行」：味型 / 最佳风味期 / 加工等级 / 配料 /
  营养成分(/100g 五项) / 原料来源 / 原料加工 / 原料配送 / 餐厅+操作工艺 /
  烹饪方式 / 制作工艺；
- 「原料来源」段是交替行：原料名一行、供应商一行（可多家）；
- PDF 页脚有印刷页码「- N -」，报告页码 = 印刷页码（≠ PDF 页码，封面偏移 2 页）；
- OCR 空格噪声：小数可能出现「3. 3」，需清洗。

授权与红线：
- 上游 lxj-trace 与 CookLikeHOC 均 unknown_no_license；官方报告 2.0 版权归老乡鸡。
  本提取只做结构化字段（等级/供应商名/数值/工艺要点），不把报告原文大段打包进 APK。
- 全部条目 reviewStatus = needs_verification，绝不标 approved（机器解析 ≠ 人工核对）。
"""
import json
import re
import sys
from datetime import datetime, timezone

# ---- 工单 5.6 加工等级四枚举（不得简化成布尔） ----
LEVEL_MAP = [
    ("餐厅现做", "RESTAURANT_FRESH"),
    ("半预制", "SEMI_PREPARED"),
    ("复热预制", "REHEAT_PREPARED"),
]
LEVEL_UNKNOWN = "UNKNOWN"

SUPPLIER_HINTS = ("公司", "厂", "中心", "基地", "合作社", "集团", "有限", "商行", "市场")

NUM_CLEAN_RE = re.compile(r"[\s,]")


def clean_num(token: str):
    """「3. 3」→ 3.3；「246」→ 246.0；解析失败返回 None。"""
    t = NUM_CLEAN_RE.sub("", token).rstrip(".")
    if not t:
        return None
    try:
        return float(t)
    except ValueError:
        return None


def split_pages(text: str):
    """按 `## Page N` 切页。返回 [(pdf_page:int, [lines])]，页内含 page:N 注释之前的页脚行。"""
    pages = []
    cur_num = None
    cur_lines = []
    for line in text.splitlines():
        m = re.match(r"^## Page (\d+)\s*$", line)
        if m:
            if cur_num is not None:
                pages.append((cur_num, cur_lines))
            cur_num = int(m.group(1))
            cur_lines = []
        elif cur_num is not None and not line.startswith("<!-- page:"):
            cur_lines.append(line.rstrip())
    if cur_num is not None:
        pages.append((cur_num, cur_lines))
    return pages


def parse_dish_page(pdf_page: int, lines):
    """解析一个菜品溯源卡。返回 dict 或 None（非菜品页：封面/目录/综述/检测报告汇总等）。"""
    # 找「基本信息」标签，下一行是名称
    name_idx = None
    for i, ln in enumerate(lines):
        if ln.strip() == "基本信息":
            name_idx = i
            break
    if name_idx is None:
        return None
    rest = [ln.strip() for ln in lines[name_idx + 1:]]
    rest = [ln for ln in rest if ln]  # 去空行
    if not rest:
        return None

    # ---- 页型判定：原料/物料溯源卡 vs 菜品溯源卡 vs 供应商资料卡 ----
    # 原料卡字段：类别/规格/产地/物料编码/检测项目；菜品卡字段：味型/加工等级/营养成分；
    # 供应商卡：rest[0] ==「产品名称」（模板标签），后面才是真名 + 产品分类/供应商信息
    lookahead = set(rest[:12])
    if rest[0] == "产品名称" and len(rest) > 1:
        return parse_supplier_page(pdf_page, lines, rest)
    if "物料编码" in lookahead or ("类别" in lookahead and "产地" in lookahead):
        return parse_material_page(pdf_page, lines, name_idx, rest)
    return parse_dish_card(pdf_page, lines, rest)


def parse_supplier_page(pdf_page, lines, rest):
    """原料/供应商资料卡：产品名称/产品分类/产地/感官与检验指标/供应商名单。
    输出 BrandIngredient（含 BrandIngredientSupplier → BrandSupplier）的结构化素材。"""
    item = {
        "pageType": "supplier_card",
        "itemName": rest[1] if len(rest) > 1 else "",
        "pdfPage": pdf_page,
        "reportPage": None,
        "productCategory": "", "origin": "", "purpose": "",
        "gmo": "", "preservative": "",
        "sensoryText": "", "inspectionText": "", "qualificationText": "",
        "storageText": "", "transportText": "",
        "suppliers": [],              # 供应商公司名单（BrandSupplier 素材）
        "reviewStatus": "needs_verification",
    }
    for ln in lines:
        m = re.match(r"^\s*-\s*(\d+)\s*-\s*$", ln)
        if m:
            item["reportPage"] = int(m.group(1))
            break

    # rest 从「产品名称」开始，真名在 rest[1]
    i = 2
    n = len(rest)
    while i < n:
        ln = rest[i]
        if ln == "产品分类":
            item["productCategory"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "产地":
            item["origin"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "用途":
            item["purpose"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "是否转基因":
            v = rest[i + 1] if i + 1 < n else ""
            item["gmo"] = v if v not in ("是否添加防腐剂",) else ""
            i += 2
        elif ln == "是否添加防腐剂":
            item["preservative"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "感官指标":
            i += 1
            buf = []
            while i < n and rest[i] not in ("检验指标", "资质证件", "储运方式", "供应商信", "供应商信息"):
                buf.append(rest[i])
                i += 1
            item["sensoryText"] = "；".join(buf)
        elif ln == "检验指标":
            i += 1
            buf = []
            while i < n and rest[i] not in ("资质证件", "储运方式", "供应商信", "供应商信息"):
                buf.append(rest[i])
                i += 1
            item["inspectionText"] = "；".join(buf)
        elif ln == "资质证件":
            i += 1
            buf = []
            while i < n and rest[i] not in ("储运方式", "供应商信", "供应商信息"):
                buf.append(rest[i])
                i += 1
            item["qualificationText"] = "；".join(buf)
        elif ln == "储运方式":
            i += 1
            while i < n and rest[i] not in ("供应商信", "供应商信息"):
                if rest[i] == "存储要求" and i + 1 < n:
                    item["storageText"] = rest[i + 1]
                    i += 2
                elif rest[i] == "运输要求" and i + 1 < n:
                    item["transportText"] = rest[i + 1]
                    i += 2
                else:
                    i += 1
        elif ln == "供应商信" or ln == "供应商信息":
            # 「供应商信/息」可能被拆行；后面是 表头(名称/联系人/联系电话) + 公司名/联系人/电话 交替
            i += 1
            if i < n and rest[i] == "息":
                i += 1
            while i < n and rest[i] in ("名称", "联系人", "联系电话"):
                i += 1
            while i < n:
                t = rest[i]
                if any(h in t for h in SUPPLIER_HINTS):
                    item["suppliers"].append(t)
                i += 1
        else:
            i += 1
    return item


def parse_material_page(pdf_page, lines, name_idx, rest):
    """原料/物料溯源卡（外采原料 + 中央厨房产品）：产地/规格/供应商/检测指标。
    输出 BrandIngredient + BrandInspectionReport 的原始结构化素材。"""
    item = {
        "pageType": "material",
        "itemName": rest[0],
        "pdfPage": pdf_page,
        "reportPage": None,
        "category": "", "spec": "", "origin": "", "shelfLife": "",
        "storage": "", "standard": "", "materialCode": "", "useRegion": "",
        "purpose": "", "allergen": "", "preservative": "",
        "ingredientSources": [],      # 原料来源（供应商）
        "processText": "",            # 工艺流程
        "deliveryText": "",           # 配送要求
        "inspectionText": "",         # 检测项目+检测指标 原文块（表格结构留人工审核）
        "reviewStatus": "needs_verification",
    }
    for ln in lines:
        m = re.match(r"^\s*-\s*(\d+)\s*-\s*$", ln)
        if m:
            item["reportPage"] = int(m.group(1))
            break

    i = 1
    n = len(rest)
    while i < n:
        ln = rest[i]
        if ln == "类别":
            item["category"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "规格":
            item["spec"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "产地":
            item["origin"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "保质期":
            item["shelfLife"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "储存方式":
            item["storage"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "执行标准":
            item["standard"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "物料编码":
            item["materialCode"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "使用区域":
            item["useRegion"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "用途":
            item["purpose"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "过敏原":
            item["allergen"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "添加防腐剂":
            item["preservative"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "配料":
            i += 1  # 原料卡的配料多为「/」，不做拆分
        elif ln == "原料" or ln == "来源" or (ln == "原料来源"):
            # 「原料」+「来源」可能拆两行；后面是供应商名单
            if ln == "原料" and i + 1 < n and rest[i + 1] == "来源":
                i += 2
            else:
                i += 1
            while i < n and rest[i] in ("原料", "来源"):
                i += 1
            while i < n and rest[i] not in ("工艺", "流程", "配送要求", "检测项目", "检测指标"):
                t = rest[i]
                if any(h in t for h in SUPPLIER_HINTS):
                    item["ingredientSources"].append({"name": "", "suppliers": [t]})
                i += 1
        elif ln == "工艺" or ln == "流程":
            i += 1
            buf = []
            while i < n and rest[i] not in ("配送要求", "检测项目", "检测指标", "原料", "来源"):
                buf.append(rest[i])
                i += 1
            item["processText"] = "；".join(buf)
        elif ln == "配送要求":
            i += 1
            buf = []
            while i < n and rest[i] not in ("检测项目", "检测指标"):
                buf.append(rest[i])
                i += 1
            item["deliveryText"] = "；".join(buf)
        elif ln in ("检测项目", "检测指标"):
            i += 1
            buf = []
            while i < n:
                t = rest[i]
                if t in ("检测指标",) and ln == "检测项目":
                    break
                buf.append(t)
                i += 1
            item["inspectionText"] = (item["inspectionText"] + "\n" if item["inspectionText"] else "") + "；".join(buf)
        else:
            i += 1
    return item


def parse_dish_card(pdf_page, lines, rest):
    dish = {
        "dishName": rest[0],
        "pdfPage": pdf_page,
        "reportPage": None,
        "flavor": "",
        "bestFlavorPeriod": "",
        "processingLevelRaw": "",
        "processingLevel": LEVEL_UNKNOWN,
        "ingredientsText": "",
        "ingredientList": [],
        "nutrition": None,
        "ingredientSources": [],
        "ingredientProcessing": [],
        "delivery": None,
        "cookingMethod": "",
        "storeProcess": [],
        "reviewStatus": "needs_verification",
    }

    # 报告印刷页码：页内「- N -」页脚
    for ln in lines:
        m = re.match(r"^\s*-\s*(\d+)\s*-\s*$", ln)
        if m:
            dish["reportPage"] = int(m.group(1))
            break

    # ---- 顺序扫描标签 ----
    i = 1  # rest[0] 是菜名，从下一行开始
    n = len(rest)
    section = None
    while i < n:
        ln = rest[i]
        if ln == "味型":
            dish["flavor"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "最佳风味期":
            dish["bestFlavorPeriod"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "加工等级":
            raw = rest[i + 1] if i + 1 < n else ""
            dish["processingLevelRaw"] = raw
            if raw == "/":
                # 报告用「/」表示该章品类无逐菜标注（如炸品：餐厅现场炸制），
                # 红线：不推测，如实记 UNKNOWN 并保留原始值供 UI 展示
                dish["processingLevel"] = LEVEL_UNKNOWN
            else:
                for kw, enum in LEVEL_MAP:
                    if kw in raw:
                        dish["processingLevel"] = enum
                        break
            i += 2
        elif ln == "配料":
            # 配料可能折行：收集到下一个已知标签为止
            j = i + 1
            buf = []
            while j < n and rest[j] not in ("营养成分", "原料来源", "原料加工", "原料配送"):
                buf.append(rest[j])
                j += 1
            dish["ingredientsText"] = "".join(buf)
            # 顶层拆分：按顿号切，括号里的顿号不算——先拆括号再合并
            parts = re.split(r"（[^）]*）", dish["ingredientsText"])  # 去括号后按顿号切
            flat = []
            for chunk in parts:
                flat += [p for p in chunk.split("、") if p.strip()]
            # 恢复带括号的主料名：粗粒度即可，审核阶段再人工核对
            dish["ingredientList"] = [p.strip() for p in flat if p.strip()]
            i = j
        elif ln in ("营养成分", "营养成分/100g") or ln.startswith("营养成分"):
            # 表头行 + 5 个数值行（数值可能带空格噪声）
            j = i + 1
            nums = []
            while j < n and len(nums) < 5:
                t = rest[j]
                # 跳过表头行
                if ("热量" in t or "蛋白质" in t or "脂肪" in t or "碳水" in t or t == "/100g"):
                    j += 1
                    continue
                v = clean_num(t)
                if v is not None:
                    nums.append(v)
                j += 1
            if len(nums) == 5:
                dish["nutrition"] = {
                    "basis": "per_100g",
                    "energyKcal": nums[0],
                    "proteinG": nums[1],
                    "fatG": nums[2],
                    "carbohydratesG": nums[3],
                    "sodiumMg": nums[4],
                }
            i = j
        elif ln == "原料来源":
            j = i + 1
            cur_ing, cur_sup = None, []
            while j < n and rest[j] not in ("原料加工", "原料配送", "餐厅", "营养成分"):
                t = rest[j]
                is_supplier = any(h in t for h in SUPPLIER_HINTS)
                if is_supplier and cur_ing is not None:
                    cur_sup.append(t)
                elif is_supplier and cur_ing is None:
                    # 没有原料名直接来供应商行（异常），记进当前组兜底
                    cur_ing, cur_sup = "(未识别原料)", [t]
                else:
                    if cur_ing is not None:
                        dish["ingredientSources"].append(
                            {"name": cur_ing, "suppliers": cur_sup})
                    cur_ing, cur_sup = t, []
                j += 1
            if cur_ing is not None:
                dish["ingredientSources"].append({"name": cur_ing, "suppliers": cur_sup})
            i = j
        elif ln == "原料加工":
            j = i + 1
            while j < n and rest[j] not in ("原料配送", "餐厅", "原料来源", "营养成分"):
                item = rest[j]
                j += 1
                # 条目名可能折行（如「香脆鸡腿\n（琵琶腿）」），括号行并入条目名
                while j < n and rest[j].startswith("（"):
                    item += rest[j]
                    j += 1
                desc = rest[j] if j < n else ""
                j += 1
                dish["ingredientProcessing"].append({"item": item, "description": desc})
            i = j
        elif ln == "原料配送":
            j = i + 1
            buf = []
            while j < n and rest[j] not in ("餐厅", "原料来源", "原料加工"):
                buf.append(rest[j])
                j += 1
            if buf:
                dish["delivery"] = {"text": "；".join(buf)}
            i = j
        elif ln == "餐厅":
            # 「餐厅」+「操作工艺」是拆成两行的标签
            if i + 1 < n and rest[i + 1] == "操作工艺":
                i += 2
            else:
                i += 1
        elif ln == "操作工艺":
            i += 1
        elif ln == "烹饪方式":
            dish["cookingMethod"] = rest[i + 1] if i + 1 < n else ""
            i += 2
        elif ln == "制作工艺":
            j = i + 1
            while j < n:
                t = rest[j]
                if t in ("基本信息", "味型", "加工等级", "配料", "营养成分", "原料来源",
                         "原料加工", "原料配送", "烹饪方式", "制作工艺"):
                    break
                dish["storeProcess"].append(t)
                j += 1
            i = j
        else:
            i += 1
    return dish


def main() -> int:
    src = sys.argv[1] if len(sys.argv) > 1 else "report-text.md"
    out = sys.argv[2] if len(sys.argv) > 2 else "laoxiangji_trace.json"
    text = open(src, encoding="utf-8").read()

    dishes = []
    materials = []
    supplier_cards = []
    non_dish_pages = 0
    for pdf_page, lines in split_pages(text):
        d = parse_dish_page(pdf_page, lines)
        if d is None:
            non_dish_pages += 1
        elif d.get("pageType") == "material":
            materials.append(d)
        elif d.get("pageType") == "supplier_card":
            supplier_cards.append(d)
        else:
            dishes.append(d)

    # ---- 统计与校验 ----
    levels = {}
    for d in dishes:
        levels[d["processingLevel"]] = levels.get(d["processingLevel"], 0) + 1
    with_nutrition = sum(1 for d in dishes if d["nutrition"])
    with_sources = sum(1 for d in dishes if d["ingredientSources"])
    with_process = sum(1 for d in dishes if d["ingredientProcessing"])
    with_store = sum(1 for d in dishes if d["storeProcess"])

    doc = {
        "meta": {
            "source": "lxj-trace",
            "sourceUrl": "https://github.com/lxjchina/lxj-trace",
            "sourceVersion": "fea82e034b",
            "sourceFile": "references/report-text.md",
            "reportTitle": "老乡鸡菜品溯源报告 2.0",
            "reportVersion": "2.0",
            "officialUrl": "https://www.lxjchina.com.cn/info.php?class_id=102106",
            "licenseStatus": "unknown_no_license",
            "reviewStatus": "needs_verification",
            "extractionNote": "机器解析 PDF 文本层，字段逐条 needs_verification；数值以 report.pdf 为准；"
                              "加工等级「/」= 报告未逐菜标注（如实记 UNKNOWN），不作推测",
            "importedAt": datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds"),
            "counts": {
                "dishPages": len(dishes),
                "materialPages": len(materials),
                "supplierCards": len(supplier_cards),
                "nonDishPages": non_dish_pages,
                "withNutrition": with_nutrition,
                "withIngredientSources": with_sources,
                "withIngredientProcessing": with_process,
                "withStoreProcess": with_store,
                "processingLevels": levels,
            },
        },
        "dishes": dishes,
        "materials": materials,
        "supplierCards": supplier_cards,
    }
    json.dump(doc, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    print(f"dish pages: {len(dishes)}, material pages: {len(materials)}, supplier cards: {len(supplier_cards)}, non-dish: {non_dish_pages}")
    print(f"levels: {levels}")
    print(f"nutrition: {with_nutrition}, sources: {with_sources}, processing: {with_process}, store: {with_store}")

    # 硬校验：一个菜品页都解析不出来 = 结构判断错了，直接失败
    if not dishes:
        print("FAILED: no dish pages parsed")
        return 1
    # 抽查：梅菜扣肉必须在场且是半预制
    mk = next((d for d in dishes if d["dishName"] == "梅菜扣肉"), None)
    if mk is None:
        print("FAILED: 梅菜扣肉 missing")
        return 1
    assert mk["processingLevel"] == "SEMI_PREPARED", mk["processingLevel"]
    assert mk["nutrition"] and mk["nutrition"]["energyKcal"] == 246.0, mk["nutrition"]
    assert any(s["name"] == "梅干菜" for s in mk["ingredientSources"]), mk["ingredientSources"]
    print("spot check 梅菜扣肉: OK (SEMI_PREPARED, kcal 246, 梅干菜来源在案)")
    # 抽查：原料卡（大葱）必须在场且带供应商与检测块
    cong = next((m for m in materials if m["itemName"] == "大葱"), None)
    if cong is None:
        print("FAILED: 原料卡 大葱 missing")
        return 1
    assert cong["materialCode"], cong
    assert cong["inspectionText"], cong
    print(f"spot check 大葱: OK (物料编码 {cong['materialCode']}, 检测块 {len(cong['inspectionText'])} 字)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
