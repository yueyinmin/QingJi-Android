package com.qingji.app.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qingji.app.data.local.entity.Food
import com.qingji.app.data.local.entity.UserFoodGroup
import com.qingji.app.data.photo.FoodGroupCoverStore
import com.qingji.app.domain.AllergenRules
import com.qingji.app.domain.FoodIcon
import com.qingji.app.ui.components.AppBottomSheet
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.SwipeToRevealDelete
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.FoodLibraryViewModel
import kotlinx.coroutines.launch

/**
 * ============================================================
 * 自定义食物库（v1.6 食物库整体重构 · TASK-04）
 * ------------------------------------------------------------
 * 嵌在食物库页的「自定义」页签里，不再是单列混排。结构：
 *   顶部新增按钮（按当前页签：食物列表→新增食物；食物组→新建组）
 *   两个胶囊：【食物列表】【食物组】
 *   胶囊下方就地搜索框：直接过滤当前页签的数据（不跳独立搜索页）
 *   食物列表：拼音/时间排序 + 中文/拼音/别名搜索 + 编辑/删除/收藏/加入组
 *   食物组：创建/编辑/删除/封面/查看组内食物/增删成员，多对多，删组不删食物
 *
 * 数据全部来自 FoodLibraryViewModel。不复制收藏、营养计算、记餐逻辑，
 * 点食物走 onPick 复用主页面同一个「加到哪一餐」弹窗。
 * ============================================================
 */
