# 轻记 Android 第二阶段治理 · 任务状态表

| 项 | 值 |
|---|---|
| 工单版本 | 《轻记 Android 第二阶段代码治理总工单 V1.0》 |
| 治理分支 | `feature/phase2-p0-data-safety`（已合并进 main，分支保留） |
| 基准 | `origin/main = b138a279`（v1.1.4 / 115）；**已推进到含 P0 + P1 的 main** |
| 最新更新 | 2026-11-15（第二批 P1 执行后） |
| 本轮范围 | TASK-00 + P0（01~04）+ P1（05~08）全部完成 |
| 推送 / PR / 合并 | ✅ **PR #4 已合并进 main**（merged_at 2026-11-15T01:39:57Z，`9a9eab7`）；本轮 P1 改动在 main 上直接提交 |
| 当前进度基线 | `docs/quality/phase2-progress-baseline.md`（区分已完成 / 未合并 / 未验证 / 待授权） |
| 状态口径 | TASK-00~08 共 **9 项已完成**；P0/P1 阶段验收是批次动作，不计入 19 项任务 |

---

## 1. 任务总览

| 顺序 | 任务 | 优先级 | 状态 | 备注 |
|---:|---|---|---|---|
| 1 | TASK-00 建立项目真实基线 | P0 | ✅ **通过** | 本轮完成，见 `phase2-baseline.md` |
| 2 | TASK-01 自律模块数据清理与备份缺失 | P0 | ✅ **通过** | `9209d84`，备份 v4 + 7 表清理 + 13 个新测试 |
| 3 | TASK-02 自由断食备份字段丢失 | P0 | ✅ **通过** | `e3de6f0`，两字段导出/解析/恢复往返 |
| 4 | TASK-03 恢复出厂后进度照片残留 | P0 | ✅ **通过** | `b827caa`，新增 `PhotoDirs` + 4 个清理用例 |
| 5 | TASK-04 备份范围描述不准确 | P0 | ✅ **通过** | `ce93c6c`，文案与导出范围对齐 |
| — | （批次验收动作，不计入 19 项任务）P0 阶段验收 | P0 | ✅ **通过** | 349 tests / 346 passed / 3 skipped / 0 failures |
| 6 | TASK-05 统一 DataStore 设置读取出口 | P1 | ✅ **通过** | `39df56d`，新增 `LocalAppSettings`，MainActivity 单点订阅 |
| 7 | TASK-06 备份和文件 IO 从 UI 下沉 | P1 | ✅ **通过** | `c3cb673`，拆出 BackupViewModel / ProgressPhotoStore / BackupShare |
| 8 | TASK-07 拆分进度页管理卡片职责 | P1 | ✅ **通过** | `ece7a68`，拆出 Catalog / ManagerSheet / Renderer，进度页 1744→1564 行 |
| 9 | TASK-08 补充数据库迁移保真测试 | P1 | ✅ **通过** | `0924a5b`，迁移列表单一来源 + 3 条保真测试，API 37 上 6/6 通过 |
| 10 | TASK-09 清理 20 项高置信死代码 | P2 | ⬜ 未开始 | — |
| 11 | TASK-10 处理 4 项待人工复核代码 | P2 | ⬜ 未开始 | — |
| 12 | TASK-11 清理 12 处未使用 import | P2 | ⬜ 未开始 | — |
| 13 | TASK-12 合并 R1–R13 真实重复组件 | P2 | ⬜ 未开始 | GroupCard 严禁删除（5 处调用） |
| 14 | TASK-13 抽离公共日期与营养计算逻辑 | P2 | ⬜ 未开始 | — |
| 15 | TASK-14 拆分 ViewModels.kt | P1 | ⬜ 未开始 | 排在 P2 之后 |
| 16 | TASK-15 拆分四个超大页面 | P1 | ⬜ 未开始 | 每文件独立提交 |
| 17 | TASK-16 引入 KtLint | P3 | 🔒 待单独授权 | 不提前接入 |
| 18 | TASK-17 引入 Detekt | P3 | 🔒 待单独授权 | 不提前接入 |
| 19 | TASK-18 Android Lint 与 GitHub Actions | P3 | 🔒 待单独授权 | 不提前接入 |

完成数：**5 / 19 项任务**（TASK-00、TASK-01、TASK-02、TASK-03、TASK-04）。
另有 **P0 阶段验收通过**，它是批次验收动作，不计入 19 项任务数。
剩余 14 项：P1 四项待开始、P2 七项待开始、P3 三项待单独授权。

