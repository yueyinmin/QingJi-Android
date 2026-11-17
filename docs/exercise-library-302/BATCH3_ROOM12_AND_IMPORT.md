# 轻记 Android｜Workout Guide 302 动作库替换 — 第三批检查报告

日期：2026-11-17  
范围：Room 12 迁移 + 302 条种子 + 安全导入。  
结论：**代码与测试已写完；尚未编译，schema 12.json 需云端生成后回写。**

---

## 一、改动清单

| 文件 | 改动 |
|---|---|
| `entity/TrainingEntities.kt` | `ExerciseEntity` 新增 6 列：`englishName` / `slug` / `exerciseType` / `isStretch` / `assetPath` / `attribution`；新增 `LibraryMeta` 表 |
| `AppDatabase.kt` | version 11 → 12；注册 `LibraryMeta` 与 `LibraryMetaDao`；新增 `MIGRATION_11_12`；加入 `ALL_MIGRATIONS` |
| `MIGRATION_11_12` | 6 条 `ALTER TABLE ... ADD COLUMN` + 1 条 `CREATE TABLE library_meta`，无删表/删列/REPLACE |
| `TrainingDaos.kt` | 新增 `LibraryMetaDao`（读取/写入导入版本） |
| `TrainingSeed.kt` | 整表替换为 302 条 Workout Guide 数据；38 个 `ex-*` + 264 个 `wg:<slug>` |
| `TrainingSeedImportPlanner.kt` | 纯 Kotlin 导入计划器：差集、用户字段保护、同名变式、版本控制 |
| `TrainingRepository.ensureSeed()` | 事务内执行导入计划 + 写版本标记；版本一致时零写入 |
| `TrainingSeedIncrementalTest.kt` | 更新为 302 条断言、收藏/自建/同名冲突/幂等测试 |
| `AppDatabaseMigrationTest.kt` | 新增 11→12 迁移测试：新列、版本表、老收藏/自建/时间戳保留 |
| `.github/workflows/build-apk.yml` | 解压 302 素材包；编译后把 `schemas/12.json` 挂到 Release 供回写 |

---

## 二、数据来源优先级（明确规则）

| 类别 | 字段 | 优先级 |
|---|---|---|
| 用户字段 | `uuid` / `isBuiltIn` / `isFavorite` / `createdAt` / `updatedAt` / `steps` / `commonMistakes` | 数据库已有值优先，种子不覆盖 |
| 动作库字段 | `name` / `englishName` / `slug` / `category` / 肌群 / 器械 / 类型 / 拉伸标记 / 素材路径 / 授权 | Workout Guide 清单为准 |
| 自建动作 | `isBuiltIn=false` 且 uuid 命中 | 整条跳过，绝不覆盖 |
| 名称冲突 | 自建动作占用了新动作中文名 | 内置动作改用「（动作库N）」变式名，保住用户原名 |

---

## 三、数据保护校验点

- 不清 `exercise` / `workout_session` / `workout_exercise` / `training_plan` / `training_plan_day` / `plan_exercise`。
- 不加外键、不加级联删除。
- `insertMissing` 仍为 `IGNORE`，没有改成 `REPLACE`。
- 历史名称快照字段一行未动。
- 302 条 UUID 写死，无随机生成。
- 老 38 条 UUID 全部保留，按 slug 精确命中。

---

## 四、待完成

1. 云端编译生成 `app/schemas/.../12.json` 并回写仓库。
2. 第四批：本地 SVG 路径改成 `exercises/<category>/<slug>/frame-N.svg`，动画与失败兜底。
3. 第五批：模板/PB 旧中文名兼容、UI 改造。
4. 第六批：备份兼容与全量测试、真机验收。

**状态：第三批代码完成，等待编译验证。**
