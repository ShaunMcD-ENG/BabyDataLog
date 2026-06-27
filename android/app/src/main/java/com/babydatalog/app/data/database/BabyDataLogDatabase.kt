package com.babydatalog.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.babydatalog.app.data.database.dao.BabyDao
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
    version = 3,
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
    }
}
