package com.qingji.app.ui.screen

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qingji.app.BuildConfig
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes

/**
 * ============================================================
 * 项目信息页（任务书第六节）
 * ------------------------------------------------------------
 * 全部版本数据动态读 BuildConfig / PackageManager，不在页面里
 * 硬编码会随发版变化的数字（任务书红线）。
 * 三个入口用系统浏览器打开 GitHub，不内置 WebView。
 * ============================================================
 */
@Composable
fun ProjectInfoScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

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
                Text("项目信息", fontSize = TextSizes.PageTitle,
                    fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 基本信息 ----------
            SectionCard {
                Text("基本信息", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                InfoRow("应用名称", "轻记 QingJi")
                InfoRow("当前版本", "v${BuildConfig.VERSION_NAME}")
                InfoRow("versionCode", "${BuildConfig.VERSION_CODE}")
                InfoRow("包名（applicationId）", BuildConfig.APPLICATION_ID)
                // 26 = app/build.gradle.kts 里的 minSdk（Android 8.0）；Gradle 改了这里要跟着改
                InfoRow("最低支持 Android", "8.0（API 26）")
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 技术栈 ----------
            SectionCard {
                Text("技术栈", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                InfoRow("开发语言", "Kotlin")
                InfoRow("界面", "Jetpack Compose")
                InfoRow("数据库", "Room")
            }
            Spacer(Modifier.height(12.dp))

            // ---------- GitHub 入口 ----------
            SectionCard {
                Text("GitHub（@yueyinmin）", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                LinkRow("项目源码", "https://github.com/yueyinmin/QingJi-Android") { openUrl(it) }
                LinkRow("版本发布", "https://github.com/yueyinmin/QingJi-Android/releases") { openUrl(it) }
                LinkRow("问题反馈（Issues）", "https://github.com/yueyinmin/QingJi-Android/issues") { openUrl(it) }
            }
            Spacer(Modifier.height(Dimens.FabSpace + 24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = TextSizes.Body, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = TextSizes.Body, color = TextPrimary,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun LinkRow(label: String, url: String, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.TinyCorner))
            .clickable { onClick(url) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = TextSizes.Body, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(url, fontSize = TextSizes.Tiny, color = TextSecondary)
        }
        Text("打开 ›", fontSize = TextSizes.Caption, color = QingJiGreen)
    }
}
