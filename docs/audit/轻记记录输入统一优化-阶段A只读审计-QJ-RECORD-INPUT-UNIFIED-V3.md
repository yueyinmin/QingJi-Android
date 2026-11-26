# QJ-RECORD-INPUT-UNIFIED-V3 · 阶段 A 只读审计报告

> 审计对象：轻记 Android（`com.qingji.app`）
> 审计范围：开始训练页有氧实际时长、标准食物库记录弹窗、首页今日饮水弹窗，及其数据链路与详情页
> 审计性质：**只读**。本阶段未修改任何源码、未改 Room Entity/DAO/Migration、未新增依赖。
> 产出物：本文件（`docs/audit/`，新增文档，未提交）

---

## 0. 审计基线与工作区状态

| 项 | 值 |
|---|---|
| 仓库路径 | `C:/qingji-git/QingJi-Android` |
| 当前分支 | `ai-native-on-main` |
| 审计时 HEAD | `3d9c0a4cbfacbc81d90cf2e81ce79c590d7ad1fc`（`docs(ai): add QJ-AI-CHAT-UI-V2 implementation report`） |
| 相对 `origin/main` | 领先 35 个提交，未推送 |
| 审计开始时工作区 | **干净**（`git status --porcelain` 无输出） |
| 审计结束时工作区 | 仅新增本审计文档（未提交、未改任何源码） |
| 构建工具链 | JDK 由 gradlew 管理；Compose BOM `2024.12.01`（material3 **1.3.1**）；compileSdk 35 / minSdk 26 / targetSdk 35 |

### 审计方法（避免「凭截图猜代码」）

1. 全量读源码，不读截图推测；
2. 关键结论都附**文件路径 + 行号 + 代码片段**；
3. 涉及框架行为（ModalBottomSheet 的 Insets）时，**解包 material3-1.3.1-sources.jar 核对上游实现**，不凭印象；
4. 无法从代码断定的（如定制输入法高度、字号缩放后视觉裁切），一律标记「需真机确认」，不写成事实。

---

## 1. 三个模块的调用链地图

### 1.1 训练：有氧「实际时长」

```
QingJiAppRoot (route training_active)
└─ TrainingActiveScreen            ui/screen/training/TrainingActiveScreen.kt:70
   ├─ Column + verticalScroll(rememberScrollState())          :148   ← 不是 LazyColumn
   ├─ d.exercises.forEach { ... }                             :249   ← 无 key() 包裹
   │  └─ NonStrengthEditorCard(draft, type, vm, onRemove)     :405
   │     ├─ minutes: String  = remember(draft.key)            :415-420
   │     ├─ emit() → vm.updateActual(key, WorkoutItem)        :476-514
   │     ├─ durationSec() = minutes.coerceIn(0,600) * 60      :474
   │     └─ OutlinedTextField(value = minutes, onValueChange = { minutes = it; emit() })
   │                                                          :586-592 / :595-601
   ├─ 底部固定条 navigationBarsPadding + 「保存训练」          :329-351
   └─ WorkoutTimerBar「改时长」→ ExerciseNumberWheelSheet      :300 / :353-362
      └─ vm.setDurationOverride(min)                          （与会话总时长有关，与卡片时长无关）

TrainingViewModel.save()                                       ui/viewmodel/TrainingViewModel.kt:485
├─ 新训练：session.durationMinutes = 计时器/override          :550-561  ← 与卡片里填的分钟无关
├─ 补记：session.durationMinutes = overrideMin                :542-553
├─ 逐项：TrainingItemMapper.draftToItem → WorkoutItem.Cardio  :587-604
├─ 有氧热量：withCardioKcal(item, entity, mets, weight)       domain/TrainingItemMapper.kt:207-236
└─ repo.saveUnifiedSession(session, strength+other, cardio, weight)
   └─ workout_cardio_detail.durationSeconds = item.durationSec（秒）   TrainingItemMapper.kt:152-167
```

### 1.2 食物：标准食物库记录弹窗 + 详情

```
QingJiAppRoot (route food_library)
└─ FoodLibraryScreen                                            ui/screen/FoodLibraryScreen.kt:88
   ├─ var pick: Food? = remember { ... }                        :119   ← 非 saveable
   └─ pick?.let { AddToMealDialog(food, defaultGrams, ...) }    :489-508
      └─ AddToMealDialog                                        :652-720
         ├─ meal: String   = remember { MealRules.guess() }     :658
         ├─ grams: String  = remember { defaultGrams.toInt() }  :659   ← 默认 "100"
         ├─ dayOffset: Long= remember { 0L }                    :660   ← 今天/昨天/前天
         └─ AppBottomSheet(...)                                 :662
            ├─ confirmEnabled = NumericKeypadLogic.isValid      :667
            ├─ NumericKeypadInput(grams, ...)                   :711-718  ← 自绘键盘，无系统输入法
            └─ onConfirm(meal, grams, dayOffset) → vm.addToMeal :494-506

FoodLibraryViewModel.addToMeal(...)                            FoodLibraryViewModel.kt:168-207
├─ FoodNutritionCalculator.compute(...)                        （热量/三宏量按基准折算）
└─ repo.addFoodLog(FoodLog(date = AppClock.today() - dayOffset,
                           createdAt = 默认 System.currentTimeMillis()))
   └─ QingJiRepository.addFoodLog                              QingJiRepository.kt:966-971

另一份重复实现：FoodLibrarySearchScreen.SearchAddToMealSheet   FoodLibrarySearchScreen.kt:329-395

首页餐食展示（要复用的样式）
HomeScreen → MealRules.ALL.forEach → MealCard(type, items, ...)  HomeScreen.kt:368-390
MealCard                                                         HomeCards.kt:490-731
用餐详情（单餐，已存在）                                          MealDetailScreen.kt:85
```

### 1.3 饮水：记录弹窗 + 详情

```
HomeScreen                                                      ui/screen/HomeScreen.kt
├─ if (showCupMenu) QuickWaterSheet(...)                        :480-492
│  └─ QuickWaterSheet                                           ui/screen/HomeSheets.kt:73-359
│     ├─ pendingMl: String = remember { "200" }                 :82
│     ├─ editing: Boolean  = remember { false }                 :84
│     ├─ ModalBottomSheet(无 sheetState → 默认半屏)             :98-112
│     ├─ 内容区 heightIn(max = 380.dp).verticalScroll(...)      :120-126
│     ├─ 中间数字 pressable { editing = true }                  :175   ← 只进编辑态，不清空/不全选
│     ├─ 快捷 +100/+200/+300 → WaterQuickAdd.add(pendingMl, ml) :209-219  ← 已是累加
│     ├─ WaterNumberKeypad(value, onChange, onDone)             :235-249
│     │  └─ appendDigit(value, key, decimals=0)                 WaterNumberKeypad.kt:61
│     ├─ 日期/时间滚轮 AppDialog + WheelDatePicker/TimePicker   :361-406
│     └─ 取消 / 确认记录 / 查看详情                              :298-357
└─ if (showCupDetail) AppBottomSheet「今日饮水」+ 逐笔明细       :493-568

HomeViewModel.addWater(ml, date, time)                          HomeViewModel.kt:143-145
└─ QingJiRepository.addWater(ml, date, time)                    QingJiRepository.kt:1054-1068
   └─ createdAt = LocalDateTime.of(date, time).atZone(AppClock.zone).toEpochMilli()   ← 关键先例
```

