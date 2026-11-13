package com.qingji.app.ui.screen.training

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.local.TrainingSeed
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.local.entity.WorkoutSession
import com.qingji.app.data.prefs.WorkoutDraft
import com.qingji.app.domain.TrainingMath
import com.qingji.app.domain.WorkoutSet
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import java.time.LocalDate
import java.time.YearMonth

/** 日记页顶部「心情日记 / 训练日记」切换。两个视图都放一份，切到哪边都能切回来。 */
@Composable
fun DiaryModeSwitch(
    training: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.PillCorner))
            .background(DividerColor)
            .padding(3.dp)
    ) {
        listOf(false to "心情日记", true to "训练日记").forEach { (isTraining, label) ->
            val selected = training == isTraining
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Dimens.PillCorner))
                    .background(if (selected) CardWhite else Color.Transparent)
                    .clickable { onChange(isTraining) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = TextSizes.Body,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) QingJiGreen else TextSecondary
                )
            }
        }
    }
}

/** 首页「今日训练」卡（放在热量主卡下面）。P0 没有计划联动，只反馈当天的实际状态。 */
@Composable
fun TodayTrainingCard(
    todaySessions: List<WorkoutSession>,
    draft: WorkoutDraft?,
    onClick: () -> Unit,
    /** P1：启用计划后显示今日安排；null = 没计划，走原来的引导文案。 */
    arrangement: com.qingji.app.ui.viewmodel.TodayArrangement? = null,
    modifier: Modifier = Modifier
) {
    val status = when {
        draft != null -> if (draft.editingUuid.isNotEmpty()) "正在修改一次训练" else "训练进行中 · 点这里继续"
        arrangement != null -> if (arrangement.isRest) {
            "今天休息 · ${arrangement.planName}"
        } else {
            "${arrangement.title} · ${arrangement.exercises.size} 个动作（${arrangement.planName}）"
        }
        todaySessions.isNotEmpty() -> {
            val minutes = todaySessions.sumOf { it.durationMinutes }
            "今天已练 ${todaySessions.size} 次 · $minutes 分钟"
        }
        else -> "还没开始，点这里记录第一次训练"
    }
    SectionCard(modifier = modifier.clickable { onClick() }) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("今日训练", fontSize = TextSizes.CardTitle, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(status, fontSize = TextSizes.Caption, color = TextSecondary)
                if (arrangement != null && !arrangement.isRest && arrangement.exercises.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        arrangement.exercises.joinToString("、") { it.exerciseNameSnapshot },
                        fontSize = TextSizes.Tiny, color = QingJiGreen,
                        maxLines = 2
                    )
                }
                if (arrangement != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(arrangement.notice, fontSize = TextSizes.Tiny, color = TextSecondary)
                }
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(QingJiGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "开始训练",
                    tint = QingJiGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** 训练月历：周一起始，练过的那天点一个绿点。 */
@Composable
fun TrainingCalendarCard(
    month: YearMonth,
    trainingDates: Set<LocalDate>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${month.year} 年 ${month.monthValue} 月",
                fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text("上个月", fontSize = TextSizes.Caption, color = QingJiGreen,
                modifier = Modifier.clickable { onPrevMonth() }.padding(horizontal = 8.dp, vertical = 4.dp))
            Text("下个月", fontSize = TextSizes.Caption, color = QingJiGreen,
                modifier = Modifier.clickable { onNextMonth() }.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { d ->
                Text(d, fontSize = TextSizes.Tiny, color = TextSecondary,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))

        val first = month.atDay(1)
        val leading = (first.dayOfWeek.value - 1 + 7) % 7   // 周一=1
        val days = month.lengthOfMonth()
        val totalCells = ((leading + days + 6) / 7) * 7
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (row in 0 until totalCells / 7) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val index = row * 7 + col
                        val dayNumber = index - leading + 1
                        val date = if (dayNumber in 1..days) month.atDay(dayNumber) else null
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = date != null) {
                                    if (date != null) onSelectDate(date)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (date != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = dayNumber.toString(),
                                        fontSize = TextSizes.Tiny,
                                        color = if (date in trainingDates) QingJiGreen else TextPrimary
                                    )
                                    if (date in trainingDates) {
                                        Box(
                                            Modifier
                                                .padding(top = 2.dp)
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(QingJiGreen)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 一个动作的一组数据：重量 / 次数 / RPE / 删除，附「+ 添加一组」。 */
@Composable
fun SetEditorCard(
    name: String,
    sets: List<WorkoutSet>,
    unit: String,
    fromLast: Boolean,
    onUpdate: (Int, WorkoutSet) -> Unit,
    onAdd: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onRemoveExercise: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        modifier = modifier,
        contentPadding = Dimens.CardPaddingCompact
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontSize = TextSizes.CardTitle, fontWeight = FontWeight.SemiBold,
                color = TextPrimary, modifier = Modifier.weight(1f))
            if (fromLast) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.PillCorner))
                        .background(QingJiGreenLight)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("上次成绩", fontSize = TextSizes.Tiny, color = QingJiGreen)
                }
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.Close, contentDescription = "移除动作", tint = TextSecondary,
                modifier = Modifier.size(18.dp).clickable { onRemoveExercise() })
        }
        if (fromLast) {
            Spacer(Modifier.height(4.dp))
            Text("已带出上一次每组的重量和次数，直接改成这次的就行。",
                fontSize = TextSizes.Tiny, color = TextSecondary)
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("组", fontSize = TextSizes.Tiny, color = TextSecondary, modifier = Modifier.width(24.dp))
            Text("重量($unit)", fontSize = TextSizes.Tiny, color = TextSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("次数", fontSize = TextSizes.Tiny, color = TextSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("RPE", fontSize = TextSizes.Tiny, color = TextSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Spacer(Modifier.width(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        sets.forEachIndexed { index, set ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", fontSize = TextSizes.Body, color = TextSecondary,
                    modifier = Modifier.width(24.dp))
                NumberField(
                    value = if (unit == "lb") TrainingMath.kgToLb(set.weightKg) else set.weightKg,
                    isDecimal = true,
                    onValue = { raw ->
                        val kg = if (unit == "lb") TrainingMath.lbToKg(raw) else raw
                        onUpdate(index, set.copy(weightKg = kg))
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
                )
                NumberField(
                    value = set.reps.toDouble(),
                    isDecimal = false,
                    onValue = { raw -> onUpdate(index, set.copy(reps = raw.toInt())) },
                    modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
                )
                NumberField(
                    value = set.rpe.toDouble(),
                    isDecimal = false,
                    placeholder = "可选",
                    onValue = { raw -> onUpdate(index, set.copy(rpe = raw.toInt().coerceIn(0, 10))) },
                    modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
                )
                Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                    Text("×", fontSize = 16.sp, color = TextSecondary,
                        modifier = Modifier.clickable { onRemoveSet(index) })
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("添加一组", fontSize = TextSizes.Body)
        }
    }
}

/** 紧凑的数字输入框。空串/非数字按 0 处理，不让一条坏输入把页面卡住。 */
@Composable
private fun NumberField(
    value: Double,
    isDecimal: Boolean,
    onValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    val display = if (value == 0.0) "" else if (isDecimal) {
        (Math.round(value * 100) / 100.0).toString()
    } else value.toInt().toString()
    var text by remember(value) { mutableStateOf(display) }
    BasicTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() || (isDecimal && it == '.') }
            text = filtered
            onValue(filtered.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number),
        textStyle = TextStyle(fontSize = TextSizes.Body, color = TextPrimary, textAlign = TextAlign.Center),
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(Dimens.TinyCorner))
            .background(DividerColor.copy(alpha = 0.5f))
            .padding(vertical = 9.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (text.isEmpty()) Text(placeholder.ifEmpty { "0" }, fontSize = TextSizes.Tiny, color = TextSecondary)
                inner()
            }
        }
    )
}

/** 进行中训练的计时条。存的是开始时间戳，暂停时长在草稿里累计。 */
@Composable
fun WorkoutTimerBar(
    elapsedSeconds: Long,
    paused: Boolean,
    editing: Boolean,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier, contentPadding = Dimens.CardPaddingCompact) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(if (editing) "修改这次训练" else "本次训练时长",
                    fontSize = TextSizes.Caption, color = TextSecondary)
                Text(
                    text = if (editing) "时长保持原样" else TrainingMath.formatDuration(elapsedSeconds),
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                )
            }
            if (!editing) {
                OutlinedButton(onClick = onTogglePause) {
                    Text(if (paused) "继续" else "暂停", fontSize = TextSizes.Body)
                }
            }
        }
    }
}

