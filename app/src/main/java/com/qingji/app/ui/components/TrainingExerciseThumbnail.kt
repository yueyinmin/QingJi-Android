package com.qingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.training.media.ExerciseMediaMapping
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.QingJiGreen

/**
 * QJ 训练联动公共组件：三模块（计划 / 开始训练 / 训练日记）共用的动作缩略图。
 * 统一素材加载（ExerciseMediaImage）+ 尺寸 + 兜底（动作名首字），不重复导入图片资源。
 * 只负责显示，不碰任何业务数据。
 */
@Composable
fun TrainingExerciseThumbnail(
    exerciseUuid: String,
    fallbackName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(DividerColor.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        com.qingji.app.ui.screen.training.ExerciseMediaImage(
            media = ExerciseMediaMapping.forUuid(exerciseUuid),
            modifier = Modifier.size(size)
        ) {
            Text(
                fallbackName.take(1),
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Bold,
                color = QingJiGreen
            )
        }
    }
}