**这张图里最重要的一件事**：饮水把「用户选的时间」写进了 `createdAt`，**没有改表结构**。食物记录要支持时间，可以照抄这条路（见 §6.4）。

---

## 2. 逐项代码证据

### 2.1 训练时长输入（模块 A）

**状态类型**：`String`（不是 `TextFieldValue`）

```kotlin
// TrainingActiveScreen.kt:415-420
var minutes by remember(draft.key) {
    mutableStateOf(((parsed as? WorkoutItem.Cardio)?.durationSec
        ?: (parsed as? WorkoutItem.Hiit)?.durationSec
        ?: (parsed as? WorkoutItem.Flexibility)?.durationSec
        ?: (parsed as? WorkoutItem.Other)?.durationSec ?: 0L).toString())
}
```

**输入回调**：

```kotlin
// :586-592（固定器械类有氧）/ :595-601（其他非力量）
OutlinedTextField(
    value = minutes,
    onValueChange = { minutes = it; emit() },
    label = { Text("实际时长（分钟）") },
    singleLine = true,
    modifier = Modifier.fillMaxWidth()
)
```

**没有的东西**（全仓库 `grep` 结果）：

| 检查项 | 结果 |
|---|---|
| `TextFieldValue` / `TextRange` | **全仓库 0 处**（`grep -rln TextFieldValue app/src/main` 无输出） |
| `FocusRequester` / `onFocusChanged`（本文件） | 0 处 |
| `keyboardOptions` / `KeyboardType.Number`（本文件） | 0 处 → 弹的是**全键盘**，能打字母 |
| Insets 处理（本文件） | 只有 `navigationBarsPadding()`（底部操作条），无 `imePadding` |

**保存与热量**：

```kotlin
// :474
fun durationSec(): Long = (minutes.toIntOrNull() ?: 0).coerceIn(0, 600) * 60L

// TrainingItemMapper.kt:218-221
if (item.durationSec <= 0L || weightKg <= 0.0) {
    return item.copy(calculatedKcal = 0.0, calculationMethod = "", ...)
}
```

→ 空值/非法值走 `toIntOrNull() ?: 0`，最终落成 `durationSec = 0`；热量计算有 `<= 0` 短路，**不会 NaN、不会崩**。这一点现状是对的。

### 2.2 训练页两个**未被工单提到、但更严重**的单位错误（P0）

**（1）秒当成分钟显示**

`durationSec` 存的是**秒**（证据：`withCardioKcal` 用 `item.durationSec / 60.0` 当分钟；`itemToCardio` 写 `durationSeconds = item.durationSec`），但 `minutes` 的初始值直接取 `durationSec.toString()`，字段标签却是「实际时长（分钟）」。

复现路径（改一条已有训练）：
1. 一条 30 分钟划船机 → 库内 `durationSec = 1800`；
2. 进「修改训练」→ 卡片回填 `minutes = "1800"`，界面显示 **1800 分钟**；
3. 用户在框里改任意一个字符 → `emit()` → `1800.coerceIn(0,600)*60 = 36000` 秒 = **600 分钟**。

→ 静默把 30 分钟改成 600 分钟。这是数据损坏级缺陷。

**（2）米当成公里显示**

```kotlin
// :421-423
var distanceKm by remember(draft.key) {
    mutableStateOf(((parsed as? WorkoutItem.Cardio)?.distanceMeters ?: 0.0).toString())
}
```
`distanceMeters` 是米，字段标签是「实际公里」。5 公里的记录回填显示 `5000.0`；用户一动，`amount = 5000` 公里 → `takeIf { it <= 500.0 }` 判定超限 → `distanceMeters = null`，距离被静默丢掉。

**（3）步数为 0 时回填空串**

```kotlin
// :459-461
var stepsInput by remember(draft.key) {
    mutableStateOf((cardio?.steps ?: 0).takeIf { it > 0 }?.toString().orEmpty())
}
```
与 `minutes`（永远 "0"）不一致；切到步数模式再切回、或空着保存时，`durationSec` 会被写成 0。

> 这三条说明：模块 A 不只是「点击不全选」的体验问题，还有**回填口径**问题。修的时候必须一起修，否则「修好全选」反而让用户更容易触发 1800→600。

### 2.3 食物记录弹窗（模块 B）

```kotlin
// FoodLibraryScreen.kt:652-720 全文要点
var meal  by remember { mutableStateOf(MealRules.guess()) }      // 658
var grams by remember { mutableStateOf(defaultGrams.toInt().toString()) }  // 659 → "100"
var dayOffset by remember { mutableStateOf(0L) }                 // 660 → 今天/昨天/前天

AppBottomSheet(
    title = "加到哪一餐",
    confirmText = "加入",
    confirmEnabled = NumericKeypadLogic.isValid(grams, 1.0, 3000.0),  // 667
    onConfirm = { onConfirm(meal, grams.toDouble(), dayOffset) },
    onDismiss = onDismiss
) {
    ... 餐次 4 个胶囊 ...
    ... 今天/昨天/前天 3 个胶囊 ...
    NumericKeypadInput(value = grams, onValueChange = { grams = it }, ...)  // 711
}
```

**关键事实**：

| 检查项 | 结论 |
|---|---|
| 弹窗实现 | `AppBottomSheet` → `androidx.compose.material3.ModalBottomSheet`（`AppDialogs.kt:72`） |
| 是否唤起系统输入法 | **否**。整块弹窗没有任何 `TextField`，只有 `NumericKeypadInput`（自绘 4 行键盘，常驻显示） |
| 内容是否可以滚动 | **否**。`AppBottomSheet` 的内容就是裸 `Column`（`AppDialogs.kt:93-133`），全仓库 33 个调用点，**没有任何垂直滚动容器** |
| 初始展开档位 | `skipPartiallyExpanded` 未传 → 默认 `false` → **半屏**（`AppDialogs.kt:68-76`） |
| 高度限制 | 无显式 `heightIn`；由 ModalBottomSheet 的可用约束决定，超出部分直接裁掉 |
| 日期能力 | 只有 `dayOffset ∈ {0,1,2}`，**不能选任意日期，没有任何时间字段** |
| 重复实现 | 搜索页还有一份 `SearchAddToMealSheet`（`FoodLibrarySearchScreen.kt:329`），默认克数用 `defaultGrams.toString()`（可能显示 `"100.0"`），并且传了 `skipPartiallyExpanded = true` —— **同一业务两个弹窗、两套行为** |

