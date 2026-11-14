package com.qingji.app.ui.screen.record

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.MultiColor
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.pressable

/**
 * ============================================================
 * 底部中间「+」弹出的记录面板（v3.2 按吃有数的排法重做）
 * ------------------------------------------------------------
 * 从上到下四层（v9 重排）：
 * 1. 柔和绿渐变横幅「扫描食物」→ 拍照 AI 识别；
 * 2. 2 行 4 列图标网格：语音输入 / 文字录入 / 扫条形码 / 食物库，
 *    记录喝水 / 每日打卡 / 冲动记录 / 目标偏离（同一套卡片等比缩小）；
 * 3. 2×2 浅底宽卡：记录运动 / 记录体重 / 开始训练 / 记录有氧；
 * 4. 体型照片链接 + 悬浮关闭键。
 *
 * v3.2.2：用户要「参考图那种多彩」，这里改回固定语义色，
 * 颜色统一从 ui/theme/Color.kt 的 MultiColor 取，不随主题色开关变。
 * 主题色仍然负责主按钮、选中态、顶部渐变等整体基调，
 * 语义色负责「一眼分辨功能」，两条线互不打架。
 * 每个入口都有真实动作，没有「点了没反应」的摆设。
 * ============================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordSheet(
    onDismiss: () -> Unit,
    onSearchFood: () -> Unit,
    onTextAi: () -> Unit,
    onWeight: () -> Unit,
    onImage: () -> Unit,
    onVoice: () -> Unit,
    onBarcode: () -> Unit,
    /** 直接记一杯水（默认 200ml） */
    onWater: () -> Unit = {},
    /** 进进展页记运动 */
    onExercise: () -> Unit = {},
    /** 训练中心 P0：开始一次力量训练 */
    onStartTraining: () -> Unit = {},
    /** 训练中心 P0：记录有氧，直接复用现有「记录运动」流程，不另做新页 */
    onAerobic: () -> Unit = {},
    /** 进进展页更新体型照片 */
    onBodyPhoto: () -> Unit = {},
    /** 自律管理（SD-001）：每日打卡 / 冲动记录 / 目标偏离 */
    onSelfDisciplineCheckin: () -> Unit = {},
    onSelfDisciplineUrge: () -> Unit = {},
    onSelfDisciplineDeviation: () -> Unit = {}
) {
    // 先收起弹层再执行动作，否则弹层会盖住刚打开的新页面
    val go: (() -> Unit) -> Unit = { action ->
        onDismiss()
        action()
    }

    // sheetfix：默认半展开高度装不下这么多入口，最后一行（每日打卡那三个）
    // 被屏幕底边和手势条裁掉一半，后面的「文字录入/扫条形码/关闭键」全够不着。
    // skipPartiallyExpanded 让弹层直接开到内容全高；再加 verticalScroll 兜底，
    // 小屏上超高的部分也能滚动看到。
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp)
        ) {
            // ---------- 1. 扫描食物横幅（参考图的柔和绿渐变） ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(MultiColor.ScanStart, MultiColor.ScanEnd)
                        )
                    )
                    .pressable { go(onImage) }
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "扫描食物",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "AI 视觉识别，智能估算热量",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MultiColor.ScanArrow,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 2. 2 行 4 列图标网格（8 个入口，等比缩小） ----------
            // v9：下面三个自律模块移到「记录」宽卡上方；文字录入 / 扫条形码
            // 从底部宽按钮改成与语音输入同款的图标卡，一起进网格。
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SheetIconCard("语音输入", Icons.Outlined.Mic, MultiColor.Voice, Modifier.weight(1f)) {
                        go(onVoice)
                    }
                    SheetIconCard("文字录入", Icons.Outlined.EditNote, MultiColor.TextAi, Modifier.weight(1f)) {
                        go(onTextAi)
                    }
                    SheetIconCard("扫条形码", Icons.Outlined.QrCode, MultiColor.Barcode, Modifier.weight(1f)) {
                        go(onBarcode)
                    }
                    SheetIconCard("食物库", Icons.AutoMirrored.Outlined.MenuBook, MultiColor.FoodLibrary, Modifier.weight(1f)) {
                        go(onSearchFood)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SheetIconCard("记录喝水", Icons.Outlined.WaterDrop, MultiColor.Drink, Modifier.weight(1f)) {
                        go(onWater)
                    }
                    SheetIconCard("每日打卡", Icons.Outlined.TaskAlt, MultiColor.QuickWeight, Modifier.weight(1f)) {
                        go(onSelfDisciplineCheckin)
                    }
                    SheetIconCard("冲动记录", Icons.Outlined.Bolt, MultiColor.FastingText, Modifier.weight(1f)) {
                        go(onSelfDisciplineUrge)
                    }
                    SheetIconCard("目标偏离", Icons.Outlined.AltRoute, MultiColor.QuickBook, Modifier.weight(1f)) {
                        go(onSelfDisciplineDeviation)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---------- 3. 两张浅底宽卡（蓝 / 绿） ----------
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetWideCard("记录运动", "🏃", MultiColor.Exercise, Modifier.weight(1f)) {
                    go(onExercise)
                }
                SheetWideCard("记录体重", "⚖️", MultiColor.Weight, Modifier.weight(1f)) {
                    go(onWeight)
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---------- 3.5 训练中心（P0）：开始训练 / 记录有氧 ----------
            // 「记录有氧」直接跳现有的运动选择页，不新建第二条数据体系。
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetWideCard("开始训练", "🏋️", MultiColor.Exercise, Modifier.weight(1f)) {
                    go(onStartTraining)
                }
                SheetWideCard("记录有氧", "🚶", MultiColor.QuickWater, Modifier.weight(1f)) {
                    go(onAerobic)
                }
            }

            // v9：自律三入口已并入上方 2 行 4 列网格，放在「记录」宽卡之前。
            Spacer(Modifier.height(14.dp))

            // ---------- 4. 体型照片 + 补充入口 ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .pressable { go(onBodyPhoto) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(text = "更新我的体型照片", fontSize = 12.sp, color = TextSecondary)
            }

            // v9：文字录入 / 扫条形码已改成图标卡，并入上方 2 行 4 列网格。
            Spacer(Modifier.height(18.dp))

            // ---------- 悬浮关闭键 ----------
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CardWhite)
                        .border(1.dp, DividerColor, CircleShape)
                        .pressable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * 白底描边的小方卡：一个彩色圆形图标 + 名称。
 * color 用固定语义色，圆底取 18% 透明度，图标用本色。
 */
@Composable
private fun SheetIconCard(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardWhite)
            .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
            .pressable { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1
        )
    }
}

/**
 * 浅底的宽卡：左边标题 + 圆形「去记录」箭头，右边一个小插画。
 * 底色取 color 的 12% 透明度，标题/箭头/文字统一用本色。
 */
@Composable
private fun SheetWideCard(
    title: String,
    emoji: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .pressable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(text = "去记录", fontSize = 11.sp, color = color)
            }
        }
        Text(text = emoji, fontSize = 24.sp)
    }
}

// v9：MiniEntry 已删除。文字录入 / 扫条形码改成 SheetIconCard，并入上方 2 行 4 列网格。
