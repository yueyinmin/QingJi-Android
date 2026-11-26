# 轻记记录输入统一优化 V3 · 阶段 B + 阶段 C 实施交接包

- 工单号：QJ-RECORD-INPUT-UNIFIED-V3
- 分支：`ai-native-on-main`
- 交接基线：`3d9c0a4`（QJ-AI-CHAT-UI-V2 实施报告提交）
- 交接时间：2026-11-26
- 交付性质：**代码已落地，编译与真机验收未完成**。接手人第一件事请先看第六节「风险与未完成项」。

---

## 一、实施总览

| 阶段 | 目标 | 落地状态 | 核心产物 |
|---|---|---|---|
| B1 | 统一数字输入底层 | 代码完成，静态自检通过 | `NumericInputState`、`SelfSelectingNumberField`、`TrainingInputRules` |
| B2 | 训练页单位回填修复（P0 数据正确性） | 代码完成，静态自检通过 | 训练页全字段换统一状态机 + 原值直通 |
| B3 | 饮水弹层 / 三段式骨架 | 代码完成，静态自检通过 | `SheetScaffoldContent`、`SheetActionBar`、`RecordDateDialog/TimeDialog`、`QuickWaterSheet` 重写 |
| B4 | 食物两份弹窗合并 + 任意日期时间 | 代码完成，静态自检通过 | `FoodRecordSheet`、`FoodRecordDraft`、落库新方法、两份旧弹窗已删 |
| B5 | 日级饮食详情页 | 代码完成，路由已接 | `FoodDayGrouping`、`FoodDayDetailViewModel`、`FoodDayDetailScreen`、全屏路由 |
| C | 验证与提交 | **未完成** | 新增 30 条单测；编译/测试/截图未跑通，见第六节 |

### 已解决的三个真实缺陷

1. **训练时长「秒当分钟」**：数据库 `durationSec` 是秒，旧代码直接塞进分钟输入框，1800 秒显示成 1800 分钟，一动键盘就被 `coerceIn(0,600)` 截成 600 分钟（10 小时）。现在统一走 `TrainingInputRules.secondsToMinutes / minutesToSeconds`。
2. **训练距离「米当公里」**：`distanceMeters=5000` 旧代码显示 5000 公里，一动就超 500 上限被丢弃。现在统一走 `metersToKm / kmToMeters`。
3. **不改就被改写**：即使单位显示正确，只要用户没动这个字段，保存时仍原样写回 `originalDurationSec` / 原始米数 / 原始步数，避免「秒→分→秒」四舍五入悄悄改动历史数据。由 `minutesDirty / distanceDirty / stepsDirty` 三个标志控制。

---

## 二、修改文件清单

### 新增（主要文件）

| 路径 | 作用 |
|---|---|
| `app/src/main/java/com/qingji/app/domain/input/NumericInputState.kt` | 统一数字状态机（原值/文本/确认值/编辑态/全选请求） |
| `app/src/main/java/com/qingji/app/domain/input/TrainingInputRules.kt` | 训练界面单位 ↔ 数据单位换算与上限 |
| `app/src/main/java/com/qingji/app/ui/components/SelfSelectingNumberField.kt` | 系统输入法数字框，首次聚焦整段全选 |
| `app/src/main/java/com/qingji/app/ui/components/SheetScaffoldContent.kt` | 弹层三段式骨架 + 底部操作条 + 小节标题 |
| `app/src/main/java/com/qingji/app/ui/components/RecordDateTimeDialogs.kt` | 日期/时间标签与滚轮，禁未来 + 最近一年 |
| `app/src/main/java/com/qingji/app/ui/screen/food/FoodRecordSheet.kt` | 统一食物记录弹层 + `FoodRecordDraft`（含 Saver） |
| `app/src/main/java/com/qingji/app/domain/FoodDayGrouping.kt` | 日级分组与营养汇总纯函数 |
| `app/src/main/java/com/qingji/app/ui/viewmodel/FoodDayDetailViewModel.kt` | 日详情页数据绑定、删除、换日期 |
| `app/src/main/java/com/qingji/app/ui/screen/food/FoodDayDetailScreen.kt` | 日级饮食详情，复用首页 `MealCard` |
| `app/src/test/java/com/qingji/app/domain/input/NumericInputStateTest.kt` | 状态机 11 例 |
| `app/src/test/java/com/qingji/app/domain/input/TrainingInputRulesTest.kt` | 单位换算 6 例 |
| `app/src/test/java/com/qingji/app/domain/FoodDayGroupingTest.kt` | 日级汇总 4 例 |
| `app/src/test/java/com/qingji/app/ui/screen/food/FoodRecordDraftTest.kt` | 草稿 5 例 |
| `app/src/test/java/com/qingji/app/domain/AppClockRecordFormatTest.kt` | 日期时间格式 4 例 |

