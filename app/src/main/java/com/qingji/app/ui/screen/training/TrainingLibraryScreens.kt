package com.qingji.app.ui.screen.training

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import org.json.JSONObject
import com.qingji.app.data.local.TrainingSeed
import com.qingji.app.data.local.entity.ExerciseEntity
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
import com.qingji.app.ui.viewmodel.TrainingPlanViewModel

/**
 * 力训动作库 / 我的收藏（2026-11-24：原「动作库」改名）。
 * 同一个页面，favoritesOnly=true 时只列收藏。
 * 搜索支持中文名、英文名、slug、肌群、器械；分类胶囊复用 7 类。
 * 有氧动作（CARDIO）已移到「有氧动作库」，这里只列非有氧动作。
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

    // 力训库只显示非 CARDIO；有氧项统一从「有氧动作库」进
    val source = (if (favoritesOnly) favorites else all)
        .filterNot { com.qingji.app.domain.AerobicLibrary.isAerobic(it) }
    val q = query.trim()
    val shown = source.filter { e ->
        (category == null || e.category == category) &&
            (q.isBlank() || e.name.contains(q, ignoreCase = true) ||
                e.englishName.contains(q, ignoreCase = true) ||
                e.slug.contains(q, ignoreCase = true) ||
                e.primaryMuscles.contains(q, ignoreCase = true) ||
                e.secondaryMuscles.contains(q, ignoreCase = true) ||
                e.equipment.contains(q, ignoreCase = true))
    }

    Column(modifier = modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(
            title = if (favoritesOnly) "我的动作收藏" else "力训动作库",
            onBack = onBack
        )
        // v1.4 性能专项（工单 11）：旧写法 Column+verticalScroll 会把 302 张动作卡
        // 全部组合出来再滚动，进页面和来回切分类都明显卡顿；
        // LazyColumn 视口外不组合，图片也在可见时才加载。
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            item { Spacer(Modifier.height(10.dp)) }
            if (message != null) {
                item {
                    Text(message.orEmpty(), fontSize = TextSizes.Caption, color = QingJiGreen)
                    Spacer(Modifier.height(6.dp))
                }
            }
            // 搜索框
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
                        if (query.isEmpty()) Text("搜中文名 / 英文名 / 肌群 / 器械", fontSize = TextSizes.Body, color = TextSecondary)
                        inner()
                    }
                )
            }
            item {
                Spacer(Modifier.height(10.dp))
                MuscleCategoryTabs(selected = category, onSelect = { category = it })
                Spacer(Modifier.height(10.dp))
            }

            if (shown.isEmpty()) {
                item {
                    SectionCard {
                        Text(
                            if (favoritesOnly) "还没有收藏动作。在动作详情里点「收藏」就会出现在这里。"
                            else "没找到动作。可以回训练页用「自己加一个」新建。",
                            fontSize = TextSizes.Body, color = TextSecondary
                        )
                    }
                }
            } else {
                items(
                    count = shown.size,
                    key = { i -> shown[i].uuid },
                    contentType = { "exercise_card" }
                ) { i ->
                    val e = shown[i]
                    SectionCard(
                        modifier = Modifier.clickable { onOpenDetail(e.uuid) },
                        contentPadding = Dimens.CardPaddingCompact
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // V2：左侧 54dp 统一圆角浅底板 + 本地 SVG 首帧。
                            // 自建动作 / 图片加载失败时用动作名首字占位，卡片高度不变。
                            val media = ExerciseMediaMapping.forEntity(e.uuid, e.assetPath)
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(Dimens.ItemCorner))
                                    .background(QingJiGreenLight),
                                contentAlignment = Alignment.Center
                            ) {
                                ExerciseMediaImage(
                                    media = media,
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Text(
                                        e.name.take(1),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = QingJiGreenDark
                                    )
                                }
                            }
                            Spacer(Modifier.size(10.dp))
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
            item { Spacer(Modifier.height(Dimens.FabSpace)) }
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
    // 有氧库里的「记录有氧」预设项是内存实体（uuid 带 preset: 前缀），不在动作库表里；
    // 详情页按需读预设并转换，让两套数据源统一走同一详情流程。
    val exerciseVm: com.qingji.app.ui.viewmodel.ExerciseViewModel = viewModel()
    val presets by exerciseVm.presets.collectAsState()
    val extra = remember(presets) {
        presets.map(com.qingji.app.domain.AerobicLibrary::fromPreset)
    }
    val e = (all + extra).firstOrNull { it.uuid == uuid }

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
            if (e.englishName.isNotBlank()) {
                Text(e.englishName, fontSize = TextSizes.Body, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
            }
            // V2：原 130dp 文字占位换成 190dp 动作插画（本地 SVG 首帧）。
            // 透明底、完整显示、不裁切不拉伸；自建动作 / 加载失败回退原来的文字占位。
            val media = ExerciseMediaMapping.forEntity(e.uuid, e.assetPath)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(Dimens.CardCorner))
                    .background(QingJiGreenLight),
                contentAlignment = Alignment.Center
            ) {
                ExerciseAnimatedMedia(
                    media = media,
                    modifier = Modifier.fillMaxWidth().height(190.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            TrainingSeed.CATEGORY_LABELS.firstOrNull { it.first == e.category }?.second ?: "动作",
                            fontSize = 28.sp, fontWeight = FontWeight.Bold, color = QingJiGreen
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("暂无动作图示", fontSize = TextSizes.Tiny, color = TextSecondary)
                    }
                }
            }
            // 素材来源按逐条 attribution 显示：302 条 Workout Guide 走 CC BY-SA，
            // 11-24 新增 22 条是 AI 生成 PNG，不能冒充 CC BY-SA 署名。
            if (media != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "素材来源：${attributionText(media.attribution)}；动作示意仅供参考",
                    fontSize = TextSizes.Tiny, color = TextSecondary
                )
            }
            Spacer(Modifier.height(12.dp))

            // 记录有氧预设项：没有图示/步骤，也不写动作库，给一句来源说明占位
            val presetHint = aerobicPresetHint(e)
            if (presetHint.isNotBlank()) {
                SectionCard {
                    Text(presetHint, fontSize = TextSizes.Body, color = TextSecondary)
                }
                Spacer(Modifier.height(10.dp))
            }

            SectionCard {
                InfoLine("英文名", e.englishName.ifBlank { "—" })
                InfoLine("主肌群", e.primaryMuscles.ifBlank { "未填" })
                InfoLine("辅助肌群", e.secondaryMuscles.ifBlank { "未填" })
                InfoLine("器械", e.equipment.ifBlank { "徒手 / 不限" })
                InfoLine("分类", TrainingSeed.CATEGORY_LABELS.firstOrNull { it.first == e.category }?.second ?: "其它")
                InfoLine("训练类型", e.exerciseType.ifBlank { "未标注" })
                InfoLine("动作属性", if (e.isStretch) "拉伸类动作" else "训练类动作")
                InfoLine("收藏状态", if (e.isFavorite) "已收藏" else "未收藏")
            }
            if (media != null) {
                Spacer(Modifier.height(10.dp))
                SectionCard {
                    InfoLine("素材署名", attributionText(media.attribution))
                }
            }
            Spacer(Modifier.height(10.dp))
            SectionCard {
                Text("动作要点", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                val steps = e.steps.split("\n").filter { it.isNotBlank() }
                if (steps.isEmpty()) {
                    Text("上游未提供训练要点，保持空白，不生成训练指导。",
                        fontSize = TextSizes.Body, color = TextSecondary)
                } else {
                    steps.forEach { step ->
                        Text("· $step", fontSize = TextSizes.Body, color = TextPrimary,
                            lineHeight = 21.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
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
            // 预设项只在内存里，收藏不能写动作库，按钮禁用并说明
            OutlinedButton(
                onClick = { vm.toggleFavorite(e.uuid, !e.isFavorite) },
                enabled = com.qingji.app.domain.AerobicLibrary.presetIdOf(e.uuid) == null,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (com.qingji.app.domain.AerobicLibrary.presetIdOf(e.uuid) != null) "预设项不可收藏"
                    else if (e.isFavorite) "取消收藏" else "收藏",
                    fontSize = TextSizes.Body
                )
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

/**
 * 素材署名逐条解析：302 条 Workout Guide 用原授权；11-24 新增 22 条是
 * AI 生成 PNG，明确标 AI-generated，绝不泛化成 CC BY-SA 4.0。
 */
