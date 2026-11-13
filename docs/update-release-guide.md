# 轻记发版指南（v1.1.0 起）

> 对应任务书：版本更新、错误日志与诊断导出实施任务书 · P0「正式签名与更新机制」
> 验收目标：每次发版都能被 App 内「检查更新」可靠识别，且用户可安全覆盖安装。

## 一、update.json（Release 必附）

每个正式 Release 的 assets 里必须带一个 `update.json`，这是 App 判断「要不要更新」
的可靠数据源——**绝不用 APK 文件名猜版本，也绝不只用字符串字典序比较版本号**。

```json
{
  "versionName": "1.1.0",
  "versionCode": 111,
  "apkAssetName": "QingJi-v1.1.0.apk",
  "releaseNotes": "训练中心正式版：开始训练、训练日记、计划模板、个人最佳等"
}
```

规则：
- `versionName` / `versionCode` 必须与 app/build.gradle.kts 里的值一致；
- `apkAssetName` 必须与 Release 里上传的 APK 附件名逐字一致；
- `versionCode` 只增不减（v1.0.10=110 未发版并入 111，因此从 111 起步）。

## 二、发版步骤清单（每次照抄）

1. **改版本号**：app/build.gradle.kts 里 `versionCode` / `versionName`，并在注释块里记一行。
2. **登记更新记录**：`AppChangeLog.kt` 顶部插入新 Release（versionCode 必须与 Gradle 一致，
   更新弹窗和「设置 → 版本信息」都读这里）。
3. **同步测试断言**：AppChangeLogTest / UpdateNoticeStoreTest 里的版本号断言一起改。
4. **全量单测**：`./gradlew :app:testDebugUnitTest` 必须全绿。
5. **合并**：功能分支 `--no-ff` 合入 main，留合并记录。
6. **打包**：`./gradlew :app:assembleRelease`。
   - 构建脚本已有防线：本机没配正式密钥时 assembleRelease 直接失败，
     防止 debug 签名的正式包流出去（任务书红线）。
7. **核验签名**：
   `apksigner verify --print-certs app-release.apk`
   证书 SHA-256 必须是 `07a2adf2…457136`（与 v1.0.9 起的正式密钥一致）。
   不一致 → 停止发版，禁止用「卸载重装」绕过。
8. **建 Release**：tag = `v{版本号}`，target = main，附正式版说明（Markdown）。
9. **上传两个附件**：`QingJi-v{版本号}.apk` + `update.json`（按上面模板）。
10. **核验**：`GET /repos/yueyinmin/QingJi-Android/releases/latest` 返回新 tag，
    assets 里两个文件齐全，update.json 内容与 Gradle/APK 一致。

## 三、发版红线（来自任务书，不许碰）

- 禁止正式发布回退 debug 签名（已在构建脚本里加了硬防线）。
- 签名不一致时禁止安装，也不能提示用户卸载重装绕过。
- 不要只用字符串字典序比较版本（1.0.10 < 1.0.9 的字典序陷阱）。
- 不要写死 APK 下载链接，一律从 Release assets 的真实下载地址取。
- 不自动发布正式 Release：发版动作必须由维护者确认后执行。
