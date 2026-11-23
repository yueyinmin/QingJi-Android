package com.qingji.app.ui.screen

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.R
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.screen.settings.SwitchRow
import com.qingji.app.ui.viewmodel.SettingsViewModel
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.widget.BodyWidgetReceiver
import com.qingji.app.widget.OverviewWidgetReceiver
import com.qingji.app.widget.QuickRecordWidgetReceiver
import com.qingji.app.widget.WeightWidgetReceiver
import com.qingji.app.widget.WidgetSpec
import com.qingji.app.widget.WidgetTarget
import com.qingji.app.widget.WidgetPerf

/**
 * ============================================================
 * 桌面小组件设置（v2.0）
 * ------------------------------------------------------------
 * 从「设置里点一下弹个小框」改成独立页面：
 * 选项一多，弹窗就挤；而且用户最想看的是「长什么样」，
 * 所以这里每个组件都画一个接近真机的预览。
 *
 * 预览是界面里画的示意图，不是真的渲染小组件 ——
 * 真渲染要跑 Glance，成本高且容易和实际不一致。
 * ============================================================
 */
@Composable
fun WidgetSettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    // 拖动时只改本地值，松手才写设置并刷新桌面，避免滑杆每动一格就重绘一次小组件
    var alphaDraft by remember(settings.widgetAlpha) { mutableStateOf(settings.widgetAlpha.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(title = "桌面小组件", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(Dimens.Gap))

            HintBox(
                text = "点「添加到桌面」会弹系统的固定请求（安卓 8 以上支持）。" +
                    "没反应就长按桌面空白处 → 小组件 → 找到「轻记」。"
            )

            Spacer(Modifier.height(Dimens.Gap))

            // ---------- v3.4.0 外观自定义 ----------
            SectionCard {
                Text(
                    text = "外观与展示内容",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "改完会立刻刷新桌面上的小组件；下面的预览也会跟着变。",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "背景不透明度：${settings.widgetAlpha}%",
                    fontSize = 12.sp,
                    color = TextPrimary
                )
                Slider(
                    value = alphaDraft,
                    onValueChange = { alphaDraft = it },
                    onValueChangeFinished = { vm.setWidgetAlpha(alphaDraft.toInt()) },
                    valueRange = WidgetSpec.ALPHA_MIN.toFloat()..WidgetSpec.ALPHA_MAX.toFloat(),
                    steps = WidgetSpec.ALPHA_MAX - WidgetSpec.ALPHA_MIN - 1,  // 每 1% 一格，自由调节
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "数值越小越透，0% 时卡片背景完全透明（文字仍在，浅色壁纸下可能看不清）。",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = "显示饮水进度",
                    subtitle = "今日概览卡左下角的饮水数字",
                    checked = settings.widgetShowWater
                ) { vm.setWidgetShowWater(it) }
                SwitchRow(
                    title = "显示营养素进度条",
                    subtitle = "今日概览卡里的蛋白 / 碳水 / 脂肪三条",
                    checked = settings.widgetShowMacro
                ) { vm.setWidgetShowMacro(it) }
                SwitchRow(
                    title = "显示体重数字",
                    subtitle = "体重闪记卡；关掉后桌面上只显示「已隐藏」",
                    checked = settings.widgetShowWeight
                ) { vm.setWidgetShowWeight(it) }
            }

            Spacer(Modifier.height(Dimens.Gap))

            // 1. 快捷记录 2×2
            WidgetCard(
                title = "快捷记录（2×2）",
                desc = "早餐 / 体重 / 喝水 / 拍照，点一下直达",
                onAdd = { requestPin(context, QuickRecordWidgetReceiver::class.java, WidgetTarget.QUICK) },
                previewAlpha = settings.widgetAlpha / 100f
            ) {
                // V2 阶段5：预览与真实 Glance 同款「图标+短标签」轻量入口
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickCell(WidgetSpec.LABEL_BREAKFAST, R.drawable.ic_widget_breakfast)
                    QuickCell(WidgetSpec.LABEL_WEIGHT, R.drawable.ic_widget_weight)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickCell(WidgetSpec.LABEL_WATER, R.drawable.ic_widget_water)
                    QuickCell(WidgetSpec.LABEL_PHOTO, R.drawable.ic_widget_camera)
                }
            }

            Spacer(Modifier.height(Dimens.Gap))

            // 2. 今日概览 4×2
            WidgetCard(
                title = "今日剩余（4×4）",
                desc = "剩余热量 + 三条营养条 + 饮水",
                onAdd = { requestPin(context, OverviewWidgetReceiver::class.java, WidgetTarget.OVERVIEW) },
                previewAlpha = settings.widgetAlpha / 100f
            ) {
                Text(text = WidgetSpec.LABEL_REMAINING, fontSize = 11.sp, color = TextSecondary)
                Text(
                    text = "${WidgetSpec.SAMPLE_REMAINING} ${WidgetSpec.LABEL_KCAL_UNIT}",
                    fontSize = WidgetSpec.REMAINING_NUMBER_SP.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                // v1.1.0：预览跟随真实设置——关掉营养素/饮水展示，预览同步消失
                if (settings.widgetShowMacro) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniBar(WidgetSpec.LABEL_PROTEIN, WidgetSpec.SAMPLE_PROTEIN, WidgetSpec.SAMPLE_PROTEIN_GOAL,
                            WidgetSpec.MACRO_PROTEIN, Modifier.weight(1f))
                        MiniBar(WidgetSpec.LABEL_CARB, WidgetSpec.SAMPLE_CARB, WidgetSpec.SAMPLE_CARB_GOAL,
                            WidgetSpec.MACRO_CARB, Modifier.weight(1f))
                        MiniBar(WidgetSpec.LABEL_FAT, WidgetSpec.SAMPLE_FAT, WidgetSpec.SAMPLE_FAT_GOAL,
                            WidgetSpec.MACRO_FAT, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (settings.widgetShowWater) {
                    Text(text = "${WidgetSpec.LABEL_WATER_SHORT} ${WidgetSpec.SAMPLE_WATER} / ${WidgetSpec.DEFAULT_WATER_GOAL} ml",
                        fontSize = 10.sp, color = TextSecondary)
                }
            }

            Spacer(Modifier.height(Dimens.Gap))

            // 3. 体重闪记 4×1
            WidgetCard(
                title = "体重闪记（4×1）",
                desc = "当前体重 + 记录按钮",
                onAdd = { requestPin(context, WeightWidgetReceiver::class.java, WidgetTarget.WEIGHT) },
                previewAlpha = settings.widgetAlpha / 100f
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = WidgetSpec.LABEL_CURRENT_WEIGHT, fontSize = 10.sp, color = TextSecondary)
                        Text(
                            // 阶段 3：示例值与「已隐藏」文案与真实 Glance 共用 WidgetSpec
                            text = if (settings.widgetShowWeight)
                                com.qingji.app.domain.WeightUnit.formatWithSymbol(
                                    WidgetSpec.SAMPLE_WEIGHT.toDouble(), "kg"
                                ) else WidgetSpec.LABEL_WEIGHT_HIDDEN,
                            fontSize = WidgetSpec.WEIGHT_NUMBER_SP.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.width(WidgetSpec.WEIGHT_BUTTON_GAP_DP.dp))
                    Text(
                        text = WidgetSpec.LABEL_RECORD,
                        fontSize = 12.sp,
                        color = WidgetSpec.GREEN,
                        modifier = Modifier
                            .clip(RoundedCornerShape(WidgetSpec.TILE_CORNER_DP.dp))
                            .background(WidgetSpec.GREEN_LIGHT)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(Dimens.Gap))

            // 阶段D：身体数据（复用 BodyWidget，不新增第二套 Provider）
            WidgetCard(
                title = "身体数据（4×4）",
                desc = "当前体重 · 目标进度 · BMI · 体脂率（实测/估算）",
                onAdd = { requestPin(context, BodyWidgetReceiver::class.java, WidgetTarget.BODY_DATA) },
                previewAlpha = settings.widgetAlpha / 100f
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "当前体重", fontSize = 10.sp, color = TextSecondary)
                        Text(text = "82.4 kg", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = "目标 75.0 kg · 还差 7.4 kg", fontSize = 9.sp, color = TextSecondary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "BMI 24.4 · 正常", fontSize = 11.sp, color = WidgetSpec.GREEN)
                        Text(text = "体脂率 21.5% · 估算", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(Dimens.Gap))

            SectionCard {
                Text(
                    text = "小提示",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "小米 / 华为 / OPPO 请在系统设置里给轻记「允许自启动 + 允许后台运行」，" +
                        "否则小组件可能停止刷新。系统规定小组件最快 30 分钟才会自动刷一次，" +
                        "所以 App 里记完东西会主动推一把。",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "删掉重新添加不会重置外观设置。若外观和预期不一致，可恢复默认：",
                    fontSize = 11.sp, color = TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "恢复默认外观",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = QingJiGreenDark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(QingJiGreenLight)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable {
                            vm.setWidgetAlpha(100)
                            vm.setWidgetShowWater(true)
                            vm.setWidgetShowMacro(true)
                            vm.setWidgetShowWeight(true)
                            alphaDraft = 100f
                        }
                )
            }

            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }
}

@Composable
private fun WidgetCard(
    title: String,
    desc: String,
    onAdd: () -> Unit,
    previewAlpha: Float = 1f,
    preview: @Composable () -> Unit
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(text = desc, fontSize = 11.sp, color = TextSecondary)
            }
            Text(
                text = "添加到桌面",
                fontSize = 12.sp,
                color = QingJiGreenDark,
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.TinyCorner))
                    .background(QingJiGreenLight)
                    .clickable { onAdd() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(12.dp))

        // 预览：模拟桌面上的样子
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(WidgetSpec.CORNER_CARD_DP.dp))
                // 底下一层浅色当「壁纸」，上面盖半透明白卡片，拖动滑杆时能直观看到透明效果
                .background(QingJiGreenLight.copy(alpha = 0.55f))
                .background(CardWhite.copy(alpha = previewAlpha.coerceIn(
                    WidgetSpec.ALPHA_MIN / WidgetSpec.ALPHA_MAX.toFloat(), 1f
                )))
                .padding(12.dp)
        ) {
            Text(
                // v1.1.0：示例数字必须明确标注，不冒充用户真实记录
                text = "预览（示例数据，非真实记录）",
                fontSize = 9.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            preview()
        }
    }
}

