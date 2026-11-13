package com.qingji.app.ui.screen.training

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.local.TrainingSeed
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.TrainingPlanViewModel

/**
 * P1 动作库 / 我的收藏。同一个页面，favoritesOnly=true 时只列收藏。
 * 搜索按名称或主肌群，分类胶囊复用 TrainingComponents 里的 7 类。
 */
@Composable
fun ExerciseLibraryScreen(
    vm: TrainingPlanViewModel,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    favoritesOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val all by vm.exercises.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val message by vm.message.collectAsState()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }

    val source = if (favoritesOnly) favorites else all
    val shown = source.filter { e ->
        (category == null || e.category == category) &&
            (query.isBlank() || e.name.contains(query.trim(), ignoreCase = true) ||
                e.primaryMuscles.contains(query.trim(), ignoreCase = true))
    }

    Column(modifier = modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(
            title = if (favoritesOnly) "我的动作收藏" else "动作库",
            onBack = onBack
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(10.dp))
            if (message != null) {
                Text(message.orEmpty(), fontSize = TextSizes.Caption, color = QingJiGreen)
                Spacer(Modifier.height(6.dp))
            }
            // 搜索框
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = TextSizes.Body, color = TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.ItemCorner))
                    .background(CardWhite)
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("搜动作名或肌群", fontSize = TextSizes.Body, color = TextSecondary)
                    inner()
                }
            )
            Spacer(Modifier.height(10.dp))
            MuscleCategoryTabs(selected = category, onSelect = { category = it })
            Spacer(Modifier.height(10.dp))

            if (shown.isEmpty()) {
                SectionCard {
                    Text(
                        if (favoritesOnly) "还没有收藏动作。在动作详情里点「收藏」就会出现在这里。"
                        else "没找到动作。可以回训练页用「自己加一个」新建。",
                        fontSize = TextSizes.Body, color = TextSecondary
                    )
                }
            } else {
                shown.forEach { e ->
                    SectionCard(
                        modifier = Modifier.clickable { onOpenDetail(e.uuid) },
                        contentPadding = Dimens.CardPaddingCompact
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(e.name, fontSize = TextSizes.CardTitle,
                                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(
                                    listOf(e.primaryMuscles, e.equipment).filter { it.isNotBlank() }
                                        .joinToString(" · ").ifBlank { "自建动作" },
                                    fontSize = TextSizes.Tiny, color = TextSecondary
                                )
                            }
                            Text(
                                if (e.isFavorite) "★" else "☆",
                                fontSize = 20.sp,
                                color = if (e.isFavorite) QingJiGreen else TextSecondary,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clickable { vm.toggleFavorite(e.uuid, !e.isFavorite) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }
}

/**
 * P1 动作详情：统一结构（图示占位 / 主辅肌群 / 器械 / 步骤 / 常见错误 / 开始记录入口）。
 * 图示没用任何外部图片 —— 需求红线 4 要求图片有授权来源，未审核前只放文字占位。
 */
@Composable
fun ExerciseDetailScreen(
    vm: TrainingPlanViewModel,
    uuid: String,
    onBack: () -> Unit,
    onStartRecord: (ExerciseEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val all by vm.exercises.collectAsState()
    val e = all.firstOrNull { it.uuid == uuid }

    Column(modifier = modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(title = e?.name ?: "动作详情", onBack = onBack)
        if (e == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("这个动作已经不在动作库里了", color = TextSecondary, fontSize = TextSizes.Body)
            }
            return
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))
            // 图示占位：授权素材到位前不塞来路不明的图
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(Dimens.CardCorner))
                    .background(QingJiGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        TrainingSeed.CATEGORY_LABELS.firstOrNull { it.first == e.category }?.second ?: "动作",
                        fontSize = 28.sp, fontWeight = FontWeight.Bold, color = QingJiGreen
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("动作图示待补充（需授权素材）", fontSize = TextSizes.Tiny, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))

            SectionCard {
                InfoLine("主肌群", e.primaryMuscles.ifBlank { "未填" })
                InfoLine("辅助肌群", e.secondaryMuscles.ifBlank { "未填" })
                InfoLine("器械", e.equipment.ifBlank { "徒手 / 不限" })
                InfoLine("分类", TrainingSeed.CATEGORY_LABELS.firstOrNull { it.first == e.category }?.second ?: "其它")
            }
            Spacer(Modifier.height(10.dp))
            SectionCard {
                Text("动作要点", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                e.steps.split("\n").filter { it.isNotBlank() }.forEach { step ->
                    Text("· $step", fontSize = TextSizes.Body, color = TextPrimary,
                        lineHeight = 21.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text("常见错误", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(e.commonMistakes.ifBlank { "暂无记录" },
                    fontSize = TextSizes.Body, color = TextSecondary)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "动作说明为通用要点，不是训练处方；请按自身情况调整，量力而行。",
                fontSize = TextSizes.Tiny, color = TextSecondary
            )
            Spacer(Modifier.height(16.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .padding(horizontal = Dimens.ScreenPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { vm.toggleFavorite(e.uuid, !e.isFavorite) },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (e.isFavorite) "取消收藏" else "收藏", fontSize = TextSizes.Body)
            }
            Button(
                onClick = { onStartRecord(e) },
                modifier = Modifier.weight(2f)
            ) {
                Text("用这个动作开始训练", fontSize = TextSizes.Body)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = TextSizes.Caption, color = TextSecondary,
            modifier = Modifier.weight(1f))
        Text(value, fontSize = TextSizes.Body, color = TextPrimary,
            modifier = Modifier.weight(2f))
    }
}