### 2.4 饮水记录弹窗（模块 C）

逐条对照工单里列的 8 个问题，代码事实如下：

| 工单描述 | 代码事实 | 判定 |
|---|---|---|
| 键盘弹出后内容被裁切 | 该弹窗**永远不会唤起系统输入法**（无 TextField）。被当成「键盘」的是 `WaterNumberKeypad` 自绘键盘（4 行 × 46dp ≈ 208dp，`:235-249` 条件渲染） | 表述需修正，缺陷本身存在 |
| 弹窗高度受限 | `heightIn(max = 380.dp)`（`:124`）硬上限 + `ModalBottomSheet` 默认半屏（`:98`，未传 sheetState） | **已确认缺陷** |
| 上滑不能带动内部滚动 | 内层 `verticalScroll`（`:125`）在 380dp 内是能滚的；但**手指落在按钮区/标题区时滚不动**，且外层是 sheet 的拖拽手势，容易互相抢 | 部分成立，需真机确认手感 |
| 底部操作区显示不完整 | 取消/确认在滚动区之外（`:298` 起），只要 sheet 被顶到屏幕外就看不到 | 结构问题，随 380dp 一起修 |
| 输入框点击后没有自动全选 | 点中间数字只是 `editing = true`（`:175`），随后 `appendDigit("200", '3')` → `"2003"` | **已确认缺陷** |
| 清空 200 后原值无法恢复 | `onDone` 非法时回填 `WaterQuickAdd.add("", MIN_ML)` = **1 ml**（`:242-245`），不是 200 | **已确认缺陷** |
| 快捷按钮是累加还是覆盖 | `WaterQuickAdd.add(pendingMl, ml)`（`:217`），`add` 自身 clamp 到 1..3000 | **已经正确，无需修改** |
| 日期时间选择器 | `AppDialog + WheelDatePicker / WheelTimePicker`（`:361-406`），日期限「最近一年、不许未来」 | **已经正确，可直接复用** |

补充发现（同一处）：

```kotlin
// HomeSheets.kt:295-297  两个 Spacer 叠在一起
Spacer(Modifier.height(12.dp))
// ---------- 取消 / 确认：唯一的写库时机（固定底部） ----------
Spacer(Modifier.height(16.dp))
```
→ 28dp 双倍间距，属可见的布局瑕疵。

```kotlin
// :318-321  确认按钮：空值直接 return，不给任何反馈
if (submitted) return@pressable
val ml = pendingMl.toIntOrNull() ?: return@pressable
if (!WaterQuickAdd.isValid(pendingMl)) return@pressable
```
→ 「清空后点确认」= 点了没反应（按钮仍是绿色可用态），需要明确规则。

### 2.5 Insets / Sheet / 滚动：把框架行为查清楚

这是本次审计最容易写错的地方，直接看 material3 1.3.1 源码：

```kotlin
// material3-1.3.1-sources.jar :: commonMain/.../ModalBottomSheet.kt:169
Box(modifier = Modifier.fillMaxSize().imePadding().semantics { isTraversalGroup = true }) {
```
```kotlin
// material3-1.3.1-sources.jar :: androidMain/.../ModalBottomSheet.android.kt:596-601
window?.setSoftInputMode(
    if (Build.VERSION.SDK_INT >= 30) SOFT_INPUT_ADJUST_NOTHING
    else SOFT_INPUT_ADJUST_RESIZE
)
```

结论（重要，直接影响修法）：

1. **M3 1.3.1 的 ModalBottomSheet 已经自己处理 IME**：API 30+ 用 `ADJUST_NOTHING` + 容器 `imePadding()` 把 sheet 整体顶到键盘上方。
   → **绝对不能再给 sheet 内容加 `imePadding()`**，否则就是工单 §8.2 明令禁止的「重复 Insets 导致异常留白」。
2. 工程侧 `MainActivity:50` 调了 `enableEdgeToEdge()`，`AndroidManifest.xml:32` 的 `adjustResize` 在 API 35 上**实际不生效**（窗口不再被顶起，IME 只作为 insets 下发）。所以「键盘挡住内容」的真正原因不是「没人处理 insets」，而是**内容本身没有滚动容器、高度写死**。
3. **API 26–29 存在双份抬升风险**（minSdk 26）：老系统走 `ADJUST_RESIZE` 重排窗口，同时 Compose 又 `imePadding()`。这条只能真机验证，本次不写成结论。

### 2.6 日期/时间格式化的现状

| 位置 | 写法 | 问题 |
|---|---|---|
| `HomeScreen.kt:549` | `Instant.ofEpochMilli(...).atZone(AppClock.zone).format(DateTimeFormatter.ofPattern("HH:mm"))` | 内联 |
| `HomeSheets.kt:287` | `DateTimeFormatter.ofPattern("HH:mm").format(pendingTime)` | 内联，重复 |
| `AppClock`（`domain/AppClock.kt`） | 只有 `today() / now() / zone / ofEpochMillisToLocalDate()` | **没有格式化工具** |

→ 食物一旦也要显示时间，必然出现第三份内联格式化。方案里补一个 `AppClock.formatClock(epochMillis)` / `formatDateLabel(LocalDate)`，两处改为调用（纯函数，零风险）。

### 2.7 数据模型：食物到底有没有时间字段

```kotlin
// data/local/entity/Entities.kt:129-150
data class FoodLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val mealType: String,
    val foodName: String,
    val grams: Double,
    val kcal: Double,
    ...
    val createdAt: Long = System.currentTimeMillis()
)
```

**没有 time 列，但有 `createdAt`（毫秒时间戳）**。三处佐证它够用：

1. 写入：`addFoodLog(FoodLog(...))` 默认 `createdAt = now`（`QingJiRepository.kt:966-971`）；
2. 读取：`FoodLogDao.observeByDate` = `ORDER BY createdAt ASC`（`Daos.kt:131`）→ **按时间排序天然成立**；
3. 备份往返：导出写 `createdAt`（`BackupManager.kt:379`），恢复读 `createdAt`（`ParsedBackup.kt:387`）→ **时间能跟着备份走，不会丢**。

