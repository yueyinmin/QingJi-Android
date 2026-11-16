package com.qingji.app.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.BuildConfig
import com.qingji.app.R
import com.qingji.app.ui.components.AppBottomSheet
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.QingJiThemeState
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

/**
 * ============================================================
 * 关于我们（V2 工单重做，2026-11-15）
 * ------------------------------------------------------------
 * 对外签名保持 AboutScreen(onBack, useSheet = ...)，设置页
 * SettingsScreen.DialogKind.ABOUT 的调用方式一个字没改。
 *
 * 版式：浅灰紫底 / 顶部居中标题 / 居中品牌图标与版本 / 白色圆角卡片 /
 * 灰色分组标题 / 大字号菜单 / 浅灰右箭头。品牌色仍是轻记绿。
 *
 * 三个真实入口：
 *   数据与算法口径 → 本文件内的独立详情（五项口径原文迁移，不改数值）
 *   开源许可       → OpenSourceLicensesContent（可展开）
 *   源代码         → 浏览器打开仓库
 * 没有对应页面或数据的入口一律不加，不用 Toast / 空白页假装功能。
 * ============================================================
 */
private const val GITHUB_REPO_URL = "https://github.com/yueyinmin/QingJi-Android"
private const val GITHUB_PROFILE_URL = "https://github.com/yueyinmin"
private const val GITHUB_HANDLE = "@yueyinmin"

/** 关于页内部的三层，详情页用本地状态切换，不新增 NavHost 路由、不动导航栈。 */
private enum class AboutPage { MAIN, CALIBER, LICENSES }

@Composable
fun AboutScreen(onBack: () -> Unit, useSheet: Boolean = false) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(AboutPage.MAIN) }

    val title = when (page) {
        AboutPage.MAIN -> s("关于我们")
        AboutPage.CALIBER -> s("数据与算法口径")
        AboutPage.LICENSES -> s("开源许可")
    }
    // 详情页的返回只回主页面；主页面的返回才真正退出（整页或弹层）。
    val goBack: () -> Unit =
        if (page == AboutPage.MAIN) onBack else { { page = AboutPage.MAIN } }

    // 内容只写一遍：整页和底部弹层共用，避免两份代码以后改走样。
    // body 里不套 verticalScroll —— 滚动容器由外面统一管理（见下面 useSheet 分支）。
    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        // 整页模式有 PageTopBar 的返回箭头；弹层模式的标题栏没有返回按钮，
        // 所以详情页自己在内容顶部补一行「返回关于我们」，保证可返回。
        if (useSheet && page != AboutPage.MAIN) {
            SheetBackRow { page = AboutPage.MAIN }
        }
        when (page) {
            AboutPage.MAIN -> AboutMainContent(onOpen = { page = it })
            AboutPage.CALIBER -> CaliberContent()
            AboutPage.LICENSES -> OpenSourceLicensesContent(
                onOpenUrl = { openUrl(context, it) }
            )
        }
    }

    if (useSheet) {
        // 这里不用 SettingsDetailSheet：它内部的 rememberScrollState 没有 key，
        // 从主页面点进详情时会沿用已经滚到一半的位置，导致详情页顶部的
        // 「返回关于我们」和声明卡被滚出可视区（模拟器实测踩到）。
        // 换成同一套 AppBottomSheet 骨架 + 自己带 key 的 ScrollState：
        // page 一变就换一个从 0 开始的滚动状态，详情页永远从顶部开始。
        AppBottomSheet(
            title = title,
            skipPartiallyExpanded = true,
            onDismiss = goBack
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(remember(page) { ScrollState(0) })
            ) {
                body()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(aboutBackgroundColor())
        ) {
            PageTopBar(title = title, onBack = goBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
            ) {
                body()
            }
        }
    }
}

/** 浅灰紫底；深色模式换成同色系的深色，卡片用 CardWhite 自动跟随主题。 */
@Composable
private fun aboutBackgroundColor(): Color =
    if (QingJiThemeState.darkMode) Color(0xFF14151C) else Color(0xFFF3F2F8)

