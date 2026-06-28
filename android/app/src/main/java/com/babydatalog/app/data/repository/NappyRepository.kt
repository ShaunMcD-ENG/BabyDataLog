package com.babydatalog.app.data.repository

import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.dao.NappyDao
import com.babydatalog.app.data.database.entity.NappyChange
import com.babydatalog.app.data.database.entity.NappyType
import com.babydatalog.app.utils.floorToMinute
import com.babydatalog.app.utils.syncUuidFor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NappyRepository @Inject constructor(
    private val nappyDao: NappyDao,
    private val babyDao: BabyDao
) {

    fun getNappiesForBaby(babyId: Long): Flow<List<NappyChange>> =
        nappyDao.getNappiesForBaby(babyId)

    fun getNappyById(id: Long): Flow<NappyChange?> =
        nappyDao.getNappyById(id)

    fun getNappiesInRange(babyId: Long, startMs: Long, endMs: Long): Flow<List<NappyChange>> =
        nappyDao.getNappiesInRange(babyId, startMs, endMs)

    fun getLastNappy(babyId: Long): Flow<NappyChange?> =
        nappyDao.getLastNappy(babyId)

    fun getNappyCountByType(
        babyId: Long, startMs: Long, endMs: Long, type: NappyType
    ): Flow<Int> = nappyDao.getNappyCountByType(babyId, startMs, endMs, type)

    suspend fun insertNappy(nappy: NappyChange): Long =
        nappyDao.insertNappy(nappy.copy(updatedAtMs = System.currentTimeMillis()))

    suspend fun updateNappy(nappy: NappyChange) =
        nappyDao.updateNappy(nappy.copy(updatedAtMs = System.currentTimeMillis()))

    suspend fun deleteNappy(nappy: NappyChange) =
        nappyDao.deleteNappy(nappy)

    suspend fun upsertNappy(nappy: NappyChange) {
        val now = System.currentTimeMillis()
        if (nappy.id == 0L) {
            val syncUuid = if (nappy.syncUuid.isBlank()) {
                val babySyncUuid = babyDao.getBabyByIdOnce(nappy.babyId)?.syncUuid
                if (babySyncUuid != null) {
                    syncUuidFor("n", babySyncUuid, floorToMinute(nappy.timestampMs))
                } else {
                    java.util.UUID.randomUUID().toString()
                }
            } else {
                nappy.syncUuid
            }
            nappyDao.insertNappy(nappy.copy(syncUuid = syncUuid, updatedAtMs = now))
        } else {
            nappyDao.updateNappy(nappy.copy(updatedAtMs = now))
        }
    }
}
