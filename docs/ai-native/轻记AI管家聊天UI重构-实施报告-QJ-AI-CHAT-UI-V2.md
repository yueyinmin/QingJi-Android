# 轻记 AI 管家 V2 · 聊天界面整体 UI/UX 重构与键盘交互专项 — 实施报告

- 工单编号：QJ-AI-CHAT-UI-V2
- 实施分支：`ai-native-on-main`（基于 `origin/main 5caaa99`，v1.4.5 / 145）
- 实施时间：2026-11-26
- 范围：AI 管家聊天页（`ui/screen/AiChatScreen.kt` + 新增 `ui/screen/aichat/**`）

---

## 零、先说三条结论

1. **原有 AI 能力一条没删**：会话新建/切换/改名/删除、图片附件、系统语音识别、发送/停止、失败重试、上下文传递、长期记忆、管家提醒、流式回复全部保留，只有承载方式变了。
2. **键盘问题不是「调按钮宽度」能解决的**：根因是 `MainActivity` 开了 `enableEdgeToEdge()`，窗口不再被 IME 顶起，而全 App 的 `Scaffold` 已经统一给了导航栏内边距。聊天页此前既没有 IME 处理，也不该直接加 `imePadding()`（会和导航栏 padding 叠加成双份空白）。现在只补「IME 高度 − 导航栏高度」的差值。
3. **截图来自实际运行的应用**（Android 模拟器 API 37 / 1080×2400 / 420dpi，debug 包），不是设计稿。其中「AI 回复中」必须连一个真的会分片返回的服务才看得到中间态，用本地假服务验证，详见第五节。

---

## 一、阶段 A：实施前代码审计

### 1.1 页面入口与调用关系

| 层 | 文件 | 说明 |
|---|---|---|
| 导航宿主 | `ui/navigation/QingJiAppRoot.kt:696` | `Scaffold`（默认 `contentWindowInsets = systemBars`）+ `NavHost`，内容区已经被统一 `padding(innerPadding)` |
| 页面 | `ui/screen/AiChatScreen.kt`（改造前 562 行） | 顶栏、会话胶囊行、消息列表、提醒卡、快捷问题、输入框、4 个 Material Button、2 个对话框、气泡组件全挤在一个文件 |
| 状态 | `ui/viewmodel/AiChatViewModel.kt`（524 行） | `AiChatUiState` / `ChatMessageUi`；`messages` 在无持久化消息时合成一条 `WELCOME` |
| 存储 | `data/ai/AiChatStore.kt` + `RoomAiChatStore` | 会话列表 Flow、分页消息 Flow、删除（含 K 阶段深删） |
| 组件 | `ui/components/PageTopBar.kt`、`NudgeCard.kt`、`AppBottomSheet`（`AppDialogs.kt`） | 可复用 |
| 主题 | `ui/theme/{Color,AccentThemes,Dimens,TextSizes}.kt` | 颜色全是 `get()` 属性，跟随深色/换肤；`QingJiGreen/Light/Dark` 三档 |

### 1.2 改造前的布局结构（自上而下）

```
Column
├ PageTopBar(title, actionText="新会话")             ← 只有文字入口
├ Row(horizontalScroll) 会话胶囊 + 改名 + 删除 + 记忆 + 提醒   ← 7~10 个胶囊挤一行
├ LazyColumn(weight=1)
│   ├ 「加载更早消息」大胶囊
│   ├ 「上下文：首页」独立胶囊（单独占一行 + 12dp 边距）
│   └ ChatBubble × N（fillMaxWidth(0.86f) 满宽卡片）
├ NudgeCard
├ Row(horizontalScroll) 快捷问题 ×3                    ← 与顶部重复的入口感
├ 附件提示文字
├ 「已记住…」提示行
├ OutlinedTextField(maxLines=3)
├ Row: Button(图片, weight .6) Button(语音, .6) Button(发送/停止, 1) [Button(重试, 1) 条件插入]
└ 错误文字
```

### 1.3 输入框与键盘处理机制（改造前）

