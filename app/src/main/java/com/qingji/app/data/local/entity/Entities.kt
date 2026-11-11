package com.qingji.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * ============================================================
 * 用户数据库的表定义
 * ------------------------------------------------------------
 * 为什么放在一个文件里：每张表都很短，摊成一堆文件反而难找。
 * v1.0.4 起标准食物库拆成了独立的只读数据库，见 CatalogFood.kt；
 * 用户自己录的食物 / 收藏 / 搜索历史见 UserFood.kt。
 * ============================================================
 */

/**
 * 表 1：用户档案。
 * 只有一行数据，id 固定为 1，用 REPLACE 方式覆盖写入。
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val gender: String = "male",        // male / female
    val birthday: LocalDate? = null,     // 生日，用来算年龄
    val heightCm: Double = 175.0,        // 身高，厘米
    val startWeight: Double = 0.0,       // 起始体重，公斤
    val targetWeight: Double = 0.0,      // 目标体重，公斤
    val activityLevel: Int = 2,          // 1 久坐 / 2 轻度 / 3 中度 / 4 高度
    val dailyKcalGoal: Int = 0,          // 每日热量预算，0 表示还没算过
    val proteinGoalG: Int = 0,           // 每日蛋白目标克数
    val carbGoalG: Int = 0,              // 每日碳水目标克数
    val fatGoalG: Int = 0,               // 每日脂肪目标克数
    val waterGoalMl: Int = 2000,         // 每日饮水目标毫升
    // v3.5.0 营养素目标细分。defaultValue 必须和 MIGRATION_4_5 里的 DEFAULT 完全一致，
    // 否则 Room 运行时校验 schema 会不匹配，触发清库兜底。
    /** 每日膳食纤维目标（克），参考膳食指南成人约 25g */
    @ColumnInfo(defaultValue = "25")
    val fiberGoalG: Int = 25,
    /** 每日钠目标（毫克），参考指南成人 < 2000mg */
    @ColumnInfo(defaultValue = "2000")
    val sodiumGoalMg: Int = 2000,
    /** 每日钙目标（毫克），参考指南成人约 800mg */
    @ColumnInfo(defaultValue = "800")
    val calciumGoalMg: Int = 800,
    val unit: String = "kg",             // 展示单位：kg / jin（斤）
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 表 2：体重记录。
 * 一天只保留一条（date 加了唯一索引），同一天再记会覆盖。
 */
@Entity(
    tableName = "weight_log",
    indices = [Index(value = ["date"], unique = true)]
)
data class WeightLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val weightKg: Double,
    val bodyFat: Double? = null,         // 体脂率，没有就留空
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 界面层统一用的「食物」模型 —— 注意：它已经不是数据库表了。
 *
 * v1.0.4 拆库后，一个 Food 可能来自两个地方：
 *   · 标准库 food_catalog.db（只读，id 从 100001 起）
 *   · 用户库 qingji.db 的 user_food 表（可写，id 从 1 起）
 * Repository 把两边合并成同一个 Food 交给界面，所以所有页面的 import 都不用改。
 * 刻意保留原来的字段名，老代码（备份、AI 校正、搜索页）一行都不用动。
 */
data class Food(
    val id: Long = 0,
    val name: String,
    val pinyin: String = "",             // 拼音首字母，如 mf → 米饭
    val pinyinFull: String = "",         // 拼音全拼，如 mifan，只用于检索
    val aliases: String = "",            // 别名，多个用 | 分隔
    val barcode: String? = null,
    val kcalPer100g: Double,             // 每 100 克热量
    val proteinPer100g: Double = 0.0,
    val carbPer100g: Double = 0.0,
    val fatPer100g: Double = 0.0,
    /**
     * 纤维 / 钠 / 钙 / 糖：null = 未收录，0.0 = 确实测得为 0。
     * v1.0.6 之前这四列是非空 0.0，靠注释说「0 表示暂无数据」，
     * 结果界面分不出「不含」和「不知道」，只能一律当真值渲染。
     */
    val fiberPer100g: Double? = null,
    val sodiumMgPer100g: Double? = null,
    val calciumMgPer100g: Double? = null,
    val sugarPer100g: Double? = null,
    val defaultGrams: Double = 100.0,    // 默认一份是多少克
    val source: String = "preset",       // preset / user / api / openfoodfacts
    /** 食物库左侧导航的分类：主食 / 面食 / 家常菜 / 肉类 … */
    val category: String = "其他",
    val subcategory: String = "",
    val portionNote: String = "",
    val sourceId: String = "",           // 标准库数据来源编号，可追溯
    val auditStatus: Int = 0,            // 见 AuditStatus：0 未审 / 1 已核对 / 2 有出处
    val catalogVersion: String = "0.3",
    /** 外卖品牌（肯德基、麦当劳…）。有值时归到「外卖快餐」而不是「家庭厨房」 */
    val brand: String = "",
    /** 收藏：心形按钮 */
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 表 4：饮食记录（最核心的一张表）。
 * 一次记录 = 一条数据，例如"早餐吃了 100 克鸡蛋"。
 */
@Entity(
    tableName = "food_log",
    indices = [Index("date"), Index(value = ["date", "mealType"])]
)
data class FoodLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val mealType: String,                // breakfast / lunch / dinner / snack
    val foodName: String,
    val grams: Double,
    val kcal: Double,
    val proteinG: Double = 0.0,
    val carbG: Double = 0.0,
    val fatG: Double = 0.0,
    // v3.5.0：与 Food 的三列对应；来源数据没有时就是 0
    @ColumnInfo(defaultValue = "0.0")
    val fiberG: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0")
    val sodiumMg: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0")
    val calciumMg: Double = 0.0,
    val source: String = "manual",       // text / image / voice / barcode / manual
    val aiConfidence: Double? = null,    // AI 给的置信度，低于 0.6 界面要提示核对
    val imagePath: String? = null,       // 照片存 App 私有目录，这里只记路径
    val createdAt: Long = System.currentTimeMillis()
)

