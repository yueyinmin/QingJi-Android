package com.qingji.app.ui.screen
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.domain.NutritionCalculator
import com.qingji.app.domain.ProfileInputRules
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.pressable
import com.qingji.app.ui.viewmodel.OnboardingAnswers


/**
 * ============================================================
 * 首次引导（v1.5：10 步精简成 5 步）
 * ------------------------------------------------------------
 * 改版的原因很直接：第一次打开就要用户填昵称、饮水、过敏原、忌口、菜系，
 * 而这几项跟「算出第一版营养目标」一点关系都没有。很多人填到第五六页就退了。
 *
 * 现在只留非有不可的：
 *   0 欢迎 → 1 目标（减重/增肌/保持）→ 2 身体信息（性别/年龄/身高/体重/目标体重）
 *   → 3 活动量 → 4 目标预览 → 完成
 *
 * 被移出去的几项没有丢功能，只是换了入口：
 *   昵称      → 「我的」页点名字 / 设置页「昵称」
 *   饮水目标  → 先给默认 2000，个人资料页或首页饮水卡里改
 *   过敏原/忌口/菜系 → 设置 → 饮食偏好与忌口
 *
 * 两条硬约束：
 * 1. **不自己算热量**。所有目标一律调 NutritionCalculator.compute()，
 *    和「我的 → 个人资料」是同一个入口、同一套公式，避免出现
 *    「引导算完是 1600，进资料页点保存变成 1400」这种事。
 * 2. **不拿默认值冒充真实数据**。年龄/身高/体重输入框都是空的，
 *    placeholder 只是示例；没填就不让过，不会悄悄用 170cm / 25 岁建档案。
 *
 * 已有的视觉、动画、进度条、底部按钮布局都沿用 v1.5.1 那套，没有重画。
 * ============================================================
 */

/** 五个步骤的下标，写出来免得满屏魔法数字 */
private const val STEP_WELCOME = 0
private const val STEP_GOAL = 1
private const val STEP_BODY = 2
private const val STEP_ACTIVITY = 3
private const val STEP_PREVIEW = 4
private const val TOTAL_STEPS = 5

