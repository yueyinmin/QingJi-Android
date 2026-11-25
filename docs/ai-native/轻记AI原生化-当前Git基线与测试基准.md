# 轻记 AI 原生化 · 当前 Git 基线与测试基准

> 工单：QJ-AI-NATIVE-DOC-REVIEW-V1 · 任务九  
> 记录时间：2026-11-25  
> 性质：阶段 B01 开工前的基线证据。  
> 纪律：本轮未修改任何业务代码；未为了让测试通过而调整代码。

---

## 1. Git 基线

| 项 | 实际值 |
|---|---|
| 仓库根 | `C:/qingji-git/QingJi-Android` |
| 分支 | `feature/health-system-v1.1` |
| HEAD | `c97ada3d98678cc7f178061a84404b64a95d6066` |
| 工作区状态（基线确认时） | `?? docs/ai-native/`；无 `app/`、`gradle/`、`build.gradle.kts` 等代码改动 |
| 当前源码未提交修改 | 无。所有改动均为 `docs/ai-native/` 下设计文档 |
| 旧测试报告是否直接沿用 | **否**。旧 XML 与 HEAD 源码集合不一致，已重新执行测试 |

基线确认命令与关键输出：

```bash
cd /c/qingji-git/QingJi-Android
git rev-parse --show-toplevel          # C:/qingji-git/QingJi-Android
git rev-parse --abbrev-ref HEAD        # feature/health-system-v1.1
git rev-parse HEAD                     # c97ada3d98678cc7f178061a84404b64a95d6066
git status --short --branch            # ## feature/health-system-v1.1 / ?? docs/ai-native/
```

## 2. 重新执行结果

所有命令均在 HEAD `c97ada3` 上以 `--rerun-tasks --no-daemon --console=plain` 强制执行，结果如下：

| 命令 | 结果 |
|---|---|
| `./gradlew compileDebugKotlin --rerun-tasks --no-daemon --console=plain` | **BUILD SUCCESSFUL**，16 tasks executed |
| `./gradlew testDebugUnitTest --rerun-tasks --no-daemon --console=plain` | **BUILD SUCCESSFUL**，30 tasks executed |
| `./gradlew assembleDebug --rerun-tasks --no-daemon --console=plain` | **BUILD SUCCESSFUL**，38 tasks executed |
| `./gradlew assembleRelease --rerun-tasks --no-daemon --console=plain` | **BUILD SUCCESSFUL**，52 tasks executed，含 `lintVitalRelease` / R8 / packageRelease |

## 3. JVM 单元测试统计

重新生成报告目录：`app/build/test-results/testDebugUnitTest/`

| 指标 | 数量 |
|---|---:|
| 测试套件 XML | 70 |
| tests | 463 |
| failures | 0 |
| errors | 0 |
| skipped | 3 |

统计命令：

```bash
python - <<'PY'
import glob, xml.etree.ElementTree as ET
files=glob.glob('app/build/test-results/testDebugUnitTest/TEST-*.xml')
tests=failures=errors=skipped=0
for f in files:
    r=ET.parse(f).getroot()
    tests+=int(r.get('tests',0)); failures+=int(r.get('failures',0))
    errors+=int(r.get('errors',0)); skipped+=int(r.get('skipped',0))
print(len(files), tests, failures, errors, skipped)
PY
```

## 4. 跳过测试

3 个 skipped 全部来自 `HomeInteractionTest`：

- 类：`com.qingji.app.ui.HomeInteractionTest`
- 原因：类级 `@Ignore("Robolectric 首次运行需要下载 100MB+ 安卓系统包，本机网络卡住，暂时跳过")`
- 位置：`app/src/test/java/com/qingji/app/ui/HomeInteractionTest.kt:36`
- 影响：Compose UI 交互测试未执行，不得计入“已通过”。
- 阶段 B01 不依赖该测试；阶段 D 的 UI 验收前需解决 android-all 下载或提供等效验证。

## 5. 失败测试

无。重新执行结果中 `failures=0`、`errors=0`。

## 6. 构建产物

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Release APK：`app/build/outputs/apk/release/app-release-unsigned.apk`（以实际文件名为准；`assembleRelease` 成功）

构建期间仅有既有告警，例如：

- Compose/Material 图标 deprecated 警告。
- `ExperimentalCoroutinesApi` opt-in 警告。
- `stripDebugDebugSymbols` 对两个 `.so` 未 strip，按原样打包。

这些警告不影响构建成功，本轮不修改代码消除。

## 7. 后续复测（B01–B03 与收口项完成后）

在 AI Provider 迁移、SSE/Tool Calling、Compose UI 测试恢复、SSE 供应商差异兼容与 ToolExecutor 并发基础能力完成后重新执行：

| 项目 | 结果 |
|---|---|
| `testDebugUnitTest` | **BUILD SUCCESSFUL** |
| suites | 74 |
| tests | 499 |
| failures | 0 |
| errors | 0 |
| skipped | 0 |
| `assembleDebug` | 成功 |
| `assembleRelease` | 成功 |

变化说明：

- `HomeInteractionTest` 已移除 `@Ignore`，恢复为 1 个 Compose 冒烟测试：`clickAddButton_opensRecordSheet`。
- 原 3 个 skipped 测试被合并为 1 个可执行测试，避免 Robolectric 多 sandbox 下单例数据库残留连接。
- 新增/恢复的 AI 与工具测试全部通过。

## 8. 结论

- 当前基线测试与构建已重新建立，可作为后续 C 阶段前的回归基准。
- 旧 XML 与源码集合不一致的问题已通过强制重跑解决；后续不得再引用旧报告作为权威基准。
- Compose UI 冒烟测试已可执行，skipped=0；更深的业务导航交互仍可在阶段 D 单独扩展。
- 基线建立阶段未修改业务代码；后续 B01–B03 与收口项的代码改动已按各提交记录。
