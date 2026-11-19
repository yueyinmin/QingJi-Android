// ============================================================
// app 模块构建脚本
// 这个文件决定：App 叫什么、装到哪些手机上、用哪些库
// ============================================================
import java.io.File
import java.util.Properties
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)   // 用 Compose 写界面必须开这个
    alias(libs.plugins.ksp)              // Room 生成代码要用注解处理器
}

// ------------------------------------------------------------
// 本机专用：把编译中间产物挪出 OneDrive 目录
// 原因：工程放在 OneDrive 桌面上，同步程序会占用 build 目录里的小文件，
// Gradle 清理时反复报 "Unable to delete directory" 导致编译失败。
// 只有 local.properties 里写了 qingji.build.dir 才生效；
// 换电脑没有这个配置 → 自动用默认的工程内 build 目录，不影响任何人。
// ------------------------------------------------------------
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { stream -> localProps.load(stream) }
}
localProps.getProperty("qingji.build.dir")?.let { dir ->
    if (dir.isNotBlank()) layout.buildDirectory.set(file(dir))
}

// ------------------------------------------------------------
// 正式签名（v1.0.0 定版加密封装）：
// 密钥和密码都放在 local.properties（已被 .gitignore 排除，不进仓库）。
// 没有这份配置的电脑 → 自动回退 debug 签名，编译不受影响。
// ------------------------------------------------------------
val ksPath = localProps.getProperty("qingji.keystore.path")
val ksPass = localProps.getProperty("qingji.keystore.password")
val ksAlias = localProps.getProperty("qingji.keystore.alias") ?: "qingji"
val hasReleaseKey = !ksPath.isNullOrBlank() && File(ksPath).exists() && !ksPass.isNullOrBlank()

