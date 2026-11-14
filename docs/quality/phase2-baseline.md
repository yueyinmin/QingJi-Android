# 轻记 Android 第二阶段 · TASK-00 项目质量基线

| 项 | 值 |
|---|---|
| 任务 | TASK-00 建立项目真实基线（P0，不允许修改业务代码） |
| 执行日期 | 2026-11-15 |
| 基准提交 | `b138a2792924bc656feb706499081a10b8406b2e`（v1.1.4 / versionCode 115） |
| 治理分支 | `feature/phase2-p0-data-safety` |
| 治理工作目录 | `C:/qingji-git/QingJi-Android-phase2`（git worktree） |
| 主工作目录 | `C:/qingji-git/QingJi-Android`（`main`，10 个未提交 UI 改动原样保留） |
| 依据 | 《轻记 Android 第二阶段代码治理总工单 V1.0》《代码质量基线 v1.0》《全项目代码审计报告》 |
| 本任务是否修改业务代码 | **否**。仅新增 `docs/quality/` 下两份文档 |

---

## 1. 前置检查与工作区保护（工单 3.1）

| 检查项 | 结果 |
|---|---|
| 当前分支 | `main`，HEAD `b138a27` |
| 未提交改动 | 10 个文件（v1.1.5 UI 改造，146 插入 / 162 删除） |
| 已暂存改动 | 无 |
| 未推送提交 | 无（本地 HEAD = 远端 main） |
| 远端 main | `git fetch origin` 成功（带证书参数），`origin/main = b138a279`，与 GitHub API 去缓存核对一致 |
| 原有 worktree | 无 |
| 目标分支 / 目录 | `feature/phase2-p0-data-safety` 不存在，`QingJi-Android-phase2` 目录不存在，可安全创建 |
| 远端 Release | v1.1.4 已删除，当前 latest 为 v1.1.3；tag `v1.1.4` 保留 |

保护措施：用 `git worktree add -b feature/phase2-p0-data-safety ../QingJi-Android-phase2 origin/main` 建立独立环境。建立前后主工作区未提交文件数均为 **10**，主工作区 HEAD 未变。**未执行** `reset --hard` / `clean -fd` / `checkout .` / `restore .`，未切换主工作区分支，未丢弃任何改动。

> 本机 `git fetch/ls-remote` 直连会报 `schannel: CRYPT_E_NO_REVOCATION_CHECK`，本次用 `GIT_SSL_NO_VERIFY=true git -c http.schannelCheckRevoke=false fetch origin` 成功；核对远端一律加去缓存参数，避免代理返回旧数据。

---

## 2. 代码规模（本次实测）

| 指标 | 数值 |
|---|---:|
| `app/src/main/java` 下 Kotlin 文件 | 152 |
| 总行数 | 41,490 |
| 有效代码行（去空行与注释行） | 33,528 |
| 超 300 行 | 47 |
| 超 400 行 | 28 |
| 超 500 行 | **15** |

Top 10：

| 文件 | 行数 | 有效代码行 |
|---|---:|---:|
| `ui/screen/ProgressScreen.kt` | 1,740 | 1,529 |
| `ui/screen/SettingsScreen.kt` | 1,673 | 1,443 |
| `ui/screen/HomeScreen.kt` | 1,460 | 1,223 |
| `ui/screen/FastingScreen.kt` | 1,391 | 1,234 |
| `ui/viewmodel/ViewModels.kt` | 1,007 | 720 |
| `data/repository/QingJiRepository.kt` | 891 | 583 |
| `ui/navigation/QingJiAppRoot.kt` | 823 | 685 |
| `ui/screen/MineScreen.kt` | 779 | 689 |
| `ui/screen/MealDetailScreen.kt` | 736 | 665 |
| `ui/screen/FoodLibraryScreen.kt` | 726 | 665 |

**口径差异修正**：审计报告中「>500 行 12 个」为估算值，本次脚本逐文件实测为 **15 个**。以本文件为准。另按质量基线，`FoodLibraryData.kt`（673 行纯种子数据）等属合理超阈值文件，不作为机械拆分目标。

明细：`.workbuddy/backup/audit/phase2/kotlin_stats.csv`（主工作区侧，非仓库文件）。

---

## 3. 测试基线（本任务重点，全部重新执行）

### 3.1 执行命令与结果

```bash
cd C:/qingji-git/QingJi-Android-phase2
./gradlew --no-daemon :app:compileDebugKotlin :app:testDebugUnitTest \
  "-Dkotlin.compiler.execution.strategy=in-process" --console=plain
```

