#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""六大快餐品牌（肯德基/麦当劳/华莱士/塔斯汀/必胜客/达美乐）→ food_catalog.db。

输入：workspace 快餐食物库/raw/*.fixed.csv（160 条，v0.3.1）
输出：新的 food_catalog.db：19 表数据原样搬运 + brand 追加 6 行 +
      food_catalog 主表追加 160 条（source_id = brandId，food_id 从 210001 起）。

规矩（沿用 laoxiangji_import.py）：
- 不手写建表 SQL，全部表结构原库搬运（Room v5 identityHash 不变，纯数据追加）；
- food_catalog 营养列按「每 100g」存（CatalogFood 注释规定 basis_grams 恒 100）：
  原始数据是每份值 → 用真实克重换算成每 100g，default_grams = 每份克重；
  没有真实克重的行按品类给估算份重，portion_note 标「份量约Xg为估算值」；
- fiber/sugar/calcium 查不到就 NULL，绝不填 0 充数；
- 外卖 8 张 takeaway_* 表保持全空（红线 15）。
"""
import csv
import json
import os
import re
import shutil
import sqlite3
import sys
from pathlib import Path

REPO = Path(r"C:\qingji-git\QingJi-Android")
WS = Path(r"C:\Users\22109\AppData\Roaming\AutoClaw-official\accounts\a3c8efd3605712137e37e0aa4b55e3a1f3f3aa3e9a4d45f9cbfef9979c882d23\projects\Default\快餐食物库")
DB_IN = REPO / "app/src/main/assets/database/food_catalog.db"
DB_OUT = REPO / "app/src/main/assets/database/food_catalog.db.new"

BRANDS = [
    # (brandId, name, attribution, dataVersion, csv)
    ("kfc", "肯德基", "营养数据 fatsecret.cn 品牌条目（官网无营养计算器）| 未逐条人工核对", "fastfood-csv-0.3.1", "肯德基.fixed.csv"),
    ("mcdonalds", "麦当劳", "营养数据 麦当劳中国官网营养计算器（2025年4月版，kJ→kcal 换算）| 商品图官网 | 未逐条人工核对", "fastfood-csv-0.3.1", "麦当劳.fixed.csv"),
    ("hls", "华莱士", "热量 百度健康外卖称重测评；官网 cnhls.com 商品图 | 未逐条人工核对", "fastfood-csv-0.3.1", "华莱士.fixed.csv"),
    ("tastien", "塔斯汀", "营养 fatsecret.cn 品牌条目 + 百度健康/小红书称重测评；官网 tasiting.com 商品图 | 未逐条人工核对", "fastfood-csv-0.3.1", "塔斯汀.fixed.csv"),
    ("pizzahut", "必胜客", "营养 fatsecret.cn 品牌条目 + 薄荷健康 | 未逐条人工核对", "fastfood-csv-0.3.1", "必胜客.fixed.csv"),
    ("dominos", "达美乐", "营养 达美乐台湾官网营养标示（大陆配方可能略有差异）| 未逐条人工核对", "fastfood-csv-0.3.1", "达美乐.fixed.csv"),
]

# 0.9：老乡鸡 0 千卡整理后统一版本。重建完本脚本后须再跑
# patch_lxj_zero_kcal.py，否则老乡鸡热量回填会丢。
CATALOG_VERSION = "0.9-lxj-kcal-match"

# 估算份重（克）：仅用于原始数据没有真实克重的行，portion_note 会标注估算
def estimate_grams(name: str, portion: str) -> int:
    n, p = name, portion
    if any(k in n for k in ("可乐", "七喜")):
        if "迷你" in p or "迷你" in n: return 250
        if "小" in p: return 350
        if "中" in p or "中杯" in n: return 500
        if "大" in p: return 650
        return 500
    if "柠檬水" in n: return 500
    if "咖啡" in n: return 300
    if "风味奶" in n or "黑米浆" in n or "乌梅" in n or "柠檬红茶" in n: return 500
    if "薯条" in n or "粗薯" in n:
        if "迷你" in p: return 60
        if "小" in p: return 80
        if "大" in p: return 135
        return 110
    if "玉米杯" in n:
        return 120 if "小" in p else 220
    if "苹果片" in n: return 60
    if "麦旋风" in n: return 200
    if "新地" in n: return 180
    if "圆筒" in n or "甜筒" in n: return 90
    if "派" in n: return 90
    if "蛋挞" in n: return 60
    if "土豆泥" in n: return 45
    if "圣代" in n: return 180
    if "冰淇淋" in n: return 100
    if "提拉米苏" in n: return 174
    if "汉堡" in n or "堡" in n or "霸" in n: return 200
    if "鸡肉卷" in n or "卷" in n and "鸡" in n: return 180
    if "麦香鱼" in n: return 200
    if "麦香鸡" in n: return 200
    if "全鸡" in n or "手扒鸡" in n or "半边鸡" in n: return 800 if "全鸡" in n else 700
    if "半只" in p: return 700
    if "鸡腿" in n: return 150
    if "鸡翅" in n or "烤翅" in n: return 60 if "1个" in p else 100
    if "翅根" in n: return 100
    if "全翅" in n: return 100
    if "翅尖" in n or "鸡脖" in n: return 50
    if "鸡块" in n or "鸡米花" in n or "盐酥鸡" in n: return 100
    if "香酥鸡" in n: return 200
    if "鸡排" in n: return 150
    if "沙拉" in n or "色拉" in n: return 150
    if "披萨" in n or "比萨" in n: return 107  # 1片
    if "意面" in n or "肉酱面" in n: return 300
    if "烤翅" in n: return 100
    return 100

def to_f(v):
    try:
        s = str(v).strip()
        return float(s) if s and s.lower() not in ("none", "nan") else None
    except ValueError:
        return None

def main():
    # 稳妥搬运：直接复制 db 文件再打开追加（不手写建表 SQL）
    if DB_OUT.exists():
        DB_OUT.unlink()
    shutil.copyfile(DB_IN, DB_OUT)
    out = sqlite3.connect(DB_OUT)

    tables = [r[0] for r in out.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' AND name != 'room_master_table'")]

    max_id = out.execute("SELECT COALESCE(MAX(food_id),100000) FROM food_catalog").fetchone()[0]
    next_id = max(max_id + 1, 210001)
    print("max food_id =", max_id, "→ fastfood 从", next_id)

    old_food = out.execute("SELECT COUNT(*) FROM food_catalog").fetchone()[0]
    old_brand = out.execute("SELECT COUNT(*) FROM brand").fetchone()[0]

    inserted = 0
    per_brand = {}
    for sort, (bid, bname, attr, dver, csvname) in enumerate(BRANDS, start=1):
        path = WS / "raw" / csvname
        rows = list(csv.DictReader(open(path, encoding="utf-8-sig")))
        out.execute(
            "INSERT INTO brand (brandId,name,displayName,attribution,sortOrder,dataVersion,isEnabled) "
            "VALUES (?,?,?,?,?,?,1)",
            (bid, bname, bname, attr, sort, dver),
        )
        cnt = 0
        for r in rows:
            name = (r["name"] or "").strip()
            if not name:
                continue
            portion = (r.get("portion_note") or "").strip()
            basis = to_f(r.get("basis_grams"))
            default = to_f(r.get("default_grams"))
            # 真实克重判定：basis>=20 视为每份克重；100 且 note 含「每100克」视为已按100g口径
            per100_basis = basis == 100 and ("每100克" in portion or not portion)
            kcal = to_f(r.get("energy_kcal"))
            protein = to_f(r.get("protein"))
            fat = to_f(r.get("fat"))
            carbs = to_f(r.get("carbs"))
            sodium = to_f(r.get("sodium_mg"))
            sugar = to_f(r.get("sugar_g"))
            calcium = to_f(r.get("calcium_mg"))
            fiber = to_f(r.get("fiber"))

            if per100_basis:
                grams = 100.0
                scale = 1.0
                note = portion or "每100克"
            else:
                grams = basis if (basis and basis >= 20) else float(estimate_grams(name, portion))
                if not (basis and basis >= 20):
                    note = f"{portion} · 份量约{int(grams)}g为估算值".strip(" ·")
                else:
                    note = portion
                scale = 100.0 / grams
            def sc(v):
                return None if v is None else round(v * scale, 1)

            category = (r.get("subcategory") or "其他").strip()   # UI 分类栏直接用 category
            subcategory = "西式快餐"
            # 披萨按片存的默认份量给整张（8 片）
            default_grams = grams * 8 if ("披萨" in category and "8片" in portion and not per100_basis) else grams
            out.execute(
                """INSERT INTO food_catalog
                (food_id,name,aliases,pinyin,pinyin_full,category,subcategory,
                 energy_kcal,protein,fat,carbs,fiber,sodium,sugar,calcium,
                 basis_grams,default_grams,portion_note,source_id,source_note,
                 audit_status,catalog_version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                (
                    next_id, name, (r.get("aliases") or "").strip(),
                    (r.get("pinyin") or "").strip(), (r.get("pinyin_full") or "").strip(),
                    category, subcategory,
                    sc(kcal), sc(protein), sc(fat), sc(carbs),
                    sc(fiber), sc(sodium), sc(sugar), sc(calcium),
                    100.0, default_grams, note, bid,
                    (r.get("source_note") or "").strip()[:400],
                    0, CATALOG_VERSION,
                ),
            )
            next_id += 1
            cnt += 1
        per_brand[bname] = cnt
        inserted += cnt

    out.commit()

    counts = {t: out.execute(f"SELECT COUNT(*) FROM `{t}`").fetchone()[0] for t in tables}
    out.close()

    print("inserted:", inserted, json.dumps(per_brand, ensure_ascii=False))
    print("counts:", json.dumps(counts, ensure_ascii=False))

    # ---- 硬校验 ----
    assert counts["food_catalog"] == old_food + inserted, "主表行数不符"
    assert counts["brand"] == old_brand + 6, "brand 表应 +6"
    for t in tables:
        if t.startswith("takeaway_"):
            assert counts[t] == 0, f"{t} 必须仍为空（红线 15）"
    db2 = sqlite3.connect(DB_OUT)
    # 按 DAO 的查询口径抽查
    row = db2.execute("SELECT food_id,name,category,energy_kcal,default_grams FROM food_catalog WHERE source_id='mcdonalds' AND name LIKE '巨无霸%'").fetchone()
    print("巨无霸(每100g,默认份):", row)
    assert row and abs(row[3]) > 200, "巨无霸每100g热量应>200（512/220g）"
    assert row[4] and 100 <= row[4] <= 400, "默认份量应为整堡克重"
    row2 = db2.execute("SELECT COUNT(*) FROM food_catalog WHERE source_id='kfc'").fetchone()[0]
    assert row2 == per_brand["肯德基"]
    # identityHash 不动
    h = db2.execute("SELECT identity_hash FROM room_master_table WHERE id=42").fetchone()[0]
    print("identityHash:", h)
    db2.close()
    print("all checks passed; CATALOG_VERSION =", CATALOG_VERSION)
    return 0

if __name__ == "__main__":
    sys.exit(main())