@Composable
fun CustomFoodLibraryScreen(
    vm: FoodLibraryViewModel,
    allergens: List<String>,
    onAddCustomFood: () -> Unit,
    onEditCustomFood: (Long) -> Unit,
    onPick: (Food) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coverStore = remember { FoodGroupCoverStore(context) }

    val userFoods by vm.userFoods.collectAsState()
    val groups by vm.foodGroups.collectAsState()
    val groupCounts by vm.groupCounts.collectAsState()
    val foodGroupCounts by vm.foodGroupCounts.collectAsState()
    val expandedGroupId by vm.expandedGroupId.collectAsState()
    val expandedIds by vm.expandedGroupFoodIds.collectAsState()

    var subTab by remember { mutableStateOf(0) } // 0 食物列表 / 1 食物组
    var query by remember { mutableStateOf("") }
    var sortByPinyin by remember { mutableStateOf(true) }

    var showGroupEditor by remember { mutableStateOf<UserFoodGroup?>(null) } // null=不显示
    var showCreateGroup by remember { mutableStateOf(false) }
    var addToFood by remember { mutableStateOf<Food?>(null) }
    var addMembersToGroup by remember { mutableStateOf<UserFoodGroup?>(null) }
    var pendingCoverUri by remember { mutableStateOf("") }

    val pickCoverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coverStore.saveFromUri(uri)?.let { pendingCoverUri = it }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // QJ-FOOD-UI-003：删除页面内的大号「新增自定义食物」按钮。
        // 新增食物统一走标题栏右上角 + （见 FoodLibraryScreen）；
        // 食物组是另一功能，工单未要求删除，改为食物组页内的轻量入口（见下方计数行）。

        // 两个胶囊
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding, vertical = 6.dp)
                .clip(RoundedCornerShape(Dimens.PillCorner))
                .background(CardWhite)
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(0 to s("食物列表"), 1 to s("食物组")).forEach { (idx, label) ->
                val selected = subTab == idx
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = if (selected) Color.White else TextSecondary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Dimens.PillCorner))
                        .background(if (selected) QingJiGreen else Color.Transparent)
                        .clickable { subTab = idx; query = ""; vm.expandGroup(0L) }
                        .padding(vertical = 8.dp)
                )
            }
        }

        // 就地搜索框（直接过滤当前页）
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = {
                Text(
                    text = if (subTab == 0) s("搜自定义食物：名称/拼音/别名") else s("搜食物组"),
                    fontSize = 12.sp
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding, vertical = 4.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CardWhite,
                unfocusedContainerColor = CardWhite,
                focusedIndicatorColor = QingJiGreen,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        if (subTab == 0) {
            // ---------- 食物列表 ----------
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = s("共 ${userFoods.size} 个自定义食物"), fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                Text(
                    text = if (sortByPinyin) s("按拼音 ▾") else s("按时间 ▾"),
                    fontSize = 12.sp, color = QingJiGreen,
                    modifier = Modifier.clickable { sortByPinyin = !sortByPinyin }.padding(4.dp)
                )
            }
            val q = query.trim()
            val matched = userFoods.filter { food ->
                q.isBlank() || food.name.contains(q, true) ||
                    food.pinyin.startsWith(q, true) ||
                    food.pinyinFull.startsWith(q, true) ||
                    food.aliases.split("|").any { it.isNotBlank() && it.contains(q, true) }
            }.let { list ->
                if (sortByPinyin) list.sortedBy { it.pinyin.ifBlank { it.name } }
                else list.sortedByDescending { it.createdAt }
            }

            if (userFoods.isEmpty()) {
                // QJ-FOOD-UI-003：空态改为简洁提示，入口指向标题栏右上角 +
                HintBox(text = s("点击右上角 + 添加自定义食物"))
            } else if (matched.isEmpty()) {
                HintBox(text = "没有匹配「$query」的自定义食物")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding)) {
                    items(matched, key = { it.id }) { food ->
                        CustomFoodRow(
                            food = food,
                            risky = AllergenRules.isRisky(food.name, allergens),
                            groupCount = foodGroupCounts[food.id] ?: 0,
                            onFavorite = { vm.toggleFavorite(food) },
                            onPick = { onPick(food) },
                            onEdit = { onEditCustomFood(food.id) },
                            onDelete = { vm.deleteCustomFood(food) },
                            onAddToGroup = { addToFood = food }
                        )
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        } else {
            // ---------- 食物组 ----------
            val q = query.trim()
            val expanded = groups.firstOrNull { it.id == expandedGroupId }

            if (expanded != null) {
                GroupDetailView(
                    group = expanded,
                    memberIds = expandedIds,
                    allFoods = userFoods,
                    allergens = allergens,
                    onBack = { vm.expandGroup(0L) },
                    onPick = onPick,
                    onRemove = { foodId -> scope.launch { vm.removeFoodFromGroup(expanded.id, foodId) } },
                    onAddMembers = { addMembersToGroup = expanded }
                )
            } else {
                val matchedGroups = groups.filter { q.isBlank() || it.name.contains(q, true) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = s("共 ${groups.size} 个食物组"),
                        fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1f)
                    )
                    // QJ-FOOD-UI-003：大按钮删除后，食物组的轻量新建入口（功能保留）
                    Text(
                        text = "+ 新建组",
                        fontSize = 12.sp, color = QingJiGreen,
                        modifier = Modifier
                            .clickable { pendingCoverUri = ""; showCreateGroup = true }
                            .padding(4.dp)
                    )
                }
                if (groups.isEmpty()) {
                    HintBox(text = s("还没有食物组，点上方「+ 新建组」把常一起吃的食物归到一组"))
                } else if (matchedGroups.isEmpty()) {
                    HintBox(text = "没有匹配「$query」的食物组")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding)) {
                        items(matchedGroups, key = { it.id }) { g ->
                            FoodGroupRow(
                                group = g,
                                count = groupCounts[g.id] ?: 0,
                                onClick = { vm.expandGroup(g.id) },
                                onEdit = { pendingCoverUri = g.coverUri; showGroupEditor = g },
                                onDelete = { scope.launch { vm.deleteFoodGroup(g.id) } }
                            )
                        }
                        item { Spacer(Modifier.height(90.dp)) }
                    }
                }
            }
        }
    }

    // ---------- 新建组 ----------
    if (showCreateGroup) {
        GroupEditorSheet(
            group = null, coverUri = pendingCoverUri,
            onCoverPick = { pickCoverLauncher.launch("image/*") },
            onDismiss = { showCreateGroup = false; pendingCoverUri = "" },
            onSave = { name ->
                scope.launch {
                    val newId = vm.createFoodGroup(name, pendingCoverUri)
                    if (newId > 0) vm.expandGroup(newId)
                    showCreateGroup = false; pendingCoverUri = ""
                }
            }
        )
    }

    // ---------- 编辑组 ----------
    showGroupEditor?.let { g ->
        GroupEditorSheet(
            group = g, coverUri = pendingCoverUri,
            onCoverPick = { pickCoverLauncher.launch("image/*") },
            onDismiss = { showGroupEditor = null; pendingCoverUri = "" },
            onSave = { name ->
                scope.launch {
                    vm.updateFoodGroup(g.copy(name = name, coverUri = pendingCoverUri))
                    showGroupEditor = null; pendingCoverUri = ""
                }
            }
        )
    }

    // ---------- 给某食物选组 ----------
    addToFood?.let { food ->
        AddToGroupsSheet(vm = vm, food = food, groups = groups, onDismiss = { addToFood = null })
    }

    // ---------- 给某组加成员 ----------
    addMembersToGroup?.let { g ->
        AddMembersSheet(
            vm = vm, group = g, allFoods = userFoods, allergens = allergens,
            onDismiss = { addMembersToGroup = null }
        )
    }
}