android {
    namespace = "com.qingji.app"         // 代码包名
    compileSdk = 35                      // 用哪个版本的安卓 SDK 编译

    defaultConfig {
        applicationId = "com.qingji.app" // 手机上唯一标识，上架后不能改
        // v1.1.0：迁移链测试（androidTest）用的运行器 —— 此前项目没有任何 androidTest，从未配置过
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 26                      // 最低支持 Android 8.0（覆盖 95% 以上设备）
        targetSdk = 35                   // 目标版本
        // v1.5（2026-11-09）：① 杯内计量数字整个去掉；② 引导新增昵称一问（不填随机「轻er~XXX」），共 10 步；
        // ③ 「我的」页资料卡与使用情况拆开、「数据（目标/成就）」挪到使用情况上方、使用情况加说明；④ 我的页和设置页都能改昵称；
        // ⑤ 进展页身体数据卡与目标预估卡之间补间距。功能次版本：versionCode 150。
        // 正式版 v1.0.0 · 最终定版（2026-11-09）：versionName 三段式 1.0.0，versionCode 重置 100（主版本×100）。
        // 未对外发布过，重置不影响任何人；已装旧包的手机需先卸载再装（签名也换了正式密钥，同样必须卸载重装）。
        // 定版后的界面修复走 v1.0.x 补丁：v1.0.1 = 101（引导页骨架重做）。
        // v1.0.2 = 102：目标状态胶囊跟在连续天数右边；记体重挪进悬浮栏（四颗）；今日记录行右侧清空。
        // v1.0.3 = 103：轻断食按 GPT 方案增强——首页断食条改实底按钮、方案加 18:6/20:4、生效周期（周几）、
        //   跨零点进食窗口、时段切换提醒（两闹钟按窗口自动排）、断食页历史记录卡。
        // v1.0.4 = 104：数据库拆成两个文件——标准食物库 food_catalog.db（assets 预置 654 条，只读）
        //   + 用户库 qingji.db（Room version 6，新增 user_food / food_favorite / food_search_history）；
        //   食物库数据契约升级到 v0.2，搜索支持中文/别名/拼音首字母/全拼 + 最近搜索。
        // v1.0.5 = 105（2026-11-10）：三条并行分支合并成一个正式版，不再分开发预览包——
        //   ① AI 营养数据一致性交叉校验（Atwater 4-9-4 反推，对不上的只标记不改数）；
        //   ② 搜索默认列表加 LIMIT，用户自建食物优先；ApiKeyStore 读取全段兜底；
        //   ③ 引导 10 步 → 5 步，三处入口统一走 NutritionCalculator.compute()，落库顺序改成
        //      「存档案 → 存偏好 → 最后写完成标记」，修掉异步保存竞态；
        //   ④ 首页顶栏「减脂」不再折行/被压成竖排，悬浮按钮「记体重」补回专属绿色；
        //   ⑤ 叫法统一为「减脂」。
        // v1.0.6 = 106（2026-11-10）：Phase 0 审计后的「数据安全收口」——
        //   ① 清除数据拆成两档：「清除记录」清 10 张记录表、「恢复出厂」清 15 张表 + 设置 + Key + 图片，
        //      修掉「文案说清围度/运动/照片、代码只清 6 张表」的承诺不一致；
        //   ② 备份恢复改成「先整份解析、再开事务清表落库」，修掉「先删表再解析、中途失败毁一半数据」；
        //   ③ 备份补齐围度/进度照片/运动/断食/自建运动五段，补回档案三项营养目标、饮食三项微量营养素和图片路径；
        //   ④ 运动记录新增 steps / distanceKm 两列（用户库 6→7），按步数或公里记的原始数字不再丢；
        //   ⑤ 新增 BackupParsingTest 12 个用例，备份解析第一次有自动化测试。
        // v1.0.10 = 110（2026-11-13，未发版，编号并入 111）：训练中心 P0（6 张新表 / Room 10 / 备份 v3 / 训练日记全链路）
        //   + 版本更新提示与「版本信息」里的更新记录。
        // v1.1.0 = 111（2026-11-13）：训练中心正式定版（发版号，未对外发布过 1.0.10）。
        // v1.1.1 = 112（2026-11-14）：自律管理 P0（7 张新表 / Room 11）+ 首页今日自律菜单 +
        //   一级页面左右滑动切换 + 我的/设置图标与卡片规范统一 + 深色与小屏修复 + 使用情况两列四排。
        // v1.1.2 = 113（2026-11-14）：修两处悬浮按钮遮挡——首页餐次卡的千卡数与营养素条、
        //   训练日记训练记录卡的「明细/修改/删除」都被 50dp 按钮压住（后者点不到删除）。
        // v1.1.5 = 116（2026-11-15）：底栏 V2（选中胶囊缩小到 76~82dp、记一笔按钮 116x48dp、
        //   动态权重 1.5:1）+ 恢复五套主题跟底栏联动 + 第二批 P1 重构（TASK-05~08：
        //   统一设置出口、备份 IO 下沉、进度页拆卡片、迁移测试单一来源）。
        // v1.2.1 = 121（2026-11-16）：动作库 38 → 200 条（增量按 UUID 补齐，不动老用户数据）
        //   + 117 条候选图三帧连续播放 + 83 条无图占位 + 逐帧授权台账。
        // v1.3.0 = 122（2026-11-17）：Workout Guide 302 条动作整体替换 + 906 帧本地三帧 SVG
        //   + Room 11→12（只加列加表）+ UUID 差集安全导入 + 备份合并 + 英文名搜索。
        // v1.3.1 = 123（2026-11-17）：OmniFood8K 数据层入库（166 条命名食物 / 8037 样本 / 27466 组成关系）
        //   + 预置库 Room 身份修复（不再误清标准库）+ 老乡鸡堂食清洗归档。
        // v1.3.2 = 124（2026-11-17）：OmniFood8K 界面接入——搜索点开新食物显示组成明细、
        //   样本来源与异常提示；166 条全部标记人工已审核（界面不显示标记）、4 条严重异常样本删除，
        //   标准库数据版本 0.4 → 0.5（老用户自动换新库）。
        // v1.3.3 = 125（2026-11-18）：老乡鸡品牌接入 UI——外卖快餐动态品牌选择 +
        //   堂食菜当菜单（301 道，可搜可收藏可看折叠溯源）；标准库 Room 4→5 加
        //   外卖 8 张空表（无真实商品不造假），数据版本 0.6 → 0.7。
        // v1.3.4 = 126（2026-11-18）：健康体系 V1.1——组间休息计时 + 历史转计划模板 +
        //   睡眠模块（Room 14）+ 记一笔方案 A + 自律管理入口整合 + 热力图尾部补齐 +
        //   首页睡眠摘要卡 + 动作库/食物库 LazyColumn 性能优化。
        // v1.3.5 = 127（2026-11-18）：外卖快餐 UI 对齐家庭厨房（左分类 + 右懒加载列表）；
        //   老乡鸡 0 千卡整理——40 道按同名/同类近似回填并留出处，其余显示「暂无热量数据」。
        // v1.3.6 = 128（2026-11-18）：睡眠页重设计（评分环/分析卡/时间轴/指标卡 + 日期导航，
        //   录入表单收进底部弹层）；进展页新增睡眠/运动/饮食三种日记卡（共用一套组件，进卡片管理）。
        // v1.3.7 = 129（2026-11-18）：训练模块工单核验后补 3 项——有氧记录可改 + 日周月切换、
        //   从历史复制计划改用动作 UUID（名字兜底）、训练侧设备同步预留约定。
        // v1.3.8 = 130（2026-11-18）：运动补偿接入「今日剩余」主数字；运动消耗卡改读统一账本
        //   （力量训练不再漏算）；记一次加「训练记录」页签；外卖快餐改成家庭厨房双级布局。
        // v1.3.9 = 131（2026-11-18）：真机反馈修复——运动补偿开关永远关着导致今日剩余不变、
        //   存进食物库静默失败；家庭厨房加横排细类、去免责小字；训练时长可手动指定 +
        //   补记力量训练 + 记一次分有氧/力量 + 训练量≥1吨改吨显示。
        // v1.3.10 = 132（2026-11-18）：首页「今日剩余」卡按参考图改版（运动消耗橙色独立行 +
        //   营养素大数字版式）；睡眠日期改「年/月/日」三列滚轮点选；训练计时条去「点这里可改」
        //   小尾巴；食物库搜索跨分类 + AI 宏量全 0 兜底回查。
        versionCode = 142
        versionName = "1.4.2"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(ksPath!!)
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = ksPass
            }
        }
    }

    buildTypes {
        release {
            // v2.7：打开 R8 优化——这是「不够顺滑」收益最大的一项，
            // Compose 在 release 下比 debug 快 30~50%。自用包用 debug 签名。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true                   // 打开 Compose
        // 打开后可以用 BuildConfig.VERSION_NAME 在「设置 → 版本信息」里显示版本号，
        // 以后升级只改上面的 versionName，不用再去界面里改一次。
        buildConfig = true
    }

    // 让本地单元测试也能读到 res/ 资源和 AndroidManifest（Robolectric 需要）
    sourceSets {
        // v1.1.0：迁移测试 MigrationTestHelper 要把 schemas/*.json 当测试资产读
        getByName("test").assets.srcDir("$projectDir/schemas")
        // v1.1.0：迁移测试放 androidTest 跑，schema 资产同样要挂进 androidTest
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room 需要把表结构导出成 JSON（每次改表结构会自动生成新版本，方便以后写升级脚本）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // 基础
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)   // 后台线程：首次启动灌假数据

    // 界面（用 BOM 统一版本，下面的 ui / material3 不写版本号）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // 页面跳转
    implementation(libs.androidx.navigation.compose)

    // 本地数据库
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 偏好设置
    implementation(libs.androidx.datastore.preferences)

    // 图片加载（M2 再说，先放着）
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // 网络：文字/图片 AI 识别走 OpenAI 兼容协议，用 OkHttp 发请求
    // 不引 Retrofit / kotlinx.serialization，用安卓自带的 org.json 解析，依赖更少更稳
    implementation(libs.okhttp)

    // 条码：ZXing 纯 Java 解码「拍好的照片」，不用 CameraX 预览。
    // 试过 ML Kit，会把 APK 从 18MB 撑到 40MB（超 PRD 的 25MB 红线），已换成 core 精简版。
    implementation(libs.zxing.core)

    // 桌面小组件（M8）：Jetpack Glance，Compose 风格写 RemoteViews
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // 本地单元测试（只测纯 Kotlin 逻辑，不需要手机/模拟器）
    testImplementation(libs.junit)

    // v1.0.6：备份解析测试要用真的 org.json。
    // 安卓 SDK 里那个 org.json 在 JVM 单测里是「空壳」，一调就抛
    // "Method not mocked"；这个 jar 会盖在 mockable android.jar 前面。
    testImplementation(libs.org.json)

    // 在电脑上「真运行」安卓界面：Robolectric 提供安卓框架，Compose 测试库负责点击
    // 用途：验证按钮真的能点、点了真的会写库，而不是只验证能编译
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)

    // v1.1.0：迁移链测试放在 instrumentation 环境（androidTest）跑
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)

    // 调试用工具（只在 debug 包里存在）
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// ------------------------------------------------------------
// Robolectric 跑测试时，会额外去 Maven 下一个「安卓系统包」（100MB+）。
// 默认源 repo1.maven.org 在本机会卡死（实测卡 8 分钟没动静），
// 这里换成阿里云镜像。只影响本地测试，不影响 APK。
// ------------------------------------------------------------
tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.id", "aliyun")
    systemProperty("robolectric.dependency.repo.url", "https://maven.aliyun.com/repository/public")
    // 打日志出来，卡在哪一步一眼能看到
    systemProperty("robolectric.logging", "stdout")
}