// ------------------------------------------------------------
// 主页面
// ------------------------------------------------------------
@Composable
private fun AboutMainContent(onOpen: (AboutPage) -> Unit) {
    val context = LocalContext.current

    Spacer(Modifier.height(20.dp))

    // ---------- 品牌区：居中 ----------
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(116.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(CardWhite),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = s("轻记"),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            // 真实版本号 + 构建号，直接读 BuildConfig，不写死
            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            fontSize = 13.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        // 只陈述事实：当前装的是哪个版本。
        // 没有做版本检查，就绝不显示「已是最新版本」。
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(QingJiGreenLight)
                .padding(horizontal = 14.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = s("当前版本"),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = QingJiGreenDark
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    // ---------- AI 能力说明 ----------
    GroupLabel(s("智能能力说明"))
    Spacer(Modifier.height(8.dp))
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = QingJiGreen,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = s("AI 能力说明"),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "本应用的 AI 食物识别能力由用户配置的 AI 服务商提供。" +
                "拍摄的图片直接发送给用户选择的服务商，轻记不经手、不留存副本。",
            fontSize = 13.sp,
            color = TextSecondary,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "不配置 AI 服务也可以使用食物库和手动记录功能。",
            fontSize = 13.sp,
            color = TextSecondary,
            lineHeight = 20.sp
        )
    }

    Spacer(Modifier.height(20.dp))

    // ---------- 项目与声明 ----------
    GroupLabel(s("项目与声明"))
    Spacer(Modifier.height(8.dp))
    AboutMenuCard(
        listOf(
            AboutMenuItem(s("数据与算法口径"), onClick = { onOpen(AboutPage.CALIBER) }),
            AboutMenuItem(s("开源许可"), onClick = { onOpen(AboutPage.LICENSES) }),
            AboutMenuItem(s("源代码"), onClick = { openUrl(context, GITHUB_REPO_URL) })
        )
    )

    Spacer(Modifier.height(20.dp))

    // ---------- 维护者 ----------
    GroupLabel(s("维护者"))
    Spacer(Modifier.height(8.dp))
    AboutMenuCard(
        listOf(
            AboutMenuItem(
                title = GITHUB_HANDLE,
                subtitle = s("轻记的维护者"),
                onClick = { openUrl(context, GITHUB_PROFILE_URL) }
            )
        )
    )

    Spacer(Modifier.height(22.dp))

    // ---------- 底部隐私声明（沿用原有文案） ----------
    Text(
        text = s("轻记没有账号、没有服务器，数据只存在你自己的手机里。\nAI 能力用你自己填的 Key，直连你选的服务商，我们既不中转也不收集。"),
        fontSize = 11.sp,
        color = TextSecondary,
        lineHeight = 17.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(28.dp))
}

private data class AboutMenuItem(
    val title: String,
    val subtitle: String? = null,
    val onClick: () -> Unit
)

/** 大字号菜单行 + 浅灰箭头 + 1dp 分割线，不用一堆小图标和装饰小字。 */
@Composable
private fun AboutMenuCard(items: List<AboutMenuItem>) {
    SectionCard(contentPadding = 0.dp) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DividerColor)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { item.onClick() }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    if (item.subtitle != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(text = item.subtitle, fontSize = 12.sp, color = TextSecondary)
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = DividerColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** 弹层模式详情页顶部的返回行。整页模式用 PageTopBar，不需要它。 */
@Composable
private fun SheetBackRow(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onBack() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = QingJiGreenDark,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(8.dp))
        Text(s("返回关于我们"), fontSize = 14.sp, color = QingJiGreenDark)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = TextSecondary.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 4.dp)
    )
}

