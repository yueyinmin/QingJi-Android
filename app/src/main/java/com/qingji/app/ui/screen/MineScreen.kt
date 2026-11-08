package com.qingji.app.ui.screen

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.domain.Achievement
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.NutritionCalculator
import com.qingji.app.domain.WeightUnit
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.components.ThinProgressBar
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.MineViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.Period

/**
 * ============================================================
 * 我的（M6 增强完成）
 * ------------------------------------------------------------
 * 这一版补上了需求文档 M6 里原来「点了没反应」的三处：
 * 1. 我的目标：能改目标体重 / 活动量 / 减重模式，改完立刻重算热量预算；
 * 2. 我的成就：11 枚徽章按真实数据解锁，规则在 domain/Achievements.kt；
 * 3. 调用统计卡：补上「本月 AI 调用」这个真数字。
 * 另外头像支持选本地图，昵称读设置里填的。
 * ============================================================
 */
@Composable
fun MineScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    vm: MineViewModel = viewModel()
) {
    val totals by vm.totals.collectAsState()
    val profile by vm.profile.collectAsState()
    val monthAi by vm.monthAi.collectAsState()
    val achievements by vm.achievements.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember { SettingsStore.observe(context) }
        .collectAsState(initial = QingJiSettings())

    var showGoalDialog by remember { mutableStateOf(false) }
    var showAchievementDialog by remember { mutableStateOf(false) }

    // 选头像：系统相册选一张，拷进 App 私有目录，只存路径
    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = copyAvatarToPrivate(context, uri)
            if (path != null) {
                scope.launch {
                    SettingsStore.set(
                        context,
                        com.qingji.app.data.prefs.SettingsKeys.AVATAR_PATH,
                        path
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.size(24.dp))
            Text(
                text = "我的",
                fontSize = TextSizes.PageTitle,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "设置",
                tint = TextPrimary,
                modifier = Modifier
                    .size(22.dp)
                    .clickable { onOpenSettings() }
            )
        }

        Spacer(Modifier.height(16.dp))

        ProfileCard(
            profile = profile,
            totals = totals,
            settings = settings,
            onPickAvatar = { pickAvatar.launch("image/*") }
        )

        Spacer(Modifier.height(12.dp))

        UsageCard(totalAi = totals["ai"] ?: 0, monthAi = monthAi)

        Spacer(Modifier.height(12.dp))

        DataEntryCard(
            onOpenGoal = { showGoalDialog = true },
            onOpenAchievements = { showAchievementDialog = true },
            unlockedCount = achievements.count { it.unlocked },
            totalCount = achievements.size
        )

        Spacer(Modifier.height(24.dp))
    }

    if (showGoalDialog) {
        GoalDialog(
            profile = profile,
            unit = settings.unit,
            onSave = { targetWeightKg, activityLevel, mode ->
                scope.launch { vm.saveGoal(targetWeightKg, activityLevel, mode) }
                showGoalDialog = false
            },
            onDismiss = { showGoalDialog = false }
        )
    }

    if (showAchievementDialog) {
        AchievementDialog(
            achievements = achievements,
            onDismiss = { showAchievementDialog = false }
        )
    }
}

