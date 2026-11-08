package com.qingji.app.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.qingji.app.domain.ImageSizeCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * ============================================================
 * 拍照 → 压缩 → Base64（M3.2）
 * ------------------------------------------------------------
 * 需求 7.3 明确要求「长边 1024px、JPEG 80」，能省约 60% 的 token。
 * 两件事同时做：
 * 1. 算出缩放后的字节，转 Base64 交给 AI；
 * 2. 在 App 私有目录里留一份压缩后的图，路径写进 food_log.imagePath，
 *    以后翻记录能看回当时拍了什么（照片绝不进公共相册）。
 *
 * 没引 CameraX：用系统相机 Intent + FileProvider 就够了，
 * 依赖少、不用管预览生命周期、包体积也小。
 * ============================================================
 */
object ImageRecorder {

    /** 压缩后的结果。base64 用于上传，path 用于本地留档。 */
    data class ImageResult(
        val base64: String,
        val path: String,
        val width: Int,
        val height: Int,
        val bytes: Int
    )

    /** 给系统相机用的临时文件，放在 cache 里，用完系统会自动回收。 */
    fun createTempFile(context: Context): File {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        return File(dir, "camera_${System.currentTimeMillis()}.jpg")
    }

    /** 把私有文件包装成系统相机能写的 Uri，只授权这一个文件。 */
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * 日记选的照片：复制一份到 App 私有目录，返回路径。
     * 不复制的话，系统相册给的临时 Uri 重启后就失效了，日记里的图会丢。
     */
    suspend fun copyToPrivate(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val dest = File(dir, "diary_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: return@withContext null
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从「拍好的照片」里读条码（M3.4）。
     * 用 ZXing 纯 Java 解码，不引 ML Kit（那会让 APK 从 18MB 涨到 40MB）。
     * 只解 EAN-13 / EAN-8 / UPC-A / Code128，够覆盖食品包装。
     */
    suspend fun decodeBarcode(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(context, uri, 1600) ?: return@withContext null
        try {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val source = RGBLuminanceSource(w, h, pixels)
            val hints = mapOf<DecodeHintType, Any>(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.EAN_13, BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A, BarcodeFormat.CODE_128
                ),
                DecodeHintType.TRY_HARDER to true
            )
            val reader = MultiFormatReader()
            val text = reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
            reader.reset()
            text
        } catch (e: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** 解码一张不太大的图给 ZXing 用，避免 4000px 大图把内存撑爆。 */
    private fun decodeBitmap(context: Context, uri: Uri, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageSizeCalculator.sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    /**
     * 把 uri 指向的图片内容**抢下来**存成本地文件。
     *
     * 为什么要有这一步（v2.3.2 修的核心）：
     * 相册、文件管理器、云盘给的 uri 行为五花八门——有的只能读一次，
     * 有的第一次读要触发下载，有的直接读不了。以前压缩和预览**各自读一遍 uri**，
     * 任何一遍失败就「打不开这张图片」。
     *
     * 现在只读 uri 一次，先落成本地文件；之后压缩、预览全部走文件，
     * 不再碰 uri。失败时把具体原因带出来，方便定位。
     */
    suspend fun copyToStable(context: Context, uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val dest = File(dir, "src_${System.currentTimeMillis()}.jpg")
            val input: java.io.InputStream? = try {
                context.contentResolver.openInputStream(uri)
            } catch (e: Exception) {
                return@withContext Result.failure(
                    IllegalStateException("打不开这张图片（${e.javaClass.simpleName}）")
                )
            }
            input ?: return@withContext Result.failure(
                IllegalStateException("打不开这张图片：系统没有给出可读的内容")
            )
            input.use { stream ->
                FileOutputStream(dest).use { output -> stream.copyTo(output) }
            }
            if (dest.length() == 0L) {
                dest.delete()
                return@withContext Result.failure(IllegalStateException("这张图片是空的，换一张试试"))
            }
            Result.success(dest)
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException("打不开这张图片：${e.message?.take(60) ?: "未知错误"}")
            )
        }
    }

    /** 基于本地文件的压缩（不再经过 uri，怎么读都不会被系统卡住）。 */
    suspend fun compressFileToBase64(context: Context, file: File): Result<ImageResult> = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            FileInputStream(file).use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0) {
                return@withContext Result.failure(IllegalStateException("打不开这张图片"))
            }

            val sample = ImageSizeCalculator.sampleSize(bounds.outWidth, bounds.outHeight)
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = FileInputStream(file).use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@withContext Result.failure(IllegalStateException("读取图片失败"))

            val target = ImageSizeCalculator.scaled(raw.width, raw.height)
            val scaled = if (target.width == raw.width && target.height == raw.height) {
                raw
            } else {
                Bitmap.createScaledBitmap(raw, target.width, target.height, true)
            }

            val bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.toByteArray()
            }

            // 留档到 App 私有目录（卸载 App 会一起删掉，不污染相册）
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val saved = File(dir, "food_${System.currentTimeMillis()}.jpg")
            saved.writeBytes(bytes)
            if (scaled !== raw) scaled.recycle()
            if (raw !== scaled) raw.recycle()

            Result.success(
                ImageResult(
                    base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                    path = saved.absolutePath,
                    width = target.width,
                    height = target.height,
                    bytes = bytes.size
                )
            )
        } catch (e: OutOfMemoryError) {
            Result.failure(IllegalStateException("图片太大，内存不够；换一张小图试试"))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("处理图片失败：${e.message?.take(80) ?: "未知错误"}"))
        }
    }

    /**
     * 读取 Uri 指向的图片 → 等比缩到长边 1024 → JPEG 80 → Base64。
     * 全程在后台线程，避免大图解码把界面卡住。
     */
    suspend fun compressToBase64(context: Context, uri: Uri): Result<ImageResult> = withContext(Dispatchers.IO) {
        try {
            // 第一遍只解尺寸，不把整张图读进内存
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return@withContext Result.failure(IllegalStateException("打不开这张图片"))

            val sample = ImageSizeCalculator.sampleSize(bounds.outWidth, bounds.outHeight)
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@withContext Result.failure(IllegalStateException("读取图片失败"))

            val target = ImageSizeCalculator.scaled(raw.width, raw.height)
            val scaled = if (target.width == raw.width && target.height == raw.height) {
                raw
            } else {
                Bitmap.createScaledBitmap(raw, target.width, target.height, true)
            }

            val bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.toByteArray()
            }

            // 留档到 App 私有目录（卸载 App 会一起删掉，不污染相册）
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val saved = File(dir, "food_${System.currentTimeMillis()}.jpg")
            saved.writeBytes(bytes)
            if (scaled !== raw) scaled.recycle()
            if (raw !== scaled) raw.recycle()

            Result.success(
                ImageResult(
                    base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                    path = saved.absolutePath,
                    width = target.width,
                    height = target.height,
                    bytes = bytes.size
                )
            )
        } catch (e: OutOfMemoryError) {
            Result.failure(IllegalStateException("图片太大，内存不够；换一张小图试试"))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("处理图片失败：${e.message?.take(80) ?: "未知错误"}"))
        }
    }
}
