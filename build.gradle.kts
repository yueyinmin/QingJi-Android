// ============================================================
// 轻记 QingJi —— 根目录构建脚本
// 这里只声明"整个工程要用哪些 Gradle 插件"，不给具体版本号，
// 版本号统一写在 gradle/libs.versions.toml 里（改版本只改那一个文件）。
// ============================================================
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