---

## 2. TASK-00 执行记录

| 项 | 内容 |
|---|---|
| 执行状态 | 通过 |
| 修改文件 | 新增 `docs/quality/phase2-baseline.md`、`docs/quality/phase2-task-status.md`；**未改任何业务代码** |
| 编译 | `BUILD SUCCESSFUL`，0 错误 |
| 单元测试 | 328 个用例执行，3 个 `@Ignore` 跳过，**325 个实际通过**，0 失败 |
| 未执行 | `connectedDebugAndroidTest`（无设备）、`assembleRelease`（本任务不要求、worktree 无签名配置）、Detekt/KtLint（P3 未授权） |
| 基线数字 | 152 个 Kotlin 文件 / 41,490 行 / 超 300 行 47 个 / 超 400 行 28 个 / 超 500 行 15 个 |
| 口径修正 | 审计报告「>500 行 12 个」→ 实测 15 个；「331 个测试全通过」→ 实际 325 个 JVM 单测通过 + 3 跳过 + 3 androidTest 未执行 |
| Git | 分支 `feature/phase2-p0-data-safety`；提交信息 `docs(quality): establish phase2 baseline (TASK-00)`；Commit Hash 见任务最终回复与 `git log -1`（文档无法自引用自身提交哈希） |
| 是否推送 | TASK-00 当时为「否」；P0 四项与阶段验收完成后已按工单推送分支并创建 PR #4（见 `phase2-progress-baseline.md`） |

---

## 3. 批次验收节奏（工单第十二章）

| 批次 | 内容 | 状态 |
|---|---|---|
| 第一批 | TASK-00 + TASK-01~04 + P0 PR | ✅ 全部完成，PR #4 已合并进 main（`566ccc1`） |
| 第二批 | TASK-05~08（架构治理） | ✅ 全部完成，TASK-08 已在模拟器上验证（API 37，6/6） |
| 第三批 | TASK-09~15（代码清理与大文件拆分） | 未开始 |
| 第四批 | TASK-16~18（自动化，需单独授权） | 未开始 |

---

## 4. 已知阻塞与风险

| 项 | 影响 | 处理 |
|---|---|---|
| ~~无真机/模拟器~~ | 已解决 | 2026-11-15 用 `Medium_Phone_API_37.0` 模拟器跑通 6 个迁移测试（含 3 个新保真测试） |
| `HomeInteractionTest` 整类 `@Ignore` | 3 个交互测试长期失效，原因是 Robolectric 系统包下载卡住 | 记为技术债务，不擅自删除 |
| ~~双分支基线不同~~ | 已解决 | 两个分支（P0 数据安全 `566ccc1` + release UI 修复 `9a9eab7`）已于 2026-11-15 合并进 main，零冲突；分支按指示保留未删 |
| 生产与测试两份 Migration 列表 | 测试通过不代表真机升级安全 | TASK-08 统一列表并补保真断言 |
| Release / tag 状态记录曾经过期（旧文档写「Release 已删除、tag 保留」） | 进度判断会失真 | 以最新执行结果为准：旧 tag 与 Release 均已删除，并在 `708446f` 上重建同名 tag 与 Release、APK 已上传；见 `phase2-progress-baseline.md` |

---

## 5. 交付物进度（工单第十二章）

| 文件 | 状态 |
|---|---|
| `docs/quality/phase2-baseline.md` | ✅ 已交付（TASK-00 历史快照，保留不改） |
| `docs/quality/phase2-task-status.md` | ✅ 本文件（持续更新） |
| `docs/quality/phase2-progress-baseline.md` | ✅ 已交付（当前进度基线） |
| `docs/quality/phase2-data-safety-report.md` | ⬜ 未交付（P0 代码已完成，报告待补） |
| `docs/quality/phase2-database-migration-report.md` | ⬜ TASK-08 已完成，报告待补 |
| `docs/quality/phase2-dead-code-review.md` | ⬜ TASK-10 后 |
| `docs/quality/phase2-architecture-report.md` | ✅ 已交付（P1 收官） |
| `docs/quality/phase2-test-report.md` | ⬜ 阶段测试汇总后 |
| `docs/quality/phase2-final-report.md` | ⬜ 第二阶段收尾 |