@Composable
fun OnboardingScreen(
    /** 正在保存：显示「保存中…」并锁住按钮，防止连点重复提交 */
    saving: Boolean,
    /** 保存失败的提示（来自 OnboardingViewModel），空 = 没有 */
    saveError: String,
    onSubmit: (OnboardingAnswers) -> Unit
) {
    // ---------- 所有答案：全部 rememberSaveable，转屏 / 页面重建都不丢 ----------
    var step by rememberSaveable { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableStateOf(NutritionCalculator.MODE_LOSE) }
    // 性别默认空着、不预选。预选「男生」看着省事，但等于偷偷替用户
    // 做了个会影响 BMR（男女差 166 千卡）的决定，所以必须用户自己点一下。
    var gender by rememberSaveable { mutableStateOf("") }
    var ageText by rememberSaveable { mutableStateOf("") }
    var heightText by rememberSaveable { mutableStateOf("") }
    var weightText by rememberSaveable { mutableStateOf("") }
    var targetText by rememberSaveable { mutableStateOf("") }
    var activity by rememberSaveable { mutableIntStateOf(2) }
    var localError by rememberSaveable { mutableStateOf("") }

    // 首次进入的时间，写进档案的 createdAt（「加入天数」按它算）。
    // 保存失败重试时沿用同一个值，避免天数被重试刷回 1。
    val createdAt = rememberSaveable { System.currentTimeMillis() }

    // 当前这组输入的校验结果，后面第 5 步的预览要用。
    // 用 remember 卡住，避免每次重组都重新解析一遍字符串。
    val bodyInput = remember(ageText, heightText, weightText, targetText) {
        ProfileInputRules.BodyInput(ageText, heightText, weightText, targetText)
    }
    val bodyValues = remember(bodyInput, mode) {
        ProfileInputRules.parseBody(mode, bodyInput)
    }

    // 预览目标：数据不齐或者性别还没选就不算 —— 宁可不显示，也不用
    // 「默认按男性」这种隐含假设糊弄一份看起来正常的数字
    val preview = remember(bodyValues, gender, activity, mode) {
        if (gender.isBlank()) {
            null
        } else {
            bodyValues?.let {
                NutritionCalculator.compute(
                    gender = gender,
                    age = it.age,
                    heightCm = it.heightCm,
                    weightKg = it.weightKg,
                    activityLevel = activity,
                    mode = mode
                )
            }
        }
    }

    /** 最后一步：把答案交出去，交给 OnboardingViewModel 按顺序落库 */
    fun submit() {
        if (gender.isBlank()) {
            localError = "先选一下性别"
            step = STEP_BODY
            return
        }
        val values = ProfileInputRules.parseBody(mode, bodyInput)
        if (values == null) {
            localError = ProfileInputRules.bodyError(mode, bodyInput) ?: "有些数字还不对，回去检查一下"
            step = STEP_BODY
            return
        }
        localError = ""
        onSubmit(
            OnboardingAnswers(
                mode = mode,
                gender = gender,
                age = values.age,
                heightCm = values.heightCm,
                weightKg = values.weightKg,
                targetWeightKg = values.targetWeightKg,
                activityLevel = activity,
                createdAt = createdAt
            )
        )
    }

    /** 下一步：只在「身体信息」这页拦校验，其余页直接过 */
    fun goNext() {
        localError = ""
        if (step == STEP_BODY) {
            if (gender.isBlank()) {
                localError = "先选一下性别"
                return
            }
            val err = ProfileInputRules.bodyError(mode, bodyInput)
            if (err != null) {
                localError = err
                return
            }
        }
        if (step < STEP_PREVIEW) step += 1 else submit()
    }

    // 骨架沿用 v1.5.1：进度条固定顶部、按钮固定底部、中间内容独立滚动
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            // 引导页是全屏页（在 Scaffold 之前就 return 了），得自己让开状态栏。
            // 原来是固定 40dp，碰上刘海屏那种 40~48dp 的状态栏，进度条会被盖住。
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            // 键盘弹出时整页上推，底部按钮不会被输入法盖住
            .imePadding()
    ) {
        Spacer(Modifier.height(14.dp))

        // ---------- 进度条（固定顶部，按真实步数走） ----------
        LinearProgressIndicator(
            progress = { (step + 1f) / TOTAL_STEPS },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = QingJiGreen,
            trackColor = QingJiGreenLight
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${step + 1} / $TOTAL_STEPS",
            fontSize = 11.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(16.dp))

        // ---------- 中间内容：独立滚动，占据剩余全部高度 ----------
        Box(modifier = Modifier.weight(1f)) {
            // 每次换页重新触发一次「滑入 + 淡入」；旧内容立即移除，不会叠加
            val pageVisible = remember(step) {
                MutableTransitionState(false).apply { targetState = true }
            }
            // 写全限定名：ColumnScope 里也有个同名扩展，直接写 AnimatedVisibility
            // 会被解析成那个扩展，在 Box 里调不了。
            androidx.compose.animation.AnimatedVisibility(
                visibleState = pageVisible,
                enter = slideInHorizontally(tween(240)) { full -> full / 4 } + fadeIn(tween(240)),
                exit = ExitTransition.None
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (step) {
                        STEP_WELCOME -> WelcomePage()
                        STEP_GOAL -> GoalPage(mode = mode, onPick = { mode = it })
                        STEP_BODY -> BodyPage(
                            mode = mode,
                            gender = gender,
                            onGender = { gender = it },
                            ageText = ageText,
                            onAge = { ageText = it },
                            heightText = heightText,
                            onHeight = { heightText = it },
                            weightText = weightText,
                            onWeight = { weightText = it },
                            targetText = targetText,
                            onTarget = { targetText = it }
                        )
                        STEP_ACTIVITY -> ActivityPage(activity = activity, onPick = { activity = it })
                        STEP_PREVIEW -> PreviewPage(mode = mode, preview = preview, onBackToBody = {
                            localError = ""
                            step = STEP_BODY
                        })
                    }
                }
            }
        }

        // 错误提示固定在按钮上方：第 2 步的输入问题、保存失败都显示在这里
        val shownError = localError.ifBlank { saveError }
        if (shownError.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(text = shownError, fontSize = 13.sp, color = Color(0xFFD64545))
        }

        Spacer(Modifier.height(16.dp))

        // ---------- 底部按钮：固定在屏幕底端 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > STEP_WELCOME) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Dimens.ItemCorner))
                        .background(QingJiGreenLight)
                        // 保存中不让退回去改，否则会和正在跑的落库流程打架
                        .pressable(enabled = !saving) {
                            localError = ""
                            step -= 1
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "上一步", fontSize = 15.sp, color = QingJiGreenDark)
                }
                Spacer(Modifier.width(12.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Dimens.ItemCorner))
                    .background(if (saving) QingJiGreen.copy(alpha = 0.45f) else QingJiGreen)
                    .pressable(enabled = !saving) { goNext() }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        saving -> "保存中…"
                        step == STEP_PREVIEW -> "完成，开始用"
                        else -> "下一步"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ============================================================