/** 自定义食物行：图标 + 名字 + 归入组数 + 收藏/编辑 + 加入组，左滑删除。 */
@Composable
private fun CustomFoodRow(
    food: Food,
    risky: Boolean,
    groupCount: Int,
    onFavorite: () -> Unit,
    onPick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddToGroup: () -> Unit
) {
    SwipeToRevealDelete(onDelete = onDelete) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.ItemCorner))
                .background(CardWhite)
                .clickable { onPick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(Dimens.TinyCorner)).background(PageBackground),
                contentAlignment = Alignment.Center
            ) { Text(FoodIcon.emoji(food.name), fontSize = 19.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(food.name, fontSize = 14.sp, color = TextPrimary)
                    if (risky) {
                        Spacer(Modifier.width(5.dp))
                        Text("含过敏原", fontSize = 9.sp, color = Color.White,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Color(0xFFE53935)).padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${food.kcalPer100g.toInt()} 千卡/100g" +
                        if (groupCount > 0) " · 已归入 $groupCount 组" else "",
                    fontSize = 11.sp, color = TextSecondary
                )
            }
            // 加入组
            Box(Modifier.size(32.dp).clip(CircleShape).clickable { onAddToGroup() }, contentAlignment = Alignment.Center) {
                Text("📁", fontSize = 15.sp)
            }
            // 收藏
            Box(Modifier.size(32.dp).clip(CircleShape).clickable { onFavorite() }, contentAlignment = Alignment.Center) {
                Text(text = if (food.favorite) "♥" else "♡", fontSize = 17.sp,
                    color = if (food.favorite) QingJiGreen else TextSecondary)
            }
            // 编辑
            Icon(Icons.Filled.Edit, contentDescription = s("编辑"), tint = TextSecondary,
                modifier = Modifier.size(20.dp).clickable { onEdit() })
        }
    }
    Spacer(Modifier.height(6.dp))
}

/** 食物组行：封面/占位 + 名称 + 数量 + 编辑/删除。 */
@Composable
private fun FoodGroupRow(
    group: UserFoodGroup,
    count: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .clip(RoundedCornerShape(Dimens.ItemCorner)).background(CardWhite)
            .clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(PageBackground), contentAlignment = Alignment.Center) {
            if (group.coverUri.isNotBlank()) {
                AsyncImage(model = group.coverUri, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = QingJiGreen, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(group.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text("$count 个食物", fontSize = 11.sp, color = TextSecondary)
        }
        Icon(Icons.Filled.Edit, contentDescription = s("编辑"), tint = TextSecondary,
            modifier = Modifier.size(20.dp).clickable { onEdit() })
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.Delete, contentDescription = s("删除"), tint = Color(0xFFE53935),
            modifier = Modifier.size(20.dp).clickable { onDelete() })
    }
}

