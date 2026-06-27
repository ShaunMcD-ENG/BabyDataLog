package com.babydatalog.app.data.repository

import com.babydatalog.app.data.database.dao.MilestoneDao
import com.babydatalog.app.data.database.entity.Milestone
import com.babydatalog.app.data.database.entity.MilestoneCategory
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MilestoneRepository @Inject constructor(
    private val milestoneDao: MilestoneDao
) {

    fun getMilestonesForBaby(babyId: Long): Flow<List<Milestone>> =
        milestoneDao.getMilestonesForBaby(babyId)

    fun getMilestoneById(id: Long): Flow<Milestone?> =
        milestoneDao.getMilestoneById(id)

    fun getMilestonesByCategory(babyId: Long, category: MilestoneCategory): Flow<List<Milestone>> =
        milestoneDao.getMilestonesByCategory(babyId, category)

    suspend fun insertMilestone(milestone: Milestone): Long =
        milestoneDao.insertMilestone(milestone)

    suspend fun updateMilestone(milestone: Milestone) =
        milestoneDao.updateMilestone(milestone)

    suspend fun deleteMilestone(milestone: Milestone) =
        milestoneDao.deleteMilestone(milestone)

    /**
     * Inserts the record if id == 0 (new record), otherwise updates the existing row.
     * Automatically generates a syncUuid when inserting.
     */
    suspend fun upsertMilestone(milestone: Milestone) {
        if (milestone.id == 0L) {
            val withUuid = if (milestone.syncUuid.isBlank()) {
                milestone.copy(syncUuid = UUID.randomUUID().toString())
            } else {
                milestone
            }
            milestoneDao.insertMilestone(withUuid)
        } else {
            milestoneDao.updateMilestone(milestone)
        }
    }
}
