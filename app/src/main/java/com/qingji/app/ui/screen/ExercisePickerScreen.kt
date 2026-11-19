package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.data.local.entity.ExercisePreset
import com.qingji.app.data.repository.HealthRepository
import com.qingji.app.domain.ExerciseCalculator
import com.qingji.app.ui.components.AppBottomSheet
import com.qingji.app.ui.components.ExerciseIcon
import com.qingji.app.ui.components.LabeledTextField
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.pressable
import com.qingji.app.ui.viewmodel.ExerciseViewModel

/**
 * ============================================================
 * 记录运动（v3.3.0 二级菜单）
 * ------------------------------------------------------------
 * 参考图要求：运动类目之上的东西（顶栏 / 拍照识别 / 搜索 / 分类标签）
 * **全部固定**，下滑只滚动下面的运动列表，所以这里是
 * Column（固定头） + LazyColumn(weight(1f))（唯一滚动区），不是整页 verticalScroll。
 *
 * 右上角「新建」录入自定义运动；因为不想动 Room 表结构，
 * 自定义运动用 category = 「自定义」标记，归到「自定义」页签。
 * ============================================================
 */
private val TABS = listOf("最近", "常用", "收藏", "自定义", "全部", "训练记录")
private const val TAB_RECENT = 0
private const val TAB_COMMON = 1
private const val TAB_FAVORITE = 2
private const val TAB_CUSTOM = 3
private const val TAB_ALL = 4
/** 2026-11-18：选已有训练，调整它已计入的消耗（不新建记录、不双计） */
private const val TAB_TRAINING = 5

