package com.qingji.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.qingji.app.data.local.dao.FoodCatalogDao
import com.qingji.app.data.local.dao.OmniFoodCatalogDao
import com.qingji.app.data.local.dao.TakeawayCatalogDao
import com.qingji.app.data.local.entity.BrandDishIngredientEntity
import com.qingji.app.data.local.entity.BrandDishProcessingEntity
import com.qingji.app.data.local.entity.BrandDishTraceEntity
import com.qingji.app.data.local.entity.BrandEntity
import com.qingji.app.data.local.entity.CatalogFood
import com.qingji.app.data.local.entity.FoodComposition
import com.qingji.app.data.local.entity.FoodDataSource
import com.qingji.app.data.local.entity.FoodQualityIssue
import com.qingji.app.data.local.entity.NutritionSample
import com.qingji.app.data.local.entity.OmniFoodEntity
import com.qingji.app.data.local.entity.OmniFoodNutrition
import com.qingji.app.data.local.entity.TakeawayComboEntity
import com.qingji.app.data.local.entity.TakeawayComboItemEntity
import com.qingji.app.data.local.entity.TakeawayDataSourceEntity
import com.qingji.app.data.local.entity.TakeawayNutritionEntity
import com.qingji.app.data.local.entity.TakeawayPlatformEntity
import com.qingji.app.data.local.entity.TakeawayProductEntity
import com.qingji.app.data.local.entity.TakeawayStoreScopeEntity
import com.qingji.app.data.local.entity.TakeawayVariantEntity

/**
 * ============================================================
 * 标准食物库：独立的第二个数据库文件 food_catalog.db（只读）
 * ------------------------------------------------------------
 * 为什么单独一个文件：
 *   用户自己的饮食、体重、收藏放在 qingji.db；标准食物是随 App 发的公共数据。
 *   以后食物库从 654 条扩到一万条，只要换 assets 里的预置库 + 把版本号 +1，
 *   用户库一个字节都不用动，也不会有「升级把用户记录覆盖掉」的风险。
 *
 * 预置库由 tools/food_import/export_sqlite.py 生成，表结构直接抄 Room 编译出的
 * schema JSON（当前是 2.json），避免 Room 打开时做表结构体检不通过导致闪退。
 * ============================================================
 */