- `AndroidManifest.xml`：`android:windowSoftInputMode="adjustResize"`；
- `MainActivity.kt:50`：`enableEdgeToEdge()` → `decorFitsSystemWindows=false`，**`adjustResize` 实际不再生效**，IME 不会把窗口压小；
- 全工程 `grep imePadding` 只在 `FoodLibrarySearchScreen` / `OnboardingScreen` / `SleepScreen` 出现，**聊天页一处都没有**；
- 结论：键盘弹出时输入区被直接盖住；而如果简单补 `imePadding()`，由于父级已用 padding 让出导航栏高度，会出现「键盘上方多出一条导航栏高度空白」。

### 1.4 现有布局问题的代码根因

| 现象 | 代码根因 |
|---|---|
| 顶部会话管理拥挤、排列混乱 | 会话、改名、删除、记忆、提醒共 5 类入口全部塞进同一个 `Row(horizontalScroll)`，且「改名/删除」只作用于当前会话 |
| 会话/新建/改名/删除入口重复 | 顶栏文字「新会话」+ 会话行里隐含的当前会话切换，语义重叠；快捷问题又在顶部（无）与底部各出现一次 |
| 消息卡片过大、空间利用率低 | `ChatBubble` 用 `fillMaxWidth(0.86f)`，短消息也占 86% 宽度；每条都带「我 / AI 管家」标签 + 12dp 内边距 |
| 上下文标签割裂 | 作为 LazyColumn 的一个独立 item，带 `padding(vertical = 8.dp)` + 大胶囊背景 |
| 底部输入区割裂 | 输入框、按钮行、快捷问题、附件提示、记忆提示分属 5 个平级控件，各自有左右 16dp padding |
| 按钮尺寸不统一、文字换行 | 4 个 `Material3.Button` 按 `weight` 分宽，`Button` 有最小宽度与 24dp 横向内边距：360dp 宽屏下每颗只有 ~78dp，「停止生成」四个字必然折行 → Row 变高 → 聊天区跳动 |
| 重试按钮改变整体结构 | `if (!isRunning && messages.any { it.isError }) Button(重试, weight 1f)` 会把 3 按钮变 4 按钮，整行重新分配宽度 |
| 键盘弹出后布局变形 | 无 IME insets 处理（见 1.3） |
| 上下滚动被强制拉回 | `LaunchedEffect(messages.size) { animateScrollToItem(lastIndex) }` 无条件执行，用户上翻历史时也会被拽回 |
| 快捷问题被裁切不好点 | 12sp 文字 + 6dp 上下 padding ≈ 30dp 高，低于 40dp 触控基线；且 `horizontalScroll` 末项贴边被裁 |

### 1.5 可复用 / 需重构 / 重复项

- **直接复用**：`AppBottomSheet`（统一弹层）、`NudgeCard`、主题颜色与 `Dimens/TextSizes`、`material-icons-extended`（已在依赖里，无需新增）。
- **需要重构**：`AiChatScreen`（拆成编排层 + 4 个子组件）、`ChatBubble`（宽度/标签/重试位置）、输入区（合并为单一容器）。
- **重复/多余**：快捷问题两处渲染；`ChatMessageUi` 与 `AiChatUiState.messages` 计算属性本身合理（保留）；`state.conversations` 与 `activeConversationId` 无冗余（保留）。
- 未发现重复状态源：会话列表、消息、输入、附件都只有一份来源。

---

## 二、代码改造清单（A）

### 2.1 新增文件

| 文件 | 作用 |
|---|---|
| `ui/screen/aichat/ChatDesign.kt` | 聊天页尺寸 token（56dp 顶栏、40dp 图标触控、18dp 气泡、22dp 输入容器、5 行上限、气泡最大宽度比例）；颜色一律取自主题变量，不新造色板 |
| `ui/screen/aichat/AiChatTopBar.kt` | 顶栏：返回 / 标题 / 新建会话 / 更多，高度恒定 56dp |
| `ui/screen/aichat/ChatMessageList.kt` | 消息列表：来源标签（轻量小字）、消息、快捷问题（列表末项）、加载更早 |
| `ui/screen/aichat/ChatBubble.kt` | 气泡：用户右侧浅色 + 收角、AI 左侧白卡 + 收角、失败气泡内嵌「重试」 |
| `ui/screen/aichat/ChatComposer.kt` | 统一输入容器：自动增高输入框（maxLines=5，超出内部滚动）+ 图片/语音/发送圆形按钮 |
| `ui/screen/aichat/QuickPrompts.kt` | 快捷问题胶囊（40dp 触控高度，横向滑动） |
| `ui/screen/aichat/ConversationListSheet.kt` | 历史会话底部弹层：选中态 + 每行「⋮」→ 改名/删除（删除保留二次确认） |
| `ui/screen/aichat/ChatMoreSheet.kt` | 「更多」面板：历史会话 / 长期记忆 / 管家提醒 |
| `ui/screen/aichat/ChatScrollPolicy.kt` | 滚动跟随策略（纯函数，可单测） |
| `app/src/debug/res/xml/network_security_config.xml` + `app/src/debug/AndroidManifest.xml` | **仅 debug**：放行 `10.0.2.2 / 127.0.0.1 / localhost` 明文，用于连本地假服务验证「流式回复中」。release 不包含 |
| `app/src/test/java/com/qingji/app/ui/screen/aichat/ChatScrollPolicyTest.kt` | 滚动策略与列表下标计算测试（6 例） |

