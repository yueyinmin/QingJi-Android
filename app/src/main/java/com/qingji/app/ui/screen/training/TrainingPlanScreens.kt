package com.qingji.app.ui.screen.training

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.local.TrainingTemplateData
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.MonthPickerSheet
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.TrainingPlanViewModel

private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 自定义计划编辑时的一天草稿。 */
private data class CustomDayDraft(
    val weekday: Int,
    var title: String = "训练",
    var exerciseNames: List<String> = emptyList(),
    /** QJ 训练联动：每个动作的目标参数（组数, 次数, 重量kg） */
    var targets: Map<String, Triple<Int, Int, Double>> = emptyMap()
)

/**
 * P1 我的训练计划：
 * 当前启用计划 + 7 天安排预览 + 7 类模板 + 自定义计划（逐天逐动作选） + 计划历史。
 * 模板只作示例安排，顶部常驻合规提示，见 TrainingTemplateData.NOTICE。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingPlanScreen(
    vm: TrainingPlanViewModel,
    onBack: () -> Unit,
    /** QJ 训练联动：进独立全屏自定义计划编辑页 */
    onOpenCustomEdit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val plans by vm.plans.collectAsState()
    val active by vm.activePlan.collectAsState()
    val days by vm.activePlanDays.collectAsState()
    val planExercises by vm.planExercises.collectAsState()
    val library by vm.exercises.collectAsState()
    val message by vm.message.collectAsState()
    // v1.4 健康体系：最近训练列表，供「从历史训练生成计划」选择
    val recentSessions by vm.sessions.collectAsState()

    var pendingTemplate by remember { mutableStateOf<String?>(null) }
    var showCustom by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("我的训练计划") }
    var customDays by remember { mutableStateOf((1..7).map { CustomDayDraft(it) }) }
    var editingDay by remember { mutableStateOf<Int?>(null) }
    // QJ：动作选择的搜索与分类筛选（复用动作库同款能力）
    var exerciseQuery by remember { mutableStateOf("") }
    var exerciseCategory by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    // QJ 动态列表治理：历史训练的日期筛选（null = 默认最近5次；选了则看该日全部）
    var historyFilterDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var showHistoryDatePicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(title = "我的训练计划", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(10.dp))
            if (message != null) {
                Text(message.orEmpty(), fontSize = TextSizes.Caption, color = QingJiGreen)
                Spacer(Modifier.height(6.dp))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.ItemCorner))
                    .background(QingJiGreenLight)
                    .padding(10.dp)
            ) {
                Text(TrainingTemplateData.NOTICE, fontSize = TextSizes.Caption, color = QingJiGreen)
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 当前启用 ----------
            SectionCard {
                Text("当前计划", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                if (active == null) {
                    Text("还没启用计划。选一个模板，或者自己排一周。",
                        fontSize = TextSizes.Body, color = TextSecondary)
                } else {
                    Text(active!!.name, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, color = QingJiGreen)
                    Spacer(Modifier.height(10.dp))
                    days.sortedBy { it.weekday }.forEach { d ->
                        val count = planExercises.count { it.dayId == d.id }
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(WEEKDAYS[d.weekday - 1], fontSize = TextSizes.Caption,
                                color = TextSecondary, modifier = Modifier.weight(1f))
                            Text(
                                if (d.isRest) "休息" else "${d.title} · $count 个动作",
                                fontSize = TextSizes.Body, color = TextPrimary,
                                modifier = Modifier.weight(3f)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.deactivate(active!!.uuid) }) {
                        Text("停用计划（会留在历史里）", fontSize = TextSizes.Caption, color = TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 7 类模板 ----------
            Text("计划模板", fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            TrainingTemplateData.templates.forEach { t ->
                SectionCard(
                    modifier = Modifier.clickable { pendingTemplate = t.id },
                    contentPadding = Dimens.CardPaddingCompact
                ) {
                    Text(t.name, fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(3.dp))
                    Text(t.summary, fontSize = TextSizes.Caption, color = TextSecondary)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        t.days.joinToString(" / ") { "${WEEKDAYS[it.weekday - 1]} ${it.title}" },
                        fontSize = TextSizes.Tiny, color = QingJiGreen
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            PrimaryButton(
                text = "自己排一周（自定义计划）",
                // QJ 训练联动：改为进独立全屏编辑页；原弹层入口保留兼容（见下方 showCustom）。
                onClick = onOpenCustomEdit
            )

            Spacer(Modifier.height(16.dp))

            // ---------- v1.4 健康体系：从历史训练生成计划（工单 4.5） ----------
            // 取最近 5 次训练，点「存为我的计划」就把那场的动作安排复制成一份
            // 不激活的计划副本；动作库缺了的动作会按名字保留并提示。
            // QJ 动态列表治理：防重复判断（UUID）原样保留；
            // 默认最近5次独立训练，超出的由 ExpandableHistoryList 展开（300dp 内滚 + 收起 + 动画）。
            // 日期筛选是辅助能力：选了日期显示该日全部训练，清除后恢复默认5次。
            val eligibleHistory = recentSessions
                .filter { it.uuid != active?.source?.removePrefix("history:") }
            val historyCandidates = if (historyFilterDate != null) {
                eligibleHistory.filter { it.date == historyFilterDate }
            } else {
                eligibleHistory.sortedByDescending { it.date }.take(5)
            }
            if (eligibleHistory.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("从历史训练生成", fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary,
                        modifier = Modifier.weight(1f))
                    Text(
                        text = if (historyFilterDate != null) "${historyFilterDate} ✕" else "按日期筛选",
                        fontSize = TextSizes.Caption, color = QingJiGreen,
                        modifier = Modifier.clickable { showHistoryDatePicker = true }
                            .padding(4.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("把练过的一场动作安排存成计划副本，之后可随时启用；不影响历史记录。",
                    fontSize = TextSizes.Tiny, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                if (historyCandidates.isEmpty()) {
                    Text("这天没有可生成的历史训练", fontSize = TextSizes.Caption, color = TextSecondary)
                } else {
                    ExpandableHistoryList(
                        items = historyCandidates,
                        collapsedCount = 5,
                        keyOf = { it.uuid },
                        expandText = "查看全部历史训练"
                    ) { s ->
                        SectionCard(contentPadding = Dimens.CardPaddingCompact) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(s.date.toString(), fontSize = TextSizes.Body, color = TextPrimary)
                                    Text("${s.durationMinutes} 分钟", fontSize = TextSizes.Tiny, color = TextSecondary)
                                }
                                TextButton(onClick = { vm.saveHistoryAsTemplate(s) }) {
                                    Text("存为我的计划", fontSize = TextSizes.Caption, color = QingJiGreen)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // ---------- 计划历史 ----------
            // QJ 动态列表治理：默认最近5个历史计划（按现有时间字段排序），
            // 超出由 ExpandableHistoryList 展开（300dp 内滚 + 收起 + 动画）；
            // 重新启用 / 删除 / source 关联全部原样保留。
            val history = plans.filter { !it.isActive }
            if (history.isNotEmpty()) {
                Text("计划历史", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                ExpandableHistoryList(
                    items = history.sortedByDescending { it.createdAt },
                    collapsedCount = 5,
                    keyOf = { it.uuid },
                    expandText = "查看全部"
                ) { p ->
                    SectionCard(contentPadding = Dimens.CardPaddingCompact) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, fontSize = TextSizes.Body, color = TextPrimary)
                                Text(
                                    when {
                                        p.source.startsWith("template:") -> "来自模板"
                                        p.source.startsWith("history:") -> "来自历史训练"
                                        else -> "自定义"
                                    },
                                    fontSize = TextSizes.Tiny, color = TextSecondary)
                            }
                            TextButton(onClick = { vm.reactivate(p.uuid) }) {
                                Text("重新启用", fontSize = TextSizes.Caption, color = QingJiGreen)
                            }
                            TextButton(onClick = { pendingDelete = p.uuid }) {
                                Text("删除", fontSize = TextSizes.Caption, color = TextSecondary)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }

    // QJ 动态列表治理：历史训练的日期筛选（辅助能力；清除筛选恢复默认最近5次）
    if (showHistoryDatePicker) {
        MonthPickerSheet(
            selected = historyFilterDate ?: com.qingji.app.domain.AppClock.today(),
            onSelect = { historyFilterDate = it },
            onDismiss = { showHistoryDatePicker = false }
        )
    }

    // 模板启用确认：启用会顶掉当前计划，先问一句
    pendingTemplate?.let { id ->
        val t = TrainingTemplateData.byId(id)
        AppDialog(
            title = "启用「${t?.name}」？",
            confirmText = "启用",
            onConfirm = {
                vm.useTemplate(id)
                pendingTemplate = null
            },
            onDismiss = { pendingTemplate = null }
        ) {
            Text("当前计划会被停用并留在历史里，训练记录不受影响。\n${TrainingTemplateData.NOTICE}",
                fontSize = 14.sp, color = TextPrimary)
        }
    }

    pendingDelete?.let { uuid ->
        AppDialog(
            title = "删掉这份计划？",
            confirmText = "删除",
            danger = true,
            onConfirm = { vm.delete(uuid); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        ) {
            Text("只删计划安排，已经保存的训练记录不会动。", fontSize = 14.sp, color = TextPrimary)
        }
    }

    // ---------- 自定义计划弹层（先排 7 天，再逐天选动作） ----------
    if (showCustom) {
        ModalBottomSheet(
            onDismissRequest = { showCustom = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CardWhite,
            contentColor = TextPrimary
        ) {
            Column(modifier = Modifier.padding(horizontal = Dimens.ScreenPadding)) {
                if (editingDay == null) {
                    Text("自定义计划", fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    BasicTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = TextSizes.Body, color = TextPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.ItemCorner))
                            .background(DividerColor.copy(alpha = 0.45f))
                            .padding(10.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    customDays.forEach { day ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingDay = day.weekday }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(WEEKDAYS[day.weekday - 1], fontSize = TextSizes.Body,
                                color = TextSecondary, modifier = Modifier.weight(1f))
                            Text(
                                if (day.exerciseNames.isEmpty()) "休息 / 点这里选动作"
                                else "${day.title} · ${day.exerciseNames.size} 个动作",
                                fontSize = TextSizes.Body, color = TextPrimary,
                                modifier = Modifier.weight(3f)
                            )
                            Text("›", fontSize = 18.sp, color = QingJiGreen)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            vm.useCustomPlan(
                                customName,
                                customDays.filter { it.exerciseNames.isNotEmpty() }
                                    .map {
                                        TrainingTemplateData.TemplateDay(
                                            it.weekday, it.title, it.exerciseNames,
                                            targets = it.targets.ifEmpty { null }
                                        )
                                    }
                            )
                            showCustom = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("保存并启用", fontSize = TextSizes.Body) }
                } else {
                    val weekday = editingDay!!
                    val day = customDays.first { it.weekday == weekday }
                    Text("${WEEKDAYS[weekday - 1]} · 选动作", fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("点一下选中，再点一下取消。选完按「完成」。",
                        fontSize = TextSizes.Caption, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    // QJ：搜索框（名称 / 主要肌群）
                    BasicTextField(
                        value = exerciseQuery,
                        onValueChange = { exerciseQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = TextSizes.Body, color = TextPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.ItemCorner))
                            .background(DividerColor.copy(alpha = 0.45f))
                            .padding(10.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    // QJ：肌群分类筛选（复用动作库同款胶囊）
                    MuscleCategoryTabs(selected = exerciseCategory, onSelect = { exerciseCategory = it })
                    Spacer(Modifier.height(8.dp))
                    val filteredLibrary = library.filter { e ->
                        (exerciseCategory == null || e.category == exerciseCategory) &&
                            (exerciseQuery.isBlank() || e.name.contains(exerciseQuery.trim(), ignoreCase = true) ||
                                e.primaryMuscles.contains(exerciseQuery.trim(), ignoreCase = true))
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                        items(filteredLibrary, key = { it.uuid }) { e: ExerciseEntity ->
                            val checked = e.name in day.exerciseNames
                            // QJ 训练联动：已选动作显示并设置目标组/次/重量（默认 3组×10次×0kg）
                            val preset = day.targets[e.name] ?: Triple(3, 10, 0.0)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Dimens.TinyCorner))
                                    .background(if (checked) QingJiGreenLight else CardWhite)
                                    .clickable {
                                        customDays = customDays.map { d ->
                                            if (d.weekday != weekday) d
                                            else d.copy(
                                                exerciseNames = if (checked)
                                                    d.exerciseNames - e.name else d.exerciseNames + e.name
                                            )
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // QJ：动作缩略图（复用真实素材，找不到显示首字）
                                    Box(
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                                            .background(DividerColor.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ExerciseMediaImage(
                                            media = com.qingji.app.data.training.media.ExerciseMediaMapping.forUuid(e.uuid),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Text(e.name.take(1), fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold, color = QingJiGreen)
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(e.name, fontSize = TextSizes.Body,
                                        color = TextPrimary, modifier = Modifier.weight(1f))
                                    Text(if (checked) "✓" else "＋", fontSize = 16.sp,
                                        color = if (checked) QingJiGreen else TextSecondary)
                                }
                                if (checked) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        TargetField("组", preset.first.toString(), Modifier.weight(1f)) { v ->
                                            val sets = v.toIntOrNull()?.coerceIn(1, 20) ?: return@TargetField
                                            updateCustomTarget(weekday, e.name, customDays) { old ->
                                                Triple(sets, old.second, old.third)
                                            }.let { customDays = it }
                                        }
                                        TargetField("次", preset.second.toString(), Modifier.weight(1f)) { v ->
                                            val reps = v.toIntOrNull()?.coerceIn(1, 100) ?: return@TargetField
                                            updateCustomTarget(weekday, e.name, customDays) { old ->
                                                Triple(old.first, reps, old.third)
                                            }.let { customDays = it }
                                        }
                                        TargetField("kg", if (preset.third == 0.0) "" else preset.third.toString(),
                                            Modifier.weight(1f)) { v ->
                                            val kg = v.toDoubleOrNull()?.coerceIn(0.0, 1000.0) ?: 0.0
                                            updateCustomTarget(weekday, e.name, customDays) { old ->
                                                Triple(old.first, old.second, kg)
                                            }.let { customDays = it }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { editingDay = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("完成", fontSize = TextSizes.Body)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** P1 个人最佳：按动作汇总历史最好成绩，1RM 一律带「估算」角标。 */
@Composable
fun PersonalBestScreen(
    vm: TrainingPlanViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bests by vm.personalBests.collectAsState()
    Column(modifier = modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(title = "个人最佳", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))
            Text("按历史记录自动汇总，改动作名不影响已保存的成绩。",
                fontSize = TextSizes.Caption, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            if (bests.isEmpty()) {
                SectionCard {
                    Text("还没有训练记录。先去记一次训练，这里就会自动出现最好成绩。",
                        fontSize = TextSizes.Body, color = TextSecondary)
                }
            } else {
                bests.forEach { b ->
                    SectionCard(contentPadding = Dimens.CardPaddingCompact) {
                        Text(b.name, fontSize = TextSizes.CardTitle,
                            fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("最大重量", fontSize = TextSizes.Tiny, color = TextSecondary)
                                Text("%.1f kg".format(b.maxWeightKg), fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("${b.repsAtMax} 次", fontSize = TextSizes.Tiny, color = TextSecondary)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("估算 1RM", fontSize = TextSizes.Tiny, color = TextSecondary)
                                Text("%.1f kg".format(b.best1RM), fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold, color = QingJiGreen)
                                Text("估算", fontSize = TextSizes.Tiny, color = QingJiGreen)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "共 ${b.sessionCount} 次训练 / ${b.totalSets} 组" +
                                (b.date?.let { " · 最好成绩 ${it}" } ?: ""),
                            fontSize = TextSizes.Tiny, color = TextSecondary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text("1RM 用 Epley 公式估算，次数越多误差越大，仅供参考，不是医学结论。",
                    fontSize = TextSizes.Tiny, color = TextSecondary)
            }
            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }
}

/** QJ 训练联动：计划动作的目标参数小输入框（组 / 次 / kg），本地记住文本避免受控输入卡顿。 */
@Composable
private fun TargetField(label: String, initial: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.TinyCorner))
            .background(DividerColor.copy(alpha = 0.4f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = TextSizes.Tiny, color = TextSecondary)
        Spacer(Modifier.width(3.dp))
        BasicTextField(
            value = text,
            onValueChange = { v ->
                text = v
                onValue(v)
            },
            singleLine = true,
            textStyle = TextStyle(fontSize = TextSizes.Caption, color = TextPrimary),
            modifier = Modifier.weight(1f)
        )
    }
}

/** QJ 训练联动：更新某天某个动作的目标参数（组/次/重量），不可变复制，不改原草稿其它字段。 */
private fun updateCustomTarget(
    weekday: Int,
    name: String,
    days: List<CustomDayDraft>,
    transform: (Triple<Int, Int, Double>) -> Triple<Int, Int, Double>
): List<CustomDayDraft> = days.map { d ->
    if (d.weekday != weekday) d
    else {
        val old = d.targets[name] ?: Triple(3, 10, 0.0)
        d.copy(targets = d.targets + (name to transform(old)))
    }
}