对照饮水：`WaterLog` 同样只有 `createdAt`，而饮水已经用「选中的日期+时间 → 合成 createdAt」实现了精确到分钟的记录（`QingJiRepository.kt:1062-1065`）。

→ **食物加时间是同构改造，不需要动表结构。**

---

## 3. 问题原因分类（严格区分，不把推测写成事实）

### 3.1 已确认的代码缺陷（有源码证据）

| 编号 | 缺陷 | 证据 |
|---|---|---|
| D-01 | 训练时长点击不自动全选（`String` 受控，无 `TextFieldValue/TextRange`） | `TrainingActiveScreen.kt:415, 586-592`；全仓库无 `TextFieldValue` |
| D-02 | 训练时长回填把**秒**当**分钟**显示（1800 → 改一字变 600 分钟） | `:415-420` vs `:474` vs `TrainingItemMapper.kt:224` |
| D-03 | 训练距离回填把**米**当**公里**显示（5000.0），保存时超限丢距离 | `:421-423, 487-489` |
| D-04 | 训练时长弹**全键盘**、可输字母；非法字符保存成 0 但界面仍显示原文 | `:586-601`（无 keyboardOptions）；`:474` |
| D-05 | 训练时长清空后失焦不回填，界面留空白但业务值已是 0（显示与落库不一致） | `:588` 无 `onFocusChanged` |
| D-06 | 食物弹层内容不可滚动 + 默认半屏 + 无高度自适应 → 内容超出即被裁 | `AppDialogs.kt:68-133`（33 个调用点共用） |
| D-07 | 食物与饮水弹层「同一业务两套实现」：一个半屏一个全展开、默认克数一个 `100` 一个 `100.0` | `FoodLibraryScreen.kt:659` vs `FoodLibrarySearchScreen.kt:336,351` |
| D-08 | 食物重量不清空/不全选，`appendDigit` 追加 → 点 100 输 150 得 `100150` | `NumericKeypadInput.kt:139` + `NumericKeypadLogic.kt:22` |
| D-09 | 食物记录无任意日期、无时间；只有今天/昨天/前天 | `FoodLibraryScreen.kt:660, 691-707` |
| D-10 | 食物记录无「取消」「记录饮食详情」入口，只有单个「加入」按钮 | `AppDialogs.kt:111-132` |
| D-11 | 食物确认无防重复提交（饮水有 `submitted` 守卫，食物没有） | `AppDialogs.kt:118-121` vs `HomeSheets.kt:318` |
| D-12 | 饮水数字进入编辑态不清空，`appendDigit` 追加 → 点 200 输 350 得 `200350` | `HomeSheets.kt:175` + `WaterNumberKeypad.kt:61` |
| D-13 | 饮水清空后「完成」回填 1ml（`MIN_ML`），不是编辑前的原值 | `HomeSheets.kt:242-245` |
| D-14 | 饮水清空后点「确认记录」静默无响应（按钮仍显示可用） | `HomeSheets.kt:320-321` |
| D-15 | 饮水弹层 380dp 硬上限 + 默认半屏，自绘键盘出现时被裁 | `HomeSheets.kt:98, 124` |
| D-16 | 饮水弹层 12+16dp 双 Spacer | `HomeSheets.kt:295-297` |
| D-17 | 空值加/减规则不统一：`step` 空值按 `MIN_ML=1` 起算，`add` 空值按 `0` 起算 | `WaterQuickAdd.kt:15, 20` |
| D-18 | 无统一时间格式化工具，两处内联 `HH:mm` | `HomeScreen.kt:549`、`HomeSheets.kt:287` |

### 3.2 表现可确认、但原因需真机复现

| 编号 | 表现 | 为什么不能下结论 |
|---|---|---|
| R-01 | 「数字键盘弹出后弹窗被裁切」的具体视觉 | 两个弹层都不弹系统输入法；真正被裁的是自绘键盘 + 内容。裁多少取决于屏幕高度/字号缩放/手势导航，需真机量 |
| R-02 | 「上滑不能带动内部滚动」的手感 | 内层有 `verticalScroll`，外层有 sheet 拖拽；手势冲突属体感，需录屏 |
| R-03 | 定制输入法（小米/华为）下的表现 | 代码里没有 TextField，理论上无关；但搜索页带入的 IME 会残留（见 R-04），需真机确认 |
| R-04 | 从搜索页点食物时 IME 未收起（`onClick` 未 clearFocus），弹层在键盘之上被压缩 | 代码可证「未清焦点」，实际压缩幅度需真机 |

### 3.3 当前代码已经正确的部分（明确标记：无需修改）

| 编号 | 现状 |
|---|---|
| OK-01 | 饮水快捷按钮**已经是累加**（200 +100 → 300），且有单测 `WaterQuickAddTest.quickAddAccumulatesNotOverwrites` |
| OK-02 | 饮水「打开弹层不写库、取消/下拉/点遮罩都不写」的确认式语义（`:317-325`） |
| OK-03 | 饮水日期/时间滚轮 + 未来日期拦截（`:361-391`） |
| OK-04 | 饮水防重复提交 `submitted`（`:318`） |
| OK-05 | 训练多项目状态按 `draft.key`（UUID）隔离（`:415` 起，`TrainingViewModel.kt:351` 生成 UUID） |
| OK-06 | 训练空值/0 值不会让热量计算 NaN 或崩（`TrainingItemMapper.kt:218-221`） |
| OK-07 | 训练页滚动容器是 `Column + verticalScroll`（`:148`），不存在 LazyColumn 复用串值问题 |
| OK-08 | 食物 `createdAt` 已随备份导出/恢复，时间不会在换机时丢失 |
| OK-09 | `FoodLogDao.observeByDate` 已 `ORDER BY createdAt ASC`，按时间展示无需改 SQL |
| OK-10 | `MealCard` 已具备餐次分组/展开收起/营养展示/小计，可直接复用 |

### 3.4 必须真机确认的部分

- 定制输入法候选栏高度、字号 1.3×/1.5× 下的裁切；
- API 26–29 的 IME 双抬升；
- 手势导航 / 三键导航下的底部安全区；
- 小屏（≤ 640dp 高）下自绘键盘 + 操作区是否都能点到。

---

## 4. 工单 17 问逐条回答

**Q1 为什么训练时长默认 0 点击后没有自动全选？**
因为用的是 `OutlinedTextField(value: String)`（`TrainingActiveScreen.kt:586`），受控的是纯字符串，没有 `TextFieldValue`，也就没有「选区」这个概念。点击只会按落点摆放光标，`onValueChange` 直接 `minutes = it`，全仓库没有任何一处 `TextFieldValue/TextRange`（`grep` 0 命中）。→ **D-01**