### 2.2 修改文件

| 文件 | 改动目的 |
|---|---|
| `ui/screen/AiChatScreen.kt` | 562 行单文件重写为编排层：顶栏 + 列表 + 底部区三段式；IME 差值处理；滚动跟随；会话/更多弹层与两个对话框；图片/语音 launcher 逻辑逐字保留 |
| `ui/viewmodel/AiChatViewModel.kt` | 新增 `retryFrom(errorMessageId)`（按失败消息定位问题，不误重发别的问题）、`clearAttachment()`；`retry()` 行为不变 |
| `ui/viewmodel/AiChatViewModelTest.kt` | 新增 2 例：`clearAttachment` 只清附件、`retryFrom` 重发对应问题 |

### 2.3 删除文件

无。`AiChatScreen.kt` 内的旧 `ChatBubble` 私有实现随重构移除（被 `aichat/ChatBubble.kt` 取代），业务逻辑无删除。

---

## 三、问题修复对照（B）

| # | 工单问题 | 根因 | 修复 | 结果 |
|---|---|---|---|---|
| 1 | 顶部会话管理拥挤 | 5 类入口挤在一行横滑 | 顶栏只留「新建会话 + 更多」；会话列表独立弹层 | 顶栏 3 个图标，恒高 56dp |
| 2 | 新建/改名/删除排列混乱 | 改名删除只作用于当前会话且与切换混排 | 改名/删除下放到每条会话的「⋮」菜单，删除保留二次确认（含记忆条数勾选） | 每条会话可独立改名/删除 |
| 3 | 存在重复新建入口 | 顶栏文字 + 快捷方式语义重叠 | 全页唯一新建入口 = 顶栏 `＋`；会话弹层内不再放新建 | 无重复 |
| 4 | 消息卡片过大 | `fillMaxWidth(0.86f)` + 每块 12dp 内边距 | 气泡改为 `widthIn(max = 82% / 94%)` 的自适应宽度，短消息自动收窄；用户侧去标签 | 短消息不再占满屏宽 |
| 5 | 上下文标签割裂 | 独立 item + 大胶囊 + 12dp 边距 | 压成 11sp 小字「来源 · 首页」，紧贴列表顶部 | 垂直占用从 ~46dp 降到 ~17dp |
| 6 | 欢迎消息占屏过多 | 满宽卡片 + 上下大留白 | 内边距收到 12/11dp；欢迎态快捷问题紧贴气泡下方 | 初始页首屏内容更紧凑 |
| 7 | 快捷问题上下重复 | 顶部/底部各渲染一次 | 只保留一处：作为消息列表最后一项，输入框有内容或生成中自动隐藏 | 无重复，不再随首条消息「跳」到底部 |
| 8 | 输入区割裂 | 5 个平级控件各自 padding | 合并为单一圆角容器：上文本、下图标行，附件提示入容器内 | 视觉成为一个整体 |
| 9 | 按钮尺寸不统一 | `Button` + weight 分配 | 统一 40dp 触控区 + 20dp 图标；发送 36dp 圆形 | 全部等尺寸，无换行 |
| 10 | 键盘弹出按钮被挤压换行 | 4 个 Button 分宽，文字折行 | 图标按钮固定尺寸，Row 不再分配文字宽度 | 任何屏宽/输入法下都不折行 |
| 11 | 重试改变输入区结构 | 条件插入第 4 个 Button | 重试移入失败气泡内部（12sp 文字胶囊） | 输入区结构恒定 |
| 12 | 键盘遮挡输入框 | edge-to-edge 下 `adjustResize` 失效且无 IME 处理 | 只补 `ime − navigationBars` 差值，不硬编码 dp | 输入容器紧贴键盘上沿，无遮挡、无双份空白 |
| 13 | 键盘收起后跳动/丢内容 | 无焦点与 Insets 联动 | IME 变化只做「贴底滚动」，不改输入状态 | 收起后布局复原，草稿与滚动位置保留 |
| 14 | 上翻历史被强制拉回 | 无条件 `animateScrollToItem` | `ChatScrollPolicy.shouldFollow`：仅当用户本来贴底才跟随；手动发送时强制贴底一次 | 上翻不再被拽回 |
| 15 | 快捷问题被裁切/难点击 | 30dp 高、贴边裁切 | 40dp 触控高度、列表内左右留白不叠加 | 可稳定点击 |

