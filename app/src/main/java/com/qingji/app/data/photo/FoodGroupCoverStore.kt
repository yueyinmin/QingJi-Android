package com.qingji.app.data.photo

import android.content.Context
import android.net.Uri
import com.qingji.app.data.local.PhotoDirs
import java.io.File

/**
 * v1.6：自定义食物组封面的存取。
 * 照 ProgressPhotoStore 的范式：相册选的 Uri 拷进应用私有目录，
 * 数据库只存返回的路径字符串，避免相册 URI 失效后封面打不开。
 *
 * 封面段随备份走的是「路径字符串」；换机后图片文件本身不在备份里，
 * 这与进度照片的处理口径一致（界面会回退到占位图标，不会崩）。
 */
class FoodGroupCoverStore(private val context: Context) {

    fun directory(): File =
        File(context.filesDir, PhotoDirs.FOOD_GROUP_DIR).apply { mkdirs() }

    /** 把相册 Uri 拷成私有目录文件，返回绝对路径；失败返回 null。 */
    fun saveFromUri(uri: Uri): String? = runCatching {
        val target = File(directory(), "cover_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        target.absolutePath
    }.getOrNull()

    /** 删封面文件；空路径或不存在都当成功。 */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}