/** 组内详情：返回 + 成员列表（可移除、可点去加餐）+ 添加食物。 */
@Composable
private fun GroupDetailView(
    group: UserFoodGroup,
    memberIds: List<Long>,
    allFoods: List<Food>,
    allergens: List<String>,
    onBack: () -> Unit,
    onPick: (Food) -> Unit,
    onRemove: (Long) -> Unit,
    onAddMembers: () -> Unit
) {
    val members = allFoods.filter { it.id in memberIds }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s("返回"), tint = TextPrimary,
                modifier = Modifier.size(22.dp).clickable { onBack() })
            Spacer(Modifier.width(10.dp))
            Text("${group.name}（${members.size}）", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding, vertical = 4.dp)
                .clip(RoundedCornerShape(Dimens.CardCorner)).background(QingJiGreen)
                .clickable { onAddMembers() }.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(s("往这个组添加食物"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (members.isEmpty()) {
            HintBox(text = "这个组还是空的。点上面按钮把自定义食物加进来。")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding)) {
                items(members, key = { it.id }) { food ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(Dimens.ItemCorner)).background(CardWhite)
                            .clickable { onPick(food) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(FoodIcon.emoji(food.name), fontSize = 19.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(food.name, fontSize = 14.sp, color = TextPrimary)
                            Text("${food.kcalPer100g.toInt()} 千卡/100g", fontSize = 11.sp, color = TextSecondary)
                        }
                        Icon(Icons.Filled.Delete, contentDescription = s("移出组"),
                            tint = Color(0xFFE53935), modifier = Modifier.size(20.dp).clickable { onRemove(food.id) })
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }
}

/** 组编辑器：填名字 + 选封面（可选）。 */
@Composable
private fun GroupEditorSheet(
    group: UserFoodGroup?,
    coverUri: String,
    onCoverPick: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(group?.name ?: "") }
    AppBottomSheet(
        title = if (group == null) s("新建食物组") else s("编辑食物组"),
        confirmText = s("保存"),
        onConfirm = { if (name.isNotBlank()) onSave(name.trim()) },
        closeOnConfirm = false,
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text(s("组名称")) }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        Text(s("封面（可选）"), fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(PageBackground), contentAlignment = Alignment.Center) {
                if (coverUri.isNotBlank()) {
                    AsyncImage(model = coverUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(s("选一张封面图"), fontSize = 13.sp, color = QingJiGreen,
                modifier = Modifier.clickable { onCoverPick() })
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** 给一个食物选组：勾选多个组，切换即时生效（多对多）。 */
@Composable
private fun AddToGroupsSheet(
    vm: FoodLibraryViewModel,
    food: Food,
    groups: List<UserFoodGroup>,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(food.id) {
        val ids: List<Long> = vm.groupsOfFoodOnce(food.id)
        current = ids.toSet()
    }
    AppBottomSheet(title = s("加入食物组"), subtitle = food.name, confirmText = s("完成"), onConfirm = onDismiss, onDismiss = onDismiss) {
        if (groups.isEmpty()) {
            HintBox(text = "还没有食物组，先去「食物组」页签新建一个。")
        } else {
            LazyColumn {
                items(groups, key = { it.id }) { g ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (g.id in current) {
                                scope.launch { vm.removeFoodFromGroup(g.id, food.id) }
                                current = current - g.id
                            } else {
                                scope.launch { vm.addFoodToGroup(g.id, food.id) }
                                current = current + g.id
                            }
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = g.id in current, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(g.name, fontSize = 14.sp, color = TextPrimary)
                    }
                }
            }
        }
    }
}

/** 给一个组加成员：列出全部自定义食物，勾选即时增删。 */
@Composable
private fun AddMembersSheet(
    vm: FoodLibraryViewModel,
    group: UserFoodGroup,
    allFoods: List<Food>,
    allergens: List<String>,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(group.id) {
        val ids: List<Long> = vm.groupFoodIds(group.id)
        current = ids.toSet()
    }
    AppBottomSheet(title = s("添加食物到「${group.name}」"), confirmText = s("完成"), onConfirm = onDismiss, onDismiss = onDismiss) {
        if (allFoods.isEmpty()) {
            HintBox(text = "还没有自定义食物可加。")
        } else {
            LazyColumn {
                items(allFoods, key = { it.id }) { food ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (food.id in current) {
                                scope.launch { vm.removeFoodFromGroup(group.id, food.id) }
                                current = current - food.id
                            } else {
                                scope.launch { vm.addFoodToGroup(group.id, food.id) }
                                current = current + food.id
                            }
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = food.id in current, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(FoodIcon.emoji(food.name), fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(food.name, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
