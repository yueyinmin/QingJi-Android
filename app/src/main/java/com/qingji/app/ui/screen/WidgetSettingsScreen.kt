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
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.screen.settings.SwitchRow
import com.qingji.app.ui.viewmodel.SettingsViewModel
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.MacroCarb
import com.qingji.app.ui.theme.MacroFat
import com.qingji.app.ui.theme.MacroProtein
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.widget.OverviewWidgetReceiver
import com.qingji.app.widget.QuickRecordWidgetReceiver
import com.qingji.app.widget.WeightWidgetReceiver

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
                    valueRange = 30f..100f,
                    steps = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "数值越小越透，30% 时能看到桌面壁纸。",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = "显示饮水进度",
                    subtitle = "今日概览卡右上角的饮水数字",
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
                onAdd = { requestPin(context, QuickRecordWidgetReceiver::class.java) },
                previewAlpha = settings.widgetAlpha / 100f
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickCell("早餐", QingJiGreenDark)
                    QuickCell("体重", QingJiGreen)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickCell("喝水", QingJiGreen)
                    QuickCell("拍照", QingJiGreen.copy(alpha = 0.72f))
                }
            }

            Spacer(Modifier.height(Dimens.Gap))

            // 2. 今日概览 4×2
            WidgetCard(
                title = "今日概览（4×2）",
                desc = "剩余热量 + 三条营养条 + 饮水",
                onAdd = { requestPin(context, OverviewWidgetReceiver::class.java) },
                previewAlpha = settings.widgetAlpha / 100f
            ) {
                Text(text = "今日剩余", fontSize = 11.sp, color = TextSecondary)
                Text(
                    text = "2313 千卡",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniBar("蛋白", MacroProtein, Modifier.weight(1f))
                    MiniBar("碳水", MacroCarb, Modifier.weight(1f))
                    MiniBar("脂肪", MacroFat, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text(text = "饮水 0 / 3200 ml", fontSize = 10.sp, color = TextSecondary)
            }

            Spacer(Modifier.height(Dimens.Gap))

            // 3. 体重闪记 4×1
            WidgetCard(
                title = "体重闪记（4×1）",
                desc = "当前体重 + 记录按钮",
                onAdd = { requestPin(context, WeightWidgetReceiver::class.java) },
                previewAlpha = settings.widgetAlpha / 100f
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "当前体重", fontSize = 10.sp, color = TextSecondary)
                        Text(
                            text = "92.0 kg",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "+ 记录",
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(QingJiGreen)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
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
                .clip(RoundedCornerShape(14.dp))
                // 底下一层浅色当「壁纸」，上面盖半透明白卡片，拖动滑杆时能直观看到透明效果
                .background(QingJiGreenLight.copy(alpha = 0.55f))
                .background(CardWhite.copy(alpha = previewAlpha.coerceIn(0.3f, 1f)))
                .padding(12.dp)
        ) {
            Text(
                text = "预览",
                fontSize = 9.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            preview()
        }
    }
}

@Composable
private fun QuickCell(label: String, color: Color) {
    Column(
        modifier = Modifier
            .size(width = 74.dp, height = 46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MiniBar(label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, fontSize = 9.sp, color = TextSecondary)
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = 0.25f))
        )
    }
}

/** 请求把小组件固定到桌面。 */
private fun requestPin(context: Context, receiver: Class<*>) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        Toast.makeText(context, "系统版本太低，请长按桌面手动添加", Toast.LENGTH_SHORT).show()
        return
    }
    val manager = AppWidgetManager.getInstance(context)
    val component = ComponentName(context, receiver)
    if (!manager.isRequestPinAppWidgetSupported) {
        Toast.makeText(context, "这台手机不支持自动添加，请长按桌面手动添加", Toast.LENGTH_SHORT).show()
        return
    }
    manager.requestPinAppWidget(component, null, null)
}