**Q2 如何确保训练时长清空后最终仍然是 0？**
今天**已经是 0**：`durationSec()` 用 `toIntOrNull() ?: 0` 且 `coerceIn(0,600)`（`:474`），空串 → 0；`hasActualData()` 对 Cardio 判 `durationSec > 0`（`WorkoutItem.kt:275`）；热量侧 `durationSec <= 0` 直接返回 0 kcal（`TrainingItemMapper.kt:218`）。缺的只是**界面上把空串回填成 "0"**（失焦归一），以及**不能把空串当成"用户想填 0"之外的东西**。→ 只需补 `onFocusChanged`，业务值链路不用改。

**Q3 多个训练项目的输入状态是否独立？**
独立。每个 `DraftExercise.key` 是 UUID（`TrainingViewModel.kt:351`），`remember(draft.key)` 绑定；列表是 `Column + forEach`（`:148, :249`）而非 LazyColumn，不存在滚动复用串位；删除/排序导致槽位变化时 key 不匹配 → 状态重置 → 从各自的 `actualJson` 重读。**但**重置后重读会踩到 D-02/D-03 的单位错误，等于「不串值但会串单位」。

**Q4 训练时长如何进入最终训练记录和热量计算？**
两条**互不相干**的链路，必须分清：
- 会话总时长：`WorkoutSession.durationMinutes` 来自计时器或「改时长」override（`TrainingViewModel.kt:537, 550-561`），**与卡片里填的分钟无关**；
- 单项时长：卡片分钟 → `durationSec = min*60` → `workout_cardio_detail.durationSeconds`（`TrainingItemMapper.kt:159`）→ 有氧热量 `withCardioKcal`（MET × 体重 × 分钟，`:207-236`）；`ExerciseLedgerCalculator:69` 与 `TrainingAnalysisEngine:83-87` 再从明细汇总。
→ 改输入交互**不会**影响会话时长口径，这点必须守住。

**Q5 为什么食物弹窗键盘弹出后内容被裁切？**
三点叠加，且与「系统键盘」无关：
1. `AppBottomSheet` 内容是裸 `Column`，**没有滚动容器**（`AppDialogs.kt:93-133`）；
2. 默认 `skipPartiallyExpanded = false` → 一打开只有**半屏**（`:76`）；
3. 内容实际高度约 560–600dp（标题 + 餐次 + 日期 + 常驻数字键盘 208dp + 加入按钮 + 28dp 底部内边距），半屏放不下 → 直接被裁，且手指上滑只会拖 sheet，不会滚内容。
另外：`NumericKeypadInput` **常驻**显示，不是"弹出"，所以「键盘弹出来才裁」的表述不准确——它一直是裁的。→ **D-06 / D-07**

**Q6 为什么向上滑动无法正常滚动？**
`AppBottomSheet` 的内容根本不是滚动容器。M3 的 sheet 只把拖拽手势绑到 sheet 自身的 `nestedScroll` 上，内容没有可消费的滚动 → 手势全部转成「拖动整张 sheet」。sheet 一旦已经展开到最大，就什么也不动了。→ 与 Q5 同源。

**Q7 为什么饮水弹窗也存在相同问题？**
它比食物弹窗多了一层 `verticalScroll`（`HomeSheets.kt:125`），所以**内部是能滚的**；问题在别处：
- `heightIn(max = 380.dp)` 是硬上限，自绘键盘（`editing = true` 时出现，约 208dp）一进来就把可用高度吃满；
- sheet 本身也是默认半屏（`:98` 未传 sheetState），380dp + 按钮区 ≈ 450dp，半屏放不下；
- 手指落在标题行/按钮行时滚不动（那些区域不在滚动容器里）。
→ **同一个根因的两个变体：内容没有「按可用高度自适应」的滚动区。** → **D-15**

**Q8 为什么饮水输入没有自动全选？**
饮水根本没有输入框，用的是自绘键盘；点中间数字只做 `editing = true`（`:175`），不清空也不置"下次输入替换"标志，`appendDigit` 就是字符串拼接（`NumericKeypadLogic.kt:22`）。所以点 200 输 350 得到 `200350`。→ **D-12**

**Q9 为什么清空后不能恢复原来的 200 ml？**
因为**根本没有记录"原值"**：`pendingMl` 只有当前值一个状态（`:82`），`onDone` 里非法就回填 `WaterQuickAdd.add("", MIN_ML)` = 1（`:242-245`）。→ 需要 `originalValue / editingText / committedValue` 三态。→ **D-13**

**Q10 快捷饮水按钮当前是累加还是覆盖？**
**累加**，且已 clamp 到 1..3000（`:217` + `WaterQuickAdd.kt:14-17`），单测已覆盖。**无需修改**（OK-01）。

**Q11 饮水弹窗的日期时间选择器在哪里实现？**
`HomeSheets.kt:361-391`（日期）与 `:393-406`（时间），底层是 `AppDialog + WheelDatePicker/WheelTimePicker`（`ui/components/WheelTimePicker.kt:40`）。日期规则：不晚于今天、不早于今天-365 天。

**Q12 饮水弹窗下半部分操作区能否复用？**
能，但要**抽出来复用**而不是复制：现在是「滚动内容区 + 固定按钮区（取消 1 : 确认 1.6）+ 可选详情入口」（`:298-357`）。建议抽成 `SheetActionBar(primaryText, onPrimary, secondaryText, onSecondary, extraText, onExtra)`，饮水与食物共用同一份。样式 token（`QingJiGreen` / `QingJiGreenLight` / 10dp 圆角 / 14dp 纵向内边距）原样保留。

**Q13 首页今日记录餐食组件在哪里？**
`HomeCards.kt:490` 的 `internal fun MealCard(type, items, onDelete, onOpenDetail, showMacro, expanded, onToggleExpand)`，在 `HomeScreen.kt:368-390` 按 `MealRules.ALL` 顺序渲染四餐，展开态用 `"${state.date}|$type"` 做 key。内部已包含：餐次图标+语义色、`小计 N 千卡`、食物行（名称/克数/热量/蛋白）、`查看全部`（>3 条时 300dp 内滚）、`SwipeToRevealDelete`、宏观比例条。

**Q14 饮水详情页能复用哪些页面组件？**
`HomeScreen.kt:493-568` 是 `AppBottomSheet` + 「大字数值 + ThinProgressBar + 完成百分比 + 逐笔明细（`HH:mm` + `N ml`）」。可复用的是**页面组织**（标题 → 主数值 → 进度 → 明细列表 → 单按钮）与 `ThinProgressBar`；食物详情页的**内容**必须换成 `MealCard`，不能复用饮水 VM/模型。

