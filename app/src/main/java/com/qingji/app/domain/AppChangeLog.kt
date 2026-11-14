package com.qingji.app.domain

/**
 * ============================================================
 * 版本更新记录（唯一数据源）
 * ------------------------------------------------------------
 * 两个地方读同一份数据，保证「更新提示」里看到的内容和
 * 「设置 → 版本信息」里留下的记录永远一致：
 *   1. 升级后第一次打开 App 的一次性弹窗；
 *   2. 版本信息页底部的「更新记录」。
 *
 * 加新版本时只在这里最上面插一条 Release，别在界面里手写文案。
 * versionCode 必须和 app/build.gradle.kts 里的 versionCode 对得上。
 * ============================================================
 */
object AppChangeLog {

    data class Release(
        val versionName: String,
        val versionCode: Int,
        val date: String,
        val items: List<String>
    )

    /** 按版本号倒序：最新的放最上面。 */
    val releases: List<Release> = listOf(
        Release(
            versionName = "1.1.1",
            versionCode = 112,
            date = "2026-11-14",
            items = listOf(
                "新增「自律管理」：多目标计时、每日打卡、冲动记录、目标偏离与撤销、历史日历",
                "首页新增「今日自律」菜单：目标、已坚持天数、今日状态 + 打卡 / 记冲动 / 历史三个入口",
                "一级页面支持左右滑动切换（可在设置 → 页面交互关闭），切换带方向动画",
                "「我的」与设置页图标统一为圆角方形浅底 + 彩色线性图标，卡片圆角全站统一 20dp",
                "打卡的诱因 / 应对方式 / 情绪支持自建选项，自律三个子模块共用同一份",
                "进展页「饮食 30 天」搬到训练日记底部，「本周运动消耗」搬到首页本周小结上方",
                "首页板块顺序调整：今日记录排在今日训练之前；使用情况改为两列四排紧凑卡片",
                "修复深色模式：设置页背景、分割线、状态胶囊、清除记录按钮不再发白",
                "适配小屏（360dp）：悬浮按钮自动缩小，月历与多张卡片右侧不再被按钮遮挡"
            )
        ),
        Release(
            versionName = "1.1.0",
            versionCode = 111,
            date = "2026-11-13",
            items = listOf(
                "新增「训练中心」：开始训练、记录每组重量 / 次数 / RPE，自动带出上次成绩",
                "训练日记：月历、本月总结、历史明细；当天可修改，历史可删除",
                "首页新增「今日训练」卡；＋面板新增「开始训练」「记录有氧」",
                "日记页新增「心情日记 / 训练日记」切换；我的页新增「训练管理」",
                "备份升级到 v3，训练记录可随备份换机；清除记录 / 恢复出厂同步覆盖训练数据",
                "新增版本更新提示，更新记录保留在「设置 → 版本信息」",
                "P1 补充：7 类计划模板、自定义计划、动作库/收藏/详情、个人最佳、首页今日安排",
                "日记页布局定版：默认进训练日记、训练日记在左心情日记在右、心情月历去掉灰块、悬浮快捷按钮两个页面都显示",
                "修复：覆盖安装后更新提示不弹、进展页身体数据发灰、水杯白圆与长方形点击高亮"
            )
        ),
        Release(
            versionName = "1.0.9",
            versionCode = 109,
            date = "2026-11-13",
            items = listOf(
                "应用图标三套真实切换、通知总开关联动闹钟、权限授权后才开启",
                "全局 UI 视觉统一（主色、底色、文字色对齐规范）",
                "备份覆盖恢复语义收口、迁移链自动测试、小组件定向刷新"
            )
        ),
        Release(
            versionName = "1.0.8",
            versionCode = 108,
            date = "2026-11-12",
            items = listOf(
                "设置页极简单行改版、8 分组 24 项、真搜索、识别偏好二级页"
            )
        )
    )

    /** 当前版本对应的那一条记录；没登记就返回 null（弹窗会显示通用文案）。 */
    fun current(currentCode: Int): Release? = releases.firstOrNull { it.versionCode == currentCode }

    /**
     * 从「上次看过」到「现在」之间漏掉的几个版本。
     * 用户跳过好几个版本没打开时，一次弹窗把中间的更新一起列出来，不弹好几次。
     */
    fun since(lastSeenCode: Int, currentCode: Int): List<Release> =
        releases.filter { it.versionCode > lastSeenCode && it.versionCode <= currentCode }

    /** 版本信息页底部的「更新记录」纯文本（这个页面本来就展示一整段文字）。 */
    fun versionInfoText(): String = buildString {
        append("更新记录\n")
        releases.forEach { r ->
            append("\n")
            append("v${r.versionName}（${r.date}）\n")
            r.items.forEach { item -> append("· $item\n") }
        }
    }.trimEnd()
}
