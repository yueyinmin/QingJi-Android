# 300 左右标准动作库扩充 · 数据审核报告 V1

> 分支：`feature/exercise-library-300`（基于 main `42046a4`）
> 本轮范围：**数据审核 + 增量导入机制**。按工单最后一句要求，**未把候选 JSON 写入生产数据库**。

---

## 〇、必须先说明的阻塞项

工单指定的两份数据文件在本机**没有找到**：

```
QingJi_Exercise_Library_200_Candidates.csv
QingJi_Exercise_Library_200_Candidates.json
```

已检索的位置：知识库 `OneDrive/Desktop/mulin/workbuddy/`、当前工作区、下载目录、
`~/.workbuddy` 附件/会话目录、系统临时目录、仓库目录，均无同名或近似文件；
按修改时间检索近 3 小时新增的 csv/json 也没有结果。

因此：

- **本轮完成了**：现有 38 个动作的全量审核、审核规则与稳定 UUID 规则落地、
  按 UUID 增量导入机制、中文分类显示名统一、单元测试与编译。
- **本轮没有完成**：262 个左右候选动作的逐条审核与导入 —— 没有数据就不审、不编、不猜。
- **验收项「最终动作数量 300 左右」本轮未达成**，不伪称通过。文件到位后按本报告第五节的流程继续。

---

## 一、现有 38 个内置动作审核（脚本实测，非手写结论）

校验方式：直接解析 `TrainingSeed.kt` 的 `SEEDS` 列表，逐项比对。

| 检查项 | 结果 |
| --- | --- |
| 解析到的 Seed 行数 | **38** |
| UUID 全局唯一 | ✅ 38/38 |
| 动作名称全局唯一 | ✅ 38/38（无重名） |
| 跨分类同名动作 | ✅ 无 |
| 一级分类键 | ✅ 全部落在 7 个英文键内，无第 8 类 |
| 空字段（名称/分类/主肌群/器械/要点） | ✅ 无 |
| 分类分布 | 胸 6 / 背 6 / 肩 5 / 手臂 5 / 腿 6 / 核心 5 / 拉伸 5 |
| 器械分布 | 徒手 15 / 哑铃 12 / 杠铃 5 / 器械 4 / 绳索 2 |
| 主肌群写法 | 24 种，存在 `/` 合并写法（如「股四头肌/臀」） |
| 现有 UUID 是否改动 | ✅ **一个都没改**（`ex-{cat}-{nn}` 原样保留） |

### 名称与器械变式检查

同一动作的器械变式已经用名称明确区分，不会撞全局唯一索引：

- 卧推：哑铃卧推 / 杠铃卧推 / 上斜哑铃卧推 / 器械推胸
- 弯举：杠铃弯举 / 哑铃交替弯举 / 锤式弯举
- 深蹲：杠铃深蹲 / 哑铃深蹲
- 俯卧撑：俯卧撑 / 窄距俯卧撑

**审核规则（新增动作同样适用）**：候选里凡是「中文名 trim 后与现有 38 个或彼此相同」的一律不通过；
器械、角度、姿势变式必须在名称里显式区分，禁止用「卧推（2）」这类后缀凑数。

---

## 二、字段差距分析（重要设计决定）

工单第二阶段要求每个动作具备 11 项资料。现有 `ExerciseEntity` 实际字段：

| 工单要求 | 现有实体 | 本轮处理 |
| --- | --- | --- |
| 中文名称 | `name` ✅ | 复用 |
| 英文名称 | ❌ 无 | **不新增字段**，放在独立的候选审核数据/映射表里 |
| 一级分类 | `category` ✅（英文键） | 复用；中文显示名改在 `CATEGORY_LABELS` |
| 二级肌群 / 主肌群 | `primaryMuscles` ✅ / `secondaryMuscles` ✅ | 复用（现有 38 个的 secondary 为空） |
| 辅助肌群 | `secondaryMuscles` ✅ | 复用 |
| 器械 | `equipment` ✅ | 复用 |
| 难度 | ❌ 无 | **不新增字段**，放独立数据表 |
| 动作步骤 | `steps` ✅（现有是一句话要点） | 复用，不编造详细步骤 |
| 常见错误 | `commonMistakes` ✅（现有为空） | 复用，不编造 |
| 图片资源路径 | ❌ 无 | **不写进 Room**，用 UUID → 资源的独立映射表（见第四节） |
| 图片来源及许可证 | ❌ 无 | 放 `THIRD_PARTY_NOTICES.md` / 映射表，不写进 Room |

