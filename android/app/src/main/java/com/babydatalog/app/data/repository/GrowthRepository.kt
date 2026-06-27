package com.babydatalog.app.data.repository

import com.babydatalog.app.data.database.dao.GrowthDao
import com.babydatalog.app.data.database.entity.GrowthMeasurement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrowthRepository @Inject constructor(private val growthDao: GrowthDao) {
    fun getMeasurementsForBaby(babyId: Long) = growthDao.getMeasurementsForBaby(babyId)
    fun getMeasurementById(id: Long) = growthDao.getMeasurementById(id)
    fun getMeasurementsInRange(babyId: Long, startMs: Long, endMs: Long) = growthDao.getMeasurementsInRange(babyId, startMs, endMs)
    fun getLatestMeasurement(babyId: Long) = growthDao.getLatestMeasurement(babyId)
    suspend fun upsertMeasurement(m: GrowthMeasurement) {
        if (m.id == 0L) {
            growthDao.insertMeasurement(
                if (m.syncUuid.isBlank()) m.copy(syncUuid = java.util.UUID.randomUUID().toString()) else m
            )
        } else {
            growthDao.updateMeasurement(m)
        }
    }
    suspend fun deleteMeasurement(m: GrowthMeasurement) = growthDao.deleteMeasurement(m)
}