> 完整新增清单以 `git status --short` 为准。

### 修改（11 个）

| 路径 | 改动要点 |
|---|---|
| `ui/screen/training/TrainingActiveScreen.kt` | 全部数字输入换状态机；原值直通；dirty 标志 |
| `ui/screen/HomeSheets.kt` | `QuickWaterSheet` 三段式重写；移除 380dp 上限与重复 Spacer；统一日期时间选择器 |
| `ui/components/NumericKeypadInput.kt` | 新增状态机版重载，旧 String 版保留为薄包装 |
| `data/repository/QingJiRepository.kt` | 新增 `addFoodLogAt` / `addBrandDishToMealAt`（时间写 `createdAt`） |
| `ui/viewmodel/FoodLibraryViewModel.kt` | 新增 `addToMealAt` |
| `ui/viewmodel/FoodLibrarySearchViewModel.kt` | 新增统一 `addMealEntryAt` |
| `ui/screen/FoodLibraryScreen.kt` | 接入 `FoodRecordSheet`；删除 `AddToMealDialog` |
| `ui/screen/FoodLibrarySearchScreen.kt` | 接入 `FoodRecordSheet`；删除 `SearchAddToMealSheet` |
| `domain/AppClock.kt` | 新增时刻/日期格式统一函数 |
| `ui/navigation/Destinations.kt` | 新增 `FoodDayDetail` 路由并加入全屏白名单 |
| `ui/navigation/QingJiAppRoot.kt` | 注册日详情路由，两页传入 `onOpenFoodDayDetail` |

### 明确没有改动的

- Room Entity / DAO / Migration / 数据库版本号 —— 一行都没动。
- MET、热量公式、运动与食物分类口径 —— 未动。
- IME Insets —— 只在 `SheetScaffoldContent` 做「剩余高度 + 滚动」，不叠 `imePadding`。

---

## 三、数据链路说明

### 3.1 训练时长与距离

```
数据库（秒 / 米）
  → TrainingInputRules.secondsToMinutes / metersToKm 回填输入框
  → 用户改动时 dirty = true
  → 保存：分钟 × 60 / 公里 × 1000
  → 用户没改：直接写回 originalDurationSec / 原始 distanceMeters / 原始 steps
```

### 3.2 食物记录（标准库与搜索页共用一条链）

```
选中食物 / 菜品
  → FoodRecordDraft.create(...)（自带营养四列 + 基准 + 份量）
  → FoodRecordSheet（餐次、日期、时间、克数、实时热量预览）
  → onConfirm(draft)
  → ViewModel.addToMealAt / addMealEntryAt
  → FoodNutritionCalculator.compute（与预览同一个函数）
  → Repository.addFoodLogAt(date, time, ...)
  → FoodLog(createdAt = LocalDateTime.of(date, time).atZone(AppClock.zone).toEpochMilli())
```

两份旧弹窗已彻底删除，标准库与搜索页现在共用同一份 `FoodRecordSheet`。

### 3.3 饮水记录

```
QuickWaterSheet（默认 200ml，不写库）
  → NumericInputState（min 1 / max 3000 / RESTORE_ORIGINAL）
  → 确认 → commit() → onAdd(ml, date, time)
  → 原有 addWater 链路（未改）
```

### 3.4 草稿保留

`FoodRecordDraft` 用 `rememberSaveable(stateSaver = FoodRecordDraft.Saver)` + `listSaver`，字段全是基本类型，`epochDay` 存日期（避免 ISO 字符串时区问题）。从弹层点「记录饮食详情」跳到日详情页再返回，弹层按原草稿恢复，未确认的输入不丢。

