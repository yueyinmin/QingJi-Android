#!/usr/bin/env python3
"""内部任务：用 curl 线程池补齐 SVG，断点续传。跑完写 done 标记。"""
import csv, pathlib, subprocess, concurrent.futures

ROOT = pathlib.Path(__file__).resolve().parents[3]
CSV = ROOT / "docs/exercise-library/metadata/asset_licenses.csv"
FAIL = ROOT / "docs/exercise-library/metadata/download_failures.txt"
DONE = ROOT / "docs/exercise-library/metadata/.download_done"

rows = list(csv.DictReader(CSV.open(encoding="utf-8-sig")))
tasks = []
for r in rows:
    dest = ROOT / r["local_path"]
    if dest.exists() and dest.stat().st_size > 100 and "<svg" in dest.read_text(
        encoding="utf-8", errors="ignore"
    )[:2000]:
        continue
    dest.parent.mkdir(parents=True, exist_ok=True)
    tasks.append((r["source_url"], dest, r["exercise_uuid"], r["frame_index"]))

print(f"待下载 {len(tasks)} 帧", flush=True)


def dl(t):
    url, dest, u, fr = t
    for _ in range(3):
        p = subprocess.run(
            ["curl", "-sS", "-L", "--ssl-no-revoke", "--max-time", "25",
             "-o", str(dest), url],
            capture_output=True,
        )
        if dest.exists() and dest.stat().st_size > 100 and "<svg" in dest.read_text(
            encoding="utf-8", errors="ignore"
        )[:2000]:
            return None
    return f"{u} | frame {fr} | {p.stderr.decode('utf-8', 'ignore')[:120]}"


fails = []
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as ex:
    for i, res in enumerate(ex.map(dl, tasks), 1):
        if res:
            fails.append(res)
        if i % 30 == 0 or i == len(tasks):
            print(f"进度 {i}/{len(tasks)} 失败 {len(fails)}", flush=True)

FAIL.write_text("\n".join(fails), encoding="utf-8")
if not fails:
    DONE.write_text("ok", encoding="utf-8")
print(f"完成：失败 {len(fails)} / 计划 {len(tasks)}", flush=True)