/** 肌群胶囊筛选（7 类 + 「全部」）。 */
@Composable
fun MuscleCategoryTabs(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val all: List<Pair<String?, String>> =
            listOf(null to "全部") + TrainingSeed.CATEGORY_LABELS.map { it.first to it.second }
        all.forEach { (key, label) ->
            val active = selected == key
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.PillCorner))
                    .background(if (active) QingJiGreen else QingJiGreenLight)
                    .clickable { onSelect(key) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(label, fontSize = TextSizes.Caption,
                    color = if (active) CardWhite else QingJiGreen)
            }
        }
    }
}

/** 动作选择弹层：搜索 + 肌群筛选 + 一键开始记录 + 自建动作。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    exercises: List<ExerciseEntity>,
    onPick: (ExerciseEntity) -> Unit,
    onAddCustom: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var showCustom by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    val filtered = exercises.filter { e ->
        (category == null || e.category == category) &&
            (query.isBlank() || e.name.contains(query.trim(), ignoreCase = true) ||
                e.primaryMuscles.contains(query.trim(), ignoreCase = true))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CardWhite,
        contentColor = TextPrimary
    ) {
        Column(modifier = Modifier.padding(horizontal = Dimens.ScreenPadding)) {
            Text("选一个动作", fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.ItemCorner))
                    .background(DividerColor.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = TextSizes.Body, color = TextPrimary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("搜动作名或肌群", fontSize = TextSizes.Body, color = TextSecondary)
                        inner()
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
            MuscleCategoryTabs(selected = category, onSelect = { category = it })
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered, key = { it.uuid }) { e ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.TinyCorner))
                            .clickable { onPick(e); onDismiss() }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(e.name, fontSize = TextSizes.Body, color = TextPrimary)
                            Text(
                                listOf(e.primaryMuscles, e.equipment).filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                fontSize = TextSizes.Tiny, color = TextSecondary
                            )
                        }
                        if (e.isFavorite) Text("★", fontSize = 14.sp, color = QingJiGreen)
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text("没找到，下面可以自己加一个动作。",
                            fontSize = TextSizes.Caption, color = TextSecondary,
                            modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (showCustom) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = TextSizes.Body, color = TextPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Dimens.TinyCorner))
                            .background(DividerColor.copy(alpha = 0.45f))
                            .padding(10.dp),
                        decorationBox = { inner ->
                            if (customName.isEmpty()) Text("动作名称", fontSize = TextSizes.Caption, color = TextSecondary)
                            inner()
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (customName.isNotBlank()) {
                            onAddCustom(customName, category ?: TrainingSeed.CORE)
                            customName = ""
                            showCustom = false
                            onDismiss()
                        }
                    }) { Text("添加", fontSize = TextSizes.Body) }
                }
            } else {
                OutlinedButton(onClick = { showCustom = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("动作库里没有？自己加一个", fontSize = TextSizes.Body)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
