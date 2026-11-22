# ChatGPT 复审材料 · 轻记统一训练体系 B1.1

## 0. 复审任务

请基于以下材料做独立技术交叉检查，重点判断：

1. 强类型分流是否彻底，是否还存在 `recordType.isBlank()/isNotBlank()` 二分。
2. 目标值是否会被写成实际完成值。
3. HIIT/FLEXIBILITY/OTHER 是否被静默降级或丢字段。
4. 编辑、历史、日记、复制计划、备份恢复是否形成闭环。
5. 是否引入不必要 migration、重复表或历史数据改写。
6. 热量计算是否符合“无可靠参数不造数”。

复审基线 HEAD：`20258ab5eaf6170e248617022309f047ea6cdcb9`  
实施分支：`feature/unified-training`  
代码提交 SHA：`8575fe3ecd99d880c32c68baec3444e5a7869934`（`fix(training): B1.1缺陷修复与全链路补齐`）

---

## 1. 实施前审计报告摘要

- 默认分支 `main` = `30b478e`（比审查基线 `450df9f` 落后 76 提交）；本地 HEAD 正是基线 merge 的第二个 parent。
- Room v22；迁移链 1→22 完整；本次无需升版本。
- 审计确认 11 项问题全部仍存在：错误分流、目标当实际、统一 SetEditorCard、手动添加无类型、HIIT 降级、编辑丢有氧、有氧热量必跳过、历史只显示力量、日记固定力量、复制计划只拷力量、备份缺有氧与新字段。

完整报告：`docs/audit/轻记统一训练体系B1.1实施前复审报告.md`

---

## 2. 最终实施报告

完整报告：`docs/reports/轻记统一训练体系B1.1缺陷修复最终报告.md`

关键结论：

- 最终结论：有条件通过。
- Room 版本不变（v22）；备份格式 v7→v8。
- STRENGTH→`workout_exercise.setsJson`；CARDIO→`workout_cardio_detail`；HIIT/FLEX/OTHER→`workout_exercise.itemMetaJson`（版本化 WorkoutItem JSON，空 setsJson）。
- 目标只存 `targetConfigJson`；实际只来自 `actualJson` / 用户输入。
- 有氧热量只在动作 slug 命中参数库 MET 时计算，否则 method/version 留空、不写 0 kcal 账本。

---

## 3. 关键代码位置

| 主题 | 文件 | 方法 / 符号 |
|---|---|---|
| 强类型分流 | `app/src/main/java/com/qingji/app/domain/TrainingItemMapper.kt` | `resolveType`, `draftToItem` |
| 保存 / 事务 | `data/repository/TrainingRepository.kt` | `saveUnifiedSession`, `sessionDetail`, `saveSessionAsTemplate`, `upsertTrainingDiaryLog` |
| ViewModel 保存 | `ui/viewmodel/TrainingViewModel.kt` | `save`, `editSession`, `addExercise`, `updateActual` |
| 执行 UI | `ui/screen/training/TrainingActiveScreen.kt` | `NonStrengthEditorCard` |
| 历史展示 | `ui/screen/training/TrainingDiaryContent.kt` | `allSessionItems` 消费与详情渲染 |
| 备份恢复 | `data/backup/TrainingBackup.kt`, `BackupManager.kt` | `cardioDetails`, `restoreInto` |
| 热量 | `domain/WorkoutCalorieCalculator.kt`, `domain/ExerciseCalculator.kt` | `combine`, `isComputable`, `estimate` |

关键 diff（含全部新增文件）：`docs/reports/B1.1代码审查关键diff.patch`

---

## 4. 新增测试全文路径

- `app/src/test/java/com/qingji/app/training/TrainingB11IntegrationTest.kt`（11 项，真实 Room + Repository）
- `app/src/test/java/com/qingji/app/training/TrainingViewModelB11Test.kt`（2 项，ViewModel 草稿 → 真实 Room）

覆盖：T01/T02/T03/T04/T06/T07/T08/T11/T12/T13/T15/T16。所有测试为实际运行，未跳过核心用例。

---

## 5. 测试与构建日志摘要

```
./gradlew :app:compileDebugKotlin --console=plain               -> BUILD SUCCESSFUL
./gradlew :app:testDebugUnitTest --tests "com.qingji.app.training.*" -> BUILD SUCCESSFUL (13 tests)
./gradlew :app:testDebugUnitTest --console=plain                 -> BUILD SUCCESSFUL
   122 suites / 798 tests / 0 failures / 0 errors / 3 skipped
./gradlew :app:assembleDebug :app:assembleRelease --console=plain -> BUILD SUCCESSFUL
   debug APK  38,545,405 bytes
   release APK 16,710,771 bytes
```

未执行：`connectedAndroidTest`（无设备/模拟器）。

---

## 6. 请重点复核的取舍

1. 为避免新增 migration，HIIT/FLEX/OTHER 使用 `workout_exercise.itemMetaJson` 存 WorkoutItem JSON，而 CARDIO 用 `workout_cardio_detail.rawInputJson`。请判断这是否违反“WorkoutCardioDetail 不得当通用垃圾桶”的边界。
2. 历史复制为计划时，力量目标优先取 `WorkoutSet.targetReps/targetWeightKg`，缺失才用实际值作“可复用建议”；非力量只复制原目标配置，不复制实际成绩。请判断是否满足“目标与实际严格区分”。
3. 编辑保存采用整包 delete+insert（同事务），因为编辑入口现在读入完整会话。请判断是否仍存在局部数据覆盖全量数据的风险。
4. 账本在整场可计算热量为 0 时删除旧行，避免 0 kcal 假成功；请判断这对历史补算逻辑是否安全。

---

## 7. 最终结论

有条件通过；未完成项见最终报告第 8 节。
