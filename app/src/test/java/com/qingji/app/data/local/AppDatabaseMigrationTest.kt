package com.qingji.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Room 11 → 12 迁移：只加列加表，老数据一行不丢。 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    private fun openV11(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name("migration_11_12_test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE `exercise` (" +
                            "`uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL DEFAULT '', " +
                            "`primaryMuscles` TEXT NOT NULL DEFAULT '', `secondaryMuscles` TEXT NOT NULL DEFAULT '', " +
                            "`equipment` TEXT NOT NULL DEFAULT '', `steps` TEXT NOT NULL DEFAULT '', " +
                            "`commonMistakes` TEXT NOT NULL DEFAULT '', `isBuiltIn` INTEGER NOT NULL DEFAULT 0, " +
                            "`isFavorite` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL DEFAULT 0, " +
                            "`updatedAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`uuid`))"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        return factory.create(config).writableDatabase
    }

    @Test
    fun `11 到 12 迁移后 老动作收藏自建动作和历史字段全部保留`() {
        val db = openV11()
        db.execSQL(
            "INSERT INTO exercise (uuid,name,category,primaryMuscles,steps,isBuiltIn,isFavorite,createdAt,updatedAt) " +
                "VALUES ('ex-chest-01','哑铃卧推','chest','胸大肌','老要点',1,1,100,200)"
        )
        db.execSQL(
            "INSERT INTO exercise (uuid,name,category,isBuiltIn,isFavorite,createdAt,updatedAt) " +
                "VALUES ('user-abc','我的动作','core',0,1,300,400)"
        )

        AppDatabase.MIGRATION_11_12.migrate(db)

        val cols = db.query("PRAGMA table_info(`exercise`)").use { c ->
            val set = mutableSetOf<String>()
            while (c.moveToNext()) set.add(c.getString(c.getColumnIndex("name")))
            set
        }
        listOf("englishName", "slug", "exerciseType", "isStretch", "assetPath", "attribution")
            .forEach { assertTrue("新列缺失: $it", cols.contains(it)) }

        val tables = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='library_meta'").use { c ->
            var found = false
            while (c.moveToNext()) found = true
            found
        }
        assertTrue("library_meta 表未创建", tables)

        db.query("SELECT isFavorite, steps, isBuiltIn, createdAt, updatedAt FROM exercise WHERE uuid='ex-chest-01'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
            assertEquals("老要点", c.getString(1))
            assertEquals(1, c.getInt(2))
            assertEquals(100L, c.getLong(3))
            assertEquals(200L, c.getLong(4))
        }
        db.query("SELECT isBuiltIn, isFavorite, createdAt FROM exercise WHERE uuid='user-abc'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertEquals(1, c.getInt(1))
            assertEquals(300L, c.getLong(2))
        }
        assertEquals(2L, db.query("SELECT COUNT(*) FROM exercise").use { c ->
            c.moveToFirst(); c.getLong(0)
        })
        db.close()
    }
}
