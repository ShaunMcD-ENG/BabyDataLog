package com.babydatalog.app.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.babydatalog.app.data.database.entity.FeedingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedingDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFeeding(feeding: FeedingSession): Long

    @Update
    suspend fun updateFeeding(feeding: FeedingSession)

    @Delete
    suspend fun deleteFeeding(feeding: FeedingSession)

    @Query("UPDATE feeding_sessions SET deletedAtMs = :now, updatedAtMs = :now WHERE babyId = :babyId AND deletedAtMs IS NULL")
    suspend fun softDeleteAllForBaby(babyId: Long, now: Long)

    @Query("SELECT * FROM feeding_sessions WHERE babyId = :babyId AND deletedAtMs IS NULL ORDER BY startTimeMs DESC")
    fun getFeedingsForBaby(babyId: Long): Flow<List<FeedingSession>>

    @Query("SELECT * FROM feeding_sessions WHERE id = :id AND deletedAtMs IS NULL")
    fun getFeedingById(id: Long): Flow<FeedingSession?>

    @Query("""
        SELECT * FROM feeding_sessions
        WHERE babyId = :babyId
          AND startTimeMs >= :startMs
          AND startTimeMs <= :endMs
          AND deletedAtMs IS NULL
        ORDER BY startTimeMs DESC
    """)
    fun getFeedingsInRange(babyId: Long, startMs: Long, endMs: Long): Flow<List<FeedingSession>>

    @Query("""
        SELECT * FROM feeding_sessions
        WHERE babyId = :babyId AND deletedAtMs IS NULL
        ORDER BY startTimeMs DESC
        LIMIT 1
    """)
    fun getLastFeeding(babyId: Long): Flow<FeedingSession?>

    @Query("""
        SELECT COUNT(*) FROM feeding_sessions
        WHERE babyId = :babyId
          AND startTimeMs >= :dayStartMs
          AND startTimeMs < :dayEndMs
          AND deletedAtMs IS NULL
    """)
    fun getTotalFeedingsForDay(babyId: Long, dayStartMs: Long, dayEndMs: Long): Flow<Int>

    // Sync queries — include soft-deleted records so tombstones propagate to other devices
    @Query("SELECT * FROM feeding_sessions")
    suspend fun getAllForSync(): List<FeedingSession>

    @Query("SELECT * FROM feeding_sessions WHERE syncUuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): FeedingSession?

    @Query("DELETE FROM feeding_sessions")
    suspend fun deleteAll()
}
