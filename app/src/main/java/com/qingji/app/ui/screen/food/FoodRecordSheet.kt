package com.qingji.app.ui.screen.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.FoodNutritionCalculator
import com.qingji.app.domain.MealRules
import com.qingji.app.domain.input.NumericEmptyPolicy
import com.qingji.app.domain.input.NumericInputState
import com.qingji.app.ui.components.NumericKeypadInput
import com.qingji.app.ui.components.RecordDateDialog
import com.qingji.app.ui.components.RecordDateTimeRow
import com.qingji.app.ui.components.RecordTimeDialog
import com.qingji.app.ui.components.SheetActionBar
import com.qingji.app.ui.components.SheetScaffoldContent
import com.qingji.app.ui.components.SheetSectionLabel
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.pressable
import java.time.LocalDate
import java.time.LocalTime

/**
 * ============================================================
 * 食物记录草稿（QJ-RECORD-INPUT-UNIFIED-V3 / B4）
 * ------------------------------------------------------------
 * 为什么要有「草稿」这个对象：
 * 1. 食物弹层里改重量/餐次/日期/时间都只改草稿，点确认才落库（工单 6.1）；
 * 2. 从弹层点「记录饮食详情」会离开当前页面，返回时必须原样恢复——
 *    所以草稿要能进 rememberSaveable，字段必须全是可保存的基本类型。
 *
 * 字段设计刻意「自带营养参数」：nutrition 四列 + 基准 + 份量都在草稿里，
 * 这样提交时不需要再拿着 Food 实体，返回栈被回收后也能照常提交。
 * ============================================================
 */
data class FoodRecordDraft(
    val foodName: String = "",
    val basis: String = FoodNutritionCalculator.PER_100G,
    /** 营养基准对应的份量（每份基准时用；每 100g 基准时为 100） */
    val servingGrams: Double = 100.0,
    val defaultGrams: Double = 100.0,
    val kcalPerBasis: Double = 0.0,
    val proteinPerBasis: Double = 0.0,
    val carbPerBasis: Double = 0.0,
    val fatPerBasis: Double = 0.0,
    val meal: String = MealRules.LUNCH,
    /** 重量输入框的原始文本（允许为空/中间态，最终由校验决定能否提交） */
    val gramsText: String = "100",
    /** 进入编辑前的有效重量：清空后取消/失焦恢复它 */
    val originalGrams: Double = 100.0,
    val isEditing: Boolean = false,
    /** 记录日期用 epochDay 存：比 ISO 字符串更省事，也不会被时区改写 */
    val epochDay: Long = AppClock.today().toEpochDay(),
    val hour: Int = AppClock.now().hour,
    val minute: Int = AppClock.now().minute,
    /** 本次记录是否已经提交过（防重复插入 FoodLog） */
    val submitted: Boolean = false
) {

    val grams: Double? get() = gramsText.toDoubleOrNull()

    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)

    val time: LocalTime get() = LocalTime.of(hour, minute)

    /** 重量必须能解析且落在 1..3000g（沿用原有业务范围）。 */
    val gramsValid: Boolean get() = grams?.let { it >= 1.0 && it <= 3000.0 } ?: false

    val canSubmit: Boolean get() = gramsValid && !submitted

    /** 实时营养预览：与落库使用同一个折算函数，口径不会出现两套。 */
    fun previewKcal(): Double {
        val g = grams ?: return 0.0
        return FoodNutritionCalculator.compute(
            kcalPerBasis = kcalPerBasis,
            proteinPerBasis = proteinPerBasis,
            carbPerBasis = carbPerBasis,
            fatPerBasis = fatPerBasis,
            basis = basis,
            servingGrams = servingGrams,
            grams = g
        ).kcal
    }

    companion object {

        /** 从食物/菜品打开一份新草稿：重量取默认份量，餐次按当前钟点猜。 */
        fun create(
            foodName: String,
            basis: String,
            defaultGrams: Double,
            servingGrams: Double,
            kcalPerBasis: Double,
            proteinPerBasis: Double,
            carbPerBasis: Double,
            fatPerBasis: Double,
            meal: String = MealRules.guess()
        ): FoodRecordDraft {
            val normalizedBasis = FoodNutritionCalculator.normalizeBasis(basis)
            val grams = defaultGrams.takeIf { it in 1.0..3000.0 } ?: 100.0
            return FoodRecordDraft(
                foodName = foodName,
                basis = normalizedBasis,
                servingGrams = servingGrams.takeIf { it > 0.0 } ?: 100.0,
                defaultGrams = grams,
                kcalPerBasis = kcalPerBasis,
                proteinPerBasis = proteinPerBasis,
                carbPerBasis = carbPerBasis,
                fatPerBasis = fatPerBasis,
                meal = meal,
                gramsText = NumericInputState.formatValue(grams, 0),
                originalGrams = grams
            )
        }

        /**
         * rememberSaveable 用的 Saver。
         * 只用基本类型，进程被回收后也能恢复（工单 10.2 的第 5 条）。
         */
        val Saver = listSaver<FoodRecordDraft, Any?>(
            save = { d ->
                listOf(
                    d.foodName, d.basis, d.servingGrams, d.defaultGrams,
                    d.kcalPerBasis, d.proteinPerBasis, d.carbPerBasis, d.fatPerBasis,
                    d.meal, d.gramsText, d.originalGrams, d.isEditing,
                    d.epochDay, d.hour, d.minute, d.submitted
                )
            },
            restore = { v ->
                FoodRecordDraft(
                    foodName = v[0] as String,
                    basis = v[1] as String,
                    servingGrams = v[2] as Double,
                    defaultGrams = v[3] as Double,
                    kcalPerBasis = v[4] as Double,
                    proteinPerBasis = v[5] as Double,
                    carbPerBasis = v[6] as Double,
                    fatPerBasis = v[7] as Double,
                    meal = v[8] as String,
                    gramsText = v[9] as String,
                    originalGrams = v[10] as Double,
                    isEditing = v[11] as Boolean,
                    epochDay = v[12] as Long,
                    hour = v[13] as Int,
                    minute = v[14] as Int,
                    submitted = v[15] as Boolean
                )
            }
        )
    }
}

