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

    @Query("SELECT * FROM feeding_sessions WHERE babyId = :babyId ORDER BY startTimeMs DESC")
    fun getFeedingsForBaby(babyId: Long): Flow<List<FeedingSession>>

    @Query("SELECT * FROM feeding_sessions WHERE id = :id")
    fun getFeedingById(id: Long): Flow<FeedingSession?>

    @Query(
        """
        SELECT * FROM feeding_sessions
        WHERE babyId = :babyId
          AND startTimeMs >= :startMs
          AND startTimeMs <= :endMs
        ORDER BY startTimeMs DESC
        """
    )
    fun getFeedingsInRange(babyId: Long, startMs: Long, endMs: Long): Flow<List<FeedingSession>>

    @Query(
        """
        SELECT * FROM feeding_sessions
        WHERE babyId = :babyId
        ORDER BY startTimeMs DESC
        LIMIT 1
        """
    )
    fun getLastFeeding(babyId: Long): Flow<FeedingSession?>

    @Query(
        """
        SELECT COUNT(*) FROM feeding_sessions
        WHERE babyId = :babyId
          AND startTimeMs >= :dayStartMs
          AND startTimeMs < :dayEndMs
        """
    )
    fun getTotalFeedingsForDay(babyId: Long, dayStartMs: Long, dayEndMs: Long): Flow<Int>
}
