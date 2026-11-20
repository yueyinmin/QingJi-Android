# 轻记设置功能完善设计文档

**项目：** QingJi Android
**分支：** `feature/settings-functional-completion`（从 main `d19203b` / v1.0.8 创建）
**目标：** 补齐未实现的设置功能，修复设置行为缺陷；不改 Room Entity、不加 Migration、不重写已正常工作的功能。

## 一、审计结论（基于 main v1.0.8 逐条核实）

| 设置项 | 核实结果 | 处理 |
|---|---|---|
| 应用图标 | 属实：只有「先空着」的说明弹窗，无切换能力 | 实现三套启动器图标真实切换 |
| 通知总开关 | 属实：只存开关状态，六类闹钟照排，到点在 Receiver 里拦 | 关闭时真正取消全部闹钟，重开时按各类开关重排 |
| 通知权限 | 属实：`askNotificationThen` 弹授权框的同时立即执行开启，拒绝也会保存为开启 | 授权成功才执行，拒绝保持关闭 |
| 小组件预览 | 属实：概览 2313 千卡 / 三营养条 / 饮水、体重 92.0kg 均为固定值，不随展示开关变化 | 预览响应三个展示开关，并标注「示例数据」 |
| 其他平台 | 属实：列表叫「其他平台」，弹窗标题却是「换手机怎么带走数据」，还给出受分区存储限制的私有目录拷贝路径 | 更名「换机与数据迁移」，说明改为准确口径 |
| 版本信息 | 属实：写着「Room 11 张表」（实际 Room 9 / 15 张表） | 删除过时数量 |

已确认正常工作、保留原实现：自动识别餐次、保守估重、饮食偏好与忌口、缓存清理、主题与主题色、体重单位、记录时区、自定义字体、底部导航显隐、首页模块显隐。

## 二、应用图标

三套可选图标：**薄荷绿**（现有默认）、**森林绿**（深绿背景 + 三层叶片）、**暖阳奶油**（暖奶油背景 + 三层叶片）。

技术方案（已按此实现）：

- `MainActivity` 交出 LAUNCHER intent-filter，由三个 `activity-alias` 承担桌面入口，同一时刻只启用一个。
- `PackageManager.setComponentEnabledSetting` + `DONT_KILL_APP` 管理启用状态；选中项持久化在 DataStore `ICON_VARIANT`，重启后保持。
- 新增 `AppIconManager`（variant → alias 类名映射 + apply）、`AppIconSettingsPage`（三预览卡，点选立即生效）。
- 资源：`mipmap-anydpi-v26/ic_launcher_forest|cream.xml` + `colors.xml` 两个背景色（minSdk 26，自适应图标全量覆盖，无需 PNG 回退）。
- 不清用户数据；个别 ROM 可能重启 App（数据在 DataStore/Room，不丢）；桌面刷新由系统负责，第三方 launcher 可能延迟几秒。

## 三、通知功能修复

总开关「禁用所有通知」：

- **关闭**：立即取消全部六类已排程闹钟（weigh / fasting / kcal / water / fasting_eat / fasting_fast），各类提醒独立开关的原始设置不动。
- **重新打开**：按各类提醒各自的开关与时间重新排程（复用 `Reminders.apply`）。

权限（Android 13+）：

- 开启任一提醒（称重除外——它走「保存」按钮整组提交，断食/热量/喝水开关开启时检查）先检查 `POST_NOTIFICATIONS`。
- 没有权限 → 请求系统授权；**授权成功才保存开启状态**；拒绝则保持关闭并提示。
- 关闭提醒不申请权限。

## 四、小组件设置预览

保留三种组件（快捷记录 2×2 / 今日概览 4×2 / 体重闪记 4×1）。预览改为响应真实设置：

- 透明度滑杆变化立即反映（原有行为保留）。
- 关闭「显示营养素进度条」→ 预览三条营养条消失。
- 关闭「显示饮水进度」→ 预览饮水行消失。
- 关闭「显示体重数字」→ 预览体重数字显示「已隐藏」（与桌面一致）。
- 预览标注「预览（示例数据，非真实记录）」。

不把 Glance 组件嵌入 Compose 页面。

## 五、换机与数据迁移

设置项更名「换机与数据迁移」，说明明确：

- 本地 JSON 备份恢复，无云端同步。
- API Key 不进备份，换机后重新配置 AI 服务。
- JSON 不包含照片文件本身，换机后引用位置显示占位图。
- 不再提供受分区存储限制影响的私有目录手动拷贝路径作为通用方案。

## 六、版本信息

移除过时的数据库表数量，保留：应用版本、versionCode、最低支持 Android 版本、Room 本地存储、AI 服务协议、小组件框架、当前用户档案。

## 七、代码修改范围

| 文件 | 改动 |
|---|---|
| `AndroidManifest.xml` | MainActivity 交出 LAUNCHER，新增 3 个 activity-alias |
| `res/mipmap-anydpi-v26/ic_launcher_forest.xml` `ic_launcher_cream.xml` | 新增两套自适应图标 |
| `res/values/colors.xml` | 新增森林绿 / 暖阳奶油背景色 |
| `domain/AppIconManager.kt`（新增） | variant → alias 映射 + 切换 |
| `ui/screen/settings/AppIconSettingsPage.kt`（新增） | 图标切换弹层页 |
| `data/prefs/SettingsStore.kt` | 无改动（`ICON_VARIANT` 键与字段已存在，直接复用） |
| `ui/viewmodel/SettingsViewModel.kt` | 总开关真撤闹钟；新增 `setIconVariant` |
| `ui/screen/SettingsScreen.kt` | 权限时序修复；ICON 弹层换真实切换页；「其他平台」更名与文案；版本信息修正 |
| `ui/screen/WidgetSettingsScreen.kt` | 预览响应三个展示开关 + 示例数据标注 |
| `ui/screen/settings/…`（主题页） | 删除「应用图标暂不支持更换」过时提示 |
| `test/…/AppIconManagerTest.kt`（新增） | alias 类名互异、未知值回落、包名前缀 |

不修改 Room Entity，不新增数据库 Migration。

## 八、验收要求

1. 三套应用图标可正常切换；重启后图标状态保持。
2. 通知总开关真正取消和恢复提醒。
3. 拒绝通知权限后不会误开启提醒。
4. 小组件预览与三个展示开关一致。
5. 换机说明准确；版本信息无过时数据。
6. 原有设置值不丢失、功能不回归。
7. Debug / Release 构建成功；现有自动化测试通过。

## 九、Git 交付

- 从 main 创建独立分支，分阶段提交，不直接覆盖 main、不自动合并、不发 Release。
- 提交 PR 等待审查（本仓库暂无 CI，PR 上附构建与测试结果说明）。
