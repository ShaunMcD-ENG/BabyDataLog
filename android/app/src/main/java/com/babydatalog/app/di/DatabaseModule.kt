package com.babydatalog.app.di

import android.content.Context
import androidx.room.Room
import com.babydatalog.app.data.database.BabyDataLogDatabase
import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.dao.FeedingDao
import com.babydatalog.app.data.database.dao.GrowthDao
import com.babydatalog.app.data.database.dao.MilestoneDao
import com.babydatalog.app.data.database.dao.NappyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideBabyDataLogDatabase(
        @ApplicationContext context: Context
    ): BabyDataLogDatabase {
        return Room.databaseBuilder(
            context,
            BabyDataLogDatabase::class.java,
            "babydatalog.db"
        )
            .addMigrations(
                BabyDataLogDatabase.MIGRATION_1_2,
                BabyDataLogDatabase.MIGRATION_2_3,
                BabyDataLogDatabase.MIGRATION_3_4,
                BabyDataLogDatabase.MIGRATION_4_5
            )
            .build()
    }

    @Provides
    fun provideBabyDao(database: BabyDataLogDatabase): BabyDao =
        database.babyDao()

    @Provides
    fun provideFeedingDao(database: BabyDataLogDatabase): FeedingDao =
        database.feedingDao()

    @Provides
    fun provideNappyDao(database: BabyDataLogDatabase): NappyDao =
        database.nappyDao()

    @Provides
    fun provideMilestoneDao(database: BabyDataLogDatabase): MilestoneDao =
        database.milestoneDao()

    @Provides
    fun provideGrowthDao(db: BabyDataLogDatabase): GrowthDao = db.growthDao()
}
