# WorkBuddy 实施工单：轻记动作库 200 条接入

## 目标
把本包中的 200 条动作资料接入现有训练中心；保留现有历史数据、动作 UUID、收藏状态与用户自建动作；图片先接入已匹配候选，未匹配动作不显示错误图片。

## 必须遵守
1. 不得修改现有 38 个 `ex-*` UUID；不得按动作中文名重新生成 UUID。
2. 新增 162 个 `candidate-*` UUID 保持稳定，未来重命名也不能改变 UUID。
3. 分类 key 沿用 `chest/back/shoulder/arm/leg/core/stretch`；UI 中文标签统一使用两字：胸部、背部、肩部、手臂、腿部、核心、拉伸。
4. 图片映射必须以 `exercise_uuid` 关联，禁止用动作名称在运行时模糊查找。
5. `candidate_pending_visual_review` 与 `needs_new_asset_search` 必须保留状态；不能伪装成图片审核通过。
6. SVG 原始文件按目录原样保存，不改色、不裁切、不转 PNG；任何后续改动都要更新授权台账。

## 数据库增量灌入（关键）
当前 `TrainingRepository` 仅在 `exerciseDao.count() == 0` 时执行 `TrainingSeed.builtIns()`，老用户数据库已有动作时，新动作不会进入库。需要改为按 UUID 增量补齐：

- 在 `ExerciseDao` 新增 `@Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMissing(items: List<ExerciseEntity>)`。
- 初始化时执行 `insertMissing(TrainingSeed.builtIns())`，不要使用 `REPLACE`，否则可能覆盖用户收藏/编辑字段。
- 先查 `findAll()`，只把数据库中不存在的 UUID 交给 `insertMissing`；或直接 IGNORE 插入全量种子。
- 不删除数据库中多余记录，不重置 `isFavorite`，不覆盖用户自建动作，不清空训练历史。
- 对已有同名不同 UUID 的动作，先检查唯一索引 `name`，不要直接插入导致冲突。当前候选表名称已做唯一性校验，但仍需在接入前对生产库数据执行冲突检测。

## Seed 数据转换
优先参考 `integration/TrainingSeed_200.kt`（按当前仓库 `ExerciseEntity` 字段生成的 200 条 Kotlin 种子草稿），再对照 `metadata/exercise_library_200.json`。将数据转为 `ExerciseEntity`：
- `uuid` ← `uuid`
- `name` ← `name`
- `category` ← `category_key`
- `primaryMuscles` ← `primary_muscles`
- `secondaryMuscles` ← `secondary_muscles`
- `equipment` ← `equipment`
- `steps` ← `steps`
- `commonMistakes` ← `common_mistakes`
- `isBuiltIn=true`，`isFavorite=false`
- `createdAt/updatedAt` 使用初始化时间

不要把 `record_status`、`image_status` 等审核元数据写进 ExerciseEntity；这些属于资产清单/内容审核层。

## UI 图片接入
- 图片根目录：`app/src/main/assets/exercises/{image_slug}/frame-{1,2,3}.svg`
- 先实现一个 `ExerciseMediaMapping`，按 UUID 返回 slug、三帧资源路径、审核状态和播放配置。
- 若 `image_slug=null`，动作详情显示统一占位图，不得拿其他动作图片顶替。
- 只有 `candidate_pending_visual_review` 且人工审核通过后，才在普通用户界面显示动作演示；审核状态需可回滚。
- 播放序列 `[1,2,3,2]`，默认 420ms/帧，循环；页面离开/列表滚动出屏后停止动画，避免后台持续刷新。
- SVG 采用 Android 可支持的解析/渲染方案；先验证 SVG 特性兼容性，再确定 Coil/AndroidSVG 等实现，不要假定 WebView 能直接用于所有场景。

## 质量门禁
1. 200 条 UUID 唯一、中文名称唯一、分类计数与 JSON 一致。
2. 数据库升级后老用户原有训练记录、收藏、自建动作不丢失。
3. 逐个验证图片 slug 存在且三帧均为合法 SVG。
4. 逐帧核对动作姿势，错误动作不得标为审核通过。
5. `asset_licenses.csv` 中每个被打包 SVG 都有作者、许可、源 URL、来源 manifest URL。
6. `needs_new_asset_search` 只展示占位，不请求不存在的资源。
7. 测试新增动作后“上次成绩自动带出”仍按稳定 UUID 工作。

## 交付要求
提交变更前输出：动作新增数、图片实际下载数、未匹配数、核验通过数、授权台账覆盖率、数据库迁移/增量灌入测试结果。不得仅凭 JSON 生成成功就宣称动作库已上线。
