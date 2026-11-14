package com.qingji.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.ui.viewmodel.OnboardingViewModel
import com.qingji.app.ui.viewmodel.HomeViewModel
import com.qingji.app.ui.viewmodel.ProgressViewModel
import com.qingji.app.ui.viewmodel.DiaryViewModel
import com.qingji.app.ui.viewmodel.MineViewModel
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.ui.screen.BackupScreen
import com.qingji.app.ui.screen.AiAnalysisScreen
import com.qingji.app.ui.screen.AiConfigScreen
import com.qingji.app.ui.screen.DiaryEditScreen
import com.qingji.app.ui.screen.DiaryScreen
import com.qingji.app.ui.screen.ExerciseConfirmScreen
import com.qingji.app.ui.screen.ExercisePickerScreen
import com.qingji.app.ui.screen.FastingScreen
import com.qingji.app.ui.screen.FoodLibraryScreen
import com.qingji.app.ui.screen.WidgetSettingsScreen
import com.qingji.app.ui.screen.FoodPreferenceScreen
import com.qingji.app.ui.screen.OnboardingScreen
import com.qingji.app.ui.screen.HomeScreen
import com.qingji.app.ui.screen.MealDetailScreen
import com.qingji.app.ui.screen.MineScreen
import com.qingji.app.ui.screen.ProfileScreen
import com.qingji.app.ui.screen.ProgressScreen
import com.qingji.app.ui.screen.SettingsScreen
import com.qingji.app.ui.screen.record.AddRecordSheet
import com.qingji.app.ui.screen.record.BarcodeScreen
import com.qingji.app.ui.screen.record.ImageRecordScreen
import com.qingji.app.ui.screen.record.SearchFoodScreen
import com.qingji.app.ui.screen.record.TextAiScreen
import com.qingji.app.ui.screen.record.VoiceRecordScreen
import com.qingji.app.ui.screen.record.WeightScreen
import com.qingji.app.ui.screen.training.TrainingActiveScreen
import com.qingji.app.ui.screen.training.TrainingHistoryScreen
import com.qingji.app.ui.screen.training.ExerciseLibraryScreen
import com.qingji.app.ui.screen.training.ExerciseDetailScreen
import com.qingji.app.ui.screen.training.TrainingPlanScreen
import com.qingji.app.ui.screen.training.PersonalBestScreen
import com.qingji.app.ui.viewmodel.TrainingPlanViewModel
import com.qingji.app.ui.viewmodel.TrainingViewModel
import com.qingji.app.ui.viewmodel.SelfDisciplineViewModel
import com.qingji.app.ui.screen.selfdiscipline.SelfDisciplineDashboardScreen
import com.qingji.app.ui.screen.selfdiscipline.SelfDisciplineGoalsScreen
import com.qingji.app.ui.screen.selfdiscipline.SelfDisciplineGoalEditScreen
import com.qingji.app.ui.screen.selfdiscipline.SelfDisciplineCheckinScreen
import com.qingji.app.ui.screen.selfdiscipline.SelfDisciplineUrgeScreen
import com.qingji.app.ui.screen.selfdiscipline.SelfDisciplineDeviationScreen
import com.qingji.app.ui.screen.selfdiscipline.SelfDisciplineHistoryScreen
import com.qingji.app.ui.screen.selfdiscipline.SelfDisciplineSettingsScreen
import com.qingji.app.ui.theme.PageBackground

/**
 * ============================================================
 * App 的骨架：底部导航 + 页面容器 + 记录弹层
 * ------------------------------------------------------------
 * M1 的变化：
 * 1. 中间「+」不再是 Toast，改成真正的底部弹层，能跳到三个记录页。
 * 2. 加了 5 个全屏子页面（搜索 / 文字 AI / 体重 / AI 配置 / 个人资料）。
 * 3. 全屏子页面统一隐藏底部导航栏，返回键和左上角箭头都能回退。
 * ============================================================
 */