/** 表 5：饮水记录。每喝一次加一条。 */
@Entity(tableName = "water_log", indices = [Index("date")])
data class WaterLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val ml: Int,
    val createdAt: Long = System.currentTimeMillis()
)

/** 表 6：日记。一天可以写多篇。 */
@Entity(tableName = "diary", indices = [Index("date")])
data class Diary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val rating: Int = 0,                 // 自评 1-5 星，0 表示未评
    val content: String = "",
    val imagePaths: String = "",         // 多张图用英文逗号拼起来存
    val weightKg: Double? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 表 7：AI 服务配置。
 * 注意：API Key 不存这张表，单独用加密的 SharedPreferences 存，
 * 因为数据库文件可能被完整备份导出，Key 不能跟着走。
 */
@Entity(tableName = "ai_config")
data class AiConfig(
    @PrimaryKey val id: Int = 1,
    val provider: String = "custom",     // deepseek / qwen / doubao / kimi / glm / siliconflow / openai / custom
    val baseUrl: String = "",            // 例如 https://api.deepseek.com/v1
    val textModel: String = "",          // 纯文字识别用的模型名
    val visionModel: String = "",        // 图片识别用的模型名
    val temperature: Double = 0.2,
    val timeoutSec: Int = 30,
    val dailyLimit: Int = 100,           // 单日调用上限，防止费用失控
    val enabled: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 表 8：调用次数统计（对应"我的"页面那八格数字）。
 * 联合主键：日期 + 类型，同一个日期同一个类型只有一行，累加计数。
 */
@Entity(tableName = "usage_stat", primaryKeys = ["date", "type"])
data class UsageStat(
    val date: LocalDate,
    val type: String,                    // image / text / voice / barcode / ai / diary / body / weight
    val count: Int = 0
)

/**
 * 表 9：身体维度（围度，单位都是厘米）。
 * 一天只留一条，同一天再记会覆盖——和体重一样的规矩。
 * 每一项都可空，用户只量腰围也能存。
 */
@Entity(tableName = "body_measure")
data class BodyMeasure(
    @PrimaryKey val date: LocalDate,
    val waistCm: Double? = null,      // 腰围
    val chestCm: Double? = null,      // 胸围
    val hipCm: Double? = null,        // 臀围
    val thighCm: Double? = null,      // 大腿围
    val armCm: Double? = null,        // 上臂围
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 表 10：进度照片。
 * 照片本身存在 App 私有目录，这里只记路径；一天可以放多张。
 */
@Entity(tableName = "progress_photo", indices = [Index("date")])
data class ProgressPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val path: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 表 11：运动记录。
 * 只记「什么运动、多久、消耗多少千卡」，不接可穿戴设备。
 *
 * v1.0.6：补了 steps / distanceKm 两列。
 * 以前确认页让用户按「步数」或「公里」输入，换算成分钟算完热量之后
 * 就把原始数字丢了，历史里只剩「40 分钟」，用户想看「上周走了多少步」
 * 根本查不到 —— 输入了却不存，是最差的一种体验。现在按哪种方式记的，
 * 就把那个数字原样留下来；其它方式记的保持默认 0。
 * 两列的 DEFAULT 必须与迁移 SQL 逐字一致（0 / 0.0）。
 */
@Entity(tableName = "exercise_log", indices = [Index("date")])
data class ExerciseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val type: String,                 // 走路 / 跑步 / 骑行 / 力量 / 游泳 / 其他
    val minutes: Int,
    val kcal: Double,
    /** 用户按「步数」记录时填的步数；其它方式记为 0 */
    @ColumnInfo(defaultValue = "0") val steps: Int = 0,
    /** 用户按「公里」记录时填的公里数；其它方式记为 0.0 */
    @ColumnInfo(defaultValue = "0.0") val distanceKm: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 表 12：断食记录。
 * 一天一条：记当天用的方案和进食窗口，完成后标记一下，
 * 用来画「近 7 日断食」柱状图和历史列表。
 */
@Entity(tableName = "fasting_log", indices = [Index("date")])
data class FastingLog(
    @PrimaryKey val date: LocalDate,
    /** 断食小时数：16 / 14 / 12 */
    val planHours: Int = 16,
    /** 进食窗口开始（当天第几分钟，7:40 = 7*60+40） */
    val eatStartMinute: Int = 7 * 60 + 40,
    /** 进食窗口结束（第几分钟） */
    val eatEndMinute: Int = 15 * 60 + 40,
    /** 当天是否完成 */
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)


/**
 * 表 13：运动库（预置 + 用户收藏）。
 * 记运动时不再手填消耗——选一项运动 + 填时长，
 * 消耗自动按「MET × 体重 × 时长」算出来。
 */
@Entity(tableName = "exercise_preset", indices = [Index("name")])
data class ExercisePreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** MET 值（代谢当量）：消耗千卡 = MET × 体重kg × 时长h */
    val met: Double,
    /** 有氧 / 力量 / 柔韧 */
    val category: String = "有氧",
    val favorite: Boolean = false
)