**为什么不加字段**：`AppDatabase` 当前 `version = 11`，加字段就要写 Room 迁移，
而工单要求「数据库迁移和备份兼容」「不破坏训练历史关联」。
本轮采用「结构不动 + 独立映射表」的方案，与现有 `ExerciseMediaMapping` 思路一致。
**没有为了填充数据而编造任何图片来源或动作步骤**。

---

## 三、增量导入机制（本轮核心改动）

旧逻辑 `if (exerciseDao.count() == 0) insertAll(...)`：老用户升级后永远拿不到新增内置动作。

新逻辑（`TrainingRepository.ensureSeed()`）：

```kotlin
db.withTransaction {
    val missing = TrainingSeed.missingBuiltIns(exerciseDao.findAll())
    if (missing.isNotEmpty()) exerciseDao.insertAllIgnoringConflicts(missing)
}
```

`TrainingSeed.missingBuiltIns()` 是纯 Kotlin 函数，四条保护：

1. UUID 已存在 → 不补（收藏 `isFavorite`、更新时间、用户改过的字段一概不覆盖）；
2. 名称已被占用（多半是用户自建的同名动作）→ 跳过，避开 `exercise.name` 的**全局唯一索引**；
3. 名称用 `trim()` 归一，防全角空格之类的隐形重复；
4. 不删除、不更新任何行 —— 返回值只含「可安全新增」的行。

DAO 新增 `insertAllIgnoringConflicts()`（`OnConflictStrategy.IGNORE`）：即使并发下撞了主键或唯一索引，
也是直接跳过而不是 `REPLACE` 掉用户数据。整个差集写在一个 Room 事务里，批量导入要么全成要么全不成。

历史关联：`workout_exercise.exerciseNameSnapshot` 是只读快照，新增动作不 JOIN、不改历史，
训练记录与计划数据结构完全没动，`AppDatabase` 版本仍是 11。

**单元测试覆盖**（`TrainingSeedTest`，11 条）：空库补 38 条、已存在 0 条不覆盖收藏、
老用户只补差集、自建同名跳过、自建动作不参与差集、名称空白归一、
稳定 UUID 恒定性/唯一性/不撞现有 38 个、分类键与中文名一一对应。

---

## 四、图片映射清单（现有 38 条）

图片资源与 `ExerciseMediaMapping.kt` 目前在 **`feature/experience-polish-v2` 分支**（PR #6），
本分支基于 main，尚未包含。工单要求「每张图片必须与动作准确对应」「禁止错误匹配」，
所以这里先给出已审核过的 38 条对照，**本轮不新增任何图片匹配**：新增动作一律用统一占位图。

来源：Workout Guide（Bryl Lim，上游 Everkinetic），CC BY-SA 4.0，512×512 透明底 SVG 首帧。
逐条原始 attribution 随包保存在 `assets/workout-guide/ATTRIBUTION.json`。

