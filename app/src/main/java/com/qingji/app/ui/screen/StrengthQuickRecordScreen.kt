package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.TrainingMath
import com.qingji.app.domain.WorkoutSet
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.*
import com.qingji.app.ui.theme.pressable
import com.qingji.app.ui.viewmodel.ExerciseViewModel

/**
 * 阶段 C：力量动作快速补记页。
 * 从统一运动入口选力量动作后进入，直接录入组数/重量/次数，保存为正式训练会话。
 * 不再跳训练中心，也不强制计时；保存走 saveUnifiedSession（一场一条账本，幂等）。
 */
@Composable
fun StrengthQuickRecordScreen(
    exerciseUuid: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ExerciseViewModel = viewModel()
) {
    val weightKg by vm.weightKg.collectAsState()
    val isSaving by vm.isSaving.collectAsState()
    val options by vm.unifiedOptions.collectAsState()
    val name = options.firstOrNull { it.exerciseUuid == exerciseUuid }?.name ?: "力量动作"
    val today = remember { AppClock.today() }

    var sets by remember {
        mutableStateOf(listOf(WorkoutSet(reps = 10, completed = true)))
    }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(title = "补记 $name", onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(contentPadding = 14.dp) {
                    Text("$today · $name", fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text("直接补记组数，保存后计入运动消耗与今日剩余", fontSize = 12.sp, color = TextSecondary)
                }
            }
            itemsIndexed(sets) { idx, set ->
                SectionCard(contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第 ${idx + 1} 组", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = TextPrimary, modifier = Modifier.weight(1f))
                        if (sets.size > 1) {
                            Text("删除", fontSize = 12.sp, color = DangerRed,
                                modifier = Modifier.pressable { sets = sets.filterIndexed { i, _ -> i != idx } }
                                    .padding(8.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = if (set.weightKg == 0.0) "" else set.weightKg.toString(),
                            onValueChange = { v -> sets = sets.mapIndexed { i, s ->
                                if (i == idx) s.copy(weightKg = v.toDoubleOrNull() ?: 0.0) else s } },
                            label = { Text("重量 kg") }, singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = if (set.reps == 0) "" else set.reps.toString(),
                            onValueChange = { v -> sets = sets.mapIndexed { i, s ->
                                if (i == idx) s.copy(reps = v.toIntOrNull() ?: 0) else s } },
                            label = { Text("次数") }, singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("RPE", fontSize = 12.sp, color = TextSecondary)
                        Spacer(Modifier.width(8.dp))
                        (0..10).forEach { r ->
                            Text(
                                r.toString(), fontSize = 12.sp,
                                color = if (set.rpe == r) Color.White else TextSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (set.rpe == r) QingJiGreen else CardWhite)
                                    .pressable { sets = sets.mapIndexed { i, s -> if (i == idx) s.copy(rpe = r) else s } }
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            item {
                Row {
                    PrimaryButton(
                        text = "+ 添加一组",
                        onClick = { sets = sets + WorkoutSet(reps = 10, completed = true) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(), minLines = 2
                )
            }
            if (error.isNotBlank()) {
                item { Text(error, fontSize = 12.sp, color = DangerRed) }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            PrimaryButton(
                text = if (isSaving) "保存中…" else "保存补记",
                enabled = !isSaving && sets.any { it.reps > 0 },
                onClick = {
                    vm.saveStrengthQuick(
                        exerciseUuid = exerciseUuid, exerciseName = name,
                        sets = sets.map { it.copy(completed = true) },
                        weightKg = weightKg,
                        onDone = onSaved,
                        onError = { error = it }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
