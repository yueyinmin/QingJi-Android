#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""标准库 v0.6(Room v4) → v0.7(Room v5)：增量加 8 张外卖空表，不动一行旧数据。

输入：
  --existing  当前 0.6 预置库（food_catalog.db，v4，1121 条主表）
  --schema    KSP 编译生成的 5.json（19 张表的 createSql）
输出：
  新的 food_catalog.db：旧 12 张表数据原样搬运 + 8 张 takeaway_* 空表 + v5 身份信息

规矩（沿用 v1.3.1/v0.6 教训）：
- 建表 SQL 逐字来自 Room 5.json 的 createSql，绝不手写；
- 先把旧表数据读进内存，再 DROP+重建（apply_room_schema 会 DROP 所有 entity 表）；
- 红线 15：8 张外卖表全空，没真实商品绝不造假；堂食菜只留逻辑关联，不复制。
"""
import argparse
import json
import os
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from omnifood8k_etl import apply_room_schema  # noqa: E402

CATALOG_VERSION = "0.7-laoxiangji-takeaway"

# 旧库已有的 11 张数据表（7 张 OmniFood 基础 + brand 4 张）
OLD_TABLES = [
    "food_catalog", "food_entity", "food_nutrition", "nutrition_sample",
    "food_composition", "food_data_source", "quality_issue",
    "brand", "brand_dish_trace", "brand_dish_ingredient", "brand_dish_processing",
]

# 本轮新增的 8 张外卖表（必须全空）
TAKEAWAY_TABLES = [
    "takeaway_product", "takeaway_variant", "takeaway_combo", "takeaway_combo_item",
    "takeaway_nutrition", "takeaway_platform", "takeaway_store_scope", "takeaway_data_source",
]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--existing", required=True)
    ap.add_argument("--schema", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    schema = json.load(open(a.schema, encoding="utf-8"))
    ehash = schema["database"]["identityHash"]
    eversion = schema["database"]["version"]
    assert eversion == 5, f"schema 版本应为 5，实际 {eversion}"

    # 1) 先把旧表全部读进内存（含列名，逐列搬运，不丢列）
    src = sqlite3.connect(a.existing)
    snapshot = {}
    for table in OLD_TABLES:
        cols = [r[1] for r in src.execute(f"PRAGMA table_info(`{table}`)")]
        rows = src.execute(
            f"SELECT {','.join(chr(96)+c+chr(96) for c in cols)} FROM `{table}`"
        ).fetchall()
        snapshot[table] = (cols, rows)
    src.close()

    # 2) 从零重建：Room 5.json 建 19 张表（含 8 张新空表）
    if os.path.exists(a.out):
        os.remove(a.out)
    out = sqlite3.connect(a.out)
    apply_room_schema(out, a.schema)

    # 3) 旧 11 张表原样写回
    kept = {}
    for table, (cols, rows) in snapshot.items():
        ph = ",".join("?" * len(cols))
        out.executemany(
            f"INSERT INTO `{table}` ({','.join(chr(96)+c+chr(96) for c in cols)}) VALUES ({ph})",
            rows,
        )
        kept[table] = len(rows)

    # 4) Room 身份信息（identity_hash 用 5.json 的，user_version=5）
    out.execute("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)", (ehash,))
    out.execute(f"PRAGMA user_version = {eversion}")
    out.commit()

    counts = {t: out.execute(f"SELECT COUNT(*) FROM `{t}`").fetchone()[0] for t in OLD_TABLES + TAKEAWAY_TABLES}
    out.close()

    print("kept(旧表搬运):", json.dumps(kept, ensure_ascii=False))
    print("takeaway 新表(应全 0):", json.dumps({t: counts[t] for t in TAKEAWAY_TABLES}, ensure_ascii=False))
    print("identityHash:", ehash, "version:", eversion)

    # 5) 硬校验：旧数据一行不丢，外卖表全空
    assert kept["food_catalog"] == 1121, f"旧主表应 1121，实际 {kept['food_catalog']}"
    assert kept["brand"] == 1, "品牌表应 1 行"
    assert kept["brand_dish_trace"] == 176, "溯源卡应 176 行"
    for t in TAKEAWAY_TABLES:
        assert counts[t] == 0, f"{t} 应为空表，实际 {counts[t]}"
    print("all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