---

## 四、功能保留核查（C）

| 能力 | 状态 | 依据 |
|---|---|---|
| 新建会话 | 保留 | 顶栏 `＋` → `vm.newConversation()`，与旧实现同一方法 |
| 切换历史会话 | 保留 | 会话弹层 → `vm.selectConversation(id)`（旧逻辑有 `!isRunning` 保护，保留） |
| 会话改名 | 保留 | 「⋮ → 改名」→ 同一 AlertDialog + `vm.renameConversation` |
| 会话删除 | 保留 | 「⋮ → 删除」→ 同一 AlertDialog（含 K 阶段「同时删除记忆 N 条」勾选）→ `vm.deleteConversation(id, dropMemories)`；只剩一个会话时禁用删除 |
| 图片选择与上传 | 保留 | `ActivityResultContracts.GetContent` + `vm.attachImage(base64, mime)`，逻辑与旧版逐字一致；新增「移除」入口（`clearAttachment`） |
| 语音输入 | 保留 | 仍走系统 `RecognizerIntent`（未改成自研录音，避免行为变化）；新增「正在打开语音识别…」提示与失败回填 |
| 发送 / 停止生成 | 保留 | 发送按钮双态：有输入→发送；`isRunning`→停止（`vm.cancel()`） |
| 失败重试 | 保留并改进 | 旧：输入区第 4 个按钮；新：失败气泡内「重试」→ `vm.retryFrom(id)` |
| 上下文传递 | 保留 | `vm.setContext(context)` 未改；来源标签只读 `state.context` |
| 长期记忆 | 保留 | 「更多 → 长期记忆」入口；「已记住…」提示仍在输入区上方 |
| 管家提醒 | 保留 | `NudgeCard` 原组件未改，仍在输入区上方；「更多 → 管家提醒」入口 |
| AI 服务配置与调用 | 未改 | `AiChatViewModel.runDefaultStreaming` / `agentRequest` / Provider 全部零改动 |
| 流式渲染 | 保留 | 已用本地假服务验证逐字输出（截图 07） |
| Token 用量 | 未改 | 仍由 `RoomAiChatStore.appendAssistantMessage` 落库 |

---

## 五、测试与验证结果（D）

### 5.1 编译与单测

| 项 | 结果 |
|---|---|
| `:app:compileDebugKotlin` | BUILD SUCCESSFUL |
| `:app:testDebugUnitTest`（全量） | **174 suites / 1148 tests / 0 failures / 0 errors / 3 skipped**（改造前 173 / 1140） |
| `:app:assembleDebug` | BUILD SUCCESSFUL |
| `:app:assembleRelease` | BUILD SUCCESSFUL（R8 + 资源压缩） |

新增测试：
- `ChatScrollPolicyTest`（6 例）：贴底才跟随、上翻不跟、首帧贴底、空列表、header 计数、末项下标（含快捷问题作为列表项）。
- `AiChatViewModelTest`（+2 例）：`clearAttachment` 只清附件不动输入；`retryFrom` 按失败消息定位到它前面那条用户消息重发。

### 5.2 实机（模拟器）逐场景验证

设备：Android 模拟器 `Medium_Phone_API_37.0`（API 37，1080×2400，420dpi，`enableEdgeToEdge` 生效），debug 包。

