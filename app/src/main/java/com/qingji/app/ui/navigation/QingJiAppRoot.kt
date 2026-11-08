package com.qingji.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.ui.screen.BackupScreen
import com.qingji.app.ui.screen.AiAnalysisScreen
import com.qingji.app.ui.screen.AiConfigScreen
import com.qingji.app.ui.screen.DiaryEditScreen
import com.qingji.app.ui.screen.DiaryScreen
import com.qingji.app.ui.screen.FastingScreen
import com.qingji.app.ui.screen.FoodLibraryScreen
import com.qingji.app.ui.screen.WidgetSettingsScreen
import com.qingji.app.ui.screen.FoodPreferenceScreen
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
    startTargetRequestId: Long = 0L
) {
    val navController = rememberNavController()

    // 是否展开「记一笔」底部弹层
    var showAddSheet by remember { mutableStateOf(false) }

    // 小组件点进来 → 直接跳到目标页；跳完把这个目标清掉，避免反复跳
    LaunchedEffect(startTarget, startTargetRequestId) {
        val target = startTarget ?: return@LaunchedEffect
        if (target == QingJiTab.Home.route) return@LaunchedEffect
        if (target.isBlank()) return@LaunchedEffect
        navController.navigate(target) { launchSingleTop = true }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: QingJiTab.Home.route
    val showBottomBar = currentRoute !in QingJiRoutes.fullScreenRoutes

    // 底部导航要不要显示某个 Tab、首页要不要显示某些卡片，都从这里读
    val context = LocalContext.current
    val settings by remember { SettingsStore.observe(context) }
        .collectAsState(initial = QingJiSettings())

    Scaffold(
        containerColor = PageBackground,
        bottomBar = {
            if (showBottomBar) {
                QingJiBottomBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(QingJiTab.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
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
                // 页面转场统一用 Motion 里的规范：
                // 进场淡入 + 上浮 1/28 屏高，退场快速淡出，返回时方向反过来。
                // 所有页面同一套参数，切换时才不会有「有的快有的慢」的割裂感。
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
                        onOpenFoodLibrary = { navController.navigate(QingJiRoutes.FoodLibrary) }
                    )
                }
                composable(QingJiTab.Progress.route) {
                    ProgressScreen(
                        onOpenWeight = { navController.navigate(QingJiRoutes.Weight) },
                        onOpenImage = { navController.navigate(QingJiRoutes.ImageRecord) }
                    )
                }
                composable(QingJiTab.Diary.route) {
                    DiaryScreen(
                        onOpenEdit = { id -> navController.navigate(QingJiRoutes.diaryEdit(id)) },
                        onOpenWeight = { navController.navigate(QingJiRoutes.Weight) },
                        onOpenImage = { navController.navigate(QingJiRoutes.ImageRecord) }
                    )
                }
                composable(QingJiTab.Mine.route) {
                    MineScreen(
                        onOpenSettings = { navController.navigate(QingJiRoutes.Settings) }
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
            }
        }
    }

    // 弹层放在 Scaffold 外面，避免被底部导航栏遮住
    if (showAddSheet) {
        AddRecordSheet(
            onDismiss = { showAddSheet = false },
            onSearchFood = { navController.navigate(QingJiRoutes.SearchFood) },
            onTextAi = { navController.navigate(QingJiRoutes.TextAi) },
            onWeight = { navController.navigate(QingJiRoutes.Weight) },
            onImage = { navController.navigate(QingJiRoutes.ImageRecord) },
            onVoice = { navController.navigate(QingJiRoutes.VoiceRecord) },
            onBarcode = { navController.navigate(QingJiRoutes.Barcode) }
        )
    }
}
