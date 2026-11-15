package com.qingji.app.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * TASK-03：恢复出厂的图片清理边界。
 *
 * QingJiRepository 构造硬编码 AppDatabase.get(context)，没法注入内存 Room，
 * 所以清目录 / 删单文件都抽到 [PhotoDirs]，这里直接用 Robolectric 的临时目录验证，
 * 不构造 Repository。
 */
@RunWith(RobolectricTestRunner::class)
class PhotoDirsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 每个用例从干净目录开始，避免用例之间互相污染
        listOf(
            File(context.filesDir, PhotoDirs.IMAGES_DIR),
            File(context.cacheDir, PhotoDirs.IMAGES_DIR),
            File(context.filesDir, PhotoDirs.PROGRESS_DIR)
        ).forEach { it.deleteRecursively() }
    }

    private fun touch(file: File) {
        file.parentFile?.mkdirs()
        file.writeText("fake-image")
        assertTrue("构造失败：$file", file.exists())
    }

    @Test
    fun `恢复出厂清目录时 进度照片被删掉`() {
        val progressPhoto = File(context.filesDir, "${PhotoDirs.PROGRESS_DIR}/photo_1.jpg")
        touch(progressPhoto)

        PhotoDirs.clearDirs(context)

        assertFalse("filesDir/progress 里的照片必须随恢复出厂一起清掉", progressPhoto.exists())
    }

    @Test
    fun `恢复出厂清目录时 备份目录里的文件保留`() {
        val backupDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "backup")
        val backupFile = File(backupDir, "backup.json")
        touch(backupFile)

        PhotoDirs.clearDirs(context)

        assertTrue("getExternalFilesDir/backup 不在清理清单里，备份文件必须保留", backupFile.exists())
    }

    @Test
    fun `按目录清理不带 progress 时 进度照片保留`() {
        val progressPhoto = File(context.filesDir, "${PhotoDirs.PROGRESS_DIR}/photo_1.jpg")
        touch(progressPhoto)

        PhotoDirs.clearDirs(context, includeProgress = false)

        assertTrue("includeProgress=false 时不应碰进度照片", progressPhoto.exists())
    }

    @Test
    fun `clearRecords 的删文件 helper 对头像豁免`() {
        val avatar = File(context.filesDir, "${PhotoDirs.IMAGES_DIR}/avatar.jpg")
        val recordPhoto = File(context.filesDir, "${PhotoDirs.IMAGES_DIR}/a.jpg")
        touch(avatar)
        touch(recordPhoto)

        PhotoDirs.deleteImageFile(avatar.absolutePath, avatar.absolutePath)
        PhotoDirs.deleteImageFile(recordPhoto.absolutePath, avatar.absolutePath)

        assertTrue("头像路径要放过，clearRecords 不能把头像删了", avatar.exists())
        assertFalse("普通记录图要删掉", recordPhoto.exists())
    }
}
