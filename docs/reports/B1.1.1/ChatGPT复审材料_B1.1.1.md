# 轻记 B1.1.1 复审材料（交 ChatGPT）

## 0. 上次复审问题已处理

上次你指出「GitHub 上是旧代码、提交没进远端」——核对确认属实：本地领先远端 52 提交、B1.1 两提交从未 push。本次 B1.1.1 也仍在本地，推送被 GitHub HTTPS 认证拦截，需本地手动 `git push origin feature/unified-training`。你能核对的 40 位 SHA 全部列在下面。

## 1. 提交标识

- 分支：`feature/unified-training`
- B1.1 代码：`8575fe3ecd99d880c32c68baec3444e5a7869934`
- B1.1 报告：`8cd00eb8481e366f0f0e1f68a618dc94c7c27037`
- B1.1.1 代码+测试+报告：`01e877be4a645a448b5b8857e7cb88eec3b69858`
- B1.1.1 报告 HEAD 补充：`0b53767f244489da9d35dbfedc17817bb99c73cf`
- B1.1.1 ChatGPT 复审材料：`ac9635e8a5fcfdaa4dc3c842e47aeb91d5ae6e5c`
- 本地 HEAD：`ac9635e8a5fcfdaa4dc3c842e47aeb91d5ae6e5c`
- 远端 HEAD：`bacd2d04064865da5927956aa51519125cd4d0e0`（未含上述提交）
- 完整 diff：`docs/reports/B1.1.1/B1.1.1完整diff.patch`（986 行，涵盖全部代码与测试改动）

## 2. 实施前审计（真实路径 / 类 / 方法 / 调用链）

文件：`docs/audit/轻记统一训练体系B1.1.1实施前审计报告.md`
关键发现：
- D-03（P0）：`TrainingViewModel.save()` 无互斥，全类无 `isSaving`，连续点击可并行进入 `TrainingRepository.saveUnifiedSession()`。
- D-04（P0）：`save()` 协程成功后无条件 `draftStore.clear()`，会把保存期间的新修改一起清掉；`WorkoutDraft` 无版本字段。
- D-05（P0）：无 try/catch，DB 异常直接冲垮协程，草稿与回调都无保证。
- D-06/D-07/D-08：进程恢复、消耗三态、T04/T07/T08 断言缺失。
- 已确认无问题的：`saveUnifiedSession()` 与 `deleteSession()` 都在 `db.withTransaction` 内覆盖明细/账本/日记投影；`PlanExercise` 确有独立 `recordType` 与 `targetConfigJson` 列（TrainingEntities.kt:87-100）；`TrainingItemMapper.resolveType()` 共 4 个调用点；备份 `BackupFormat.VERSION=8`，恢复全程在事务内。

## 3. P0 实现（请重点追踪）

### 3.1 并发互斥
`app/src/main/java/com/qingji/app/ui/viewmodel/TrainingViewModel.kt`
```kotlin
private val savingGuard = AtomicBoolean(false)
private val _isSaving = MutableStateFlow(false)
val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

fun save(onDone: (Boolean) -> Unit) {
    if (!savingGuard.compareAndSet(false, true)) {
        say("正在保存，请稍候"); onDone(false); return
    }
    _isSaving.value = true
    val snapshot = _draft.value ?: run { release(); onDone(false); return }
    // ... 校验失败同样 release ...
    viewModelScope.launch {
        try { /* repo.saveUnifiedSession(snapshot...) */ }
        catch (e: Exception) { /* 草稿保留 */ onDone(false) }
        finally { savingGuard.set(false); _isSaving.value = false }
    }
}
```
UI 层 `TrainingActiveScreen.kt` 读 `isSaving` 显示「保存中…」并禁用按钮——但它不是唯一保护，ViewModel 层 CAS 才是闸门。

### 3.2 草稿快照一致性
- `WorkoutDraftStore.kt`：`WorkoutDraft` 新增 `revision: Long`，save/load 均含该字段（默认 0，老草稿兼容）。
- `persist()`：`d.copy(revision = d.revision + 1)`，每次改动同步落盘。
- 保存成功后：`if (current == null || current.revision == snapshotRevision) clear()` 否则保留新草稿并提示。
- DB 异常：`try/catch` 清草稿前抛错 → 草稿原封不动；`onDone(false)` 可重试。

