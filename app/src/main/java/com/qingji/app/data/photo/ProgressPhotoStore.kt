package com.qingji.app.data.photo

import android.content.Context
import android.net.Uri
import com.qingji.app.data.local.PhotoDirs
import java.io.File

/**
 * ============================================================
 * 进展页照片的存取（TASK-06-B）
 * ------------------------------------------------------------
 * 以前「把相册选的图拷进私有目录」这件事写在 ProgressScreen.kt 里
 * （一个 private fun），UI 文件直接做文件 IO：
 *
 *   - UI 层碰 FileOutputStream / contentResolver，测不了也不好复用；
 *   - 目录名散落在 UI 和 PhotoDirs 两处，谁改谁漏。
 *
 * 现在统一收到这里：目录名只认 PhotoDirs.PROGRESS_DIR，读写删都在一处，
 * UI 只拿到一个路径字符串。
 *
 * 行为与改造前完全一致：文件名仍是 photo_<时间戳>.jpg，失败返回 null，
 * 目录不存在先 mkdirs。不改动任何历史照片的相对路径。
 * ============================================================
 */
class ProgressPhotoStore(private val context: Context) {

    /** 进度照片所在目录（filesDir/progress），不存在就建好。 */
    fun directory(): File =
        File(context.filesDir, PhotoDirs.PROGRESS_DIR).apply { mkdirs() }

    /**
     * 把相册 / 相机的 Uri 拷成私有目录里的一个文件，返回绝对路径。
     * 任何一步失败（Uri 不可读、空间不足）都返回 null，由调用方决定提示文案。
     */
    fun saveFromUri(uri: Uri): String? = runCatching {
        val dir = directory()
        val target = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        target.absolutePath
    }.getOrNull()

    /** 删掉一张进度照片（记录里删图时用）；路径为空或文件不存在都当成功。 */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}
