package com.babydatalog.app.data.repository

import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.dao.FeedingDao
import com.babydatalog.app.data.database.entity.FeedingSession
import com.babydatalog.app.data.sync.SyncScheduler
import com.babydatalog.app.utils.floorToMinute
import com.babydatalog.app.utils.syncUuidFor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedingRepository @Inject constructor(
    private val feedingDao: FeedingDao,
    private val babyDao: BabyDao,
    private val syncScheduler: SyncScheduler
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

    suspend fun insertFeeding(feeding: FeedingSession): Long {
        val id = feedingDao.insertFeeding(feeding.copy(updatedAtMs = System.currentTimeMillis()))
        syncScheduler.requestSyncSoon()
        return id
    }

    suspend fun updateFeeding(feeding: FeedingSession) {
        feedingDao.updateFeeding(feeding.copy(updatedAtMs = System.currentTimeMillis()))
        syncScheduler.requestSyncSoon()
    }

    suspend fun deleteFeeding(feeding: FeedingSession) {
        val now = System.currentTimeMillis()
        feedingDao.updateFeeding(feeding.copy(deletedAtMs = now, updatedAtMs = now))
        syncScheduler.requestSyncSoon()
    }

    // Returns the persisted entity (with its real id/syncUuid populated) so callers like
    // feeding-timer autosave can adopt the same row on subsequent writes instead of inserting duplicates.
    suspend fun upsertFeeding(feeding: FeedingSession): FeedingSession {
        val now = System.currentTimeMillis()
        val saved = if (feeding.id == 0L) {
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
            val toInsert = feeding.copy(syncUuid = syncUuid, updatedAtMs = now)
            val newId = feedingDao.insertFeeding(toInsert)
            toInsert.copy(id = newId)
        } else {
            val toUpdate = feeding.copy(updatedAtMs = now)
            feedingDao.updateFeeding(toUpdate)
            toUpdate
        }
        syncScheduler.requestSyncSoon()
        return saved
    }

    suspend fun calculateAndSaveDuration(feeding: FeedingSession): FeedingSession {
        val endTime = feeding.endTimeMs ?: return feeding
        val durationMinutes = (endTime - feeding.startTimeMs) / 60_000f
        val updated = feeding.copy(
            durationMinutes = durationMinutes,
            updatedAtMs = System.currentTimeMillis()
        )
        feedingDao.updateFeeding(updated)
        syncScheduler.requestSyncSoon()
        return updated
    }
}
