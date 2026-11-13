# 轻记 · 训练中心 P0 — 评估结论与开发决策记录

> 版本：v1.0（2026-11-13） · 分支：`feature/training-center`（从 main `8978f6f` 创建）
> 依据：《轻记-训练中心-需求手册》《轻记-训练中心-开发手册》《轻记-新窗口交接包-v1.0.9》
> 本文与两份手册冲突时，**以本文为准**（两份手册里的错误已在下面逐条列明）。

---

## 一、评估结论（能不能做）

| 维度 | 结论 | 依据 |
| --- | --- | --- |
| 技术 | **可行** | 6 张全新表、不动任何旧列；组件/主题/迁移测试/278 单测基线都在；`main` 干净 |
| 老用户数据 | **零风险（已补验证）** | 9→10 只有 CREATE TABLE + CREATE INDEX；迁移测试新增「旧数据原样读回」断言 |
| 内容素材 | **不就绪，但已绕开** | 《健身 Excel 超级套表》不在知识库目录、未完成安全/授权审核 → P0 改用最小内置动作清单 |
| 交付流程 | **不推送** | GitHub token 待主人换新；「v1.1.0」命名已被工程加固批次占用，发版号另定 |

**实际交付范围：P0 训练日记 MVP 全链路可运行**（记录 → 组数据 → 上次成绩 → 历史 → 日历 → 总结 → 编辑/删除 → 备份 → 清除数据）。P1（计划/动作库详情/个人最佳）、P2（有氧心率/拉伸/恢复/计算器）、P3（AI）按手册约定**待主人确认后再开工**。

---

## 二、6 个待拍板问题的定稿

| # | 问题 | 定稿 | 理由 / 代价 |
| --- | --- | --- | --- |
| 1 | P0 动作清单从哪来 | 内置 36 个通用动作（名称 + 肌群 + 器械 + 一句话要点），`TrainingSeed.kt`，首启灌库；允许自建动作 | Excel 是内容素材不是处方，红线 4 要求人工审核；P0 先让主链路跑通。代价：图文详情、7 类模板留 P1 |
| 2 | 杀进程怎么恢复 | `SharedPreferences`（`WorkoutDraftStore`）存整份草稿 JSON：会话 uuid、开始时间戳、暂停累计、动作与每组数据 | 冷启动能同步读、不留「半成品数据库行」污染历史/日历/备份。代价：草稿不进备份（本来就是没保存的内容） |
| 3 | 力量训练算不算热量 | **P0 不计入**，不写 `exercise_log`，不碰现有 50% 运动补偿通道 | 没有可信的力量训练 kcal 估算口径；贸然写一条会把饮食额度算歪。有氧仍走原「记录运动」流程（P2 再补心率字段） |
| 4 | kg/lb 偏好存哪 | 训练页顶部胶囊切换，偏好存 `WorkoutDraftStore`（纯显示层，库里永远 kg） | 不复用 `UserProfile.unit`（那是体重单位，混用会互相改）；不进备份，换机回默认 kg 无损失 |
| 5 | 备份版本 / 清空语义 | **升 version 3**，新增 `training` 段；`version >= 3` 才整包覆盖；v1/v2 老备份**一行训练数据都不动** | 手册写「1→2」是错的（见第三节）；老备份导出时还没有训练模块，清掉新数据属于无故丢数据 |
| 6 | 版本号 / token | **本次不发版、不推送**；建议下个发版号 `1.0.10`（versionCode 110） | token 换新是交接包未闭环 P0；`v1.1.0` 已被工程加固批次占用 |

---

## 三、两份手册的硬伤（已修正，别再照旧文档写）

| # | 手册原文 | 代码实际 | 本次怎么修的 |
| --- | --- | --- | --- |
| 1 | 「备份 JSON 带 `version: Int = 1`，备份升 version 2」 | `BackupFormat.VERSION` **早就是 2**（v1.0.6 起补了五段）；`ParsedBackup.version=1` 只是读旧文件的兜底默认值 | 升到 **3**；`ParsedBackup` 加 `training` / `hasTraining`；`isEmpty` 把训练段算进去（否则只含训练的备份被判空）；`BackupManager` 导出/恢复/摘要全接上 |
| 2 | 「把 MIGRATION_9_10 追加进 `ALL_MIGRATIONS`」 | `get()` 里 `.addMigrations(...)` 是**另一份手写死列表**，且项目禁用 `fallbackToDestructiveMigration` | **两处都加**；SQL 从 KSP 生成的 `app/schemas/10.json` 的 `createSql` 原样抄（DEFAULT/NOT NULL 逐字一致）；迁移测试扩到 1→10。只改一处 → 测试过、真机升级打不开 |
| 3 | P0「从动作库选，支持搜索」，但动作库排 P1、Excel 素材未就绪 | 没有稳定动作 ID 就查不到「上次成绩」 | P0 内置最小清单 + 自建动作；动作选择弹层支持搜索与 7 类肌群筛选 |

**另外补上的 4 个手册没提的口子：**

| 口子 | 处理 |
| --- | --- |
| `restoreInto` 对记录类一律无条件清表，手册却照抄了 profile/AI 的「缺段保留」语义 | 训练段按版本号判断：v3 覆盖、v1/v2 不动，和「导入=覆盖」整体语义一致 |
| 新增 6 表没进「清除记录 / 恢复出厂」 | `clearRecords()` 清会话+明细并作废草稿（动作库/计划属配置，保留）；`factoryReset()` 清 6 表 + 草稿 + 单位偏好 |
| `showDiaryTab` 可在设置里关掉 → 训练日记入口消失 | 新增全屏路由 `training_history`（我的 → 训练记录），关掉日记栏也进得去 |
| ＋面板真实文件是 `ui/screen/record/AddRecordSheet.kt`；没有现成通用月历 | 两个入口接在 AddRecordSheet；新写 `TrainingCalendarCard`（周一起始、训练日打点）；「极限减脂三档」代码里不存在（只有 lose/keep/gain），属独立需求，不混进本次 |