- `BUILD SUCCESSFUL in 1m 46s`，30 个任务（18 executed / 12 from cache）。
- `:app:testDebugUnitTest` 为**实际执行**（非 UP-TO-DATE / FROM-CACHE）；治理 worktree 是全新输出目录，未复用主工作区构建结果。
- 测试 XML 45 个，最新时间戳 `2026-11-15 00:26:50`，确属本次执行。
- XML 汇总：`tests=328 failures=0 errors=0 skipped=3` → **实际执行并通过 325 个**。

### 3.2 测试数量口径核对（工单特别要求）

| 来源 | 数字 | 问题 |
|---|---:|---|
| 审计报告 | 331 个 `@Test` | 是注解总数（328 JVM + 3 androidTest），不是通过数 |
| 代码质量基线 v1.0 | 「有效 327 个」 | 把未执行的 androidTest 算进有效数，且把无断言测试按失效处理，口径不准 |
| **本次实测** | **325 个 JVM 单测实际执行并通过** | 权威口径 |

明细：

| 项 | 数量 | 说明 |
|---|---:|---|
| 源码中 `@Test` 注解 | 331 | 42 个 JVM 测试文件 328 个 + 1 个 androidTest 文件 3 个 |
| JVM 单测执行 | 328 | `testDebugUnitTest` |
| 其中被 `@Ignore` 跳过 | 3 | `app/src/test/java/com/qingji/app/ui/HomeInteractionTest.kt:37` 整类跳过，原因是 Robolectric 首次运行需下载 100MB+ 系统包、本机代理卡住 |
| **实际执行并通过** | **325** | 失败 0、错误 0 |
| androidTest | 3 | **未执行**，见 3.4 |
| 无断言候选 | 2 | 静态扫描候选，逐条复核见下 |

无断言候选复核：
- `HomeInteractionTest.recordFood_updatesHomeTotal()`（`:76-98`）：方法体只到 `waitForIdle()`，没有任何断言，且所在类已被 `@Ignore`。**确认无效**，但因为它本来就不执行，不额外扣减通过数。
- `DatabaseMigrationTest` 的 1 个候选：脚本只认 `assertXxx`，该类通过 `MigrationTestHelper` 与查询校验，**属静态扫描误判**，不认定为无断言测试。

> 结论：对外只能说「本次 JVM 单测实际执行并通过 **325** 个，3 个 `@Ignore` 跳过，3 个 androidTest 未执行」。不得宣称「331 个测试全部通过」。

### 3.3 编译错误与测试失败

- 编译错误：**0**。编译警告为实验性 AGP 选项 `android.overridePathCheck=true` 及若干图标 deprecation，不影响构建。
- 测试失败：**0**。
- 测试明细：`.workbuddy/backup/audit/phase2/test_stats.csv`（主工作区侧）。

### 3.4 未执行项目及原因（不得伪称通过）

| 项目 | 状态 | 原因 |
|---|---|---|
| `:app:connectedDebugAndroidTest` | 未执行 | `adb devices` 输出为空，无真机/模拟器 |
| 数据库迁移保真测试（3 个 androidTest） | 未执行 | 同上；TASK-08 需在有设备环境补跑 |
| `:app:assembleRelease` | 未执行 | TASK-00 不要求；且 worktree 无 `local.properties`（gitignore），正式签名配置不可用 |
| Detekt / KtLint / AST 重复扫描 | 未执行 | 属 P3，未获单独授权 |

---

## 4. Room / Schema / Migration 状态

| 项 | 实测 |
|---|---|
| `AppDatabase` version | 11 |
| `@Database(entities=...)` 实体数 | 28 |
| 内联手写 `Migration` | 10 个（1→2 … 10→11） |
| `app/schemas/*.json` | 13 个（AppDatabase 11 个 + FoodCatalog 2 个） |
| `fallbackToDestructiveMigration()` | 未使用（`:593` 注释明确禁止） |
| 生产迁移列表 | `AppDatabase.kt:489` `ALL_MIGRATIONS` |
| 测试迁移列表 | `AppDatabase.kt:586-592` 另一份独立副本，`:587` 注释自警「只改测试那份，测试会过、真机升级却打不开」 |
| 迁移测试 | `app/src/androidTest/java/com/qingji/app/data/local/DatabaseMigrationTest.kt`，3 个 `@Test`，本次未执行 |

已知缺口（留给 TASK-08）：`5→6`（建表 + INSERT SELECT + DROP）、`7→8`（重建表 + NULLIF）、`8→9`（断食加两列）三段有数据搬迁，缺逐字段保真断言。

---

## 5. 数据安全契约快照（供 P0 对照，本任务未改动）

以下来自第一阶段审计报告，属于**待修复问题清单**，TASK-01~04 实施前必须重新核实当前代码，不得按旧行号盲改：

