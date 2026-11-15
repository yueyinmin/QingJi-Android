package com.qingji.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** 「吃过的」列表里的一条：按食物名聚合出来的。 */
data class EatenFood(
    val name: String,
    val count: Int,
    val lastDate: java.time.LocalDate,
    val kcalPer100g: Double
)