| UUID | 中文名称 | slug | 匹配结论 |
| --- | --- | --- | --- |
| ex-chest-01 | 哑铃卧推 | dumbbell-bench-press | 直接匹配 |
| ex-chest-02 | 杠铃卧推 | bench-press | 直接匹配 |
| ex-chest-03 | 俯卧撑 | push-up | 直接匹配 |
| ex-chest-04 | 上斜哑铃卧推 | incline-dumbbell-press | 直接匹配 |
| ex-chest-05 | 器械推胸 | machine-chest-press | 直接匹配 |
| ex-chest-06 | 哑铃飞鸟 | dumbbell-fly | 直接匹配 |
| ex-back-01 | 引体向上 | pull-up | 直接匹配 |
| ex-back-02 | 高位下拉 | lat-pulldown | 直接匹配 |
| ex-back-03 | 坐姿划船 | seated-row | 直接匹配 |
| ex-back-04 | 单臂哑铃划船 | one-arm-dumbbell-row | 直接匹配 |
| ex-back-05 | 杠铃划船 | barbell-row | 直接匹配 |
| ex-back-06 | 硬拉 | deadlift | 直接匹配 |
| ex-shoulder-01 | 哑铃肩推 | seated-dumbbell-press | **待人工核对**（坐姿 vs 未限定姿势） |
| ex-shoulder-02 | 哑铃侧平举 | lateral-raise | 直接匹配 |
| ex-shoulder-03 | 面拉 | face-pull | 直接匹配 |
| ex-shoulder-04 | 阿诺德推举 | arnold-press | 直接匹配 |
| ex-shoulder-05 | 俯身反向飞鸟 | bent-over-rear-delt-raise | 直接匹配 |
| ex-arm-01 | 杠铃弯举 | ez-bar-curl | **待人工核对**（EZ 杠 vs 直杆） |
| ex-arm-02 | 哑铃交替弯举 | bicep-curl | **待人工核对**（普通弯举 vs 交替） |
| ex-arm-03 | 绳索下压 | tricep-pushdown | 直接匹配 |
| ex-arm-04 | 窄距俯卧撑 | diamond-push-up | **待人工核对**（钻石 vs 普通窄距） |
| ex-arm-05 | 锤式弯举 | hammer-curl | 直接匹配 |
| ex-leg-01 | 杠铃深蹲 | squat | 直接匹配 |
| ex-leg-02 | 哑铃深蹲 | goblet-squat | **待人工核对**（高脚杯 vs 自然持铃） |
| ex-leg-03 | 罗马尼亚硬拉 | dumbbell-romanian-deadlift | 直接匹配 |
| ex-leg-04 | 腿举 | leg-press | 直接匹配 |
| ex-leg-05 | 箭步蹲 | forward-lunge | 直接匹配 |
| ex-leg-06 | 提踵 | calf-raise | 直接匹配 |
| ex-core-01 | 平板支撑 | plank | 直接匹配 |
| ex-core-02 | 卷腹 | crunch | 直接匹配 |
| ex-core-03 | 死虫式 | dead-bug | 直接匹配 |
| ex-core-04 | 俄罗斯转体 | russian-twist | 直接匹配 |
| ex-core-05 | 悬垂举腿 | hanging-leg-raise | 直接匹配 |
| ex-stretch-01 | 胸部拉伸 | doorway-chest-stretch | 直接匹配 |
| ex-stretch-02 | 背阔肌拉伸 | childs-pose | **待人工核对**（儿童式 vs 专项背阔肌拉伸） |
| ex-stretch-03 | 股四头肌拉伸 | standing-quad-stretch | 直接匹配 |
| ex-stretch-04 | 腘绳肌拉伸 | hamstring-stretch | 直接匹配 |
| ex-stretch-05 | 髋屈肌拉伸 | kneeling-hip-flexor-stretch | 直接匹配 |

---

## 五、新增动作审核规则与 UUID 规则（已落地，待数据）

### 审核规则（候选文件到位后逐条执行）

1. 中文名非空、`trim()` 后与现有 38 个及同一批候选**全局唯一**（避开唯一索引）；
2. 一级分类必须是 7 个英文键之一，且与动作主肌群相符（如「杠铃弯举」不能进 `leg`）；
3. 器械变式必须在名称里显式区分，禁止「（2）」「新」这类占位后缀；
4. 主肌群 / 辅助肌群不得与分类明显冲突；没把握的进「待核对」而不是硬塞；
5. 英文名、难度、动作步骤、常见错误、图片来源/许可证**没有可靠来源就留空并标注「未审核」**，不编造；
6. 图片只在能确认与动作准确对应时才建立映射，否则统一占位；
7. 审核通过的才固定 UUID，候选 JSON 里的临时 ID 一律不作数。