@Database(
    entities = [
        CatalogFood::class,
        OmniFoodEntity::class,
        OmniFoodNutrition::class,
        NutritionSample::class,
        FoodComposition::class,
        FoodDataSource::class,
        FoodQualityIssue::class,
        // v1.4 老乡鸡接入（Room v4）：品牌 + 溯源三表，品牌由数据库驱动不写死 UI
        BrandEntity::class,
        BrandDishTraceEntity::class,
        BrandDishIngredientEntity::class,
        BrandDishProcessingEntity::class,
        // 第六阶段 外卖快餐独立体系（Room v5）：8 张空表，无真实商品不造假
        TakeawayProductEntity::class,
        TakeawayVariantEntity::class,
        TakeawayComboEntity::class,
        TakeawayComboItemEntity::class,
        TakeawayNutritionEntity::class,
        TakeawayPlatformEntity::class,
        TakeawayStoreScopeEntity::class,
        TakeawayDataSourceEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class FoodCatalogDatabase : RoomDatabase() {

    abstract fun catalogDao(): FoodCatalogDao
    abstract fun omniFoodDao(): OmniFoodCatalogDao
    abstract fun brandCatalogDao(): com.qingji.app.data.local.dao.BrandCatalogDao
    abstract fun takeawayDao(): TakeawayCatalogDao

    companion object {
        /**
         * 数据版本（不是 Room 的 version）。
         * 和 tools/food_import/common.py 的 CATALOG_VERSION 保持一致；
         * 以后换预置库，两边一起改，手机才会自动丢弃旧库、重新从 assets 拷新的。
         *
         * 0.2 → 0.3（v1.0.6）：删掉 state 列，纤维/钠/糖/钙改成可空（NULL = 未收录）。
         * 0.3 → 0.4（v1.3.1）：OmniFood8K 第一批数据。旧 food_catalog 表 654 条保留，
         *   新增 166 条命名食物；8,037 条样本、27,466 条组成关系独立入表，
         *   异常记录隔离，不混入普通食物搜索。换库只删标准库文件，不碰 qingji.db。
         * 0.4 → 0.5（v1.3.2）：用户拍板的审核状态落地——166 条命名食物全部改成
         *   「人工已审核」（food_entity/food_nutrition = approved，food_catalog = 1），
         *   8 条 provisional 保留；4 条严重异常样本连同组成明细、异常标记一起删除
         *   （样本 8037→8033）。只改数据不改表结构，Room schema 不动。
         *   老手机装新版后靠这个版本号把旧库删掉、重新拷 assets 里的新库。
         * 0.5 → 0.6（老乡鸡接入 · Room v3→v4）：新增品牌表 + 溯源三表
         *   （brand / brand_dish_trace / brand_dish_ingredient / brand_dish_processing），
         *   堂食菜品 280 条进 food_catalog 主表（source_id='laoxiangji'，audit_status=0），
         *   溯源数据来自《老乡鸡菜品溯源报告 2.0》（每条带报告页码）。
         *   预置库由 laoxiangji_import.py 按 Room schema 4.json 重建，绝不手写建表 SQL。
         * 0.6 → 0.7（外卖体系 · Room v4→v5）：新增外卖 8 张独立表
         *   （takeaway_product / variant / combo / combo_item / nutrition / platform /
         *   store_scope / data_source），结构先行、数据为空（红线 15：无真实商品不造假）。
         *   堂食菜不复制成外卖商品，只留 linkedFoodId 逻辑关联。旧 11 表数据全量搬运。
         * 0.7 -> 0.8（六大快餐品牌 - 纯数据）：brand 表追加肯德基/麦当劳/华莱士/塔斯汀/
         *   必胜客/达美乐 6 行，food_catalog 主表追加 160 条（food_id 从 210001 起，
         *   source_id = brandId，audit_status=0）。营养按每 100g 存，每份值用真实/估算克重
         *   折算，估算份重已在 portion_note 标注。外卖 8 张表仍为空。Room v5 不动。
         * 0.8 -> 0.9（v1.3.5）：老乡鸡 0 千卡整理——40 道从本地可信库做同名/同类近似
         *   匹配并写回热量与三大营养素，source_note 留痕；其余保持无数据，界面显示
         *   「暂无热量数据」而不是「0 千卡」。表结构不变，audit_status 不变。
         */
        const val CATALOG_VERSION = "0.9-lxj-kcal-match"
        private const val DB_NAME = "food_catalog.db"
        private const val ASSET_PATH = "database/food_catalog.db"
        private const val PREFS_NAME = "qingji_catalog_meta"

        @Volatile
        private var INSTANCE: FoodCatalogDatabase? = null

        /**
         * 数据版本变了就把旧的只读库删掉。
         * 删的是标准库，不是用户库——这正是拆两个文件的意义。
         * 这里用独立的 SharedPreferences，不往 SettingsStore 里加设置项，
         * 因为它记录的是「随 App 一起来的资源装到哪版了」，不是用户偏好。
         */
        /**
         * QJ-GOV-2026-03 / P2-02 两阶段提交第一步：准备。
         * 只删旧库，【不写版本号】——版本号必须等新库打开并校验通过后由 [markInstalled] 写。
         * 这样中途失败时下次启动会重新走准备流程，不会把错误版本标记为已安装。
         */
        private fun prepareFresh(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val installed = prefs.getString("catalog_version", "")
            if (installed == CATALOG_VERSION) return

            val dbFile = context.getDatabasePath(DB_NAME)
            if (dbFile.exists()) {
                context.deleteDatabase(DB_NAME)
                // WAL 模式下还可能留着 -wal / -shm，一并清掉，避免旧数据被读回来
                dbFile.parentFile?.listFiles()?.forEach { f ->
                    if (f.name.startsWith(DB_NAME)) f.delete()
                }
            }
        }

        /** 两阶段提交第二步：新库打开并校验通过后，同步提交版本号（commit 不用异步 apply）。 */
        private fun markInstalled(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString("catalog_version", CATALOG_VERSION).commit()
        }

        /**
         * 基本完整性校验：库文件存在 + PRAGMA quick_check 通过 + 必要表存在。
         * 校验失败返回 false，调用方不写版本号，下次启动重试。
         */
        private fun verifyInstalled(db: FoodCatalogDatabase): Boolean = try {
            val cursor = db.openHelper.readableDatabase.query("PRAGMA quick_check")
            val ok = cursor.use { c -> c.moveToFirst() && c.getString(0).equals("ok", true) }
            ok && db.openHelper.readableDatabase.query(
                "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='food_catalog'"
            ).use { c -> c.moveToFirst() && c.getInt(0) > 0 }
        } catch (e: Exception) {
            android.util.Log.e("FoodCatalog", "标准库校验失败", e)
            false
        }

        fun get(context: Context): FoodCatalogDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    prepareFresh(context.applicationContext)

                    // assets 万一没打进来，也不能让 App 一打开就崩：退化成空标准库，
                    // 用户自建食物和所有记录照常可用。
                    val hasAsset = try {
                        context.applicationContext.assets.open(ASSET_PATH).close()
                        true
                    } catch (e: Exception) {
                        android.util.Log.e("FoodCatalog", "预置食物库缺失，标准库为空", e)
                        false
                    }

                    val builder = Room.databaseBuilder(
                        context.applicationContext,
                        FoodCatalogDatabase::class.java,
                        DB_NAME
                    )
                    if (hasAsset) builder.createFromAsset(ASSET_PATH)
                    val built = builder
                        // 这里保留兜底：标准库是随 APK 来的公共只读数据，重建不会丢用户记录；
                        // 换预置库靠 prepareFresh() 主动删文件再拷新的，Room version 变了也能打开。
                        // 用户库 qingji.db 那边已移除这行，绝不静默清用户数据。
                        .fallbackToDestructiveMigration()
                        .build()

                    // P2-02：只有资产存在且新库通过完整性校验，才提交 installed version。
                    // 失败时不写版本号 → 下次启动重新准备，可自愈，不会静默当成功。
                    if (hasAsset && verifyInstalled(built)) {
                        markInstalled(context.applicationContext)
                    }
                    built.also { INSTANCE = it }
                }
            }
    }
}
