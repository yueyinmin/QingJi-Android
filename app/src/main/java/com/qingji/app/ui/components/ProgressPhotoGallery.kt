package com.qingji.app.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.qingji.app.data.local.entity.ProgressPhoto
import java.io.File

/**
 * 进度照片「查看全部 + 前后对比」（v3.4.0）。
 *
 * 为什么不用第三方图片查看库：项目里已经在用 Coil，只需要「看全部 + 选两张并排比」，
 * 引 zoomable / 大图库会白涨包体积，也不符合本项目「图表和看图都自己画」的基线。
 *
 * 交互：
 * - 打开是三列网格，点一张选中（高亮描边）；
 * - 选满两张自动进入左右对比，能看到各自的日期；
 * - 对比页点「重新选择」回到网格。
 */
@Composable
fun ProgressPhotoGallery(
    photos: List<ProgressPhoto>,
    onDismiss: () -> Unit
) {
    var selectedIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    val selected = photos.filter { it.id in selectedIds }
    val comparing = selected.size == 2

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE6121212))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // ---------- 顶栏 ----------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (comparing) "前后对比" else "全部进度照片（${photos.size} 张）",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "关闭",
                        fontSize = 13.sp,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33FFFFFF))
                            .clickable { onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (comparing) "左右两张按拍摄日期排列，方便看变化" else "点两张照片就能并排对比",
                    fontSize = 11.sp,
                    color = Color(0xFFBDBDBD)
                )
                Spacer(Modifier.height(12.dp))

                if (comparing) {
                    CompareView(
                        first = selected[0],
                        second = selected[1],
                        onReset = { selectedIds = emptyList() }
                    )
                } else {
                    GridView(
                        photos = photos,
                        selectedIds = selectedIds,
                        onToggle = { id ->
                            selectedIds = if (id in selectedIds) {
                                selectedIds - id
                            } else {
                                // 已经选了两张还点第三张时，丢掉最早那张，始终最多两张
                                (selectedIds + id).takeLast(2)
                            }
                        }
                    )
                }
            }
        }
    }
}

/** 三列网格；用 Column + chunked 而不是 LazyVerticalGrid，避免多引一个组件依赖。 */
@Composable
private fun GridView(
    photos: List<ProgressPhoto>,
    selectedIds: List<Long>,
    onToggle: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        photos.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { photo ->
                    val checked = photo.id in selectedIds
                    Column(modifier = Modifier.weight(1f)) {
                        AsyncImage(
                            model = File(photo.path),
                            contentDescription = "进度照片 ${photo.date}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2A2A2A))
                                .clickable { onToggle(photo.id) }
                        )
                        Text(
                            text = if (checked) "✓ ${photo.date}" else photo.date.toString(),
                            fontSize = 10.sp,
                            color = if (checked) Color(0xFF7BE0B6) else Color(0xFFBDBDBD),
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
                // 补齐空位，保证每行三列对齐
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 两张并排对比：早的在左、晚的在右（列表本身按日期倒序，所以反过来排）。 */
@Composable
private fun CompareView(
    first: ProgressPhoto,
    second: ProgressPhoto,
    onReset: () -> Unit
) {
    val left: ProgressPhoto
    val right: ProgressPhoto
    if (first.date <= second.date) {
        left = first; right = second
    } else {
        left = second; right = first
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ComparePanel(photo = left, label = "之前 · ${left.date}", modifier = Modifier.weight(1f))
            ComparePanel(photo = right, label = "之后 · ${right.date}", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "重新选择",
            fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1F8A70))
                .clickable { onReset() }
                .padding(vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ComparePanel(
    photo: ProgressPhoto,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = label, fontSize = 11.sp, color = Color(0xFFBDBDBD))
        Spacer(Modifier.height(6.dp))
        AsyncImage(
            model = File(photo.path),
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF2A2A2A))
        )
    }
}