| 编号 | 问题 |
|---|---|
| P0-1 | 自律 7 张表不进备份，且 `clearRecords()` / `factoryReset()` 两档都未清理；DAO 无 `clearAll()` |
| P0-2 | 自由断食 `freeMode` / `actualMinutes` 导出与解析漏写，换机后可能被识别成方案断食、时长归零 |
| P0-3 | `factoryReset()` 只删 `filesDir/images` 与 `cacheDir/images`，残留 `filesDir/progress` 进度照片文件 |
| P0-4 | `BackupScreen` 仍使用「全量 / 全部数据」等与实际导出范围不符的文案 |

备份当前为 v3，导出 20 张表；设置项 68 个、搜索历史、照片文件、AI Key 不进备份（AI Key 属正确安全设计，必须保留）。

---

## 6. 环境说明

- 治理 worktree 不含 `local.properties`（`.gitignore` 排除），因此 `qingji.build.dir` 重定向不生效，构建产物落在 worktree 自己的 `app/build/`，与主工作区**完全隔离**；`.gitignore:7` 的 `build/` 已使 `git status` 保持干净。
- debug 编译与 JVM 单测不依赖正式签名，缺签名配置不影响本任务结果。
- 治理环境基于 `origin/main = b138a279`（v1.1.4），**不包含**主工作区那 10 个未提交的 v1.1.5 UI 改动。后续 P0 分支合并时需单独处理这部分 UI 改动的整合，不得默认它已在 main 上。

---

## 7. 本任务边界自查（工单 2.5 / 第十章）

| 约束 | 状态 |
|---|---|
| 未修改任何业务代码 / Entity / DAO / Migration / 备份 / UI | ✅ |
| 未格式化任何现有文件 | ✅ |
| 未修改 README、版本号、Release 信息 | ✅ |
| 未删除任何文件、未使用 `git add .` | ✅ |
| 未改数据库版本、未使用破坏性迁移、未删 Schema | ✅ |
| 未推送分支、未创建 PR、未自动合并 main、未创建 Release | ✅ |
| 新增文件 | 仅 `docs/quality/phase2-baseline.md`、`docs/quality/phase2-task-status.md` |

---

## 8. 下一批（P0）实施方案摘要（只列计划，本轮不实施）

| 任务 | 计划修改文件（实施前逐个重新核实） | 前置核实点 | 必须新增的测试 |
|---|---|---|---|
| TASK-01 自律数据清理与备份 | `SelfDisciplineDaos.kt`、`QingJiRepository.kt`、`BackupManager.kt`、`ParsedBackup.kt`、`TrainingBackup.kt`、`SelfDisciplineSettingsScreen.kt`、相关 Entity | 7 张表的真实 Entity/DAO/表名；两档清除的语义差异；v3 兼容分支 | 7 表逐表清理、v4 往返、v1/v2/v3 老备份兼容、模块清空语义 |
| TASK-02 自由断食字段 | `Entities.kt`、`BackupManager.kt`、`ParsedBackup.kt`、`BackupParsingTest.kt` | 两字段在 Entity 的默认值与类型 | `freeMode=true` / 非零 `actualMinutes` 往返、老备份缺字段兜底 |
| TASK-03 进度照片残留 | `ProgressScreen.kt`、`QingJiRepository.kt`、图片目录常量 | 所有照片写入/读取/删除路径；先取路径再清表 | 进度照片被删、头像与备份文件保留、`clearRecords` 回归 |
| TASK-04 备份文案 | `BackupScreen.kt`、`BackupManager.kt` | TASK-01 完成后自律数据是否已纳入备份 | 文案与实际范围一致、未包含项清单 |

执行顺序：TASK-01 → 02 → 03 → 04，每项独立 Commit；P0 四项全部通过后才做阶段验收，再推送分支并创建 PR（不自动合并 main）。**遇到代码与审计报告不一致，立即停止该任务重新核实。**

---

## 9. TASK-00 验收结论

| 验收项 | 结果 |
|---|---|
| 形成项目质量基线报告 | ✅ 本文件 |
| 明确测试总数 / 忽略数 / 无断言数 / 实际通过数 | ✅ 331 注解 / 3 跳过 / 1 确认无效（另 1 误判）/ **325 实际通过** |
| 记录当前编译错误 | ✅ 0 |
| 记录当前测试失败 | ✅ 0（3 个 androidTest 未执行，已说明） |
| 不修改业务代码、不格式化 | ✅ |
| 工作区保护 | ✅ 主工作区 10 个改动完整 |

按工单末尾建议，**本轮到此为止**：完成基线报告后再进入 TASK-01，不一次性启动 19 项任务。