internal fun attributionText(raw: String): String {
    if (raw.isBlank()) return "未标注"
    val json = try { JSONObject(raw) } catch (_: Exception) { return "未标注" }
    val creator = json.optString("creator").ifBlank { "未标注" }
    val license = json.optString("license").ifBlank { "" }
    return when {
        license.equals("AI-generated", ignoreCase = true) ->
            "$creator · AI 生成（无 CC 授权）"
        creator.contains("Everkinetic", ignoreCase = true) ->
            "$creator · CC BY-SA 4.0"
        license.contains("CC BY-SA", ignoreCase = true) ->
            "$creator · CC BY-SA 4.0"
        else -> "$creator · ${license.ifBlank { "未标注" }}"
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

/**
 * V2：动作插画的唯一加载入口（动作库列表 + 详情页共用）。
 *
 * 两个关键点：
 * 1) 上游素材是**纯白剪影**（fill="#fff"）+ 透明背景。直接贴在浅绿底板上
 *    等于隐形，所以这里用 ColorFilter.tint 在绘制层把它染成主题深色，
 *    **原始 SVG 一个字节都不改**，版权/署名信息保持完整。
 *    QingJiGreenDark 会随主题走：浅色模式是深绿、深色模式是浅绿，
 *    而底板 QingJiGreenLight 正好相反，两种模式下都保证看得见。
 * 2) onError 把失败状态记住，回退到调用方给的占位，页面不会崩、不会空白。
 *    资产全在 assets/ 里，走 file:///android_asset，完全离线、不依赖网络。
 */
@Composable
internal fun ExerciseMediaImage(
    media: ExerciseMediaMapping.ExerciseMedia?,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit
) {
    if (media == null) {
        fallback()
        return
    }
    var failed by remember(media.slug) { mutableStateOf(false) }
    if (failed) {
        fallback()
        return
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            // 走三帧体系的首帧；老单帧动作（legacy）自动回退旧目录，见 frameUri
            .data(media.frameUri(1))
            .decoderFactory(SvgDecoder.Factory())
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(QingJiGreenDark, BlendMode.SrcIn),
        onError = { failed = true },
        modifier = modifier
    )
}

/**
 * 详情页三帧连续播放：1→2→3→2，每帧 420ms 循环。
 * 用 produceState 驱动帧序号，离开详情页时 Compose 自动取消协程，
 * 离开详情页时 Compose 自动取消协程，不会在后台继续刷新；
 * 列表卡片仍走静态首帧，避免滚动时几十张卡片同时播放。
 * 加载失败先退回首帧，首帧也失败才走默认图标，不让一张坏图拖垮整个列表。
 */
@Composable
private fun ExerciseAnimatedMedia(
    media: ExerciseMediaMapping.ExerciseMedia?,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit
) {
    if (media == null) {
        fallback()
        return
    }
    val slug = media.slug
    var failed by remember(slug) { mutableStateOf(false) }
    var useFirstFrame by remember(slug) { mutableStateOf(false) }
    if (failed) {
        fallback()
        return
    }
    val frame by produceState(initialValue = ExerciseMediaMapping.PLAY_SEQUENCE[0], key1 = slug) {
        val seq = ExerciseMediaMapping.PLAY_SEQUENCE
        var i = 0
        while (true) {
            delay(ExerciseMediaMapping.FRAME_DURATION_MS)
            i = (i + 1) % seq.size
            value = seq[i]
        }
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            // 某帧加载失败先退回首帧；首帧仍失败才走 fallback，不让列表崩溃
            .data(media.frameUri(if (useFirstFrame) 1 else frame))
            .decoderFactory(SvgDecoder.Factory())
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(QingJiGreenDark, BlendMode.SrcIn),
        onError = {
            if (useFirstFrame) failed = true else useFirstFrame = true
        },
        modifier = modifier
    )
}
