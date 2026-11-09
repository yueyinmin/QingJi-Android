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
        minSdk = 26                      // 最低支持 Android 8.0（覆盖 95% 以上设备）
        targetSdk = 35                   // 目标版本
        // v1.5（2026-11-09）：① 杯内计量数字整个去掉；② 引导新增昵称一问（不填随机「轻er~XXX」），共 10 步；
        // ③ 「我的」页资料卡与使用情况拆开、「数据（目标/成就）」挪到使用情况上方、使用情况加说明；④ 我的页和设置页都能改昵称；
        // ⑤ 进展页身体数据卡与目标预估卡之间补间距。功能次版本：versionCode 150。
        // 正式版 v1.0.0 · 最终定版（2026-11-09）：versionName 三段式 1.0.0，versionCode 重置 100（主版本×100）。
        // 未对外发布过，重置不影响任何人；已装旧包的手机需先卸载再装（签名也换了正式密钥，同样必须卸载重装）。
        // 定版后的界面修复走 v1.0.x 补丁：v1.0.1 = 101（引导页骨架重做）。
        // v1.0.2 = 102：目标状态胶囊跟在连续天数右边；记体重挪进悬浮栏（四颗）；今日记录行右侧清空。
        versionCode = 102
        versionName = "1.0.2"
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

    // 在电脑上「真运行」安卓界面：Robolectric 提供安卓框架，Compose 测试库负责点击
    // 用途：验证按钮真的能点、点了真的会写库，而不是只验证能编译
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
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
