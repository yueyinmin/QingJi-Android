package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import java.time.LocalDate

/** AI 分析（M3.8）：今日/历史复盘 + 周报。数字由仓库层聚合，模型只负责说人话。 */
class AiAnalysisViewModel(app: Application) : AndroidViewModel(app) {

    private var repo = QingJiRepository(app)

    /** QJ-AI-HISTORY-DATE-V1：仅单测注入仓库（生产仍走上面的构造入口）。 */
    internal constructor(app: Application, repoOverride: QingJiRepository) : this(app) {
        repo = repoOverride
    }

    /**
     * QJ-AI-HISTORY-DATE-V1：dataDate（分析哪天）由首页传入，usageDate（额度/统计记哪天）
     * 固定为实际调用当天。禁止把 dataDate 传给 analyze 的 usageDate 参数，否则历史分析
     * 会把调用次数记到历史日期、并绕过当天额度。
     */
    suspend fun daily(dataDate: LocalDate): Result<String> {
        return repo.analyze(repo.buildDailyFacts(dataDate), AppClock.today())
    }

    /** 周报口径不变：统计范围与额度/统计日期都锚定实际调用当天。 */
    suspend fun weekly(): Result<String> {
        val today = AppClock.today()
        return repo.analyze(repo.buildWeeklyFacts(), today)
    }
}
