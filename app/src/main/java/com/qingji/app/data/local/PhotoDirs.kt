package com.qingji.app.data.local

import android.content.Context
import java.io.File

/**
 * 本地图片目录名与清理逻辑（TASK-03）。
 *
 * 以前「写」和「清」各写一份字符串：进度照片落在 filesDir/progress，
 * 恢复出厂的清理清单却只有 filesDir/images 和 cacheDir/images，
 * 结果点了恢复出厂，进度照片文件还留在私有目录里。
 * 现在两处都引用同一组常量，清单里也补上 progress。
 *
 * 边界：
 * - 备份目录 getExternalFilesDir/backup 不在这份清单里，永远不清；
 * - clearRecords 是按记录里的路径删单文件（deleteImageFile），头像要豁免。
 * 抽成独立对象是为了单测能直接拿 Robolectric 的临时目录验证，
 * 不必构造 QingJiRepository（它硬编码 AppDatabase.get(context)，无法注入内存 Room）。
 */
object PhotoDirs {
    const val PROGRESS_DIR = "progress"
    const val IMAGES_DIR = "images"

    /**
     * 恢复出厂要整目录重建的图片目录清单，沿用 deleteRecursively() + mkdirs() 写法。
     * includeProgress 默认 true：恢复出厂时进度照片一起清。
     */
    fun clearDirs(context: Context, includeProgress: Boolean = true) {
        val dirs = mutableListOf(
            File(context.filesDir, IMAGES_DIR),
            File(context.cacheDir, IMAGES_DIR)
        )
        if (includeProgress) dirs.add(File(context.filesDir, PROGRESS_DIR))
        dirs.forEach { dir ->
            runCatching { dir.deleteRecursively() }
            runCatching { dir.mkdirs() }
        }
    }

    /** 删一张记录图；头像路径要放过，别把用户头像一起删了。 */
    fun deleteImageFile(path: String, avatarPath: String) {
        if (path.isBlank()) return
        if (avatarPath.isNotBlank() && path == avatarPath) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}
