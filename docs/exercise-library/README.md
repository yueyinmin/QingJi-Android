# 动作库 200 条接入说明（docs/exercise-library/）

本目录是「轻记动作库 200 条」接入的完整台账，供人工复核与后续扩充使用。

## 落地状态（如实记录，不虚报）

| 项 | 数值 | 说明 |
| --- | --- | --- |
| 动作总数 | 200 | 38 个原内置（ex-*，UUID 与内容原样保留）+ 162 个新增候选（candidate-*，UUID 稳定） |
| 七类分布 | 胸30/背35/肩25/手25/腿40/核心25/拉伸20 | 与 exercise_library_200.json 一致 |
| 有候选图 | 117 | 111 中文名直接匹配 + 6 别名候选；全部为「候选匹配待核图」 |
| 无可靠图 | 83 | 只显示统一占位，未拿相似动作顶替 |
| SVG 帧 | 351 | 117 × 3 帧，逐帧下载与校验，清单 asset_licenses.csv |
| 授权登记覆盖率 | 100% | 351 行逐帧记录作者/来源 URL/许可/manifest URL/修改记录 |
| 图片审核 | 0 条通过 | 117 条全部 candidate_pending_visual_review；UI 标注「姿势待核对」 |
| 内容审核 | 草稿 | 162 条新增动作的肌群/要点/常见错误为待复核草稿 |

## 关键文件

- `metadata/exercise_library_200.json` / `.csv`：200 条动作标准资料。
- `metadata/exercise_media_mapping_200.json`：UUID → 三帧映射 + 播放配置 + 审核状态。
- `metadata/asset_licenses.csv`：351 行逐帧授权署名台账。
- `metadata/image_match_review_queue.csv`：83 条无图动作清单（下一步优先找素材）。
- `metadata/source_image_matches_137.csv`：上游 137 条原始匹配数据（未加工）。
- `metadata/source_exercise_library_200_candidates.json`：原始候选清单。
- `metadata/validation_report.json`：数据校验报告。
- `metadata/source_attributions.md`：素材来源与许可说明。
- `scripts/download_exercise_assets.py`：断了可重跑的 SVG 下载脚本（断点续传）。
- `IMPLEMENTATION_WORK_ORDER.md`：本次接入的实施工单原文。

## 代码落点

- `data/local/TrainingSeed.kt`：200 条内置动作种子。
- `data/local/dao/TrainingDaos.kt`：`insertMissing`（IGNORE）增量插入。
- `data/repository/TrainingRepository.kt`：`ensureSeed()` 按 UUID 差集补齐，不覆盖用户数据。
- `data/training/media/ExerciseMediaMapping.kt`：117 条 UUID → 三帧映射 + 播放配置。
- `ui/screen/training/TrainingLibraryScreens.kt`：详情页三帧动画、列表静态首帧、待核对提示。
- `assets/exercises/<slug>/frame-{1,2,3}.svg`：351 张素材（本地离线）。

## 授权

Workout Guide 代码/文档 MIT，动作视觉素材 CC BY-SA 4.0（作者 Bryl Lim）。
302 个上游动作中 76 个第一帧源自 Everkinetic，**不可泛化署名到所有帧**；
逐帧归属见 asset_licenses.csv 与上游 manifest。App 内「开源许可」与根目录
THIRD_PARTY_NOTICES.md 均有说明。

## 待办（人工复核，未完成）

1. 83 条无图动作：优先寻找有三帧 SVG、授权清晰的素材，逐个核验姿势后再映射。
2. 162 条新增动作的肌群/要点/常见错误：人工复核草稿，修正 "待补充审核" / "Mobility" 等占位内容。
3. 117 条候选图：逐帧核对动作姿势，通过后把 review_status 改为已审核；不通过则撤下映射。
4. 授权署名合规：正式发布前由项目维护者/法务核对分发方式与 CC BY-SA 4.0 署名是否充分。
