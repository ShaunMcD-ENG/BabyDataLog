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
        feedingDao.insertFeeding(feeding)

    suspend fun updateFeeding(feeding: FeedingSession) =
        feedingDao.updateFeeding(feeding)

    suspend fun deleteFeeding(feeding: FeedingSession) =
        feedingDao.deleteFeeding(feeding)

    /**
     * Inserts the feeding if id == 0 (new record), otherwise updates the existing row.
     * Automatically generates a syncUuid when inserting.
     */
    suspend fun upsertFeeding(feeding: FeedingSession) {
        if (feeding.id == 0L) {
            val withUuid = if (feeding.syncUuid.isBlank()) {
                feeding.copy(syncUuid = UUID.randomUUID().toString())
            } else {
                feeding
            }
            feedingDao.insertFeeding(withUuid)
        } else {
            feedingDao.updateFeeding(feeding)
        }
    }

    /**
     * If both startTimeMs and endTimeMs are present, computes durationMinutes and
     * persists the updated record. Returns the updated FeedingSession, or the original
     * if endTimeMs is null.
     */
    suspend fun calculateAndSaveDuration(feeding: FeedingSession): FeedingSession {
        val endTime = feeding.endTimeMs ?: return feeding
        val durationMinutes = (endTime - feeding.startTimeMs) / 60_000f
        val updated = feeding.copy(durationMinutes = durationMinutes)
        feedingDao.updateFeeding(updated)
        return updated
    }
}
