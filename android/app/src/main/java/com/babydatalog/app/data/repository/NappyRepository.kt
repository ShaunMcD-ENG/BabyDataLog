package com.babydatalog.app.data.repository

import com.babydatalog.app.data.database.dao.NappyDao
import com.babydatalog.app.data.database.entity.NappyChange
import com.babydatalog.app.data.database.entity.NappyType
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NappyRepository @Inject constructor(
    private val nappyDao: NappyDao
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
        babyId: Long,
        startMs: Long,
        endMs: Long,
        type: NappyType
    ): Flow<Int> = nappyDao.getNappyCountByType(babyId, startMs, endMs, type)

    suspend fun insertNappy(nappy: NappyChange): Long =
        nappyDao.insertNappy(nappy)

    suspend fun updateNappy(nappy: NappyChange) =
        nappyDao.updateNappy(nappy)

    suspend fun deleteNappy(nappy: NappyChange) =
        nappyDao.deleteNappy(nappy)

    /**
     * Inserts the record if id == 0 (new record), otherwise updates the existing row.
     * Automatically generates a syncUuid when inserting.
     */
    suspend fun upsertNappy(nappy: NappyChange) {
        if (nappy.id == 0L) {
            val withUuid = if (nappy.syncUuid.isBlank()) {
                nappy.copy(syncUuid = UUID.randomUUID().toString())
            } else {
                nappy
            }
            nappyDao.insertNappy(withUuid)
        } else {
            nappyDao.updateNappy(nappy)
        }
    }
}
