package com.babydatalog.app.data.repository

import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.dao.FeedingDao
import com.babydatalog.app.data.database.dao.GrowthDao
import com.babydatalog.app.data.database.dao.MilestoneDao
import com.babydatalog.app.data.database.dao.NappyDao
import com.babydatalog.app.data.database.entity.Baby
import kotlinx.coroutines.flow.Flow
import com.babydatalog.app.utils.floorToDay
import com.babydatalog.app.utils.syncUuidFor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BabyRepository @Inject constructor(
    private val babyDao: BabyDao,
    private val feedingDao: FeedingDao,
    private val nappyDao: NappyDao,
    private val milestoneDao: MilestoneDao,
    private val growthDao: GrowthDao
) {

    fun getAllBabies(): Flow<List<Baby>> = babyDao.getAllBabies()

    fun getBabyById(id: Long): Flow<Baby?> = babyDao.getBabyById(id)

    suspend fun getBabyByIdOnce(id: Long): Baby? = babyDao.getBabyByIdOnce(id)

    suspend fun insertBaby(baby: Baby): Long =
        babyDao.insertBaby(baby.copy(updatedAtMs = System.currentTimeMillis()))

    suspend fun updateBaby(baby: Baby) =
        babyDao.updateBaby(baby.copy(updatedAtMs = System.currentTimeMillis()))

    suspend fun deleteBaby(baby: Baby) {
        val now = System.currentTimeMillis()
        // Cascade soft-delete all child records first so their tombstones sync independently
        feedingDao.softDeleteAllForBaby(baby.id, now)
        nappyDao.softDeleteAllForBaby(baby.id, now)
        milestoneDao.softDeleteAllForBaby(baby.id, now)
        growthDao.softDeleteAllForBaby(baby.id, now)
        babyDao.updateBaby(baby.copy(deletedAtMs = now, updatedAtMs = now))
    }

    suspend fun getOrCreateDefaultBaby(): Baby {
        val existing = babyDao.getFirstBabyOnce()
        if (existing != null) return existing

        val now = System.currentTimeMillis()
        val default = Baby(
            syncUuid = syncUuidFor("b", "baby", floorToDay(now)),
            name = "Baby",
            birthDateMs = now,
            birthWeightGrams = null,
            createdAtMs = now,
            updatedAtMs = now
        )
        val newId = babyDao.insertBaby(default)
        return default.copy(id = newId)
    }
}
