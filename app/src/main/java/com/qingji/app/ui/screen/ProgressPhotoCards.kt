package com.qingji.app.ui.screen

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.theme.pressable


/** 进度照片：横向缩略图。 */
@Composable
internal fun ProgressPhotoCard(
    photos: List<com.qingji.app.data.local.entity.ProgressPhoto>,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
    onViewAll: () -> Unit = {}
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = s("进度照片"),
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (photos.isEmpty()) "拍一张留个底，过段时间对比最有感觉" else "${photos.size} 张",
                    fontSize = TextSizes.Caption,
                    color = TextSecondary
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (photos.isNotEmpty()) {
                    Text(
                        text = s("查看全部"),
                        fontSize = 12.sp,
                        color = QingJiGreenDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.TinyCorner))
                            .background(QingJiGreenLight)
                            .pressable { onViewAll() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Text(
                    text = s("+ 添加"),
                    fontSize = 12.sp,
                    color = QingJiGreenDark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.TinyCorner))
                        .background(QingJiGreenLight)
                        .pressable { onAdd() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(Dimens.ItemCorner))
                    .background(PageBackground)
                    .pressable { onAdd() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = s("点这里从相册选一张"), fontSize = 12.sp, color = TextSecondary)
            }
        } else {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(photos.size, key = { photos[it].id }) { index ->
                    val photo = photos[index]
                    Box {
                        coil.compose.AsyncImage(
                            model = java.io.File(photo.path),
                            contentDescription = "进度照片",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(Dimens.ItemCorner))
                                .background(PageBackground)
                                .clickable { onViewAll() }
                        )
                        Text(
                            text = "×",
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(Color(0x99000000))
                                .clickable { onDelete(photo.id) }
                                .padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
