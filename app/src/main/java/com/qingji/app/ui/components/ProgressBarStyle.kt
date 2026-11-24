package com.qingji.app.ui.components

/**
 * 七种进度条特效样式（QJ-PROGRESS-V2 新增）。
 *
 * key 直接作为 DataStore 字符串持久化；未知/老数据一律回落 SIMPLE，
 * 保证未来新增样式或数据损坏时不会出现空指针。
 */
enum class ProgressBarStyle(
    val key: String,
    val label: String,
    val description: String
) {
    SIMPLE("simple", "简洁细条", "静态填充，不消耗动画资源"),
    COMET("comet", "彗星拖尾", "末端光核与渐变尾迹"),
    SHIMMER("shimmer", "流光能量", "柔和扫描高光掠过填充"),
    LIQUID("liquid", "液体波浪", "双层波浪与上升气泡"),
    STRIPE("stripe", "动感斑纹", "斜向条纹持续流动"),
    AURORA("aurora", "极光流彩", "青蓝与主题绿交融的渐变"),
    PULSE("pulse", "脉冲心跳", "双光环缓慢呼吸");

    companion object {
        const val DEFAULT_KEY = "simple"

        /** 未知 key 回落 SIMPLE，保护已持久化的老数据。 */
        fun fromKey(key: String?): ProgressBarStyle =
            entries.firstOrNull { it.key == key } ?: SIMPLE
    }
}