/** V2 阶段5：预览入口与真实 Glance 同款「图标+短标签」，图标资源也共用。 */
@Composable
private fun QuickCell(label: String, iconRes: Int) {
    Column(
        modifier = Modifier
            .size(width = 74.dp, height = 52.dp)
            .clip(RoundedCornerShape(WidgetSpec.TILE_CORNER_DP.dp))
            .background(CardWhite.copy(alpha = WidgetSpec.TILE_BG_ALPHA)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(WidgetSpec.QUICK_ICON_DP.dp)
        )
        Spacer(Modifier.height(WidgetSpec.QUICK_ICON_LABEL_GAP_DP.dp))
        Text(text = label, fontSize = WidgetSpec.QUICK_LABEL_SP.sp, color = TextPrimary)
    }
}

/** V2 阶段5：进度按示例 current/goal 计算，与真实 MacroBar 同一算法，不再写死 45%。 */
@Composable
private fun MiniBar(
    label: String,
    current: Int,
    goal: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    // V3：与真实 MacroBar 共用同一安全进度算法（目标<=0/超目标/异常输入）。
    val progress = com.qingji.app.widget.WidgetProgress.fraction(current, goal)
    Column(modifier = modifier) {
        Text(text = label, fontSize = 9.sp, color = TextSecondary)
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WidgetSpec.MACRO_BAR_HEIGHT_DP.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = WidgetSpec.MACRO_TRACK_ALPHA)),
            contentAlignment = Alignment.CenterStart
        ) {
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(WidgetSpec.MACRO_BAR_HEIGHT_DP.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
            }
        }
    }
}

