package com.qingji.app.ui.screen

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.qingji.app.ui.components.GroupCard
import com.qingji.app.ui.components.GroupDivider
import com.qingji.app.ui.components.GroupHeader
import com.qingji.app.ui.components.IconTones
import com.qingji.app.ui.components.SettingsRow
import com.qingji.app.ui.components.StatCell
import com.qingji.app.ui.components.UsageStatCard
import com.qingji.app.ui.components.ThinProgressBar
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.MineViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.Period
import com.qingji.app.ui.i18n.s

/**
 * ============================================================
 * 我的（M6 增强完成）
 * ------------------------------------------------------------
 * 这一版补上了需求文档 M6 里原来「点了没反应」的三处：
 * 1. 我的目标：能改目标体重 / 活动量 / 减重模式，改完立刻重算热量预算；
 * 2. 我的成就：11 枚徽章按真实数据解锁，规则在 domain/Achievements.kt；
 * 3. 调用统计卡：补上「本月 AI 调用」这个真数字（v3.2.5 起放在整页最下方）。
 * 另外头像支持选本地图，昵称读设置里填的。
 * ============================================================
 */
@Composable
fun MineScreen(
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    /** 训练中心 P0：训练记录全屏页 */
    onOpenTrainingHistory: () -> Unit = {},
    /** 训练中心 P0：直接开一次训练 */
    onStartTraining: () -> Unit = {},
    /** 训练中心 P1：计划 / 动作库 / 收藏 / 个人最佳 */
    onOpenTrainingPlan: () -> Unit = {},
    onOpenExerciseLibrary: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenPersonalBest: () -> Unit = {},
    /** 自律管理（SD-001）：模块首页 */
    onOpenSelfDiscipline: () -> Unit = {},
    /** 自律管理（SD-001）：模块设置 */
    onOpenSelfDisciplineSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** tabfix：滚动状态由 Root 持有，切 Tab 回来不丢位置。 */
    scrollState: ScrollState = rememberScrollState(),
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
            .verticalScroll(scrollState)
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
                text = s("我的"),
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

        // 资料卡：横向布局、头像 58dp、整张卡可点进个人资料
        ProfileHeaderCard(
            nickname = settings.nickname,
            profile = profile,
            settings = settings,
            onOpenProfile = onOpenProfile
        )

        GroupHeader("数据")
        DataGroup(
            goalLabel = when (settings.goalMode) {
                "gain" -> "增肌计划"
                "keep" -> "保持计划"
                else -> "减脂计划"
            },
            onOpenGoal = { showGoalDialog = true },
            onOpenAchievements = { showAchievementDialog = true },
            unlockedCount = achievements.count { it.unlocked },
            totalCount = achievements.size
        )

        GroupHeader("自律管理")
        GroupCard {
            SettingsRow(
                title = "目标与打卡",
                icon = Icons.Outlined.CheckCircle,
                tone = IconTones.Green,
                trailing = "计时 / 历史",
                onClick = onOpenSelfDiscipline
            )
            GroupDivider()
            SettingsRow(
                title = "模块设置",
                icon = Icons.Outlined.Tune,
                tone = IconTones.Gray,
                trailing = "隐私 / 提醒",
                onClick = onOpenSelfDisciplineSettings
            )
        }

        GroupHeader("训练管理")
        TrainingGroup(
            onOpenTrainingHistory = onOpenTrainingHistory,
            onStartTraining = onStartTraining,
            onOpenTrainingPlan = onOpenTrainingPlan,
            onOpenExerciseLibrary = onOpenExerciseLibrary,
            onOpenFavorites = onOpenFavorites,
            onOpenPersonalBest = onOpenPersonalBest
        )

        GroupHeader("使用情况")
        UsageSectionCard(totals = totals)

        Spacer(Modifier.height(12.dp))

        // 调用统计卡放整页最下方：平时不挡视线，想看 AI 调用次数往下滑到底即可
        UsageCard(totalAi = totals["ai"] ?: 0, monthAi = monthAi)

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

/** v1.0.7：头像 + 昵称（整块可点，进个人资料页）+ 性别年龄身高 + 加入天数。 */
@Composable
private fun ProfileHeaderCard(
    nickname: String,
    profile: UserProfile?,
    settings: QingJiSettings,
    onOpenProfile: () -> Unit
) {
    val genderText = if (profile?.gender == "female") "女" else "男"
    val age = profile?.birthday?.let { Period.between(it, AppClock.today()).years } ?: 0
    val height = profile?.heightCm?.toInt() ?: 0
    val joinedDays = profile?.let {
        val created = LocalDate.ofEpochDay(it.createdAt / 86_400_000L)
        (AppClock.today().toEpochDay() - created.toEpochDay()).toInt() + 1
    } ?: 1

    // 统一工单：头像缩小到 58dp、横向布局、昵称做主信息、整张卡可点、右侧统一箭头
    GroupCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenProfile() }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(QingJiGreenLight)
                    .border(1.dp, QingJiGreen.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (settings.avatarPath.isNotBlank() && File(settings.avatarPath).exists()) {
                    AsyncImage(
                        model = File(settings.avatarPath),
                        contentDescription = "头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = settings.nickname.take(1),
                        fontSize = 22.sp,
                        color = QingJiGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = nickname,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$genderText · ${if (age > 0) "${age}岁" else "未填生日"} · " +
                        "${if (height > 0) "${height}cm" else "未填身高"}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "加入轻记 $joinedDays 天",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.55f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 使用情况：三列宫格，每格浅色底 + 小图标 + 名称 + 累计次数。
 * 统计口径一条都没动，只是换了排版（工单要求：不创建独立大卡片、不重复显示）。
 */
@Composable
private fun UsageSectionCard(totals: Map<String, Int>) {
    // 顺序、数据来源、统计口径全部保持原样，只换成 2 列 × 4 排的横卡
    val items = listOf(
        StatCell("图像识别", (totals["image"] ?: 0).toString(), Icons.Outlined.PhotoCamera, IconTones.Blue),
        StatCell("文字识别", (totals["text"] ?: 0).toString(), Icons.Outlined.Description, IconTones.Teal),
        StatCell("语音识别", (totals["voice"] ?: 0).toString(), Icons.Outlined.Mic, IconTones.Purple),
        StatCell("条码", (totals["barcode"] ?: 0).toString(), Icons.Outlined.QrCode, IconTones.Gray),
        StatCell("AI 分析", (totals["ai"] ?: 0).toString(), Icons.Outlined.AutoAwesome, IconTones.Teal),
        StatCell("日记", (totals["diary"] ?: 0).toString(), Icons.AutoMirrored.Outlined.MenuBook, IconTones.Green),
        StatCell("体型图", (totals["body"] ?: 0).toString(), Icons.Outlined.AccessibilityNew, IconTones.Pink),
        StatCell("体重", (totals["weight"] ?: 0).toString(), Icons.Outlined.MonitorWeight, IconTones.Orange)
    )
    GroupCard(contentPadding = 10.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 8 项正好 4 排，不需要补空位；每排两个 weight(1f)，宽度随屏幕自适应
            items.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { cell ->
                        UsageStatCard(
                            label = cell.label,
                            value = cell.value,
                            icon = cell.icon,
                            tone = cell.tone,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** 调用统计（对应需求文档 M6 的会员卡区域）。token 和费用不瞎估。 */
@Composable
private fun UsageCard(totalAi: Int, monthAi: Int) {
    GroupCard(contentPadding = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = s("调用统计"),
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
            text = s("token 与费用以服务商账单为准，本地只记调用次数 · 数据只存在本机"),
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

/** 数据：目标 + 成就两个入口。 */
@Composable
private fun DataGroup(
    goalLabel: String,
    onOpenGoal: () -> Unit,
    onOpenAchievements: () -> Unit,
    unlockedCount: Int,
    totalCount: Int
) {
    GroupCard {
        SettingsRow(
            title = "我的目标",
            icon = Icons.Outlined.Flag,
            tone = IconTones.Orange,
            trailing = goalLabel,
            onClick = onOpenGoal
        )
        GroupDivider()
        SettingsRow(
            title = "我的成就",
            icon = Icons.Outlined.EmojiEvents,
            tone = IconTones.Purple,
            trailing = if (totalCount > 0) "$unlockedCount/$totalCount" else "",
            onClick = onOpenAchievements
        )
    }
}

/** 训练管理：六个入口，全部统一列表行（「开始训练」也走普通行，不做大绿按钮）。 */
@Composable
private fun TrainingGroup(
    onOpenTrainingHistory: () -> Unit,
    onStartTraining: () -> Unit,
    onOpenTrainingPlan: () -> Unit,
    onOpenExerciseLibrary: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPersonalBest: () -> Unit
) {
    GroupCard {
        SettingsRow("训练记录", Icons.Outlined.History, IconTones.Blue, onClick = onOpenTrainingHistory)
        GroupDivider()
        SettingsRow("开始训练", Icons.Outlined.FitnessCenter, IconTones.Orange, onClick = onStartTraining)
        GroupDivider()
        SettingsRow("我的训练计划", Icons.Outlined.EventNote, IconTones.Purple, onClick = onOpenTrainingPlan)
        GroupDivider()
        SettingsRow("动作库", Icons.Outlined.LibraryBooks, IconTones.Teal, onClick = onOpenExerciseLibrary)
        GroupDivider()
        SettingsRow("我的动作收藏", Icons.Filled.Favorite, IconTones.Pink, onClick = onOpenFavorites)
        GroupDivider()
        SettingsRow("个人最佳", Icons.Outlined.WorkspacePremium, IconTones.Green, onClick = onOpenPersonalBest)
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

    // 目标模式跟着设置走：引导选了什么，这里就显示什么。
    // 以前写死「减重」，用户在引导里选「增肌」后进这里一看是减重，
    // 直接点保存就把目标覆盖回减重了。
    val dialogContext = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        mode = com.qingji.app.domain.ProfileInputRules.normalizeMode(
            com.qingji.app.data.prefs.SettingsStore.observe(dialogContext).first().goalMode
        )
    }

    com.qingji.app.ui.components.AppBottomSheet(
        title = "我的目标",
        confirmText = "保存并重算",
        closeOnConfirm = false,
        onConfirm = {
            val kg = WeightUnit.parseToKg(targetText, unit)
            when {
                kg == null -> error = "目标体重填得不太对，检查一下数字"
                // 和引导、个人资料页共用同一段合理范围（20~350kg）
                kg !in com.qingji.app.domain.ProfileInputRules.WEIGHT_RANGE_KG ->
                    error = "目标体重超出合理范围了，检查一下数字"
                else -> onSave(kg, activity, mode)
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
                        NutritionCalculator.MODE_LOSE to "减脂",
                        NutritionCalculator.MODE_KEEP to "保持",
                        NutritionCalculator.MODE_GAIN to "增肌"
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
                    text = s("保存后按新目标重算每日热量与三大营养素（公式：Mifflin-St Jeor）。"),
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
fun copyAvatarToPrivate(context: Context, uri: Uri): String? {
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