// ------------------------------------------------------------
// 详情页 1：数据与算法口径（五项原文迁移，一个计算规则都没改）
// ------------------------------------------------------------
@Composable
private fun CaliberContent() {
    Spacer(Modifier.height(12.dp))
    SectionCard {
        CaliberRow(
            title = "热量预算",
            body = "Mifflin-St Jeor 基础代谢 × 活动系数，再按减脂 / 增肌 / 保持调整。"
        )
        CaliberRow(
            title = "AI 识别校验",
            body = "识别结果按 4-9-4（蛋白 / 碳水 4 千卡、脂肪 9 千卡）与营养素总重规则复核，" +
                "数值不自洽时不能保存；每次手动编辑都会重新校验。"
        )
        CaliberRow(
            title = "运动补偿",
            body = "运动消耗默认按 50% 加回饮食额度（0-100% 可调）。" +
                "设备和算法普遍高估消耗，全额加回容易越练越多吃。"
        )
        CaliberRow(
            title = "食物库",
            body = "654 条常见食物随包内置，每条带来源与「未审核」标记；" +
                "纤维 / 钠 / 糖 / 钙未收录就不显示，不编造数值。"
        )
        CaliberRow(
            title = "免责",
            body = "全部为估算值，供自我参考，不构成营养或医学建议。"
        )
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun CaliberRow(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = QingJiGreenDark)
        Spacer(Modifier.height(4.dp))
        Text(text = body, fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp)
    }
}

// ------------------------------------------------------------
// 详情页 2：开源许可
// ------------------------------------------------------------
private data class LicenseItem(
    val key: String,
    val name: String,
    val author: String,
    val license: String,
    val note: String,
    val url: String
)

/**
 * 清单按 app/build.gradle.kts 里实际的 implementation(...) 逐项核对，
 * 只列 APK 运行时依赖；testImplementation / debugImplementation / ksp /
 * 编译插件一律不算。Kotlin / Coroutines / Compose 等由 BOM 或传递依赖引入，
 * note 里写清楚来源，不把开发期依赖冒充成运行依赖。
 */
private val RUNTIME_LICENSES = listOf(
    LicenseItem("kotlin", "Kotlin", "JetBrains", "Apache-2.0",
        "语言与标准库，随 Kotlin Gradle 插件进入 APK。",
        "https://kotlinlang.org/docs/license.html"),
    LicenseItem("androidx", "AndroidX / Jetpack Compose", "Google / AOSP", "Apache-2.0",
        "core-ktx、lifecycle、activity-compose、Compose BOM（ui / material3 / icons）。",
        "https://developer.android.com/jetpack/androidx/licenses"),
    LicenseItem("navigation", "Navigation Compose", "Google / AOSP", "Apache-2.0",
        "implementation(libs.androidx.navigation.compose)。",
        "https://developer.android.com/jetpack/androidx/releases/navigation"),
    LicenseItem("room", "Room", "Google / AOSP", "Apache-2.0",
        "room-runtime + room-ktx；ksp 注解处理器只在编译期使用，不进 APK。",
        "https://developer.android.com/jetpack/androidx/releases/room"),
    LicenseItem("datastore", "DataStore", "Google / AOSP", "Apache-2.0",
        "implementation(libs.androidx.datastore.preferences)。",
        "https://developer.android.com/jetpack/androidx/releases/datastore"),
    LicenseItem("glance", "Glance", "Google / AOSP", "Apache-2.0",
        "glance-appwidget + glance-material3，桌面小组件运行时。",
        "https://developer.android.com/jetpack/androidx/releases/glance"),
    LicenseItem("coroutines", "Kotlin Coroutines", "JetBrains", "Apache-2.0",
        "kotlinx-coroutines-android，与 AndroidX 也是传递依赖。",
        "https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt"),
    LicenseItem("coil", "Coil", "Coil Contributors", "Apache-2.0",
        "coil-compose + coil-svg（V2 新增，用于本地动作插画 SVG 解码）。",
        "https://github.com/coil-kt/coil/blob/main/LICENSE.txt"),
    LicenseItem("okhttp", "OkHttp", "Square, Inc.", "Apache-2.0",
        "implementation(libs.okhttp)，AI 服务商直连用的 HTTP 客户端。",
        "https://github.com/square/okhttp/blob/master/LICENSE.txt"),
    LicenseItem("zxing", "ZXing", "ZXing Authors", "Apache-2.0",
        "implementation(libs.zxing.core)，条形码识别。",
        "https://github.com/zxing/zxing/blob/master/LICENSE")
)

