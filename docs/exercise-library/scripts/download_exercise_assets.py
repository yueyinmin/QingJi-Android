#!/usr/bin/env python3
"""下载动作库候选 SVG 帧（三帧体系）到 app/src/main/assets/exercises/。

用法（仓库根目录）：python docs/exercise-library/scripts/download_exercise_assets.py
清单来源：docs/exercise-library/metadata/asset_licenses.csv（351 条逐帧任务）
特性：断点续传（已存在且合法的跳过）、失败重试 3 次、进度输出、失败清单落盘。
不联网时也安全：失败的帧只记录，不会写坏文件；UI 会自动回退占位图。
"""
import csv
import pathlib
import sys
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[3]  # 仓库根目录
CSV = ROOT / "docs/exercise-library/metadata/asset_licenses.csv"
FAIL = ROOT / "docs/exercise-library/metadata/download_failures.txt"

rows = list(csv.DictReader(CSV.open(encoding="utf-8-sig")))
fail = []
ok = skipped = 0
total = len(rows)

for i, row in enumerate(rows, 1):
    dest = ROOT / row["local_path"]
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 100 and "<svg" in dest.read_text(
        encoding="utf-8", errors="ignore"
    )[:2000]:
        skipped += 1
        continue
    data = None
    for attempt in range(3):
        try:
            req = urllib.request.Request(
                row["source_url"],
                headers={"User-Agent": "QingJi-Exercise-Asset-Importer/1.0"},
            )
            with urllib.request.urlopen(req, timeout=30) as r:
                data = r.read()
            if "<svg" not in data[:2000].decode("utf-8", errors="ignore"):
                raise ValueError("response is not an SVG document")
            break
        except Exception as e:  # noqa: BLE001
            if attempt == 2:
                fail.append(
                    f"{row['exercise_uuid']} | {row['frame_index']} | "
                    f"{row['source_url']} | {e}"
                )
            else:
                time.sleep(1.0 * (attempt + 1))
    if data is not None:
        dest.write_bytes(data)
        ok += 1
    if i % 50 == 0 or i == total:
        print(f"进度 {i}/{total}  下载 {ok} 跳过 {skipped} 失败 {len(fail)}", flush=True)

FAIL.write_text("\n".join(fail), encoding="utf-8")
print(f"完成：下载 {ok}，跳过 {skipped}，失败 {len(fail)}，计划 {total}")
if fail:
    sys.exit(2)
