package com.qingji.app.ui.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * ============================================================
 * 备份文件的系统分享（TASK-06-C）
 * ------------------------------------------------------------
 * 这段代码原本长在 BackupManager（纯数据层）里：一个「生成 / 解析 JSON、
 * 落库、算缓存」的类，末尾却 `context.startActivity(chooser)`。
 * 数据层碰 Intent 有三个坏处：
 *
 *   1. 单测里跑不了（没有 Activity 上下文，startActivity 直接抛）；
 *   2. 分享属于「平台适配」，换 UI 形态（比如以后加桌面小组件入口）就得改数据层；
 *   3. 读代码的人以为备份管理器和屏幕强绑定。
 *
 * 现在搬到 ui/platform 下：调用方是 ViewModel / 页面，数据层只负责产出文件。
 *
 * 行为一个字节都没变：同一个 FileProvider authority、同样的 MIME、
 * 同样的 EXTRA_SUBJECT、同样带 NEW_TASK 的 chooser。
 * ============================================================
 */
object BackupShare {

    /** 用系统分享面板把导出的文件发出去（微信、QQ、网盘、邮箱都能选）。 */
    fun send(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension == "csv") "text/csv" else "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "发送备份文件").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
