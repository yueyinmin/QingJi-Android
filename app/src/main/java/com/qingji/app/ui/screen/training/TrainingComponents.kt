package com.qingji.app.ui.screen.training

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
import com.qingji.app.data.training.media.ExerciseMediaMapping
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import java.time.LocalDate
import java.time.YearMonth

/** 日记页顶部「训练日记 / 心情日记」切换：训练日记在左、心情日记在右（主人 2026-11-13 指定）。两个视图都放一份，切到哪边都能切回来。 */
@Composable
fun DiaryModeSwitch(
    training: Boolean,
    onChange: (Boolean) -> Unit,
    /** 专项工单 2026-11-16：由 DiaryScreen 传入的**共享进度**（0=训练日记，1=心情日记）。
     *  胶囊不再自己跑一个动画实例 —— 旧版胶囊 200ms、内容 250ms 各跑各的，
     *  两边节奏对不齐，就是工单里「选中胶囊与页面内容不同步」的来源之一。
     *  现在胶囊和内容读同一个值：胶囊走到哪一帧，页面就走到哪一帧。 */
    progress: Float,
    modifier: Modifier = Modifier
) {
    // 回归工单 P1：
    // 旧版用「底部小圆点 + (cellWidth - 4.dp)/2、y=34.dp 固定偏移」表达选中，
    // 既不能完整覆盖对应的那半边，也靠魔法数字修补位置，切换时还容易看着像错位。
    // 现在改成：外层统一胶囊 + 两个等权 Tab + 一整块宽度正好等于半格的选中背景，
    // 圆角与外层一致；位置只由传入的共享进度驱动，画在 graphicsLayer 绘制层，
    // 动画每帧只重绘、不重组，连续快速切换也不会闪。
    val pillShape = RoundedCornerShape(Dimens.PillCorner)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // 高度固定 44dp：BoxWithConstraints 是 SubcomposeLayout，不支持 IntrinsicSize 测量，
            // 用 IntrinsicSize 会直接崩（真机复现过）；内容是固定文字，写死高度即可。
            .height(44.dp)
            .clip(pillShape)
            .background(CardWhite)
            .padding(3.dp)
    ) {
        // padding 已经从 constraints 里扣掉，这里的 maxWidth 就是两个 Tab 的真实可用宽度
        val cellWidth = maxWidth / 2
        val cellWidthPx = with(LocalDensity.current) { cellWidth.toPx() }
        Box(
            modifier = Modifier
                .width(cellWidth)
                .fillMaxHeight()
                // 只平移绘制层，不改布局测量：选中块永远精确覆盖左/右半格
                .graphicsLayer { translationX = progress * cellWidthPx }
                .clip(pillShape)
                .background(QingJiGreenLight)
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(true to s("训练日记"), false to s("心情日记")).forEach { (isTraining, label) ->
                val selected = training == isTraining
                // 灰色选中条的来源：clickable 默认带 Material 涟漪，按下那一瞬
                // 整个半格会先铺一层灰色按压底（还带一个灰圈），绿指示条滑过来时
                // 叠在上面就成了「灰色选中条 + 第二个绿色标签」。
                // 现在关掉涟漪，按压反馈只交给绿指示条本身的位置变化。
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onChange(isTraining) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = TextSizes.Body,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) QingJiGreen else TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
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
    // 折叠状态纯 UI 本地：展开/收起不触发任何 ViewModel/数据库重查。
    // 无计划且无记录时不提供展开（空态不展开空白）。
    var expanded by remember { mutableStateOf(false) }
    val hasContent = arrangement != null || todaySessions.isNotEmpty()
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(180),
        label = "training_expand_arrow"
    )
    val status = when {
        draft != null -> if (draft.editingUuid.isNotEmpty()) "正在修改一次训练" else "训练进行中 · 点这里继续"
        arrangement != null -> if (arrangement.isRest) {
            "今天休息 · ${arrangement.planName}"
        } else {
            "${arrangement.title} · ${arrangement.exercises.size} 个动作（${arrangement.planName}）"
        }
        todaySessions.isNotEmpty() -> "点这里记录下一次训练"
        else -> "还没开始，点这里记录第一次训练"
    }
    // 训练次数与总时长拆成独立摘要行：原来它是 status 的一个分支，
    // 有草稿/计划时会被覆盖，收起态看不到（工单要求收起保留该摘要）。
    val summary = if (todaySessions.isNotEmpty()) {
        val minutes = todaySessions.sumOf { it.durationMinutes }
        "今天已练 ${todaySessions.size} 次 · $minutes 分钟"
    } else null

    SectionCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 左侧标题点击切换折叠；右侧 + 按钮进入训练，两者互不误触
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = hasContent) { expanded = !expanded }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("今日训练", fontSize = TextSizes.CardTitle, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    if (hasContent) {
                        Spacer(Modifier.size(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (expanded) "收起" else "展开",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = arrowRotation }
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(QingJiGreenLight)
                        .clickable { onClick() },
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
            Spacer(Modifier.height(4.dp))
            Text(status, fontSize = TextSizes.Caption, color = TextSecondary)
            if (summary != null) {
                Spacer(Modifier.height(2.dp))
                Text(summary, fontSize = TextSizes.Caption, color = TextSecondary)
            }
            if (expanded && hasContent) {
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
    /**
     * P1：心情日记复用同一套月历时传这个，格子按当天心情档位着色；
     * 不传就是训练日记原来的「练过的日子打绿点」。
     */
    dateColors: Map<LocalDate, Color>? = null,
    /**
     * 训练日记传当前选中日期：格子显示主题色胶囊 + 高对比文字 + 轻微缩放动画。
     * 默认 null = 不显示选中态——心情日记 / 自律历史页沿用原来的颜色表达，零影响。
     * 工单要求「不改变布局结构」：只在既有 36dp 格子上加背景/缩放，不动行列排布。
     */
    selectedDate: LocalDate? = null,
    /**
     * 右侧要给悬浮快捷按钮让出的宽度。
     * 训练日记 / 心情日记右侧有 4 颗 50dp 悬浮按钮，不留白的话
     * 周日那一列和「下个月」会被压住（360dp 小屏上尤其明显）。
     * 自律历史页没有悬浮按钮，传 0.dp 即可。
     */
    // v10：默认 0——快捷按钮已默认收起，月历不再让出右侧空隙（主人要求补齐）
    fabSafeEnd: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = fabSafeEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
        Row(modifier = Modifier.fillMaxWidth().padding(end = fabSafeEnd)) {
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
        Column(
            modifier = Modifier.padding(end = fabSafeEnd),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (row in 0 until totalCells / 7) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val index = row * 7 + col
                        val dayNumber = index - leading + 1
                        val date = if (dayNumber in 1..days) month.atDay(dayNumber) else null
                        CalendarDayCell(
                            date = date,
                            dayNumber = dayNumber,
                            trainingDates = trainingDates,
                            dateColors = dateColors,
                            selectedDate = selectedDate,
                            onSelectDate = onSelectDate,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 月历单个日期格子（从 TrainingCalendarCard 抽出，便于每格独立持有动画状态）。
 *
 * 选中态只在「训练日记模式」(dateColors == null) 且传入 selectedDate 时启用：
 * animateColorAsState 背景浅绿胶囊 + 文字转主题深绿，animateFloatAsState 轻微放大，
 * 180ms / FastOutSlowInEasing，与内容切换节奏一致。训练标记绿点独立绘制，
 * 选中态不覆盖它。心情/自律历史传 dateColors，保持原颜色语义、无选中动画。
 */
@Composable
private fun CalendarDayCell(
    date: LocalDate?,
    dayNumber: Int,
    trainingDates: Set<LocalDate>,
    dateColors: Map<LocalDate, Color>?,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val isTrainingMode = dateColors == null
    val isSelected = isTrainingMode && selectedDate != null && date == selectedDate
    val moodColor = if (dateColors != null && date != null) dateColors[date] else null

    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> QingJiGreenLight
            moodColor != null -> moodColor
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "dayCellBg"
    )
    val cellScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "dayCellScale"
    )
    val numberColor by animateColorAsState(
        targetValue = when {
            isSelected -> QingJiGreenDark
            isTrainingMode && date != null && date in trainingDates -> QingJiGreen
            else -> TextPrimary
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "dayCellText"
    )

    Box(
        modifier = modifier
            .height(36.dp)
            .scale(cellScale)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(enabled = date != null) { if (date != null) onSelectDate(date) },
        contentAlignment = Alignment.Center
    ) {
        if (date != null) {
            if (!isTrainingMode) {
                // 心情 / 自律历史：颜色已铺在格子上，只留日期数字（原有行为不变）
                Text(text = dayNumber.toString(), fontSize = TextSizes.Tiny, color = TextPrimary)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = dayNumber.toString(), fontSize = TextSizes.Tiny, color = numberColor)
                    // 有训练的日期保留绿点；选中态下照常显示，不被背景覆盖
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

/** 一个动作的一组数据：重量 / 次数 / RPE / 删除，附「+ 添加一组」。 */
@Composable
fun SetEditorCard(
    name: String,
    /** QJ UI 统一：动作 UUID，用于加载与计划/日记同款缩略图 */
    exerciseUuid: String = "",
    sets: List<WorkoutSet>,
    unit: String,
    fromLast: Boolean,
    /** v1.4 健康体系：每一组已记部分的估算热量（做功+组间休息），空列表 = 不显示这一行 */
    setKcal: List<Double> = emptyList(),
    onUpdate: (Int, WorkoutSet) -> Unit,
    onAdd: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onRemoveExercise: () -> Unit,
    /** v1.4 健康体系：勾选 / 取消「完成此组」，勾选后自动开始组间休息 */
    onToggleComplete: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    SectionCard(
        modifier = modifier,
        contentPadding = Dimens.CardPaddingCompact
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // QJ UI 统一：与计划/日记同款动作缩略图
            com.qingji.app.ui.components.TrainingExerciseThumbnail(
                exerciseUuid = exerciseUuid, fallbackName = name
            )
            Spacer(Modifier.width(10.dp))
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
            Text("完成", fontSize = TextSizes.Tiny, color = TextSecondary, modifier = Modifier.width(32.dp))
            Spacer(Modifier.width(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        sets.forEachIndexed { index, set ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${index + 1}",
                    fontSize = TextSizes.Body,
                    color = if (set.completed) QingJiGreen else TextSecondary,
                    fontWeight = if (set.completed) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.width(24.dp)
                )
                NumberField(
                    value = if (unit == "lb") TrainingMath.kgToLb(set.weightKg) else set.weightKg,
                    isDecimal = true,
                    // QJ 训练联动：计划目标重量作为占位提示（实际值仍独立存 weightKg）
                    placeholder = (set.targetWeightKg?.takeIf { it > 0.0 }?.let {
                        if (unit == "lb") "%.0f".format(TrainingMath.kgToLb(it)) else "${it.toInt()}"
                    } ?: ""),
                    onValue = { raw ->
                        val kg = if (unit == "lb") TrainingMath.lbToKg(raw) else raw
                        onUpdate(index, set.copy(weightKg = kg))
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
                )
                NumberField(
                    value = set.reps.toDouble(),
                    isDecimal = false,
                    // QJ 训练联动：计划目标次数作为占位提示
                    placeholder = (set.targetReps?.takeIf { it > 0 }?.toString() ?: ""),
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
                // v1.4 健康体系：完成勾选。点了变绿，同时自动开始组间休息倒计时。
                Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (set.completed) "✓" else "○",
                        fontSize = 18.sp,
                        color = if (set.completed) QingJiGreen else TextSecondary,
                        modifier = Modifier.clickable { onToggleComplete(index) }
                    )
                }
                Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                    Text("×", fontSize = 16.sp, color = TextSecondary,
                        modifier = Modifier.clickable { onRemoveSet(index) })
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        PrimaryButton(text = "添加一组", icon = Icons.Filled.Add, onClick = onAdd)
        // 每组估算热量：只列已记的组（次数或重量至少填一个），数字随
        // 次数/RPE/休息时长自然变化，不是固定值；整体是估算，仅供参考。
        if (setKcal.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "各组热量（估算）：" + setKcal.withIndex().joinToString(" · ") { (i, kcal) ->
                    "第${i + 1}组 ≈ ${com.qingji.app.domain.SessionCalorieCalculator.displayKcal(kcal)} 千卡"
                },
                fontSize = TextSizes.Tiny,
                color = TextSecondary
            )
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

/**
 * 进行中训练的计时条。存的是开始时间戳，暂停时长在草稿里累计。
 * 2026-11-18：时长可以手动指定（点右边的「改时长」），补记模式下这条
 * 变成「日期 + 指定时长」两行，计时器不跑。
 */
@Composable
fun WorkoutTimerBar(
    elapsedSeconds: Long,
    paused: Boolean,
    editing: Boolean,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
    /** 手动指定的时长（分钟）。0 = 没指定，按计时走。 */
    durationOverrideMin: Int = 0,
    /** 点「改时长」弹滚轮。 */
    onDurationClick: () -> Unit = {},
    /** 点「恢复计时」清掉手动指定的时长。 */
    onResetDuration: () -> Unit = {},
    /** 非空 = 补记模式，显示补记日期，不显示计时。 */
    backfillDate: String = "",
    /** 补记模式下点日期行弹日期滚轮。 */
    onDateClick: () -> Unit = {},
    /** 编辑旧记录时的原始时长（分钟），大数字直接显示它而不是 misleading 的计时。 */
    editingDurationMin: Int = 0
) {
    SectionCard(modifier = modifier, contentPadding = Dimens.CardPaddingCompact) {
        if (backfillDate.isNotBlank()) {
            // 补记：日期行 + 时长行，没有计时器
            val dateText = runCatching {
                val d = java.time.LocalDate.parse(backfillDate)
                "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
            }.getOrDefault(backfillDate)
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onDateClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("补记日期", fontSize = TextSizes.Caption, color = TextSecondary)
                    Text(dateText, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Text("改日期", fontSize = TextSizes.Body, color = QingJiGreen)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onDurationClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("训练时长", fontSize = TextSizes.Caption, color = TextSecondary)
                    Text("${durationOverrideMin.coerceAtLeast(1)} 分钟",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Text("改时长", fontSize = TextSizes.Body, color = QingJiGreen)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f).clickable { onDurationClick() },
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(if (editing) "修改这次训练 · 时长" else "本次训练时长",
                        fontSize = TextSizes.Caption, color = TextSecondary)
                    // 大数字只放纯时长，不再缀「点这里可改」小尾巴；
                    // 入口是右边的绿字「改时长」，整个大数字区域照样能点。
                    Text(
                        text = when {
                            durationOverrideMin > 0 -> "${durationOverrideMin} 分钟"
                            editing && editingDurationMin > 0 -> "${editingDurationMin} 分钟"
                            else -> TrainingMath.formatDuration(elapsedSeconds)
                        },
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                    )
                }
                Text(
                    text = if (durationOverrideMin > 0) "恢复计时" else "改时长",
                    fontSize = TextSizes.Body,
                    color = QingJiGreen,
                    modifier = Modifier.clickable {
                        if (durationOverrideMin > 0) onResetDuration() else onDurationClick()
                    }
                )
                if (!editing) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onTogglePause) {
                        Text(if (paused) "继续" else "暂停", fontSize = TextSizes.Body)
                    }
                }
            }
        }
    }
}

/**
 * v1.4 健康体系：组间休息倒计时条。
 * 剩余时间由「结束时间戳 - now」算出来，页面怎么重组都不会丢；
 * 走完后条子变成浅绿提示「休息结束」，点「知道了」关掉，
 * 不弹窗不抢焦点，不阻塞用户记组。
 */
@Composable
fun RestTimerBar(
    totalSeconds: Long,
    remainingSeconds: Long,
    paused: Boolean,
    finished: Boolean,
    onPauseResume: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier, contentPadding = Dimens.CardPaddingCompact) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (finished) "休息结束，开练！" else "组间休息",
                    fontSize = TextSizes.Caption,
                    color = if (finished) QingJiGreen else TextSecondary
                )
                Text(
                    text = if (finished) "搞定" else TrainingMath.formatDuration(remainingSeconds),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (finished) QingJiGreen else TextPrimary
                )
            }
            if (!finished) {
                OutlinedButton(onClick = onPauseResume) {
                    Text(if (paused) "继续" else "暂停", fontSize = TextSizes.Body)
                }
                Spacer(Modifier.width(8.dp))
            }
            OutlinedButton(onClick = onSkip) {
                Text(if (finished) "知道了" else "跳过", fontSize = TextSizes.Body)
            }
        }
        // 进度条：按总时长画剩余比例，总时长为 0 时不画（防除零）
        if (totalSeconds > 0 && !finished) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DividerColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(remainingSeconds.toFloat() / totalSeconds.toFloat())
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(QingJiGreen)
                )
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
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 64dp 统一圆角缩略图：本地 SVG 首帧，走 coil 内存/磁盘缓存不重复下载；
                        // 自建动作 / 加载失败 / 无图时用动作名首字占位，行高不变（T01/T03/T05）
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(Dimens.ItemCorner))
                                .background(QingJiGreenLight),
                            contentAlignment = Alignment.Center
                        ) {
                            ExerciseMediaImage(
                                media = ExerciseMediaMapping.forUuid(e.uuid),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Text(
                                    e.name.take(1),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = QingJiGreenDark
                                )
                            }
                        }
                        Spacer(Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(e.name, fontSize = TextSizes.Body, color = TextPrimary)
                            Text(
                                listOf(e.primaryMuscles, e.equipment).filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                fontSize = TextSizes.Tiny, color = TextSecondary
                            )
                        }
                        if (e.isFavorite) Text("★", fontSize = 14.sp, color = QingJiGreen)
                        Spacer(Modifier.size(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = QingJiGreen,
                            modifier = Modifier.size(18.dp)
                        )
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
                // v1.4.2（工单四）：主要操作改一级按钮——主题色实心 + 白色文字/加号，
                // 文案更直白，用户一眼知道这是可点的主入口（T09）
                PrimaryButton(
                    text = "添加自定义动作",
                    icon = Icons.Filled.Add,
                    onClick = { showCustom = true }
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
