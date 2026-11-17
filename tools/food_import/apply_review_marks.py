#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 OmniFood8K 新数据的审核状态改成「人工已审核」，并删除严重异常样本。

背景（2026-11-17 用户拍板）：
- 166 条命名食物全部标记人工已审核（approved / audit_status=1），
  界面上不显示任何审核标记（本来也没有界面读这两列）；
- 8 条 provisional（暂定）条目保留现状，不动；
- 4 条严重异常样本（346 / 347 / 348 / 2740）按用户指示直接删除：
  连同它们的组成明细（9 行）和异常标记（4 行）一起清掉。

只改数据、不改表结构：Room schema（3.json）和 identity hash 都不变，
不需要任何 Migration。手机端换库靠 FoodCatalogDatabase.CATALOG_VERSION
升到 0.5-omnifood8k-reviewed 触发「删旧文件、从 assets 重新拷贝」。

用法（在仓库根目录）：
    python tools/food_import/apply_review_marks.py app/src/main/assets/database/food_catalog.db
"""
import sqlite3
import sys

SEVERE_SAMPLES = ("346", "347", "348", "2740")

EXPECTED = {
    "food_entity_approved": 158,   # 166 - 8 条 provisional
    "food_entity_provisional": 8,
    "food_nutrition_approved": 158,
    "samples_after": 8033,         # 8037 - 4
    "composition_after": 27457,    # 27466 - 9
    "quality_issue_after": 466,    # 470 - 4
}


def main(db_path: str) -> int:
    con = sqlite3.connect(db_path)
    cur = con.cursor()

    # ---- 1) 审核状态：needs_verification -> approved，provisional 不动 ----
    cur.execute("UPDATE food_entity SET audit_status='approved' WHERE audit_status='needs_verification'")
    cur.execute("UPDATE food_nutrition SET audit_status='approved' WHERE audit_status='needs_verification'")

    # ---- 2) food_catalog 主表：166 条 OmniFood8K 行 audit_status 0(未审) -> 1(已核对) ----
    # AuditStatus 语义：0 未审 / 1 已核对 / 2 有出处（见 CatalogFood.kt）。旧 654 条不动。
    cur.execute(
        "UPDATE food_catalog SET audit_status=1, "
        "portion_note='OmniFood8K 已人工复核（CC BY-NC 4.0）' "
        "WHERE source_id='OmniFood8K'"
    )

    # ---- 3) 删除 4 条严重异常样本及其关联行 ----
    ph = ",".join("?" * len(SEVERE_SAMPLES))
    del_comp = cur.execute(
        f"DELETE FROM food_composition WHERE parent_sample_id IN ({ph})", SEVERE_SAMPLES
    ).rowcount
    del_issue = cur.execute(
        f"DELETE FROM quality_issue WHERE sample_id IN ({ph})", SEVERE_SAMPLES
    ).rowcount
    del_sample = cur.execute(
        f"DELETE FROM nutrition_sample WHERE sample_id IN ({ph})", SEVERE_SAMPLES
    ).rowcount

    con.commit()

    # ---- 4) 校验 ----
    counts = {
        "food_entity_approved": cur.execute(
            "SELECT COUNT(*) FROM food_entity WHERE audit_status='approved'").fetchone()[0],
        "food_entity_provisional": cur.execute(
            "SELECT COUNT(*) FROM food_entity WHERE audit_status='provisional'").fetchone()[0],
        "food_nutrition_approved": cur.execute(
            "SELECT COUNT(*) FROM food_nutrition WHERE audit_status='approved'").fetchone()[0],
        "food_catalog_checked": cur.execute(
            "SELECT COUNT(*) FROM food_catalog WHERE source_id='OmniFood8K' AND audit_status=1").fetchone()[0],
        "samples_after": cur.execute("SELECT COUNT(*) FROM nutrition_sample").fetchone()[0],
        "composition_after": cur.execute("SELECT COUNT(*) FROM food_composition").fetchone()[0],
        "quality_issue_after": cur.execute("SELECT COUNT(*) FROM quality_issue").fetchone()[0],
    }
    con.execute("VACUUM")
    con.close()

    print("deleted: composition=%d issue=%d sample=%d" % (del_comp, del_issue, del_sample))
    for k, v in counts.items():
        flag = "OK" if EXPECTED.get(k, v) == v else "!! MISMATCH"
        print(f"{k}={v} {flag}")

    bad = [k for k, v in counts.items() if EXPECTED.get(k, v) != v]
    if bad:
        print("FAILED:", bad)
        return 1
    print("all checks passed")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
