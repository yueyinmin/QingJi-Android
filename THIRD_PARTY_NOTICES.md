# 第三方声明 / THIRD PARTY NOTICES

> 对应分支：`feature/experience-polish-v2`（V2 综合体验优化工单）
> 生成依据：`app/build.gradle.kts` 的 `implementation(...)` 清单 + 上游
> `packages/workout-guide/manifest.json`。**只记录 APK 运行时依赖**；
> `testImplementation` / `debugImplementation` / `ksp` / Gradle 插件等开发期依赖不计入，
> 不把开发测试依赖冒充成运行依赖。

---

## 一、轻记自身源码

**轻记项目源码目前暂未单独声明开源许可证。**

仓库虽然公开，但公开仓库不等于自动授予复制、修改或再分发权限。
在没有权利人明确授权前，请勿假定本项目适用 MIT、Apache-2.0 或任何其它许可证。
App 内「设置 → 关于 → 开源许可」页也展示了同样的说明。

第三方依赖与动作插画按各自许可证使用，**不因为被收录进本项目而改变其授权状态**。

---

## 二、运行时依赖（逐项核对 `implementation`）

| 项目 | 作者 / 维护组织 | 许可证 | 在轻记里的用途 |
| --- | --- | --- | --- |
| Kotlin（语言与标准库） | JetBrains | Apache-2.0 | 全体 Kotlin 代码，随 Kotlin 插件进入 APK |
| AndroidX / Jetpack Compose | Google / AOSP | Apache-2.0 | core-ktx、lifecycle、activity-compose、Compose BOM（ui / material3 / icons） |
| Navigation Compose | Google / AOSP | Apache-2.0 | 四个一级 Tab 与二级页面导航 |
| Room | Google / AOSP | Apache-2.0 | room-runtime + room-ktx；`ksp` 注解处理器只在编译期使用 |
| DataStore | Google / AOSP | Apache-2.0 | datastore-preferences，用户设置 |
| Glance | Google / AOSP | Apache-2.0 | glance-appwidget + glance-material3，桌面小组件 |
| Kotlin Coroutines | JetBrains | Apache-2.0 | kotlinx-coroutines-android（也是 AndroidX 的传递依赖） |
| Coil | Coil Contributors | Apache-2.0 | coil-compose + coil-svg（V2 新增，本地动作插画 SVG 解码） |
| OkHttp | Square, Inc. | Apache-2.0 | 用户配置的 AI 服务商直连 |
| ZXing | ZXing Authors | Apache-2.0 | zxing-core，条形码识别 |

官方链接见 App 内「开源许可」页每一项的展开内容。

---

## 三、动作插画素材

| 项 | 内容 |
| --- | --- |
| 素材项目 | Workout Guide — https://github.com/bryllim/workout-guide |
| 作者 | Bryl Lim — https://bryllim.com |
| 上游素材 | Everkinetic — https://github.com/everkinetic/data |
| 许可证 | CC BY-SA 4.0 — https://creativecommons.org/licenses/by-sa/4.0/ |
| 引入内容 | 38 张 `frame-1.svg`，512 × 512，透明背景，本地 `assets/workout-guide/` |
| 加载方式 | 离线，`file:///android_asset`，不联网 |

### 上游原始 attribution（照录，未改写）

上游 manifest 的每条动作 / 每帧都带 `attribution`，逐帧写的是：

```json
{
  "creator": "Bryl Lim",
  "creatorUrl": "https://bryllim.com",
  "license": "CC BY-SA 4.0",
  "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
  "source": {
    "name": "Everkinetic",
    "url": "https://github.com/everkinetic/data/blob/main/dist/svg/xxxx-tension.svg",
    "license": "CC BY-SA 4.0",
    "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
    "changes": "Rasterized on a transparent 512 × 512 canvas, recolored for monochrome display, and vector-traced."
  }
}
```

也就是说：Everkinetic 原图是经 **栅格化到 512×512 透明画布、改为单色、矢量描摹**
之后由 Bryl Lim 收录的，许可为 CC BY-SA 4.0。这部分改动是**上游作者做的**，
**不是轻记做的**，本文件不把这些素材描述成轻记重新创作或重新绘制。

轻记侧做的只有两件事：

1. 原样下载 `frame-1.svg` 放进 `assets/`，**SVG 源文件一个字节都没有改**；
2. 因为素材是纯白剪影（`fill="#fff"`），直接贴浅色底板会看不见，
   所以在 Compose 绘制层用 `ColorFilter.tint(...)` 临时着色以适配浅色 / 深色主题。
   这是运行时显示效果，不改变随包文件，也不改变素材本身的授权与署名要求。

### 逐条署名存档

38 条完整的原始 attribution（含每条的 `name` / `frame1` / `format` / `width` /
`height` / 帧级 `attribution` / 动作级 `attribution`）**逐条原样保存在**：

```
app/src/main/assets/workout-guide/ATTRIBUTION.json
```

该文件随 APK 打包，App 内「开源许可 → Workout Guide」展开项也会说明它的位置。
上游 manifest 里个别条目没有提供 `source.url`，存档里按原样保留空值，**没有编造链接**。

本次引入的 38 个 slug：

```
arnold-press, barbell-row, bench-press, bent-over-rear-delt-raise, bicep-curl,
calf-raise, childs-pose, crunch, dead-bug, deadlift, diamond-push-up,
doorway-chest-stretch, dumbbell-bench-press, dumbbell-fly,
dumbbell-romanian-deadlift, ez-bar-curl, face-pull, forward-lunge,
goblet-squat, hammer-curl, hamstring-stretch, hanging-leg-raise,
incline-dumbbell-press, kneeling-hip-flexor-stretch, lat-pulldown,
lateral-raise, leg-press, machine-chest-press, one-arm-dumbbell-row,
plank, pull-up, push-up, russian-twist, seated-dumbbell-press,
seated-row, squat, standing-quad-stretch, tricep-pushdown
```

其中 6 个是**近似匹配**（素材动作与轻记动作存在差异），在 App 动作详情里
会显示「动作示意，姿势待核对」，不会当作完全一致的演示：

| UUID | 动作 | 差异 |
| --- | --- | --- |
| `ex-shoulder-01` | 哑铃肩推 | 坐姿素材 vs 未限定姿势 |
| `ex-arm-01` | 杠铃弯举 | EZ 杠 vs 直杆 |
| `ex-arm-02` | 哑铃交替弯举 | 普通弯举 vs 交替过程 |
| `ex-arm-04` | 窄距俯卧撑 | 钻石俯卧撑 vs 普通窄距 |
| `ex-leg-02` | 哑铃深蹲 | 高脚杯深蹲 vs 自然持铃 |
| `ex-stretch-02` | 背阔肌拉伸 | 儿童式伸展 vs 专项背阔肌拉伸 |

---

## 四、不虚构声明

- 本文件只写实际存在的运行依赖与实际下载的素材；
- 未使用、未引入的库不列入清单；
- 未做的版本检查、未提供的协议页面、未确认的联系方式不写入 App 菜单；
- 未修改的素材不描述为重新创作，已修改的部分（运行时着色）如实注明。
