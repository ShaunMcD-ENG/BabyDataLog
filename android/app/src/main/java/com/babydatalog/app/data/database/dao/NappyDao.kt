package com.babydatalog.app.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.babydatalog.app.data.database.entity.NappyChange
import com.babydatalog.app.data.database.entity.NappyType
import kotlinx.coroutines.flow.Flow

@Dao
interface NappyDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNappy(nappy: NappyChange): Long

    @Update
    suspend fun updateNappy(nappy: NappyChange)

    @Delete
    suspend fun deleteNappy(nappy: NappyChange)

    @Query("SELECT * FROM nappy_changes WHERE babyId = :babyId ORDER BY timestampMs DESC")
    fun getNappiesForBaby(babyId: Long): Flow<List<NappyChange>>

    @Query("SELECT * FROM nappy_changes WHERE id = :id")
    fun getNappyById(id: Long): Flow<NappyChange?>

    @Query(
        """
        SELECT * FROM nappy_changes
        WHERE babyId = :babyId
          AND timestampMs >= :startMs
          AND timestampMs <= :endMs
        ORDER BY timestampMs DESC
        """
    )
    fun getNappiesInRange(babyId: Long, startMs: Long, endMs: Long): Flow<List<NappyChange>>

    @Query(
        """
        SELECT * FROM nappy_changes
        WHERE babyId = :babyId
        ORDER BY timestampMs DESC
        LIMIT 1
        """
    )
    fun getLastNappy(babyId: Long): Flow<NappyChange?>

    @Query(
        """
        SELECT COUNT(*) FROM nappy_changes
        WHERE babyId = :babyId
          AND timestampMs >= :startMs
          AND timestampMs < :endMs
          AND type = :type
        """
    )
    fun getNappyCountByType(babyId: Long, startMs: Long, endMs: Long, type: NappyType): Flow<Int>

    @Query("SELECT * FROM nappy_changes")
    suspend fun getAllForSync(): List<NappyChange>

    @Query("SELECT * FROM nappy_changes WHERE syncUuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): NappyChange?
}
