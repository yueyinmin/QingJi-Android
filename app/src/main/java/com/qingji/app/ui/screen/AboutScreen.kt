package com.qingji.app.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.BuildConfig
import com.qingji.app.R
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

/**
 * ============================================================
 * 关于轻记（2026-11-11 重做）
 * ------------------------------------------------------------
 * 以前这里只是一段纯文字（InfoDetailPage），看不到项目在哪、谁在维护。
 * 现在按「应用身份 → 项目信息 → 维护者」三段排：
 *   ① 主卡片：三叶图标 + 名字 + 一句话定位 + 版本胶囊 + 三个特性标签；
 *   ② 项目信息：源代码一行，点了直接开浏览器到 GitHub；
 *   ③ 维护者：GitHub 账号，同样可点。
 *
 * 视觉沿用轻记现有语言：浅绿底、白色圆角卡片、胶囊标签，
 * 不引入新的配色，也不放赞助/下载之类的东西 —— 这个 App 没有服务器、
 * 没有账号，也没什么可推广的。
 * ============================================================
 */

/** 仓库地址集中一处，改仓库只改这里。 */
private const val GITHUB_URL = "https://github.com/yueyinmin/QingJi-Android"
private const val GITHUB_HANDLE = "@yueyinmin"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(title = s("关于"), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(Dimens.Gap))

            IdentityCard()
            Spacer(Modifier.height(Dimens.Gap))

            GroupLabel(text = s("项目信息"), subtitle = s("源码公开，每一行改动都能在 GitHub 上翻到"))
            Spacer(Modifier.height(8.dp))
            SectionCard(contentPadding = 14.dp) {
                LinkRow(
                    icon = { Icon(Icons.Outlined.Code, contentDescription = null, tint = QingJiGreenDark, modifier = Modifier.size(20.dp)) },
                    title = s("源代码"),
                    subtitle = "github.com/yueyinmin/QingJi-Android",
                    onClick = { openUrl(context, GITHUB_URL) }
                )
            }

            Spacer(Modifier.height(Dimens.Gap))

            // 数据与算法口径（2026-11-12，对标竞品「吃有数」的可信度说明）
            GroupLabel(text = s("数据与算法口径"), subtitle = s("怎么算的、数据从哪来，都在这里"))
            Spacer(Modifier.height(8.dp))
            SectionCard(contentPadding = 14.dp) {
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

            Spacer(Modifier.height(Dimens.Gap))

            GroupLabel(text = s("维护者"), subtitle = s("一个人写的，欢迎提 issue"))
            Spacer(Modifier.height(8.dp))
            SectionCard(contentPadding = 14.dp) {
                LinkRow(
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(QingJiGreenLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "木", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = QingJiGreenDark)
                        }
                    },
                    title = GITHUB_HANDLE,
                    subtitle = s("轻记的作者 · 代码和食物库都是自己弄的"),
                    onClick = { openUrl(context, GITHUB_URL) }
                )
            }

            Spacer(Modifier.height(Dimens.Gap))

            Text(
                text = s("轻记没有账号、没有服务器，数据只存在你自己的手机里。\nAI 能力用你自己填的 Key，直连你选的服务商，我们既不中转也不收集。"),
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }
}

/** 主卡片：图标 + 名称 + 一句话 + 版本 + 特性标签。 */
@Composable
private fun IdentityCard() {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(QingJiGreenLight),
                contentAlignment = Alignment.Center
            ) {
                // 和桌面图标、开屏同一套三叶资源，不另画一份
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = s("轻记"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = s("一个只存在本机的减重账本"),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag(text = "v${BuildConfig.VERSION_NAME}", filled = true)
            Spacer(Modifier.width(6.dp))
            Tag(text = s("本地优先"), filled = false)
            Spacer(Modifier.width(6.dp))
            Tag(text = s("不需要账号"), filled = false)
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Tag(text = "Kotlin · Compose", filled = false)
            Spacer(Modifier.width(6.dp))
            Tag(text = "3 MB", filled = false)
        }
    }
}

@Composable
private fun Tag(text: String, filled: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (filled) QingJiGreen else QingJiGreenLight)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal,
            color = if (filled) CardWhite else QingJiGreenDark
        )
    }
}

@Composable
private fun GroupLabel(text: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(2.dp))
        Text(text = subtitle, fontSize = 11.sp, color = TextSecondary)
    }
}

/** 「数据与算法口径」里的一行：绿色小标题 + 灰色说明。 */
@Composable
private fun CaliberRow(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = QingJiGreenDark)
        Spacer(Modifier.height(2.dp))
        Text(text = body, fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
    }
}

/** 一整行可点条目：左侧图标 + 两行文字 + 右箭头。 */
@Composable
private fun LinkRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(QingJiGreenLight),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 11.sp, color = TextSecondary)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
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