**表名统一**：需求手册写 `plan / plan_day / workout_set`，开发手册写 `training_plan / training_plan_day` 且组打包成 `setsJson`。**一律以开发手册为准**，6 表为 `exercise / training_plan / training_plan_day / plan_exercise / workout_session / workout_exercise`（`workout_exercise.setsJson` 存一组 JSON）。

---

## 四、实际落地清单

| 层 | 文件 |
| --- | --- |
| 数据库 | `entity/TrainingEntities.kt`（6 表）、`dao/TrainingDaos.kt`（6 DAO）、`AppDatabase.kt`（version 10 + MIGRATION_9_10 + 两处注册 + 6 个 abstract DAO）、`schemas/10.json`（KSP 生成） |
| 备份 | `backup/TrainingBackup.kt`、`backup/ParsedBackup.kt`、`backup/BackupManager.kt`、`domain/BackupFormat.kt` |
| 草稿/单位 | `prefs/WorkoutDraftStore.kt` |
| 内容/计算 | `data/local/TrainingSeed.kt`、`domain/TrainingMath.kt` |
| 仓库 | `repository/TrainingRepository.kt`、`repository/QingJiRepository.kt`（两档清除） |
| ViewModel | `ui/viewmodel/TrainingViewModel.kt` |
| 界面 | `ui/screen/training/`：`TrainingComponents.kt`、`TrainingDiaryContent.kt`、`TrainingActiveScreen.kt`、`TrainingHistoryScreen.kt` |
| 接线 | `Destinations.kt`、`QingJiAppRoot.kt`、`HomeScreen.kt`、`DiaryScreen.kt`、`MineScreen.kt`、`record/AddRecordSheet.kt` |
| 测试 | `TrainingMathTest`、`TrainingBackupParsingTest`、`TrainingBackupRestoreTest`、`DatabaseMigrationTest`（1→10 + 旧数据保留） |

**入口（全部真实可用，无摆设）**：首页「今日训练」卡 → 训练日记；日记页顶部「心情日记 / 训练日记」；＋面板「开始训练」「记录有氧」（后者直跳现有 `ExercisePicker`）；我的 → 训练管理 → 训练记录 / 开始训练。

---

## 五、明确的边界（没做的）

- 不做训练计划模板、每周安排、动作详情图文、动作收藏、个人最佳 → P1。
- 不做有氧心率/主观强度、拉伸图示、恢复记录、健身计算器 → P2。
- 不做 AI 总结/趋势图/饮食-训练关联 → P3。
- 不做 3D 解剖、视频课、社区、第六个底部导航、深饱和健身风。
- 训练消耗**不进**当日饮食额度；`exercise_log` 一行没改，营养算法零改动。
- 所有估算类数字（1RM 尚未开放、训练量）界面带「估算/仅供参考」文案。

---

## 六、发版前待办（需主人确认）

1. GitHub token 作废换新，替换 `GitHub信息.txt` 与 `local.properties`（本机配置不进仓库）。
2. 发版号与 `versionCode`：**已在分支预置 `1.0.10 / 110`**（改 `app/build.gradle.kts` 两行即可换号；换号时同步改 `AppChangeLog` 里对应条目的 versionCode）。**仍未发版、未推送。**
3. 主人在真机/模拟器验收 P0 全流程（尤其：杀进程恢复计时、上次成绩带出、导入 v2 旧备份不丢训练数据）。
4. 合并 `feature/training-center` 到 main、打 tag、发 Release。
5. 旧手册按本文修订；三条已合并的旧分支是否清理。

---

## 七、版本更新提示（2026-11-13 追加需求）

**需求**：App 更新后弹一个弹窗，列出本次更新项，点「我知道了」关闭；这些更新项同时长期保留在「设置 → 版本信息」里。

| 项 | 实现 |
| --- | --- |
| 唯一数据源 | `domain/AppChangeLog.kt`：按版本倒序的 `Release(versionName, versionCode, date, items)`，加新版本只改这一个文件 |
| 弹窗 | `ui/components/UpdateNoticeDialog.kt`：Material3 单按钮，视觉沿用 `AppDialog`（20dp 圆角、白卡、绿主按钮），只有「我知道了」，没有「取消」 |
| 只弹一次 | `data/prefs/UpdateNoticeStore.kt` 记「上次点过我知道了的 versionCode」；全新安装静默记下不弹；升级后弹一次；连跳几个版本一次列全；**只有点按钮才写已读**，中途杀进程回来还会弹 |
| 触发位置 | `MainActivity.setContent`：开屏动画之后、主界面之上，避免新用户引导期被打断 |
| 版本信息 | `SettingsScreen` 的 `DialogKind.VERSION` 文本末尾追加 `AppChangeLog.versionInfoText()`，弹层本来就可滚动，能回看全部历史更新 |
| 测试 | `AppChangeLogTest`（跨版本/同版本/文案一致）、`UpdateNoticeStoreTest`（全新安装/升级/已读后） |

**注意**：弹窗内容依赖 `versionCode` 真的变大。本次已从 109 → 110（versionName 1.0.9 → 1.0.10），所以从 1.0.9 覆盖安装的设备会看到「v1.0.10 更新啦」；全新安装的设备不会弹。

**实测**（模拟器 `Medium_Phone_API_37.0`，`uiautomator dump` + `input tap`）：全新安装不弹；把 `app_version_notice.xml` 的已读版本改成 109 重启 → 弹窗出现且 6 条更新项齐全；点「我知道了」后记录变 110，重启不再弹。
