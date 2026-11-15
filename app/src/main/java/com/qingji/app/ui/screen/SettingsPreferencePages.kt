package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.screen.settings.DangerActionButton
import com.qingji.app.ui.screen.settings.SettingsDetailScaffold
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary


// ============================================================
// 几个组合弹窗
// ============================================================


// ============================================================
// 文案小工具
// ============================================================

internal fun timezoneOptions(): List<Pair<String, String>> = listOf(
    "Asia/Shanghai" to "北京时间（UTC+8）",
    "Asia/Tokyo" to "东京（UTC+9）",
    "Asia/Singapore" to "新加坡（UTC+8）",
    "Europe/London" to "伦敦（UTC+0/+1）",
    "America/New_York" to "纽约（UTC-5/-4）",
    "UTC" to "协调世界时 UTC"
)


@Composable
internal fun MacroRatioPage(
    proteinInit: Int,
    carbInit: Int,
    fatInit: Int,
    /** v3.5.0：膳食纤维 / 钠 / 钙三个每日目标，存档案表 */
    fiberInit: Int,
    sodiumInit: Int,
    calciumInit: Int,
    onSave: (Int, Int, Int) -> Unit,
    onSaveGoals: (fiberGoalG: Int, sodiumGoalMg: Int, calciumGoalMg: Int) -> Unit,
    onBack: () -> Unit,
    useSheet: Boolean = false
) {
    var protein by remember { mutableStateOf(proteinInit.toString()) }
    var carb by remember { mutableStateOf(carbInit.toString()) }
    var fat by remember { mutableStateOf(fatInit.toString()) }
    var fiber by remember { mutableStateOf(fiberInit.toString()) }
    var sodium by remember { mutableStateOf(sodiumInit.toString()) }
    var calcium by remember { mutableStateOf(calciumInit.toString()) }

    val sum = (protein.toIntOrNull() ?: 0) + (carb.toIntOrNull() ?: 0) + (fat.toIntOrNull() ?: 0)

    SettingsDetailScaffold(title = "营养素分配", onBack = onBack, useSheet = useSheet) {
        HintBox(text = "三项加起来要是 100%。改完会重新算首页和进展页的目标克数。")
        Spacer(Modifier.height(Dimens.Gap))

        SectionCard {
            MacroField("蛋白质", protein, "%") { protein = it }
            Spacer(Modifier.height(10.dp))
            MacroField("碳水", carb, "%") { carb = it }
            Spacer(Modifier.height(10.dp))
            MacroField("脂肪", fat, "%") { fat = it }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "当前合计 $sum%" + if (sum == 100) " ✓" else "（需要调整到 100）",
                fontSize = 12.sp,
                color = if (sum == 100) QingJiGreen else Color(0xFFD64545)
            )
        }

        Spacer(Modifier.height(Dimens.Gap))

        // AI 专项：核心营养口径统一为热量 + 三大营养素，
        // 移除膳食纤维 / 钠 / 钙的每日目标录入入口。
        // 三个值仍随 onSaveGoals 按默认值写回档案，备份 JSON 也照旧导出，
        // 数据库字段与旧数据一行都不动（Room 的 defaultValue 与迁移 SQL
        // 必须逐字一致，动注解会触发清库兜底）。

        Spacer(Modifier.height(Dimens.Gap))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.ItemCorner))
                .background(if (sum == 100) QingJiGreen else DividerColor)
                .clickable(enabled = sum == 100) {
                    onSave(
                        protein.toIntOrNull() ?: 0,
                        carb.toIntOrNull() ?: 0,
                        fat.toIntOrNull() ?: 0
                    )
                    onSaveGoals(
                        fiber.toIntOrNull() ?: 25,
                        sodium.toIntOrNull() ?: 2000,
                        calcium.toIntOrNull() ?: 800
                    )
                    onBack()
                }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "保存", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}


@Composable
internal fun MacroField(label: String, value: String, unit: String = "%", onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            ),
            modifier = Modifier.width(96.dp)
        )
        Spacer(Modifier.size(6.dp))
        Text(text = unit, fontSize = 14.sp, color = TextSecondary)
    }
}