/**
 * ============================================================
 * 统一食物记录弹层（QJ-RECORD-INPUT-UNIFIED-V3 / B4）
 * ------------------------------------------------------------
 * 这个组件取代了此前两套各自维护的实现：
 * · FoodLibraryScreen 里的 AddToMealDialog（只能选今天/昨天/前天）
 * · FoodLibrarySearchScreen 里的 SearchAddToMealSheet（只有今天/昨天）
 * 现在标准食物库、自定义食物、吃过的、搜索页、品牌菜品全部走同一份，
 * 交互/布局/校验/日期时间规则只有一处可改。
 *
 * 与饮水弹层保持一致的部分（工单 6.2 / 7.4）：
 * · 三段式布局：标题固定、中间可滚、底部操作区固定；
 * · 日期 + 时间两枚标签 + 同一套滚轮对话框；
 * · 「取消 / 确认」并排 + 第三入口整行描边的操作条。
 *
 * 保持独立的业务：重量范围 1–3000g、营养折算、餐次规则一个都没动。
 * ============================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodRecordSheet(
    draft: FoodRecordDraft,
    onDraftChange: (FoodRecordDraft) -> Unit,
    onCancel: () -> Unit,
    onConfirm: (FoodRecordDraft) -> Unit,
    onOpenDetail: ((FoodRecordDraft) -> Unit)? = null
) {
    var showDateWheel by remember { mutableStateOf(false) }
    var showTimeWheel by remember { mutableStateOf(false) }

    // 由草稿派生输入状态：草稿是唯一真相，导航返回/重组都不会丢编辑中的文本
    val gramsState = NumericInputState.of(
        value = draft.originalGrams,
        min = 1.0,
        max = 3000.0,
        decimals = 0,
        emptyPolicy = NumericEmptyPolicy.RESTORE_ORIGINAL,
        emptyFallback = draft.originalGrams
    ).copy(text = draft.gramsText, isEditing = draft.isEditing)

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CardWhite,
        contentColor = TextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.28f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DividerColor)
            )
        }
    ) {
        SheetScaffoldContent(
            header = {
                Text(
                    text = s("加入饮食记录"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = draft.foodName,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(14.dp))
            },
            content = {
                // ---------- 餐次 ----------
                SheetSectionLabel(s("哪一餐"))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealRules.ALL.forEach { type ->
                        val active = type == draft.meal
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) QingJiGreen else PageBackground)
                                .clickable { onDraftChange(draft.copy(meal = type)) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = MealRules.label(type),
                                fontSize = 12.sp,
                                color = if (active) Color.White else TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ---------- 日期 / 时间（复用饮水同一套入口） ----------
                SheetSectionLabel(s("记录时间"))
                Spacer(Modifier.height(6.dp))
                RecordDateTimeRow(
                    date = draft.date,
                    time = draft.time,
                    onPickDate = { showDateWheel = true },
                    onPickTime = { showTimeWheel = true }
                )

                Spacer(Modifier.height(14.dp))

                // ---------- 重量 ----------
                SheetSectionLabel(
                    "吃了多少（${FoodNutritionCalculator.basisLabel(draft.basis)}）"
                )
                Spacer(Modifier.height(6.dp))
                NumericKeypadInput(
                    state = gramsState,
                    onStateChange = {
                        onDraftChange(
                            draft.copy(gramsText = it.text, isEditing = it.isEditing)
                        )
                    },
                    unit = if (draft.basis == FoodNutritionCalculator.PER_100ML) "毫升" else "克",
                    min = 1.0,
                    max = 3000.0,
                    step = 10.0
                )
                if (!draft.gramsValid) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = s("请输入 1–3000 之间的重量"),
                        fontSize = 11.sp,
                        color = DangerRed
                    )
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "约 ${draft.previewKcal().toInt()} 千卡",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            },
            footer = {
                SheetActionBar(
                    confirmText = s("确认加入"),
                    onConfirm = {
                        // 同步守卫：连点也只提交一次（不依赖按钮置灰的视觉反馈）
                        if (draft.submitted || !draft.gramsValid) return@SheetActionBar
                        onConfirm(draft.copy(submitted = true))
                    },
                    confirmEnabled = draft.canSubmit,
                    cancelText = s("取消"),
                    // 取消：不写库、丢弃草稿（由调用方清空）
                    onCancel = onCancel,
                    tertiaryText = if (onOpenDetail != null) s("记录饮食详情") else null,
                    onTertiary = onOpenDetail?.let { open ->
                        {
                            // 只是去看详情：不保存、不清草稿，返回时弹层自动恢复
                            if (!draft.submitted) open(draft)
                        }
                    }
                )
            }
        )
    }

    if (showDateWheel) {
        RecordDateDialog(
            initialDate = draft.date,
            onDismiss = { showDateWheel = false },
            onConfirm = { picked ->
                // 只改草稿，不写库
                onDraftChange(draft.copy(epochDay = picked.toEpochDay()))
                showDateWheel = false
            }
        )
    }

    if (showTimeWheel) {
        RecordTimeDialog(
            initialTime = draft.time,
            onDismiss = { showTimeWheel = false },
            onConfirm = { picked ->
                onDraftChange(draft.copy(hour = picked.hour, minute = picked.minute))
                showTimeWheel = false
            }
        )
    }
}
