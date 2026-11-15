package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock

/** AI 分析（M3.8）：今日复盘 / 周报。数字由仓库层聚合，模型只负责说人话。 */
class AiAnalysisViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    suspend fun daily(): Result<String> {
        val today = AppClock.today()
        return repo.analyze(repo.buildDailyFacts(today), today)
    }

    suspend fun weekly(): Result<String> {
        val today = AppClock.today()
        return repo.analyze(repo.buildWeeklyFacts(), today)
    }
}