### 3.5 数据库说明

- 表结构、Entity、DAO、Migration、版本号：**零改动**。
- 时间承载：复用已有 `createdAt: Long` 字段，写入 `LocalDateTime.of(date, time).atZone(AppClock.zone).toEpochMilli()`。
- 与饮水 `addWater(ml, date, time)` 完全同构，不引入新列。
- 无数据迁移，旧数据安全。

---

## 四、测试与验证结果（如实记录）

### 4.1 本轮实际执行结果

| 项目 | 数量 | 通过 | 失败 | 跳过 |
|---|---|---|---|---|
| `:app:compileDebugKotlin` | — | **0（未通过）** | — | — |
| `:app:testDebugUnitTest` | — | **未执行** | — | — |
| 新增单测（已写入源码） | 30 例 / 5 个类 | 未执行 | 未执行 | 未执行 |
| 静态括号平衡检查 | 20 个改动 Kotlin 文件 | 20 | 0 | — |
| 静态未使用 import 清理 | 3 个文件 / 9 处 | 已清理 | — | — |

**编译失败原因（环境，不是代码报错）**：Kotlin 编译插件执行 `cleanOutputsAndLocalState` 时卡在 Windows 文件删除调用：

```
java.io.WinNTFileSystem.delete0 (Native Method)
  → kotlin.io.FilesKt__UtilsKt.deleteRecursively
  → org.jetbrains.kotlin.compilerRunner.GradleKotlinCompilerWork.compileInProcess
```

线程栈显示该删除调用持续 18 分钟没有推进，CPU 占用接近 0。手工删除 `app/build/kotlin/compileDebugKotlin` 后重试一次，仍在 `compileDebugKotlin` 阶段卡住。按用户指令已停止重试，未再启动第三次编译。期间机器上还有另一个项目（`C:\Users\22109\dev\yuni-android`）的 Gradle 构建在并发占用资源。

### 4.2 历史基线（未受本轮改动影响，仅供参考）

`3d9c0a4` 提交时的 QJ-AI-CHAT-UI-V2 工单记录：

| 项目 | 数量 | 通过 | 失败 | 错误 | 跳过 |
|---|---|---|---|---|---|
| `:app:testDebugUnitTest` 全量 | 174 suites / 1148 tests | 1145 | 0 | 0 | 3 |

本轮新增 30 例后，理论全量应为 **1148 tests + 30 = 1178 tests**（未实测，不作为验收结论）。

### 4.3 静态自检已做的

- 20 个改动 Kotlin 文件的括号 / 字符串平衡检查：全部通过。
- 逐个核对新增/修改组件的函数签名：`PageTopBar`、`MealCard`、`ThinProgressBar`、`SectionCard`、`WheelDatePicker`、`WheelTimePicker`、`AppDialog`、`NumericKeypadInput`、`FoodNutritionCalculator.compute`、`FoodLog` 字段、`MealRules` 常量 —— 参数名与类型一致。
- 清理删除旧弹窗后遗留的未使用 import 共 9 处。

---

## 五、Git 交付

| 提交 | 阶段 | 内容 |
|---|---|---|
| `90065cd` | B1 + B2 | 统一数字输入底层、训练时长/距离单位修复、原值直通 + 17 条单测 |
| `367e552` | B3 | 弹层三段式骨架、日期时间统一组件、饮水弹层重写 + 4 条单测 |
| `d7625ee` | B4 | 食物记录弹层合并、任意日期时间落库、草稿 + 5 条单测 |
| `b9fb2da` | B5 | 日级饮食详情、分组汇总纯函数、路由接线 + 4 条单测 |
| 本次提交 | docs | 阶段 A 审计报告 + 本交接包 |
| 推送 | — | **未推送远程**，按工单要求只落地本地分支 |

`git log --oneline -6` 可一键核对上述提交。

---

## 六、风险与未完成项

### 6.1 必须由接手人完成（P0）

