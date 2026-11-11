package com.qingji.app.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * ============================================================
 * 轻量界面双语（v3.5.0，S4「核心页中英双语」）
 * ------------------------------------------------------------
 * 设计取舍（为什么不走 strings.xml + stringResource 全量抽取）：
 * - 全 App 几百处文案都是 Kotlin 硬编码中文，逐条抽 key 的工作量和漏译风险都大；
 * - 这里用「中文原文本身当 key」：中文模式零成本直出，英文模式查表，
 *   表里没有的条目自动回落中文——不会因为漏了一条就显示 key 或报错；
 * - 接入点只改一处写法：Text("首页") → Text(s("首页"))。
 *
 * 覆盖范围（S4 承诺的核心页）：底部导航、首页主结构、食物库、设置主列表、
 * 通用弹窗按钮；日记页 / 二级详情页的长文案先保留中文，后续再补。
 * ============================================================
 */

/** 界面语言：zh / en，由 MainActivity 从 DataStore 读出来后放到 CompositionLocal。 */
val LocalUiLang = compositionLocalOf { "zh" }

/** 英文表：key = 中文原文，value = 英文。没收录的条目回落中文。 */
private val EN: Map<String, String> = mapOf(
    // ---------- 底部导航 / 通用 ----------
    "首页" to "Home",
    "进展" to "Progress",
    "日记" to "Diary",
    "我的" to "Me",
    "记一笔" to "Quick add",
    "返回" to "Back",
    "保存" to "Save",
    "取消" to "Cancel",
    "确定" to "OK",
    "知道了" to "Got it",
    "删除" to "Delete",
    "搜索" to "Search",
    "完成" to "Done",
    "关闭" to "Close",
    "去设置" to "Set up",
    "设置" to "Settings",
    "加入" to "Add",
    "记录" to "Log",

    // ---------- 首页 ----------
    "今日记录" to "Today's log",
    "当日记录" to "Day's log",
    "回到今天" to "Back to today",
    "选择日期" to "Pick a date",
    "记体重" to "Log weight",
    "今日饮水" to "Today's water",
    "水和饮食一样，记了才有趋势" to "Water works like food: log it to see trends",
    "快速记录喝水" to "Quick water",
    "输入自定义水量" to "Custom amount",
    "填 1～3000 之间的数字" to "Enter a number between 1 and 3000",
    "查看今日饮水详情" to "Water details",
    "今日剩余" to "Remaining today",
    // 「减脂」是统一叫法：以前首页写「减肥」、引导和进展页写「减脂」，
    // 同一个状态两种说法，用户会以为是两种模式。
    "减脂" to "Fat loss",
    "营养素 (g)" to "Macros (g)",
    "蛋白质" to "Protein",
    "碳水" to "Carbs",
    "脂肪" to "Fat",
    "暂无记录" to "No records yet",
    "周一" to "Mon",
    "周二" to "Tue",
    "周三" to "Wed",
    "周四" to "Thu",
    "周五" to "Fri",
    "周六" to "Sat",
    "周日" to "Sun",
    "早餐" to "Breakfast",
    "午餐" to "Lunch",
    "晚餐" to "Dinner",
    "加餐" to "Snack",
    "千卡" to "kcal",

    // ---------- 食物库 ----------
    "标准食物库" to "Food library",
    "自定义食物" to "My foods",
    "吃过的" to "History",
    "我的收藏" to "Favorites",
    "搜索食物" to "Search food",
    "搜食物名或拼音首字母（如 sp）" to "Search by name or initials (e.g. sp)",
    "家庭厨房" to "Home cooking",
    "外卖快餐" to "Takeout & fast food",
    "标准" to "Library",
    "自定义" to "Custom",
    "加到哪一餐" to "Add to which meal",
    "记到哪一天" to "Log to which day",
    "份量（克）" to "Portion (g)",
    "食物热量按常见值估算，日常记账够用，别当营养学依据" to
        "Calories are typical estimates, good for logging but not nutritional or medical advice",

    // ---------- 设置 · 分组 ----------
    "账号与安全" to "Account & privacy",
    "热量与营养" to "Calories & nutrition",
    "记录与识别" to "Logging & AI",
    "提醒" to "Reminders",
    "通用" to "General",
    "显示与外观" to "Display & appearance",
    "设备与数据" to "Device & data",

    // ---------- 设置 · 条目 ----------
    "个人资料" to "Profile",
    "昵称、性别、生日、身高" to "Nickname, gender, birthday, height",
    "隐私与安全" to "Privacy & security",
    "饮食偏好与忌口" to "Diet preferences",
    "营养素分配方案" to "Macro split",
    "蛋白 / 碳水 / 脂肪 比例" to "Protein / carb / fat ratio",
    "运动消耗补偿" to "Exercise bonus",
    "把运动消耗加回每日预算" to "Add exercise burn back to the daily budget",
    "饮水目标" to "Water goal",
    "自动识别餐次" to "Auto meal detection",
    "AI 按时间猜餐次，可以关掉" to "AI guesses the meal by time; turn off to pick manually",
    "估算偏保守" to "Conservative estimates",
    "拿不准时按低一点的热量算" to "Round calories down when uncertain",
    "AI 识别语言" to "AI reply language",
    "识别结果返回的语言" to "Language of AI recognition results",
    "通知与提醒" to "Notifications & reminders",
    "称重、断食、热量、喝水四类提醒的开关与时间" to "Four reminders: weigh-in, fasting, logging, water",
    "记录时区" to "Logging timezone",
    "记录按哪个时区的日期归档" to "Which timezone defines a \"day\"",
    "体重单位" to "Weight unit",
    "界面语言" to "App language",
    "核心页中英文切换，未翻译的仍显示中文" to "Core screens in English; the rest stays Chinese",
    "主题与显示" to "Theme & display",
    "主题色、深色模式、自定义字体" to "Accent color, dark mode, custom font",
    "桌面小组件" to "Home-screen widgets",
    "底部导航" to "Bottom tabs",
    "AI 服务配置" to "AI service",
    "数据备份与导出" to "Backup & export",
    // v1.0.6：清除数据拆成两档，菜单词条跟着改
    "清除记录" to "Clear records",
    "恢复出厂设置" to "Factory reset",
    "清除全部记录" to "Clear all records",
    "关于轻记" to "About",

    // ---------- AI 语言 / 界面语言 选项 ----------
    "简体中文" to "Chinese (Simplified)",
    "跟随输入" to "Follow input",
    "English" to "English",
    "推荐：所有识别结果都是中文" to "Recommended: all results in Chinese",
    "AI 模型自行判断，可能返回英文" to "The model decides; may reply in English",
    "界面默认中文，核心页可切英文" to "Chinese by default; core screens switch to English",
    "切换后立即生效，未翻译的条目仍显示中文" to "Applies immediately; untranslated items stay Chinese",

    // ---------- 2026-11-11 双语补全：四个主 Tab + 记录链路 ----------
    "+ 添加" to "+ Add",
    "+ 记一次" to "+ Log one",
    "BMI 分级" to "BMI scale",
    "token 与费用以服务商账单为准，本地只记调用次数 · 数据只存在本机" to "Tokens and cost follow your provider's bill; only call counts are stored locally · data stays on device",
    "✦ AI 分析" to "✦ AI analysis",
    "个人资料还没填完整，进度可能显示为 0" to "Profile incomplete; progress may show 0",
    "中国标准" to "China standard",
    "体重走势" to "Weight trend",
    "使用情况" to "Usage",
    "保存后按新目标重算每日热量与三大营养素（公式：Mifflin-St Jeor）。" to "Saving recalculates daily calories and macros from the new goal (Mifflin-St Jeor).",
    "写一篇" to "Write",
    "减脂进展" to "Fat-loss progress",
    "千卡/100g" to "kcal/100g",
    "只有 1 条记录，再记几次就能连成曲线" to "Only one record; log a few more to draw a line",
    "按最近 7 天平均算；没记的日子也计入分母" to "Averaged over the last 7 days; days without records still count",
    "数据" to "Data",
    "最新" to "Latest",
    "最近搜过" to "Recent searches",
    "本区间变化" to "Change in range",
    "本周运动消耗" to "Exercise this week",
    "查看全部" to "View all",
    "标准食物库没读出来。点下面的按钮重新检查一次，还不行就重启 App 试试。" to "The food catalog failed to load. Tap below to recheck, or restart the app.",
    "清空" to "Clear",
    "点这里从相册选一张" to "Tap to pick one from gallery",
    "玻璃罐" to "Glass jar",
    "蛋白" to "Protein",
    "记体重 ›" to "Log weight ›",
    "记到哪一餐" to "Which meal",
    "调用统计" to "Call stats",
    "身体数据" to "Body data",
    "身体维度" to "Measurements",
    "近 30 天" to "Last 30 days",
    "近一周营养摄入" to "Nutrition this week",
    "近一周饮水" to "Water this week",
    "还没有日记，点右下角「写一篇」" to "No entries yet; tap Write at the bottom right",
    "还没有饮水记录" to "No water records yet",
    "这里的数字就是你实际使用轻记的记录，方便回头看自己的习惯" to "These are your actual usage records, handy for looking back at habits",
    "进度照片" to "Progress photos",
    "重新检查食物库" to "Recheck catalog",
    "量一次腰围、胸围、臀围，变化比体重更早看得出来" to "Measure waist, chest and hips — changes show earlier than weight",
    "食物库正在载入，稍等一下下…" to "Loading food catalog…",
    "保持" to "Maintain",
    "增肌" to "Bulk",
)


/**
 * 取当前语言的文案。中文直接返回原文；英文查表，查不到回落中文。
 * 这是 @Composable 版本（读 CompositionLocal，语言切换后界面自动重组）。
 */
@Composable
fun s(zh: String): String {
    val lang = LocalUiLang.current
    return if (lang == "en") EN[zh] ?: zh else zh
}

/** 非 Composable 场景（如 enum 定义处）用的静态版本；一般只在 BottomBar 内部兜底用。 */
fun sStatic(zh: String, lang: String): String =
    if (lang == "en") EN[zh] ?: zh else zh