/**
 * 隐私与安全：说明 + 两档清除（v1.0.6 拆开）。
 *
 * 以前这里只有一个「清空本地记录」，文案写着会删围度/运动/照片，
 * 实际代码只清了 6 张表，那三样根本没动。现在按「清多少」分成两档，
 * 每一档下面直接写清「删哪些、留哪些」，不让用户靠猜。
 */
@Composable
internal fun PrivacyPage(
    onClearRecords: () -> Unit,
    onFactoryReset: () -> Unit,
    onBack: () -> Unit,
    useSheet: Boolean = false
) {
    var confirmRecords by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    SettingsDetailScaffold(title = "隐私与安全", onBack = onBack, useSheet = useSheet) {
        SectionCard {
            Text(
                text = "轻记没有账号、没有服务器、没有任何埋点统计：\n" +
                    "· 所有记录只存本机数据库，卸载 App 就一起没了；\n" +
                    "· API Key 用系统 Keystore 加密存，不进数据库、不进备份、不进日志；\n" +
                    "· 照片存在 App 私有目录，其它 App 读不到；\n" +
                    "· 只有你点「识别」时才联网，请求直接发给你填的服务商。",
                fontSize = 13.sp,
                color = TextPrimary,
                lineHeight = 21.sp
            )
        }

        Spacer(Modifier.height(Dimens.Gap))

        // ---------- 第一档：只清记录 ----------
        Text(
            text = "清除记录",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "删掉饮食、体重、饮水、日记、围度、进度照片、运动、断食记录和使用统计。\n" +
                "个人档案（身高体重目标）、自己录的食物、收藏、AI 配置和所有设置都保留 —— " +
                "适合「想重新开始记，但不想重新配置一遍」。",
            fontSize = 11.sp,
            color = TextSecondary,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(8.dp))
        DangerActionButton(text = "清除全部记录") { confirmRecords = true }

        Spacer(Modifier.height(20.dp))

        // ---------- 第二档：恢复出厂 ----------
        Text(
            text = "恢复出厂设置",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "在上面那一档的基础上，再删掉个人档案、目标、自己录的食物、收藏、" +
                "AI 配置、所有设置和 API Key。删完回到刚装好的状态，要重新填一次资料。\n" +
                "备份文件不会删（那是你导出来准备带走的东西）。",
            fontSize = 11.sp,
            color = TextSecondary,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(8.dp))
        DangerActionButton(text = "恢复出厂设置") { confirmReset = true }
    }

    if (confirmRecords) {
        com.qingji.app.ui.components.AppDialog(
            title = "确定清除全部记录？",
            confirmText = "确认清除",
            danger = true,
            onConfirm = {
                confirmRecords = false
                onClearRecords()
                onBack()
            },
            dismissText = "取消",
            onDismiss = { confirmRecords = false }
        ) {
            Text(
                text = "饮食、体重、饮水、日记、围度、照片、运动、断食、训练记录都会删掉，" +
                    "删了就找不回来。档案、目标、动作库和训练计划会留着。\n\n" +
                    "建议先去「数据备份与导出」导一份 JSON。",
                fontSize = 14.sp,
                color = TextPrimary,
                lineHeight = 21.sp
            )
        }
    }

    if (confirmReset) {
        com.qingji.app.ui.components.AppDialog(
            title = "确定恢复出厂设置？",
            confirmText = "确认恢复出厂",
            danger = true,
            onConfirm = {
                confirmReset = false
                onFactoryReset()
            },
            dismissText = "取消",
            onDismiss = { confirmReset = false }
        ) {
            Text(
                text = "这次是全部删掉：记录、档案、目标、自己录的食物、收藏、" +
                    "AI 配置、所有设置、API Key 都不留。\n\n" +
                    "删完会回到引导页，要重新填一次资料。这个操作不可撤销，" +
                    "现在先去导一份 JSON 备份还来得及。",
                fontSize = 14.sp,
                color = TextPrimary,
                lineHeight = 21.sp
            )
        }
    }
}
