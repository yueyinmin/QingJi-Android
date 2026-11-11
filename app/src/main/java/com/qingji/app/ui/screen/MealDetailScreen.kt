package com.qingji.app.ui.screen

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.FoodIcon
import com.qingji.app.domain.MealRules
import com.qingji.app.domain.RefineOptions
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.LabeledTextField
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.components.ThinProgressBar
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.MacroCarb
import com.qingji.app.ui.theme.MacroFat
import com.qingji.app.ui.theme.MacroProtein
import com.qingji.app.ui.theme.pressable
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.MealDetailViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * ============================================================
 * 用餐详情（点首页某一餐卡片进来）
 * ------------------------------------------------------------
 * 一屏说清四件事：
 * 1. 这一餐一共吃了多少热量、蛋白/碳水/脂肪各多少
 * 2. 具体是哪几样食物（带图标），AI 识别的会打「AI 生成」角标
 * 3. 不满意可以「重新识别」——能写补充说明，还能选少油/多糖/清蒸/加酱料/生熟
 * 4. 底部两个入口：长按说话改、打字改；更多操作（改餐次/改日期/写日记）收进右上角菜单
 *
 * 照片不在这里展示：用户要的是「吃了什么」，原图放这儿只会挤占空间。
 * ============================================================
 */
