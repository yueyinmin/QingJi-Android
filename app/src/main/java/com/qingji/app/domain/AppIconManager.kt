package com.qingji.app.domain

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * v1.1.0：启动器图标切换。
 * ------------------------------------------------------------
 * 三套图标各对应 AndroidManifest 里的一个 activity-alias，
 * MainActivity 本身不再直接带 LAUNCHER intent-filter，
 * 同一时刻只启用一个别名 —— 这是系统层面切换图标的唯一正规做法。
 * 选中项持久化在 DataStore（SettingsKeys.ICON_VARIANT），重启后保持。
 */
object AppIconManager {

    /** 可选图标 key，顺序即设置页展示顺序。default = 现有薄荷绿。 */
    val VARIANTS = listOf("default", "forest", "cream")

    /** 每套图标对应的 activity-alias 类名（必须与 AndroidManifest 完全一致）。 */
    fun aliasClass(variant: String): String = when (variant) {
        "forest" -> "com.qingji.app.LauncherAliasForest"
        "cream" -> "com.qingji.app.LauncherAliasCream"
        else -> "com.qingji.app.LauncherAliasDefault"
    }

    /** 设置页展示用的中文名。 */
    fun label(variant: String): String = when (variant) {
        "forest" -> "森林绿"
        "cream" -> "暖阳奶油"
        else -> "薄荷绿"
    }

    /**
     * 启用选中的别名、禁用其余两个。
     * DONT_KILL_APP 只是「尽量别杀进程」，个别 ROM 仍可能重启 App；
     * 数据都在 DataStore / Room，不会丢。桌面图标刷新由系统负责，
     * 通常立即生效，个别桌面（第三方 launcher）可能延迟几秒。
     */
    fun apply(context: Context, variant: String) {
        val pm = context.packageManager
        val chosen = aliasClass(variant)
        for (v in VARIANTS) {
            val cn = ComponentName(context.packageName, aliasClass(v))
            val state =
                if (aliasClass(v) == chosen) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
        }
    }
}
