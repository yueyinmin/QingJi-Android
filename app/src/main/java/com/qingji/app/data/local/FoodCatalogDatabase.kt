package com.qingji.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.qingji.app.data.local.dao.FoodCatalogDao
import com.qingji.app.data.local.entity.CatalogFood

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
@Database(entities = [CatalogFood::class], version = 2, exportSchema = true)
abstract class FoodCatalogDatabase : RoomDatabase() {

    abstract fun catalogDao(): FoodCatalogDao

    companion object {
        /**
         * 数据版本（不是 Room 的 version）。
         * 和 tools/food_import/common.py 的 CATALOG_VERSION 保持一致；
         * 以后换预置库，两边一起改，手机才会自动丢弃旧库、重新从 assets 拷新的。
         *
         * 0.2 → 0.3（v1.0.6）：删掉 state 列，纤维/钠/糖/钙改成可空（NULL = 未收录）。
         */
        const val CATALOG_VERSION = "0.3"
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
        private fun ensureFresh(context: Context) {
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
            prefs.edit().putString("catalog_version", CATALOG_VERSION).apply()
        }

        fun get(context: Context): FoodCatalogDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    ensureFresh(context.applicationContext)

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
                    builder
                        // 这里保留兜底：标准库是随 APK 来的公共只读数据，重建不会丢用户记录；
                        // 换预置库靠 ensureFresh() 主动删文件再拷新的，Room version 变了也能打开。
                        // 用户库 qingji.db 那边已移除这行，绝不静默清用户数据。
                        .fallbackToDestructiveMigration()
                        .build()
                        .also { INSTANCE = it }
                }
            }
    }
}
