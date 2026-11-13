package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.BuildConfig
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.UpdateViewModel

/**
 * ============================================================
 * 检查版本更新页（任务书 3.3）
 * ------------------------------------------------------------
 * 展示：当前版本 / 最新版本 / 更新日期 / 更新说明 / 下载按钮 / 下载进度 / 安装入口。
 * 全部状态分支都在这里铺出来，检查失败绝不显示为「已是最新」。
 * ============================================================
 */
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    vm: UpdateViewModel = viewModel()
) {
    val state by vm.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(PageBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("← 返回", fontSize = TextSizes.Body, color = QingJiGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.PillCorner))
                        .clickable { onBack() }
                        .padding(horizontal = 10.dp, vertical = 6.dp))
                Text("检查版本更新", fontSize = TextSizes.PageTitle,
                    fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 当前版本 ----------
            SectionCard {
                Text("当前版本", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "v${BuildConfig.VERSION_NAME}（versionCode ${BuildConfig.VERSION_CODE}）",
                    fontSize = TextSizes.Body, color = TextSecondary
                )
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 状态区（任务书 3.3 的全部分支） ----------
            when (val s = state) {
                UpdateViewModel.State.Idle -> {
                    SectionCard {
                        Text("点下面的按钮检查 GitHub 上有没有新版本。",
                            fontSize = TextSizes.Body, color = TextSecondary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.check() }, modifier = Modifier.fillMaxWidth()) {
                        Text("检查更新", fontSize = TextSizes.Body)
                    }
                }
                UpdateViewModel.State.Checking -> {
                    SectionCard {
                        Text("正在检查更新…", fontSize = TextSizes.Body, color = TextSecondary)
                    }
                }
                UpdateViewModel.State.UpToDate -> {
                    SectionCard {
                        Text("✓ 已是最新版本", fontSize = TextSizes.CardTitle,
                            fontWeight = FontWeight.SemiBold, color = QingJiGreen)
                    }
                }
                is UpdateViewModel.State.Available -> {
                    SectionCard {
                        Text("发现新版本 v${s.newVersion}", fontSize = TextSizes.CardTitle,
                            fontWeight = FontWeight.SemiBold, color = QingJiGreen)
                        Spacer(Modifier.height(4.dp))
                        Text(s.title, fontSize = TextSizes.Body, color = TextPrimary)
                        if (s.publishedAt.isNotBlank()) {
                            Text("发布日期：${s.publishedAt}", fontSize = TextSizes.Caption, color = TextSecondary)
                        }
                        if (s.apkSizeBytes > 0) {
                            Text("安装包大小：%.1f MB".format(s.apkSizeBytes / 1024.0 / 1024.0),
                                fontSize = TextSizes.Caption, color = TextSecondary)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("更新说明", fontSize = TextSizes.SectionTitle,
                            fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            s.notes.ifBlank { "（没有更新说明）" },
                            fontSize = TextSizes.Body, color = TextPrimary, lineHeight = 20.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { vm.download(s.apkUrl, s.apkAssetName) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("下载 v${s.newVersion}", fontSize = TextSizes.Body) }
                }
                is UpdateViewModel.State.Downloading -> {
                    SectionCard {
                        Text("正在下载…", fontSize = TextSizes.CardTitle,
                            fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        if (s.percent in 0..100) {
                            LinearProgressIndicator(
                                progress = { s.percent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = QingJiGreen
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("${s.percent}%", fontSize = TextSizes.Caption, color = TextSecondary)
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = QingJiGreen)
                            Text("下载中…", fontSize = TextSizes.Caption, color = TextSecondary)
                        }
                    }
                }
                is UpdateViewModel.State.ReadyToInstall -> {
                    SectionCard {
                        Text("✓ 下载完成，安全校验通过", fontSize = TextSizes.CardTitle,
                            fontWeight = FontWeight.SemiBold, color = QingJiGreen)
                        Spacer(Modifier.height(4.dp))
                        Text("包名、版本号、签名证书都验过了，可以安装。",
                            fontSize = TextSizes.Caption, color = TextSecondary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (!vm.canRequestInstall()) vm.openUnknownSourceSettings()
                            else vm.install(s.apkFile)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("安装 v${s.newVersion}", fontSize = TextSizes.Body) }
                }
                is UpdateViewModel.State.DownloadFailed -> {
                    ErrorCard("下载失败：${s.detail}", retry = { vm.backToAvailable() })
                }
                is UpdateViewModel.State.VerifyFailed -> {
                    ErrorCard(s.detail, retry = { vm.backToAvailable() })
                }
                UpdateViewModel.State.RateLimited -> {
                    // GitHub 未认证配额 60 次/小时，用完返回 403（任务书 3.3 点名）
                    ErrorCard("GitHub 接口限流了（每小时 60 次限额）。等一会儿再试，不影响手机上的任何功能。")
                }
                is UpdateViewModel.State.NetworkError -> {
                    ErrorCard("网络异常：${s.detail}", retry = { vm.check() })
                }
                is UpdateViewModel.State.NoApk -> {
                    ErrorCard(
                        if (s.hasUpdateJson) "这个 Release 没有 APK 安装包，无法更新。"
                        else "这个 Release 缺少 update.json 数据源，App 拒绝猜测版本（防止误判）。"
                    )
                }
                is UpdateViewModel.State.ParseError -> {
                    ErrorCard("更新信息解析失败：${s.detail}")
                }
            }
            Spacer(Modifier.height(Dimens.FabSpace + 24.dp))
        }
    }
}

@Composable
private fun ErrorCard(message: String, retry: (() -> Unit)? = null) {
    SectionCard {
        Text("⚠ $message", fontSize = TextSizes.Body, color = DangerRed, lineHeight = 20.sp)
    }
    if (retry != null) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = retry, modifier = Modifier.fillMaxWidth()) {
            Text("重试", fontSize = TextSizes.Body)
        }
    }
}