### 稳定 UUID 规则

`TrainingSeed.stableUuid(englishName)`：英文名 `trim().lowercase()` + 空白归一 →
SHA-256 前 6 字节 → 12 位十六进制，前缀 `ex2-`，共 16 字符。

- 名称不变则 UUID 永不变；换分类、换排序、重跑导入都不会漂移；
- `ex2-` 与现有 `ex-` 前缀隔离，数学上不会撞现有 38 个；
- 单元测试已验证同名恒定、大小写/空白归一、300 个不同名称无碰撞。

### 下一轮流程

候选文件到位 → 跑审核脚本产出「通过 / 待核对 / 驳回」三张清单 → 主人确认 →
把通过项生成 `ex2-*` UUID 写进 `TrainingSeed.SEEDS` → `ensureSeed()` 自动增量补库 →
回归测试（旧用户升级、收藏不丢、自建动作保留、历史记录正常）。

### 界面分类统一（第四阶段，已完成最小改动）

动作库胶囊 `MuscleCategoryTabs`、动作详情「分类」行都直接读
`TrainingSeed.CATEGORY_LABELS`，所以只改这一处显示名即全站生效：
**全部 / 胸部 / 背部 / 肩部 / 手臂 / 腿部 / 核心 / 拉伸**。
一级分类仍是 7 个英文键，数量没有增加，视觉规范未动。

### 二级筛选接口设计（只设计，本轮不接 UI）

工单要求「增加二级筛选能力的接口设计，但本轮不要擅自大幅重做页面」。
建议下一轮引入一个纯数据筛选条件，不碰页面结构：

```kotlin
data class ExerciseFilter(
    val category: String? = null,        // 一级：7 个英文键之一
    val secondaryMuscle: String? = null, // 二级：主/辅肌群
    val equipment: String? = null,       // 二级：器械
    val difficulty: String? = null,      // 二级：难度（数据到位后才有意义）
    val query: String = ""               // 名称/英文名模糊搜索
)
```

`ExerciseLibraryScreen` 演进为接收 `filter: ExerciseFilter` 与
`onFilterChange: (ExerciseFilter) -> Unit`，筛选仍在现有 `shown` 那一行做纯内存
`filter`，不新增 Room 查询、不改 DAO、不加页面。本轮**不落地这段代码**。

---

## 六、本轮验收结果

| 验收项 | 结果 |
| --- | --- |
| 现有 38 个 UUID 不变 | ✅ 已核对 |
| 新增动作没有重复名称 | ✅ 现有 38 个无重复；候选未导入，规则与测试已就绪 |
| 用户自建动作完整保留 / 收藏不丢失 | ✅ 增量逻辑只插缺失 UUID，单测覆盖 |
| 旧版用户能获得新增动作 | ✅ 机制已改（`count()==0` → 按 UUID 差集），待候选数据到位后生效 |
| 历史训练记录正常 / 备份兼容 | ✅ 未改表结构、未改 Room version 11、历史只读 nameSnapshot |
| `./gradlew test` | ✅ **367 用例 / 364 通过 / 0 失败 / 3 跳过** |
| `./gradlew assembleDebug` | ✅ BUILD SUCCESSFUL |
| 最终动作数量 300 左右 | ❌ **本轮未达成**：候选文件缺失，且工单明确禁止未经审核写入 |
| 七类数量符合规划 | ⚠️ 现有 38 个分布已核对；新增规划待候选数据 |

### 顺带修掉的基线债务

main `42046a4` 上 `AppChangeLogTest` / `UpdateNoticeStoreTest` 共 6 条断言仍写死
`versionCode 111 / v1.1.0`（v1.2.0 合并更新日志时漏改），在本分支同步到 `120 / 1.2.0`，
否则 `./gradlew test` 从一开始就是红的。这不是本工单引入的问题。
