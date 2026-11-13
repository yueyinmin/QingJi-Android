package com.qingji.app.diagnostics

import android.content.Context
import android.os.Build
import org.json.JSONObject

/**
 * ============================================================
 * 设备 / 应用信息收集（任务书 5.1：只收集必要字段）
 * ------------------------------------------------------------
 * 白名单字段，写死的就这几项：
 *   设备制造商、型号、Android 版本、API Level、CPU ABI、
 *   App 版本、包名、导出时间。
 * 绝不收：姓名、手机号、位置、任何用户记录。
 * ============================================================
 */
object DiagnosticInfoProvider {

    fun appInfo(context: Context, exportTime: Long): JSONObject = JSONObject().apply {
        val pm = context.packageManager
        val pkg = pm.getPackageInfo(context.packageName, 0)
        put("app_name", "轻记 QingJi")
        put("package_name", context.packageName)
        put("version_name", pkg.versionName ?: "")
        put("version_code", pkg.longVersionCode)
        put("export_time", AppLogger.dateFormat().format(java.util.Date(exportTime)))
    }

    fun deviceInfo(): JSONObject = JSONObject().apply {
        put("manufacturer", Build.MANUFACTURER ?: "")
        put("model", Build.MODEL ?: "")
        put("brand", Build.BRAND ?: "")
        put("android_version", Build.VERSION.RELEASE ?: "")
        put("api_level", Build.VERSION.SDK_INT)
        put("cpu_abi", Build.SUPPORTED_ABIS?.firstOrNull() ?: "")
    }
}
