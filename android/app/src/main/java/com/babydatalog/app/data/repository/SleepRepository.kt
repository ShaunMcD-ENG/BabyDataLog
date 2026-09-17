package com.babydatalog.app.data.repository

import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.dao.SleepDao
import com.babydatalog.app.data.database.entity.SleepSession
import com.babydatalog.app.data.database.entity.SleepType
import com.babydatalog.app.data.sync.SyncScheduler
import com.babydatalog.app.utils.floorToMinute
import com.babydatalog.app.utils.syncUuidFor
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepository @Inject constructor(
    private val sleepDao: SleepDao,
    private val babyDao: BabyDao,
    private val syncScheduler: SyncScheduler
) {

    fun getSleepSessionsForBaby(babyId: Long): Flow<List<SleepSession>> =
        sleepDao.getSleepSessionsForBaby(babyId)

    fun getSleepById(id: Long): Flow<SleepSession?> =
        sleepDao.getSleepById(id)

    fun getOpenSleepSession(babyId: Long): Flow<SleepSession?> =
        sleepDao.getOpenSleepSession(babyId)

    fun getLastClosedSleepSession(babyId: Long): Flow<SleepSession?> =
        sleepDao.getLastClosedSleepSession(babyId)

    private suspend fun newSyncUuid(babyId: Long, startTimeMs: Long): String {
        val babySyncUuid = babyDao.getBabyByIdOnce(babyId)?.syncUuid
        return if (babySyncUuid != null) {
            syncUuidFor("s", babySyncUuid, floorToMinute(startTimeMs))
        } else {
            UUID.randomUUID().toString()
        }
    }

    // Starts a session immediately (endTimeMs = null) so it persists across app
    // restarts and syncs to the other parent's device as "currently asleep"
    // (spec §2.3 / §4.1 — either device can later end it, not just this one).
    suspend fun startSleep(babyId: Long, type: SleepType, startTimeMs: Long = System.currentTimeMillis()): SleepSession {
        val now = System.currentTimeMillis()
        val session = SleepSession(
            syncUuid = newSyncUuid(babyId, startTimeMs),
            babyId = babyId,
            startTimeMs = startTimeMs,
            endTimeMs = null,
            type = type,
            notes = null,
            createdAtMs = now,
            updatedAtMs = now
        )
        val id = sleepDao.insertSleep(session)
        syncScheduler.requestSyncSoon()
        return session.copy(id = id)
    }

    // Ends whatever session is passed in — callable from either device holding
    // the open row, not only the device that started it (spec §2.3).
    suspend fun endSleep(session: SleepSession, endTimeMs: Long = System.currentTimeMillis()) {
        sleepDao.updateSleep(session.copy(endTimeMs = endTimeMs, updatedAtMs = System.currentTimeMillis()))
        syncScheduler.requestSyncSoon()
    }

    // Insert-or-update for the manual entry form — mirrors FeedingRepository.upsertFeeding.
    suspend fun upsertSleep(session: SleepSession): SleepSession {
        val now = System.currentTimeMillis()
        val saved = if (session.id == 0L) {
            val syncUuid = if (session.syncUuid.isBlank()) newSyncUuid(session.babyId, session.startTimeMs) else session.syncUuid
            val toInsert = session.copy(syncUuid = syncUuid, updatedAtMs = now)
            val newId = sleepDao.insertSleep(toInsert)
            toInsert.copy(id = newId)
        } else {
            val toUpdate = session.copy(updatedAtMs = now)
            sleepDao.updateSleep(toUpdate)
            toUpdate
        }
        syncScheduler.requestSyncSoon()
        return saved
    }

    suspend fun deleteSleep(session: SleepSession) {
        val now = System.currentTimeMillis()
        sleepDao.deleteSleep(session.copy(deletedAtMs = now, updatedAtMs = now))
        syncScheduler.requestSyncSoon()
    }
}
