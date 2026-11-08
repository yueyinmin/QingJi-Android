package com.qingji.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * ============================================================
 * 全部数据表定义（共 8 张）
 * ------------------------------------------------------------
 * 为什么放在一个文件里：每张表都很短，摊成 8 个文件反而难找。
 * 等某个表开始长胖（加了很多逻辑），再单独拆出去。
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
 * 表 3：食物库。
 * 三种来源：预置的中国食物成分表、用户自己录的、条码接口查到的。
 */
@Entity(
    tableName = "food",
    indices = [Index("name"), Index("barcode")]
)
data class Food(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pinyin: String = "",             // 拼音首字母，用于快速搜索（如 mf → 米饭）
    val barcode: String? = null,
    val kcalPer100g: Double,             // 每 100 克热量
    val proteinPer100g: Double = 0.0,
    val carbPer100g: Double = 0.0,
    val fatPer100g: Double = 0.0,
    val defaultGrams: Double = 100.0,    // 默认一份是多少克
    val source: String = "preset",       // preset / user / api / openfoodfacts
    /** 食物库左侧导航的分类：主食 / 面食 / 家常菜 / 肉类 … 灌库时按名字自动归类 */
    val category: String = "其他",
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
 */
@Entity(tableName = "exercise_log", indices = [Index("date")])
data class ExerciseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val type: String,                 // 走路 / 跑步 / 骑行 / 力量 / 游泳 / 其他
    val minutes: Int,
    val kcal: Double,
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
