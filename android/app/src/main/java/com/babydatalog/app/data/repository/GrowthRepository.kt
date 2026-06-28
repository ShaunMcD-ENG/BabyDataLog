package com.babydatalog.app.data.repository

import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.dao.GrowthDao
import com.babydatalog.app.data.database.entity.GrowthMeasurement
import com.babydatalog.app.utils.floorToMinute
import com.babydatalog.app.utils.syncUuidFor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrowthRepository @Inject constructor(
    private val growthDao: GrowthDao,
    private val babyDao: BabyDao
) {

    fun getMeasurementsForBaby(babyId: Long) = growthDao.getMeasurementsForBaby(babyId)
    fun getMeasurementById(id: Long) = growthDao.getMeasurementById(id)
    fun getMeasurementsInRange(babyId: Long, startMs: Long, endMs: Long) =
        growthDao.getMeasurementsInRange(babyId, startMs, endMs)
    fun getLatestMeasurement(babyId: Long) = growthDao.getLatestMeasurement(babyId)

    suspend fun upsertMeasurement(m: GrowthMeasurement) {
        val now = System.currentTimeMillis()
        if (m.id == 0L) {
            val syncUuid = if (m.syncUuid.isBlank()) {
                val babySyncUuid = babyDao.getBabyByIdOnce(m.babyId)?.syncUuid
                if (babySyncUuid != null) {
                    syncUuidFor("g", babySyncUuid, floorToMinute(m.timestampMs))
                } else {
                    java.util.UUID.randomUUID().toString()
                }
            } else {
                m.syncUuid
            }
            growthDao.insertMeasurement(m.copy(syncUuid = syncUuid, updatedAtMs = now))
        } else {
            growthDao.updateMeasurement(m.copy(updatedAtMs = now))
        }
    }

    suspend fun deleteMeasurement(m: GrowthMeasurement) {
        val now = System.currentTimeMillis()
        growthDao.updateMeasurement(m.copy(deletedAtMs = now, updatedAtMs = now))
    }
}
