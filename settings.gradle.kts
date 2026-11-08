// ============================================================
// 工程级设置：包含哪些模块 + 依赖去哪儿下载
// ------------------------------------------------------------
// 本机实测（2026-11-07）：官方 google() / mavenCentral() 直连慢，
// 所以把国内镜像排在前面，官方源留作兜底（镜像缺包时自动回退）。
// 换网络环境时，把带「镜像」注释的几行删掉即可，其它代码不用动。
// ============================================================
pluginManagement {
    repositories {
        // 镜像：AGP、Kotlin、KSP 这些插件的国内副本
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // 官方兜底
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 镜像：Compose、Room、Navigation 等运行库
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        // 官方兜底
        google()
        mavenCentral()
    }
}

rootProject.name = "QingJi"
include(":app")
