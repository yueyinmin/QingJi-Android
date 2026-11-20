package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.remote.AiRecognitionResult
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock

/** 图片识别（M3.2）。压缩后的 Base64 由界面传进来。 */
class ImageRecordViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    suspend fun recognize(base64Jpeg: String): Result<AiRecognitionResult> =
        repo.recognizeImage(base64Jpeg, AppClock.today())

    /** v3.4.0：拍照识别同样记名字纠错。 */
    suspend fun save(
        logs: List<FoodLog>,
        nameCorrections: List<Pair<String, String>> = emptyList()
    ) {
        repo.rememberAiNameCorrections(nameCorrections)
        repo.addFoodLogs(logs)
    }
}
