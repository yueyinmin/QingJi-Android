package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.qingji.app.data.local.entity.Diary
import com.qingji.app.data.repository.QingJiRepository

/** 日记编辑（M5）：保存时把当天体重顺手关联上。 */
class DiaryEditViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    suspend fun latestWeight(): Double? = repo.latestWeight()?.weightKg

    suspend fun load(id: Long): Diary? = repo.findDiary(id)

    suspend fun save(diary: Diary) = repo.saveDiary(diary)
}