@Composable
fun ExercisePickerScreen(
    onBack: () -> Unit,
    /** 选中一条运动 → 进「确认记录」三级页 */
    onPick: (Long) -> Unit,
    /** 2026-11-18：选「记力量」→ 跳训练中心的开始训练页 */
    onOpenStrengthTraining: () -> Unit = {},
    modifier: Modifier = Modifier,
    vm: ExerciseViewModel = viewModel()
) {
    val presets by vm.presets.collectAsState()
    val recentNames by vm.recentNames.collectAsState()
    // 2026-11-18：「训练记录」页签的数据
    val sessions by vm.sessions.collectAsState()
    val ledger by vm.ledger.collectAsState()

    var keyword by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(TAB_COMMON) }
    // 2026-11-18：先选记哪种——有氧走预设列表，力量跳训练中心
    var strengthMode by remember { mutableStateOf(false) }
    var showNewDialog by remember { mutableStateOf(false) }
    var showPhotoHint by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newMet by remember { mutableStateOf("5.0") }
    var error by remember { mutableStateOf("") }
    // 选中的训练场次 + 消耗输入弹层
    var selectedSession by remember {
        mutableStateOf<com.qingji.app.data.local.entity.WorkoutSession?>(null)
    }
    var sessionKcalInput by remember { mutableStateOf("") }
    var sessionAdjustError by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    val visible = remember(presets, recentNames, keyword, tab) {
        val base = when (tab) {
            TAB_RECENT -> {
                val order = recentNames.withIndex().associate { it.value to it.index }
                presets.filter { order.containsKey(it.name) }
                    .sortedBy { order[it.name] ?: Int.MAX_VALUE }
            }
            TAB_FAVORITE -> presets.filter { it.favorite }
            TAB_CUSTOM -> presets.filter { it.category == ExerciseCalculator.CUSTOM_CATEGORY }
            TAB_ALL -> presets
            // 常用 = 预置运动（DAO 已经把收藏置顶、按名称排好）
            else -> presets.filter { it.category != ExerciseCalculator.CUSTOM_CATEGORY }
        }
        if (keyword.isBlank()) base else base.filter {
            it.name.contains(keyword.trim(), ignoreCase = true)
        }
    }

    Column(modifier = modifier.fillMaxSize().background(PageBackground)) {
        // ---------- 固定区 1：顶栏 ----------
        PageTopBar(
            title = "记录运动",
            onBack = onBack,
            actionText = "新建",
            onAction = {
                newName = ""
                newMet = "5.0"
                error = ""
                showNewDialog = true
            }
        )

        // ---------- 2026-11-18：先选记有氧还是记力量 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf("记有氧" to false, "记力量" to true).forEach { (label, isStrength) ->
                val selected = strengthMode == isStrength
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) QingJiGreen else CardWhite)
                        .border(
                            width = 1.dp,
                            color = if (selected) Color.Transparent else DividerColor,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .pressable { strengthMode = isStrength }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isStrength) "记力量（练器械）" else "记有氧（跑步/骑行等）",
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) Color.White else TextPrimary
                    )
                }
            }
        }

        if (strengthMode) {
            // ---------- 力量：跳训练中心 ----------
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    SectionCard(contentPadding = 14.dp) {
                        Text(
                            text = "力量训练去训练中心记",
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "练器械、举铁这类记录在「训练」模块：选动作、记组数重量、自动算消耗，" +
                                "记完自动计入运动消耗和今日剩余额度。想补记之前忘记的训练，" +
                                "到「我的 → 训练记录」里点「补记一次」。",
                            fontSize = 13.sp, color = TextSecondary
                        )
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(QingJiGreen)
                                .pressable {
                                    strengthMode = false
                                    onOpenStrengthTraining()
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "去开始力量训练",
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "也可以切回「训练记录」页签，查看或调整已计入的消耗",
                            fontSize = 12.sp, color = TextSecondary
                        )
                    }
                }
            }
        } else {
        // ---------- 固定区 2：拍照识别 / 搜索 / 分类 ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            SectionCard(contentPadding = 14.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable { showPhotoHint = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(QingJiGreenLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            tint = QingJiGreenDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            text = "拍照识别",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(text = "拍一张，估算运动消耗", fontSize = 12.sp, color = TextSecondary)
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = DividerColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                singleLine = true,
                placeholder = { Text("搜索运动", fontSize = 13.sp, color = TextSecondary) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardWhite,
                    unfocusedContainerColor = CardWhite,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TABS.forEachIndexed { index, label ->
                    val selected = index == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) QingJiGreen else CardWhite)
                            .border(
                                width = 1.dp,
                                color = if (selected) Color.Transparent else DividerColor,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .pressable { tab = index }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Color.White else TextPrimary
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ---------- 唯一滚动区：运动列表 ----------
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (tab == TAB_TRAINING) {
                // 2026-11-18：列出用户真正做过的训练。点一条调整它已计入的消耗；
                // 保存走「覆盖同一账本行」，不新建运动记录，绝不双计。
                items(sessions, key = { it.uuid }) { session ->
                    val kcal = ledger.firstOrNull {
                        it.sourceType == HealthRepository.SOURCE_STRENGTH_TRAINING && it.sourceId == session.uuid
                    }?.caloriesGross ?: 0.0
                    TrainingSessionRow(
                        date = session.date,
                        durationMinutes = session.durationMinutes,
                        kcal = kcal,
                        onClick = {
                            selectedSession = session
                            sessionKcalInput = if (kcal > 0.0) kcal.toInt().toString() else ""
                            sessionAdjustError = ""
                        }
                    )
                }
                if (sessions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "还没有训练记录，先到训练中心完成一次训练",
                                fontSize = 13.sp, color = TextSecondary
                            )
                        }
                    }
                }
            } else {
            items(visible, key = { it.id }) { item ->
                ExercisePresetRow(
                    item = item,
                    onClick = { onPick(item.id) },
                    onToggleFavorite = { fav -> vm.toggleFavorite(item.id, fav) }
                )
            }
            if (visible.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                keyword.isNotBlank() -> "没找到这个运动，换个词或点「新建」"
                                tab == TAB_CUSTOM -> "还没有自定义运动，点右上角「新建」加一个"
                                tab == TAB_RECENT -> "还没有记录过运动，先选一项记一次吧"
                                tab == TAB_FAVORITE -> "还没有收藏的运动，点卡片右边的星标就能收藏"
                                else -> "这里暂时没有运动"
                            },
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
            } // else 分支结束（非训练页签的运动预设列表）
            item { Spacer(Modifier.height(16.dp)) }
        }
        } // else 分支结束（有氧模式：拍照 / 搜索 / 页签 / 运动列表）
    }

    // ---------- 新建自定义运动 ----------
    if (showNewDialog) {
        AppBottomSheet(
            title = "新建运动",
            subtitle = "自定义运动会出现在「自定义」页签里",
            confirmText = "保存",
            closeOnConfirm = false,
            onConfirm = {
                val name = newName.trim()
                val met = newMet.toDoubleOrNull()
                when {
                    name.isEmpty() -> error = "先给运动起个名字"
                    presets.any { it.name == name } -> error = "已经有同名运动了，换个名字"
                    met == null || met !in 0.5..20.0 -> error = "MET 请填 0.5 ~ 20 之间的数字"
                    else -> {
                        vm.addCustomExercise(name, met)
                        tab = TAB_CUSTOM
                        keyword = ""
                        error = ""
                        showNewDialog = false
                    }
                }
            },
            onDismiss = { showNewDialog = false }
        ) {
            LabeledTextField(
                label = "运动名称",
                value = newName,
                onValueChange = { newName = it; error = "" },
                hint = "例如：遛狗"
            )
            Spacer(Modifier.height(12.dp))
            LabeledTextField(
                label = "MET 值（不知道就填 5.0）",
                value = newMet,
                onValueChange = { newMet = it; error = "" },
                hint = "5.0",
                decimalOnly = true
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "消耗按「MET × 体重 × 时长」估算：数值越大代表越费力气。" +
                    "快走约 3.5、慢跑约 8、跳绳约 12。",
                fontSize = 11.sp,
                color = TextSecondary
            )
            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(text = error, fontSize = 12.sp, color = DangerRed)
            }
        }
    }

    // ---------- 2026-11-18：选训练后调整它已计入的消耗 ----------
    selectedSession?.let { session ->
        AppBottomSheet(
            title = "这次训练的消耗",
            subtitle = "${session.date} · ${session.durationMinutes} 分钟 · 已计入运动消耗",
            confirmText = "保存",
            closeOnConfirm = false,
            onConfirm = {
                val kcal = sessionKcalInput.toDoubleOrNull()
                if (kcal == null || kcal < 0) {
                    sessionAdjustError = "请填一个不小于 0 的数字"
                } else {
                    vm.adjustTrainingCalorie(session.uuid, kcal) { ok ->
                        if (ok) {
                            selectedSession = null
                            android.widget.Toast.makeText(
                                context, "已更新，运动消耗总额已同步", android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            sessionAdjustError = "这场训练没有找到消耗记录，请到训练中心重新保存一次"
                        }
                    }
                }
            },
            onDismiss = { selectedSession = null }
        ) {
            Text(
                text = "训练保存时已自动算过本次消耗。这里改的数字会直接更新到运动消耗总额，" +
                    "不会重复记一笔。",
                fontSize = 12.sp, color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = sessionKcalInput,
                onValueChange = { sessionKcalInput = it; sessionAdjustError = "" },
                singleLine = true,
                label = { Text("消耗（千卡）", fontSize = 12.sp) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (sessionAdjustError.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text = sessionAdjustError, fontSize = 12.sp, color = DangerRed)
            }
        }
    }
    // ---------- 拍照识别：诚实说明还没做，不摆一个点了没反应的假入口 ----------
    if (showPhotoHint) {
        AppBottomSheet(
            title = "拍照识别运动",
            confirmText = "知道了",
            onConfirm = {},
            onDismiss = { showPhotoHint = false }
        ) {
            Text(
                text = "拍一张运动截图或器材照片来估算消耗，这个功能还没做。" +
                    "现在先用下面的运动库选一项、填时长，消耗会自动按 MET × 体重 × 时长 算好。",
                fontSize = 14.sp,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "想加运动库里没有的项目，点右上角「新建」。",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

/** 运动列表里的一条：图标 + 名称 + 类型标签 + MET + 收藏星标。 */
@Composable
private fun ExercisePresetRow(
    item: ExercisePreset,
    onClick: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit
) {
    val isCustom = item.category == ExerciseCalculator.CUSTOM_CATEGORY
    val tag = if (isCustom) "自定义" else item.category
    val tagBg = if (isCustom) QingJiGreenLight else DangerRed.copy(alpha = 0.12f)
    val tagFg = if (isCustom) QingJiGreenDark else DangerRed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .pressable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = ExerciseIcon.of(item.name),
                contentDescription = null,
                tint = QingJiGreen,
                modifier = Modifier.size(34.dp)
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                text = item.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(tagBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = tag, fontSize = 10.sp, color = tagFg)
                }
                Spacer(Modifier.width(8.dp))
                Text(text = "MET ${item.met}", fontSize = 11.sp, color = TextSecondary)
            }
        }
        Icon(
            imageVector = if (item.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = "收藏",
            tint = if (item.favorite) QingJiGreen else DividerColor,
            modifier = Modifier
                .size(24.dp)
                .pressable { onToggleFavorite(!item.favorite) }
        )
    }
}

/**
 * 「训练记录」页签里的一行：日期 + 时长 + 已计入的消耗。
 * 点一下调整这次训练已计入的消耗（覆盖同一账本行，不新建记录）。
 */
@Composable
private fun TrainingSessionRow(
    date: java.time.LocalDate,
    durationMinutes: Int,
    kcal: Double,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .pressable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(QingJiGreenLight),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🏋️", fontSize = 22.sp)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = "${date.monthValue}月${date.dayOfMonth}日 力量训练",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "$durationMinutes 分钟 · 已计入 ${kcal.toInt()} 千卡",
                fontSize = 12.sp, color = TextSecondary
            )
        }
        Text(text = "调整", fontSize = 12.sp, color = QingJiGreenDark,
            modifier = Modifier.padding(8.dp))
    }
}
