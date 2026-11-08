package com.qingji.app.ui.screen.record

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.pressable

/**
 * ============================================================
 * 底部中间「+」弹出的记录面板（v3.2 按吃有数的排法重做）
 * ------------------------------------------------------------
 * 从上到下四层：
 * 1. 主题色渐变横幅「扫描食物」→ 拍照 AI 识别；
 * 2. 三张白色图标卡：语音输入 / 食物库 / 记录喝水；
 * 3. 两张浅底宽卡：记录运动 / 记录体重；
 * 4. 体型照片链接 + 文字录入/条形码补充入口 + 悬浮关闭键。
 *
 * 颜色全部取当前主题色，不再像旧版那样六个入口各写死橙/青/蓝/粉/紫。
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
    /** 进进展页更新体型照片 */
    onBodyPhoto: () -> Unit = {}
) {
    // 先收起弹层再执行动作，否则弹层会盖住刚打开的新页面
    val go: (() -> Unit) -> Unit = { action ->
        onDismiss()
        action()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp)
        ) {
            // ---------- 1. 扫描食物横幅 ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(QingJiGreen, QingJiGreen.copy(alpha = 0.78f))
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
                        tint = QingJiGreenDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 2. 三张白色图标卡 ----------
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetIconCard("语音输入", Icons.Outlined.Mic, Modifier.weight(1f)) { go(onVoice) }
                SheetIconCard("食物库", Icons.AutoMirrored.Outlined.MenuBook, Modifier.weight(1f)) {
                    go(onSearchFood)
                }
                SheetIconCard("记录喝水", Icons.Outlined.WaterDrop, Modifier.weight(1f)) { go(onWater) }
            }

            Spacer(Modifier.height(10.dp))

            // ---------- 3. 两张浅底宽卡 ----------
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetWideCard("记录运动", "🏃", Modifier.weight(1f)) { go(onExercise) }
                SheetWideCard("记录体重", "⚖️", Modifier.weight(1f)) { go(onWeight) }
            }

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

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniEntry("文字录入", Icons.Outlined.EditNote, Modifier.weight(1f)) { go(onTextAi) }
                MiniEntry("扫条形码", Icons.Outlined.QrCode, Modifier.weight(1f)) { go(onBarcode) }
            }

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

/** 白底描边的小方卡：一个主题色圆形图标 + 名称。 */
@Composable
private fun SheetIconCard(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .border(1.dp, DividerColor, RoundedCornerShape(14.dp))
            .pressable { onClick() }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(QingJiGreenLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = QingJiGreen,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1
        )
    }
}

/** 浅底的宽卡：左边标题 + 圆形「去记录」箭头，右边一个小插画。 */
@Composable
private fun SheetWideCard(
    title: String,
    emoji: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(QingJiGreenLight.copy(alpha = 0.6f))
            .pressable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(QingJiGreen),
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
                Text(text = "去记录", fontSize = 11.sp, color = QingJiGreenDark)
            }
        }
        Text(text = emoji, fontSize = 24.sp)
    }
}

/** 补充入口：文字录入 / 条形码，避免重排版把原有功能弄丢。 */
@Composable
private fun MiniEntry(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(QingJiGreenLight.copy(alpha = 0.45f))
            .pressable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = QingJiGreen,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, fontSize = 12.sp, color = QingJiGreenDark, maxLines = 1)
    }
}
