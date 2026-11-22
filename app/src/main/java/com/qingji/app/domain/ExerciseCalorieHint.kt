package com.qingji.app.domain

/**
 * B1.1.1：运动消耗的三态提示。绝不把「没数据」和「算不出」都显示成 0 kcal。
 *
 * 状态区分：
 * - 未录入实际运动数据（没有任何可计算的实际值）
 * - 已录入，但缺结算体重 / 有效 MET / 可靠时长（参数不足）
 * - 已完成热量计算（有正数结果）
 */
object ExerciseCalorieHint {

    enum class State { NOT_RECORDED, MISSING_PARAMS, CALCULATED }

    fun state(
        hasActualData: Boolean,
        hasWeight: Boolean,
        result: WorkoutCalorieCalculator.Result
    ): State = when {
        result.totalKcal > 0.0 -> State.CALCULATED
        !hasActualData -> State.NOT_RECORDED
        else -> State.MISSING_PARAMS // hasWeight=false 或 MET/时长参数不足
    }

    /** 返回保存反馈文案（前缀含中文分号，直接接在「训练已保存」后）。 */
    fun message(
        hasActualData: Boolean,
        hasWeight: Boolean,
        result: WorkoutCalorieCalculator.Result
    ): String = when (state(hasActualData, hasWeight, result)) {
        State.CALCULATED ->
            "，预计消耗约 ${SessionCalorieCalculator.displayKcal(result.totalKcal)} 千卡（估算）"
        State.NOT_RECORDED -> "；未录入可用于计算的实际运动数据，暂未计算消耗"
        State.MISSING_PARAMS -> when {
            !hasWeight -> "；未记录结算体重，本次未估算消耗"
            result.hasSkipped -> "；已录入 ${result.notComputableItems} 项但缺少可靠时长/参数，暂未计算消耗"
            else -> "；运动参数不足，暂未计算消耗"
        }
    }
}