| 场景 | 结果 |
|---|---|
| 初次进入 | 结构完整，无异常空白（截图 01） |
| 新建会话 | 正常创建并切换，只剩欢迎语（截图 02） |
| 切换/历史消息 | 多轮消息正确加载并自动贴底（截图 03） |
| 输入框获得焦点 | 边框转为主色轻描边，键盘收起后布局复原、草稿保留（截图 04） |
| 键盘弹出 | 输入容器紧贴键盘上沿，三颗按钮完整不换行，聊天区正常压缩（截图 05） |
| 长文本 | 自动增高至 5 行封顶后内部滚动，容器不再变高（截图 06） |
| AI 回复中 | 占位气泡 + 停止按钮 + 图片/语音置灰（截图 07） |
| 发送失败 | 失败气泡内「重试」，输入区结构不变（截图 08） |
| 连续发送 | 消息顺序正确，滚动跟随 |
| 向上浏览历史 | 不再被强制拉回（策略单测覆盖 + 实机观察） |
| 快捷问题 | 点击直接发送；输入内容后自动隐藏 |

### 5.3 APK

| 类型 | 大小 | SHA256 |
|---|---|---|
| Release | 19,546,786 B | `364375f47a3c43cd2091ea1866862978bd7b67df17016ba16d691d27314491e8` |
| Debug | 43.6 MB | 仅用于截图与联调，不入交付包 |

产物位置：
- 安装包：`体重管理App/安装包/轻记-v1.4.5-145-AI管家聊天UI重构-20261126.apk`

### 5.4 截图的真实性说明（必读）

- 截图全部来自**实际运行的 debug 应用**（模拟器），非设计稿。
- **「AI 回复中」这一张必须连一个会分片返回的服务**：本机没有可用 Key，`http://` 又被 targetSdk 35 默认禁止明文，因此新增了 **仅 debug 生效**的 `network_security_config`（只放行 `10.0.2.2 / 127.0.0.1 / localhost`），连本地假 OpenAI 兼容服务（`data: {...delta.content...}` 每 0.35s 一帧）抓到的中间态。release 包不含该配置，线上行为不变。
- 截图中出现的提示「模型未按结构化 JSON 输出，已按普通文本展示」是假服务的真实后果（它不返回 `conclusions` 结构），属于应用既有降级逻辑，不是本次改动引入。

---

## 六、截图交付（E）

目录：`体重管理App/截图/20261126-AI管家聊天UI重构/`

| 文件 | 内容 |
|---|---|
| `01-初始页面.png` | 初次进入：欢迎气泡 + 快捷问题紧随其下 |
| `02-新建会话.png` | 点 `＋` 后的空会话 |
| `03-多轮历史消息.png` | 两轮问答，自动贴底 |
| `04-输入框聚焦(键盘收起).png` | 聚焦态描边，布局复原 |
| `05-键盘弹出.png` | 输入容器贴键盘，按钮不换行 |
| `06-长文本5行封顶.png` | 5 行封顶 + 内部滚动 |
| `07-AI回复中(进行中).png` | 占位气泡「正在查询本地记录…」+ 停止按钮 |
| `08-发送失败与重试.png` | 失败气泡 + 气泡内「重试」 |

（另附 `06b`/`07b` 两张过程图在工作目录，未纳入交付清单。）

---

## 七、最终交付与遗留（F）

- 提交：见仓库 `feat(ai): rebuild AI chat UI and keyboard insets (QJ-AI-CHAT-UI-V2)`；文档单独提交。
- 编译：Debug / Release 均通过；全量单测 0 失败。
- 未解决/待确认真机项：
  1. **真机键盘**：模拟器用 AOSP 输入法验证通过；小米/华为等定制输入法（含候选栏高度变化）需真机再确认一次。
  2. **语音输入**：模拟器无中文语音识别服务，只验证了「按钮可用 + 无识别时回填提示」，真机需再走一遍。
  3. **图片附件**：模拟器相册无图片，未跑通「选图→附件 chip→发送」的完整链路。
  4. **会话弹层与键盘同开**：抽屉打开时不主动收键盘（系统会自行处理），真机可观察一次。
  5. **快捷问题第三颗**：横向滑动可点，但在窄屏上会贴边被裁一半，属正常横滑表现；若要完全不裁，需要改成换行布局（会与工单 6.4「横向滑动」冲突），暂不动。
