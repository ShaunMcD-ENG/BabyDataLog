package com.babydatalog.app.data.repository

import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.entity.Baby
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BabyRepository @Inject constructor(
    private val babyDao: BabyDao
) {

    fun getAllBabies(): Flow<List<Baby>> = babyDao.getAllBabies()

    fun getBabyById(id: Long): Flow<Baby?> = babyDao.getBabyById(id)

    suspend fun getBabyByIdOnce(id: Long): Baby? = babyDao.getBabyByIdOnce(id)

    suspend fun insertBaby(baby: Baby): Long =
        babyDao.insertBaby(baby.copy(updatedAtMs = System.currentTimeMillis()))

    suspend fun updateBaby(baby: Baby) =
        babyDao.updateBaby(baby.copy(updatedAtMs = System.currentTimeMillis()))

    suspend fun deleteBaby(baby: Baby) = babyDao.deleteBaby(baby)

    suspend fun getOrCreateDefaultBaby(): Baby {
        val existing = babyDao.getFirstBabyOnce()
        if (existing != null) return existing

        val now = System.currentTimeMillis()
        val default = Baby(
            syncUuid = UUID.randomUUID().toString(),
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
