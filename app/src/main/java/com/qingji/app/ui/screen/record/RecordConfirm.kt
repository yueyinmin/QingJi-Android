package com.qingji.app.ui.screen.record

import com.qingji.app.domain.AppClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.remote.AiRecognitionResult
import com.qingji.app.domain.PortionRules
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import java.time.LocalDate
import kotlin.math.round
import com.qingji.app.ui.i18n.s

/**
 * ============================================================
 * 识别结果确认区（PRD 9.3 第 11 页：文字 / 图片 / 语音共用）
 * ------------------------------------------------------------
 * 需求 1.4 的头号坑是「AI 估不准」，所以三种识别方式拿到的结果
 * 都必须经过同一套「可编辑列表」才能入库：
 *   - 能改名、改克数、改千卡、删条目、改餐次
 *   - 改克数按比例自动重算热量和三大营养素
 *   - 置信度 < 0.6 的条目标红提醒核对
 * 共用一份代码 = 修一个 bug 三条路一起好，也不会出现「图片那条
 * 忘了让人确认」这种漏网之鱼。
 * ============================================================
 */

/** 界面里一行可编辑的食物条目。数字用字符串存，方便直接绑定输入框。 */
data class EditableItem(
    val name: String,
    val grams: String,
    val kcal: String,
    val proteinG: Double,
    val carbG: Double,
    val fatG: Double,
    val confidence: Double,
    val amountText: String = "",
    /** v3.4.0：AI 最初给的名字，用来和最终保存的名字比对，记下用户的纠错。 */
    val aiName: String = name,
    // 下面这组是「AI 最初给的那一份」，快捷倍数永远以它为基准，
    // 避免连点两次 2x 变成 4x。默认值取构造时传进来的当前值。
    val baseGrams: String = grams,
    val baseKcal: String = kcal,
    val baseProteinG: Double = proteinG,
    val baseCarbG: Double = carbG,
    val baseFatG: Double = fatG
)

fun oneDecimal(value: Double): String = (round(value * 10) / 10.0).toString()

/** AI 返回 → 可编辑列表。 */
fun AiRecognitionResult.toEditableItems(): List<EditableItem> = items.map {
    EditableItem(
        name = it.name,
        grams = oneDecimal(it.grams),
        kcal = oneDecimal(it.kcal),
        proteinG = it.proteinG,
        carbG = it.carbG,
        fatG = it.fatG,
        confidence = it.confidence,
        amountText = it.amountText,
        aiName = it.name
    )
}

/**
 * v3.4.0：挑出用户真的改过名字的条目，交给 ViewModel 记成纠错映射。
 * 只改了克数 / 营养值的不会进来；空名字也不会被记（保存时本来就会被跳过）。
 */
fun List<EditableItem>.nameCorrections(): List<Pair<String, String>> = mapNotNull {
    val from = it.aiName.trim()
    val to = it.name.trim()
    if (from.isEmpty() || to.isEmpty() || from == to) null else from to to
}

/** 可编辑列表 → 能写进 food_log 的记录；填得不合法的条目直接跳过。 */
fun List<EditableItem>.toFoodLogs(
    mealType: String,
    source: String,
    date: LocalDate = AppClock.today(),
    imagePath: String? = null
): List<FoodLog> = mapNotNull {
    val g = it.grams.toDoubleOrNull() ?: return@mapNotNull null
    val k = it.kcal.toDoubleOrNull() ?: return@mapNotNull null
    if (g <= 0 || it.name.isBlank()) return@mapNotNull null
    FoodLog(
        date = date,
        mealType = mealType,
        foodName = it.name.trim(),
        grams = g,
        kcal = k,
        proteinG = it.proteinG,
        carbG = it.carbG,
        fatG = it.fatG,
        source = source,
        aiConfidence = it.confidence,
        imagePath = imagePath
    )
}

/** 改克数时同步缩放营养值，返回新条目。 */
fun EditableItem.withGrams(newGrams: String): EditableItem {
    val old = grams.toDoubleOrNull() ?: 0.0
    val new = newGrams.toDoubleOrNull() ?: 0.0
    val ratio = if (old > 0 && new > 0) new / old else 1.0
    return copy(
        grams = newGrams,
        kcal = oneDecimal((kcal.toDoubleOrNull() ?: 0.0) * ratio),
        proteinG = proteinG * ratio,
        carbG = carbG * ratio,
        fatG = fatG * ratio
    )
}