**Q15 食物记录如何增加时间字段或复用现有时间戳？**
复用 `createdAt`，**不改表**。三处已就绪：写入可指定（`addFoodLog` 接受整个 `FoodLog`）、排序已按 `createdAt`、备份已含 `createdAt`。照抄饮水 `addWater(ml, date, time)` 的做法（`QingJiRepository.kt:1062-1065`）：`createdAt = LocalDateTime.of(date, time).atZone(AppClock.zone).toEpochMilli()`。→ 无 Entity 改动、无 DAO 改动、**无 Migration**。

**Q16 如何实现饮食详情页并保持首页餐食样式一致？**
新建**日级**详情页（与现有**单餐**详情 `MealDetailScreen` 并存，不改造它以免回归）：`repo.observeFoodLogs(date)`（`QingJiRepository.kt:253`）→ 按 `mealType` 分组 → 直接复用 `MealCard` 四次 → 顶部用 `PageTopBar` + 日期行 + 汇总（总热量/三宏量用同一份 `sumOf` 口径）。**禁止**另写热量公式：`MealCard` 的 `subtitle = items.sumOf { it.kcal }`，详情页汇总也用 `logs.sumOf { it.kcal }`，同源同口径。

**Q17 如何在进入详情页后返回原弹窗并保留未确认草稿？**
现状**做不到**：草稿全部是 `remember`（`FoodLibraryScreen.kt:658-660`），一旦导航到别的 route，该 composable 被移出组合，`remember` 状态销毁（只有 `rememberSaveable` 会被 NavHost 的 `SaveableStateHolder` 保存）。
两个可行方案（阶段 B 择一，报告 §6.5 给了推荐）：
- **A（推荐）**：草稿上提为 `FoodRecordDraft`（纯数据类，`@Parcelize` 或自定义 `Saver`），在 `FoodLibraryScreen` 里 `rememberSaveable`，弹层只是「渲染 + 回调」；
- **B（更省事）**：详情页也用 sheet 叠在记录弹层之上（同级 dialog），记录弹层不销毁，草稿天然保留。
两者都**不允许**用「先自动保存再打开详情」来绕（工单明令禁止）。

---

## 5. 工单需求逐条映射（A–G + §14 验收清单）

### 5.1 模块 A（开始训练）

| 需求 | 现状 | 判定 |
|---|---|---|
| 首次进入显示 0 | `durationSec=0 → "0"`（`:415`） | ✅ 已满足 |
| 点击 0 自动全选 | 无 `TextFieldValue` | ❌ 需改 |
| 直接输入 30 显示 30 | 现在会得 `030`/`300` | ❌ 需改 |
| 编辑中允许空 | `onValueChange` 直接赋值，允许 | ✅ 已满足 |
| 清空后失焦恢复 0 | 无 `onFocusChanged`，界面留空 | ❌ 需改 |
| 清空后保存按 0 | `toIntOrNull() ?: 0` | ✅ 已满足（业务层） |
| 主动输入 0 正常 | `"0" → 0` | ✅ 已满足 |
| 不出现 030 / 追加 | 现在就是追加 | ❌ 需改 |
| 多项目独立 | UUID key 隔离 | ✅ 已满足（但受 D-02 影响） |
| 不破坏 MET/热量/保存 | 链路与本改动解耦 | ✅ 守住即可 |
| 回填口径正确（工单未列，审计补） | 秒当分钟、米当公里 | ❌ **必修（P0）** |

### 5.2 模块 B（食物弹窗）

| 需求 | 现状 | 判定 |
|---|---|---|
| 弹窗可完整显示 | 半屏 + 不可滚 | ❌ |
| 内容可滚动 | 无滚动容器 | ❌ |
| 食物名称 / 餐次 | 有 | ✅ |
| 记录日期 | 仅今天/昨天/前天 | ❌ |
| 记录时间 | 完全没有 | ❌ |
| 重量输入 + 加减 | 有（step 10g） | ✅（但 D-08） |
| 点击 100 全选 | 追加 | ❌ |
| 清空后恢复原重量 | 无原值概念 | ❌ |
| 加减后显示值=保存值 | `NumericKeypadLogic.step` 夹紧 1..3000 且回写同一 state | ✅ |
| 底部三入口（取消/确认/详情） | 只有「加入」 | ❌ |
| 视觉参考饮水操作区 | 两套样式 | ❌ |

### 5.3 模块 C（饮水弹窗）

| 需求 | 现状 | 判定 |
|---|---|---|
| 今日已喝 / 目标 | 有（`:137-141`） | ✅ |
| 数值输入 + 加减 | 有 | ✅ |
| +100/+200/+300 累加 | 已累加 | ✅ |
| 日期时间信息 | 有 | ✅ |
| 取消 / 确认 / 查看详情 | 有 | ✅ |
| 点击 200 全选 | 追加 | ❌ |
| 清空后取消编辑恢复 200 | 回填 1 | ❌ |
| 清空后确认的明确规则 | 静默无响应 | ❌ |
| 高度自适应 + 可滚 | 380dp 硬限 + 半屏 | ❌ |

### 5.4 模块 D（统一数字输入状态）

| 需求 | 现状 | 判定 |
|---|---|---|
| original / editing / committed 三态分离 | 完全没有（各自只有一个 String） | ❌ 新建 |
| 首次聚焦全选、重组不重复全选、尊重用户光标 | 无 | ❌ 新建 |
| 空值不即时回填（避免无法输多位） | 现在**没有**即时回填，这点是对的 | ✅ 保持 |
| 主动输入 0 ≠ 空串 | 业务层可区分（`toIntOrNull` vs null），UI 层未区分 | ⚠️ 需显式建模 |
| 三处复用同一底层、各自保留业务规则 | 训练用 TextField、食物/饮水用自绘键盘 | ⚠️ 抽公共状态机 + 两种渲染 |

### 5.5 模块 E / F（统一交互 + 详情页）

| 需求 | 现状 | 判定 |
|---|---|---|
| 食物复用饮水日期/时间选择器 | 无 | ❌ 复用 `WheelDatePicker/TimePicker` |
| 时间只改草稿、取消不写库 | 无草稿概念 | ❌ |
| 确认才提交、不重复提交 | 无 `submitted` 守卫 | ❌ |
| 详情页默认查当前选择日期 | 无详情页 | ❌ 新建 |
| 详情页浏览不触发保存 | — | ❌ 新建时保证 |
| 返回保留草稿 | `remember` 会丢 | ❌ 见 Q17 |
| 详情页复用首页餐食样式/营养口径 | `MealCard` 可复用 | ✅ 具备条件 |
| 空状态 / 不串日期 | `observeFoodLogs(date)` 天然按日 | ✅ 具备条件 |

### 5.6 模块 G（键盘与弹窗自适应）