// 五个页面
// ============================================================

@Composable
private fun WelcomePage() {
    // 首屏结构：品牌标题 → 产品视觉预览 → 三步引导。
    // 视觉预览全部是真 Compose 组件（不是一张整页图），点不动但和真界面同源，
    // 所以配色、圆角、字号天然跟后面四页一致。
    Column(modifier = Modifier.fillMaxWidth()) {

        // ---------- 1. 品牌标题 ----------
        Text(text = "欢迎使用轻记", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "让每一次记录，都离健康更近一步。",
            fontSize = 15.sp,
            // 比辅助文案稍深一档：两行都次级色会糊成一片，层级靠字号 + 明度一起拉开
            color = TextPrimary.copy(alpha = 0.72f),
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "饮食、运动、体重，轻松记录你的每一点改变。",
            fontSize = 13.sp,
            color = TextSecondary,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(16.dp))

        // ---------- 2. 产品视觉预览 ----------
        PreviewShowcase()

        Spacer(Modifier.height(20.dp))

        // ---------- 3. 三步引导 ----------
        Text(text = "接下来，只需要三步", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        val steps = listOf(
            Triple(Icons.Filled.Flag, "确定你的目标", "选择减脂、增肌或保持。"),
            Triple(Icons.Filled.Person, "完善身体资料", "填写身高、体重、年龄等基本信息。"),
            Triple(Icons.Filled.Bolt, "设置日常活动量", "根据生活习惯计算每日热量与营养目标。")
        )
        steps.forEachIndexed { i, (icon, title, desc) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 统一尺寸的圆角图标底板
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(QingJiGreenLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = QingJiGreenDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "0${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = QingJiGreen)
                        Spacer(Modifier.width(6.dp))
                        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(text = desc, fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp)
                }
            }
            // 步骤之间的轻量分割线，左边让出图标底板的宽度
            if (i < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 50.dp)
                        .height(1.dp)
                        .background(DividerColor)
                )
            }
        }
        // 内容滚到底时和固定按钮留一点呼吸位，字体放大到 1.3x 也不会贴边
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * 首屏的产品视觉预览：浅绿底 + 白色浮层，全部用真组件拼。
 *
 * 数据是写死的示例值 —— 引导页还没有任何用户资料，这里只是让人一眼看懂
 * 「装完之后每天看到的是什么」。所以不接 ViewModel，也不参与任何计算。
 */
@Composable
private fun PreviewShowcase() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(QingJiGreenLight)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        // 每日热量管理
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardWhite)
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = QingJiGreen,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(text = "今日剩余", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                Text(text = "减脂", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = QingJiGreenDark)
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = "1813", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = QingJiGreenDark)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "千卡",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(9.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(QingJiGreenLight)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.38f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(QingJiGreen)
                )
            }
            Spacer(Modifier.height(11.dp))
            // 三大营养素
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("蛋白质" to "132 g", "碳水" to "228 g", "脂肪" to "50 g").forEach { (name, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(PageBackground)
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = QingJiGreenDark)
                        Spacer(Modifier.height(1.dp))
                        Text(text = name, fontSize = 10.sp, color = TextSecondary)
                    }
                }
            }
        }

        // 饮食 / 运动 / 体重 三个功能符号
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Icons.Filled.Restaurant to "饮食",
                Icons.Filled.DirectionsRun to "运动",
                Icons.Filled.Scale to "体重"
            ).forEach { (icon, label) ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(13.dp))
                        .background(CardWhite)
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = QingJiGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(text = label, fontSize = 12.sp, color = TextPrimary)
                }
            }
        }

        // 一条饮食记录
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardWhite)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(QingJiGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = QingJiGreenDark,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "早餐", fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(1.dp))
                Text(text = "燕麦牛奶 · 水煮蛋", fontSize = 13.sp, color = TextPrimary)
            }
            Text(text = "320 千卡", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = QingJiGreenDark)
        }
    }
}

