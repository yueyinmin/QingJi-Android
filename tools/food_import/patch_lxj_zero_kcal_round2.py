#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
老乡鸡「0 千卡」第二轮补齐（v1.3.5 追加）：覆盖剩余 96 道。

原则不变——只写有出处的值：
  1. 同类近似（CURATED2，94 道）：全部映射到 v0.2 标准库/v0.3 快餐库的
     「同主料或同做法」锚点菜，source_note 写明锚点与差异点；
  2. 包装标示（ZERO_LABEL，2 道）：农夫山泉矿泉水、摩登零度可乐为无糖/水，
     包装标示即 0 千卡，按标示写 0 并留痕。注意：界面把 energy_kcal<=0
     一律显示为「暂无热量数据」，这两行显示口径偏保守但不误导。

幂等：带「热量估算：」标记的行自动跳过；可重复执行。
"""
from __future__ import annotations

import sys
import sqlite3
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from patch_lxj_zero_kcal import (  # noqa: E402
    ASSET_DB, LXJ, MARK, load_candidates, norm,
)

REPORT = LXJ / "zero_kcal_round2_report.md"

# 菜名 -> (锚点菜名, 差异说明)。锚点值即每 100g 值。
CURATED2: dict[str, tuple[str, str]] = {
    # ---- 主食（汤面/拌面，浇头主导）----
    "大大大块牛腩面": ("红烧牛腩", "同为大块牛腩，面条本体另计"),
    "大排面": ("排骨面", "同为浇头汤面，猪排换排骨"),
    "大盘肥肠鸡手工面": ("黄焖鸡", "同为鸡块炖浇头，肥肠另计"),
    "浓香整块鸡汤面": ("鸡肉盖浇饭", "同为鸡肉浇头主食"),
    "砂锅荠菜鲜肉馄饨": ("馄饨", "同为鲜肉馄饨"),
    "肥西老母鸡汤面": ("阳春面", "清汤面底，鸡汤浇头清淡"),
    "雪菜肉丝面": ("榨菜肉丝", "同为咸菜肉丝浇头"),
    "香辣鸡丁拌面": ("炸酱面", "同为油润拌面，鸡丁浇头"),
    # ---- 其他（砂锅/嗨嗨锅）----
    "冒烤鸭": ("手撕鸡", "同为熟禽肉，冒制调料另计"),
    "嗨嗨锅（泡椒鸡米花米线）": ("牛肉面", "同为浇头汤粉，泡椒浇头近似"),
    "嗨嗨锅（番茄肥牛锅）": ("西红柿炖牛腩", "同为番茄+牛肉炖锅"),
    "嗨嗨锅（酸菜鱼米线）": ("酸菜鱼", "同为酸菜鱼，米线本体另计"),
    "嗨嗨锅（鲜蔬锅）": ("素什锦", "同为杂蔬炖锅"),
    "砂锅三鲜豆腐": ("家常豆腐", "同为豆腐烧制"),
    "砂锅木瓜": ("南瓜粥", "同为瓜类炖煮甜口"),
    "砂锅泡椒鸡米花米线": ("牛肉面", "同为浇头汤粉，泡椒浇头近似"),
    "砂锅牛杂煲": ("萝卜炖牛腩", "同为牛杂炖煲"),
    "砂锅盐焗鸡": ("盐焗鸡", "同名做法"),
    "砂锅腐竹": ("凉拌腐竹", "同为腐竹主料，炖制吸油相近"),
    "砂锅蒜蓉粉丝虾": ("蒜蓉粉丝虾", "同名做法"),
    "葱油拌面": ("热干面", "同为油润拌面"),
    "酸菜肥肠煲": ("干煸肥肠", "同为肥肠主料，煲制略轻"),
    "麻辣鸡块": ("辣子鸡丁", "同为辣味鸡块"),
    # ---- 凉拌 ----
    "凉拌莴笋丝": ("凉拌海带丝", "同为清脆凉拌菜"),
    "西芹花生米": ("凉拌豆腐皮", "同为植物蛋白凉拌，花生脂含量更高"),
    # ---- 卤菜 ----
    "卤大排": ("卤牛肉", "同为卤制瘦肉"),
    "嗨嗨桶（红油串串）": ("炸串（平均）", "同为串串油炸"),
    # ---- 早餐 ----
    "包子": ("猪肉包子", "同名做法"),
    "手工春卷": ("锅贴", "同为煎炸面点包馅"),
    "牛肉盒": ("猪肉馅饼", "同为油炸肉馅饼"),
    "粢饭糕": ("锅贴", "同为油炸米面早餐，吸油相近"),
    "蛋饼": ("鸡蛋灌饼", "同为鸡蛋+面饼"),
    "赤豆糊元宵": ("豆沙包", "同为豆沙甜馅主食"),
    "酥皮萝卜丝馅饼": ("猪肉馅饼", "同为酥皮馅饼"),
    # ---- 炒菜 ----
    "什锦蛋炒饭": ("扬州炒饭", "同为什锦蛋炒饭"),
    "包菜炒鸡蛋": ("韭菜炒蛋", "同为蔬菜炒蛋"),
    "小炒河虾": ("白灼虾", "同为小鲜虾，油炒略高"),
    "小炒花菜": ("肉片炒花菜", "同为花菜小炒"),
    "小炒面筋": ("家常豆腐", "同为豆制品小炒"),
    "小炒黄牛肉": ("尖椒牛柳", "同为嫩牛肉小炒"),
    "毛豆烧土鸡": ("大盘鸡", "同为鸡块烧毛豆类炖烧"),
    "油渣大白菜": ("手撕包菜", "同为白菜小炒，油渣提香另计"),
    "清炒毛白菜": ("清炒生菜", "同为绿叶清炒"),
    "清炒莴笋片": ("清炒芦笋", "同为茎秆类清炒"),
    "清炒青菜": ("清炒茼蒿", "同为绿叶清炒"),
    "生炒上海青": ("清炒空心菜", "同为绿叶旺火炒"),
    "笋子烧肉": ("蒜苗炒肉", "同为时蔬烧五花，笋子换蒜苗近似"),
    "肥肠鸡": ("大盘鸡", "同为鸡块炖烧，肥肠另计"),
    "胡萝卜炒木耳": ("素炒三丝", "同为什锦素炒"),
    "胡萝卜炒肉片": ("土豆炒肉片", "同为根茎+肉片"),
    "胡萝卜炒鸡蛋": ("木耳炒蛋", "同为蔬菜炒蛋"),
    "芋儿鸡": ("黄焖鸡", "同为鸡块炖芋头类，芋头另计"),
    "芹菜香干炒肉丝": ("豆角炒肉丝", "同为时蔬香干肉丝小炒"),
    "蒜苔炒香干": ("芹菜炒香干", "同为香干时蔬小炒"),
    "蚕豆炒鸡蛋": ("韭菜炒蛋", "同为时蔬炒蛋"),
    "贵州风味辣子鸡": ("辣子鸡", "同名做法"),
    "青椒毛豆米": ("清炒豆角", "同为豆类素炒，毛豆略高"),
    "香椿炒鸡蛋": ("蒜苗炒蛋", "同为香气蔬菜炒蛋"),
    # ---- 炖菜 ----
    "甜辣辣年糕烧鸡": ("大盘鸡", "同为鸡块烧配菜，年糕另计"),
    "肉汁素鸡": ("素鸡", "同名做法"),
    "鸡血汤": ("老母鸡汤", "同为鸡副清汤"),
    # ---- 炸品 ----
    "傲椒风味翅尖": ("炸鸡翅", "同为带皮炸鸡，翅尖量少"),
    "嗨嗨桶（炸物桶）": ("炸串（平均）", "同为混合炸物"),
    "心形鸡排": ("炸鸡翅", "同为裹粉炸鸡排"),
    "香芋地瓜丸": ("洋葱圈", "同为裹粉油炸团丸"),
    "香酥鸡米花": ("劲爆鸡米花", "同为裹粉炸鸡米花（快餐库 per100）"),
    "鸡肉洋葱圈": ("洋葱圈", "同名做法，鸡肉裹馅"),
    # ---- 烤类 ----
    "烤肠": ("烤香肠", "同名做法"),
    # ---- 蒸菜 ----
    "活珠子/凤凰蛋": ("水煮蛋", "同为整蛋蒸煮，风味另计"),
    "胡萝卜牛肉": ("萝卜炖牛腩", "同为根茎炖牛肉"),
    "蒜蓉娃娃菜": ("上汤娃娃菜", "同为娃娃菜蒸煮"),
    "豌豆肉末": ("肉末豇豆", "同为肉末焖时蔬"),
    "酱椒蒜香片片鱼": ("剁椒鱼头", "同为剁椒蒸鱼"),
    "酱蒸白干": ("红烧豆腐", "同为豆制品酱蒸"),
    "酱蒸豆腐": ("红烧豆腐", "同为豆腐酱蒸"),
    "酱香小河虾": ("白灼虾", "同为小虾，酱香另计"),
    "风干牛肉蒸白干": ("酱牛肉", "同为风干/酱制牛肉，白干另计"),
    "香肠蒸豆米": ("广式香肠", "同为香肠主料蒸制，豆米另计"),
    "香辣红油鸡（小母鸡版）": ("口水鸡", "同为红油拌熟鸡"),
    "香辣红油鸡（白切鸡版）": ("口水鸡", "同为红油拌熟鸡"),
    "香辣红油鸡（鸡排腿版）": ("口水鸡", "同为红油拌熟鸡"),
    "香辣血旺": ("水煮肉片", "同为川味血旺辣汤，肉片另计"),
    # ---- 饮品（包装/现制，锚点同品类）----
    "东方树叶（335mL）": ("红茶（无糖）", "同为无糖茶饮"),
    "劲酒": ("白酒", "同为高度蒸馏酒（劲酒 35° 略低，取白酒近似）"),
    "奶皮子酸奶": ("希腊酸奶", "同为浓稠酸奶"),
    "摩登罐装可乐": ("可乐", "同为含糖汽水"),
    "摩登罐装芬达": ("雪碧", "同为含糖碳酸饮料"),
    "摩登罐装雪碧": ("雪碧", "同为含糖碳酸饮料"),
    "热奶茶": ("奶茶（全糖）", "同为含糖奶茶"),
    "精酿啤酒330mL": ("啤酒", "同为啤酒，精酿度数稍高"),
    "苹果山楂红茶": ("苹果汁", "同为果味甜饮，茶底更淡"),
    "蒙牛纯牛奶（150mL）": ("全脂牛奶", "同为全脂牛奶"),
    "雪花纯生啤酒（罐装）": ("啤酒", "同为啤酒"),
    "鸡笼香柠檬茶": ("蜂蜜柚子茶", "同为含糖果味茶饮"),
}

# 包装标示直接为 0 的条目：(kcal, protein, fat, carbs, 说明)
ZERO_LABEL: dict[str, tuple[float, float, float, float, str]] = {
    "农夫山泉矿泉水（运动盖）": (0.0, 0.0, 0.0, 0.0, "包装营养成分表标示 0 千卡（矿泉水）"),
    "摩登零度可乐": (0.0, 0.0, 0.0, 0.0, "包装营养成分表标示 0 千卡（无糖可乐）"),
}


def main() -> None:
    index, by_name = load_candidates()
    conn = sqlite3.connect(ASSET_DB)
    cur = conn.cursor()

    rows = cur.execute(
        "SELECT food_id, name, category, source_note, energy_kcal FROM food_catalog "
        "WHERE source_id='laoxiangji' AND energy_kcal<=0"
    ).fetchall()

    applied: list[tuple[str, str, str]] = []
    zeroed: list[tuple[str, str]] = []
    skipped: list[tuple[str, str, str]] = []

    for food_id, name, cat, old_note, old_kcal in rows:
        if MARK in (old_note or ""):
            skipped.append((name, cat, "已写过（幂等跳过）"))
            continue
        if name in ZERO_LABEL:
            kcal, p, f, c, why = ZERO_LABEL[name]
            prov = f"{MARK}包装标示（{why}），非估算"
            cur.execute(
                "UPDATE food_catalog SET energy_kcal=?, protein=?, fat=?, carbs=?, source_note=? "
                "WHERE food_id=?",
                (kcal, p, f, c, ((old_note or "") + " | " + prov).strip(" |"), food_id),
            )
            zeroed.append((name, why))
            continue
        if name not in CURATED2:
            skipped.append((name, cat, "本轮无映射"))
            continue
        target, note = CURATED2[name]
        rec = by_name.get(norm(target))
        if rec is None:
            skipped.append((name, cat, f"锚点缺失：{target}"))
            continue
        prov = (
            f"{MARK}同类近似自{rec['source']}·{target}（{note}，锚点 {rec['kcal']:g} 千卡/100g），"
            "非官方营养数据"
        )
        cur.execute(
            "UPDATE food_catalog SET energy_kcal=?, protein=?, fat=?, carbs=?, source_note=? "
            "WHERE food_id=?",
            (rec["kcal"], rec["protein"], rec["fat"], rec["carbs"],
             ((old_note or "") + " | " + prov).strip(" |"), food_id),
        )
        applied.append((name, cat, prov))

    conn.commit()
    remain = cur.execute(
        "SELECT COUNT(*) FROM food_catalog WHERE source_id='laoxiangji' AND energy_kcal<=0"
    ).fetchone()[0]
    pos = cur.execute(
        "SELECT COUNT(*) FROM food_catalog WHERE source_id='laoxiangji' AND energy_kcal>0"
    ).fetchone()[0]
    conn.close()

    lines = [
        "# 老乡鸡 0 千卡整理报告 · 第二轮",
        "",
        "生成：`tools/food_import/patch_lxj_zero_kcal_round2.py`（可重复执行，幂等）",
        "",
        f"- 本轮写回：{len(applied)} 道（全部为同类近似，锚点来自 v0.2 标准库/v0.3 快餐库）",
        f"- 包装标示写 0：{len(zeroed)} 道（矿泉水/无糖可乐，界面显示「暂无热量数据」，口径保守不误导）",
        f"- 跳过：{len(skipped)} 道",
        f"- 库内老乡鸡：有热量 {pos} 道，energy_kcal<=0 剩 {remain} 道",
        "",
        "## 写回明细",
        "",
        "| 菜名 | 分类 | 千卡/100g | 来源 |",
        "|---|---|---:|---|",
    ]
    for name, cat, prov in applied:
        kcal = prov.split("锚点 ")[1].split(" 千卡")[0] if "锚点 " in prov else ""
        lines.append(f"| {name} | {cat} | {kcal} | {prov} |")
    lines += ["", "## 包装标示写 0", ""]
    for name, why in zeroed:
        lines.append(f"- {name}：{why}")
    lines += ["", "## 跳过明细", "", "| 菜名 | 分类 | 原因 |", "|---|---|---|"]
    for name, cat, why in skipped:
        lines.append(f"| {name} | {cat} | {why} |")
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"applied={len(applied)} zeroed={len(zeroed)} skipped={len(skipped)} remain_zero={remain}")
    print("report:", REPORT)


if __name__ == "__main__":
    main()