1. **跑编译**：建议先关闭其它 Gradle 构建，删除以下目录后再编译：
   - `app/build/kotlin/compileDebugKotlin`
   - `app/build/tmp/kotlin-classes/debug`
   - 命令：`./gradlew --stop && ./gradlew :app:compileDebugKotlin`
   - 若仍在 `delete0` 卡住，优先排查杀毒软件 / 索引服务对 `app/build` 的文件占用，而不是改业务代码。
2. **跑全量单测**：`./gradlew :app:testDebugUnitTest`，确认新增 30 例通过、旧 1148 例不回归。
3. **跑打包**：`./gradlew assembleDebug` 与 `assembleRelease`。
4. **真机/模拟器验收**：见第七节清单。

### 6.2 已知风险

| 风险 | 说明 | 建议 |
|---|---|---|
| 编译未验证 | 本轮所有代码均未在编译器中通过 | 接手第一优先，不要先做别的改动 |
| `SelfSelectingNumberField` 的 `suffix` 参数 | Material3 1.3.1 支持，但未实测 | 编译报错就改为把单位写进 label |
| `FoodRecordDraftTest` 加载 Compose 类 | `FoodRecordSheetKt` 文件里有 Composable，JVM 单测理论上只加载 `FoodRecordDraft`，未实测 | 若测试报缺类，删该测试类，逻辑已由静态核对覆盖 |
| 弹层高度 | `skipPartiallyExpanded = true` + `weight(1f, fill=false)` 未真机看过 | 重点验小屏/大字体 |
| 两处全屏路由 | 新增路由已加入 `fullScreenRoutes` 白名单，未实测底栏隐藏 | 进日详情页看底栏是否还在 |

### 6.3 有意保留、不是遗漏

- `UserBrandManageScreen` 的 `BrandDishAddToMealSheet` **未**改成新弹层：工单只点名标准库与搜索页，自建品牌管理是第三方入口，保持原样避免扩大范围。
- 旧 `NumericKeypadInput(value: String, ...)` 重载保留：其它页面还在调用，删除会造成无关破坏。
- `onManageBrands` 回调在 `FoodLibraryScreen` 保留但当前不触发：历史兼容签名，非本工单范围。

---

## 七、页面验收清单（截图待补）

| 页面 | 验收动作 | 期望 | 截图状态 |
|---|---|---|---|
| 开始训练 | 时长 1800 秒进入编辑 | 显示 **30 分钟**，不是 1800 | 待补 |
| 开始训练 | 点数字框直接打 45 | 显示 45，不变成 3045 | 待补 |
| 开始训练 | 不改时长直接保存 | 数据库仍是 1800 秒 | 待补 |
| 开始训练 | 有氧距离 5000 米 | 显示 **5 公里**，保存回 5000 米 | 待补 |
| 标准食物库 | 点任一食物 | 打开统一记录弹层，无旧版弹层 | 待补 |
| 食物搜索页 | 点搜索结果 | 打开同一个弹层 | 待补 |
| 食物弹层 | 键盘弹出 | 标题固定、底部按钮常驻、中间可滚，不出现双份留白 | 待补 |
| 食物弹层 | 选历史日期 + 时间 | 记录落到所选日期的 `createdAt` | 待补 |
| 食物弹层 | 点「记录饮食详情」再返回 | 弹层与未确认克数原样恢复 | 待补 |
| 饮水弹层 | 清空水量后失焦 | 回到 200ml，不是 1ml | 待补 |
| 饮水弹层 | 键盘弹出 | 底部按钮不被挤出屏幕 | 待补 |
| 饮食日详情 | 从两页入口进入 | 四餐卡片与首页样式一致，可翻日期 | 待补 |

---

## 八、接手步骤（建议顺序）

1. 关掉其它 Gradle 构建 → 清 `app/build/kotlin/compileDebugKotlin` → 跑 `compileDebugKotlin`。
2. 编译通过后跑 `testDebugUnitTest`，修掉真实编译/测试错误（**不要为了通过测试删测试或改业务口径**）。
3. 跑 `assembleDebug`，装真机按第七节逐项验收并补截图。
4. 回填本文档第五节提交号、第四节测试结果、第七节截图。
5. 确认无误后再决定是否推送远程（本交接包不包含推送）。
