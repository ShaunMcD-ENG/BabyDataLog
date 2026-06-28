package com.babydatalog.app.data.repository

import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.dao.FeedingDao
import com.babydatalog.app.data.database.entity.FeedingSession
import com.babydatalog.app.utils.floorToMinute
import com.babydatalog.app.utils.syncUuidFor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedingRepository @Inject constructor(
    private val feedingDao: FeedingDao,
    private val babyDao: BabyDao
) {

    fun getFeedingsForBaby(babyId: Long): Flow<List<FeedingSession>> =
        feedingDao.getFeedingsForBaby(babyId)

    fun getFeedingById(id: Long): Flow<FeedingSession?> =
        feedingDao.getFeedingById(id)

    fun getFeedingsInRange(babyId: Long, startMs: Long, endMs: Long): Flow<List<FeedingSession>> =
        feedingDao.getFeedingsInRange(babyId, startMs, endMs)

    fun getLastFeeding(babyId: Long): Flow<FeedingSession?> =
        feedingDao.getLastFeeding(babyId)

    fun getTotalFeedingsForDay(babyId: Long, dayStartMs: Long, dayEndMs: Long): Flow<Int> =
        feedingDao.getTotalFeedingsForDay(babyId, dayStartMs, dayEndMs)

    suspend fun insertFeeding(feeding: FeedingSession): Long =
        feedingDao.insertFeeding(feeding.copy(updatedAtMs = System.currentTimeMillis()))

    suspend fun updateFeeding(feeding: FeedingSession) =
        feedingDao.updateFeeding(feeding.copy(updatedAtMs = System.currentTimeMillis()))

    suspend fun deleteFeeding(feeding: FeedingSession) =
        feedingDao.deleteFeeding(feeding)

    suspend fun upsertFeeding(feeding: FeedingSession) {
        val now = System.currentTimeMillis()
        if (feeding.id == 0L) {
            val syncUuid = if (feeding.syncUuid.isBlank()) {
                val babySyncUuid = babyDao.getBabyByIdOnce(feeding.babyId)?.syncUuid
                if (babySyncUuid != null) {
                    syncUuidFor("f", babySyncUuid, floorToMinute(feeding.startTimeMs))
                } else {
                    java.util.UUID.randomUUID().toString()
                }
            } else {
                feeding.syncUuid
            }
            feedingDao.insertFeeding(feeding.copy(syncUuid = syncUuid, updatedAtMs = now))
        } else {
            feedingDao.updateFeeding(feeding.copy(updatedAtMs = now))
        }
    }

    suspend fun calculateAndSaveDuration(feeding: FeedingSession): FeedingSession {
        val endTime = feeding.endTimeMs ?: return feeding
        val durationMinutes = (endTime - feeding.startTimeMs) / 60_000f
        val updated = feeding.copy(
            durationMinutes = durationMinutes,
            updatedAtMs = System.currentTimeMillis()
        )
        feedingDao.updateFeeding(updated)
        return updated
    }
}
