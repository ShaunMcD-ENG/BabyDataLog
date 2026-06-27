package com.babydatalog.app.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.babydatalog.app.data.database.entity.GrowthMeasurement
import kotlinx.coroutines.flow.Flow

@Dao
interface GrowthDao {
    @Insert suspend fun insertMeasurement(m: GrowthMeasurement): Long
    @Update suspend fun updateMeasurement(m: GrowthMeasurement)
    @Delete suspend fun deleteMeasurement(m: GrowthMeasurement)
    // All measurements for a baby, newest first
    @Query("SELECT * FROM growth_measurements WHERE babyId = :babyId ORDER BY timestampMs DESC")
    fun getMeasurementsForBaby(babyId: Long): Flow<List<GrowthMeasurement>>
    // Single by id (for edit screen)
    @Query("SELECT * FROM growth_measurements WHERE id = :id")
    fun getMeasurementById(id: Long): Flow<GrowthMeasurement?>
    // Range query for charts
    @Query("SELECT * FROM growth_measurements WHERE babyId = :babyId AND timestampMs BETWEEN :startMs AND :endMs ORDER BY timestampMs ASC")
    fun getMeasurementsInRange(babyId: Long, startMs: Long, endMs: Long): Flow<List<GrowthMeasurement>>
    // Latest measurement (for home screen)
    @Query("SELECT * FROM growth_measurements WHERE babyId = :babyId ORDER BY timestampMs DESC LIMIT 1")
    fun getLatestMeasurement(babyId: Long): Flow<GrowthMeasurement?>
}
