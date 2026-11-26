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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.domain.AerobicLibrary
import com.qingji.app.data.training.media.ExerciseMediaMapping
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.ExerciseViewModel
import com.qingji.app.ui.viewmodel.TrainingPlanViewModel

/**
 * 有氧动作库（2026-11-24）：力训动作库的兄弟页。
 *
 * 数据源两路合并：
 *  1. 动作库 302 条里标为 CARDIO 的动作（跑步/骑行/游泳/跳绳等，有 SVG 图示）；
 *  2. 「记录有氧」原有的预设（快走/跑步机/球类等，带 MET，无图示）。
 * 点任意一项统一走动作库详情页；预设项以内存实体呈现，不写库。
 */
@Composable
fun AerobicLibraryScreen(
    planVm: TrainingPlanViewModel,
    exerciseVm: ExerciseViewModel = viewModel(),
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val all by planVm.exercises.collectAsState()
    val presets by exerciseVm.presets.collectAsState()
    var query by remember { mutableStateOf("") }
    var sourceFilter by remember { mutableStateOf<String?>(null) }

    // 两套数据源合并；力量/柔韧预设必须先挡掉，再按规范化名称去重。
    val cardioEntities = remember(all) { all.filter(AerobicLibrary::isAerobic) }
    val presetEntities = remember(presets) {
        presets.filter(AerobicLibrary::isAerobicPreset).map(AerobicLibrary::fromPreset)
    }
    val merged = remember(cardioEntities, presetEntities) {
        cardioEntities + presetEntities.filter { p ->
            cardioEntities.none { AerobicLibrary.sameCanonicalName(it.name, p.name) }
        }
    }

    val q = query.trim()
    val shown = remember(merged, q, sourceFilter) {
        merged.filter { e ->
            val fromPreset = AerobicLibrary.presetIdOf(e.uuid) != null
            val sourceOk = sourceFilter == null ||
                (sourceFilter == "预设" && fromPreset) ||
                (sourceFilter == "动作库" && !fromPreset)
            sourceOk && (q.isBlank() || e.name.contains(q, ignoreCase = true) ||
                e.englishName.contains(q, ignoreCase = true) ||
                e.primaryMuscles.contains(q, ignoreCase = true))
        }
    }

    Column(modifier = modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(title = "有氧动作库", onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = Dimens.ScreenPadding)
        ) {
            item { Spacer(Modifier.height(10.dp)) }
            item {
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
                        if (query.isEmpty()) Text("搜运动名 / 英文名", fontSize = TextSizes.Body, color = TextSecondary)
                        inner()
                    }
                )
            }
            item {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(null, "动作库", "预设").forEach { key ->
                        val active = sourceFilter == key
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Dimens.PillCorner))
                                .background(if (active) QingJiGreen else QingJiGreenLight)
                                .clickable { sourceFilter = key }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = key ?: "全部",
                                fontSize = TextSizes.Caption,
                                color = if (active) CardWhite else QingJiGreen
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (shown.isEmpty()) {
                item {
                    SectionCard {
                        Text("没找到有氧动作。换个关键词试试。", fontSize = TextSizes.Body, color = TextSecondary)
                    }
                }
            } else {
                items(count = shown.size, key = { i -> shown[i].uuid }) { i ->
                    val e = shown[i]
                    SectionCard(
                        modifier = Modifier.clickable { onOpenDetail(e.uuid) },
                        contentPadding = Dimens.CardPaddingCompact
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(Dimens.ItemCorner))
                                    .background(QingJiGreenLight),
                                contentAlignment = Alignment.Center
                            ) {
                                ExerciseMediaImage(
                                    media = ExerciseMediaMapping.forEntity(e.uuid, e.assetPath),
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Text(
                                        e.name.take(1),
                                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = QingJiGreenDark
                                    )
                                }
                            }
                            Spacer(Modifier.size(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(e.name, fontSize = TextSizes.CardTitle,
                                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(
                                    if (AerobicLibrary.presetIdOf(e.uuid) != null) "记录有氧预设 · 带 MET"
                                    else listOf(e.primaryMuscles, e.equipment).filter { it.isNotBlank() }
                                        .joinToString(" · ").ifBlank { "有氧动作" },
                                    fontSize = TextSizes.Tiny, color = TextSecondary
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            item { Spacer(Modifier.height(Dimens.FabSpace)) }
        }
    }
}

/** 仅供详情页展示：预设项没有 302 条素材与步骤，显示占位说明。 */
internal fun aerobicPresetHint(entity: ExerciseEntity): String =
    if (AerobicLibrary.presetIdOf(entity.uuid) != null)
        "这一项来自「记录有氧」预设，用 MET 与时长按消耗自动计算；暂无动作图示和步骤。"
    else ""
