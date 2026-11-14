# 轻记 Android 第二阶段治理 · 任务状态表

| 项 | 值 |
|---|---|
| 工单版本 | 《轻记 Android 第二阶段代码治理总工单 V1.0》 |
| 治理分支 | `feature/phase2-p0-data-safety` |
| 基准 | `origin/main = b138a279`（v1.1.4 / 115） |
| 最新更新 | 2026-11-15 |
| 本轮范围 | TASK-00 + P0 四项（TASK-01~04）全部完成 |
| 推送 / PR / 合并 | 已推送分支，已创建 PR #4（open，base=main）；**未合并** |

---

## 1. 任务总览

| 顺序 | 任务 | 优先级 | 状态 | 备注 |
|---:|---|---|---|---|
| 1 | TASK-00 建立项目真实基线 | P0 | ✅ **通过** | 本轮完成，见 `phase2-baseline.md` |
| 2 | TASK-01 自律模块数据清理与备份缺失 | P0 | ✅ **通过** | ，备份 v4 + 7 表清理 + 13 个新测试 |
| 3 | TASK-02 自由断食备份字段丢失 | P0 | ✅ **通过** | ，两字段导出/解析/恢复往返 |
| 4 | TASK-03 恢复出厂后进度照片残留 | P0 | ✅ **通过** | ，新增  + 4 个清理用例 |
| 5 | TASK-04 备份范围描述不准确 | P0 | ✅ **通过** | ，文案与导出范围对齐 |
| 6 | P0 阶段验收 | P0 | ✅ **通过** | 349 tests / 346 passed / 3 skipped / 0 failures |
| 7 | TASK-05 统一 DataStore 设置读取出口 | P1 | ⬜ 未开始 | — |
| 8 | TASK-06 备份和文件 IO 从 UI 下沉 | P1 | ⬜ 未开始 | — |
| 9 | TASK-07 拆分进度页管理卡片职责 | P1 | ⬜ 未开始 | — |
| 10 | TASK-08 补充数据库迁移保真测试 | P1 | ⬜ 未开始 | 需有设备环境 |
| 11 | TASK-09 清理 20 项高置信死代码 | P2 | ⬜ 未开始 | — |
| 12 | TASK-10 处理 4 项待人工复核代码 | P2 | ⬜ 未开始 | — |
| 13 | TASK-11 清理 12 处未使用 import | P2 | ⬜ 未开始 | — |
| 14 | TASK-12 合并 R1–R13 真实重复组件 | P2 | ⬜ 未开始 | GroupCard 严禁删除（5 处调用） |
| 15 | TASK-13 抽离公共日期与营养计算逻辑 | P2 | ⬜ 未开始 | — |
| 16 | TASK-14 拆分 ViewModels.kt | P1 | ⬜ 未开始 | 排在 P2 之后 |
| 17 | TASK-15 拆分四个超大页面 | P1 | ⬜ 未开始 | 每文件独立提交 |
| 18 | TASK-16 引入 KtLint | P3 | 🔒 待单独授权 | 不提前接入 |
| 19 | TASK-17 引入 Detekt | P3 | 🔒 待单独授权 | 不提前接入 |
| 20 | TASK-18 Android Lint 与 GitHub Actions | P3 | 🔒 待单独授权 | 不提前接入 |

完成数：**6 / 19 项任务**（含 TASK-00 与 P0 阶段验收）（不含阶段验收与 P3 授权项）。

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
| 是否推送 | 否（P0 阶段未完成，按工单 3.4 阶段完成后再推分支 + PR） |

---

## 3. 批次验收节奏（工单第十二章）

| 批次 | 内容 | 状态 |
|---|---|---|
| 第一批 | TASK-00 + TASK-01~04 + P0 PR | ✅ 全部完成，PR #4 已建（未合并） |
| 第二批 | TASK-05~08（架构治理） | 未开始 |
| 第三批 | TASK-09~15（代码清理与大文件拆分） | 未开始 |
| 第四批 | TASK-16~18（自动化，需单独授权） | 未开始 |

---

## 4. 已知阻塞与风险

| 项 | 影响 | 处理 |
|---|---|---|
| 无真机/模拟器 | 3 个数据库迁移 androidTest 无法执行，TASK-08 无法验收 | 需在具备设备或模拟器的环境补跑；不得伪称通过 |
| `HomeInteractionTest` 整类 `@Ignore` | 3 个交互测试长期失效，原因是 Robolectric 系统包下载卡住 | 记为技术债务，不擅自删除 |
| 治理分支基于 v1.1.4，主工作区有 10 个未提交 v1.1.5 UI 改动 | 后续合并可能产生冲突 | 主工作区改动已完整保护；合并前需单独确认整合方式 |
| 生产与测试两份 Migration 列表 | 测试通过不代表真机升级安全 | TASK-08 统一列表并补保真断言 |
| GitHub 上 v1.1.4 Release 已删除、tag 保留 | 下次发版需确认版本号口径 | 未经主人确认不重建 Release、不改 tag |

---

## 5. 交付物进度（工单第十二章）

| 文件 | 状态 |
|---|---|
| `docs/quality/phase2-baseline.md` | ✅ 已交付 |
| `docs/quality/phase2-task-status.md` | ✅ 本文件 |
| `docs/quality/phase2-data-safety-report.md` | ⬜ P0 完成后 |
| `docs/quality/phase2-database-migration-report.md` | ⬜ TASK-08 后 |
| `docs/quality/phase2-dead-code-review.md` | ⬜ TASK-10 后 |
| `docs/quality/phase2-architecture-report.md` | ⬜ P1 完成后 |
| `docs/quality/phase2-test-report.md` | ⬜ 阶段测试汇总后 |
| `docs/quality/phase2-final-report.md` | ⬜ 第二阶段收尾 |
