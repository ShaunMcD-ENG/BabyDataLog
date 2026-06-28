package com.babydatalog.app.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.babydatalog.app.data.database.entity.Baby
import kotlinx.coroutines.flow.Flow

@Dao
interface BabyDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBaby(baby: Baby): Long

    @Update
    suspend fun updateBaby(baby: Baby)

    @Delete
    suspend fun deleteBaby(baby: Baby)

    @Query("SELECT * FROM babies WHERE deletedAtMs IS NULL ORDER BY createdAtMs ASC")
    fun getAllBabies(): Flow<List<Baby>>

    @Query("SELECT * FROM babies WHERE id = :id AND deletedAtMs IS NULL")
    fun getBabyById(id: Long): Flow<Baby?>

    @Query("SELECT * FROM babies WHERE id = :id")
    suspend fun getBabyByIdOnce(id: Long): Baby?

    @Query("SELECT * FROM babies WHERE deletedAtMs IS NULL ORDER BY id ASC LIMIT 1")
    suspend fun getFirstBabyOnce(): Baby?

    // Sync queries — include soft-deleted records so tombstones propagate to other devices
    @Query("SELECT * FROM babies")
    suspend fun getAllForSync(): List<Baby>

    @Query("SELECT * FROM babies WHERE syncUuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): Baby?
}