/** 头像 + 性别年龄身高 + 加入天数 + 八格使用统计。 */
@Composable
private fun ProfileCard(
    profile: UserProfile?,
    totals: Map<String, Int>,
    settings: QingJiSettings,
    onPickAvatar: () -> Unit
) {
    val genderText = if (profile?.gender == "female") "女" else "男"
    val age = profile?.birthday?.let { Period.between(it, AppClock.today()).years } ?: 0
    val height = profile?.heightCm?.toInt() ?: 0
    val joinedDays = profile?.let {
        val created = LocalDate.ofEpochDay(it.createdAt / 86_400_000L)
        (AppClock.today().toEpochDay() - created.toEpochDay()).toInt() + 1
    } ?: 1

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(QingJiGreenLight)
                    .border(2.dp, QingJiGreen.copy(alpha = 0.3f), CircleShape)
                    .clickable { onPickAvatar() },
                contentAlignment = Alignment.Center
            ) {
                if (settings.avatarPath.isNotBlank() && File(settings.avatarPath).exists()) {
                    AsyncImage(
                        model = File(settings.avatarPath),
                        contentDescription = "头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = settings.nickname.take(1),
                        fontSize = 24.sp,
                        color = QingJiGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = settings.nickname,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$genderText · ${if (age > 0) "${age}岁" else "未填生日"} · " +
                        "${if (height > 0) "${height}cm" else "未填身高"}",
                    fontSize = 13.sp,
                    color = QingJiGreen
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "加入轻记 $joinedDays 天 · 点头像可换图",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "使用情况",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )

        Spacer(Modifier.height(10.dp))

        val items = listOf(
            "图像识别" to (totals["image"] ?: 0).toString(),
            "文字识别" to (totals["text"] ?: 0).toString(),
            "语音识别" to (totals["voice"] ?: 0).toString(),
            "条码" to (totals["barcode"] ?: 0).toString(),
            "AI 分析" to (totals["ai"] ?: 0).toString(),
            "日记" to (totals["diary"] ?: 0).toString(),
            "体型图" to (totals["body"] ?: 0).toString(),
            "体重" to (totals["weight"] ?: 0).toString()
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { (label, value) ->
                        StatCell(label = label, value = value, modifier = Modifier.weight(1f))
                    }
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "数字是本机累计次数，喝水、记饮食、记体重都会计入",
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}

/** 一格统计：上面图标位、中间数字、下面名称。 */
@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PageBackground)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(QingJiGreen.copy(alpha = 0.18f))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(2.dp))
        Text(text = label, fontSize = 11.sp, color = TextSecondary)
    }
}

