package com.qingji.app.ui.screen.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.WeightUnit
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.LabeledTextField
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.WeightViewModel
import kotlinx.coroutines.launch

/**
 * ============================================================
 * 记体重
 * ------------------------------------------------------------
 * 需求文档要求：单日体重波动超过 5kg 要二次确认。
 * 原因：多数是手滑把 62 写成 26、把斤当公斤填，直接入库会
 * 把体重曲线画歪，还不好改。这里弹一次确认，让人有机会反悔。
 *
 * M7 补充：设置里的单位是「斤」时，输入框填的就是斤，
 * 存进数据库前统一除以 2 换成公斤 —— 数据库永远只存公斤。
 * ============================================================
 */
@Composable
fun WeightScreen(
    onBack: () -> Unit,
    vm: WeightViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val settings by remember { SettingsStore.observe(context) }
        .collectAsState(initial = QingJiSettings())
    val unit = settings.unit
    val unitName = WeightUnit.symbol(unit)

    var weightText by remember { mutableStateOf("") }
    var fatText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var lastWeightKg by remember { mutableStateOf<Double?>(null) }
    var errorText by remember { mutableStateOf("") }
    var confirmAsked by remember { mutableStateOf(false) }

    // 进页面时读一次「上一次记的体重」，用来做 ±5kg 判断
    LaunchedEffect(Unit) {
        lastWeightKg = vm.latest()?.weightKg
    }

    // 用户填的数字 → 数据库要存的公斤。返回 null 表示输入不合法。
    fun parsedKg(): Double? = WeightUnit.parseToKg(weightText, unit)

    fun doSave() {
        val kg = parsedKg() ?: return
        val fat = fatText.toDoubleOrNull()
        scope.launch {
            vm.save(kg, fat, note, AppClock.today())
            onBack()
        }
    }

    fun onSaveClick() {
        errorText = ""
        val kg = parsedKg()
        when {
            weightText.isBlank() -> errorText = "请填一个数字，例如 68.5"
            kg == null -> errorText =
                "体重看起来不太对（合理范围 20~350 kg，或 40~700 斤），检查一下单位"
            fatText.isNotBlank() && (fatText.toDoubleOrNull() == null || fatText.toDouble() !in 3.0..70.0) ->
                errorText = "体脂率请填 3~70 之间的数字，不测就留空"
            lastWeightKg != null && kotlin.math.abs(kg - lastWeightKg!!) > 5.0 -> confirmAsked = true
            else -> doSave()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(title = "记体重", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            SectionCard {
                Text(text = AppClock.today().toString(), fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                LabeledTextField(
                    label = "体重（$unitName）",
                    value = weightText,
                    onValueChange = { weightText = it },
                    hint = lastWeightKg?.let {
                        "上次 ${WeightUnit.format(it, unit)} $unitName"
                    } ?: if (unit == WeightUnit.JIN) "例如 137.0" else "例如 68.5",
                    decimalOnly = true
                )
                Spacer(Modifier.height(12.dp))
                LabeledTextField(
                    label = "体脂率（%，可以不填）",
                    value = fatText,
                    onValueChange = { fatText = it },
                    hint = "例如 22.5",
                    decimalOnly = true
                )
                Spacer(Modifier.height(12.dp))
                LabeledTextField(
                    label = "备注（可以不填）",
                    value = note,
                    onValueChange = { note = it },
                    hint = "例如：晨起空腹"
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton(text = "保存", onClick = { onSaveClick() })
            }

            if (errorText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                HintBox(text = errorText, isError = true)
            }

            Spacer(Modifier.height(12.dp))
            HintBox(
                text = lastWeightKg?.let {
                    "上次记录 ${WeightUnit.formatWithSymbol(it, unit)}。" +
                        "一天只保留一条，再记一次会覆盖当天的数据。"
                } ?: "还没有体重记录，记下第一条后进展页就会出现曲线。"
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // ---------- ±5kg 二次确认 ----------
    if (confirmAsked) {
        com.qingji.app.ui.components.AppDialog(
            title = "确认一下体重",
            confirmText = "确认保存",
            onConfirm = {
                confirmAsked = false
                doSave()
            },
            dismissText = "再改改",
            onDismiss = { confirmAsked = false }
        ) {
            Text(
                text = "上次是 ${lastWeightKg?.let { WeightUnit.formatWithSymbol(it, unit) }}，" +
                    "这次填的是 ${weightText} $unitName，相差超过 5 公斤。\n\n" +
                    "常见原因是把「斤」当成「公斤」，或者手滑多按了一位。确认无误再保存。",
                fontSize = 14.sp,
                color = TextPrimary
            )
        }
    }
}
