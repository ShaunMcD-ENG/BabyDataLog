package com.babydatalog.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.utils.floorToDay
import com.babydatalog.app.utils.syncUuidFor
import com.babydatalog.app.data.database.dao.FeedingDao
import com.babydatalog.app.data.database.dao.GrowthDao
import com.babydatalog.app.data.database.dao.MilestoneDao
import com.babydatalog.app.data.database.dao.NappyDao
import com.babydatalog.app.data.database.entity.Baby
import com.babydatalog.app.data.database.entity.FeedingSession
import com.babydatalog.app.data.database.entity.GrowthMeasurement
import com.babydatalog.app.data.database.entity.Milestone
import com.babydatalog.app.data.database.entity.NappyChange

@Database(
    entities = [
        Baby::class,
        FeedingSession::class,
        NappyChange::class,
        Milestone::class,
        GrowthMeasurement::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class BabyDataLogDatabase : RoomDatabase() {

    abstract fun babyDao(): BabyDao
    abstract fun feedingDao(): FeedingDao
    abstract fun nappyDao(): NappyDao
    abstract fun milestoneDao(): MilestoneDao
    abstract fun growthDao(): GrowthDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `growth_measurements` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `syncUuid` TEXT NOT NULL,
                        `babyId` INTEGER NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `weightGrams` INTEGER,
                        `heightCm` REAL,
                        `headCircumferenceCm` REAL,
                        `notes` TEXT,
                        `createdAtMs` INTEGER NOT NULL,
                        FOREIGN KEY(`babyId`) REFERENCES `babies`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_growth_measurements_babyId` ON `growth_measurements` (`babyId`)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `growth_measurements` ADD COLUMN `footSizeMm` INTEGER")
                database.execSQL("ALTER TABLE `growth_measurements` ADD COLUMN `handSizeMm` INTEGER")
                database.execSQL("ALTER TABLE `growth_measurements` ADD COLUMN `legLengthCm` REAL")
                database.execSQL("ALTER TABLE `growth_measurements` ADD COLUMN `armLengthCm` REAL")
                database.execSQL("ALTER TABLE `growth_measurements` ADD COLUMN `backLengthCm` REAL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val tables = listOf(
                    "babies", "feeding_sessions", "nappy_changes",
                    "milestones", "growth_measurements"
                )
                for (table in tables) {
                    database.execSQL(
                        "ALTER TABLE `$table` ADD COLUMN `updatedAtMs` INTEGER NOT NULL DEFAULT 0"
                    )
                    database.execSQL("UPDATE `$table` SET `updatedAtMs` = `createdAtMs`")
                }
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val tables = listOf(
                    "babies", "feeding_sessions", "nappy_changes",
                    "milestones", "growth_measurements"
                )
                for (table in tables) {
                    database.execSQL(
                        "ALTER TABLE `$table` ADD COLUMN `deletedAtMs` INTEGER"
                    )
                }
            }
        }

        // Re-derive baby syncUuids from name+birthdate so the same baby
        // entered independently on two phones produces the same UUID.
        // No schema change — data-only migration.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                val cursor = database.query("SELECT id, name, birthDateMs FROM babies")
                val updates = mutableListOf<Triple<Long, String, Long>>()
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    val birthDateMs = cursor.getLong(cursor.getColumnIndexOrThrow("birthDateMs"))
                    val newUuid = syncUuidFor("b", name.trim().lowercase(), floorToDay(birthDateMs))
                    updates.add(Triple(id, newUuid, now))
                }
                cursor.close()
                for ((id, uuid, ts) in updates) {
                    database.execSQL(
                        "UPDATE babies SET syncUuid = ?, updatedAtMs = ? WHERE id = ?",
                        arrayOf(uuid, ts, id)
                    )
                }
            }
        }

        // Split the single type+amount pair into independent wee/poo amounts so a
        // nappy with both contents can record different quantities for each.
        // The table is rebuilt (rather than ALTER TABLE ... DROP COLUMN) because
        // the bundled SQLite on minSdk 26 devices predates DROP COLUMN support.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `nappy_changes_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `syncUuid` TEXT NOT NULL,
                        `babyId` INTEGER NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `weeAmount` TEXT NOT NULL,
                        `pooAmount` TEXT NOT NULL,
                        `pooColour` TEXT,
                        `notes` TEXT,
                        `createdAtMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        `deletedAtMs` INTEGER,
                        FOREIGN KEY(`babyId`) REFERENCES `babies`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO `nappy_changes_new`
                        (id, syncUuid, babyId, timestampMs, weeAmount, pooAmount, pooColour, notes, createdAtMs, updatedAtMs, deletedAtMs)
                    SELECT
                        id, syncUuid, babyId, timestampMs,
                        CASE WHEN type = 'POO' THEN 'NONE' ELSE amount END,
                        CASE WHEN type = 'PEE' THEN 'NONE' ELSE amount END,
                        pooColour, notes, createdAtMs, updatedAtMs, deletedAtMs
                    FROM `nappy_changes`
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE `nappy_changes`")
                database.execSQL("ALTER TABLE `nappy_changes_new` RENAME TO `nappy_changes`")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_nappy_changes_babyId` ON `nappy_changes` (`babyId`)"
                )
            }
        }
    }
}
