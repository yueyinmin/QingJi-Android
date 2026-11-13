package com.qingji.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.qingji.app.ui.viewmodel.TrainingViewModel
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
    /** 日记页的「心情日记 / 训练日记」切换状态放这里，切 Tab 回来不会自己跳回心情日记。 */
    var diaryTrainingTab by remember { mutableStateOf(false) }

    // tabfix（首页→进展抖动的真正根因）：这里原来按路由切 contentWindowInsets
    // —— 首页给全 0，其他页给默认 systemBars。currentRoute 在 navigate() 那一帧
    // 就变了，可 NavHost 的转场动画才刚开始，于是：
    //   1) 内容区 top padding 从 0 瞬间变成状态栏高度，新旧两页同时被重新测量下移；
    //   2) 还没淡出的首页自己也带 statusBarsPadding，退出帧变成双重内边距；
    //   3) 内容区高度一变，slideInVertically 的 height/28 目标位移也跟着变。
    // 现在所有路由统一用 Scaffold 默认值，innerPadding 切换前后恒定；
    // 首页渐变「延伸到状态栏」改由 HomeScreen 用负 offset 补回，视觉零变化。
    Scaffold(
        containerColor = PageBackground,
        bottomBar = {
            if (showBottomBar) {
                QingJiBottomBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        // v3.2.3：已经在当前页就什么都不做。
                        // 以前重复点同一 tab 也会走一遍 popUpTo + restoreState，
                        // 而 restoreState 会把「上次离开这个 tab 时保存的整个返回栈」
                        // 恢复回来 —— 真机上的表现就是点「首页」却落回「进展」。
                        if (tab.route != currentRoute) {
                            navController.navigate(tab.route) {
                                // 只保留首页当栈底，切 tab 不叠加历史；
                                // 刻意不用 restoreState，保证「点哪个就停在哪」。
                                popUpTo(QingJiTab.Home.route) { saveState = false }
                                launchSingleTop = true
                            }
                        }
                    },
                    onAddClick = { showAddSheet = true },
                    settings = settings
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = QingJiTab.Home.route,
                // tabfix：所有路由共用 Motion 的 150ms 原地淡入淡出，
                // 进出同节奏、零位移，转场期间两页始终占同一块区域。
                enterTransition = { com.qingji.app.ui.theme.Motion.pageEnter },
                exitTransition = { com.qingji.app.ui.theme.Motion.pageExit },
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
                        trainingVm = trainingVm,
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
            onBodyPhoto = { navController.navigate(QingJiTab.Progress.route) }
        )
    }
}
