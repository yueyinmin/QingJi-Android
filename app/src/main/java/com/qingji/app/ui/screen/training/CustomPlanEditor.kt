package com.qingji.app.ui.screen.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.local.TrainingTemplateData
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.TrainingPlanViewModel

/** 自定义计划编辑的一天（全屏页与弹层共用）。 */
data class PlanDayDraft(
    val weekday: Int,
    var title: String = "训练",
    var exerciseNames: List<String> = emptyList(),
    var targets: Map<String, Triple<Int, Int, Double>> = emptyMap()
)

/**
 * ============================================================
 * 自定义训练计划编辑器（QJ 训练联动 · 全屏页与弹层共用的唯一实现）
 * ------------------------------------------------------------
 * 两段式：7 天安排 → 某天选动作（搜索 / 分类 / 缩略图 / 目标组次重量）。
 * 保存统一走 [TrainingPlanViewModel.useCustomPlan]，业务逻辑只有一处。
 * 状态在本组件内部 remember：进入即一份新草稿，取消不污染原计划。
 * ============================================================
 */
@Composable
fun CustomPlanEditor(
    vm: TrainingPlanViewModel,
    library: List<ExerciseEntity>,
    onDone: () -> Unit
) {
    var customName by remember { mutableStateOf("我的训练计划") }
    var days by remember { mutableStateOf((1..7).map { PlanDayDraft(it) }) }
    var editingDay by remember { mutableStateOf<Int?>(null) }
    var exerciseQuery by remember { mutableStateOf("") }
    var exerciseCategory by remember { mutableStateOf<String?>(null) }

    if (editingDay == null) {
        Text("自定义计划", fontSize = TextSizes.CardTitle,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = TextPrimary)
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
        days.forEach { day ->
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
        PrimaryButton(text = "保存并启用", onClick = {
            vm.useCustomPlan(
                customName,
                days.filter { it.exerciseNames.isNotEmpty() }
                    .map {
                        TrainingTemplateData.TemplateDay(
                            it.weekday, it.title, it.exerciseNames,
                            targets = it.targets.ifEmpty { null }
                        )
                    }
            )
            onDone()
        })
    } else {
        val weekday = editingDay!!
        val day = days.first { it.weekday == weekday }
        Text("${WEEKDAYS[weekday - 1]} · 选动作", fontSize = TextSizes.CardTitle,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("点一下选中，再点一下取消。选完按「完成」。",
            fontSize = TextSizes.Caption, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
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
        MuscleCategoryTabs(selected = exerciseCategory, onSelect = { exerciseCategory = it })
        Spacer(Modifier.height(8.dp))
        val filtered = library.filter { e ->
            (exerciseCategory == null || e.category == exerciseCategory) &&
                (exerciseQuery.isBlank() || e.name.contains(exerciseQuery.trim(), ignoreCase = true) ||
                    e.primaryMuscles.contains(exerciseQuery.trim(), ignoreCase = true))
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
            items(filtered, key = { it.uuid }) { e: ExerciseEntity ->
                val checked = e.name in day.exerciseNames
                val preset = day.targets[e.name] ?: Triple(3, 10, 0.0)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.TinyCorner))
                        .background(if (checked) QingJiGreenLight else CardWhite)
                        .clickable {
                            days = days.map { d ->
                                if (d.weekday != weekday) d
                                else d.copy(
                                    exerciseNames = if (checked) d.exerciseNames - e.name else d.exerciseNames + e.name
                                )
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.qingji.app.ui.components.TrainingExerciseThumbnail(
                            exerciseUuid = e.uuid, fallbackName = e.name
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(e.name, fontSize = TextSizes.Body, color = TextPrimary, modifier = Modifier.weight(1f))
                        Text(if (checked) "✓" else "＋", fontSize = 16.sp,
                            color = if (checked) QingJiGreen else TextSecondary)
                    }
                    if (checked) {
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PlanTargetField("组", preset.first.toString(), Modifier.weight(1f)) { v ->
                                val sets = v.toIntOrNull()?.coerceIn(1, 20) ?: return@PlanTargetField
                                days = updatePlanTarget(weekday, e.name, days) { Triple(sets, it.second, it.third) }
                            }
                            PlanTargetField("次", preset.second.toString(), Modifier.weight(1f)) { v ->
                                val reps = v.toIntOrNull()?.coerceIn(1, 100) ?: return@PlanTargetField
                                days = updatePlanTarget(weekday, e.name, days) { Triple(it.first, reps, it.third) }
                            }
                            PlanTargetField("kg", if (preset.third == 0.0) "" else preset.third.toString(),
                                Modifier.weight(1f)) { v ->
                                val kg = v.toDoubleOrNull()?.coerceIn(0.0, 1000.0) ?: 0.0
                                days = updatePlanTarget(weekday, e.name, days) { Triple(it.first, it.second, kg) }
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
}

/** QJ 训练联动：自定义计划的独立全屏编辑页（三段式流程的一段容器，编辑逻辑与弹层共用）。 */
@Composable
fun CustomPlanEditScreen(
    vm: TrainingPlanViewModel,
    onBack: () -> Unit
) {
    val library by vm.exercises.collectAsState()
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.qingji.app.ui.theme.PageBackground)
    ) {
        com.qingji.app.ui.components.PageTopBar(title = "自定义训练计划", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))
            CustomPlanEditor(vm = vm, library = library, onDone = onBack)
            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }
}

private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 计划动作的目标参数小输入框，本地记住文本避免受控输入卡顿。 */
@Composable
private fun PlanTargetField(label: String, initial: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Row(
        modifier = modifier.clip(RoundedCornerShape(Dimens.TinyCorner))
            .background(DividerColor.copy(alpha = 0.4f)).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = TextSizes.Tiny, color = TextSecondary)
        Spacer(Modifier.width(3.dp))
        BasicTextField(value = text, onValueChange = { text = it; onValue(it) },
            singleLine = true, textStyle = TextStyle(fontSize = TextSizes.Caption, color = TextPrimary),
            modifier = Modifier.weight(1f))
    }
}

private fun updatePlanTarget(
    weekday: Int,
    name: String,
    days: List<PlanDayDraft>,
    transform: (Triple<Int, Int, Double>) -> Triple<Int, Int, Double>
): List<PlanDayDraft> = days.map { d ->
    if (d.weekday != weekday) d
    else {
        val old = d.targets[name] ?: Triple(3, 10, 0.0)
        d.copy(targets = d.targets + (name to transform(old)))
    }
}