@Composable
fun MealDetailScreen(
    dateIso: String,
    mealType: String,
    onBack: () -> Unit,
    onOpenDiary: (Long) -> Unit,
    vm: MealDetailViewModel = viewModel()
) {
    // 导航参数喂给 ViewModel（AndroidViewModel 是系统创建的，拿不到参数）
    LaunchedEffect(dateIso, mealType) {
        val date = runCatching { LocalDate.parse(dateIso) }.getOrDefault(AppClock.today())
        vm.bind(date, mealType)
    }

    val state by vm.state.collectAsState()
    val profile by vm.profile.collectAsState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showMenu by remember { mutableStateOf(false) }
    var showRefine by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var refineSeed by remember { mutableStateOf("") }

    // 系统语音识别：长按「说话」时调起，把识别到的文字塞进重新识别的输入框
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) {
                refineSeed = text
                showRefine = true
            }
        }
    }

    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说说这一餐实际吃了什么")
        }
        runCatching { voiceLauncher.launch(intent) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(
            title = "${MealRules.label(state.mealType)}详情",
            onBack = onBack,
            actionText = "更多",
            onAction = { showMenu = true }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(Dimens.Gap))

            // ---------- 汇总 ----------
            MealSummaryCard(state, profile)

            Spacer(Modifier.height(Dimens.Gap))

            // ---------- AI 角标 + 重新识别 ----------
            if (state.hasAi) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.ItemCorner))
                        .background(QingJiGreenLight)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI 生成",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(QingJiGreenDark)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "这份数据是 AI 估算的，份量可能偏",
                        fontSize = 12.sp,
                        color = QingJiGreenDark,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "重新识别",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = QingJiGreenDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.TinyCorner))
                            .background(CardWhite)
                            .pressable { refineSeed = ""; showRefine = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.height(Dimens.Gap))
            }

            // ---------- 食物列表 ----------
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "这一餐吃了什么",
                        fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${state.logs.size} 项",
                        fontSize = TextSizes.Caption,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(10.dp))

                if (state.logs.isEmpty()) {
                    Text(
                        text = "这一餐还没有记录。回首页点「+ 记一笔」，或者用上方的重新识别。",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                } else {
                    state.logs.forEachIndexed { index, log ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .height(1.dp)
                                    .background(DividerColor)
                            )
                        }
                        FoodLogRow(log = log, onDelete = { vm.deleteLog(log.id) })
                    }
                }
            }

            Spacer(Modifier.height(Dimens.Gap))

            // ---------- 说明 ----------
            HintBox(
                text = "照片不在这里显示：这一页只关心「吃了什么、多少量」。" +
                    "想改份量就点重新识别，或者直接删掉重记。"
            )

            Spacer(Modifier.height(Dimens.FabSpace))
        }

        // ---------- 底部：长按说话 / 文字修改 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .padding(horizontal = Dimens.ScreenPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(Dimens.ItemCorner))
                    .background(QingJiGreenLight)
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { startVoice() })
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎙 长按说话修改",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = QingJiGreenDark
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(Dimens.ItemCorner))
                    .background(QingJiGreen)
                    .pressable { refineSeed = ""; showRefine = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✏️ 文字修改",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }

    // ---------- 更多菜单 ----------
    if (showMenu) {
        com.qingji.app.ui.components.AppDialog(
            title = "更多操作",
            dismissText = "",
            onDismiss = { showMenu = false }
        ) {
            Column {
                    MenuRow("改餐次和日期") { showMenu = false; showMove = true }
                    MenuRow("写日记") {
                        showMenu = false
                        onOpenDiary(0L)
                    }
                    MenuRow("重新识别这一餐") {
                        showMenu = false
                        refineSeed = ""
                        showRefine = true
                    }
                    MenuRow("把这一餐存进我的食物库") {
                        showMenu = false
                        scope.launch {
                            val n = vm.saveToFoodLibrary()
                            android.widget.Toast.makeText(
                                context,
                                if (n > 0) "已存 $n 条到自定义食物库" else "这一餐没有可存的食物",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
    }

    // ---------- 改餐次 / 改日期 ----------
    if (showMove) {
        MoveMealDialog(
            currentMeal = state.mealType,
            currentDate = state.date,
            onDismiss = { showMove = false },
            onConfirm = { newDate, newMeal ->
                showMove = false
                scope.launch { vm.moveTo(newDate, newMeal) }
            }
        )
    }

    // ---------- 重新识别 ----------
    if (showRefine) {
        RefineDialog(
            initialText = refineSeed,
            onDismiss = { showRefine = false },
            onConfirm = { text, options ->
                showRefine = false
                scope.launch {
                    // 识别成功就把这一餐整体换成新结果；失败一定要说话，
                    // 不能像以前那样「点了没反应」。
                    val result = vm.refine(text, options)
                    result.onSuccess { r ->
                        vm.applyRefined(r.items, state.mealType)
                        android.widget.Toast.makeText(
                            context,
                            "已按新条件重新算过",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { e ->
                        android.widget.Toast.makeText(
                            context,
                            e.message ?: "识别失败，稍后再试",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }
}

/** 顶部汇总：总热量 + 三大营养素（AI 专项后统一为这四项）。 */
@Composable
private fun MealSummaryCard(
    state: com.qingji.app.ui.viewmodel.MealDetailState,
    profile: com.qingji.app.data.local.entity.UserProfile?
) {
    SectionCard {
        Text(
            text = "总热量",
            fontSize = TextSizes.Caption,
            color = TextSecondary
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${state.kcal.toInt()}",
                fontSize = TextSizes.HugeNumber,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = "千卡",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${state.logs.size} 样食物",
                fontSize = TextSizes.Caption,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        // 三大营养素：按「占总热量比例」画条，蛋白/碳水 4 kcal/g，脂肪 9 kcal/g
        val total = (state.protein * 4 + state.carb * 4 + state.fat * 9).takeIf { it > 0 } ?: 1.0
        NutrientRow("蛋白", state.protein, state.protein * 4 / total, MacroProtein)
        Spacer(Modifier.height(8.dp))
        NutrientRow("碳水", state.carb, state.carb * 4 / total, MacroCarb)
        Spacer(Modifier.height(8.dp))
        NutrientRow("脂肪", state.fat, state.fat * 9 / total, MacroFat)

        // AI 专项：核心营养口径统一只保留热量 + 三大营养素。
        // 这一段原来放膳食纤维 / 钠 / 钙，但全项目没有任何写入这三个值的路径
        // （标准库四列 654/654 为 NULL、AI 结果也不带），hasMicronutrient 恒为 false，
        // 于是每次都只是显示一行「暂无数据」。删掉这个死分支，页面更干净。
        // 数据库字段、备份 JSON 键、解析器全部原样保留，旧数据不受影响。
    }
}

@Composable
private fun NutrientRow(name: String, grams: Double, ratio: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.size(width = 32.dp, height = 16.dp)
        )
        ThinProgressBar(
            progress = ratio.toFloat().coerceIn(0f, 1f),
            modifier = Modifier.weight(1f),
            color = color,
            barHeight = 6.dp
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "${grams.toInt()} g",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.size(width = 42.dp, height = 16.dp)
        )
    }
}

/** 一条食物：图标 + 名字 + 克数/蛋白 + 热量 + 删除。 */
@Composable
private fun FoodLogRow(log: FoodLog, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(Dimens.TinyCorner))
                .background(PageBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(text = FoodIcon.emoji(log.foodName), fontSize = 18.sp)
        }
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = log.foodName, fontSize = 14.sp, color = TextPrimary)
                if (log.source == "text" || log.source == "image" || log.source == "voice") {
                    Spacer(Modifier.size(5.dp))
                    Text(
                        text = "AI",
                        fontSize = 9.sp,
                        color = QingJiGreenDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(QingJiGreenLight)
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${log.grams.toInt()} g · 蛋白 ${log.proteinG.toInt()}g · 碳水 ${
                    log.carbG.toInt()
                }g · 脂肪 ${log.fatG.toInt()}g",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        Text(
            text = "${log.kcal.toInt()} 千卡",
            fontSize = 13.sp,
            color = TextPrimary
        )
        Spacer(Modifier.size(8.dp))
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(PageBackground)
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "×", fontSize = 16.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 15.sp,
        color = TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.TinyCorner))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp)
    )
}

/** 改餐次 / 改日期：整餐挪到别的日子或别的时段。 */
@Composable
private fun MoveMealDialog(
    currentMeal: String,
    currentDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, String) -> Unit
) {
    var meal by remember { mutableStateOf(currentMeal) }
    var dateText by remember { mutableStateOf(currentDate.toString()) }
    var error by remember { mutableStateOf("") }

    com.qingji.app.ui.components.AppBottomSheet(
        title = "改餐次和日期",
        confirmText = "确定",
        closeOnConfirm = false,
        onConfirm = {
            val d = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
            if (d == null) {
                error = "日期格式不对，要写成 2026-11-08 这样"
            } else {
                onConfirm(d, meal)
            }
        },
        onDismiss = onDismiss
    ) {
        Column {
                Text(text = "记到哪一餐", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealRules.ALL.forEach { type ->
                        val selected = type == meal
                        Text(
                            text = MealRules.label(type),
                            fontSize = 12.sp,
                            color = if (selected) Color.White else TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Dimens.TinyCorner))
                                .background(if (selected) QingJiGreen else PageBackground)
                                .clickable { meal = type }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                LabeledTextField(
                    label = "记到哪一天（格式 2026-11-08）",
                    value = dateText,
                    onValueChange = { dateText = it },
                    hint = "2026-11-08"
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0L to "今天", 1L to "昨天", 2L to "前天").forEach { (offset, label) ->
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Dimens.PillCorner))
                                .background(PageBackground)
                                .clickable { dateText = AppClock.today().minusDays(offset).toString() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    HintBox(text = error, isError = true)
                }
            }
        }
}

/** 重新识别：写说明 + 选口味做法。 */
@Composable
private fun RefineDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String, RefineOptions) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var sugar by remember { mutableStateOf(RefineOptions.SUGAR_NORMAL) }
    var oil by remember { mutableStateOf(RefineOptions.OIL_NORMAL) }
    var salt by remember { mutableStateOf(RefineOptions.SALT_NORMAL) }
    var cooking by remember { mutableStateOf(RefineOptions.COOKING_STIR_FRY) }
    var sauce by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf(false) }

    com.qingji.app.ui.components.AppBottomSheet(
        title = "重新识别这一餐",
        confirmText = "开始识别",
        onConfirm = {
            onConfirm(
                text,
                com.qingji.app.domain.RefineOptions(
                    note = text,
                    sugar = sugar,
                    oil = oil,
                    salt = salt,
                    cooking = cooking,
                    sauce = sauce,
                    raw = raw
                )
            )
        },
        onDismiss = onDismiss
    ) {
        Column {
                LabeledTextField(
                    label = "补充说明（可以不写）",
                    value = text,
                    onValueChange = { text = it },
                    hint = "例如：米饭只吃了半碗，剩下了"
                )
                Spacer(Modifier.height(12.dp))

                OptionGroup("含油量", RefineOptions.OIL_OPTIONS, oil) { oil = it }
                Spacer(Modifier.height(10.dp))
                OptionGroup("含糖量", RefineOptions.SUGAR_OPTIONS, sugar) { sugar = it }
                Spacer(Modifier.height(10.dp))
                OptionGroup("含盐量", RefineOptions.SALT_OPTIONS, salt) { salt = it }
                Spacer(Modifier.height(12.dp))

                Text(text = "烹饪方式", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RefineOptions.COOKING_OPTIONS.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { item ->
                                val selected = item == cooking
                                Text(
                                    text = item,
                                    fontSize = 12.sp,
                                    color = if (selected) Color.White else TextSecondary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Dimens.TinyCorner))
                                        .background(if (selected) QingJiGreen else PageBackground)
                                        .clickable { cooking = item }
                                        .padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.TinyCorner))
                        .clickable { sauce = !sauce }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = if (sauce) "☑" else "☐", fontSize = 15.sp, color = QingJiGreen)
                    Spacer(Modifier.size(8.dp))
                    Text(text = "加了酱料（酱油/沙拉酱/辣椒酱等）", fontSize = 13.sp, color = TextPrimary)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.TinyCorner))
                        .clickable { raw = !raw }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = if (raw) "☑" else "☐", fontSize = 15.sp, color = QingJiGreen)
                    Spacer(Modifier.size(8.dp))
                    Text(text = "食物是生的（凉拌/刺身/沙拉）", fontSize = 13.sp, color = TextPrimary)
                }
            }
        }
}

/** 一组单选项。 */
@Composable
private fun OptionGroup(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Text(text = title, fontSize = 13.sp, color = TextSecondary)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { item ->
            val active = item == selected
            Text(
                text = item,
                fontSize = 12.sp,
                color = if (active) Color.White else TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.PillCorner))
                    .background(if (active) QingJiGreen else PageBackground)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