@Composable
private fun GoalPage(mode: String, onPick: (String) -> Unit) {
    QuestionPage(
        title = "这次的目标是？"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Triple(NutritionCalculator.MODE_LOSE, "减脂", "甩掉多余的体重和脂肪"),
                Triple(NutritionCalculator.MODE_GAIN, "增肌", "涨体重涨肌肉，线条更明显"),
                Triple(NutritionCalculator.MODE_KEEP, "保持", "现在挺好的，稳住就行")
            ).forEach { (key, label, desc) ->
                val selected = mode == key
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) QingJiGreenLight else Color.White)
                        .clickable { onPick(key) }
                        .padding(vertical = 18.dp, horizontal = 18.dp)
                ) {
                    Column {
                        Text(
                            text = label,
                            fontSize = 17.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) QingJiGreenDark else TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = desc,
                            fontSize = 12.sp,
                            color = if (selected) QingJiGreenDark else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyPage(
    mode: String,
    gender: String,
    onGender: (String) -> Unit,
    ageText: String,
    onAge: (String) -> Unit,
    heightText: String,
    onHeight: (String) -> Unit,
    weightText: String,
    onWeight: (String) -> Unit,
    targetText: String,
    onTarget: (String) -> Unit
) {
    QuestionPage(
        title = "身体数据"
    ) {
        Text(text = "性别", fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("male" to "男生", "female" to "女生").forEach { (key, label) ->
                val selected = gender == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) QingJiGreenLight else Color.White)
                        .clickable { onGender(key) }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 16.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) QingJiGreenDark else TextPrimary
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionCard {
            Field(
                label = "年龄",
                value = ageText,
                onChange = onAge,
                hint = "比如 25",
                keyboardType = KeyboardType.Number
            )
            Spacer(Modifier.height(10.dp))
            Field(
                label = "身高（cm）",
                value = heightText,
                onChange = onHeight,
                hint = "比如 175"
            )
            Spacer(Modifier.height(10.dp))
            Field(
                label = "当前体重（kg）",
                value = weightText,
                onChange = onWeight,
                hint = "比如 70"
            )
            Spacer(Modifier.height(10.dp))
            Field(
                label = if (mode == NutritionCalculator.MODE_KEEP) {
                    "目标体重（kg）· 可不填"
                } else {
                    "目标体重（kg）"
                },
                value = targetText,
                onChange = onTarget,
                hint = if (mode == NutritionCalculator.MODE_KEEP) "不填就按当前体重算" else "比如 65"
            )
        }
    }
}

@Composable
private fun ActivityPage(activity: Int, onPick: (Int) -> Unit) {
    QuestionPage(
        title = "平时活动量大吗？"
    ) {
        SectionCard {
            (1..4).forEach { level ->
                val selected = activity == level
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) QingJiGreenLight else PageBackground)
                        .clickable { onPick(level) }
                        .padding(vertical = 14.dp, horizontal = 16.dp)
                ) {
                    Text(
                        // 用和「个人资料」页同一个标签函数，两处说法一致
                        text = NutritionCalculator.activityLabel(level),
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) QingJiGreenDark else TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewPage(
    mode: String,
    preview: NutritionCalculator.Goal?,
    onBackToBody: () -> Unit
) {
    val modeLabel = when (mode) {
        NutritionCalculator.MODE_GAIN -> "增肌"
        NutritionCalculator.MODE_KEEP -> "保持"
        else -> "减脂"
    }

    QuestionPage(
        title = "你每天的目标"
    ) {
        if (preview == null) {
            // 正常走流程到不了这里（第 2 步已经拦过），万一走到也给出路
            SectionCard {
                Text(text = "身体数据还不完整，先回去补上", fontSize = 14.sp, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "点下面的按钮回到上一步",
                    fontSize = 13.sp,
                    color = QingJiGreenDark,
                    modifier = Modifier.clickable { onBackToBody() }
                )
            }
        } else {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = modeLabel, fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "每天建议热量",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${preview.kcal}",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = QingJiGreenDark
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "千卡",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroSummaryCell("蛋白质", "${preview.proteinG}", Modifier.weight(1f))
                    MacroSummaryCell("碳水", "${preview.carbG}", Modifier.weight(1f))
                    MacroSummaryCell("脂肪", "${preview.fatG}", Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text(
                    text = "基础代谢 ${preview.bmr} 千卡 · 每日总消耗 ${preview.tdee} 千卡",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "这些都是估算值，给自己做参考用，不是医疗建议。",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

/** 预览页里的一个营养素小格。 */
@Composable
private fun MacroSummaryCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(QingJiGreenLight)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = QingJiGreenDark)
        Text(text = "$label g", fontSize = 11.sp, color = TextSecondary)
    }
}

// ============================================================
// 公用小组件
// ============================================================

/** 一页一问的骨架：问题标题 + 副说明 + 选项区。 */
@Composable
private fun QuestionPage(
    title: String,
    content: @Composable () -> Unit
) {
    Text(text = title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    Spacer(Modifier.height(24.dp))
    content()
}

/** 数字输入框。默认小数键盘，年龄那格传整数键盘。 */
@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    Column {
        Text(text = label, fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint, fontSize = 13.sp, color = TextSecondary) }
        )
    }
}
