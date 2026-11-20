package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.qingji.app.data.local.entity.WeightLog
import com.qingji.app.data.repository.QingJiRepository
import java.time.LocalDate

/** 体重记录页。 */
class WeightViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    suspend fun latest(): WeightLog? = repo.latestWeight()

    suspend fun save(weightKg: Double, bodyFat: Double?, note: String, date: LocalDate) {
        repo.saveWeight(
            WeightLog(
                date = date,
                weightKg = weightKg,
                bodyFat = bodyFat,
                note = note.ifBlank { null }
            )
        )
    }
}
