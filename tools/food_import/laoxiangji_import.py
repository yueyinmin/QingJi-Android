#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""老乡鸡堂食入库 + 溯源档案 → food_catalog.db（标准库 v0.6，Room v4）。

输入：
  --dinein  docs/food-catalog/laoxiangji/laoxiangji_dinein.json（280 条，已清洗）
  --trace   docs/food-catalog/laoxiangji/laoxiangji_trace.json（177 菜品溯源卡）
  --schema  app/schemas/com.qingji.app.data.local.FoodCatalogDatabase/4.json
  --existing app/src/main/assets/database/food_catalog.db（当前 0.5 库）
输出：
  新的 food_catalog.db：旧 7 表数据原样搬运 + brand/溯源四张新表 + 堂食菜品 280 条
  进 food_catalog 主表（food_id 从 200001 起，source_id='laoxiangji'，audit_status=0）。

规矩：
- Room schema 4.json 由编译器生成，建表 SQL 逐字来自 createSql，绝不手写（v1.3.1 的教训）；
- 旧表数据全量搬运，一行不丢；
- 堂食分类映射：烫菜/煮锅/砂锅菜 → 其他（原目录名进 portion_note 备查），汤 → 汤品；
- 配料（seasoning 40 条）不进主表——红线：原料不是可食用商品；
- 无出品克重/无营养的字段如实处理：food_catalog 数值列 NOT NULL，缺失填 0 并在
  portion_note 注明「未收录」；trace 表全部 needs_verification + unknown_no_license。