/** 调用统计（对应需求文档 M6 的会员卡区域）。token 和费用不瞎估。 */
@Composable
private fun UsageCard(totalAi: Int, monthAi: Int) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "调用统计",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = "本月 $monthAi 次",
                fontSize = 12.sp,
                color = QingJiGreenDark,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(QingJiGreenLight)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            UsageNumber(
                label = "累计 AI 调用",
                value = totalAi.toString(),
                unit = "次",
                modifier = Modifier.weight(1f)
            )
            UsageNumber(label = "消耗 token", value = "—", unit = "", modifier = Modifier.weight(1f))
            UsageNumber(label = "估算花费", value = "—", unit = "", modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "token 与费用以服务商账单为准，本地只记调用次数 · 数据只存在本机",
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun UsageNumber(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = label, fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = " $unit",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

/** 我的目标 / 我的成就 两个入口。 */
@Composable
private fun DataEntryCard(
    onOpenGoal: () -> Unit,
    onOpenAchievements: () -> Unit,
    unlockedCount: Int,
    totalCount: Int
) {
    SectionCard {
        Text(
            text = "数据",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(10.dp))
        EntryRow(
            title = "我的目标",
            trailing = "减重计划",
            iconColor = Color(0xFF9B8AFB),
            onClick = onOpenGoal
        )
        Spacer(Modifier.height(6.dp))
        EntryRow(
            title = "我的成就",
            trailing = if (totalCount > 0) "$unlockedCount/$totalCount" else "",
            iconColor = Color(0xFF5B8DEF),
            onClick = onOpenAchievements
        )
    }
}

@Composable
private fun EntryRow(
    title: String,
    trailing: String,
    iconColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(iconColor)
            )
        }
        Text(
            text = title,
            fontSize = 15.sp,
            color = TextPrimary,
            modifier = Modifier.padding(start = 12.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        if (trailing.isNotBlank()) {
            Text(text = trailing, fontSize = 12.sp, color = TextSecondary)
        }
        Text(
            text = "›",
            fontSize = 18.sp,
            color = DividerColor,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

// ============================================================
// 我的目标弹窗
// ============================================================
@Composable
private fun GoalDialog(
    profile: UserProfile?,
    unit: String,
    onSave: (Double, Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var targetText by remember {
        mutableStateOf(
            profile?.targetWeight?.takeIf { it > 0 }?.let { WeightUnit.format(it, unit) } ?: ""
        )
    }
    var activity by remember { mutableStateOf(profile?.activityLevel ?: 2) }
    var mode by remember { mutableStateOf(NutritionCalculator.MODE_LOSE) }
    var error by remember { mutableStateOf("") }

    com.qingji.app.ui.components.AppBottomSheet(
        title = "我的目标",
        confirmText = "保存并重算",
        closeOnConfirm = false,
        onConfirm = {
            val kg = WeightUnit.parseToKg(targetText, unit)
            if (kg == null) {
                error = "目标体重填得不太对，检查一下数字"
            } else {
                onSave(kg, activity, mode)
            }
        },
        onDismiss = onDismiss
    ) {
        Column {
                Text(
                    text = "目标体重（${WeightUnit.symbol(unit)}）",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it; error = "" },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))
                Text("目标模式", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                OptionRow(
                    options = listOf(
                        NutritionCalculator.MODE_LOSE to "减重",
                        NutritionCalculator.MODE_KEEP to "维持",
                        NutritionCalculator.MODE_GAIN to "增重"
                    ),
                    selected = mode,
                    onSelect = { mode = it }
                )

                Spacer(Modifier.height(14.dp))
                Text("活动量", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                (1..4).forEach { level ->
                    val selected = level == activity
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) QingJiGreenLight else PageBackground)
                            .clickable { activity = level }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = NutritionCalculator.activityLabel(level),
                            fontSize = 13.sp,
                            color = if (selected) QingJiGreenDark else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) Text("✓", fontSize = 13.sp, color = QingJiGreen)
                    }
                    Spacer(Modifier.height(4.dp))
                }

                if (error.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, fontSize = 12.sp, color = Color(0xFFE5544B))
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "保存后按新目标重算每日热量与三大营养素（公式：Mifflin-St Jeor）。",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
        }
    }
}

@Composable
private fun OptionRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) QingJiGreen else PageBackground)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = if (isSelected) Color.White else TextSecondary
                )
            }
        }
    }
}

// ============================================================
// 成就弹窗
// ============================================================
@Composable
private fun AchievementDialog(
    achievements: List<Achievement>,
    onDismiss: () -> Unit
) {
    val unlocked = achievements.count { it.unlocked }

    com.qingji.app.ui.components.AppBottomSheet(
        title = "我的成就（$unlocked/${achievements.size}）",
        onDismiss = onDismiss
    ) {
        Column {
                if (achievements.isEmpty()) {
                    Text("还在算…记一笔就会有了", fontSize = 13.sp, color = TextSecondary)
                }
                achievements.forEach { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (item.unlocked) "★" else "○",
                                fontSize = 14.sp,
                                color = if (item.unlocked) Color(0xFFF5A623) else TextSecondary
                            )
                            Text(
                                text = item.title,
                                fontSize = 14.sp,
                                fontWeight = if (item.unlocked) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                                color = if (item.unlocked) TextPrimary else TextSecondary,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = item.progressText,
                                fontSize = 11.sp,
                                color = if (item.unlocked) QingJiGreen else TextSecondary
                            )
                        }
                        Text(
                            text = item.desc,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 20.dp, top = 2.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        ThinProgressBar(
                            progress = item.progress,
                            barHeight = 4.dp,
                            modifier = Modifier.padding(start = 20.dp)
                        )
                    }
                }
        }
    }
}

/** 把用户选的头像拷进 App 私有目录，返回文件路径。 */
private fun copyAvatarToPrivate(context: Context, uri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val target = File(dir, "avatar.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.absolutePath
    } catch (e: Exception) {
        null
    }
}
