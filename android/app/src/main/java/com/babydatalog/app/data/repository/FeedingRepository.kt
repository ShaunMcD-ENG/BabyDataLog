package com.babydatalog.app.data.repository

import com.babydatalog.app.data.database.dao.FeedingDao
import com.babydatalog.app.data.database.entity.FeedingSession
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedingRepository @Inject constructor(
    private val feedingDao: FeedingDao
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
            feedingDao.insertFeeding(
                feeding.copy(
                    syncUuid = if (feeding.syncUuid.isBlank()) UUID.randomUUID().toString() else feeding.syncUuid,
                    updatedAtMs = now
                )
            )
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