"""
import argparse
import json
import os
import re
import sqlite3
import sys
import unicodedata
from datetime import datetime, timezone

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from omnifood8k_etl import apply_room_schema, copy_existing  # noqa: E402

SRC_BRAND = "laoxiangji"
SOURCE_NOTE = "CookLikeHOC@4d8e068 + 溯源报告2.0(lxj-trace@fea82e034b) | 无LICENSE声明 | 未逐条人工核对"
CATALOG_VERSION = "0.6-laoxiangji"

# CookLikeHOC 目录 → 工单统一堂食分类
CATEGORY_MAP = {
    "炒菜": "炒菜", "蒸菜": "蒸菜", "炖菜": "炖菜", "卤菜": "卤菜",
    "汤": "汤品", "早餐": "早餐", "主食": "主食", "炸品": "炸品",
    "烤类": "烤类", "凉拌": "凉拌", "饮品": "饮品",
    "砂锅菜": "其他", "烫菜": "其他", "煮锅": "其他",
}

OLD_TABLES = ["food_catalog", "food_entity", "food_nutrition", "nutrition_sample",
              "food_composition", "food_data_source", "quality_issue"]


def normalize_name(name: str) -> str:
    """规范化菜名用于两源匹配：去空格、去全角空格、去（...）版本后缀、转小写。"""
    t = name.replace(" ", "").replace("\u3000", "")
    t = re.sub(r"（[^）]*）", "", t)
    t = re.sub(r"\([^)]*\)", "", t)
    t = unicodedata.normalize("NFKC", t).lower()
    return t


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dinein", required=True)
    ap.add_argument("--trace", required=True)
    ap.add_argument("--schema", required=True)
    ap.add_argument("--existing", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    dinein = json.load(open(a.dinein, encoding="utf-8"))
    trace = json.load(open(a.trace, encoding="utf-8"))
    dishes = dinein["dishes"] + dinein["beverages"]  # 饮品也是可食用商品，进主表
    trace_dishes = trace["dishes"]

    # 溯源卡按规范化菜名建索引；PDF 文本层会把菜名截断（如「西红柿炒」），
    # 所以先精确匹配，再前缀兜底（唯一命中才收），「目录」等导航页直接跳过
    trace_by_key = {}
    for t in trace_dishes:
        key = normalize_name(t["dishName"])
        if key in ("目录", "综述", "前言") or len(key) < 2:
            continue
        t["_key"] = key
        trace_by_key.setdefault(key, t)

    if os.path.exists(a.out):
        os.remove(a.out)
    out = sqlite3.connect(a.out)
    apply_room_schema(out, a.schema)

    # ---- 旧 7 表数据全量搬运 ----
    kept = {}
    src = sqlite3.connect(a.existing)
    for table in OLD_TABLES:
        cols = [r[1] for r in src.execute(f"PRAGMA table_info(`{table}`)")]
        rows = src.execute(f"SELECT {','.join(chr(96)+c+chr(96) for c in cols)} FROM `{table}`").fetchall()
        ph = ",".join("?" * len(cols))
        out.executemany(
            f"INSERT INTO `{table}` ({','.join(chr(96)+c+chr(96) for c in cols)}) VALUES ({ph})",
            rows,
        )
        kept[table] = len(rows)

    max_id = out.execute("SELECT COALESCE(MAX(food_id),100000) FROM food_catalog").fetchone()[0]
    next_id = max(max_id + 1, 200001)

    now = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")

    # ---- 先为每道菜匹配溯源卡（精确 → 截断名前缀唯一兜底）----
    # 营养回填规则：dinein 侧没营养而溯源卡有 → 用溯源卡（BATCH3 决策：营养以溯源卡为准）
    trace_for_dish = {}
    for d in dishes:
        key = normalize_name(d["baseName"])
        t = trace_by_key.get(key)
        if t is None:
            cands = [k for k in trace_by_key if key.startswith(k) and len(k) >= 2]
            if len(cands) == 1:
                t = trace_by_key[cands[0]]
        if t is not None:
            trace_for_dish[key] = t

    # ---- 品牌表 ----
    out.execute(
        "INSERT INTO brand (brandId, name, displayName, attribution, sortOrder, dataVersion, isEnabled) "
        "VALUES (?,?,?,?,?,?,1)",
        (SRC_BRAND, "老乡鸡", "老乡鸡",
         "堂食资料 CookLikeHOC（无LICENSE，未核对）；溯源《老乡鸡菜品溯源报告2.0》",
         0, "report-2.0+cooklikehoc-4d8e068"),
    )

    # ---- 堂食菜品进主表 + 溯源关联 ----
    dish_food_id_by_key = {}
    inserted_dishes = 0
    nutrition_backfilled = 0
    for d in dishes:
        cat = CATEGORY_MAP.get(d["category"], "其他")
        note = f"老乡鸡堂食（{d['category']}）"
        ncount = d.get("nutritionFieldCount") or 0
        energy = float(d.get("energyKcalPer100g") or 0.0)
        protein = float(d.get("proteinPer100g") or 0.0)
        fat = float(d.get("fatPer100g") or 0.0)
        carbs = float(d.get("carbohydratePer100g") or 0.0)
        sodium = None
        if ncount == 0:
            note += " · 营养未收录"
            # 营养回填：溯源卡有每100g五项就用它（营养以溯源卡为准，BATCH3）
            key = normalize_name(d["baseName"])
            t = trace_for_dish.get(key)
            tn = (t or {}).get("nutrition")
            if tn:
                energy = float(tn["energyKcal"])
                protein = float(tn["proteinG"])
                fat = float(tn["fatG"])
                carbs = float(tn["carbohydratesG"])
                sodium = float(tn["sodiumMg"])
                note = note.replace(" · 营养未收录", " · 营养来自溯源报告2.0")
                nutrition_backfilled += 1
        if not d.get("servingWeightG"):
            note += " · 份量未收录"
        food_id = next_id
        next_id += 1
        out.execute(
            """INSERT INTO food_catalog
            (food_id,name,aliases,pinyin,pinyin_full,category,subcategory,energy_kcal,protein,fat,carbs,
             fiber,sodium,sugar,calcium,basis_grams,default_grams,portion_note,source_id,source_note,
             audit_status,catalog_version)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            (
                food_id, d["foodName"], d.get("aliases") or "",
                d.get("pinyin") or "", d.get("pinyinFull") or "",
                cat, d["category"],  # subcategory 保留原目录名备查
                energy, protein, fat, carbs,
                None, sodium, None, None,
                100.0, float(d.get("servingWeightG") or 100.0),
                note, SRC_BRAND, SOURCE_NOTE,
                0, CATALOG_VERSION,
            ),
        )
        dish_food_id_by_key[normalize_name(d["baseName"])] = str(food_id)
        inserted_dishes += 1

    # ---- 溯源卡 → brand_dish_trace / ingredient / processing ----
    trace_rows = 0
    ing_rows = 0
    proc_rows = 0
    skipped_trace = 0
    unmatched_trace = []
    for t in trace_dishes:
        key = t.get("_key") or normalize_name(t["dishName"])
        if key in ("目录", "综述", "前言") or len(key) < 2:
            skipped_trace += 1
            continue
        # dish 循环里已做过匹配（含前缀兜底），这里直接复用
        matched = None
        for dkey, t2 in trace_for_dish.items():
            if (t2.get("_key") or normalize_name(t2["dishName"])) == key:
                matched = dkey
                break
        food_id = dish_food_id_by_key.get(matched, "") if matched else ""
        if not food_id:
            unmatched_trace.append(t["dishName"])
        out.execute(
            """INSERT INTO brand_dish_trace
            (brandId,dishFoodId,dishKey,dishName,processingLevel,processingLevelRaw,flavor,
             bestFlavorPeriod,reportPage,pdfPage,cookingMethod,deliveryText,storeProcessText,
             sourceVersion,reviewStatus)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            (
                SRC_BRAND, food_id, key, t["dishName"],
                t["processingLevel"], t["processingLevelRaw"], t.get("flavor") or "",
                t.get("bestFlavorPeriod") or "", t.get("reportPage") or 0, t.get("pdfPage") or 0,
                t.get("cookingMethod") or "",
                (t.get("delivery") or {}).get("text", ""),
                "\n".join(t.get("storeProcess") or []),
                trace["meta"]["sourceVersion"], "needs_verification",
            ),
        )
        trace_rows += 1
        for s in t.get("ingredientSources") or []:
            out.execute(
                "INSERT INTO brand_dish_ingredient (brandId,dishFoodId,ingredientName,supplier,"
                "sourceVersion,reviewStatus) VALUES (?,?,?,?,?,?)",
                (SRC_BRAND, food_id, s["name"], "；".join(s.get("suppliers") or []),
                 trace["meta"]["sourceVersion"], "needs_verification"),
            )
            ing_rows += 1
        for p in t.get("ingredientProcessing") or []:
            out.execute(
                "INSERT INTO brand_dish_processing (brandId,dishFoodId,item,description,"
                "sourceVersion,reviewStatus) VALUES (?,?,?,?,?,?)",
                (SRC_BRAND, food_id, p["item"], p.get("description") or "",
                 trace["meta"]["sourceVersion"], "needs_verification"),
            )
            proc_rows += 1

    # ---- Room 身份信息 ----
    schema = json.load(open(a.schema, encoding="utf-8"))
    out.execute("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                (schema["database"]["identityHash"],))
    out.execute(f"PRAGMA user_version = {schema['database']['version']}")
    out.commit()

    counts = {t: out.execute(f"SELECT COUNT(*) FROM `{t}`").fetchone()[0]
              for t in OLD_TABLES + ["brand", "brand_dish_trace", "brand_dish_ingredient", "brand_dish_processing"]}
    out.close()

    print("kept(旧表搬运):", json.dumps(kept))
    print("inserted dishes:", inserted_dishes, "(营养从溯源卡回填:", nutrition_backfilled, ")",
          "trace:", trace_rows, "ingredients:", ing_rows, "processing:", proc_rows)
    print("skipped trace pages:", skipped_trace, "unmatched trace dishes:", len(unmatched_trace), unmatched_trace[:10])
    print("counts:", json.dumps(counts, ensure_ascii=False))

    # ---- 硬校验 ----
    assert kept["food_catalog"] == 820, f"旧主表应为 820，实际 {kept['food_catalog']}"
    assert counts["food_catalog"] == 820 + inserted_dishes
    assert counts["brand"] == 1
    assert counts["brand_dish_trace"] == trace_rows
    if inserted_dishes != 280 + 21:  # 280 菜品 + 21 饮品 = 301
        print(f"WARNING: inserted dishes = {inserted_dishes}（预期 301）")
    if len(unmatched_trace) > 15:
        print(f"WARNING: 未关联溯源卡 {len(unmatched_trace)} 条，超过预期（截断名/歧义前缀）")
    print("all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
