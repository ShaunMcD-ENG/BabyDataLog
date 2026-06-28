package com.babydatalog.app.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.babydatalog.app.data.database.entity.Milestone
import com.babydatalog.app.data.database.entity.MilestoneCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface MilestoneDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMilestone(milestone: Milestone): Long

    @Update
    suspend fun updateMilestone(milestone: Milestone)

    @Delete
    suspend fun deleteMilestone(milestone: Milestone)

    @Query("UPDATE milestones SET deletedAtMs = :now, updatedAtMs = :now WHERE babyId = :babyId AND deletedAtMs IS NULL")
    suspend fun softDeleteAllForBaby(babyId: Long, now: Long)

    @Query("SELECT * FROM milestones WHERE babyId = :babyId AND deletedAtMs IS NULL ORDER BY timestampMs DESC")
    fun getMilestonesForBaby(babyId: Long): Flow<List<Milestone>>

    @Query("SELECT * FROM milestones WHERE id = :id AND deletedAtMs IS NULL")
    fun getMilestoneById(id: Long): Flow<Milestone?>

    @Query("""
        SELECT * FROM milestones
        WHERE babyId = :babyId
          AND category = :category
          AND deletedAtMs IS NULL
        ORDER BY timestampMs DESC
    """)
    fun getMilestonesByCategory(babyId: Long, category: MilestoneCategory): Flow<List<Milestone>>

    // Sync queries — include soft-deleted records so tombstones propagate to other devices
    @Query("SELECT * FROM milestones")
    suspend fun getAllForSync(): List<Milestone>

    @Query("SELECT * FROM milestones WHERE syncUuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): Milestone?

    @Query("DELETE FROM milestones")
    suspend fun deleteAll()
}
