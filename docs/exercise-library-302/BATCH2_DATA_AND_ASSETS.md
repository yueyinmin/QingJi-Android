# 轻记 Android｜Workout Guide 302 动作库替换 — 第二批检查报告

日期：2026-11-17  
范围：302 条标准化数据 + 906 帧 SVG 本地化素材包。  
未改动：Room、DAO、Repository、UI、备份逻辑、App 代码。  
结论：**数据与素材全部通过校验，可进入第三批数据库迁移。**

---

## 一、标准化数据

| 校验项 | 结果 |
|---|---|
| 动作总数 | 302 ✅ |
| 旧 UUID 保留 | 38 条 `ex-*`，未改动 ✅ |
| 新动作稳定 ID | 264 条 `wg:<slug>`，无随机生成 ✅ |
| UUID / slug / 中文名唯一 | 302 / 302 / 302 ✅ |
| 中文名 | 302 条全部人工审核翻译，旧 38 条中 8 条按确认结果修订 ✅ |
| 英文名 | 上游原名完整保留在 `englishName` ✅ |
| 原始肌群 | `primaryMuscles`、`secondaryMuscles` 原样保留英文原值 ✅ |
| 器械 / 训练类型 / 拉伸标记 | 原样保留 ✅ |
| `steps` / `commonMistakes` | 全部留空，未生成任何训练指导 ✅ |
| attribution | 302 条逐条保留 JSON 原文 ✅ |

分类按工单字面规则「primaryMuscle → 七类」映射，未使用名称相似度：

| 分类 | 条数 |
|---|---|
| 胸部 | 26 |
| 背部 | 43 |
| 肩部 | 26 |
| 手臂 | 35 |
| 腿部 | 114 |
| 核心 | 54 |
| 拉伸 | 4 |

> 口径提醒：上游 `isStretch=true` 共 14 条，但其中只有 4 条 `primaryMuscle=Mobility`。当前严格按工单的肌群映射规则，所以拉伸分类只有 4 条，其余 10 条保留原肌群分类并显示拉伸标记。若你希望 14 条全部归入「拉伸」，我可在第三批前重生成数据。

---

## 二、SVG 本地化

| 校验项 | 结果 |
|---|---|
| 应有帧数 | 302 × 3 = 906 |
| 实际下载 | 906 ✅ |
| XML 可解析 | 906 / 906 ✅ |
| 根目录与尺寸 | 全部 `<svg>`，512×512 ✅ |
| 外部资源引用 | 0 ✅ |
| `<script>` | 0 ✅ |
| `<image>` / `<foreignObject>` / `<animate>` / `<set>` | 0 ✅ |
| 不兼容滤镜 | 0 ✅ |
| 逐帧授权台账 | 906 行 ✅ |
| Everkinetic 上游来源 | 76 行（仅对应动作的相应帧，未泛化到全部动作）✅ |
| CC BY-SA 4.0 授权全文 | 已保存 ✅ |

素材路径结构：`exercises/<category>/<slug>/frame-1.svg` 等，全部本地文件，无 CDN 依赖。

---

## 三、交付文件（均在 `docs/exercise-library-302/`）

| 文件 | 内容 |
|---|---|
| `exercise_library_302.json` | 302 条标准化动作记录 |
| `asset_manifest_302.json` | UUID → slug / 分类 / 三帧路径 / 授权映射 |
| `name_review_302.csv` | 302 条中文名审核表（含来源标注） |
| `old38_uuid_mapping.csv` | 第一批的旧 UUID 映射表 |
| `asset_attribution_302.csv` | 906 行逐帧授权台账 |
| `exercises_302_assets.zip` | 906 个 SVG，10.1 MB，CI 解压到 assets |
| `LICENSE_CC_BY-SA-4.0.txt` | 官方授权全文 |

---

## 四、进入第三批前的锁定项

1. 种子数据必须整体替换为这 302 条；`candidate-*` 不再作为独立动作保留。
2. 旧 38 条 UUID 一个不变，媒体映射和历史关联按 slug 精确命中。
3. 灌库仍走 UUID 差集 + `IGNORE`，不 REPLACE、不清表。
4. 新增字段在 Room 12 中用 `ALTER TABLE ... ADD COLUMN` 完成，不做破坏性迁移。
5. 对用户自建动作同名冲突：保留自定义，内置动作使用唯一变式名后插入。

**状态：第二批完成，进入第三批 Room 12 迁移与安全导入。**