| 需求 | 现状 | 判定 |
|---|---|---|
| 弹窗按 IME 调整 | M3 sheet 已 `imePadding`（库源码） | ✅ 框架已做，**不要重复加** |
| 内容可滚、焦点可见、底部按钮可访问 | 无滚动区 | ❌ |
| 收键盘恢复、不跳动、无重复留白 | 380dp 硬限会导致跳动 | ❌ |
| 不简单叠加 `imePadding` | 现状未叠加（正确） | ✅ 守住 |
| 适配小屏 / 导航模式 / 字体缩放 | 未验证 | ⏳ 真机 |

### 5.7 §14 验收清单（27 项）

| # | 项 | 判定 |
|---|---|---|
| 1 | 训练时长默认 0 | ✅ 已满足 |
| 2 | 点击 0 可直接替换 | ❌ |
| 3 | 空值归一化 0 | ✅ 业务层已满足 |
| 4 | 多项目状态独立 | ✅ |
| 5 | 训练记录/热量无回归 | ✅ 现状可保持（但 D-02/D-03 必须先修） |
| 6 | 食物弹窗键盘遮挡修复 | ❌ |
| 7 | 饮水弹窗键盘遮挡修复 | ❌ |
| 8 | 两弹窗内滚 | ❌ |
| 9 | 食物重量自动全选 | ❌ |
| 10 | 饮水数值自动全选 | ❌ |
| 11 | 饮水清空恢复原值 | ❌ |
| 12 | 食物清空恢复原值 | ❌ |
| 13 | 饮水快捷累加 | ✅ 已正确 |
| 14 | 食物日期选择 | ❌ |
| 15 | 食物时间选择 | ❌ |
| 16 | 食物复用饮水操作区 | ❌ |
| 17 | 食物取消入口 | ❌ |
| 18 | 食物确认入口 | ⚠️ 有「加入」，但无二次校验与防重 |
| 19 | 食物详情入口 | ❌ |
| 20 | 详情页参考饮水详情组织 | ❌ 新建 |
| 21 | 详情页用首页餐食样式 | ✅ 组件可复用，页面待建 |
| 22 | 历史日期查询正确 | ✅ DAO 已支持，页面待建 |
| 23 | 返回弹窗草稿保留 | ❌ |
| 24 | 不重复保存 | ❌ |
| 25 | 三模块历史记录无回归 | ⏳ 回归测试保证 |
| 26 | 自动化测试通过 | ⏳ 阶段 C |
| 27 | 真机录屏 | ⏳ 阶段 C |

---

## 6. 最小修改方案（阶段 B 依据）

### 6.1 新增文件

| 文件 | 作用 | 说明 |
|---|---|---|
| `domain/input/NumericInputState.kt` | 纯 Kotlin 状态机：`originalText / editingText / isEditing / committedValue`、`beginEdit()`、`onTextChange()`、`endEdit()`、`applyDelta()`、`commit()` | **不依赖 Compose**，N01–N16 / T01–T10 全部可用 JVM 单测覆盖 |
| `ui/components/SelfSelectingNumberField.kt` | Compose 封装：`OutlinedTextField(TextFieldValue)` + `onFocusChanged` 首次全选 + 失焦归一 + `KeyboardType.Number` | 只用于系统键盘场景（训练页） |
| `ui/components/SheetScaffoldContent.kt` | 用 `SubcomposeLayout` 先量 header/footer，剩余高度给可滚 body | 供 `AppBottomSheet` 与 `QuickWaterSheet` 共用 |
| `ui/components/SheetActionBar.kt` | 取消 / 主操作 / 次入口 三段式底部操作区 | 饮水现状原样抽出，食物复用 |
| `ui/screen/food/FoodRecordSheet.kt` | 统一食物记录弹层：餐次 + 日期 + 时间 + 重量 + 三入口 | 替换 `AddToMealDialog` 与 `SearchAddToMealSheet` |
| `ui/screen/food/FoodDayDetailScreen.kt` | 日级饮食详情页（四餐 `MealCard` + 汇总 + 空态） | 新 route |
| `ui/viewmodel/FoodDayDetailViewModel.kt` | `bind(date)` + `observeFoodLogs(date)` + 分组 | 仿 `MealDetailViewModel` |

### 6.2 修改文件

| 文件 | 改动 | 范围 |
|---|---|---|
| `TrainingActiveScreen.kt` | `minutes/distanceKm/stepsInput/workSec/restSec/rounds` 改用新数字组件；**修正秒→分钟、米→公里回填**；失焦归一 | 仅 `NonStrengthEditorCard`，不动计时/保存 |
| `HomeSheets.kt`（`QuickWaterSheet`） | `pendingMl` 改由 `NumericInputState` 托管（首次输入替换）；`onDone` 非法回原值；去掉 12+16 双 Spacer；380dp 改自适应滚动区；空值时确认按钮给出明确反馈 | 仅该弹层 |
| `AppDialogs.kt`（`AppBottomSheet`） | 新增 `scrollableContent: Boolean = false`；`skipPartiallyExpanded` 默认值不变；按钮区抽成 `SheetActionBar` | **默认行为不变**，其余 31 个调用点零影响 |
| `FoodLibraryScreen.kt` | 删除本地 `AddToMealDialog`，改用 `FoodRecordSheet`；草稿上提为 `rememberSaveable`；新增「记录饮食详情」跳转 | 只动 pick 相关 |
| `FoodLibrarySearchScreen.kt` | 改用 `FoodRecordSheet`；打开前 `focusManager.clearFocus()` 收 IME | 删一份重复实现 |
| `FoodLibraryViewModel.kt` | `addToMeal(..., date: LocalDate, time: LocalTime)`（旧参数保留默认值）→ 写 `createdAt` | 1 个方法 |
| `FoodLibrarySearchViewModel.kt` | 同上（`addFoodToMeal` / `addDishToMeal`） | 2 个方法 |
| `Destinations.kt` + `QingJiAppRoot.kt` | 新增 `FoodDayDetail = "food_day_detail/{date}"` 与 composable | 1 路由 |
| `domain/AppClock.kt` | 加 `formatClock(epochMillis)` / `formatDateLabel(LocalDate)` | 纯函数 |
| `HomeScreen.kt` | 饮水明细改用 `AppClock.formatClock` | 1 行 |

### 6.3 复用方案（不重复造）

