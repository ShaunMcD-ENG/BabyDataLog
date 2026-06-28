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

    @Insert
    suspend fun insertMeasurement(m: GrowthMeasurement): Long

    @Update
    suspend fun updateMeasurement(m: GrowthMeasurement)

    @Delete
    suspend fun deleteMeasurement(m: GrowthMeasurement)

    @Query("UPDATE growth_measurements SET deletedAtMs = :now, updatedAtMs = :now WHERE babyId = :babyId AND deletedAtMs IS NULL")
    suspend fun softDeleteAllForBaby(babyId: Long, now: Long)

    @Query("SELECT * FROM growth_measurements WHERE babyId = :babyId AND deletedAtMs IS NULL ORDER BY timestampMs DESC")
    fun getMeasurementsForBaby(babyId: Long): Flow<List<GrowthMeasurement>>

    @Query("SELECT * FROM growth_measurements WHERE id = :id AND deletedAtMs IS NULL")
    fun getMeasurementById(id: Long): Flow<GrowthMeasurement?>

    @Query("SELECT * FROM growth_measurements WHERE babyId = :babyId AND timestampMs BETWEEN :startMs AND :endMs AND deletedAtMs IS NULL ORDER BY timestampMs ASC")
    fun getMeasurementsInRange(babyId: Long, startMs: Long, endMs: Long): Flow<List<GrowthMeasurement>>

    @Query("SELECT * FROM growth_measurements WHERE babyId = :babyId AND deletedAtMs IS NULL ORDER BY timestampMs DESC LIMIT 1")
    fun getLatestMeasurement(babyId: Long): Flow<GrowthMeasurement?>

    // Sync queries — include soft-deleted records so tombstones propagate to other devices
    @Query("SELECT * FROM growth_measurements")
    suspend fun getAllForSync(): List<GrowthMeasurement>

    @Query("SELECT * FROM growth_measurements WHERE syncUuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): GrowthMeasurement?

    @Query("DELETE FROM growth_measurements")
    suspend fun deleteAll()
}
