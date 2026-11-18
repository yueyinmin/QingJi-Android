#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
老乡鸡堂食菜「0 千卡」整理脚本（v1.3.5）。

背景
----
老乡鸡 301 道堂食菜来自 CookLikeHOC + 溯源报告 2.0：只有 92 道带每 100g
营养，其余 194 道原始营养字段为空；入库时 energy_kcal 非空列落成 0.0，
界面就显示成「0 千卡/100g」，看着像真的零热量，其实是没数据。

本脚本只做「有出处的匹配」，不编造：
  1. 同名 / 别名命中本地可信数据（v0.2 标准库、v0.3 快餐库、OmniFood8K、
     CookLikeHOC PR#216 候选）→ 写回热量与三大营养素，source_note 留痕。
  2. 人工逐条核对过的同类近似（见 CURATED）→ 写回估算值，source_note
     明确写「同类近似估算，非官方营养数据」。
  3. 其余保持 0，由界面显示「暂无热量数据」，不冒充 0 千卡。

可重复执行：写过一次的行 source_note 带「热量估算：」标记，自动跳过。
不删行、不改表结构、不动 audit_status（无 LICENSE 依旧不标已人工核对）。
"""
from __future__ import annotations

import csv
import json
import re
import sqlite3
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LXJ = ROOT / "docs/food-catalog/laoxiangji"
ASSET_DB = ROOT / "app/src/main/assets/database/food_catalog.db"
KNOWLEDGE = Path("C:/Users/22109/OneDrive/Desktop/mulin/workbuddy/体重管理App")
FOODDATA = Path("C:/Users/22109/dev/fooddata")
REPORT = LXJ / "zero_kcal_match_report.md"

MARK = "热量估算："


def norm(s: str) -> str:
    s = unicodedata.normalize("NFKC", s or "")
    return s.strip().lower().replace(" ", "").replace("\u3000", "")


def variants(s: str) -> set[str]:
    s = norm(s)
    out = {s}
    base = re.sub(r"[（(][^)）]*[)）]$", "", s)
    if base and base != s:
        out.add(base)
    return out


# 人工核对的同类近似：老乡鸡菜名 -> (标准库菜名, 说明)
# 只放「做法/主料基本一致」的条目；拿不准的一律留空，不凑数。
CURATED: dict[str, tuple[str, str]] = {
    "白米粥": ("白粥", "同为白米粥"),
    "小米南瓜粥": ("小米粥", "以小米粥近似，南瓜另计"),
    "素面": ("面条（煮）", "清汤素面近似"),
    "特色热干面": ("热干面", "同名做法"),
    "香辣牛肉面": ("牛肉面", "同为牛肉汤面，香辣口略高"),
    "手工烧麦": ("烧麦", "同名做法"),
    "荠菜鲜肉蒸饺": ("蒸饺", "同为鲜肉蒸饺"),
    "鸡汁汤包": ("灌汤包", "同为汤包"),
    "柠檬凤爪": ("泡椒凤爪", "同为去骨凤爪，调味不同"),
    "老鸡汤": ("老母鸡汤", "同为老母鸡清汤"),
    "肥西老母鸡汤": ("老母鸡汤", "同名做法"),
    "竹荪鹿茸菇鸡汤": ("香菇鸡汤", "同为菌菇鸡汤"),
    "酸辣大白菜": ("酸辣白菜", "同名做法"),
    "蒜泥菠菜": ("凉拌菠菜", "同为凉拌菠菜"),
    "白菜炖豆腐": ("豆腐炖白菜", "同为白菜炖豆腐"),
    "土豆牛腩": ("土豆炖牛肉", "同为土豆炖牛肉"),
    "炸鸡腿": ("炸鸡翅", "同为带皮炸鸡，部位不同"),
    "生炸大鸡腿": ("炸鸡翅", "同为带皮炸鸡，部位不同"),
    "手枪大鸡腿": ("炸鸡翅", "同为带皮炸鸡，部位不同"),
    "浇汁西兰花": ("清炒西兰花", "同为西兰花，浇汁另计"),
    "葱油生菜": ("清炒生菜", "同为熟生菜"),
    "葱油菜心": ("白灼菜心", "同为熟菜心"),
    "葱油青菜": ("清炒生菜", "同为熟绿叶菜"),
    "菠菜蛋皮丝": ("菠菜炒蛋", "同为菠菜炒蛋"),
    "香脆木耳": ("凉拌木耳", "同为熟拌木耳"),
    "金汤酸菜鱼": ("酸菜鱼", "同为酸菜鱼，金汤另计"),
    "砂锅酸菜鱼": ("酸菜鱼", "同为酸菜鱼"),
    "砂锅番茄米线": ("米线（煮）", "米线本体，浇头另计"),
    "砂锅原味鸡汤米线": ("米线（煮）", "米线本体，浇头另计"),
    "农家蒸蛋": ("蒸蛋羹", "同名做法"),
    "虾仁蒸鸡蛋": ("蒸蛋羹", "同为蒸蛋，虾仁另计"),
    "奥尔良鸡翅": ("红烧鸡翅", "同为带骨熟鸡翅，调味不同"),
    "蜜汁南瓜": ("蒸南瓜", "同为熟南瓜，蜜汁另计"),
    "香芋蒸排骨": ("豉汁蒸排骨", "同为蒸排骨，芋头另计"),
    "葱油鸡": ("白切鸡", "同为熟白切鸡，葱油另计"),
}


def load_candidates() -> tuple[dict[str, dict], dict[str, dict]]:
    """返回 (索引, 菜名->数据)。同名冲突时保留先出现的（优先级：v0.2 > 其他）。"""
    index: dict[str, dict] = {}
    by_name: dict[str, dict] = {}

    def add(name: str, kcal, protein, fat, carbs, source: str, tier: str):
        if not name:
            return
        try:
            kcal = float(kcal)
        except (TypeError, ValueError):
            return
        if kcal <= 0:
            return
        rec = {
            "name": name,
            "kcal": kcal,
            "protein": float(protein) if str(protein or "").strip() else None,
            "fat": float(fat) if str(fat or "").strip() else None,
            "carbs": float(carbs) if str(carbs or "").strip() else None,
            "source": source,
            "tier": tier,
        }
        by_name.setdefault(norm(name), rec)
        for v in variants(name):
            index.setdefault(v, rec)

    for rel, label in [
        (KNOWLEDGE / "食物库清单-v0.2.csv", "v0.2 标准库"),
        (KNOWLEDGE / "快餐食物库-v0.3/食物库清单-快餐品牌-v0.3.csv", "v0.3 快餐库"),
    ]:
        with open(rel, encoding="utf-8-sig") as f:
            for r in csv.DictReader(f):
                add(r.get("name", ""), r.get("energy_kcal"), r.get("protein"),
                    r.get("fat"), r.get("carbs"), label, "same")

    for rec in json.load(open(FOODDATA / "omnifood8k_dish_catalog.json", encoding="utf-8")):
        add(rec.get("name_cn", ""), rec.get("energy_kcal_per_100g"),
            rec.get("protein_g_per_100g"), rec.get("fat_g_per_100g"),
            rec.get("carbohydrates_g_per_100g"), "OmniFood8K", "same")

    patch = (LXJ / "cooklikehoc_pr216_patch.txt").read_text(encoding="utf-8")
    for block in re.split(r"={10,}\nFILE: ", patch)[1:]:
        path = re.sub(r"\s*\[[^\]]+\]$", "", block.split("\n", 1)[0].strip())
        if not path.endswith(".md"):
            continue
        name = path.split("/")[-1][:-3]
        m = re.search(r"\|\s*热量\s*\|\s*([\d.]+)\s*Kcal\s*\|", block)
        if not m:
            continue

        def gram(label: str) -> str:
            mm = re.search(r"\|\s*" + label + r"\s*\|\s*([\d.]+)\s*g\s*\|", block)
            return mm.group(1) if mm else ""

        add(name, m.group(1), gram("蛋白质"), gram("脂肪"),
            gram("碳水化合物"), "PR#216 候选（未合并）", "same")

    return index, by_name


def main() -> None:
    index, by_name = load_candidates()

    with open(LXJ / "laoxiangji_dinein.csv", encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))
    missing = [r for r in rows if not (r.get("energyKcalPer100g") or "").strip()]

    conn = sqlite3.connect(ASSET_DB)
    cur = conn.cursor()
    applied: list[tuple[str, str, str, dict, str]] = []
    skipped: list[tuple[str, str]] = []

    for r in missing:
        name = r["foodName"]
        rec = None
        for v in variants(name):
            if v in index:
                rec = index[v]
                break
        if rec is None:
            for alias in (r.get("aliases") or "").split("|"):
                for v in variants(alias):
                    if v in index:
                        rec = index[v]
                        break
                if rec:
                    break
        if rec is None and name in CURATED:
            target, note = CURATED[name]
            rec = by_name.get(norm(target))
            if rec:
                rec = dict(rec, tier="curated", source=f"{rec['source']}·{target}", note=note)

        if rec is None:
            skipped.append((name, r["category"]))
            continue

        note = rec.get("note", "同名数据" if rec["tier"] == "same" else "同类近似")
        provenance = (
            f"{MARK}{'同名匹配' if rec['tier'] == 'same' else '同类近似'}自"
            f"{rec['source']}（{note}），非官方营养数据"
        )

        cur.execute(
            "SELECT food_id, source_note, energy_kcal FROM food_catalog "
            "WHERE name=? AND source_id='laoxiangji'", (name,)
        )
        hit = cur.fetchone()
        if hit is None:
            skipped.append((name, "未入库"))
            continue
        food_id, old_note, old_kcal = hit
        if MARK in (old_note or ""):
            # 已写过：重跑不重复累加，仅计入报告（幂等）。
            prev_tier = "same" if "同名匹配自" in old_note else "curated"
            applied.append((name, r["category"], "", rec, prev_tier))
            continue
        if float(old_kcal or 0) > 0:
            continue

        cur.execute(
            "UPDATE food_catalog SET energy_kcal=?, protein=?, fat=?, carbs=?, source_note=? "
            "WHERE food_id=?",
            (rec["kcal"], rec["protein"], rec["fat"], rec["carbs"],
             ((old_note or "") + " | " + provenance).strip(" |"), food_id),
        )
        applied.append((name, r["category"], provenance, rec, rec["tier"]))

    conn.commit()
    remain = cur.execute(
        "SELECT COUNT(*) FROM food_catalog WHERE source_id='laoxiangji' AND energy_kcal<=0"
    ).fetchone()[0]
    conn.close()

    lines = [
        "# 老乡鸡 0 千卡整理报告",
        "",
        "生成：`tools/food_import/patch_lxj_zero_kcal.py`（可重复执行）",
        "",
        f"- 源数据无营养字段：{len(missing)} 道",
        f"- 本次写回：{len(applied)} 道（同名 {sum(1 for a in applied if a[4] == 'same')} / "
        f"同类近似 {sum(1 for a in applied if a[4] == 'curated')}）",
        f"- 仍无数据：{len(skipped)} 道（界面显示「暂无热量数据」，不显示 0 千卡）",
        f"- 库中仍为 0 千卡的老乡鸡行：{remain}",
        "",
        "## 写回明细",
        "",
        "| 菜名 | 分类 | 千卡/100g | 来源 |",
        "|---|---|---:|---|",
    ]
    for name, cat, prov, rec, tier in applied:
        lines.append(f"| {name} | {cat} | {rec['kcal']:g} | {prov or '（已写回，重跑跳过）'} |")
    lines += ["", "## 保持无数据（不编造）", "", "| 菜名 | 分类 |", "|---|---|"]
    for name, cat in skipped:
        lines.append(f"| {name} | {cat} |")
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"applied={len(applied)} skipped={len(skipped)} remain_zero={remain}")
    print("report:", REPORT)


if __name__ == "__main__":
    main()