@Composable
fun QingJiAppRoot(
    /** 桌面小组件要求直达的页面路由，null 表示正常从首页打开。 */
    startTarget: String? = null,
    /** 每次点小组件都会 +1，用来强制触发一次跳转（同一按钮连点两次也能跳）。 */
    startTargetRequestId: Long = 0L,
    /** 目标用完后立刻回告 MainActivity 清空，避免残留目标被反复消费。 */
    onTargetConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()

    // tabfix：Tab 切换会销毁旧页面的 Composable（popUpTo 明确不带 saveState，
    // 见下方 v3.2.3 的注释），页面内部的 rememberScrollState 会跟着重建、回到顶部。
    // 把四个 Tab 的滚动状态提到这里 —— Root 永远不会被导航销毁，
    // 切回来时 ScrollState 对象还在，滚动位置自然保留。
    val tabScrollStates = remember {
        QingJiTab.values().associateWith { ScrollState(0) }
    }

    // tabfix：状态栏高度在 Scaffold 外面读，这里的 insets 还没被 Scaffold 消耗。
    // 首页要靠它把顶部渐变往上画回状态栏区域（见 HomeScreen）。
    val statusBarTopDp = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }

    // 是否展开「记一笔」底部弹层
    var showAddSheet by remember { mutableStateOf(false) }

    // 小组件点进来 → 直接跳到目标页。
    // v3.2.3：不管最后有没有真的跳，都要把目标「消费掉」并回告 MainActivity 清空。
    // 以前 target=="home" 或空值时直接 return，目标就永远留在 Activity 里；
    // 之后只要 Root 因换主题、转屏、设置变化重新组合，这段就会再跑一次，
    // 用户看到的就是「点首页，最后却停在别的页」。
    LaunchedEffect(startTarget, startTargetRequestId) {
        val target = startTarget ?: return@LaunchedEffect
        if (target != QingJiTab.Home.route && target.isNotBlank()) {
            // 断食常驻通知专项（2026-11-11）：冷启动带 target（通知 / 小组件）时，
            // NavHost 的 graph 可能还没挂上，直接 navigate 会抛
            // IllegalArgumentException: Navigation graph has not been set → 整个 App 崩掉。
            // 以前小组件多数在 App 已运行时才点，所以没暴露。
            // 现在等 currentDestination 出现再跳，最多等 15 秒；超时就放弃跳转（宁可停首页，也不崩）。
            var waited = 0L
            while (navController.currentDestination == null && waited < 15_000L) {
                kotlinx.coroutines.delay(16L)
                waited += 16L
            }
            runCatching {
                navController.navigate(target) { launchSingleTop = true }
            }
        }
        // 放在最后调用：它把 startTarget 变成 null，会让本 LaunchedEffect
        // 重启一次；重启后 target 已是 null，第一行就直接返回，不会重复跳。
        onTargetConsumed()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: QingJiTab.Home.route
    val showBottomBar = currentRoute !in QingJiRoutes.fullScreenRoutes

    // 底部导航要不要显示某个 Tab、首页要不要显示某些卡片，都从这里读
    val context = LocalContext.current
    val settings by remember { SettingsStore.observe(context) }
        .collectAsState(initial = QingJiSettings())
    val scope = rememberCoroutineScope()
    val appRepo = remember { com.qingji.app.data.repository.QingJiRepository(context) }

    // ============================================================
    // 一级页面左右滑动切换（UI-NAV-006）
    // ------------------------------------------------------------
    // 关键设计：
    // 1) 只有一个切换入口 goToTab()，底部点击和滑动都走它，动画方向一致；
    // 2) 手势挂在整个页面容器上，但不消费事件 —— 子组件（LazyRow、图表、
    //    月历）先拿到事件并消费，我们检测到 isConsumed 就放弃，天然不打架；
    // 3) 只在四个一级 Tab 上启用；详情页 tabIndex = -1，直接不响应；
    // 4) 左右各留 24dp 给系统返回手势，避免抢系统滑动；
    // 5) 垂直位移一旦超过 touchSlop 就判定为滚动，整次手势不再考虑切换。
    // ============================================================
    val tabList = QingJiTab.values()
    val tabIndex = tabList.indexOfFirst { it.route == currentRoute }
    var navDirection by remember { mutableIntStateOf(0) }   // 1=去右边, -1=去左边, 0=淡入
    var tabSwitching by remember { mutableStateOf(false) }
    // 注意：这里故意不看 settings.swipeNavigate。
    // 只要在一级页，就始终拦截「横向拖拽」——否则拖到卡片上会被当成点击，
    // 顺手把用餐详情之类的页面打开（实测踩到）。开关只决定「拦下来之后切不切」。
    val swipeEnabled = !showAddSheet && tabIndex >= 0 && !tabSwitching

    fun goToTab(tab: QingJiTab, direction: Int) {
        if (tabSwitching) return
        navDirection = direction
        tabSwitching = true
        navController.navigate(tab.route) {
            popUpTo(QingJiTab.Home.route) { saveState = false }
            launchSingleTop = true
        }
        scope.launch {
            // 动画 250ms，稍微多留一点再解锁，防止连点连切
            kotlinx.coroutines.delay(280)
            tabSwitching = false
        }
    }

    val edgeGuardPx = with(LocalDensity.current) { 24.dp.toPx() }
    val triggerPx = with(LocalDensity.current) { 72.dp.toPx() }
    val flickPx = with(LocalDensity.current) { 28.dp.toPx() }

    // ============================================================
    // 首次启动引导（v1.5：10 步精简成 5 步）
    // ------------------------------------------------------------
    // 以前这里是三个独立协程（存档案 / 存偏好 / 写完成标记）各跑各的，
    // 「完成标记」经常最先落库，界面先切到首页、档案却还没写完。
    // 现在只调 viewModel 里一条按顺序执行的流程，
    // 档案和设置都成功才会写 ONBOARDING_DONE，settings 流再自动把界面切走。
    // ============================================================
    if (!settings.onboardingDone) {
        val onboardingVm: OnboardingViewModel = viewModel()
        val saving by onboardingVm.saving.collectAsState()
        val onboardingError by onboardingVm.error.collectAsState()
        OnboardingScreen(
            saving = saving,
            saveError = onboardingError,
            onSubmit = { answers -> onboardingVm.submit(answers) }
        )
        return
    }

    // tabfix：四个 Tab 的 ViewModel 提到 Root（= Activity 作用域）创建。
    // 原来页面里 viewModel() 的默认作用域是 NavBackStackEntry，切 Tab 时
    // popUpTo 会销毁它，回来时新 VM 的 stateIn 先发 initial 空值，
    // 进展页第一帧就变成「-- kg / 待记录 / 还没有体重记录」，
    // 数据到了再跳回真实卡片 —— 录屏里那一下「内容闪跳」就是它。
    // 这里创建后传给页面，StateFlow 保留最后一次的值，切回来第一帧就是真数据。
    // VM 全是 WhileSubscribed(5_000) 且没有 init 副作用，常驻是安全的。
    val homeVm: HomeViewModel = viewModel()
    val progressVm: ProgressViewModel = viewModel()
    val diaryVm: DiaryViewModel = viewModel()
    val mineVm: MineViewModel = viewModel()

    // 训练中心的草稿状态必须在 Root（Activity）作用域共享：
    // 首页卡、日记页、进行中训练页、训练日记页读的是同一个 ViewModel，
    // 否则切一次页面就换一个实例，「进行中」状态会对不上。
    val trainingVm: TrainingViewModel = viewModel()
    /** P1：计划 / 动作库 / 个人最佳也挂在 Activity 作用域，首页和设置页读同一份。 */
    val planVm: TrainingPlanViewModel = viewModel()
    /** 自律管理：模块内所有页面共用一份（和训练中心一致，挂 Activity 作用域）。 */
    val selfDisciplineVm: SelfDisciplineViewModel = viewModel()
    /** 日记页默认进「训练日记」（主人 2026-11-13 指定）；状态放 Root，切 Tab 回来不丢。 */
    var diaryTrainingTab by remember { mutableStateOf(true) }

    // tabfix（首页→进展抖动的真正根因）：这里原来按路由切 contentWindowInsets
    // —— 首页给全 0，其他页给默认 systemBars。currentRoute 在 navigate() 那一帧
    // 就变了，可 NavHost 的转场动画才刚开始，于是：
    //   1) 内容区 top padding 从 0 瞬间变成状态栏高度，新旧两页同时被重新测量下移；
    //   2) 还没淡出的首页自己也带 statusBarsPadding，退出帧变成双重内边距；
    //   3) 内容区高度一变，slideInVertically 的 height/28 目标位移也跟着变。
    // 现在所有路由统一用 Scaffold 默认值，innerPadding 切换前后恒定；
    // 首页渐变「延伸到状态栏」改由 HomeScreen 用负 offset 补回，视觉零变化。
    // barfx v4：底栏选中回调提到 Scaffold 外——经典款（bottomBar 槽）和
    // 悬浮款（内容上层 Overlay）共用同一个入口，切换皮肤时行为完全一致。
    val onBarTabSelected: (QingJiTab) -> Unit = { tab ->
        // v3.2.3：已经在当前页就什么都不做。
        // 点击和左右滑动统一走 goToTab，动画方向按索引判断。
        if (tab.route != currentRoute) {
            val from = tabList.indexOfFirst { it.route == currentRoute }
            val to = tabList.indexOfFirst { it.route == tab.route }
            goToTab(tab, if (from >= 0 && to >= 0 && to != from) (if (to > from) 1 else -1) else 0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = PageBackground,
            // barfix：经典款进出二级页时底栏平滑收放（尺寸动画带动 innerPadding 逐帧变化，
            // 内容区跟手长回来；底边锚定，视觉上是「底栏沉下去、新页面同时补位」）。
            // 悬浮款不在这里——见 Scaffold 后面的 Overlay，它要盖在内容上，不能占布局槽。
            bottomBar = {
                AnimatedVisibility(
                    visible = showBottomBar && !settings.floatingBottomBar,
                    enter = expandVertically(
                        expandFrom = Alignment.Bottom,
                        animationSpec = tween(com.qingji.app.ui.theme.Motion.Normal, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(com.qingji.app.ui.theme.Motion.Fast)),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Bottom,
                        animationSpec = tween(com.qingji.app.ui.theme.Motion.Normal, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(com.qingji.app.ui.theme.Motion.Fast))
                ) {
                    QingJiBottomBar(
                        currentRoute = currentRoute,
                        onTabSelected = onBarTabSelected,
                        onAddClick = { showAddSheet = true },
                        settings = settings
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                        top = innerPadding.calculateTopPadding(),
                        end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                        // 悬浮款：内容一直铺到手势条上沿，Dock 盖在内容上层，两侧能看见列表；
                        // 经典款：照旧让出整个 bottomBar 的高度。
                        bottom = if (settings.floatingBottomBar)
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        else
                            innerPadding.calculateBottomPadding()
                    )
                    // 一级页面左右滑动切换（UI-NAV-006）
                    // 用系统 detectHorizontalDragGestures：
                    // · 它只在横向超过 touchSlop 后才启动，纵向滚动完全不触发；
                    // · 启动后会 consume，子组件的 clickable 收到 consumed 就取消点击，
                    //   所以「在列表行上横滑」不会顺手把页面点开（第一版踩过这个坑）；
                    // · 子组件（LazyRow / 图表）先处理事件，它们消费后这里根本不会启动，
                    //   天然做到「子组件优先」。
                    .pointerInput(swipeEnabled, tabIndex, settings.swipeNavigate) {
                        if (!swipeEnabled) return@pointerInput
                        var total = 0f
                        var blocked = false
                        var startedAt = 0L
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                total = 0f
                                blocked = offset.x < edgeGuardPx || offset.x > size.width - edgeGuardPx
                                startedAt = System.currentTimeMillis()
                            },
                            onDragCancel = { total = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                if (!blocked) {
                                    total += dragAmount
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (blocked) return@detectHorizontalDragGestures
                                // 用户在设置里关掉了滑动切换：手势照样拦截，但不翻页
                                if (!settings.swipeNavigate) return@detectHorizontalDragGestures
                                val elapsed = System.currentTimeMillis() - startedAt
                                val passed = kotlin.math.abs(total) >= triggerPx ||
                                    (kotlin.math.abs(total) >= flickPx && elapsed <= 260)
                                if (!passed) return@detectHorizontalDragGestures
                                if (total < 0 && tabIndex < tabList.lastIndex) {
                                    goToTab(tabList[tabIndex + 1], 1)
                                } else if (total > 0 && tabIndex > 0) {
                                    goToTab(tabList[tabIndex - 1], -1)
                                }
                            }
                        )
                    }
            ) {
                val tabRoutes = remember { tabList.map { it.route }.toSet() }
                NavHost(
                    navController = navController,
                    startDestination = QingJiTab.Home.route,
                    // tabfix：所有路由共用 Motion 的 150ms 原地淡入淡出，
                    // 进出同节奏、零位移，转场期间两页始终占同一块区域。
                    // 一级 Tab 之间：轻微横移 10% + 淡入淡出，方向跟着手势/点击走；
                    // 其他路由（详情、弹层页）保持原来的纯淡入，不动。
                    enterTransition = {
                        val bothTabs = initialState.destination.route in tabRoutes &&
                            targetState.destination.route in tabRoutes
                        if (bothTabs && settings.pageTransition && navDirection != 0) {
                            slideInHorizontally(
                                animationSpec = tween(250, easing = FastOutSlowInEasing),
                                initialOffsetX = { (it * 0.10f * navDirection).toInt() }
                            ) + fadeIn(tween(250))
                        } else if (bothTabs) {
                            fadeIn(tween(150))
                        } else {
                            com.qingji.app.ui.theme.Motion.pageEnter
                        }
                    },
                    exitTransition = {
                        val bothTabs = initialState.destination.route in tabRoutes &&
                            targetState.destination.route in tabRoutes
                        if (bothTabs && settings.pageTransition && navDirection != 0) {
                            slideOutHorizontally(
                                animationSpec = tween(250, easing = FastOutSlowInEasing),
                                targetOffsetX = { (-it * 0.10f * navDirection).toInt() }
                            ) + fadeOut(tween(250))
                        } else if (bothTabs) {
                            fadeOut(tween(150))
                        } else {
                            com.qingji.app.ui.theme.Motion.pageExit
                        }
                    },
                    popEnterTransition = { com.qingji.app.ui.theme.Motion.popEnter },
                    popExitTransition = { com.qingji.app.ui.theme.Motion.popExit }
                ) {
                    composable(QingJiTab.Home.route) {
                        HomeScreen(
                            onAddRecord = { showAddSheet = true },
                            onOpenAnalysis = { navController.navigate(QingJiRoutes.AiAnalysis) },
                            onOpenWeight = { navController.navigate(QingJiRoutes.Weight) },
                            onOpenImage = { navController.navigate(QingJiRoutes.ImageRecord) },
                            onOpenMealDetail = { date, meal ->
                                navController.navigate(QingJiRoutes.mealDetail(date, meal))
                            },
                            onOpenFasting = { navController.navigate(QingJiRoutes.Fasting) },
                            onOpenFoodLibrary = { navController.navigate(QingJiRoutes.FoodLibrary) },
                            onOpenTraining = { navController.navigate(QingJiRoutes.TrainingHistory) },
                            onOpenSelfDiscipline = { navController.navigate(QingJiRoutes.SelfDiscipline) },
                            onOpenExercise = { navController.navigate(QingJiRoutes.ExercisePicker) },
                            onSelfDisciplineCheckin = {
                                val id = selfDisciplineVm.primaryGoal.value?.id
                                navController.navigate(
                                    if (id != null) QingJiRoutes.selfDisciplineCheckin(id) else QingJiRoutes.SelfDiscipline
                                )
                            },
                            onSelfDisciplineUrge = {
                                val id = selfDisciplineVm.primaryGoal.value?.id
                                navController.navigate(
                                    if (id != null) QingJiRoutes.selfDisciplineUrge(id) else QingJiRoutes.SelfDiscipline
                                )
                            },
                            onSelfDisciplineHistory = {
                                val id = selfDisciplineVm.primaryGoal.value?.id
                                navController.navigate(
                                    if (id != null) QingJiRoutes.selfDisciplineHistory(id) else QingJiRoutes.SelfDiscipline
                                )
                            },
                            trainingVm = trainingVm,
                            planVm = planVm,
                            selfDisciplineVm = selfDisciplineVm,
                            progressVm = progressVm,
                            statusBarTop = statusBarTopDp,
                            scrollState = tabScrollStates.getValue(QingJiTab.Home),
                            vm = homeVm
                        )
                    }
                    composable(QingJiTab.Progress.route) {
                        ProgressScreen(
                            onOpenWeight = { navController.navigate(QingJiRoutes.Weight) },
                            onOpenImage = { navController.navigate(QingJiRoutes.ImageRecord) },
                            // v3.2.4：悬浮栏第二颗换成食物库，这里接上跳转
                            onOpenFoodLibrary = { navController.navigate(QingJiRoutes.FoodLibrary) },
                            // v1.0.6：运动卡片的「+ 记一次」走二级页，才能填步数 / 公里
                            onOpenExercise = { navController.navigate(QingJiRoutes.ExercisePicker) },
                            scrollState = tabScrollStates.getValue(QingJiTab.Progress),
                            vm = progressVm
                        )
                    }
                    composable(QingJiTab.Diary.route) {
                        DiaryScreen(
                            onOpenEdit = { id -> navController.navigate(QingJiRoutes.diaryEdit(id)) },
                            onOpenWeight = { navController.navigate(QingJiRoutes.Weight) },
                            onOpenImage = { navController.navigate(QingJiRoutes.ImageRecord) },
                            onOpenFoodLibrary = { navController.navigate(QingJiRoutes.FoodLibrary) },
                            trainingTab = diaryTrainingTab,
                            onTrainingTabChange = { diaryTrainingTab = it },
                            onStartTraining = { navController.navigate(QingJiRoutes.TrainingActive) },
                            onEditTraining = { navController.navigate(QingJiRoutes.TrainingActive) },
                            trainingVm = trainingVm,
                            progressVm = progressVm,
                            scrollState = tabScrollStates.getValue(QingJiTab.Diary),
                            vm = diaryVm
                        )
                    }
                    composable(QingJiTab.Mine.route) {
                        MineScreen(
                            onOpenSettings = { navController.navigate(QingJiRoutes.Settings) },
                            onOpenProfile = { navController.navigate(QingJiRoutes.Profile) },
                            onOpenTrainingHistory = { navController.navigate(QingJiRoutes.TrainingHistory) },
                            onStartTraining = { navController.navigate(QingJiRoutes.TrainingActive) },
                            onOpenTrainingPlan = { navController.navigate(QingJiRoutes.TrainingPlan) },
                            onOpenExerciseLibrary = { navController.navigate(QingJiRoutes.ExerciseLibrary) },
                            onOpenFavorites = { navController.navigate(QingJiRoutes.ExerciseFavorites) },
                            onOpenPersonalBest = { navController.navigate(QingJiRoutes.PersonalBest) },
                            onOpenSelfDiscipline = { navController.navigate(QingJiRoutes.SelfDiscipline) },
                            onOpenSelfDisciplineSettings = { navController.navigate(QingJiRoutes.SelfDisciplineSettings) },
                            scrollState = tabScrollStates.getValue(QingJiTab.Mine),
                            vm = mineVm
                        )
                    }
                    composable(QingJiRoutes.Settings) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenFoodPreference = { navController.navigate(QingJiRoutes.FoodPreference) },
                            onOpenWidget = { navController.navigate(QingJiRoutes.WidgetSettings) },
                            onOpenAiConfig = { navController.navigate(QingJiRoutes.AiConfig) },
                            onOpenProfile = { navController.navigate(QingJiRoutes.Profile) },
                            onOpenBackup = { navController.navigate(QingJiRoutes.Backup) }
                        )
                    }
                    composable(QingJiRoutes.Backup) {
                        BackupScreen(onBack = { navController.popBackStack() })
                    }
                    composable(QingJiRoutes.SearchFood) {
                        SearchFoodScreen(onBack = { navController.popBackStack() })
                    }
                    composable(QingJiRoutes.TextAi) {
                        TextAiScreen(
                            onBack = { navController.popBackStack() },
                            onOpenAiConfig = { navController.navigate(QingJiRoutes.AiConfig) }
                        )
                    }
                    composable(QingJiRoutes.Weight) {
                        WeightScreen(onBack = { navController.popBackStack() })
                    }
                    composable(QingJiRoutes.AiConfig) {
                        AiConfigScreen(onBack = { navController.popBackStack() })
                    }
                    composable(QingJiRoutes.Profile) {
                        ProfileScreen(onBack = { navController.popBackStack() })
                    }
                    composable(QingJiRoutes.ImageRecord) {
                        ImageRecordScreen(
                            onBack = { navController.popBackStack() },
                            onFallback = { navController.navigate(QingJiRoutes.SearchFood) },
                            onOpenAiConfig = { navController.navigate(QingJiRoutes.AiConfig) }
                        )
                    }
                    composable(QingJiRoutes.VoiceRecord) {
                        VoiceRecordScreen(
                            onBack = { navController.popBackStack() },
                            onFallback = { navController.navigate(QingJiRoutes.SearchFood) },
                            onOpenAiConfig = { navController.navigate(QingJiRoutes.AiConfig) }
                        )
                    }
                    composable(QingJiRoutes.Barcode) {
                        BarcodeScreen(onBack = { navController.popBackStack() })
                    }
                    composable(QingJiRoutes.AiAnalysis) {
                        AiAnalysisScreen(
                            onBack = { navController.popBackStack() },
                            onOpenAiConfig = { navController.navigate(QingJiRoutes.AiConfig) }
                        )
                    }
                    composable(
                        route = QingJiRoutes.DiaryEdit,
                        arguments = listOf(navArgument("diaryId") {
                            type = NavType.LongType
                            defaultValue = 0L
                        })
                    ) { entry ->
                        DiaryEditScreen(
                            diaryId = entry.arguments?.getLong("diaryId") ?: 0L,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(QingJiRoutes.WidgetSettings) {
                        WidgetSettingsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(QingJiRoutes.FoodLibrary) {
                        FoodLibraryScreen(onBack = { navController.popBackStack() })
                    }
                    composable(QingJiRoutes.FoodPreference) {
                        FoodPreferenceScreen(onBack = { navController.popBackStack() })
                    }
                    composable(QingJiRoutes.Fasting) {
                        FastingScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = QingJiRoutes.MealDetail,
                        arguments = listOf(
                            navArgument("date") { type = NavType.StringType },
                            navArgument("mealType") { type = NavType.StringType }
                        )
                    ) { entry ->
                        MealDetailScreen(
                            dateIso = entry.arguments?.getString("date").orEmpty(),
                            mealType = entry.arguments?.getString("mealType").orEmpty(),
                            onBack = { navController.popBackStack() },
                            onOpenDiary = { id -> navController.navigate(QingJiRoutes.diaryEdit(id)) }
                        )
                    }
                    // v3.3.0 运动模块：二级选运动 → 三级确认记录
                    composable(QingJiRoutes.ExercisePicker) {
                        ExercisePickerScreen(
                            onBack = { navController.popBackStack() },
                            onPick = { id ->
                                // 「更换」会 pop 回本页；再选别的运动时把旧的确认页弹掉，
                                // 避免返回栈里堆一串确认页。
                                navController.navigate(QingJiRoutes.exerciseConfirm(id)) {
                                    popUpTo(QingJiRoutes.ExercisePicker) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    composable(
                        route = QingJiRoutes.ExerciseConfirm,
                        arguments = listOf(navArgument("presetId") { type = NavType.LongType })
                    ) { entry ->
                        ExerciseConfirmScreen(
                            presetId = entry.arguments?.getLong("presetId") ?: 0L,
                            onBack = { navController.popBackStack() },
                            onSaved = { navController.popBackStack(QingJiTab.Home.route, false) }
                        )
                    }
                    // ---------- 训练中心 P0 ----------
                    composable(QingJiRoutes.TrainingActive) {
                        TrainingActiveScreen(
                            vm = trainingVm,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(QingJiRoutes.TrainingHistory) {
                        TrainingHistoryScreen(
                            vm = trainingVm,
                            onBack = { navController.popBackStack() },
                            onStartTraining = { navController.navigate(QingJiRoutes.TrainingActive) },
                            onEditSession = { navController.navigate(QingJiRoutes.TrainingActive) }
                        )
                    }
                    // ---------- 训练中心 P1 ----------
                    composable(QingJiRoutes.ExerciseLibrary) {
                        ExerciseLibraryScreen(
                            vm = planVm,
                            onBack = { navController.popBackStack() },
                            onOpenDetail = { uuid -> navController.navigate(QingJiRoutes.exerciseDetail(uuid)) }
                        )
                    }
                    composable(QingJiRoutes.ExerciseFavorites) {
                        ExerciseLibraryScreen(
                            vm = planVm,
                            onBack = { navController.popBackStack() },
                            onOpenDetail = { uuid -> navController.navigate(QingJiRoutes.exerciseDetail(uuid)) },
                            favoritesOnly = true
                        )
                    }
                    composable(
                        route = QingJiRoutes.ExerciseDetail,
                        arguments = listOf(navArgument("uuid") { type = NavType.StringType })
                    ) { entry ->
                        ExerciseDetailScreen(
                            vm = planVm,
                            uuid = entry.arguments?.getString("uuid").orEmpty(),
                            onBack = { navController.popBackStack() },
                            onStartRecord = { ex ->
                                // 先寄存在 VM，进行中训练页第一帧取走
                                trainingVm.queueExercise(ex)
                                navController.navigate(QingJiRoutes.TrainingActive)
                            }
                        )
                    }
                    composable(QingJiRoutes.TrainingPlan) {
                        TrainingPlanScreen(
                            vm = planVm,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(QingJiRoutes.PersonalBest) {
                        PersonalBestScreen(
                            vm = planVm,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // ==================== 自律管理（P0） ====================
                    composable(QingJiRoutes.SelfDiscipline) {
                        SelfDisciplineDashboardScreen(
                            onBack = { navController.popBackStack() },
                            vm = selfDisciplineVm,
                            onOpenGoals = { navController.navigate(QingJiRoutes.SelfDisciplineGoals) },
                            onOpenCheckin = { id -> navController.navigate(QingJiRoutes.selfDisciplineCheckin(id)) },
                            onOpenUrge = { id -> navController.navigate(QingJiRoutes.selfDisciplineUrge(id)) },
                            onOpenDeviation = { id -> navController.navigate(QingJiRoutes.selfDisciplineDeviation(id)) },
                            onOpenHistory = { id -> navController.navigate(QingJiRoutes.selfDisciplineHistory(id)) },
                            onOpenSettings = { navController.navigate(QingJiRoutes.SelfDisciplineSettings) }
                        )
                    }
                    composable(QingJiRoutes.SelfDisciplineGoals) {
                        SelfDisciplineGoalsScreen(
                            onBack = { navController.popBackStack() },
                            vm = selfDisciplineVm,
                            onCreate = { navController.navigate(QingJiRoutes.selfDisciplineGoalEdit(0L)) },
                            onEdit = { id -> navController.navigate(QingJiRoutes.selfDisciplineGoalEdit(id)) },
                            onOpenCheckin = { id -> navController.navigate(QingJiRoutes.selfDisciplineCheckin(id)) }
                        )
                    }
                    composable(
                        route = QingJiRoutes.SelfDisciplineGoalEdit,
                        arguments = listOf(navArgument("goalId") {
                            type = NavType.LongType
                            defaultValue = 0L
                        })
                    ) { entry ->
                        SelfDisciplineGoalEditScreen(
                            goalId = entry.arguments?.getLong("goalId") ?: 0L,
                            vm = selfDisciplineVm,
                            onBack = { navController.popBackStack() },
                            onSaved = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = QingJiRoutes.SelfDisciplineCheckin,
                        arguments = listOf(navArgument("goalId") { type = NavType.LongType })
                    ) { entry ->
                        SelfDisciplineCheckinScreen(
                            goalId = entry.arguments?.getLong("goalId") ?: 0L,
                            vm = selfDisciplineVm,
                            onBack = { navController.popBackStack() },
                            onSaved = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = QingJiRoutes.SelfDisciplineUrge,
                        arguments = listOf(navArgument("goalId") { type = NavType.LongType })
                    ) { entry ->
                        SelfDisciplineUrgeScreen(
                            goalId = entry.arguments?.getLong("goalId") ?: 0L,
                            vm = selfDisciplineVm,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = QingJiRoutes.SelfDisciplineDeviation,
                        arguments = listOf(navArgument("goalId") { type = NavType.LongType })
                    ) { entry ->
                        SelfDisciplineDeviationScreen(
                            goalId = entry.arguments?.getLong("goalId") ?: 0L,
                            vm = selfDisciplineVm,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = QingJiRoutes.SelfDisciplineHistory,
                        arguments = listOf(navArgument("goalId") { type = NavType.LongType })
                    ) { entry ->
                        SelfDisciplineHistoryScreen(
                            goalId = entry.arguments?.getLong("goalId") ?: 0L,
                            vm = selfDisciplineVm,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(QingJiRoutes.SelfDisciplineSettings) {
                        SelfDisciplineSettingsScreen(
                            onBack = { navController.popBackStack() },
                            vm = selfDisciplineVm,
                            onOpenGoals = { navController.navigate(QingJiRoutes.SelfDisciplineGoals) }
                        )
                    }
                }
            }
        }

        // barfx v4：悬浮底栏是 Overlay——盖在内容上层，不占布局槽。
        // 之前放在 bottomBar 槽里，Scaffold 给它让出一整块背景，Dock 两侧/下方
        // 露出一截空底色，看起来像给底栏蒙了层遮罩；现在内容延伸到 Dock 背后，
        // 两侧直接能看到页面列表。进出二级页沿用滑落/滑入 + 淡入淡出。
        if (settings.floatingBottomBar) {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(
                    animationSpec = tween(com.qingji.app.ui.theme.Motion.Normal, easing = FastOutSlowInEasing)
                ) { it } + fadeIn(tween(com.qingji.app.ui.theme.Motion.Fast)),
                exit = slideOutVertically(
                    animationSpec = tween(com.qingji.app.ui.theme.Motion.Normal, easing = FastOutSlowInEasing)
                ) { it } + fadeOut(tween(com.qingji.app.ui.theme.Motion.Fast)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                QingJiFloatingBar(
                    currentRoute = currentRoute,
                    onTabSelected = onBarTabSelected,
                    onAddClick = { showAddSheet = true },
                    settings = settings
                )
            }
        }
    }

    // 弹层放在 Scaffold 外面，避免被底部导航栏遮住
    if (showAddSheet) {
        AddRecordSheet(
            onDismiss = { showAddSheet = false },
            // 食物库入口直接进食物库页（在那里选好加到某一餐）
            onSearchFood = { navController.navigate(QingJiRoutes.FoodLibrary) },
            onTextAi = { navController.navigate(QingJiRoutes.TextAi) },
            onWeight = { navController.navigate(QingJiRoutes.Weight) },
            onImage = { navController.navigate(QingJiRoutes.ImageRecord) },
            onVoice = { navController.navigate(QingJiRoutes.VoiceRecord) },
            onBarcode = { navController.navigate(QingJiRoutes.Barcode) },
            // 记一杯水（默认 200ml，直接落库）；体型照片进进展页
            onWater = {
                scope.launch {
                    appRepo.addWater(200, com.qingji.app.domain.AppClock.today())
                }
            },
            // v3.3.0：记运动改走独立的「记录运动」二级页，不再只是跳进展页
            onExercise = { navController.navigate(QingJiRoutes.ExercisePicker) },
            onStartTraining = { navController.navigate(QingJiRoutes.TrainingActive) },
            onAerobic = { navController.navigate(QingJiRoutes.ExercisePicker) },
            onBodyPhoto = { navController.navigate(QingJiTab.Progress.route) },
            // 自律管理：有主要目标就直达对应页，没有就先回模块首页建目标
            onSelfDisciplineCheckin = {
                val id = selfDisciplineVm.primaryGoal.value?.id
                navController.navigate(
                    if (id != null) QingJiRoutes.selfDisciplineCheckin(id) else QingJiRoutes.SelfDiscipline
                )
            },
            onSelfDisciplineUrge = {
                val id = selfDisciplineVm.primaryGoal.value?.id
                navController.navigate(
                    if (id != null) QingJiRoutes.selfDisciplineUrge(id) else QingJiRoutes.SelfDiscipline
                )
            },
            onSelfDisciplineDeviation = {
                val id = selfDisciplineVm.primaryGoal.value?.id
                navController.navigate(
                    if (id != null) QingJiRoutes.selfDisciplineDeviation(id) else QingJiRoutes.SelfDiscipline
                )
            }
        )
    }
}