/** 请求把小组件固定到桌面。 */
private fun requestPin(context: Context, receiver: Class<*>, target: WidgetTarget? = null) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        Toast.makeText(context, "系统版本太低，请长按桌面手动添加", Toast.LENGTH_SHORT).show()
        WidgetPerf.mark("pin_request", target?.name ?: "-", "unsupported_sdk")
        return
    }
    val pinTrace = WidgetPerf.begin("pin_request", target?.name ?: "-")
    val manager = AppWidgetManager.getInstance(context)
    val component = ComponentName(context, receiver)
    if (!manager.isRequestPinAppWidgetSupported) {
        Toast.makeText(context, "这台手机不支持自动添加，请长按桌面手动添加", Toast.LENGTH_SHORT).show()
        WidgetPerf.mark("pin_request", target?.name ?: "-", "unsupported_launcher")
        WidgetPerf.end(pinTrace, extra = "supported=false") // V3：早退也要闭合埋点生命周期
        return
    }
    // 用户确认固定成功后回调 → 立即用当前代码/数据重绘全部小组件，
    // 不再等 30 分钟系统周期（解决「删掉重加看起来没变化」）。
    manager.requestPinAppWidget(
        component,
        null,
        com.qingji.app.widget.WidgetPinReceiver.pendingIntent(context, target)
    )
    WidgetPerf.end(pinTrace, extra = "supported=true")
}