### 3.3 新增测试钩子
`TrainingRepository` 新增 `private val saveHook: suspend () -> Unit = {}`，在保存事务首行调用，生产为空；测试注入 `CompletableDeferred.await()` 制造保存窗口或抛异常模拟 DB 故障。

## 4. 测试代码全文位置

- `app/src/test/java/com/qingji/app/training/TrainingB111IntegrationTest.kt`（10 项）
- `app/src/test/java/com/qingji/app/training/TrainingViewModelB111Test.kt`（4 项）
- `app/src/test/java/com/qingji/app/domain/ExerciseCalorieHintTest.kt`（5 项）

覆盖映射：
- T04 编辑混合删有氧 → 旧有氧明细删除
- T07 删除混合训练 → 明细/账本/日记投影/会话全清
- T08 纯力量/纯有氧/混合 → 日记分类「力量/有氧/混合」
- T11 DB 异常 → 草稿保留、revision 不变、可重试成功
- T12 连续两次 save → 第二次被拒，entered==1，1 场/1 明细/1 账本
- T14 保存期间改草稿 → 新 revision 保留、新组值保留
- 进程恢复：写真实 SharedPreferences → 新建 `WorkoutDraftStore`（非重建 ViewModel）读磁盘，验证力量组/有氧类型/目标 JSON/实际时长距离/revision 全恢复
- 边界1 空 recordType 经实体 UUID 识别；边界2 未知→OTHER 且原始 JSON 保留；边界3 计划显式类型不被动作库覆盖；边界4 解析失败原文保留；边界5 v7 缺字段正常解析；边界6 v8 全字段往返一致；边界7 损坏备份不恢复、v2 老备份不清空当前数据

## 5. 实测结果（命令 + 数字）

```
./gradlew :app:testDebugUnitTest --tests "com.qingji.app.training.TrainingB111IntegrationTest" --tests "com.qingji.app.training.TrainingViewModelB111Test" --console=plain
→ BUILD SUCCESSFUL，14 项，0 失败 / 0 错误

./gradlew :app:testDebugUnitTest --tests "com.qingji.app.domain.ExerciseCalorieHintTest" --console=plain
→ BUILD SUCCESSFUL，5 项，0 失败 / 0 错误

./gradlew :app:testDebugUnitTest --console=plain
→ BUILD SUCCESSFUL；套件 125，测试 817，失败 0，错误 0，跳过 3

./gradlew --no-daemon :app:assembleDebug :app:assembleRelease --console=plain
→ BUILD SUCCESSFUL in 3m12s
→ app-debug.apk 40,229,735 bytes；app-release.apk 16,710,771 bytes
```

## 6. 请你重点复核

1. `AtomicBoolean` CAS 在 `viewModelScope.launch` 之前的调用线程上锁，是否足以覆盖所有重复提交路径（含 UI 快速双击、配置变更重建 ViewModel）。
2. revision 版本化清理在「保存期间用户改草稿」时保留新草稿，但其 `sessionUuid` 仍是已保存的那一场的 UUID——下次保存会不会误覆盖刚保存的历史场次？（当前实现走 `editingUuid` 分支，请你判断语义是否正确）
3. `saveHook` 仅用于测试、生产为空，是否可接受；有无更干净的注入方式。
4. T12 用 `entered==1` + 会话/明细/账本计数三重断言，是否足以证明无重复提交。
5. 三态消耗提示的归类（未录入 / 参数不足 / 已计算）是否符合产品预期，尤其「纯力量且体重缺失」应归「参数不足」还是「未录入」。

## 7. 未完成

- 推送：环境无 GitHub 凭据，远端仍是 `bacd2d04`，需本地手动 push。
- androidTest 迁移 instrumentation：无设备，未跑。
- HIIT/FLEX/OTHER 多阶段编排：按工单限制未混入。
- 用户 3 个未提交设置页文件未触碰、未纳入提交。