private val MEDIA_LICENSE = LicenseItem(
    key = "workout-guide",
    name = "Workout Guide 动作插画",
    author = "Bryl Lim（上游素材 Everkinetic）",
    license = "CC BY-SA 4.0",
    note = "117 个动作各 3 帧、共 351 张 512×512 透明底 SVG，来自 github.com/bryllim/workout-guide，" +
        "按 1→2→3→2 连续播放。上游 manifest 逐帧注明来源：其中 76 个动作的第一帧改编自 " +
        "Everkinetic（经栅格化、单色化、矢量描摹后由 Bryl Lim 收录），其余帧各有自己的归属，" +
        "不能把 Everkinetic 署名套用到所有帧；均为 CC BY-SA 4.0。" +
        "轻记只在运行时用 ColorFilter 着色以适配深浅主题，随包 SVG 源文件一个字节未改。" +
        "逐动作、逐帧的作者/来源/许可/修改记录见仓库 docs/exercise-library/metadata/asset_licenses.csv，" +
        "旧版 38 条 attribution 仍保留在 assets/workout-guide/ATTRIBUTION.json（随 APK 打包）。",
    url = "https://github.com/bryllim/workout-guide"
)

/**
 * 开源许可内容：可独立复用（也供以后别的入口调用）。
 * 每行点一下展开版权说明和官方链接，再点收起。
 */
@Composable
fun OpenSourceLicensesContent(onOpenUrl: (String) -> Unit = {}) {
    var expandedKey by remember { mutableStateOf<String?>(null) }

    Spacer(Modifier.height(12.dp))
    SectionCard {
        Text(
            text = "关于轻记自身源码",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = QingJiGreenDark
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "轻记项目源码目前暂未单独声明开源许可证。" +
                "公开仓库不等于自动授予复制、修改或再分发权限。" +
                "第三方依赖和动作插画按照各自许可证使用。",
            fontSize = 13.sp,
            color = TextSecondary,
            lineHeight = 20.sp
        )
    }

    Spacer(Modifier.height(18.dp))
    GroupLabel(s("运行依赖"))
    Spacer(Modifier.height(8.dp))
    SectionCard(contentPadding = 0.dp) {
        RUNTIME_LICENSES.forEachIndexed { index, item ->
            if (index > 0) LicenseDivider()
            LicenseRow(
                item = item,
                expanded = expandedKey == item.key,
                onToggle = { expandedKey = if (expandedKey == item.key) null else item.key },
                onOpenUrl = onOpenUrl
            )
        }
    }

    Spacer(Modifier.height(18.dp))
    GroupLabel(s("动作插画素材"))
    Spacer(Modifier.height(8.dp))
    SectionCard(contentPadding = 0.dp) {
        LicenseRow(
            item = MEDIA_LICENSE,
            expanded = expandedKey == MEDIA_LICENSE.key,
            onToggle = {
                expandedKey = if (expandedKey == MEDIA_LICENSE.key) null else MEDIA_LICENSE.key
            },
            onOpenUrl = onOpenUrl
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = "CC BY-SA 4.0：https://creativecommons.org/licenses/by-sa/4.0/",
        fontSize = 11.sp,
        color = TextSecondary,
        modifier = Modifier
            .padding(start = 4.dp)
            .clickable { onOpenUrl("https://creativecommons.org/licenses/by-sa/4.0/") }
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun LicenseDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DividerColor)
    )
}

@Composable
private fun LicenseRow(
    item: LicenseItem,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 18.dp, vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(item.license, fontSize = 12.sp, color = QingJiGreenDark)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = DividerColor,
                modifier = Modifier.size(20.dp)
            )
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text("作者 / 维护组织：${item.author}", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Text(item.note, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.url,
                fontSize = 12.sp,
                color = QingJiGreenDark,
                modifier = Modifier.clickable { onOpenUrl(item.url) }
            )
        }
    }
}

/** 打开外部链接；没装浏览器就静默失败，不让「关于」页崩。 */
private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        // 没有浏览器 / 被系统限制：什么都不做
    }
}
