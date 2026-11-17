#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CookLikeHOC -> 轻记「老乡鸡堂食」清洗 ETL（第二批）。

输入（审计阶段抓下来的快照，不再联网）：
  docs/food-catalog/laoxiangji/cooklikehoc_md_dump.txt      359 个 Markdown 正文合并转储
  docs/food-catalog/laoxiangji/orphan_..._maocai.md         无扩展名孤儿文件（单独补抓，含营养表）

输出（全部落在 docs/food-catalog/laoxiangji/ 下，供人工核对与后续入库）：
  laoxiangji_dinein.json        堂食菜品（dish）完整字段
  laoxiangji_dinein.csv         同上，给表格软件看
  laoxiangji_seasoning.csv      配料/复合调味料（40 项，独立实体，不进普通食物搜索）
  laoxiangji_beverage.csv       饮品（21 项，其中包装商品单独标记）
  laoxiangji_nutrition_review.csv  营养数据逐条核对表（原始文本 + 数值 + 状态）
  laoxiangji_ingredient.csv     菜品->配料明细（含品牌/供应商括号拆出）

严格遵守工单的清洗规则（写死校验，跑不过直接崩，不静默降级）：
1. 营养统一每 100g；原始值与原始单位另存 raw 字段。
2. 不把每份数值当每 100g（本仓库表头全部是「每 100g 含量」，已逐条校验）。
3. 缺营养就是 null，不用 0 填，不用别的菜补。
4. 不按菜名/价格猜热量。
5. 同名不同版本保留独立记录，另建 variantGroup 归组。
6. 出品重量只在正文**明确写了克重**时才取，否则 null。
7. 堂食菜品与外卖商品完全分离：本脚本只产堂食，不产生任何外卖商品。
8. 每一步都留来源：sourceFile / sourceVersion(commit sha) / sourceUrl / licenseStatus / reviewStatus。
"""
import argparse, csv, io, json, os, re
from datetime import datetime, timezone

from pypinyin import lazy_pinyin, Style

SRC_ID = "lxj_cooklikehoc"
BRAND_ID = "laoxiangji"
DATASET_VERSION = "cooklikehoc-4d8e068-2026-11-17"
REPO_SHA = "4d8e06812f501b8369bb9f00804141462f22bf7c"
SOURCE_URL = "https://github.com/Gar-b-age/CookLikeHOC"
# 仓库无 LICENSE，GitHub license: null；README 自述文字摘编自第三方溯源报告。
LICENSE_STATUS = "unknown_no_license"
REVIEW_STATUS = "needs_verification"

# 审计报告里的分类：13 个菜品目录 + 配料 + 饮品 + 文档
DISH_DIRS = ["蒸菜", "炒菜", "早餐", "主食", "炸品", "砂锅菜", "炖菜", "烫菜", "煮锅", "卤菜", "凉拌", "汤", "烤类"]
DOC_DIRS = ["docs", "docker_support"]

EXPECTED = {
    "md_files": 359,
    "content_items": 341,
    "dishes": 280,        # 279 + 1 个无扩展名孤儿
    "seasonings": 40,
    "beverages": 21,
    "docs": 1,
    # 审计报告里 359 个 Markdown 中含营养表的是 91 条；
    # 加上补抓的无扩展名孤儿菜品（自带营养表）后是 92 条。
    "with_nutrition": 92,
}

SEP = "=" * 80 + "\nFILE: "

# 括号里的品牌/供应商，例如「虾滑馄饨（老乡鸡中央厨房）」「紫菜（阿一波）」
PAREN = re.compile(r"[（(]([^（()）]{1,30})[)）]\s*$")
VARIANT = re.compile(r"^(.*?)[（(]([^（()）]{1,20})[)）]\s*$")
NUM_UNIT = re.compile(r"(-?\d+(?:\.\d+)?)\s*([A-Za-z千卡焦克毫克]+)?")


def strip_md_link(text: str) -> str:
    """仓库正文里有 `[葱油](/配料/葱油.md)鸡` 这种写法，标题/配料名要先还原成纯文字。"""
    return re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text).strip()


def slug(text: str) -> str:
    """中文 -> 拼音 slug，只留小写字母数字，用于稳定 ID。"""
    if not text:
        return ""
    full = "".join(lazy_pinyin(text))
    return re.sub(r"[^a-z0-9]+", "", full.lower())


def pinyin_pair(text: str):
    full = "".join(lazy_pinyin(text)).lower()
    abbr = "".join(lazy_pinyin(text, style=Style.FIRST_LETTER)).lower()
    return abbr, full


def split_sections(body: str):
    """把正文按 `## 标题` 切成 {标题: [行]}；没有标题的行归到 ''。"""
    out, cur = {}, ""
    for raw in body.splitlines():
        line = raw.rstrip()
        m = re.match(r"^#{1,3}\s*(.+)$", line)
        if m:
            cur = m.group(1).strip()
            out.setdefault(cur, [])
            continue
        out.setdefault(cur, []).append(line)
    return out


def parse_nutrition(lines):
    """解析营养表。

    返回 (raw_map, basis_ok, note)。
    raw_map 里的值保留原始文本，例如 {'热量': ('59 Kcal', 59.0, 'Kcal'), ...}
    """
    raw, header, basis_ok = {}, "", False
    for line in lines:
        s = line.strip()
        if not s.startswith("|"):
            continue
        cells = [c.strip() for c in s.strip("|").split("|")]
        if len(cells) < 2:
            continue
        if "项目" in cells[0] or "含量" in cells[1]:
            header = cells[1]
            basis_ok = bool(re.search(r"每\s*100\s*g", header, re.I))
            continue
        if set(cells[0]) <= set("-: "):
            continue
        m = NUM_UNIT.search(cells[1].replace(",", ""))
        if not m:
            raw[cells[0]] = (cells[1], None, "")
            continue
        raw[cells[0]] = (cells[1], float(m.group(1)), (m.group(2) or "").strip())
    return raw, basis_ok, header


def pick(raw, *names):
    for n in names:
        for k, v in raw.items():
            if k.replace(" ", "") == n:
                return v
    return None


def parse_ingredients(lines):
    """配料小节 -> [(名称, 供应商/括号内容)]，去掉空行与纯图片行。"""
    out = []
    for line in lines:
        s = line.strip()
        if not s or s.startswith("![") or s.startswith(">"):
            continue
        s = re.sub(r"^[-*+]\s*", "", s)
        if not s:
            continue
        # 葱油这类配料用「、」把多种原料写在一行
        for piece in re.split(r"[、，,]", s) if len(s) < 60 and "、" in s else [s]:
            piece = piece.strip()
            if not piece:
                continue
            name, supplier = strip_md_link(piece), ""
            m = PAREN.search(piece)
            if m:
                supplier = m.group(1).strip()
                name = PAREN.sub("", piece).strip()
            out.append((name, supplier))
    return out


# 只认「明确标注为一份/出品重量」的写法。
# 绝不能用宽泛的「出品…(\d+)g」——那会抓到「出品点缀 1g 葱花」这种 garnishing 用量，
# 把 1g 当成一份菜的重量，属于编造数据。
SERVING_PATTERNS = [
    re.compile(r"(?:标准份量|出品重量|单份重量|净重|每份重量)\s*[:：]?\s*(\d+(?:\.\d+)?)\s*(?:g|克)", re.I),
    re.compile(r"每份\s*[:：]?\s*(\d+(?:\.\d+)?)\s*(?:g|克)", re.I),
]


def parse_serving_weight(text: str):
    """只有在正文明确写了「标准份量/出品重量/每份 xxx g」时才取，否则 None。"""
    for pat in SERVING_PATTERNS:
        m = pat.search(text)
        if m:
            v = float(m.group(1))
            # 低于 20g 基本不可能是成品一份，视为误抓，直接丢弃
            return (v, "explicit_in_steps") if v >= 20 else (None, "not_published")
    return None, "not_published"


def load_snapshot(dump_path, orphan_path):
    text = io.open(dump_path, encoding="utf-8").read()
    chunks = text.split(SEP)
    header, parts = chunks[0], chunks[1:]
    files = {}
    for p in parts:
        path, body = p.split("\n", 1)
        body = body.split("\n" + "=" * 80, 1)[0]
        files[path.strip()] = body

    # 无扩展名孤儿文件：用单文件快照补上，路径沿用仓库里的原路径（补 .md 便于识别）
    if orphan_path and os.path.exists(orphan_path):
        files["煮锅/鸡汤清爽冒菜（含鸡肉牛肉）.md"] = io.open(orphan_path, encoding="utf-8").read()
    return header, files


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dump", required=True)
    ap.add_argument("--orphan", default="")
    ap.add_argument("--out-dir", required=True)
    a = ap.parse_args()

    header, files = load_snapshot(a.dump, a.orphan)
    md_count = int(re.search(r"MARKDOWN_FILE_COUNT=(\d+)", header).group(1))
    assert md_count == EXPECTED["md_files"], "Markdown 总数与审计报告不一致：%s" % md_count
    assert len(files) == EXPECTED["md_files"] + 1, "文件数不对：%s" % len(files)

    now = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")
    dishes, seasonings, beverages = [], [], []
    nutrition_rows, ingredient_rows = [], []
    used_ids, name_pairs = {}, {}

    for path in sorted(files):
        body = files[path]
        top = path.split("/")[0] if "/" in path else "(root)"
        secs = split_sections(body)
        title = ""
        for k in secs:
            if k and not title:
                title = k
        if not title:
            continue
        title = strip_md_link(title)
        # 索引/说明文件：没有正文结构，跳过
        is_index = path.endswith("README.md") or path in ("index.md",) or top in DOC_DIRS
        if is_index:
            continue

        if top == "配料":
            kind = "seasoning"
        elif top == "饮品":
            kind = "beverage"
        elif top in DISH_DIRS:
            kind = "dish"
        else:
            kind = "other"

        ing_lines = secs.get("配料") or secs.get("已知成分") or secs.get("食材") or []
        step_lines = secs.get("步骤") or secs.get("制作步骤") or []
        nut_lines = secs.get("营养成分") or []
        ing = parse_ingredients(ing_lines)
        steps = [re.sub(r"^[-*+]\s*", "", x).strip() for x in step_lines if x.strip() and not x.strip().startswith("![")]
        raw_nut, basis_ok, nut_header = parse_nutrition(nut_lines)

        # ---- 名称与版本变体 ----
        m = VARIANT.match(title)
        base, variant = (m.group(1).strip(), m.group(2).strip()) if m else (title, "")
        base_clean = re.sub(r"\s+", "", base)

        prefix = {"dish": "lxj:dinein:", "seasoning": "lxj:seasoning:", "beverage": "lxj:beverage:"}.get(kind, "lxj:other:")
        fid = prefix + (slug(title) or "item")
        if fid in used_ids:
            used_ids[fid] += 1
            fid = "%s-%d" % (fid, used_ids[fid])
        else:
            used_ids[fid] = 1

        # ---- 营养（严格按每 100g 处理）----
        def per100(*names):
            v = pick(raw_nut, *names)
            if not v:
                return None, ""
            raw_text, num, unit = v
            if num is None:
                return None, raw_text
            if not basis_ok:
                # 表头没写「每 100g」就不认，绝不猜口径
                return None, raw_text
            return num, raw_text

        kcal, kcal_raw = per100("热量", "能量", "能量(千卡)")
        protein, protein_raw = per100("蛋白质")
        fat, fat_raw = per100("脂肪")
        carb, carb_raw = per100("碳水化合物", "碳水")
        sodium, sodium_raw = per100("钠")
        # 单位自检：热量必须 kcal、钠必须 mg，否则不认这个值
        if kcal is not None and pick(raw_nut, "热量", "能量"):
            u = pick(raw_nut, "热量", "能量")[2].lower()
            if u not in ("kcal", "千卡", "大卡"):
                kcal = None
        if sodium is not None and pick(raw_nut, "钠"):
            u = pick(raw_nut, "钠")[2].lower()
            if u not in ("mg", "毫克"):
                sodium = None

        serving, serving_basis = parse_serving_weight(body)

        rec = {
            "foodId": fid,
            "brandId": BRAND_ID,
            "foodName": title,
            "baseName": base_clean,
            "variantName": variant,
            "variantGroup": ("lxj:dinein:" + slug(base_clean)) if kind == "dish" else "",
            "recordType": kind,
            "category": top,
            "aliases": "、".join(sorted({base_clean})) if base_clean != title else "",
            "ingredients": [{"name": n, "supplier": s} for n, s in ing],
            "ingredientCount": len(ing),
            "preparationSteps": steps,
            "stepCount": len(steps),
            "servingWeightG": serving,
            "servingWeightBasis": serving_basis,
            "energyKcalPer100g": kcal,
            "proteinPer100g": protein,
            "fatPer100g": fat,
            "carbohydratePer100g": carb,
            "sodiumMgPer100g": sodium,
            "nutritionBasis": "per_100g" if basis_ok else ("table_without_basis" if raw_nut else "none"),
            "nutritionFieldCount": sum(1 for v in (kcal, protein, fat, carb, sodium) if v is not None),
            "rawNutrition": {k: {"text": v[0], "value": v[1], "unit": v[2]} for k, v in raw_nut.items()},
            "is2026New": "2.0" in body or "溯源报告 2.0" in body,
            "sourceId": SRC_ID,
            "sourceFile": path,
            "sourceVersion": REPO_SHA,
            "sourceUrl": "%s/blob/%s/%s" % (SOURCE_URL, REPO_SHA, path),
            "licenseStatus": LICENSE_STATUS,
            "reviewStatus": REVIEW_STATUS,
            "importedAt": now,
        }
        abbr, full = pinyin_pair(title)
        rec["pinyin"] = abbr
        rec["pinyinFull"] = full

        if kind == "dish":
            dishes.append(rec)
        elif kind == "seasoning":
            seasonings.append(rec)
        elif kind == "beverage":
            beverages.append(rec)

        if nut_lines:
            nutrition_rows.append({
                "foodId": fid, "foodName": title, "category": top, "recordType": kind,
                "tableHeader": nut_header, "basisOk": int(basis_ok),
                "energyRaw": kcal_raw, "energyPer100g": kcal if kcal is not None else "",
                "proteinRaw": protein_raw, "proteinPer100g": protein if protein is not None else "",
                "fatRaw": fat_raw, "fatPer100g": fat if fat is not None else "",
                "carbRaw": carb_raw, "carbPer100g": carb if carb is not None else "",
                "sodiumRaw": sodium_raw, "sodiumPer100g": sodium if sodium is not None else "",
                "fieldCount": rec["nutritionFieldCount"],
                "reviewStatus": REVIEW_STATUS,
                "sourceFile": path,
            })
        for n, s in ing:
            ingredient_rows.append({
                "foodId": fid, "foodName": title, "recordType": kind,
                "ingredientName": n, "supplier": s, "sourceFile": path,
            })

    # ---- 硬校验：数量必须和审计报告一致，否则说明解析退化 ----
    assert len(dishes) == EXPECTED["dishes"], "菜品数 %d != %d" % (len(dishes), EXPECTED["dishes"])
    assert len(seasonings) == EXPECTED["seasonings"], "配料数 %d != %d" % (len(seasonings), EXPECTED["seasonings"])
    assert len(beverages) == EXPECTED["beverages"], "饮品数 %d != %d" % (len(beverages), EXPECTED["beverages"])
    assert len(nutrition_rows) == EXPECTED["with_nutrition"], "营养条目 %d != %d" % (len(nutrition_rows), EXPECTED["with_nutrition"])
    assert sum(1 for d in dishes if d["nutritionFieldCount"] == 5) >= 80, "五项齐全的菜品过少，解析可能有问题"
    assert all(d["servingWeightG"] is None or d["servingWeightG"] > 0 for d in dishes)

    os.makedirs(a.out_dir, exist_ok=True)

    def dump_json(name, data):
        p = os.path.join(a.out_dir, name)
        with io.open(p, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=1)
        return p

    def dump_csv(name, rows, cols):
        p = os.path.join(a.out_dir, name)
        with io.open(p, "w", encoding="utf-8-sig", newline="") as f:
            w = csv.DictWriter(f, fieldnames=cols, extrasaction="ignore")
            w.writeheader()
            for r in rows:
                w.writerow(r)
        return p

    dump_json("laoxiangji_dinein.json", {"meta": {
        "source": "CookLikeHOC", "sourceUrl": SOURCE_URL, "sourceVersion": REPO_SHA,
        "datasetVersion": DATASET_VERSION, "licenseStatus": LICENSE_STATUS,
        "reviewStatus": REVIEW_STATUS, "importedAt": now,
        "counts": {"dish": len(dishes), "seasoning": len(seasonings), "beverage": len(beverages),
                   "withNutrition": len(nutrition_rows)},
        "notice": "仓库无 LICENSE，文字摘编自第三方溯源报告；本文件仅作内部参考，不随 APK 离线分发。",
    }, "dishes": dishes, "seasonings": seasonings, "beverages": beverages})

    dish_cols = ["foodId", "brandId", "foodName", "baseName", "variantName", "variantGroup", "category",
                 "aliases", "pinyin", "pinyinFull", "ingredientCount", "stepCount", "servingWeightG",
                 "servingWeightBasis", "energyKcalPer100g", "proteinPer100g", "fatPer100g",
                 "carbohydratePer100g", "sodiumMgPer100g", "nutritionBasis", "nutritionFieldCount",
                 "is2026New", "reviewStatus", "licenseStatus", "sourceFile"]
    dump_csv("laoxiangji_dinein.csv", dishes, dish_cols)
    dump_csv("laoxiangji_seasoning.csv", seasonings, dish_cols)
    dump_csv("laoxiangji_beverage.csv", beverages, dish_cols)
    dump_csv("laoxiangji_nutrition_review.csv", nutrition_rows,
             ["foodId", "foodName", "category", "recordType", "tableHeader", "basisOk",
              "energyRaw", "energyPer100g", "proteinRaw", "proteinPer100g", "fatRaw", "fatPer100g",
              "carbRaw", "carbPer100g", "sodiumRaw", "sodiumPer100g", "fieldCount", "reviewStatus", "sourceFile"])
    dump_csv("laoxiangji_ingredient.csv", ingredient_rows,
             ["foodId", "foodName", "recordType", "ingredientName", "supplier", "sourceFile"])

    # ---- 统计打印，供报告引用 ----
    groups = {}
    for d in dishes:
        groups.setdefault(d["variantGroup"], []).append(d["foodName"])
    multi = {k: v for k, v in groups.items() if len(v) > 1}
    print("菜品 %d｜配料 %d｜饮品 %d｜含营养 %d" % (len(dishes), len(seasonings), len(beverages), len(nutrition_rows)))
    print("唯一菜品组 %d｜多版本组 %d｜多版本条目 %d" % (len(groups), len(multi), sum(len(v) for v in multi.values())))
    print("有配料明细的菜品 %d｜配料条目 %d" % (sum(1 for d in dishes if d["ingredientCount"] > 0), len(ingredient_rows)))
    print("有步骤的菜品 %d｜有出品克重的菜品 %d" % (sum(1 for d in dishes if d["stepCount"] > 0), sum(1 for d in dishes if d["servingWeightG"])))
    print("五项齐全 %d｜缺营养 %d" % (sum(1 for d in dishes if d["nutritionFieldCount"] == 5), sum(1 for d in dishes if d["nutritionFieldCount"] == 0)))
    with_supplier = sum(1 for r in ingredient_rows if r["supplier"])
    print("配料里带品牌/供应商括号的 %d 条" % with_supplier)
    print("输出目录：%s" % os.path.abspath(a.out_dir))


if __name__ == "__main__":
    main()
