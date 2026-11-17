#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OmniFood8K -> 轻记 food_catalog.db 离线 ETL。

原则：
- 现有 654 条食物原样保留；
- 新增 166 条命名食物（141 菜品 + 24 水果 + 1 米饭），全部 audit_status=0（未审核）；
- 8,037 条样本、27,466 条组成明细、470 条异常标记独立入表，不伪装成菜品；
- 严重异常样本只进 nutrition_sample / quality_issue，不进 food_catalog；
- 原始 kJ 与换算 kcal 同时保留。
"""
import argparse, csv, io, json, os, sqlite3, zipfile
from datetime import datetime, timezone
from pypinyin import lazy_pinyin, Style

SRC = "OmniFood8K"
LICENSE = "CC BY-NC 4.0"
DATASET_VERSION = "omnifood8k-clean-2026-11-17"
SEVERE = {"346", "347", "348", "2740"}  # 严重异常样本，不进正式可搜索表
EXPECTED = {
    "samples": 8037, "composition": 27466, "dictionary": 166,
    "dishes": 141, "fruits": 24, "staple": 1, "issues": 470,
}

def rows(z, name):
    text = z.read(name).decode("utf-8-sig", errors="replace")
    return list(csv.DictReader(io.StringIO(text)))

def num(v, default=0.0):
    try:
        return float(v)
    except (TypeError, ValueError):
        return default

def pinyin_pair(name):
    full = "".join(lazy_pinyin(name)).lower()
    abbr = "".join(lazy_pinyin(name, style=Style.FIRST_LETTER)).lower()
    return abbr, full

def create_tables(c):
    c.executescript("""
    CREATE TABLE IF NOT EXISTS `food_catalog` (
      `food_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `aliases` TEXT NOT NULL,
      `pinyin` TEXT NOT NULL, `pinyin_full` TEXT NOT NULL, `category` TEXT NOT NULL,
      `subcategory` TEXT NOT NULL, `energy_kcal` REAL NOT NULL, `protein` REAL NOT NULL,
      `fat` REAL NOT NULL, `carbs` REAL NOT NULL, `fiber` REAL, `sodium` REAL,
      `sugar` REAL, `calcium` REAL, `basis_grams` REAL NOT NULL,
      `default_grams` REAL NOT NULL, `portion_note` TEXT NOT NULL,
      `source_id` TEXT NOT NULL, `source_note` TEXT NOT NULL,
      `audit_status` INTEGER NOT NULL, `catalog_version` TEXT NOT NULL,
      PRIMARY KEY(`food_id`));
    CREATE TABLE IF NOT EXISTS `food_data_source` (
      `source_id` TEXT PRIMARY KEY, `source_name` TEXT NOT NULL, `source_version` TEXT NOT NULL,
      `license` TEXT NOT NULL, `attribution` TEXT NOT NULL, `original_file` TEXT NOT NULL,
      `imported_at` TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS `food_entity` (
      `food_id` TEXT PRIMARY KEY, `source_id` TEXT NOT NULL, `source_code` TEXT NOT NULL,
      `name_cn` TEXT NOT NULL, `name_en` TEXT NOT NULL DEFAULT '', `aliases` TEXT NOT NULL DEFAULT '',
      `pinyin` TEXT NOT NULL DEFAULT '', `pinyin_full` TEXT NOT NULL DEFAULT '',
      `food_type` TEXT NOT NULL, `category` TEXT NOT NULL, `data_source` TEXT NOT NULL,
      `license` TEXT NOT NULL, `audit_status` TEXT NOT NULL, `dataset_version` TEXT NOT NULL,
      `created_at` TEXT NOT NULL);
    CREATE INDEX IF NOT EXISTS `idx_food_entity_name` ON `food_entity` (`name_cn`);
    CREATE INDEX IF NOT EXISTS `idx_food_entity_code` ON `food_entity` (`source_code`);
    CREATE TABLE IF NOT EXISTS `food_nutrition` (
      `nutrition_id` TEXT PRIMARY KEY, `food_id` TEXT NOT NULL,
      `reference_weight_g` REAL NOT NULL, `energy_kj_original` REAL NOT NULL,
      `energy_kcal` REAL NOT NULL, `protein_g` REAL NOT NULL, `fat_g` REAL NOT NULL,
      `carbohydrates_g` REAL NOT NULL, `energy_kcal_per_100g` REAL NOT NULL,
      `protein_per_100g` REAL NOT NULL, `fat_per_100g` REAL NOT NULL,
      `carbohydrates_per_100g` REAL NOT NULL, `nutrition_basis` TEXT NOT NULL,
      `audit_status` TEXT NOT NULL, `source_id` TEXT NOT NULL, `source_sample_id` TEXT NOT NULL);
    CREATE INDEX IF NOT EXISTS `idx_food_nutrition_food` ON `food_nutrition` (`food_id`);
    CREATE TABLE IF NOT EXISTS `nutrition_sample` (
      `sample_id` TEXT PRIMARY KEY, `sample_type` TEXT NOT NULL,
      `total_weight_g` REAL NOT NULL, `total_energy_kj` REAL NOT NULL,
      `total_energy_kcal` REAL NOT NULL, `protein_g` REAL NOT NULL, `fat_g` REAL NOT NULL,
      `carbohydrates_g` REAL NOT NULL, `component_count` INTEGER NOT NULL,
      `quality_status` TEXT NOT NULL, `source_dataset` TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS `food_composition` (
      `composition_id` INTEGER PRIMARY KEY AUTOINCREMENT,
      `parent_sample_id` TEXT NOT NULL, `parent_food_id` TEXT NOT NULL DEFAULT '',
      `ingredient_name` TEXT NOT NULL, `ingredient_code` TEXT NOT NULL DEFAULT '',
      `ingredient_weight_g` REAL NOT NULL, `ingredient_type` TEXT NOT NULL,
      `source_sample_id` TEXT NOT NULL);
    CREATE INDEX IF NOT EXISTS `idx_composition_sample` ON `food_composition` (`parent_sample_id`);
    CREATE INDEX IF NOT EXISTS `idx_composition_code` ON `food_composition` (`ingredient_code`);
    CREATE TABLE IF NOT EXISTS `quality_issue` (
      `issue_id` INTEGER PRIMARY KEY AUTOINCREMENT, `sample_id` TEXT NOT NULL,
      `issue_flags` TEXT NOT NULL, `weight_g` REAL NOT NULL, `energy_kcal` REAL NOT NULL,
      `energy_kcal_per_100g` REAL NOT NULL, `protein_g_per_100g` REAL NOT NULL,
      `fat_g_per_100g` REAL NOT NULL, `carbohydrates_g_per_100g` REAL NOT NULL,
      `component_weight_sum_g` REAL NOT NULL, `component_weight_diff_g` REAL NOT NULL,
      `energy_relative_error_4_9_4` REAL NOT NULL, `source_dataset` TEXT NOT NULL);
    CREATE INDEX IF NOT EXISTS `idx_quality_sample` ON `quality_issue` (`sample_id`);
    """)

def copy_existing(dst, existing):
    if not existing or not os.path.exists(existing):
        return 0
    src = sqlite3.connect(existing)
    cols = [r[1] for r in src.execute("PRAGMA table_info(food_catalog)")]
    data = src.execute("SELECT " + ",".join('`%s`' % c for c in cols) + " FROM food_catalog").fetchall()
    src.close()
    ph = ",".join("?" * len(cols))
    dst.executemany("INSERT INTO food_catalog (" + ",".join('`%s`' % c for c in cols) + ") VALUES (" + ph + ")", data)
    return len(data)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--zip", required=True)
    ap.add_argument("--dish-json", required=True)
    ap.add_argument("--existing-db", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    z = zipfile.ZipFile(a.zip)
    dic = rows(z, "omnifood8k_food_dictionary.csv")
    samples = rows(z, "omnifood8k_samples_cleaned.csv")
    comp = rows(z, "omnifood8k_composition_long.csv")
    issues = rows(z, "omnifood8k_quality_issues.csv")
    catalog = json.load(open(a.dish_json, encoding="utf-8"))
    assert len(dic) == EXPECTED["dictionary"]
    assert len(samples) == EXPECTED["samples"]
    assert len(comp) == EXPECTED["composition"]
    assert len(issues) == EXPECTED["issues"]
    assert len(catalog) == EXPECTED["dishes"]
    assert sum(r["food_type"] == "fruit" for r in dic) == EXPECTED["fruits"]
    assert sum(r["food_type"] == "staple" for r in dic) == EXPECTED["staple"]

    # 产物是构建中间物，重复运行必须从零生成，保证幂等
    if os.path.exists(a.out):
        os.remove(a.out)
    out = sqlite3.connect(a.out)
    create_tables(out)
    kept = copy_existing(out, a.existing_db)
    now = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")
    out.execute("INSERT OR REPLACE INTO food_data_source VALUES (?,?,?,?,?,?,?)",
                (SRC, "OmniFood8K", DATASET_VERSION, LICENSE,
                 "OmniFood8K / yudongjian / CC BY-NC 4.0 / 非商业用途；原始文件 8k-result.xlsx",
                 "8k-result.xlsx", now))

    max_id = out.execute("SELECT COALESCE(MAX(food_id),100000) FROM food_catalog").fetchone()[0]
    next_id = max_id + 1
    label = {"dish": "菜品", "fruit": "水果", "staple": "主食"}
    dish_by_code = {x["food_code"]: x for x in catalog}
    for r in dic:
        code = r["food_code"]
        name = r["name_cn"]
        abbr, full = pinyin_pair(name)
        kcal = num(r["reference_energy_kcal"])
        protein = num(r["reference_protein_g"])
        fat = num(r["reference_fat_g"])
        carbs = num(r["reference_carbohydrates_g"])
        w = num(r["reference_weight_g"], 100.0)
        sample_id = str(int(num(r["source_sample_id"]))) if r["source_sample_id"] else ""
        audit = 0  # 全部未审核；provisional 与异常样本不冒充实测推荐
        out.execute("""INSERT INTO food_catalog
            (food_id,name,aliases,pinyin,pinyin_full,category,subcategory,energy_kcal,protein,fat,carbs,
             fiber,sodium,sugar,calcium,basis_grams,default_grams,portion_note,source_id,source_note,
             audit_status,catalog_version)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            (next_id, name, r["name_en"], abbr, full, label.get(r["food_type"], "其它"), r["food_type"],
             kcal, protein, fat, carbs, None, None, None, None, 100.0, w,
             "OmniFood8K 原始数据，未逐条人工核对", SRC, LICENSE + " | " + DATASET_VERSION,
             audit, "0.4-omnifood8k"))
        food_id = "omni:" + code
        d = dish_by_code.get(code, {})
        # 本次只做了机器结构校验，没有逐条人工审核；不得标 approved。
        status = "provisional" if d.get("review_status") == "provisional" else "needs_verification"
        if sample_id in SEVERE:
            status = "quarantined_severe"
        out.execute("INSERT OR IGNORE INTO food_entity VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    (food_id, SRC, code, name, r["name_en"], r["name_en"], abbr, full,
                     r["food_type"], label.get(r["food_type"], "其它"), SRC, LICENSE,
                     status, DATASET_VERSION, now))
        out.execute("""INSERT OR IGNORE INTO food_nutrition
            (nutrition_id,food_id,reference_weight_g,energy_kj_original,energy_kcal,protein_g,fat_g,
             carbohydrates_g,energy_kcal_per_100g,protein_per_100g,fat_per_100g,
             carbohydrates_per_100g,nutrition_basis,audit_status,source_id,source_sample_id)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            ("omni-n:" + code, food_id, w, num(r["reference_energy_kj"]), kcal, protein, fat, carbs,
             num(r["energy_kcal_per_100g"]), num(r["protein_g_per_100g"]),
             num(r["fat_g_per_100g"]), num(r["carbohydrates_g_per_100g"]),
             "per_100g", status, SRC, sample_id))
        next_id += 1

    for r in samples:
        sid = r["sample_id"]
        status = "quarantined_severe" if sid in SEVERE else "recorded"
        out.execute("INSERT OR IGNORE INTO nutrition_sample VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    (sid, r["sample_type"], num(r["weight_g"]), num(r["energy_kj_raw"]),
                     num(r["energy_kcal"]), num(r["protein_g"]), num(r["fat_g"]),
                     num(r["carbohydrates_g"]), int(num(r["component_count"])),
                     status, SRC))
    for r in comp:
        out.execute("""INSERT INTO food_composition
            (parent_sample_id,parent_food_id,ingredient_name,ingredient_code,
             ingredient_weight_g,ingredient_type,source_sample_id)
            VALUES (?,?,?,?,?,?,?)""",
            (r["sample_id"], "", r["ingredient_name_cn"] or r["ingredient_raw"],
             r["ingredient_code"], num(r["component_weight_g"]), r["ingredient_type"], r["sample_id"]))
    for r in issues:
        out.execute("""INSERT INTO quality_issue
            (sample_id,issue_flags,weight_g,energy_kcal,energy_kcal_per_100g,
             protein_g_per_100g,fat_g_per_100g,carbohydrates_g_per_100g,
             component_weight_sum_g,component_weight_diff_g,energy_relative_error_4_9_4,source_dataset)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""",
            (r["sample_id"], r["issue_flags"], num(r["weight_g"]), num(r["energy_kcal"]),
             num(r["energy_kcal_per_100g"]), num(r["protein_g_per_100g"]),
             num(r["fat_g_per_100g"]), num(r["carbohydrates_g_per_100g"]),
             num(r["component_weight_sum_g"]), num(r["component_weight_diff_g"]),
             num(r["energy_relative_error_4_9_4"]), SRC))

    out.commit()
    counts = {t: out.execute("SELECT COUNT(*) FROM " + t).fetchone()[0] for t in
              ["food_catalog", "food_entity", "food_nutrition", "nutrition_sample",
               "food_composition", "quality_issue", "food_data_source"]}
    out.close()
    print("existing_kept", kept)
    print("counts", json.dumps(counts, ensure_ascii=False))
    print("out", a.out, os.path.getsize(a.out))

if __name__ == "__main__":
    main()
