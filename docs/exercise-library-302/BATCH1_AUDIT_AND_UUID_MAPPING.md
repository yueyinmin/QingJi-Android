# 轻记 Android｜Workout Guide 302 动作库替换 — 第一批检查报告

日期：2026-11-17  
范围：只读审计 + 旧 38 条 UUID 映射 + 中文名审核。  
未改动：数据库、迁移、种子数据、DAO、Repository、UI、备份逻辑。  
结论：**旧 38 条可 100% 精确映射到上游 302 条，不需要靠名称相似度猜测；但开工前需要你确认 2 个口径问题（见文末）。**

---

## 一、现状审计（代码事实）

### 1. 数据库版本与结构

| 项目 | 事实 | 位置 |
|---|---|---|
| Room 版本 | `version = 11`，`exportSchema = true` | `AppDatabase.kt:131-133` |
| 迁移 | 1→2 … 10→11 全套齐全，无 11→12 | `AppDatabase.kt:176-506` |
| 迁移清单 | `ALL_MIGRATIONS` 按序列出，`by lazy` | `AppDatabase.kt:488-495` |
| 破坏性迁移 | 训练库**没有** `fallbackToDestructiveMigration()` | `AppDatabase.kt:590-595` |
| schemas | `app/schemas/.../1.json` ~ `11.json` 齐全 | 目录实测 |
| 训练表 | 训练 6 表在 9→10 用 `CREATE TABLE IF NOT EXISTS` 建，无 foreign key | `AppDatabase.kt:465-486` |

训练 6 表：`exercise`、`training_plan`、`training_plan_day`、`plan_exercise`、`workout_session`、`workout_exercise`。

### 2. ExerciseEntity 与引用关系

- `ExerciseEntity`：`uuid` 是主键；`name` 上有 **UNIQUE 索引**；字段共 12 个（见 `TrainingEntities.kt:23-40`）。
- 无外键、无级联删除 ⇒ 删除动作不会连带清历史（这是保护特性，必须保持）。
- 引用动作 UUID 的只有两个普通字符串列：
  - `workout_exercise.exerciseUuid`（`TrainingEntities.kt:100`）
  - `plan_exercise.exerciseUuid`（`TrainingEntities.kt:67`）
- 历史名称快照独立保存：`workout_exercise.exerciseNameSnapshot`（:101）、`plan_exercise.exerciseNameSnapshot`（:68）。

### 3. 当前种子与导入

| 项目 | 事实 |
|---|---|
| 当前条数 | **200 条**（38 条 `ex-*` + 162 条 `candidate-*`），不是 38 条 |
| 种子位置 | `TrainingSeed.kt:55-256`，`builtIns()` 在 :258-275 |
| 导入入口 | `TrainingRepository.ensureSeed()` :57-62，按 UUID 求差集 |
| 冲突策略 | `insertMissing` = `OnConflictStrategy.IGNORE`（`TrainingDaos.kt:44-45`） |
| 危险接口 | `insertAll` = `REPLACE`（`TrainingDaos.kt:36-37`），只给自建动作/备份 recovery 用，**种子绝不能走它** |
| 用户数据标记 | `isBuiltIn`、`isFavorite`、`updatedAt`；自建 UUID 前缀 `user-` |
| 触发点 | 仅 `TrainingViewModel.init`（:64-67）；进计划页不会触发补种 |

### 4. 上游 302 条原始数据核验

数据源：`bryllim/workout-guide` 的 `packages/workout-guide/manifest.json` + GitHub 文件树。

| 校验项 | 结果 |
|---|---|
| 动作总数 | 302 ✅ |
| slug / id / 英文名唯一 | 302 / 302 / 302 ✅ |
| 帧数 | 302 × 3 = **906** ✅ |
| 上游文件树中的 SVG | **906** ✅，manifest 里 906 个 path 全部能在文件树中找到 |
| 尺寸格式 | 全部 512×512 SVG ✅ |
| primaryMuscle | 20 种（Core 45、Glutes 38、Quads 35、Chest 26…），无 `category` 字段，需按工单规则映射七类 |
| secondaryMuscles | 22 种，包含 Cardio / Grip / Groin 等，必须原样保留 |
| isStretch | true 14 条，false 288 条 |
| exerciseType | weight_reps 136 / bodyweight_reps 114 / duration 39 / distance_duration 10 / assisted_bodyweight 3 |
| attribution | 302 条 creator = Bryl Lim，license = CC BY-SA 4.0 |
| Everkinetic 来源 | **76 条**有 `source.name = Everkinetic`，其余不得泛化署名 ✅ |
| steps / commonMistakes | 上游无字段 ⇒ 落地时必须留空，不编造 |

### 5. 备份与模板的真实风险