/** 快捷倍数：以 AI 最初那一份为基准，整体缩放克数和营养值。 */
fun EditableItem.withMultiplier(multiplier: Double): EditableItem {
    val base = baseGrams.toDoubleOrNull() ?: 0.0
    val baseKcalValue = baseKcal.toDoubleOrNull() ?: 0.0
    if (base <= 0) return this
    return copy(
        grams = oneDecimal(PortionRules.scaled(base, multiplier)),
        kcal = oneDecimal(PortionRules.scaled(baseKcalValue, multiplier)),
        proteinG = PortionRules.scaled(baseProteinG, multiplier),
        carbG = PortionRules.scaled(baseCarbG, multiplier),
        fatG = PortionRules.scaled(baseFatG, multiplier)
    )
}

/** 结果列表：标题 + 合计 + 每一条可编辑行。 */
@Composable
fun EditableFoodList(
    items: SnapshotStateList<EditableItem>,
    onUpdate: (Int, EditableItem) -> Unit,
    onDelete: (Int) -> Unit,
    onScale: (Int, Double) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "识别结果（${items.size} 条，请核对）",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "共 ${items.sumOf { it.kcal.toDoubleOrNull() ?: 0.0 }.toInt()} 千卡",
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
    Spacer(Modifier.height(8.dp))

    items.forEachIndexed { index, item ->
        EditableFoodRow(
            item = item,
            onNameChange = { onUpdate(index, item.copy(name = it)) },
            onGramsChange = { onUpdate(index, item.withGrams(it)) },
            onKcalChange = { onUpdate(index, item.copy(kcal = it)) },
            onScale = { onScale(index, it) },
            onDelete = { onDelete(index) }
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** 底部保存区。 */
@Composable
fun ConfirmSaveBar(
    count: Int,
    onSave: () -> Unit,
    hint: String = "保存后自动回到首页，数据只存本机"
) {
    PrimaryButton(
        text = "全部保存（$count 条）",
        enabled = count > 0,
        onClick = onSave
    )
    Spacer(Modifier.height(8.dp))
    Text(text = hint, fontSize = 11.sp, color = TextSecondary)
}

/** 一条可编辑结果：名称 + 克数 + 千卡 + 删除。 */
@Composable
private fun EditableFoodRow(
    item: EditableItem,
    onNameChange: (String) -> Unit,
    onGramsChange: (String) -> Unit,
    onKcalChange: (String) -> Unit,
    onScale: (Double) -> Unit,
    onDelete: () -> Unit
) {
    SectionCard(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallField(
                value = item.name,
                onValueChange = onNameChange,
                label = if (item.amountText.isBlank()) "名称" else "名称（AI 估：${item.amountText}）",
                modifier = Modifier.weight(1f),
                isNumber = false
            )
            Spacer(Modifier.padding(6.dp))
            Text(
                text = "×",
                fontSize = 20.sp,
                color = TextSecondary,
                modifier = Modifier
                    .background(Color.Transparent)
                    .clickable { onDelete() }
                    .padding(horizontal = 6.dp)
            )
        }

        if (item.confidence < 0.6) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "AI 把握不大（置信度 ${(item.confidence * 100).toInt()}%），请重点核对",
                fontSize = 11.sp,
                color = DangerRed
            )
        }

        // 需求 M3.1：一键半份 / 一份 / 一份半 / 双份
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PortionRules.MULTIPLIERS.forEach { multiplier ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(QingJiGreenLight)
                        .clickable { onScale(multiplier) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = PortionRules.label(multiplier),
                        fontSize = 12.sp,
                        color = QingJiGreenDark
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallField(
                value = item.grams,
                onValueChange = onGramsChange,
                label = "克数 g",
                modifier = Modifier.weight(1f),
                isNumber = true
            )
            SmallField(
                value = item.kcal,
                onValueChange = onKcalChange,
                label = "千卡",
                modifier = Modifier.weight(1f),
                isNumber = true
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = s("蛋白"), fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${oneDecimal(item.proteinG)} g",
                    fontSize = 14.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun SmallField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isNumber: Boolean
) {
    Column(modifier = modifier) {
        Text(text = label, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isNumber) KeyboardType.Decimal else KeyboardType.Text
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CardWhite,
                unfocusedContainerColor = CardWhite,
                focusedIndicatorColor = QingJiGreen,
                unfocusedIndicatorColor = DividerColor,
                cursorColor = QingJiGreen
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 「记到哪一天」一行（v3.5.0）：默认今天，点右侧文字弹出日期/时/分三列滚轮。
 * 文字 / 拍照 / 语音三条识别链路共用；弹层本身直接复用运动模块的
 * ExerciseDateTimeWheelSheet（最多回看最近 7 天）。
 */
@Composable
fun RecordDateRow(date: LocalDate, onClick: () -> Unit) {
    val today = AppClock.today()
    val label = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        today.minusDays(2) -> "前天"
        else -> "${date.monthValue} 月 ${date.dayOfMonth} 日"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = s("记到哪一天"), fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.weight(1f))
        Text(
            text = "$label ›",
            fontSize = 13.sp,
            color = QingJiGreenDark,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