| 能力 | 复用自 |
|---|---|
| 日期/时间选择 | `WheelDatePicker` / `WheelTimePicker` + `AppDialog`（`HomeSheets.kt:361-406` 的写法） |
| 底部操作区 | 饮水弹层 `:298-357` 抽成 `SheetActionBar` |
| 数字键帽 UI | `WaterNumberKeypad` / `NumericKeypadInput`（保留，改内部调用新的状态机） |
| 餐食展示 | `MealCard`（`HomeCards.kt:490`），**零改动** |
| 页面骨架 | `PageTopBar` + `Dimens` token |
| 数据查询 | `repo.observeFoodLogs(date)` + `FoodLogDao.observeByDate` |
| 时间落库 | `QingJiRepository.addWater(ml, date, time)` 的 createdAt 写法 |

### 6.4 数据链路影响 & 数据库影响

| 项 | 结论 |
|---|---|
| Room Entity | **不改**（`FoodLog` 已有 `createdAt`） |
| DAO | **不改**（`observeByDate` 已 `ORDER BY createdAt ASC`） |
| Migration | **不需要**（无 schema 变化） |
| 版本号 | **不升**（`AppDatabase` 版本不变） |
| 写入链路 | `FoodLog(date=选中日, createdAt=选中日+选中时)`，与饮水完全同构 |
| 备份 | **不改**（`createdAt` 已导出/已恢复，`BackupManager.kt:379` / `ParsedBackup.kt:387`） |
| 统计口径 | 不受影响：所有汇总按 `date` 过滤，`createdAt` 只用于同日内排序与时间展示 |
| 兼容性 | 老数据 `createdAt` = 当时写入时刻，落在当天范围内，展示为合理时间；不迁移、不改写 |

> 如果阶段 B 有人提议「加 `time` 列」，本报告明确反对：违反工单 §10.11–10.13，且 `createdAt` 已能满足「精确到分钟 + 跟随备份 + 已排序」。

### 6.5 草稿保留方案（Q17 的落地选择）

**推荐 A：草稿上提 + `rememberSaveable`**

```kotlin
// 伪代码，阶段 B 实现
@Parcelize
data class FoodRecordDraft(
    val foodName: String, val basis: String, val servingGrams: Double,
    val meal: String, val grams: String,          // 重量保持字符串，允许中间态
    val date: String, val time: String,           // ISO，便于 Saver
    val originalGrams: String                     // 取消/失焦回填用
) : Parcelable

// FoodLibraryScreen
var draft by rememberSaveable { mutableStateOf<FoodRecordDraft?>(null) }
```
- 打开记录弹层 → `draft = 新草稿`；
- 点「记录饮食详情」→ **保留 draft**，`navigate(foodDayDetail(draft.date))`；
- 返回 → `LaunchedEffect` 检测 draft 非空 → 重新弹出记录弹层，值原样；
- 点「取消」→ `draft = null`（不写库）；
- 点「确认」→ 写库成功后 `draft = null`。

**备选 B**：详情页作为第二个 sheet 叠在记录弹层上（同级 dialog）。实现最省，草稿天然不丢；代价是两层遮罩叠加、返回手势语义略怪。若 A 在真机上出现 NavHost 恢复异常，退回 B。

### 6.6 测试计划（对齐 §11 的 N/T/S）

| 层 | 内容 | 可行性 |
|---|---|---|
| JVM 单测（主） | `NumericInputStateTest`：N01–N16（全选语义/替换/空值/回原值/累加/重组稳定/非法输入） | ✅ 纯逻辑，无 Compose |
| JVM 单测（主） | `TrainingDurationInputTest`：T01–T10（0 默认/替换/空→0/多项目隔离/非法值/保存值=显示值） | ✅ 逻辑层 + 现有 `TrainingViewModel` 测试基建 |
| JVM 单测 | `FoodRecordDraftTest`：S13–S19（改日期/时间只动草稿、取消不写、确认写一次、防重） | ✅ |
| JVM 单测 | `FoodDayGroupingTest`：S21–S25（按日期查、餐次分组、空态、与首页口径一致） | ✅ 用内存 Room（已有 `BackupExportScopeTest` 先例） |
| Compose UI（可选） | S01–S12（弹层布局/滚动/键盘/小屏） | ⚠️ 仓库已有 `HomeInteractionTest` 且被 `@Ignore`（Robolectric 依赖下载问题）；**不作为门禁**，真机截图为准 |
| 仪器测试 | 无现成基建（`androidTest` 仅 1 个迁移测试） | ❌ 不新增，避免拖慢 CI |
| 构建 | `compileDebugKotlin` + `testDebugUnitTest`（全量）+ `assembleDebug` + `assembleRelease` | ✅ 沿用上轮范式 |

### 6.7 风险分析

| 风险 | 等级 | 缓解 |
|---|---|---|
| 改 `AppBottomSheet` 影响其余 31 个调用点 | 高 | 新增参数默认关闭；只对 3 处开启；全量单测 + 关键页截图 |
| 修「单位回填」改变既有数据显示 | 中 | 只影响「编辑已有有氧训练」；补单测锁定 `durationSec/60`；历史数据本身不动 |
| `SubcomposeLayout` 引入测量复杂度 | 中 | 封装在一个组件内，配 2 个 Robolectric/真机截图；失败可退回 `fillMaxHeight + weight` |
| 食物弹层替换导致搜索页行为变化 | 中 | 两处统一到同一组件，先替换搜索页（调用轻）再替换标准库 |
| 老系统（API 26–29）IME 双抬升 | 中 | 不加 `imePadding`；真机（API 29 模拟器）验证一次 |
| 详情页导航导致草稿丢失 | 中 | 方案 A + 真机「进详情→返回」连续 3 次验证 |
| `createdAt` 被别处当「写入时刻」使用 | 低 | 已核查：仅备份与排序使用，无业务依赖 |
| 字号缩放 1.5× 下自绘键盘溢出 | 中 | 真机验证；必要时键高改 `minHeight` 而非固定 46dp |

---

## 7. 本阶段结论

- 工单列出的问题里，**13 项是已确认的代码缺陷**，**10 项现状已正确**（快捷累加、防重复提交、日期滚轮、多项目隔离、空值热量安全等），**4 项需要真机复现**才能定因。
- 工单有一处前提需要修正：**两个弹层都不会唤起系统输入法**（只有自绘键盘），所以「键盘遮挡」的真实机制是「内容无滚动容器 + 默认半屏 + 高度写死」，而不是「缺 `imePadding`」。按错误前提去加 `imePadding()`，会立刻踩上「重复 Insets 留白」。
- 审计额外发现 **3 个工单未覆盖的 P0 级问题**（训练时长秒/分钟、距离米/公里、食物的确认无防重），其中前两个会让「修好全选」变成数据损坏的加速器，必须在模块 A 一并修。
- 数据库**零改动**：食物时间用 `createdAt`，与饮水既有做法同构，备份链路已闭环。

---

**阶段 A 只读审计已完成，未修改代码。现等待确认，未进入实施阶段。**
