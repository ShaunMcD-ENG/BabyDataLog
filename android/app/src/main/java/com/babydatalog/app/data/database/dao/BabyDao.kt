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

    @Query("SELECT * FROM babies ORDER BY createdAtMs ASC")
    fun getAllBabies(): Flow<List<Baby>>

    @Query("SELECT * FROM babies WHERE id = :id")
    fun getBabyById(id: Long): Flow<Baby?>

    @Query("SELECT * FROM babies WHERE id = :id")
    suspend fun getBabyByIdOnce(id: Long): Baby?

    @Delete
    suspend fun deleteBaby(baby: Baby)

    /** One-shot query — returns the first baby inserted (lowest id), or null if the table is empty. */
    @Query("SELECT * FROM babies ORDER BY id ASC LIMIT 1")
    suspend fun getFirstBabyOnce(): Baby?
}
