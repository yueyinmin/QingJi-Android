# 轻记统一训练体系 B1.1.2 · 交 ChatGPT 技术复审材料

（以下内容自包含，可直接全选复制发送）

---

【轻记统一训练体系 B1.1.2 · 保存一致性与最终边界补齐 · 请技术复审】

## 一、背景与基线

针对你上次指出的 B1.1.1 遗留边界，做小范围定点补齐，不推翻架构、不重做已实现功能。

- 分支：`feature/unified-training`
- 实施前 local HEAD：`091d9a60eb147a4c4b2b902c38e077285638b118`
- 代码提交 SHA：`4358d6a77e82e7e4020d1c3964bfd2f3c9e9e1b4`
- remote HEAD：`bacd2d04064865da5927956aa51519125cd4d0e0`
- ahead / behind：57 / 0
- 推送状态：未推送，`git push origin feature/unified-training` 被 GitHub HTTPS 认证拦截，远端未变。你可以在本地代码/diff 层面复审，但 GitHub 上暂时看不到。

## 二、你上次指出的问题 → 本次处置

| 你指出的问题 | 本次处置 |
| --- | --- |
| P0：保存期间改草稿后再次保存没验证 | 补 T15（新建两次保存）、T16（编辑历史两次保存），真实 Room+Repository 验证 sessionUuid 语义与最终库内容 |
| P0：try/catch 范围过大，UI 回调异常误判保存失败 | save() 拆成「数据库段」与「成功后段」两段 try，回调异常不再改变数据库结果 |
| P0：CancellationException 被吞 | 单独 catch CancellationException 并重抛 |
| P1：persist 落盘失败内存/磁盘不一致 | persist 改为先落盘成功再更新内存，补 T20 |
| P1：备份事务回滚测试不充分 | 用真实 restoreInto + 事务中途异常注入验证回滚（区别于 JSON 解析失败测试） |
| P1：混合训练保存期间修改覆盖不足 | 补 T17（新增有氧）、T18（删除动作） |

## 三、关键代码变更（请重点看这 3 处）

### 3.1 TrainingViewModel.save() 异常分层

```
viewModelScope.launch {
  val weight = settleWeight.value
  try {
    // 第一段：仅 DB 操作
    val result = try {
      ... repo.saveUnifiedSession(...) ...
    } catch (c: CancellationException) {
      throw c                      // 取消重抛，不吞
    } catch (e: Exception) {
      say("保存失败..."); runCatching { onDone(false) }
      return@launch                // 只有 DB 失败才判失败、保留草稿
    }

    // 第二段：DB 已成功，以下异常不改结果
    try {
      ... 草稿版本化清理（revision 未变才清）...
      ... ExerciseCalorieHint 提示 ...
    } catch (c: CancellationException) {
      throw c
    } catch (e: Exception) {
      runCatching { say("训练已保存，但界面刷新遇到问题") }
    }
    runCatching { onDone(true) }   // 回调异常不外溢、不二次调 onDone(false)
  } finally {
    savingGuard.set(false); _isSaving.value = false   // 全路径释放锁
  }
}
```

### 3.2 sessionUuid 语义（T15/T16 验证）

- 新建：draft.sessionUuid = 新建随机 UUID，editingUuid = ""；保存期间改草稿保留的新草稿沿用同一 sessionUuid → 第二次保存同 uuid REPLACE，不重复。
- 编辑：editSession() 里 sessionUuid = editingUuid = 历史场 uuid → 第二次保存仍更新原历史场，不改日期/source、不波及其他场。

### 3.3 persist 落盘一致性 + 备份回滚注入

- persist：先 `draftStore.save(versioned)` 成功后再 `_draft.value = versioned`；失败则内存保持旧值并提示。
- WorkoutDraftStore.save() 增 `saveHook`（生产空），BackupManager.restoreInto() 训练段清表后增 `restoreHook`（生产空），仅供测试注入失败/异常。

## 四、测试结果（实测命令与数字）

- 全量单测：`./gradlew --stop && ./gradlew :app:testDebugUnitTest --no-daemon --console=plain` → BUILD SUCCESSFUL；XML 汇总 **127 套件 / 825 项 / 0 失败 / 0 错误 / 3 跳过**（B1.1.1 为 817 项，新增 8 项）。
- 定向：`./gradlew :app:testDebugUnitTest --tests "...TrainingViewModelB112Test" --tests "...BackupRollbackB112Test" --console=plain` → BUILD SUCCESSFUL，8 项全过：
  - T15 新建两次保存：一场会话/一账本/一日记，最终明细=新草稿 50kg/8 次，无重复；
  - T16 编辑历史两次保存：仍更新原 uuid，日期保持 2026-11-20，其他历史场重量 60kg 未被覆盖；
  - T17 混合保存期间改有氧时长：力量+有氧都在，有氧 duration=1800s，日记=混合；
  - T18 混合保存期间删动作：有氧明细清除，日记=力量；
  - T19 取消：锁释放、不回调 false、草稿保留、之后仍可保存；
  - T20 落盘失败：内存与磁盘 revision/重量一致，恢复后可正常递增；
  - 回调异常：DB 成功 + onDone(true) 抛异常 → failCalled=0、草稿清理、锁释放；
  - 备份回滚：restoreInto 训练段清表后抛异常 → session/力量/有氧/账本/日记全部回滚保留。
- 构建：`./gradlew :app:assembleDebug :app:assembleRelease --no-daemon --console=plain` → BUILD SUCCESSFUL；debug APK 40,229,735 bytes，release APK 16,710,771 bytes。

## 五、证据状态（请按此判断，不夸大）

- 已由真实代码+测试验证：异常分层、sessionUuid 语义、revision 一致性、备份事务回滚（均为真实 Room/Repository/ViewModel 执行）。
- 仅测试证明、未真机手操：T15-T20 的 UI 交互路径。
- 尚未运行：androidTest 迁移 instrumentation（无设备）。
- 环境无法验证：真正 Android 进程死亡恢复（进程恢复测试是新建 Store 读同一 SharedPreferences 文件）；GitHub 推送（认证拦截）。

## 六、请你重点复审

1. save() 两段 try 的边界是否还有「数据库成功却判失败」的漏网路径（例如草稿清理里的 `say()` 抛异常、onDone 二次调用）。
2. T15/T16 的 sessionUuid 语义是否符合产品预期：保存期间改草稿后再次保存，是否应该覆盖同一场（当前实现是覆盖），还是应生成新场。
3. persist「先落盘再更新内存」是否有竞态：保存期间的 revision 比对读的是内存，落盘异步 apply() 是否可能导致内存/磁盘短暂不一致被 save 读到。
4. saveHook/restoreHook 两个生产类测试注入参数是否可接受，或必须改为接口/测试替身。
5. 备份回滚测试是否真正覆盖了 restoreInto 的事务边界（当前在训练段清表后抛异常，包在调用方 db.withTransaction 内）。

如需完整 diff、某个类的全文或测试代码，请告知；材料在仓库 `docs/reports/B1.1.2/` 与 `docs/audit/` 下。