| 风险 | 事实 | 位置 |
|---|---|---|
| 旧备份恢复会清库 | `clearAll()` 6 张表后 `insertAll(备份的动作)`，旧备份可把新动作库整包覆盖 | `BackupManager.kt:508-525` |
| 名称唯一索引冲突 | 用户自建动作若与 302 条某条同名，`IGNORE` 会静默跳过该内置动作 | `TrainingEntities.kt:25`、`TrainingDaos.kt:44` |
| 模板按名字找 UUID | `activatePlan` 按中文名查 uuid，名字一变就写空串；快照仍按原名保留 | `TrainingRepository.kt:149-158` |
| PB 按快照分组 | `PersonalBestCalculator.kt:40` 只读 `exerciseNameSnapshot` | 改中文名需评估 |
| 媒体映射按 UUID 硬编码 | `ExerciseMediaMapping.kt:65` 起，老 UUID 不能变 | 已纳入保护 |
| ExercisePreset 独立 | 另一张表、另一套 DAO；`ExercisePickerScreen.kt` 不用训练库 | 本工单不涉及 ✅ |

---

## 二、旧 38 条 → 新库 UUID 映射

完整表格见同目录 `old38_uuid_mapping.csv`。

判定依据只采用**代码里现存的显式 slug 对应**（`ExerciseMediaMapping.kt` 中 38 条 `ex-*` → slug），再用 slug 与上游 manifest 做精确匹配。没有使用中文名或英文名相似度自动匹配。

- 精确命中：**38 / 38**
- 需要人工确认对应关系：**0**
- 新 UUID：老 38 条全部**沿用原 `ex-*` UUID**；其余 264 条采用稳定 ID `wg:<slug>`。

### 中文名审核：8 条建议修订

这些动作的 slug 对应是确定的，只是旧中文名与上游英文名语义不一致。旧名会影响模板关联和 PB 分组，所以单独列出，不擅自改。

| old_uuid | 旧中文名 | 上游英文名 | 建议 |
|---|---|---|---|
| ex-back-03 | 坐姿划船 | Seated Cable Row | 坐姿绳索划船 |
| ex-shoulder-01 | 哑铃肩推 | Dumbbell Seated Shoulder Press | 坐姿哑铃肩推 |
| ex-arm-01 | 杠铃弯举 | EZ-Bar Curl | EZ 杠弯举 |
| ex-arm-02 | 哑铃交替弯举 | Bicep Curl | 哑铃弯举 |
| ex-arm-04 | 窄距俯卧撑 | Diamond Push-up | 钻石俯卧撑 |
| ex-leg-02 | 哑铃深蹲 | Goblet Squat | 高脚杯深蹲 |
| ex-leg-03 | 罗马尼亚硬拉 | Dumbbell Romanian Deadlift | 哑铃罗马尼亚硬拉 |
| ex-stretch-02 | 背阔肌拉伸 | Child's Pose | 婴儿式 |

其余 30 条建议保留旧中文名。

---

## 三、开工前必须确认的 2 个口径

### 问题 1：162 条 `candidate-*` 算不算「必须保留的用户数据」？

- 事实：它们是今天刚编译出来的 v1.2.1 里才有的种子；仓库代码有，但尚未交付用户。已发布用户基线仍是 38 条。
- 选项 A（建议）：按「未发布」处理。最终内置动作恰好 302 条：38 条保留 `ex-*`，264 条用 `wg:<slug>`；`candidate-*` 不再作为独立动作存在。
- 选项 B：如果你已经把 v1.2.1 装到手机并收藏过其中动作，则要把 `candidate-*` 也映射保留（会与 302 条有重复动作，内置总数不再是 302）。

### 问题 2：上面 8 条中文名，改还是不改？

- 选项 A（建议）：按上游英文名修订，模板和 PB 快照逻辑同步兼容新旧名。
- 选项 B：全部保留旧中文名，避免任何历史/PB 语义变化。

---

## 四、下一批执行前的硬约束（已锁定）

1. 灌库只能走 UUID 差集 + `IGNORE`，绝不用 `REPLACE`。
2. `ex-*` 老 UUID 一个都不能变。
3. 302 条之间中文名必须唯一；与用户自建同名时内置动作让路，不能覆盖用户数据。
4. 备份恢复路径必须改：旧备份不得清掉新动作库，历史记录仍按原 UUID + 名称快照恢复。
5. 不加外键级联；不加清空表的迁移。
6. `steps` / `commonMistakes` 留空，不生成训练指导。
7. 76 条 Everkinetic 来源逐条署名，其余只署 Bryl Lim / CC BY-SA 4.0。
8. 不动 `ExercisePreset` / `ExercisePicker`。

### 确认结果（2026-11-17）

- 问题 1：**按未发布处理**。`candidate-*` 于 2026-11-16 17:16 随提交 `d1a12b7` 加入代码，v1.2.1 包 18:01 才编译完成，未交付用户。不作为独立动作保留。
- 问题 2：**按英文名修订** 8 条中文名；模板匹配与 PB 快照逻辑需兼容新旧名。

**状态：第一批完成，口径已确认，进入第二批。**
