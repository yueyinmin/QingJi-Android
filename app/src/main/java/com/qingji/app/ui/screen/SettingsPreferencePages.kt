package com.qingji.app.ui.screen

import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock

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
import com.qingji.app.ui.components.AppDialog
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
    useSheet: Boolean = false,
    /** V1：日记隐私锁统一状态（Activity 作用域），由设置页调用统一业务层。 */
    privacyVm: com.qingji.app.ui.viewmodel.DiaryPrivacyViewModel? = null,
    /** 阶段 K：AI 数据规模（点在清除按钮上才查，不用一进页面就读库）。 */
    onInspectAiData: suspend () -> String = { "" },
    /** 阶段 K：清除全部 AI 数据（会话 / 消息 / 工具记录 / 记忆 / 提醒）。 */
    onClearAiData: () -> Unit = {}
) {
    var confirmRecords by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    // 阶段 K：AI 数据单独一档，确认弹窗里先告诉用户要删掉多少
    var confirmAi by remember { mutableStateOf(false) }
    var aiSummary by remember { mutableStateOf("") }
    var showSetup by remember { mutableStateOf(false) }
    var showDisable by remember { mutableStateOf(false) }
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var pinOld by remember { mutableStateOf("") }
    var showChangePin by remember { mutableStateOf(false) }
    val privacyScope = androidx.compose.runtime.rememberCoroutineScope()

    SettingsDetailScaffold(title = "隐私与安全", onBack = onBack, useSheet = useSheet) {
        // ---------- V1：日记隐私锁 ----------
        if (privacyVm != null) {
            SectionCard {
                com.qingji.app.ui.screen.settings.SwitchRow(
                    title = "日记隐私锁",
                    subtitle = if (privacyVm.config.enabled)
                        "已开启 · ${if (privacyVm.config.biometricEnabled) "生物识别+数字密码" else "数字密码"}"
                    else "解锁后才可查看训练/心情/饮食/运动/睡眠日记",
                    checked = privacyVm.config.enabled,
                    onCheckedChange = { on ->
                        if (on) { pin1 = ""; pin2 = ""; showSetup = true }
                        else { pin1 = ""; showDisable = true }
                    }
                )
                if (privacyVm.config.enabled) {
                    com.qingji.app.ui.screen.settings.SwitchRow(
                        title = "生物识别解锁",
                        subtitle = if (privacyVm.availability == com.qingji.app.domain.BiometricAvailability.SUCCESS)
                            "已开启 · 系统生物识别（具体模态以系统为准）"
                        else "设备当前不可用，可使用数字密码",
                        checked = privacyVm.config.biometricEnabled,
                        onCheckedChange = { on -> privacyScope.launch { privacyVm.setBiometricEnabled(on) } }
                    )
                    com.qingji.app.ui.screen.settings.SettingIconRow(
                        title = "修改数字密码",
                        icon = Icons.Default.Lock,
                        iconColor = com.qingji.app.ui.theme.QingJiGreen,
                        onClick = { pinOld = ""; pin1 = ""; pin2 = ""; showChangePin = true }
                    )
                }
            }
            Spacer(Modifier.height(Dimens.Gap))
        }

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

        // ---------- 阶段 K：AI 数据单独一档 ----------
        Text(
            text = "AI 数据",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "删掉 AI 管家产生的全部内容：对话与消息、工具调用记录、用量记录、" +
                "长期记忆、管家提醒历史，以及「不再记住此类」和提醒开关。\n" +
                "AI 服务配置和 API Key 会保留（不想留 Key 就用下面的恢复出厂）；" +
                "饮食、体重、训练这些业务记录一条不动。",
            fontSize = 11.sp,
            color = TextSecondary,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(8.dp))
        DangerActionButton(text = "清除全部 AI 数据") {
            privacyScope.launch {
                aiSummary = onInspectAiData()
                confirmAi = true
            }
        }

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

    if (confirmAi) {
        com.qingji.app.ui.components.AppDialog(
            title = "确定清除全部 AI 数据？",
            confirmText = "确认清除",
            danger = true,
            onConfirm = {
                confirmAi = false
                onClearAiData()
            },
            dismissText = "取消",
            onDismiss = { confirmAi = false }
        ) {
            Text(
                text = "将要删除：$aiSummary。\n\n" +
                    "对话、消息、长期记忆、提醒历史都会消失，删了找不回来。" +
                    "饮食、体重、训练等业务记录不受影响，AI 服务配置和 Key 也会保留。",
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


    // 首次开启：两次输入数字密码，确认后由统一业务层启用
    if (showSetup && privacyVm != null) {
        AppDialog(
            title = "设置数字密码",
            onDismiss = { showSetup = false },
            confirmText = "开启",
            confirmEnabled = pin1.length >= com.qingji.app.domain.DiaryPasscode.MIN_LENGTH && pin1 == pin2,
            onConfirm = {
                privacyVm.setupPasscode(pin1, pin2) { ok -> if (ok) showSetup = false }
            }
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = pin1, onValueChange = { if (it.length <= 8) pin1 = it.filter { c -> c.isDigit() } },
                label = { androidx.compose.material3.Text("数字密码（4~8位）") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = pin2, onValueChange = { if (it.length <= 8) pin2 = it.filter { c -> c.isDigit() } },
                label = { androidx.compose.material3.Text("再次确认") }, modifier = Modifier.fillMaxWidth()
            )
            if (pin2.isNotBlank() && pin1 != pin2) {
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Text("两次输入不一致", fontSize = 11.sp, color = com.qingji.app.ui.theme.DangerRed)
            }
        }
    }

    // 修改数字密码：先验证原密码，再两次输入新密码
    if (showChangePin && privacyVm != null) {
        AppDialog(
            title = "修改数字密码",
            onDismiss = { showChangePin = false },
            confirmText = "确认修改",
            confirmEnabled = pinOld.isNotBlank() &&
                pin1.length >= com.qingji.app.domain.DiaryPasscode.MIN_LENGTH && pin1 == pin2,
            onConfirm = {
                privacyVm.changePasscode(pinOld, pin1, pin2) { ok -> if (ok) showChangePin = false }
            }
        ) {
            androidx.compose.material3.OutlinedTextField(value = pinOld, onValueChange = { pinOld = it.filter { c -> c.isDigit() } },
                label = { androidx.compose.material3.Text("原密码") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(value = pin1, onValueChange = { pin1 = it.filter { c -> c.isDigit() } },
                label = { androidx.compose.material3.Text("新密码") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(value = pin2, onValueChange = { pin2 = it.filter { c -> c.isDigit() } },
                label = { androidx.compose.material3.Text("再次确认新密码") }, modifier = Modifier.fillMaxWidth())
            if (privacyVm.message.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Text(privacyVm.message, fontSize = 11.sp, color = com.qingji.app.ui.theme.DangerRed)
            }
        }
    }

    // 关闭隐私锁：必须先通过身份验证（数字密码）
    if (showDisable && privacyVm != null) {
        AppDialog(
            title = "关闭日记隐私锁",
            onDismiss = { showDisable = false },
            confirmText = "验证并关闭",
            confirmEnabled = pin1.isNotBlank(),
            onConfirm = {
                if (privacyVm.verifyPasscode(pin1)) {
                    privacyScope.launch {
                        privacyVm.disablePrivacy { showDisable = false }
                    }
                }
            }
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = pin1, onValueChange = { pin1 = it.filter { c -> c.isDigit() } },
                label = { androidx.compose.material3.Text("输入数字密码验证") }, modifier = Modifier.fillMaxWidth()
            )
            if (privacyVm.message.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Text(privacyVm.message, fontSize = 11.sp, color = com.qingji.app.ui.theme.DangerRed)
            }
        }
    }
}
