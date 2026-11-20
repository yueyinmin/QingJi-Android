package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.remote.AiRecognitionResult
import com.qingji.app.data.repository.QingJiRepository
import java.time.LocalDate

/** 文字 AI 识别页。识别结果的可编辑列表放在界面里，这里只管发请求和落库。 */
class TextAiViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    suspend fun recognize(input: String, date: LocalDate): Result<AiRecognitionResult> =
        repo.recognizeText(input, date)

    /** v3.4.0：保存这一餐时顺手记下用户改过的名字，下次识别自动套用。 */
    suspend fun save(
        logs: List<FoodLog>,
        nameCorrections: List<Pair<String, String>> = emptyList()
    ) {
        repo.rememberAiNameCorrections(nameCorrections)
        repo.addFoodLogs(logs)
    }
}
